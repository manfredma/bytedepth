#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly RELEASE_VERSION="${1:-}"
readonly DEVELOPMENT_VERSION="${2:-}"
readonly TAG="v${RELEASE_VERSION}"

usage() {
    printf 'Usage: %s RELEASE_VERSION DEVELOPMENT_VERSION\nExample: %s 1.2.3 1.2.4-SNAPSHOT\n' "$0" "$0" >&2
    exit 2
}

cleanup_release_state() {
    "$MAVEN_CMD" -B release:clean -Dsort.skip=true >/dev/null || true
}

[[ -n "$RELEASE_VERSION" && -n "$DEVELOPMENT_VERSION" ]] || usage
[[ "$RELEASE_VERSION" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || usage
[[ "$DEVELOPMENT_VERSION" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)-SNAPSHOT$ ]] || usage

JAVA_HOME="$(/usr/libexec/java_home -v 21)"
readonly JAVA_HOME
readonly MAVEN_CMD="${BYTEDEPTH_RELEASE_MAVEN:-${JAVA_HOME}/bin/mvn}"

cd "$SOURCE_ROOT"

if [[ "$(git branch --show-current)" != "main" ]]; then
    printf 'Release preparation must run on main.\n' >&2
    exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
    printf 'Release preparation requires a clean working tree.\n' >&2
    exit 1
fi

if git rev-parse --verify --quiet "refs/tags/$TAG" >/dev/null; then
    printf 'Release tag %s already exists locally.\n' "$TAG" >&2
    exit 1
fi

if git ls-remote --exit-code --tags origin "refs/tags/$TAG" >/dev/null 2>&1; then
    printf 'Release tag %s already exists on origin.\n' "$TAG" >&2
    exit 1
fi

if ! grep -Fq "## [$TAG]" docs/releases/CHANGELOG.md; then
    printf 'CHANGELOG.md must contain a %s entry before preparing a release.\n' "$TAG" >&2
    exit 1
fi

trap cleanup_release_state EXIT

"$MAVEN_CMD" clean install -DskipTests -Dsort.skip=true
"$MAVEN_CMD" test -Dsort.skip=true

if [[ -n "${COVERAGE_INCLUDES:-}" ]]; then
    COVERAGE_INCLUDES="$COVERAGE_INCLUDES" bash scripts/verify-changed-coverage.sh
else
    printf 'Coverage check is not run because COVERAGE_INCLUDES is empty; set it for production Java changes.\n' >&2
fi

"$MAVEN_CMD" -B release:prepare -DreleaseVersion="$RELEASE_VERSION" -DdevelopmentVersion="$DEVELOPMENT_VERSION"
git push origin main --follow-tags
