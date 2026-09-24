#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
slot="$root/deploy/lib/staging-test-slot.sh"
[[ -f "$slot" ]] || { printf 'FAIL: slot library missing\n' >&2; exit 1; }
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

fail_without_calls() {
    local label="$1"; shift
    if "$@" > "$tmp/out" 2>&1; then
        printf 'FAIL: %s was accepted\n' "$label" >&2; exit 1
    fi
    if [[ -s "$FAKE_CALLS" ]]; then
        printf 'FAIL: %s invoked external CLI\n' "$label" >&2; exit 1
    fi
}

# shellcheck disable=SC2016 # positional parameters belong to bash -c
fail_without_calls 'unsafe run id' bash -c 'source "$1"; validate_run_id "../escape"' _ "$slot"
# shellcheck disable=SC2016
fail_without_calls 'missing manifest' bash -c 'source "$1"; require_manifest "$2"' _ "$slot" "$tmp/missing"
printf 'run_id=20260924_231530_a1b2c3d4\nmode=production\n' > "$tmp/invalid-manifest"
chmod 0600 "$tmp/invalid-manifest"
# shellcheck disable=SC2016
fail_without_calls 'invalid manifest' bash -c 'source "$1"; require_manifest "$2"' _ "$slot" "$tmp/invalid-manifest"
# shellcheck disable=SC2016
fail_without_calls 'staging database' bash -c 'source "$1"; assert_not_staging_resource mysql bytedepth "$2"' _ "$slot" 20260924_231530_a1b2c3d4
# shellcheck disable=SC2016
fail_without_calls 'staging index' bash -c 'source "$1"; assert_not_staging_resource meili posts "$2"' _ "$slot" 20260924_231530_a1b2c3d4
# shellcheck disable=SC2016
fail_without_calls 'FLUSHALL' bash -c 'source "$1"; redis_scan_delete 14 "bytedepth:it:20260924_231530_a1b2c3d4:" FLUSHALL' _ "$slot"
# shellcheck disable=SC2016
fail_without_calls 'FLUSHDB' bash -c 'source "$1"; redis_scan_delete 14 "bytedepth:it:20260924_231530_a1b2c3d4:" FLUSHDB' _ "$slot"
# shellcheck disable=SC2016
fail_without_calls 'KEYS *' bash -c 'source "$1"; redis_scan_delete 14 "bytedepth:it:20260924_231530_a1b2c3d4:" "KEYS *"' _ "$slot"
# shellcheck disable=SC2016
fail_without_calls 'wildcard namespace' bash -c 'source "$1"; redis_scan_delete 14 "*"' _ "$slot"
# shellcheck disable=SC2016
fail_without_calls 'missing Redis capacity' bash -c 'source "$1"; require_redis_capacity 8 0 14 15' _ "$slot"
printf 'PASS: staging test slot negative contracts\n'
