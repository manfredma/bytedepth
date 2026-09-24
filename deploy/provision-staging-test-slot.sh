#!/usr/bin/env bash
set -euo pipefail
umask 077
# shellcheck source=deploy/lib/staging-test-slot.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib/staging-test-slot.sh"

[[ $EUID == 0 ]] || { slot_die 'run as root'; exit 1; }
[[ ${BYTEDEPTH_DEPLOY_MODE:-} == staging && ${BYTEDEPTH_TEST_SLOT_LOCK_HELD:-} == 1 ]] || { slot_die 'staging mode and deployment-test lock are required'; exit 1; }
[[ $# == 4 && $1 == --run-id && $3 == --manifest ]] || { slot_die 'usage: --run-id RUN_ID --manifest PATH'; exit 1; }
run_id="$2"; manifest="$4"
validate_run_id "$run_id"
state_dir="${BYTEDEPTH_TEST_STATE_DIR:?}"
slot_root_directory "$state_dir"
slot_root_directory /data/images-test
[[ $manifest == "$state_dir/$run_id/manifest" && ! -e $manifest && ! -L $manifest ]] || { slot_die 'manifest path is not a fresh run in state directory'; exit 1; }
[[ ${BYTEDEPTH_TEST_CANDIDATE_SHA:-} =~ ^[0-9a-f]{40}$ ]] || { slot_die 'candidate SHA required'; exit 1; }
[[ ${BYTEDEPTH_TEST_STAGING_REDIS_DB:-} =~ ^[0-9]+$ && ${BYTEDEPTH_TEST_IT_REDIS_DB:-} =~ ^[0-9]+$ && ${BYTEDEPTH_TEST_E2E_REDIS_DB:-} =~ ^[0-9]+$ ]] || { slot_die 'explicit Redis DB assignments required'; exit 1; }
[[ ${BYTEDEPTH_TEST_IT_REDIS_DB} == 14 && ${BYTEDEPTH_TEST_E2E_REDIS_DB} == 15 ]] || { slot_die 'reserved Redis DBs must be 14 and 15'; exit 1; }
[[ ${BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE:-} && ${BYTEDEPTH_TEST_REDIS_SECRET_FILE:-} && ${BYTEDEPTH_TEST_MEILI_SECRET_FILE:-} && ${BYTEDEPTH_TEST_FIXTURE:-} && ${BYTEDEPTH_TEST_FIXTURE_SHA256_FILE:-} ]] || { slot_die 'explicit credential and fixture files required'; exit 1; }
for secret in "$BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE" "$BYTEDEPTH_TEST_REDIS_SECRET_FILE" "$BYTEDEPTH_TEST_MEILI_SECRET_FILE" "$BYTEDEPTH_TEST_FIXTURE_SHA256_FILE"; do slot_root_private "$secret"; done
[[ -f $BYTEDEPTH_TEST_FIXTURE && ! -L $BYTEDEPTH_TEST_FIXTURE ]] || { slot_die 'fixture missing'; exit 1; }
slot_root_private "$BYTEDEPTH_TEST_FIXTURE"
expected_sha="$(tr -d '[:space:]' < "$BYTEDEPTH_TEST_FIXTURE_SHA256_FILE")"
[[ $expected_sha =~ ^[0-9a-f]{64}$ && $(shasum -a 256 "$BYTEDEPTH_TEST_FIXTURE" | awk '{print $1}') == "$expected_sha" ]] || { slot_die 'fixture checksum mismatch'; exit 1; }
validate_fixture "$BYTEDEPTH_TEST_FIXTURE"

REDISCLI_AUTH="$(< "$BYTEDEPTH_TEST_REDIS_SECRET_FILE")"; export REDISCLI_AUTH
BYTEDEPTH_TEST_MEILI_API_KEY="$(< "$BYTEDEPTH_TEST_MEILI_SECRET_FILE")"; export BYTEDEPTH_TEST_MEILI_API_KEY
[[ -n $REDISCLI_AUTH && -n $BYTEDEPTH_TEST_MEILI_API_KEY && $REDISCLI_AUTH != *$'\n'* && $BYTEDEPTH_TEST_MEILI_API_KEY != *$'\n'* ]] || { slot_die 'invalid service secrets'; exit 1; }
BYTEDEPTH_TEST_MEILI_URL="${BYTEDEPTH_TEST_MEILI_URL:-http://127.0.0.1:7700}"
[[ $BYTEDEPTH_TEST_MEILI_URL == http://127.0.0.1:7700 ]] || { slot_die 'Meili must use local endpoint'; exit 1; }
export BYTEDEPTH_TEST_MEILI_URL
redis_config="$(redis-cli CONFIG GET databases)"
capacity="$(printf '%s\n' "$redis_config" | tail -1)"
require_redis_capacity "$capacity" "$BYTEDEPTH_TEST_STAGING_REDIS_DB" "$BYTEDEPTH_TEST_IT_REDIS_DB" "$BYTEDEPTH_TEST_E2E_REDIS_DB"
export BYTEDEPTH_TEST_REDIS_CAPACITY="$capacity"

run_dir="$(dirname "$manifest")"
[[ ! -e $run_dir && ! -L $run_dir ]] || { slot_die 'run directory already exists'; exit 1; }
image_root="/data/images-test/$run_id"
[[ ! -e $image_root && ! -L $image_root ]] || { slot_die 'test image run directory already exists'; exit 1; }
provision_complete=0
run_dir_created=0
image_root_created=0
it_key_created=0
e2e_key_created=0
it_db_created=0
e2e_db_created=0
it_user_created=0
e2e_user_created=0
it_index_created=0
e2e_index_created=0
it_key_uid=''; e2e_key_uid=''
it_index=''; e2e_index=''
it_db=''; e2e_db=''
it_user=''; e2e_user=''
it_password=''; e2e_password=''
provision_cleanup() {
    if (( provision_complete != 0 )); then
        return
    fi
    cleanup_failed=0
    for profile in it e2e; do
        case "$profile" in
            it) key_uid="$it_key_uid"; index="$it_index"; db="$it_db"; user="$it_user"; password="$it_password"; key_created=$it_key_created; index_created=$it_index_created; user_created=$it_user_created; db_created=$it_db_created ;;
            e2e) key_uid="$e2e_key_uid"; index="$e2e_index"; db="$e2e_db"; user="$e2e_user"; password="$e2e_password"; key_created=$e2e_key_created; index_created=$e2e_index_created; user_created=$e2e_user_created; db_created=$e2e_db_created ;;
        esac
        if (( index_created != 0 )); then
            task="$(curl -fsS -X DELETE -H "Authorization: Bearer $BYTEDEPTH_TEST_MEILI_API_KEY" "$BYTEDEPTH_TEST_MEILI_URL/indexes/$index" | jq -er '.taskUid')" &&
                meili_wait_task "$task" || cleanup_failed=1
        fi
        if (( key_created != 0 )); then
            [[ $(curl -sS -o /dev/null -w '%{http_code}' -X DELETE -H "Authorization: Bearer $BYTEDEPTH_TEST_MEILI_API_KEY" "$BYTEDEPTH_TEST_MEILI_URL/keys/$key_uid") == 204 ]] || cleanup_failed=1
        fi
        if (( user_created != 0 )); then
            mysql --defaults-extra-file="$BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE" -e "DROP USER IF EXISTS '$user'@'localhost'" || cleanup_failed=1
        fi
        if (( db_created != 0 )); then
            mysql --defaults-extra-file="$BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE" -e "DROP DATABASE IF EXISTS \`$db\`" || cleanup_failed=1
        fi
    done
    if (( cleanup_failed != 0 )); then
        slot_die 'partial provision cleanup failed; state retained'
        return 1
    fi
    if (( run_dir_created != 0 )) && [[ -d $run_dir && ! -L $run_dir ]]; then
        rm -r -- "$run_dir" || slot_die 'partial local test state cleanup failed'
    fi
    image_root="/data/images-test/$run_id"
    if (( image_root_created != 0 )) && [[ -d $image_root && ! -L $image_root ]]; then
        rm -r -- "$image_root" || slot_die 'partial image directory cleanup failed'
    fi
}
trap provision_cleanup EXIT
for profile in it e2e; do
    db="bytedepth_${profile}_$run_id"
    user="bd_${profile}_$run_id"
    index="posts_${profile}_$run_id"
    [[ $(mysql --defaults-extra-file="$BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE" --batch --skip-column-names -e "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='$db'") == 0 ]] || { slot_die 'run database already exists'; exit 1; }
    [[ $(mysql --defaults-extra-file="$BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE" --batch --skip-column-names -e "SELECT COUNT(*) FROM mysql.user WHERE user='$user' AND host='localhost'") == 0 ]] || { slot_die 'run user already exists'; exit 1; }
    status="$(curl -sS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $BYTEDEPTH_TEST_MEILI_API_KEY" "$BYTEDEPTH_TEST_MEILI_URL/indexes/$index")" || { slot_die 'Meili preflight failed'; exit 1; }
    [[ $status == 404 ]] || { slot_die 'run index exists or Meili preflight failed'; exit 1; }
done
if systemctl is-active --quiet bytedepth-app.service; then slot_die 'staging app must be stopped before provisioning'; exit 1; fi
mkdir -m 0700 "$run_dir"
run_dir_created=1
slot_root_directory "$run_dir"
mkdir -m 0700 "$image_root"
image_root_created=1
slot_root_directory "$image_root"
staging_resource_snapshot "$BYTEDEPTH_TEST_STAGING_REDIS_DB" > "$run_dir/staging-baseline"
chmod 0600 "$run_dir/staging-baseline"
it_password="$(openssl rand -hex 24)"
e2e_password="$(openssl rand -hex 24)"
it_db="bytedepth_it_$run_id"; e2e_db="bytedepth_e2e_$run_id"
it_user="bd_it_$run_id"; e2e_user="bd_e2e_$run_id"
it_index="posts_it_$run_id"; e2e_index="posts_e2e_$run_id"
it_namespace="bytedepth:it:$run_id:"; e2e_namespace="bytedepth:e2e:$run_id:"
it_key_uid="$(uuidgen | tr '[:upper:]' '[:lower:]')"
e2e_key_uid="$(uuidgen | tr '[:upper:]' '[:lower:]')"
for profile in it e2e; do
    upper="${profile^^}"
    case "$profile" in
        it)
            db="$it_db"; user="$it_user"; password="$it_password"
            index="$it_index"; namespace="$it_namespace"; redis_db="$BYTEDEPTH_TEST_IT_REDIS_DB"
            ;;
        e2e)
            db="$e2e_db"; user="$e2e_user"; password="$e2e_password"
            index="$e2e_index"; namespace="$e2e_namespace"; redis_db="$BYTEDEPTH_TEST_E2E_REDIS_DB"
            ;;
        *) slot_die "unsupported test profile: $profile"; exit 1 ;;
    esac
    env_file="$run_dir/staging-$profile.env"
    image_dir="/data/images-test/$run_id/$profile"
    assert_not_staging_resource directory "$image_dir" "$run_id"
    [[ ! -e $image_dir && ! -L $image_dir ]] || { slot_die 'test image directory already exists'; exit 1; }
    mkdir "$image_dir"
    chmod 0700 "$image_dir"
    printf 'SPRING_PROFILES_ACTIVE=staging-%s\nBYTEDEPTH_STAGING_%s_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/%s\nBYTEDEPTH_STAGING_%s_DATASOURCE_USERNAME=%s\nBYTEDEPTH_STAGING_%s_DATASOURCE_PASSWORD=%s\nBYTEDEPTH_STAGING_%s_REDIS_DATABASE=%s\nBYTEDEPTH_STAGING_%s_REDIS_PASSWORD=%s\nBYTEDEPTH_STAGING_%s_REDIS_SESSION_NAMESPACE=%s\nBYTEDEPTH_STAGING_%s_REDIS_KEY_NAMESPACE=%s\nBYTEDEPTH_STAGING_%s_SEARCH_INDEX=%s\nBYTEDEPTH_STAGING_%s_SEARCH_API_KEY=%s\nBYTEDEPTH_STAGING_%s_UPLOAD_IMAGE_DIR=%s\n' \
        "$profile" "$upper" "$db" "$upper" "$user" "$upper" "$password" "$upper" "$redis_db" "$upper" "$REDISCLI_AUTH" "$upper" "$namespace" "$upper" "$namespace" "$upper" "$index" "$upper" '__PENDING_SCOPED_KEY__' "$upper" "$image_dir" > "$env_file"
    chmod 0600 "$env_file"
