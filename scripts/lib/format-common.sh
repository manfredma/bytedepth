#!/usr/bin/env bash

FORMAT_SUPPORTED_EXTENSIONS='java|js|jsx|ts|tsx|html|css|scss|json|yml|yaml|md|sh'

# Shared local formatter helpers: file collection, ignore rules, formatter
# discovery, and mode-preserving replacement. No Git or remote side effects.

format_file_mode() {
  local file="$1"
  if stat -f '%Lp' "$file" > /dev/null 2>&1; then
    stat -f '%Lp' "$file"
  else
    stat -c '%a' "$file"
  fi
}

format_replace_file() {
  local temporary="$1"
  local destination="$2"
  local mode
  mode="$(format_file_mode "$destination")"
  mv "$temporary" "$destination"
  chmod "$mode" "$destination"
}

format_usage() {
  cat << 'EOF'
Usage:
  scripts/format-code.sh --all
  scripts/format-code.sh --changed
  scripts/format-code.sh PATH [PATH...]

The check-only entry point accepts the same scope arguments:
  scripts/format-check.sh --all
  scripts/format-check.sh --changed
  scripts/format-check.sh PATH [PATH...]
EOF
}

format_is_ignored() {
  local path="$1"
  local relative_path
  relative_path="${path#"$SOURCE_ROOT/"}"

  [[ "$relative_path" == package-lock.json ]] && return 0
  [[ "$relative_path" == pnpm-lock.yaml ]] && return 0
  [[ "$relative_path" == yarn.lock ]] && return 0
  [[ "$relative_path" == *.min.js ]] && return 0
  [[ "$relative_path" == *.min.css ]] && return 0
  [[ "$relative_path" == *.map ]] && return 0
  [[ "$relative_path" == *.svg ]] && return 0

  case "/$relative_path/" in
    */node_modules/* | */target/* | */build/* | */dist/* | */coverage/* | */generated/* | */vendor/*)
      return 0
      ;;
  esac

  return 1
}

format_is_supported() {
  local path="$1"
  local extension="${path##*.}"
  [[ "$path" == *.* ]] && [[ "$extension" =~ ^($FORMAT_SUPPORTED_EXTENSIONS)$ ]]
}

format_add_file() {
  local candidate="$1"
  [[ -f "$candidate" ]] || return 0

  local absolute_path
  absolute_path="$(cd "$(dirname "$candidate")" && pwd -P)/$(basename "$candidate")"
  format_is_ignored "$absolute_path" && return 0
  format_is_supported "$absolute_path" || return 0

  FORMAT_FILES+=("$absolute_path")
}

format_add_tree() {
  local root="$1"
  while IFS= read -r -d '' file; do
    format_add_file "$file"
  done < <(
    find "$root" -type d \( -name node_modules -o -name target -o -name build -o -name dist -o -name coverage -o -name generated -o -name vendor \) -prune -o -type f -print0
  )
}

format_collect_changed() {
  while IFS= read -r file; do
    [[ -n "$file" ]] && format_add_file "$SOURCE_ROOT/$file"
  done < <(
    {
      git -C "$SOURCE_ROOT" diff --name-only --diff-filter=ACMR HEAD
      git -C "$SOURCE_ROOT" ls-files --others --exclude-standard
    } | sort -u
  )
}

format_collect_scope() {
  FORMAT_FILES=()
  local scope="$1"
  shift

  case "$scope" in
    all)
      format_add_tree "$SOURCE_ROOT"
      ;;
    changed)
      format_collect_changed
      ;;
    paths)
      local path
      for path in "$@"; do
        if [[ "$path" != /* ]]; then
          path="$SOURCE_ROOT/$path"
        fi
        if [[ -d "$path" ]]; then
          format_add_tree "$path"
        else
          format_add_file "$path"
        fi
      done
      ;;
    *)
      printf 'Unknown formatting scope: %s\n' "$scope" >&2
      return 2
      ;;
  esac

  if ((${#FORMAT_FILES[@]} > 0)); then
    mapfile -t FORMAT_FILES < <(printf '%s\n' "${FORMAT_FILES[@]}" | sort -u)
  fi
}

format_require_prettier() {
  PRETTIER_BIN="$SOURCE_ROOT/node_modules/.bin/prettier"
  if [[ ! -x "$PRETTIER_BIN" ]]; then
    printf 'Prettier is unavailable. Run npm ci --ignore-scripts --no-audit --no-fund first.\n' >&2
    return 1
  fi
}

format_shfmt_binary() {
  local version='3.11.0'
  local os arch asset expected cache_root binary checksum_file actual
  os="$(uname -s | tr '[:upper:]' '[:lower:]')"
  arch="$(uname -m)"
  case "$os:$arch" in
    darwin:x86_64)
      asset="shfmt_v${version}_darwin_amd64"
      expected='810a76cb7c78351e021c8025f344b12149d8426ce51609a179af68109ed5698e'
      ;;
    darwin:arm64)
      asset="shfmt_v${version}_darwin_arm64"
      expected='af206d234dff5d05d9ac355529b2b33a7a78e13fab9b59db777746aab3e72530'
      ;;
    linux:x86_64)
      asset="shfmt_v${version}_linux_amd64"
      expected='1904ec6bac715c1d05cd7f6612eec8f67a625c3749cb327e5bfb4127d09035ff'
      ;;
    linux:aarch64 | linux:arm64)
      asset="shfmt_v${version}_linux_arm64"
      expected='b3976121710fd4b12bf641b0a7fb2686da598fb0da9f148c641b61b54cfa3407'
      ;;
    *)
      printf 'Unsupported shfmt platform: %s/%s\n' "$os" "$arch" >&2
      return 1
      ;;
  esac

  cache_root="${FORMATTER_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/project-formatters}/shfmt/$version/$os-$arch"
  binary="$cache_root/$asset"
  if [[ -x "$binary" ]]; then
    printf '%s\n' "$binary"
    return 0
  fi

  command -v curl > /dev/null 2>&1 || {
    printf 'curl is required to download shfmt %s.\n' "$version" >&2
    return 1
  }
  mkdir -p "$cache_root"
  checksum_file="$cache_root/$asset.sha256"
  curl --fail --silent --show-error --location \
    "https://github.com/mvdan/sh/releases/download/v${version}/${asset}" \
    --output "$binary"
  if command -v shasum > /dev/null 2>&1; then
    actual="$(shasum -a 256 "$binary" | awk '{print $1}')"
  elif command -v sha256sum > /dev/null 2>&1; then
    actual="$(sha256sum "$binary" | awk '{print $1}')"
  else
    rm -f "$binary" "$checksum_file"
    printf 'shasum or sha256sum is required to verify shfmt.\n' >&2
    return 1
  fi
  if [[ "$actual" != "$expected" ]]; then
    rm -f "$binary" "$checksum_file"
    printf 'shfmt checksum mismatch for %s.\n' "$asset" >&2
    return 1
  fi
  printf '%s  %s\n' "$expected" "$binary" > "$checksum_file"
  chmod +x "$binary"
  printf '%s\n' "$binary"
}
