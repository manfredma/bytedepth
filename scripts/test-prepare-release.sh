#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly TEMP_ROOT="$(mktemp -d)"
readonly CURRENT_SHA='0123456789abcdef0123456789abcdef01234567'
readonly EVIDENCE_DIR="$TEMP_ROOT/staging-evidence"
trap 'rm -rf "$TEMP_ROOT"' EXIT

assert_xpath_true() {
  local pom_file="$1"
  local expression="$2"
  [[ "$(xmllint --xpath "boolean($expression)" "$pom_file")" == true ]]
}

assert_maven_test_boundaries() {
  local pom_file="$1"
  local profile="/*[local-name()='project']/*[local-name()='profiles']/*[local-name()='profile'][*[local-name()='id' and text()='staging-integration']]"
  local failsafe="$profile/*[local-name()='build']/*[local-name()='plugins']/*[local-name()='plugin'][*[local-name()='artifactId' and text()='maven-failsafe-plugin']]"
  local surefire="/*[local-name()='project']/*[local-name()='build']/*[local-name()='pluginManagement']/*[local-name()='plugins']/*[local-name()='plugin'][*[local-name()='artifactId' and text()='maven-surefire-plugin']]"
  local expected_arg_line='${argLine} -Xshare:off --enable-native-access=ALL-UNNAMED -javaagent:${settings.localRepository}/net/bytebuddy/byte-buddy-agent/${byte-buddy.version}/byte-buddy-agent-${byte-buddy.version}.jar'
  local byte_buddy_agent_dependency="*[local-name()='dependencies']/*[local-name()='dependency'][*[local-name()='groupId' and text()='net.bytebuddy'] and *[local-name()='artifactId' and text()='byte-buddy-agent'] and *[local-name()='version' and text()='\${byte-buddy.version}']]"

  # Unit tests must stay offline: Failsafe is exclusive to this inactive staging profile.
  assert_xpath_true "$pom_file" "count($profile) = 1 and count($profile/*[local-name()='activation']) = 0" || return 1
  assert_xpath_true "$pom_file" "count(//*[local-name()='artifactId' and text()='maven-failsafe-plugin']) = 1 and count($failsafe) = 1" || return 1
  assert_xpath_true "$pom_file" "count($failsafe/*[local-name()='configuration']/*[local-name()='includes']/*[local-name()='include']) = 1 and $failsafe/*[local-name()='configuration']/*[local-name()='includes']/*[local-name()='include' and text()='**/*IT.java']" || return 1
  assert_xpath_true "$pom_file" "count($failsafe/*[local-name()='executions']/*[local-name()='execution']/*[local-name()='goals']/*[local-name()='goal']) = 2 and count($failsafe/*[local-name()='executions']/*[local-name()='execution']/*[local-name()='goals']/*[local-name()='goal' and text()='integration-test']) = 1 and count($failsafe/*[local-name()='executions']/*[local-name()='execution']/*[local-name()='goals']/*[local-name()='goal' and text()='verify']) = 1" || return 1
  assert_xpath_true "$pom_file" "$failsafe/*[local-name()='configuration']/*[local-name()='argLine' and normalize-space(text())='$expected_arg_line']" || return 1
  assert_xpath_true "$pom_file" "$surefire/*[local-name()='configuration']/*[local-name()='argLine' and normalize-space(text())='$expected_arg_line']" || return 1
  assert_xpath_true "$pom_file" "count($surefire/$byte_buddy_agent_dependency) = 1" || return 1
  assert_xpath_true "$pom_file" "count($failsafe/$byte_buddy_agent_dependency) = 1" || return 1
  assert_xpath_true "$pom_file" "count(//*[local-name()='argLine'][contains(text(), '-javaagent:') and contains(text(), 'mockito-core')]) = 0" || return 1
  assert_xpath_true "$pom_file" "$failsafe/*[local-name()='configuration']/*[local-name()='systemPropertyVariables']/*[local-name()='bytedepth.it.redis.password' and text()='\${env.BYTEDEPTH_IT_REDIS_PASSWORD}']" || return 1
  assert_xpath_true "$pom_file" "$surefire/*[local-name()='configuration']/*[local-name()='excludes']/*[local-name()='exclude' and text()='**/*IT.java']" || return 1
}

assert_maven_test_boundaries "$SOURCE_ROOT/pom.xml"
# Coverage is unit-only by construction and must never opt into the staging Failsafe profile.
! rg -Fq 'staging-integration' "$SOURCE_ROOT/scripts/verify-changed-coverage.sh"

