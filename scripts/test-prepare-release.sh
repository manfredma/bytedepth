#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT

mkdir -p "$TEMP_ROOT/scripts" "$TEMP_ROOT/docs/releases" "$TEMP_ROOT/java/bin" "$TEMP_ROOT/bin"
cp "$SOURCE_ROOT/scripts/prepare-release.sh" "$TEMP_ROOT/scripts/prepare-release.sh"
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
