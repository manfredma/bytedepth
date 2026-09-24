#!/usr/bin/env bash
# Shared, fail-closed resource contract for the serial staging test slot.

slot_die() { printf 'Refusing: %s\n' "$*" >&2; return 1; }

validate_run_id() {
    [[ ${1:-} =~ ^[0-9]{8}_[0-9]{6}_[a-z0-9]{8}$ ]] || slot_die 'invalid RUN_ID'
}

slot_stat_uid() { stat -f %u "$1" 2>/dev/null || stat -c %u "$1"; }
slot_stat_mode() { stat -f %Lp "$1" 2>/dev/null || stat -c %a "$1"; }
slot_root_private() {
    [[ ! -L $1 && $(slot_stat_uid "$1") == 0 && $(slot_stat_mode "$1") == 600 ]] || slot_die "not a root-owned 0600 file: $1"
}
slot_root_directory() {
    [[ -d $1 && ! -L $1 && $(slot_stat_uid "$1") == 0 && $(slot_stat_mode "$1") == 700 && -w $1 ]] || slot_die "not a writable root-owned 0700 directory: $1"
}

assert_not_staging_resource() {
    local kind="$1" name="$2" run_id="$3"
    validate_run_id "$run_id" || return
    case "$kind:$name" in
        "mysql:bytedepth_it_$run_id"|"mysql:bytedepth_e2e_$run_id"|\
        "user:bd_it_$run_id"|"user:bd_e2e_$run_id"|\
        "meili:posts_it_$run_id"|"meili:posts_e2e_$run_id"|\
        "redis:bytedepth:it:$run_id:"|"redis:bytedepth:e2e:$run_id:") return 0 ;;
        directory:*) [[ "$name" == "/data/images-test/$run_id/it" || "$name" == "/data/images-test/$run_id/e2e" ]] && return 0 ;;
    esac
    slot_die "resource is outside RUN_ID scope: $kind"
}

require_redis_capacity() {
    local capacity="$1" staging="$2" it="$3" e2e="$4"
    [[ $capacity =~ ^[0-9]+$ && $staging =~ ^[0-9]+$ && $it =~ ^[0-9]+$ && $e2e =~ ^[0-9]+$ ]] || { slot_die 'invalid Redis DB numbers'; return; }
    (( 10#$capacity > 10#$it && 10#$capacity > 10#$e2e && 10#$it != 10#$e2e && 10#$it != 10#$staging && 10#$e2e != 10#$staging )) || slot_die 'missing reserved Redis DB capacity or DB overlap'
}

slot_manifest_value() { sed -n "s/^$2=//p" "$1"; }
require_manifest() {
    local file="$1" run_id profile key value
    [[ -f $file && ! -L $file ]] || { slot_die 'manifest missing or symlinked'; return; }
    slot_root_private "$file" || return
    [[ $(cut -d= -f1 "$file" | sort | uniq -d | wc -l | tr -d ' ') == 0 ]] || { slot_die 'duplicate manifest keys'; return; }
    [[ $(wc -l < "$file" | tr -d ' ') == 18 ]] || { slot_die 'invalid manifest fields'; return; }
    run_id="$(slot_manifest_value "$file" run_id)"
    validate_run_id "$run_id" || return
    [[ $(slot_manifest_value "$file" candidate_sha) =~ ^[0-9a-f]{40}$ && $(slot_manifest_value "$file" mode) == staging ]] || { slot_die 'invalid candidate SHA or mode'; return; }
    [[ $(slot_manifest_value "$file" it_db) == "bytedepth_it_$run_id" &&
        $(slot_manifest_value "$file" it_user) == "bd_it_$run_id" &&
        $(slot_manifest_value "$file" it_index) == "posts_it_$run_id" &&
        $(slot_manifest_value "$file" it_namespace) == "bytedepth:it:$run_id:" &&
        $(slot_manifest_value "$file" e2e_db) == "bytedepth_e2e_$run_id" &&
        $(slot_manifest_value "$file" e2e_user) == "bd_e2e_$run_id" &&
        $(slot_manifest_value "$file" e2e_index) == "posts_e2e_$run_id" &&
        $(slot_manifest_value "$file" e2e_namespace) == "bytedepth:e2e:$run_id:" ]] || {
        slot_die 'manifest resources are not bound to their profile'
        return 1
    }
    for profile in it e2e; do
        for kind in mysql user meili redis; do
            case $kind in
                mysql) key="${profile}_db" ;;
                user) key="${profile}_user" ;;
                meili) key="${profile}_index" ;;
                redis) key="${profile}_namespace" ;;
            esac
            value="$(slot_manifest_value "$file" "$key")"
            assert_not_staging_resource "$kind" "$value" "$run_id" || return
        done
        value="$(slot_manifest_value "$file" "${profile}_redis_db")"
        [[ $value =~ ^[0-9]+$ ]] || { slot_die 'invalid Redis DB'; return; }
        value="$(slot_manifest_value "$file" "${profile}_key_uid")"
        [[ $value =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]] || { slot_die 'invalid scoped Meili key UID'; return; }
    done
    [[ $(slot_manifest_value "$file" app_port) == 8080 ]] || { slot_die 'invalid test app port'; return; }
    [[ $(slot_manifest_value "$file" it_env) == "$(dirname "$file")/staging-it.env" && $(slot_manifest_value "$file" e2e_env) == "$(dirname "$file")/staging-e2e.env" ]] || { slot_die 'invalid environment path'; return; }
    [[ $(slot_manifest_value "$file" it_redis_db) == 14 && $(slot_manifest_value "$file" e2e_redis_db) == 15 ]] || { slot_die 'invalid reserved Redis DBs'; return; }
    slot_root_directory "$(dirname "$file")" || return
    require_redis_capacity "${BYTEDEPTH_TEST_REDIS_CAPACITY:?}" "${BYTEDEPTH_TEST_STAGING_REDIS_DB:?}" "$(slot_manifest_value "$file" it_redis_db)" "$(slot_manifest_value "$file" e2e_redis_db)"
}

