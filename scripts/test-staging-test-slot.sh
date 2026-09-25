#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
slot="$root/deploy/lib/staging-test-slot.sh"
[[ -f "$slot" ]] || { printf 'FAIL: slot library missing\n' >&2; exit 1; }
if grep -n -E '(^|[[:space:]])rg([[:space:]]|$)' "$slot" >/dev/null; then
    printf 'FAIL: staging test slot runtime must not require ripgrep on the staging host\n' >&2
    exit 1
fi
if grep -n -E 'mysql --defaults-extra-file=.*--batch|mysql --defaults-extra-file=.*-e' "$root/deploy/provision-staging-test-slot.sh" >/dev/null; then
    printf 'FAIL: staging test slot admin MySQL commands must use the explicit native port helper\n' >&2
    exit 1
fi
if grep -n -E 'mysql --defaults-extra-file=.*--batch|mysql --defaults-extra-file=.*-e' "$root/deploy/teardown-staging-test-slot.sh" >/dev/null; then
    printf 'FAIL: staging test slot teardown admin MySQL commands must use the explicit native port helper\n' >&2
    exit 1
fi
if grep -Fq 'slot_chown_ubuntu "$scan_file"' "$slot"; then
    printf 'FAIL: Redis snapshot /tmp file must retain creator ownership until redirect completes\n' >&2
    exit 1
fi
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

private_file="$tmp/private-file"
touch "$private_file"
chmod 0600 "$private_file"
cat > "$tmp/bin/stat" <<'STAT'
#!/usr/bin/env bash
if [[ "$1" == -f ]]; then
    printf '999\n'
elif [[ "$1" == -c && "$2" == %U ]]; then
    printf 'ubuntu\n'
elif [[ "$1" == -c && "$2" == %a ]]; then
    printf '600\n'
else
    exit 2
fi
STAT
chmod +x "$tmp/bin/stat"
PATH="$tmp/bin:$PATH" bash -c 'source "$1"; slot_root_private "$2"' _ "$slot" "$private_file"

cat > "$tmp/bin/redis-cli" <<'REDIS_EMPTY'
#!/usr/bin/env bash
if [[ "$*" == *' EVAL '* ]]; then
    printf '\n'
fi
REDIS_EMPTY
chmod +x "$tmp/bin/redis-cli"
cat > "$tmp/bin/curl" <<'CURL_STATS'
#!/usr/bin/env bash
printf '%s\n' '{"numberOfDocuments":0,"isIndexing":false,"fieldDistribution":{}}'
CURL_STATS
chmod +x "$tmp/bin/curl"
BYTEDEPTH_TEST_IT_REDIS_DB=14 \
BYTEDEPTH_TEST_E2E_REDIS_DB=15 \
BYTEDEPTH_TEST_MEILI_API_KEY=test-key \
BYTEDEPTH_TEST_MEILI_URL=http://127.0.0.1:17700 \
    bash -c 'source "$1"; staging_resource_snapshot 0 > "$2"' _ "$slot" "$tmp/empty-snapshot"
grep -Fq $'meta\tcaptured_at\t' "$tmp/empty-snapshot" || {
    printf 'FAIL: empty Redis snapshot was not accepted\n' >&2
    exit 1
}

cat > "$tmp/safe-fixture.sql" <<'SAFE_FIXTURE'
INSERT INTO article (id, title) VALUES (1, 'fixture');
INSERT INTO category (id, name) VALUES (1, 'fixture');
INSERT INTO admin (id, password_hash) VALUES (1, '$argon2id$v=19$m=1$fixture');
SAFE_FIXTURE
validate_fixture_cmd="source \"\$1\"; validate_fixture \"\$2\""
bash -c "$validate_fixture_cmd" _ "$slot" "$tmp/safe-fixture.sql"
cat > "$tmp/flyway-history-fixture.sql" <<'FLYWAY_HISTORY_FIXTURE'
INSERT INTO article (id, title) VALUES (1, 'fixture');
INSERT INTO category (id, name) VALUES (1, 'fixture');
INSERT INTO admin (id, password_hash) VALUES (1, '$argon2id$v=19$m=1$fixture');
INSERT INTO flyway_schema_history (version, script) VALUES ('1', 'V1__init_tables.sql');
FLYWAY_HISTORY_FIXTURE
bash -c "$validate_fixture_cmd" _ "$slot" "$tmp/flyway-history-fixture.sql"
cat > "$tmp/decimal-fixture.sql" <<'DECIMAL_FIXTURE'
INSERT INTO article (id, title, score) VALUES (1, 'fixture', 1.23);
INSERT INTO category (id, name) VALUES (1, 'fixture');
INSERT INTO admin (id, password_hash) VALUES (1, '$argon2id$v=19$m=1$fixture');
DECIMAL_FIXTURE
bash -c "$validate_fixture_cmd" _ "$slot" "$tmp/decimal-fixture.sql"
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
for statement in 'RENAME TABLE article TO article_copy;' 'RENAME USER old_user TO new_user;' 'SET GLOBAL max_connections = 100;' 'SET PERSIST_ONLY max_connections = 100;' 'CALL dangerous_procedure();' 'LOCK TABLES article WRITE;' 'CREATE EVENT dangerous_event ON SCHEDULE EVERY 1 DAY DO DELETE FROM article;' 'LOAD XML LOCAL INFILE "fixture.xml" INTO TABLE article;' 'PREPARE dangerous_statement FROM "DELETE FROM article";' 'EXECUTE dangerous_statement;' 'DEALLOCATE PREPARE dangerous_statement;'; do
    {
        printf '%s\n' "INSERT INTO article (id, title) VALUES (1, 'fixture');"
        printf '%s\n' "INSERT INTO category (id, name) VALUES (1, 'fixture');"
        printf '%s\n' "INSERT INTO admin (id, password_hash) VALUES (1, '\$argon2id\$v=19\$m=1\$fixture');"
        printf '%s\n' "$statement"
    } > "$tmp/dangerous-fixture.sql"
    fail_without_calls "dangerous fixture statement: $statement" bash -c "$validate_fixture_cmd" _ "$slot" "$tmp/dangerous-fixture.sql"
