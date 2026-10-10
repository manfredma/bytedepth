#!/usr/bin/env bash
set -Eeuo pipefail

# Freeze the repository's Unreleased notes into the next SemVer section before
# a candidate is created. This script only edits the changelog; it never tags,
# commits, pushes, or calls release-platform.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHANGELOG="$ROOT_DIR/docs/releases/CHANGELOG.md"
KIND=''
EXPLICIT_VERSION=''
RELEASE_DATE="$(date -u +%F)"

usage() {
  printf 'Usage: %s [--kind minor|patch|major | --version vMAJOR.MINOR.PATCH] [--date YYYY-MM-DD] [--changelog PATH]\n' "$0" >&2
  exit 2
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --kind)
      [[ $# -ge 2 ]] || usage
      KIND="$2"
      shift 2
      ;;
    --patch|--minor|--major)
      KIND="${1#--}"
      shift
      ;;
    --version)
      [[ $# -ge 2 && "$2" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || usage
      EXPLICIT_VERSION="$2"
      shift 2
      ;;
    --date)
      [[ $# -ge 2 && "$2" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || usage
      RELEASE_DATE="$2"
      shift 2
      ;;
    --changelog)
      [[ $# -ge 2 ]] || usage
      CHANGELOG="$2"
      shift 2
      ;;
    -h|--help)
      usage
      ;;
    *)
      usage
      ;;
  esac
done

if [[ -n "$EXPLICIT_VERSION" && -n "$KIND" ]]; then
  printf 'ERROR: --version cannot be combined with --kind/--patch/--minor/--major\n' >&2
  exit 2
fi
[[ -n "$EXPLICIT_VERSION" || -z "$KIND" || "$KIND" == minor || "$KIND" == patch || "$KIND" == major ]] || usage
[[ -f "$CHANGELOG" && ! -L "$CHANGELOG" ]] || {
  printf 'ERROR: changelog is missing or symlinked: %s\n' "$CHANGELOG" >&2
  exit 1
}

first_section="$(awk '/^## / { print; exit }' "$CHANGELOG")"
[[ "$first_section" == '## Unreleased' ]] || {
  printf 'ERROR: changelog must start with ## Unreleased\n' >&2
  exit 1
}

unreleased_entries="$(awk '
  /^## Unreleased$/ { in_unreleased = 1; next }
  in_unreleased && /^## / { exit }
  in_unreleased && /^[[:space:]]*-[[:space:]]+/ { count++ }
  END { print count + 0 }
' "$CHANGELOG")"
[[ "$unreleased_entries" -gt 0 ]] || {
  printf 'ERROR: ## Unreleased must contain at least one classified bullet\n' >&2
  exit 1
}

if [[ -n "$EXPLICIT_VERSION" ]]; then
  next_version="$EXPLICIT_VERSION"
else
  [[ -n "$KIND" ]] || KIND=minor
  latest="$(sed -nE 's/^## \[v([0-9]+)\.([0-9]+)\.([0-9]+)\] - [0-9]{4}-[0-9]{2}-[0-9]{2}[[:space:]]*$/\1 \2 \3/p' "$CHANGELOG" | head -n 1)"
  [[ "$latest" =~ ^([0-9]+)[[:space:]]+([0-9]+)[[:space:]]+([0-9]+)$ ]] || {
    printf 'ERROR: changelog must contain an existing release section, or use --version for initialization\n' >&2
    exit 1
  }
  major="${BASH_REMATCH[1]}"
  minor="${BASH_REMATCH[2]}"
  patch="${BASH_REMATCH[3]}"
  case "$KIND" in
    major) major=$((major + 1)); minor=0; patch=0 ;;
    minor) minor=$((minor + 1)); patch=0 ;;
    patch) patch=$((patch + 1)) ;;
  esac
  next_version="v${major}.${minor}.${patch}"
fi

if grep -Fq "## [$next_version] - " "$CHANGELOG"; then
  printf 'ERROR: changelog already contains release section %s\n' "$next_version" >&2
  exit 1
fi

temporary="$(mktemp "${CHANGELOG}.tmp.XXXXXX")"
trap 'rm -f "$temporary"' EXIT
awk -v version="$next_version" -v release_date="$RELEASE_DATE" '
  /^## Unreleased$/ && !promoted {
    print
    print ""
    print "## [" version "] - " release_date
    promoted = 1
    next
  }
  { print }
' "$CHANGELOG" > "$temporary"
mv "$temporary" "$CHANGELOG"
trap - EXIT

bash "$ROOT_DIR/scripts/check-changelog-order.sh" "$CHANGELOG" > /dev/null
printf 'Prepared release %s in %s; commit this changelog change before creating the candidate.\n' "$next_version" "$CHANGELOG"
