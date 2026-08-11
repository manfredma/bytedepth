#!/usr/bin/env bash
set -euo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly JAVA_HOME="$(/usr/libexec/java_home -v 25)"
readonly COVERAGE_BASE_REF="${COVERAGE_BASE_REF:-$(git -C "$SOURCE_ROOT" describe --tags --abbrev=0)}"
readonly LOG_FILE="$(mktemp)"

cleanup() {
  rm -f "$LOG_FILE"
}
trap cleanup EXIT

run_maven() {
  : > "$LOG_FILE"
  if ! env JAVA_HOME="$JAVA_HOME" mvn "$@" 2>&1 | tee "$LOG_FILE"; then
    exit 1
  fi
  if rg -i '(^|[^[:alpha:]])warning([^[:alpha:]]|$)' "$LOG_FILE"; then
    printf 'Maven command emitted a warning and is rejected by the zero-warning policy.\n' >&2
    exit 1
  fi
}

changed_files() {
  {
    git diff --name-only "$COVERAGE_BASE_REF"...HEAD --
    git diff --name-only --
    git diff --name-only --cached --
  } | awk '/^bytedepth-[^\/]+\/src\/main\/java\/.*\.java$/ { print }' | sort -u
}

cd "$SOURCE_ROOT"
git rev-parse --verify --quiet "$COVERAGE_BASE_REF^{commit}" >/dev/null

if [[ -n "${COVERAGE_INCLUDES:-}" ]]; then
  coverage_includes="$COVERAGE_INCLUDES"
  mapfile -t modules < <(find bytedepth-* -path '*/src/main/java' -type d -print | sed 's#/.*##' | sort -u)
else
  mapfile -t files < <(changed_files)
  if [[ ${#files[@]} -eq 0 ]]; then
    printf 'No changed production Java classes since %s; coverage gate has nothing to verify.\n' "$COVERAGE_BASE_REF"
    exit 0
  fi

  coverage_includes=""
  modules=()
  for file in "${files[@]}"; do
    module="${file%%/*}"
    class_path="${file#*/src/main/java/}"
    class_path="${class_path%.java}*"
    coverage_includes+="${coverage_includes:+,}${class_path}"
    modules+=("$module")
  done
  mapfile -t modules < <(printf '%s\n' "${modules[@]}" | sort -u)
fi

printf 'Coverage base: %s\nCoverage includes: %s\n' "$COVERAGE_BASE_REF" "$coverage_includes"
run_maven clean install -DskipTests -Dsort.skip=true
run_maven verify -Pchanged-coverage -Dcoverage.includes="$coverage_includes" \
  -Dcoverage.check.skip=true -Dsort.skip=true

for module in "${modules[@]}"; do
  printf 'Checking 100%% coverage: %s\n' "$module"
  run_maven -pl "$module" -Pchanged-coverage -Dcoverage.includes="$coverage_includes" \
    -Dcoverage.check.skip=false -Dsort.skip=true jacoco:check@check-changed-classes
done