done
printf 'run_id=%s\ncandidate_sha=%s\nmode=staging\nit_db=%s\nit_user=%s\nit_index=%s\nit_namespace=%s\nit_redis_db=%s\nit_key_uid=%s\ne2e_db=%s\ne2e_user=%s\ne2e_index=%s\ne2e_namespace=%s\ne2e_redis_db=%s\ne2e_key_uid=%s\napp_port=8080\nit_env=%s\ne2e_env=%s\n' \
    "$run_id" "$BYTEDEPTH_TEST_CANDIDATE_SHA" "$it_db" "$it_user" "$it_index" "$it_namespace" "$BYTEDEPTH_TEST_IT_REDIS_DB" "$it_key_uid" "$e2e_db" "$e2e_user" "$e2e_index" "$e2e_namespace" "$BYTEDEPTH_TEST_E2E_REDIS_DB" "$e2e_key_uid" "$run_dir/staging-it.env" "$run_dir/staging-e2e.env" > "$manifest"
chmod 0600 "$manifest"
require_manifest "$manifest"
# Manifest and environment files exist before external writes, so an interrupted
# provision can be cleaned by the same strict teardown contract.
settings="$(curl -fsS -H "Authorization: Bearer $BYTEDEPTH_TEST_MEILI_API_KEY" "$BYTEDEPTH_TEST_MEILI_URL/indexes/posts/settings")"
[[ $(printf '%s' "$settings" | jq -r '.searchableAttributes | type') == array && $(printf '%s' "$settings" | jq -r '.filterableAttributes | type') == array && $(printf '%s' "$settings" | jq -r '.sortableAttributes | type') == array ]] || { slot_die 'production index settings unavailable'; exit 1; }
for profile in it e2e; do
    case "$profile" in
        it) index="$it_index"; key_uid="$it_key_uid" ;;
        e2e) index="$e2e_index"; key_uid="$e2e_key_uid" ;;
        *) slot_die "unsupported test profile: $profile"; exit 1 ;;
    esac
    key_payload="$(jq -nc --arg uid "$key_uid" --arg index "$index" '{uid:$uid,name:"bytedepth staging test slot",actions:["*"],indexes:[$index],expiresAt:null}')"
    key_response="$(curl -fsS -X POST -H "Authorization: Bearer $BYTEDEPTH_TEST_MEILI_API_KEY" -H 'Content-Type: application/json' -d "$key_payload" "$BYTEDEPTH_TEST_MEILI_URL/keys")" || { slot_die 'Meili key creation failed'; exit 1; }
    case "$profile" in
        it) it_key_created=1 ;;
        e2e) e2e_key_created=1 ;;
    esac
    scoped_key="$(printf '%s' "$key_response" | jq -er '.key')"
    [[ $scoped_key =~ ^[A-Za-z0-9_-]+$ ]] || { slot_die 'invalid scoped Meili key'; exit 1; }
    env_file="$run_dir/staging-$profile.env"
    env_tmp="$(mktemp "$run_dir/.staging-$profile.XXXXXX")"
    while IFS= read -r line; do
        if [[ $line == *'__PENDING_SCOPED_KEY__' ]]; then printf '%s\n' "${line/__PENDING_SCOPED_KEY__/$scoped_key}"; else printf '%s\n' "$line"; fi
    done < "$env_file" > "$env_tmp"
    chmod 0600 "$env_tmp"
    mv -f "$env_tmp" "$env_file"