mkdir -p "$TEMP_ROOT/scripts" "$TEMP_ROOT/docs/releases" "$TEMP_ROOT/java/bin" "$TEMP_ROOT/bin"
cp "$SOURCE_ROOT/scripts/prepare-release.sh" "$TEMP_ROOT/scripts/prepare-release.sh"
cp "$SOURCE_ROOT/pom.xml" "$TEMP_ROOT/invalid-pom.xml"
sed -i '' 's/<id>staging-integration<\/id>/<id>not-staging-integration<\/id>/' "$TEMP_ROOT/invalid-pom.xml"
if assert_maven_test_boundaries "$TEMP_ROOT/invalid-pom.xml"; then
    printf 'Expected structural POM assertion to reject a Failsafe profile outside staging-integration.\n' >&2
    exit 1
fi
printf '## [v1.2.3]\n' > "$TEMP_ROOT/docs/releases/CHANGELOG.md"

cat > "$TEMP_ROOT/scripts/verify-changed-coverage.sh" <<'EOF'
#!/usr/bin/env bash
printf 'coverage\n' >> "$RELEASE_TEST_LOG"
EOF
chmod +x "$TEMP_ROOT/scripts/verify-changed-coverage.sh"

cat > "$TEMP_ROOT/bin/git" <<'EOF'
#!/usr/bin/env bash
printf 'git %s\n' "$*" >> "$RELEASE_TEST_LOG"
case "$1 $2" in
  'branch --show-current') printf 'main\n' ;;
  'status --porcelain') [[ "${RELEASE_TEST_DIRTY:-}" == 1 ]] && printf ' M pom.xml\n' ;;
  'rev-parse HEAD') printf '%s\n' "$RELEASE_TEST_SHA" ;;
  'rev-parse --verify') exit 1 ;;
  'ls-remote --exit-code') exit 2 ;;
esac
EOF
chmod +x "$TEMP_ROOT/bin/git"

cat > "$TEMP_ROOT/java/bin/mvn" <<'EOF'
#!/usr/bin/env bash
printf 'mvn release_mode=%s %s\n' "${BYTEDEPTH_RELEASE_MODE:-0}" "$*" >> "$RELEASE_TEST_LOG"
EOF
chmod +x "$TEMP_ROOT/java/bin/mvn"

run_prepare() {
    RELEASE_TEST_LOG="$1" PATH="$TEMP_ROOT/bin:$PATH" BYTEDEPTH_RELEASE_MAVEN="$TEMP_ROOT/java/bin/mvn" \
        RELEASE_TEST_SHA="$CURRENT_SHA" BYTEDEPTH_STAGING_EVIDENCE_DIR="$EVIDENCE_DIR" \
        "$TEMP_ROOT/scripts/prepare-release.sh" 1.2.3 1.2.4-SNAPSHOT
}

assert_release_rejects_without_maven() {
    local description="$1"
    local log_file="$2"
    if run_prepare "$log_file" >/dev/null 2>&1; then
        printf 'Expected release preparation to reject %s.\n' "$description" >&2
        exit 1
    fi
    [[ ! -e "$log_file" ]] || ! grep -q '^mvn release_mode=1 ' "$log_file"
}

# Commit-bound staging records are mandatory; a bare green result is not evidence.
assert_release_rejects_without_maven 'absent staging evidence' "$TEMP_ROOT/absent-evidence.log"

mkdir -p "$EVIDENCE_DIR"
printf 'green\n' > "$EVIDENCE_DIR/staging-integration"
printf 'green\n' > "$EVIDENCE_DIR/staging-e2e"
assert_release_rejects_without_maven 'malformed staging evidence' "$TEMP_ROOT/malformed-evidence.log"

cat > "$EVIDENCE_DIR/staging-integration" <<EOF
commit=$CURRENT_SHA
command=run-staging-integration-tests
timestamp=2026-09-10T10:11:12Z
result=passed
EOF
rm "$EVIDENCE_DIR/staging-e2e"
assert_release_rejects_without_maven 'absent staging E2E evidence' "$TEMP_ROOT/absent-e2e.log"

