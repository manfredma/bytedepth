#!/usr/bin/env bash
set -Eeuo pipefail

# Local formatter/checker entrypoint for AI Agent worktrees. It may rewrite
# selected files when applying format, but never performs Git or deployment IO.

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
source "$SOURCE_ROOT/scripts/lib/format-common.sh"

MODE='apply'
SCOPE=''
declare -a SCOPE_ARGS=()

while (($# > 0)); do
  case "$1" in
    --check)
      MODE='check'
      shift
      ;;
    --all | --changed)
      [[ -z "$SCOPE" ]] || {
        format_usage >&2
        exit 2
      }
      SCOPE="${1#--}"
      shift
      ;;
    --help | -h)
      format_usage
      exit 0
      ;;
    -*)
      printf 'Unknown option: %s\n' "$1" >&2
      format_usage >&2
      exit 2
      ;;
    *)
      [[ -z "$SCOPE" ]] || {
        format_usage >&2
        exit 2
      }
      SCOPE='paths'
      SCOPE_ARGS=("$@")
      break
      ;;
  esac
done

[[ -n "$SCOPE" ]] || {
  format_usage >&2
  exit 2
}
format_collect_scope "$SCOPE" "${SCOPE_ARGS[@]}"

if ((${#FORMAT_FILES[@]} == 0)); then
  printf 'No supported files selected.\n'
  exit 0
fi

declare -a JAVA_FILES=()
declare -a PRETTIER_FILES=()
declare -a SHELL_FILES=()
for file in "${FORMAT_FILES[@]}"; do
  case "${file##*.}" in
    java) JAVA_FILES+=("$file") ;;
    sh) SHELL_FILES+=("$file") ;;
    *) PRETTIER_FILES+=("$file") ;;
  esac
done

run_prettier() {
  ((${#PRETTIER_FILES[@]} > 0)) || return 0
  format_require_prettier
  local file temporary
  for file in "${PRETTIER_FILES[@]}"; do
    temporary="$(mktemp "${TMPDIR:-/tmp}/bytedepth-format.XXXXXX")"
    trap 'rm -f "$temporary"' RETURN
    "$PRETTIER_BIN" "$file" > "$temporary"
    if [[ "$MODE" == check ]]; then
      if ! cmp -s "$file" "$temporary"; then
        printf '[format-check] Prettier violation: %s\n' "${file#"$SOURCE_ROOT/"}" >&2
        return 1
      fi
    else
      format_replace_file "$temporary" "$file"
    fi
    rm -f "$temporary"
    trap - RETURN
  done
}

run_shfmt() {
  ((${#SHELL_FILES[@]} > 0)) || return 0
  local shfmt file temporary
  shfmt="$(format_shfmt_binary)"
  for file in "${SHELL_FILES[@]}"; do
    temporary="$(mktemp "${TMPDIR:-/tmp}/bytedepth-format.XXXXXX")"
    trap 'rm -f "$temporary"' RETURN
    "$shfmt" -i 2 -ci -bn -sr < "$file" > "$temporary"
    if [[ "$MODE" == check ]]; then
      if ! cmp -s "$file" "$temporary"; then
        printf '[format-check] shfmt violation: %s\n' "${file#"$SOURCE_ROOT/"}" >&2
        return 1
      fi
    else
      format_replace_file "$temporary" "$file"
    fi
    rm -f "$temporary"
    trap - RETURN
  done
}

run_spotless() {
  ((${#JAVA_FILES[@]} > 0)) || return 0
  local joined file
  joined=''
  for file in "${JAVA_FILES[@]}"; do
    [[ -z "$joined" ]] || joined+=','
    joined+="$file"
  done
  local goal='apply'
  [[ "$MODE" == check ]] && goal='check'
  JAVA_HOME="$(source "$SOURCE_ROOT/scripts/lib/java-25.sh" && resolve_java_25)" \
    "$SOURCE_ROOT/mvnw" "com.diffplug.spotless:spotless-maven-plugin:3.10.2:$goal" "-DspotlessFiles=$joined"
}

run_prettier
run_shfmt
run_spotless

printf '[format-%s] %d supported file(s) processed.\n' "$MODE" "${#FORMAT_FILES[@]}"