done
for profile in it e2e; do
    case "$profile" in
        it) db="$it_db"; user="$it_user"; password="$it_password"; index="$it_index" ;;
        e2e) db="$e2e_db"; user="$e2e_user"; password="$e2e_password"; index="$e2e_index" ;;
        *) slot_die "unsupported test profile: $profile"; exit 1 ;;
    esac
    mysql --defaults-extra-file="$BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE" -e "CREATE DATABASE \`$db\`"
    case "$profile" in
        it) it_db_created=1 ;;
        e2e) e2e_db_created=1 ;;
    esac
    grant_db="${db//_/\\_}"
    mysql --defaults-extra-file="$BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE" -e "CREATE USER '$user'@'localhost' IDENTIFIED BY '$password'"
    case "$profile" in
        it) it_user_created=1 ;;
        e2e) e2e_user_created=1 ;;
    esac
    mysql --defaults-extra-file="$BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE" -e "GRANT ALL PRIVILEGES ON \`$grant_db\`.* TO '$user'@'localhost'"
    grants="$(mysql --defaults-extra-file="$BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE" --batch --skip-column-names -e "SHOW GRANTS FOR '$user'@'localhost'")"
    expected_grant_pattern="^GRANT ALL PRIVILEGES ON \`$grant_db\`\.\* TO '$user'@'localhost'(;)?$"
    expected_usage_pattern="^GRANT USAGE ON \*\.\* TO '$user'@'localhost'(;)?$"
    while IFS= read -r grant; do
        [[ -z $grant || $grant =~ $expected_grant_pattern || $grant =~ $expected_usage_pattern ]] || {
            slot_die 'test database grant is broader than the manifest database'
            exit 1
        }
    done <<< "$grants"
    MYSQL_PWD="$password" mysql -u "$user" -h 127.0.0.1 "$db" < "$BYTEDEPTH_TEST_FIXTURE"
    [[ $(MYSQL_PWD="$password" mysql -u "$user" -h 127.0.0.1 "$db" --batch --skip-column-names -e 'SELECT DATABASE()') == "$db" ]] || { slot_die 'MySQL connection did not select test DB'; exit 1; }
    index_response="$(curl -fsS -X POST -H "Authorization: Bearer $BYTEDEPTH_TEST_MEILI_API_KEY" -H 'Content-Type: application/json' -d "{\"uid\":\"$index\",\"primaryKey\":\"id\"}" "$BYTEDEPTH_TEST_MEILI_URL/indexes")" || { slot_die 'Meili index creation failed'; exit 1; }
    case "$profile" in
        it) it_index_created=1 ;;
        e2e) e2e_index_created=1 ;;
    esac
    task="$(printf '%s' "$index_response" | jq -er '.taskUid')"
    meili_wait_task "$task"
    task="$(printf '%s' "$settings" | curl -fsS -X PATCH -H "Authorization: Bearer $BYTEDEPTH_TEST_MEILI_API_KEY" -H 'Content-Type: application/json' --data-binary @- "$BYTEDEPTH_TEST_MEILI_URL/indexes/$index/settings" | jq -er '.taskUid')"
    meili_wait_task "$task"
    [[ $(curl -fsS -H "Authorization: Bearer $BYTEDEPTH_TEST_MEILI_API_KEY" "$BYTEDEPTH_TEST_MEILI_URL/indexes/$index" | jq -er '.uid') == "$index" ]] || { slot_die 'Meili index identity mismatch'; exit 1; }
done
provision_complete=1
trap - EXIT
printf 'Provisioned test resources for %s\n' "$run_id"
