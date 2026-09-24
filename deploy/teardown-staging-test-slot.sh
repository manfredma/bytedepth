#!/usr/bin/env bash
set -euo pipefail
umask 077
# shellcheck source=deploy/lib/staging-test-slot.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib/staging-test-slot.sh"

[[ $EUID == 0 ]] || { slot_die 'run as root'; exit 1; }
[[ ${BYTEDEPTH_DEPLOY_MODE:-} == staging && ${BYTEDEPTH_TEST_SLOT_LOCK_HELD:-} == 1 ]] || { slot_die 'staging mode and deployment-test lock are required'; exit 1; }
[[ $# == 2 && $1 == --manifest ]] || { slot_die 'usage: --manifest PATH'; exit 1; }
manifest="$2"
state_dir="${BYTEDEPTH_TEST_STATE_DIR:?}"
slot_root_directory "$state_dir"
slot_root_directory /data/images-test
[[ $manifest == "$state_dir/"*/manifest ]] || { slot_die 'manifest outside state directory'; exit 1; }
require_manifest "$manifest"
run_id="$(slot_manifest_value "$manifest" run_id)"
[[ $manifest == "$state_dir/$run_id/manifest" ]] || { slot_die 'manifest path does not match RUN_ID'; exit 1; }
for secret in "${BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE:?}" "${BYTEDEPTH_TEST_REDIS_SECRET_FILE:?}" "${BYTEDEPTH_TEST_MEILI_SECRET_FILE:?}"; do slot_root_private "$secret"; done
for profile in it e2e; do
    slot_root_private "$(slot_manifest_value "$manifest" "${profile}_env")"
done
baseline="$(dirname "$manifest")/staging-baseline"
slot_root_private "$baseline"
REDISCLI_AUTH="$(< "$BYTEDEPTH_TEST_REDIS_SECRET_FILE")"; export REDISCLI_AUTH
BYTEDEPTH_TEST_MEILI_API_KEY="$(< "$BYTEDEPTH_TEST_MEILI_SECRET_FILE")"; export BYTEDEPTH_TEST_MEILI_API_KEY
BYTEDEPTH_TEST_MEILI_URL="${BYTEDEPTH_TEST_MEILI_URL:-http://127.0.0.1:7700}"
[[ $BYTEDEPTH_TEST_MEILI_URL == http://127.0.0.1:7700 ]] || { slot_die 'Meili must use local endpoint'; exit 1; }
export BYTEDEPTH_TEST_MEILI_URL

failed=0
systemctl stop bytedepth-test-slot.service || failed=1
for profile in it e2e; do
    db="$(slot_manifest_value "$manifest" "${profile}_db")"
    user="$(slot_manifest_value "$manifest" "${profile}_user")"
    index="$(slot_manifest_value "$manifest" "${profile}_index")"
    key_uid="$(slot_manifest_value "$manifest" "${profile}_key_uid")"
    namespace="$(slot_manifest_value "$manifest" "${profile}_namespace")"
    redis_db="$(slot_manifest_value "$manifest" "${profile}_redis_db")"
    redis_scan_delete "$redis_db" "$namespace" "$run_id" || failed=1
    index_status="$(curl -sS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $BYTEDEPTH_TEST_MEILI_API_KEY" "$BYTEDEPTH_TEST_MEILI_URL/indexes/$index")" || { failed=1; index_status=000; }
    if [[ $index_status == 200 ]]; then
        task="$(curl -fsS -X DELETE -H "Authorization: Bearer $BYTEDEPTH_TEST_MEILI_API_KEY" "$BYTEDEPTH_TEST_MEILI_URL/indexes/$index" | jq -er '.taskUid')" && meili_wait_task "$task" || failed=1
    elif [[ $index_status != 404 ]]; then failed=1; fi
    key_status="$(curl -sS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $BYTEDEPTH_TEST_MEILI_API_KEY" "$BYTEDEPTH_TEST_MEILI_URL/keys/$key_uid")" || { failed=1; key_status=000; }
    if [[ $key_status == 200 ]]; then
        [[ $(curl -sS -o /dev/null -w '%{http_code}' -X DELETE -H "Authorization: Bearer $BYTEDEPTH_TEST_MEILI_API_KEY" "$BYTEDEPTH_TEST_MEILI_URL/keys/$key_uid") == 204 ]] || failed=1
    elif [[ $key_status != 404 ]]; then failed=1; fi
    mysql --defaults-extra-file="$BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE" -e "DROP USER IF EXISTS '$user'@'localhost'; DROP DATABASE IF EXISTS \`$db\`" || failed=1
    [[ $(mysql --defaults-extra-file="$BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE" --batch --skip-column-names -e "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='$db'") == 0 ]] || failed=1
    image_dir="/data/images-test/$run_id/$profile"
    assert_not_staging_resource directory "$image_dir" "$run_id" || failed=1
    if [[ -d $image_dir && ! -L $image_dir ]]; then
        # The exact RUN_ID/profile directory is the only recursive target.
        rm -r -- "$image_dir" || failed=1
    fi
done
[[ $(staging_resource_digest "$BYTEDEPTH_TEST_STAGING_REDIS_DB") == "$(< "$baseline")" ]] || failed=1
systemctl start bytedepth-app.service || failed=1
systemctl is-active --quiet bytedepth-app.service || failed=1
if (( failed != 0 )); then slot_die 'cleanup or staging restoration failed; preserving manifest'; exit 1; fi
for profile in it e2e; do
    env_file="$(slot_manifest_value "$manifest" "${profile}_env")"
    [[ $env_file == "$(dirname "$manifest")/staging-$profile.env" ]] || exit 1
    rm -- "$env_file"
done
rm -- "$manifest"
rm -- "$baseline"
rmdir -- "$(dirname "$manifest")"
rmdir -- "/data/images-test/$run_id"
printf 'Removed test resources for %s and restored staging app\n' "$run_id"
