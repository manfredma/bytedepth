#!/usr/bin/env bash
set -Eeuo pipefail

readonly REPOSITORY_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
    printf 'Staging Changelog gate refused: current directory is not a Git repository.\n' >&2
    exit 1
}
readonly CHANGELOG_PATH='docs/releases/CHANGELOG.md'

TARGET_REF=''
BASE_REF=''

usage() {
    printf 'Usage: %s --target REF --base REF\n' "$0" >&2
    exit 2
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --target)
            [[ $# -ge 2 ]] || usage
            TARGET_REF="$2"
            shift 2
            ;;
        --base)
            [[ $# -ge 2 ]] || usage
            BASE_REF="$2"
            shift 2
            ;;
        *)
            usage
            ;;
    esac
done

[[ -n "$TARGET_REF" && -n "$BASE_REF" ]] || usage

cd "$REPOSITORY_ROOT"
TARGET_COMMIT="$(git rev-parse --verify "$TARGET_REF^{commit}" 2>/dev/null)" || {
    printf 'Staging Changelog gate refused: unable to resolve target ref %s.\n' "$TARGET_REF" >&2
    exit 1
}
BASE_COMMIT="$(git rev-parse --verify "$BASE_REF^{commit}" 2>/dev/null)" || {
    printf 'Staging Changelog gate refused: unable to resolve base ref %s.\n' "$BASE_REF" >&2
    exit 1
}

if [[ "$TARGET_COMMIT" == "$BASE_COMMIT" ]]; then
    printf 'Staging Changelog gate refused: target %s is identical to base %s; deploy the frozen candidate branch, not origin/main.\n' \
        "$TARGET_REF" "$BASE_REF" >&2
    exit 1
fi

if ! git diff --quiet "$BASE_COMMIT...$TARGET_COMMIT" -- "$CHANGELOG_PATH"; then
    printf 'Staging Changelog gate passed: %s changed between %s and %s.\n' \
        "$CHANGELOG_PATH" "$BASE_REF" "$TARGET_REF"
    exit 0
fi

printf 'Staging Changelog gate refused: %s must be modified between %s and %s before staging deployment.\n' \
    "$CHANGELOG_PATH" "$BASE_REF" "$TARGET_REF" >&2
exit 1