validate_fixture() {
    local fixture="$1" normalized_fixture
    [[ -f $fixture && ! -L $fixture ]] || { slot_die 'fixture missing'; return 1; }
    for token in article category admin; do
        rg -qi "INSERT[[:space:]]+INTO[[:space:]]+.*$token" "$fixture" || { slot_die "fixture lacks $token insert"; return 1; }
    done
    rg -q '\$2[aby]\$|\$argon2(id|i)\$' "$fixture" || { slot_die 'fixture lacks administrator password hash'; return 1; }
    normalized_fixture="$(tr '\n\r\t' ' ' < "$fixture")"
    if rg -n -i '(^|[^a-z])(admin123|changeme|production|bytedepth\.cn)([^a-z]|$)|(--|#|/\*|\*/)|(^|[[:space:];])\\[!#.]|(^|[[:space:];])(system|delimiter|pager|tee|source|use|connect|status|warnings|nowarning|charset|prompt|rehash|edit|go|print)([[:space:];]|$)|(^|[[:space:];])(USE|DELETE|UPDATE|DROP|ALTER|TRUNCATE|CREATE[[:space:]]+(DATABASE|USER)|GRANT|REVOKE|FLUSH|SOURCE|LOAD[[:space:]]+DATA|INTO[[:space:]]+OUTFILE)([[:space:];]|$)|(`[^`]*`[[:space:]]*\.)|(`?[a-z0-9_-]+`?[[:space:]]*\.)' <<< "$normalized_fixture"; then
        slot_die 'fixture contains unsafe SQL or qualified production tables'
        return 1
    fi
}

mysql_exec() {
    local database="$1" sql="$2" run_id="$3"
    assert_not_staging_resource mysql "$database" "$run_id" || return
    [[ $sql == 'SELECT DATABASE()' ]] || { slot_die 'unsupported SQL in mysql_exec'; return; }
    mysql --defaults-extra-file="$BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE" --batch --skip-column-names "$database" -e "$sql"
}

