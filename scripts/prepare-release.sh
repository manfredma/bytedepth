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

require_staging_evidence() {
    local evidence_name="$1"
    local expected_command="$2"
    local evidence_file="$BYTEDEPTH_STAGING_EVIDENCE_DIR/$evidence_name"
    local line_count
    local commit
    local command
    local timestamp
    local result

    if [[ ! -f "$evidence_file" || -L "$evidence_file" ]]; then
        printf 'Missing staging evidence file: %s\n' "$evidence_name" >&2
        exit 1
    fi

    line_count="$(wc -l < "$evidence_file")"
    line_count="${line_count//[[:space:]]/}"
    if [[ "$line_count" != 4 ]]; then
        printf 'Malformed staging evidence file: %s\n' "$evidence_name" >&2
        exit 1
    fi

    {
        IFS= read -r commit
        IFS= read -r command
        IFS= read -r timestamp
        IFS= read -r result
    } < "$evidence_file"

    if [[ ! "$commit" =~ ^commit=[0-9a-f]{40}$ ]] \
        || [[ "$commit" != "commit=$HEAD_SHA" ]] \
        || [[ "$command" != "command=$expected_command" ]] \
        || [[ ! "$timestamp" =~ ^timestamp=[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] \
        || [[ "$result" != 'result=passed' ]]; then
        printf 'Malformed or mismatched staging evidence file: %s\n' "$evidence_name" >&2
        exit 1
    fi
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

if [[ -z "${BYTEDEPTH_STAGING_EVIDENCE_DIR:-}" ]] \
    || [[ ! -d "$BYTEDEPTH_STAGING_EVIDENCE_DIR" ]] \
    || [[ -L "$BYTEDEPTH_STAGING_EVIDENCE_DIR" ]]; then
    printf 'Release preparation requires BYTEDEPTH_STAGING_EVIDENCE_DIR to name a copied staging evidence directory.\n' >&2
    exit 1
fi

readonly HEAD_SHA="$(git rev-parse HEAD)"
if [[ ! "$HEAD_SHA" =~ ^[0-9a-f]{40}$ ]]; then
    printf 'Unable to determine the full current commit SHA.\n' >&2
    exit 1
fi

require_staging_evidence 'staging-integration' 'run-staging-integration-tests'
require_staging_evidence 'staging-e2e' 'run-staging-e2e-tests'

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
