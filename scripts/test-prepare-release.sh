#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly TEMP_ROOT="$(mktemp -d)"
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
  local expected_arg_line='${argLine} -Xshare:off --enable-native-access=ALL-UNNAMED -javaagent:${settings.localRepository}/org/mockito/mockito-core/${mockito.version}/mockito-core-${mockito.version}.jar'

  # Unit tests must stay offline: Failsafe is exclusive to this inactive staging profile.
  assert_xpath_true "$pom_file" "count($profile) = 1 and count($profile/*[local-name()='activation']) = 0" || return 1
  assert_xpath_true "$pom_file" "count(//*[local-name()='artifactId' and text()='maven-failsafe-plugin']) = 1 and count($failsafe) = 1" || return 1
  assert_xpath_true "$pom_file" "count($failsafe/*[local-name()='configuration']/*[local-name()='includes']/*[local-name()='include']) = 1 and $failsafe/*[local-name()='configuration']/*[local-name()='includes']/*[local-name()='include' and text()='**/*IT.java']" || return 1
  assert_xpath_true "$pom_file" "count($failsafe/*[local-name()='executions']/*[local-name()='execution']/*[local-name()='goals']/*[local-name()='goal']) = 2 and count($failsafe/*[local-name()='executions']/*[local-name()='execution']/*[local-name()='goals']/*[local-name()='goal' and text()='integration-test']) = 1 and count($failsafe/*[local-name()='executions']/*[local-name()='execution']/*[local-name()='goals']/*[local-name()='goal' and text()='verify']) = 1" || return 1
  assert_xpath_true "$pom_file" "$failsafe/*[local-name()='configuration']/*[local-name()='argLine' and normalize-space(text())='$expected_arg_line']" || return 1
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

RELEASE_TEST_LOG="$TEMP_ROOT/release.log" PATH="$TEMP_ROOT/bin:$PATH" BYTEDEPTH_RELEASE_MAVEN="$TEMP_ROOT/java/bin/mvn" \
    "$TEMP_ROOT/scripts/prepare-release.sh" 1.2.3 1.2.4-SNAPSHOT

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
