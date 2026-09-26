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
    printf 'Usage: %s [--target REF] [--base REF] [--mode candidate|frozen-candidate|release]\n' "$0" >&2
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

[[ "$MODE" == candidate || "$MODE" == frozen-candidate || "$MODE" == release ]] || usage

cd "$REPOSITORY_ROOT"
CHANGELOG_FILE="$REPOSITORY_ROOT/docs/releases/CHANGELOG.md"
TEMP_CHANGELOG=''
trap 'if [[ -n "$TEMP_CHANGELOG" ]]; then rm -f "$TEMP_CHANGELOG"; fi' EXIT

check_frozen_changelog() {
    awk '
        /^## Unreleased[[:space:]]*$/ { in_unreleased = 1; saw_unreleased = 1; next }
        in_unreleased && /^## / { in_unreleased = 0 }
        in_unreleased && /^[[:space:]]*-[[:space:]]+/ { stale_items = 1 }
        !saw_unreleased { next }
        !in_unreleased && !saw_release && /^## \[v[0-9]+\.[0-9]+\.[0-9]+\] - [0-9]{4}-[0-9]{2}-[0-9]{2}[[:space:]]*$/ {
            in_release = 1
            saw_release = 1
            next
        }
        saw_release && /^## / { in_release = 0 }
        in_release && /^### (Added|Changed|Deprecated|Removed|Fixed|Security|Compatibility)[[:space:]]*$/ { has_category = 1 }
        in_release && /^[[:space:]]*-[[:space:]]+/ { has_item = 1 }
        in_release && /^\*\*Tag\*\*[：:]/ { has_tag = 1 }
        in_release && /^\*\*回滚基线\*\*[：:]/ { has_rollback = 1 }
        END { exit !(saw_unreleased && !stale_items && saw_release && has_category && has_item && has_tag && has_rollback) }
    ' "$CHANGELOG_FILE"
}

has_release_heading() {
    grep -Eq '^## \[v[0-9]+\.[0-9]+\.[0-9]+\] - [0-9]{4}-[0-9]{2}-[0-9]{2}[[:space:]]*$' "$CHANGELOG_FILE"
}

unreleased_has_items() {
    awk '
        /^## Unreleased[[:space:]]*$/ { in_unreleased = 1; next }
        in_unreleased && /^## / { exit }
        in_unreleased && /^[[:space:]]*-[[:space:]]+/ { found = 1 }
        END { exit !found }
    ' "$CHANGELOG_FILE"
}

TARGET_COMMIT="$(git rev-parse --verify "$TARGET_REF^{commit}" 2>/dev/null)" || {
    printf 'Release readiness refused: unable to resolve target ref %s.\n' "$TARGET_REF" >&2
    exit 1
}
BASE_COMMIT="$(git rev-parse --verify "$BASE_REF^{commit}" 2>/dev/null)" || {
    printf 'Release readiness refused: unable to resolve base ref %s.\n' "$BASE_REF" >&2
    exit 1
}

if [[ "$TARGET_REF" != HEAD ]]; then
    TEMP_CHANGELOG="$(mktemp)"
    if ! git show "$TARGET_COMMIT:docs/releases/CHANGELOG.md" > "$TEMP_CHANGELOG"; then
        printf 'Release readiness refused: target %s has no docs/releases/CHANGELOG.md.\n' "$TARGET_REF" >&2
        exit 1
    fi
    CHANGELOG_FILE="$TEMP_CHANGELOG"
fi

[[ -f "$CHANGELOG_FILE" ]] || {
    printf 'Release readiness refused: missing %s.\n' "$CHANGELOG_FILE" >&2
    exit 1
}
bash "$REPOSITORY_ROOT/scripts/check-changelog-order.sh" "$CHANGELOG_FILE"

if [[ "$MODE" == frozen-candidate || "$MODE" == release ]]; then
    if ! check_frozen_changelog; then
        printf 'Release readiness refused: frozen Changelog requires an empty ## Unreleased section and a categorized version entry with Tag and rollback baseline.\n' >&2
        exit 1
    fi
    printf 'Release readiness passed: frozen Changelog has no stale Unreleased entries.\n'
    exit 0
fi

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
    if [[ "$MODE" == candidate ]] && ! unreleased_has_items && has_release_heading; then
        if ! check_frozen_changelog; then
            printf 'Release readiness refused: frozen Changelog requires an empty ## Unreleased section and a categorized version entry with Tag and rollback baseline.\n' >&2
            exit 1
        fi
        printf 'Release readiness passed: frozen Changelog has no stale Unreleased entries.\n'
        exit 0
    fi
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

if [[ "$MODE" == frozen-candidate || "$MODE" == release ]] || { ! unreleased_has_items && has_release_heading; }; then
    if ! check_frozen_changelog; then
        printf 'Release readiness refused: frozen Changelog requires an empty ## Unreleased section and a categorized version entry with Tag and rollback baseline.\n' >&2
        exit 1
    fi
    printf 'Release readiness passed: frozen Changelog has no stale Unreleased entries.\n'
    exit 0
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
