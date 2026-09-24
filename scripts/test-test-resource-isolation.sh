#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
slot="$root/deploy/lib/staging-test-slot.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir "$tmp/bin"
for cli in mysql redis-cli curl systemctl; do
    # shellcheck disable=SC2016 # generated fake expands at execution time
    printf '#!/usr/bin/env bash\nprintf "%%s\\n" "%s $*" >> "$FAKE_CALLS"\n' "$cli" > "$tmp/bin/$cli"
    chmod +x "$tmp/bin/$cli"
done
export PATH="$tmp/bin:$PATH" FAKE_CALLS="$tmp/calls"
: > "$FAKE_CALLS"
run_id=20260924_231530_a1b2c3d4
for kind_name in 'mysql bytedepth' 'mysql bytedepth_it_other' 'user staging' 'meili posts' 'redis bytedepth:' 'directory /data/images'; do
    read -r kind name <<< "$kind_name"
    if bash -c 'source "$1"; assert_not_staging_resource "$2" "$3" "$4"' _ "$slot" "$kind" "$name" "$run_id" > "$tmp/out" 2>&1; then
        printf 'FAIL: accepted %s %s\n' "$kind" "$name" >&2; exit 1
    fi
done
if [[ -s "$FAKE_CALLS" ]]; then
    printf 'FAIL: invalid resources reached external CLI\n' >&2; exit 1
fi
for kind_name in "mysql bytedepth_it_$run_id" "mysql bytedepth_e2e_$run_id" "meili posts_it_$run_id" "redis bytedepth:it:$run_id:"; do
    read -r kind name <<< "$kind_name"
    bash -c 'source "$1"; assert_not_staging_resource "$2" "$3" "$4"' _ "$slot" "$kind" "$name" "$run_id"
done
if bash -c 'source "$1"; redis_scan_delete 14 "bytedepth:it:$2:" "$2"' _ "$slot" "$run_id" > "$tmp/out" 2>&1; then
    printf 'FAIL: Redis cleanup accepted an unspecified reserved DB\n' >&2; exit 1
fi
export BYTEDEPTH_TEST_IT_REDIS_DB=14 BYTEDEPTH_TEST_E2E_REDIS_DB=15
printf 'bytedepth:it:%s:sample\n' "$run_id" > "$tmp/keys"
export FAKE_KEYS="$tmp/keys"
# External Redis is replaced at the CLI boundary; the scan/delete arguments
# still come from the real library.
printf '%s\n' '#!/usr/bin/env bash' 'printf "%s\n" "redis-cli $*" >> "$FAKE_CALLS"' 'if [[ " $* " == *" --scan "* ]]; then' '    test ! -s "$FAKE_KEYS" || cat "$FAKE_KEYS"' 'elif [[ " $* " == *" DEL "* ]]; then' '    : > "$FAKE_KEYS"' 'fi' > "$tmp/bin/redis-cli"
chmod +x "$tmp/bin/redis-cli"
bash -c 'source "$1"; redis_scan_delete 14 "bytedepth:it:$2:" "$2"' _ "$slot" "$run_id"
[[ ! -s "$FAKE_KEYS" ]] || { printf 'FAIL: namespaced Redis key survived cleanup\n' >&2; exit 1; }
rg -q -- "--pattern bytedepth:it:$run_id:\*" "$FAKE_CALLS"
rg -q -- "DEL bytedepth:it:$run_id:sample" "$FAKE_CALLS"
printf 'PASS: test resource isolation contracts\n'
