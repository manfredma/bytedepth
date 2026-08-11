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
    mvn_cmd -B release:clean -Dsort.skip=true >/dev/null || true
}

mvn_cmd() {
    env JAVA_HOME="$JAVA_HOME" "$MAVEN_CMD" "$@"
}

release_mvn_cmd() {
    env JAVA_HOME="$JAVA_HOME" BYTEDEPTH_RELEASE_MODE=1 "$MAVEN_CMD" "$@"
}

[[ -n "$RELEASE_VERSION" && -n "$DEVELOPMENT_VERSION" ]] || usage
[[ "$RELEASE_VERSION" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || usage
[[ "$DEVELOPMENT_VERSION" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)-SNAPSHOT$ ]] || usage

JAVA_HOME="$(/usr/libexec/java_home -v 25)"
readonly JAVA_HOME
readonly MAVEN_CMD="${BYTEDEPTH_RELEASE_MAVEN:-mvn}"

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

# 开发完成时必须已独立运行本脚本；这里再次执行，避免发布时绕过覆盖率与零告警门禁。
bash scripts/verify-changed-coverage.sh

release_mvn_cmd -B release:prepare -DskipTests -Darguments="-DskipTests" -DreleaseVersion="$RELEASE_VERSION" -DdevelopmentVersion="$DEVELOPMENT_VERSION"
git push origin main --follow-tags
