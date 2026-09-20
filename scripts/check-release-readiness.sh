#!/usr/bin/env bash
set -Eeuo pipefail

REPOSITORY_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
    printf 'Release readiness refused: current directory is not a Git repository.\n' >&2
    exit 1
}
readonly REPOSITORY_ROOT

TARGET_REF=HEAD
BASE_REF=origin/main
MODE=candidate

usage() {
    printf 'Usage: %s [--target REF] [--base REF] [--mode candidate|release]\n' "$0" >&2
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
        --mode)
            [[ $# -ge 2 ]] || usage
            MODE="$2"
            shift 2
            ;;
        *)
            usage
            ;;
    esac
done

[[ "$MODE" == candidate || "$MODE" == release ]] || usage

cd "$REPOSITORY_ROOT"
readonly CHANGELOG_FILE="$REPOSITORY_ROOT/docs/releases/CHANGELOG.md"

bash "$REPOSITORY_ROOT/scripts/check-changelog-order.sh" "$CHANGELOG_FILE"

TARGET_COMMIT="$(git rev-parse --verify "$TARGET_REF^{commit}" 2>/dev/null)" || {
    printf 'Release readiness refused: unable to resolve target ref %s.\n' "$TARGET_REF" >&2
    exit 1
}
BASE_COMMIT="$(git rev-parse --verify "$BASE_REF^{commit}" 2>/dev/null)" || {
    printf 'Release readiness refused: unable to resolve base ref %s.\n' "$BASE_REF" >&2
    exit 1
}

changed_paths=''
if ! changed_paths="$(git diff --name-only "$BASE_COMMIT...$TARGET_COMMIT" --)"; then
    printf 'Release readiness refused: unable to calculate the change range.\n' >&2
    exit 1
fi

if [[ "$TARGET_REF" == HEAD ]]; then
    changed_paths+="$(git diff --name-only "$BASE_COMMIT" --)
$(git diff --cached --name-only --)
$(git ls-files --others --exclude-standard)"
fi

mapfile -t changed_files < <(printf '%s\n' "$changed_paths" | sed '/^$/d' | sort -u)
if [[ "${#changed_files[@]}" -eq 0 ]]; then
    printf 'Release readiness passed: no changes relative to %s.\n' "$BASE_REF"
    exit 0
fi

is_non_runtime_path() {
    local path="$1"

    case "$path" in
        docs/*|AGENTS.md|README|README.*|LICENSE|LICENSE.*|NOTICE|NOTICE.*|CONTRIBUTING|CONTRIBUTING.*)
            return 0
            ;;
        src/test/*|*/src/test/*|tests/*|frontend/tests/*)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

runtime_files=()
for path in "${changed_files[@]}"; do
    if ! is_non_runtime_path "$path"; then
        runtime_files+=("$path")
    fi
done

if [[ "${#runtime_files[@]}" -eq 0 ]]; then
    printf 'Release readiness passed: documentation/test-only change.\n'
    exit 0
fi

if [[ ! -f "$CHANGELOG_FILE" ]]; then
    printf 'Release readiness refused: missing %s.\n' "$CHANGELOG_FILE" >&2
    exit 1
fi

if ! awk '
    BEGIN { in_unreleased = 0; has_category = 0; has_item = 0 }
    /^## Unreleased[[:space:]]*$/ { in_unreleased = 1; next }
    in_unreleased && /^## / { exit }
    !in_unreleased { next }
    /^### (Added|Changed|Deprecated|Removed|Fixed|Security|Compatibility)[[:space:]]*$/ {
        has_category = 1
        next
    }
    in_unreleased && has_category && /^[[:space:]]*-[[:space:]]+/ {
        item = $0
        sub(/^[[:space:]]*-[[:space:]]+/, "", item)
        if (item != "" && item !~ /^[[:space:]]*<!--[[:space:][:print:]]*-->[[:space:]]*$/) {
            has_item = 1
        }
    }
    END { exit !(in_unreleased && has_category && has_item) }
' "$CHANGELOG_FILE"; then
    printf 'Release readiness refused: %s must contain a non-empty categorized ## Unreleased entry.\n' "$CHANGELOG_FILE" >&2
    printf 'Add a bullet under one of Added, Changed, Deprecated, Removed, Fixed, Security or Compatibility.\n' >&2
    printf 'Runtime-affecting changed paths:\n' >&2
    printf ' - %s\n' "${runtime_files[@]}" >&2
    exit 1
fi

printf 'Release readiness passed: valid Unreleased entry covers runtime changes.\n'
