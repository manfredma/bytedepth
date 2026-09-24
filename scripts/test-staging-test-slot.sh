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

cat > "$tmp/safe-fixture.sql" <<'SAFE_FIXTURE'
INSERT INTO article (id, title) VALUES (1, 'fixture');
INSERT INTO category (id, name) VALUES (1, 'fixture');
INSERT INTO admin (id, password_hash) VALUES (1, '$argon2id$v=19$m=1$fixture');
SAFE_FIXTURE
validate_fixture_cmd="source \"\$1\"; validate_fixture \"\$2\""
bash -c "$validate_fixture_cmd" _ "$slot" "$tmp/safe-fixture.sql"
cat > "$tmp/unsafe-fixture.sql" <<'UNSAFE_FIXTURE'
INSERT INTO article (id, title) VALUES (1, 'fixture');
INSERT INTO category (id, name) VALUES (1, 'fixture');
INSERT INTO admin (id, password_hash) VALUES (1, '$argon2id$v=19$m=1$fixture');
USE bytedepth;
DELETE FROM article;
INSERT INTO \`bytedepth-\`.\`article\` VALUES (2);
UNSAFE_FIXTURE
fail_without_calls 'fixture database switch or destructive SQL' bash -c "$validate_fixture_cmd" _ "$slot" "$tmp/unsafe-fixture.sql"
cat > "$tmp/qualified-fixture.sql" <<'QUALIFIED_FIXTURE'
INSERT INTO article (id, title) VALUES (1, 'fixture');
INSERT INTO category (id, name) VALUES (1, 'fixture');
INSERT INTO admin (id, password_hash) VALUES (1, '$argon2id$v=19$m=1$fixture');
INSERT INTO `bytedepth-prod`/**/.`article` VALUES (2);
QUALIFIED_FIXTURE
fail_without_calls 'fixture qualified database through comment' bash -c "$validate_fixture_cmd" _ "$slot" "$tmp/qualified-fixture.sql"
cat > "$tmp/client-command-fixture.sql" <<'CLIENT_COMMAND_FIXTURE'
INSERT INTO article (id, title) VALUES (1, 'fixture');
INSERT INTO category (id, name) VALUES (1, 'fixture');
INSERT INTO admin (id, password_hash) VALUES (1, '$argon2id$v=19$m=1$fixture');
system id;
CLIENT_COMMAND_FIXTURE
fail_without_calls 'mysql system client command' bash -c "$validate_fixture_cmd" _ "$slot" "$tmp/client-command-fixture.sql"
cat > "$tmp/backslash-command-fixture.sql" <<'BACKSLASH_COMMAND_FIXTURE'
INSERT INTO article (id, title) VALUES (1, 'fixture');
INSERT INTO category (id, name) VALUES (1, 'fixture');
INSERT INTO admin (id, password_hash) VALUES (1, '$argon2id$v=19$m=1$fixture');
\u
BACKSLASH_COMMAND_FIXTURE
fail_without_calls 'mysql backslash client command' bash -c "$validate_fixture_cmd" _ "$slot" "$tmp/backslash-command-fixture.sql"

stop_line="$(rg -n 'systemctl stop bytedepth-test-slot\.service' "$root/deploy/teardown-staging-test-slot.sh" | cut -d: -f1)"
delete_line="$(rg -n 'redis_scan_delete|DROP USER|rm -r --' "$root/deploy/teardown-staging-test-slot.sh" | head -1 | cut -d: -f1)"
[[ $stop_line =~ ^[0-9]+$ && $delete_line =~ ^[0-9]+$ && $stop_line -lt $delete_line ]] || {
    printf 'FAIL: teardown must stop test slot before destructive cleanup\n' >&2
    exit 1
}
printf 'PASS: staging test slot negative contracts\n'