cat > "$EVIDENCE_DIR/staging-e2e" <<EOF
commit=$CURRENT_SHA
command=run-staging-e2e-tests
timestamp=2026-09-10T10:11:12Z
result=passed
EOF
rm "$EVIDENCE_DIR/staging-integration"
assert_release_rejects_without_maven 'absent staging integration evidence' "$TEMP_ROOT/absent-integration.log"

cat > "$EVIDENCE_DIR/staging-integration" <<EOF
commit=$CURRENT_SHA
command=run-staging-integration-tests
timestamp=2026-09-10T10:11:12Z
result=passed
EOF
cat > "$EVIDENCE_DIR/staging-e2e" <<EOF
commit=ffffffffffffffffffffffffffffffffffffffff
command=run-staging-e2e-tests
timestamp=2026-09-10T10:11:12Z
result=passed
EOF
assert_release_rejects_without_maven 'mismatched staging E2E commit' "$TEMP_ROOT/mismatched-e2e.log"

cat > "$EVIDENCE_DIR/staging-e2e" <<EOF
commit=$CURRENT_SHA
command=run-staging-e2e-tests
timestamp=2026-09-10T10:11:12Z
result=passed
EOF
cat > "$EVIDENCE_DIR/staging-integration" <<EOF
commit=ffffffffffffffffffffffffffffffffffffffff
command=run-staging-integration-tests
timestamp=2026-09-10T10:11:12Z
result=passed
EOF
assert_release_rejects_without_maven 'mismatched staging integration commit' "$TEMP_ROOT/mismatched-integration.log"

cat > "$EVIDENCE_DIR/staging-integration" <<EOF
commit=$CURRENT_SHA
command=run-staging-integration-tests
timestamp=2026-09-10T10:11:12Z
result=passed
EOF
cat > "$EVIDENCE_DIR/staging-e2e" <<EOF
commit=$CURRENT_SHA
command=run-staging-e2e-tests
timestamp=2026-02-30T10:11:12Z
result=passed
EOF
assert_release_rejects_without_maven 'an impossible UTC timestamp' "$TEMP_ROOT/impossible-timestamp.log"

cat > "$EVIDENCE_DIR/staging-e2e" <<EOF
commit=$CURRENT_SHA
command=run-staging-e2e-tests
timestamp=2026-09-10T10:11:12Z
result=passed
EOF
printf 'unterminated-extra-field' >> "$EVIDENCE_DIR/staging-e2e"
assert_release_rejects_without_maven 'an incomplete fifth evidence line' "$TEMP_ROOT/incomplete-fifth-line.log"

cat > "$EVIDENCE_DIR/staging-e2e" <<EOF
commit=$CURRENT_SHA
command=run-staging-e2e-tests
timestamp=2026-09-10T10:11:12Z
result=passed
EOF
run_prepare "$TEMP_ROOT/release.log"

grep -Fqx 'coverage' "$TEMP_ROOT/release.log"
grep -Fqx 'mvn release_mode=1 -B release:prepare -DskipTests -Darguments=-DskipTests -DreleaseVersion=1.2.3 -DdevelopmentVersion=1.2.4-SNAPSHOT' "$TEMP_ROOT/release.log"
grep -Fqx 'git push origin main --follow-tags' "$TEMP_ROOT/release.log"
grep -Fqx 'mvn release_mode=0 -B release:clean -Dsort.skip=true' "$TEMP_ROOT/release.log"

if RELEASE_TEST_LOG="$TEMP_ROOT/invalid.log" PATH="$TEMP_ROOT/bin:$PATH" BYTEDEPTH_RELEASE_MAVEN="$TEMP_ROOT/java/bin/mvn" \
    "$TEMP_ROOT/scripts/prepare-release.sh" >/dev/null 2>&1; then
    printf 'Expected missing-version validation to fail.\n' >&2
    exit 1
fi

if RELEASE_TEST_DIRTY=1 RELEASE_TEST_LOG="$TEMP_ROOT/dirty.log" PATH="$TEMP_ROOT/bin:$PATH" BYTEDEPTH_RELEASE_MAVEN="$TEMP_ROOT/java/bin/mvn" \
    "$TEMP_ROOT/scripts/prepare-release.sh" 1.2.3 1.2.4-SNAPSHOT >/dev/null 2>&1; then
    printf 'Expected dirty-worktree validation to fail.\n' >&2
    exit 1
fi
[[ ! -e "$TEMP_ROOT/dirty.log" ]] || ! grep -q '^mvn ' "$TEMP_ROOT/dirty.log"

printf 'prepare-release script tests passed\n'