redis_scan_delete() {
    local db="$1" namespace="$2" run_id="$3" key keys before=0 after=0
    [[ $# == 3 ]] || { slot_die 'unexpected Redis operation'; return; }
    assert_not_staging_resource redis "$namespace" "$run_id" || return
    [[ $db == "$BYTEDEPTH_TEST_IT_REDIS_DB" || $db == "$BYTEDEPTH_TEST_E2E_REDIS_DB" ]] || { slot_die 'unreserved Redis DB'; return; }
    keys="$(redis-cli -n "$db" --scan --pattern "${namespace}*")" || return
    while IFS= read -r key; do
        [[ -n $key ]] || continue
        [[ $key == "$namespace"* ]] || { slot_die 'Redis scan returned out-of-scope key'; return; }
        ((before+=1))
        redis-cli -n "$db" DEL "$key" >/dev/null || return
    done <<< "$keys"
    keys="$(redis-cli -n "$db" --scan --pattern "${namespace}*")" || return
    while IFS= read -r key; do [[ -z $key ]] || ((after+=1)); done <<< "$keys"
    (( after == 0 )) || slot_die "Redis cleanup left $after keys (found $before)"
}

meili_wait_task() {
    local task_id="$1" result attempt
    [[ $task_id =~ ^[0-9]+$ ]] || { slot_die 'invalid Meili task id'; return; }
    for ((attempt=0; attempt<30; attempt++)); do
        result="$(curl -fsS -H "Authorization: Bearer $BYTEDEPTH_TEST_MEILI_API_KEY" "$BYTEDEPTH_TEST_MEILI_URL/tasks/$task_id")" || return
        [[ $result == *'"status":"succeeded"'* || $result == *'"status": "succeeded"'* ]] && return 0
        [[ $result == *'"status":"failed"'* || $result == *'"status": "failed"'* ]] && { slot_die 'Meili task failed'; return; }
        sleep 1
    done
    slot_die 'Meili task timeout'
}

write_resource_digest() {
    local manifest="$1" output="$2"
    require_manifest "$manifest" || return
    (umask 077; { for key in run_id candidate_sha it_db e2e_db it_index e2e_index; do slot_manifest_value "$manifest" "$key"; done; } | shasum -a 256 | awk '{print $1}' > "$output")
}

staging_resource_snapshot() {
    local staging_db="$1" redis_snapshot meili_stats key key_id value_hash ttl ttl_state
    [[ $staging_db =~ ^[0-9]+$ && $staging_db != "$BYTEDEPTH_TEST_IT_REDIS_DB" && $staging_db != "$BYTEDEPTH_TEST_E2E_REDIS_DB" ]] || { slot_die 'invalid staging Redis DB'; return; }
    redis_snapshot="$(redis-cli -n "$staging_db" --json --scan | jq -j '.[] , "\u0000"' | while IFS= read -r -d $'\0' key; do
        [[ -n $key ]] || continue
        key_id="$(printf '%s' "$key" | shasum -a 256 | awk '{print $1}')" || exit 1
        value_hash="$(redis-cli -n "$staging_db" --raw DUMP "$key" | shasum -a 256 | awk '{print $1}')" || exit 1
        ttl="$(redis-cli -n "$staging_db" PTTL "$key")" || exit 1
        case "$ttl" in
            -1) ttl_state=persistent ;;
            -2) continue ;;
            [0-9]*) ttl_state=expiring ;;
            *) exit 1 ;;
        esac
        printf 'redis:%s\t%s\t%s\t%s\n' "$key_id" "$ttl_state" "$value_hash" "$ttl"
    done)" || return
    meili_stats="$(curl -fsS -H "Authorization: Bearer $BYTEDEPTH_TEST_MEILI_API_KEY" "$BYTEDEPTH_TEST_MEILI_URL/indexes/posts/stats" | jq -cS .)" || return
    printf 'meta\tcaptured_at\t%s\t0\n' "$(date +%s)"
    printf '%s\n' "$redis_snapshot"
    printf 'meili:index\tpersistent\t%s\t-1\n' "$(printf '%s' "$meili_stats" | shasum -a 256 | awk '{print $1}')"
}

verify_staging_resource_baseline() {
    local staging_db="$1" baseline="$2" current
    current="$(mktemp "$(dirname "$baseline")/.staging-current.XXXXXX")"
    chmod 0600 "$current"
    if ! staging_resource_snapshot "$staging_db" > "$current"; then
        rm -f -- "$current"
        return 1
    fi
    if ! awk -F '\t' '
        FNR == NR {
            if ($1 == "meta") { baseline_time = $3; next }
            baseline_type[$1] = $2; baseline_hash[$1] = $3; baseline_ttl[$1] = $4; next
        }
        {
            if ($1 == "meta") { current_time = $3; next }
            current_type[$1] = $2; current_hash[$1] = $3; current_ttl[$1] = $4
        }
        END {
            elapsed = (current_time - baseline_time) * 1000
            for (key in baseline_type) {
                if (!(key in current_type)) {
                    if (baseline_type[key] == "expiring" && baseline_ttl[key] - elapsed <= 10000) continue
                    exit 1
                }
                if (baseline_type[key] != current_type[key] || baseline_hash[key] != current_hash[key]) exit 1
                if (baseline_type[key] == "expiring") {
                    expected_ttl = baseline_ttl[key] - elapsed
                    if (current_ttl[key] < expected_ttl - 10000 || current_ttl[key] > expected_ttl + 10000) exit 1
                }
            }
            for (key in current_type) if (!(key in baseline_type)) exit 1
        }
    ' "$baseline" "$current"; then
        rm -f -- "$current"
        return 1
    fi
    rm -f -- "$current"
}
