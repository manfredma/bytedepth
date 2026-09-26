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
EXPECTED_RELEASE_VERSION=''

usage() {
    printf 'Usage: %s [--target REF] [--base REF] [--mode candidate|frozen-candidate|release] [--expected-release X.Y.Z]\n' "$0" >&2
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
        --expected-release)
            [[ $# -ge 2 ]] || usage
            EXPECTED_RELEASE_VERSION="$2"
            shift 2
            ;;
        *)
            usage
            ;;
    esac
done

[[ "$MODE" == candidate || "$MODE" == frozen-candidate || "$MODE" == release ]] || usage
if [[ -n "$EXPECTED_RELEASE_VERSION" && ! "$EXPECTED_RELEASE_VERSION" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
    usage
fi
if [[ "$MODE" == release && -z "$EXPECTED_RELEASE_VERSION" ]]; then
    printf 'Release readiness refused: --expected-release is required in release mode.\n' >&2
    exit 2
fi

cd "$REPOSITORY_ROOT"
CHANGELOG_FILE="$REPOSITORY_ROOT/docs/releases/CHANGELOG.md"
TEMP_CHANGELOG=''
BASE_CHANGELOG_TEMP=''
trap 'if [[ -n "$TEMP_CHANGELOG" ]]; then rm -f "$TEMP_CHANGELOG"; fi; if [[ -n "$BASE_CHANGELOG_TEMP" ]]; then rm -f "$BASE_CHANGELOG_TEMP"; fi' EXIT

check_frozen_changelog() {
    local baseline_version="$1"
    local baseline_tag="$2"

    awk -v baseline_version="$baseline_version" -v baseline_tag="$baseline_tag" \
        -v expected_release="$EXPECTED_RELEASE_VERSION" '
        function version_is_newer(current, previous, current_parts, previous_parts, i) {
            split(current, current_parts, ".")
            split(previous, previous_parts, ".")
            for (i = 1; i <= 3; i++) {
                if ((current_parts[i] + 0) > (previous_parts[i] + 0)) return 1
                if ((current_parts[i] + 0) < (previous_parts[i] + 0)) return 0
            }
            return 0
        }
        /^## Unreleased[[:space:]]*$/ { in_unreleased = 1; saw_unreleased = 1; next }
        in_unreleased && /^## / { in_unreleased = 0 }
        in_unreleased && /^[[:space:]]*-[[:space:]]+/ { stale_items = 1 }
        !saw_unreleased { next }
        !in_unreleased && !saw_release && /^## \[v[0-9]+\.[0-9]+\.[0-9]+\] - [0-9]{4}-[0-9]{2}-[0-9]{2}[[:space:]]*$/ {
            release_version = $0
            sub(/^## \[v/, "", release_version)
            sub(/\].*$/, "", release_version)
            in_release = 1
            saw_release = 1
            next
        }
        saw_release && /^## / { in_release = 0 }
        in_release && /^### (Added|Changed|Deprecated|Removed|Fixed|Security|Compatibility)[[:space:]]*$/ { has_category = 1 }
        in_release && /^[[:space:]]*-[[:space:]]+/ { has_item = 1 }
        in_release && /^\*\*Tag\*\*[：:]/ {
            if (match($0, /`v[0-9]+\.[0-9]+\.[0-9]+`/)) {
                candidate_tag = substr($0, RSTART + 1, RLENGTH - 2)
            }
        }
        in_release && /^\*\*回滚基线\*\*[：:]/ {
            if (match($0, /`v[0-9]+\.[0-9]+\.[0-9]+`/)) {
                candidate_rollback = substr($0, RSTART + 1, RLENGTH - 2)
            }
        }
        END {
            valid = saw_unreleased && !stale_items && saw_release && has_category && has_item
            valid = valid && release_version != "" && version_is_newer(release_version, baseline_version)
            valid = valid && candidate_tag == ("v" release_version) && candidate_rollback == baseline_tag
            if (expected_release != "") valid = valid && release_version == expected_release
            exit !valid
        }
    ' "$CHANGELOG_FILE"
}

first_release_field() {
    local changelog_path="$1"
    local field_name="$2"

    awk -v field_name="$field_name" '
        /^## \[v[0-9]+\.[0-9]+\.[0-9]+\] - [0-9]{4}-[0-9]{2}-[0-9]{2}[[:space:]]*$/ {
            if (saw_release) exit
            saw_release = 1
            in_release = 1
            next
        }
        saw_release && /^## / { exit }
        in_release && $0 ~ "^\\*\\*" field_name "\\*\\*[：:]" {
            if (match($0, /`v[0-9]+\.[0-9]+\.[0-9]+`/)) {
                print substr($0, RSTART + 1, RLENGTH - 2)
                exit
            }
        }
    ' "$changelog_path"
}

first_release_version() {
    local changelog_path="$1"

    awk '
        /^## \[v[0-9]+\.[0-9]+\.[0-9]+\] - [0-9]{4}-[0-9]{2}-[0-9]{2}[[:space:]]*$/ {
            version = $0
            sub(/^## \[v/, "", version)
            sub(/\].*$/, "", version)
            print version
            exit
        }
    ' "$changelog_path"
}

latest_stable_tag_for_commit() {
    local commit="$1"

    git tag --merged "$commit" --sort=-version:refname \
        | awk '/^v[0-9]+\.[0-9]+\.[0-9]+$/ && !found { print; found = 1 }'
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

BASE_RELEASE_VERSION=''
BASE_RELEASE_TAG=''
if [[ "$MODE" == release ]]; then
    BASE_RELEASE_TAG="$(latest_stable_tag_for_commit "$BASE_COMMIT")"
    if [[ -z "$BASE_RELEASE_TAG" ]]; then
        printf 'Release readiness refused: no prior stable release Tag is reachable from %s.\n' "$BASE_REF" >&2
        exit 1
    fi
    BASE_RELEASE_VERSION="${BASE_RELEASE_TAG#v}"
else
    BASE_CHANGELOG_TEMP="$(mktemp)"
    if ! git show "$BASE_COMMIT:docs/releases/CHANGELOG.md" > "$BASE_CHANGELOG_TEMP"; then
        printf 'Release readiness refused: base %s has no docs/releases/CHANGELOG.md.\n' "$BASE_REF" >&2
        exit 1
    fi
    BASE_RELEASE_VERSION="$(first_release_version "$BASE_CHANGELOG_TEMP")"
    BASE_RELEASE_TAG="$(first_release_field "$BASE_CHANGELOG_TEMP" Tag)"
    if [[ -n "$BASE_RELEASE_VERSION" && "$BASE_RELEASE_TAG" != "v$BASE_RELEASE_VERSION" ]]; then
        printf 'Release readiness refused: base Changelog latest version and Tag do not match.\n' >&2
        exit 1
    fi
fi

[[ -f "$CHANGELOG_FILE" ]] || {
    printf 'Release readiness refused: missing %s.\n' "$CHANGELOG_FILE" >&2
    exit 1
}
bash "$REPOSITORY_ROOT/scripts/check-changelog-order.sh" "$CHANGELOG_FILE"

if [[ "$MODE" == frozen-candidate || "$MODE" == release ]]; then
    if ! check_frozen_changelog "$BASE_RELEASE_VERSION" "$BASE_RELEASE_TAG"; then
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
        if ! check_frozen_changelog "$BASE_RELEASE_VERSION" "$BASE_RELEASE_TAG"; then
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
    if ! check_frozen_changelog "$BASE_RELEASE_VERSION" "$BASE_RELEASE_TAG"; then
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