done

rg -q 'state_uncertain=1' "$root/deploy/provision-staging-test-slot.sh"
rg -q 'cleanup_failed=1' "$root/deploy/provision-staging-test-slot.sh"
rg -Fq 'expected_grant_line="GRANT ALL PRIVILEGES ON \`$db\`.* TO \`$user\`@\`localhost\`"' "$root/deploy/provision-staging-test-slot.sh"
rg -Fq 'expected_usage_line="GRANT USAGE ON *.* TO \`$user\`@\`localhost\`"' "$root/deploy/provision-staging-test-slot.sh"
! rg -Fq 'grant_db="${db//_/\\_}"' "$root/deploy/provision-staging-test-slot.sh"
rg -Fq 'MYSQL_PWD="$password" mysql -u "$user" -h 127.0.0.1 --port="$BYTEDEPTH_STAGING_MYSQL_PORT" "$db"' "$root/deploy/provision-staging-test-slot.sh"
rg -Fq 'MYSQL_PWD="$password" mysql -u "$user" -h 127.0.0.1 --port="$BYTEDEPTH_STAGING_MYSQL_PORT" "$db" --batch' "$root/deploy/provision-staging-test-slot.sh"

grep -Fqx '    if (( state_uncertain != 0 )); then' <(sed -n '1,90p' "$root/deploy/provision-staging-test-slot.sh") || {
    printf 'FAIL: uncertain provision state must be preserved without destructive cleanup\n' >&2
    exit 1
}
grep -Fqx "        slot_die 'provision state is uncertain; preserving manifest and all resources for manual recovery'" <(sed -n '1,90p' "$root/deploy/provision-staging-test-slot.sh") || {
    printf 'FAIL: uncertain provision state must be preserved without destructive cleanup\n' >&2
    exit 1
}
rg -q 'state-uncertain' "$root/deploy/teardown-staging-test-slot.sh"
rg -q 'refusing destructive cleanup until manual recovery' "$root/deploy/teardown-staging-test-slot.sh"

grep -Fqx 'if systemctl is-active --quiet "$BYTEDEPTH_STAGING_TEST_SLOT_SERVICE"; then' <(sed -n '35,45p' "$root/deploy/teardown-staging-test-slot.sh") || {
    printf 'FAIL: teardown must stop the optional E2E test slot only when it exists and is active\n' >&2
    exit 1
}
grep -Fqx '    if ! systemctl stop "$BYTEDEPTH_STAGING_TEST_SLOT_SERVICE" || systemctl is-active --quiet "$BYTEDEPTH_STAGING_TEST_SLOT_SERVICE"; then' <(sed -n '35,45p' "$root/deploy/teardown-staging-test-slot.sh") || {
    printf 'FAIL: teardown must stop the optional E2E test slot only when it exists and is active\n' >&2
    exit 1
}

stop_line="$(rg -n 'systemctl stop "\$BYTEDEPTH_STAGING_TEST_SLOT_SERVICE"' "$root/deploy/teardown-staging-test-slot.sh" | cut -d: -f1)"
delete_line="$(rg -n 'redis_scan_delete|DROP USER|rm -r --' "$root/deploy/teardown-staging-test-slot.sh" | head -1 | cut -d: -f1)"
[[ $stop_line =~ ^[0-9]+$ && $delete_line =~ ^[0-9]+$ && $stop_line -lt $delete_line ]] || {
    printf 'FAIL: teardown must stop test slot before destructive cleanup\n' >&2
    exit 1
}
printf 'PASS: staging test slot negative contracts\n'
