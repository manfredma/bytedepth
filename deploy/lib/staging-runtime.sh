#!/usr/bin/env bash

staging_runtime_sha256() {
    shasum -a 256 "$1" | awk '{print $1}'
}

write_runtime_manifest() {
    local manifest="$1"
    local source_root="$2"
    local commit="$3"
    local runtime_directory
    local temporary_file
    local lockfile_sha
    local pom_sha
    local chromium_path
    local chromium_sha

    runtime_directory="$(dirname "$manifest")"
    lockfile_sha="$(staging_runtime_sha256 "$source_root/package-lock.json")"
    pom_sha="$(staging_runtime_sha256 "$source_root/pom.xml")"
    chromium_path="$(find "$source_root/.e2e" -type f -name chrome -perm -u+x -print -quit 2>/dev/null || true)"
    if [[ -z "$chromium_path" || ! -x "$chromium_path" ]]; then
        printf 'Refusing: staging Chromium executable is unavailable.\n' >&2
        return 1
    fi
    chromium_sha="$(staging_runtime_sha256 "$chromium_path")"

    install -d -m 0700 "$runtime_directory"
    temporary_file="$(mktemp "$runtime_directory/.manifest.XXXXXX")"
    printf 'commit=%s\npackage_lock_sha256=%s\npom_sha256=%s\nchromium_path=%s\nchromium_sha256=%s\n' \
        "$commit" "$lockfile_sha" "$pom_sha" "$chromium_path" "$chromium_sha" > "$temporary_file"
    if [[ "${EUID}" -eq 0 ]]; then
        chown root:root "$runtime_directory" "$temporary_file"
    fi
    chmod 0600 "$temporary_file"
    mv -f "$temporary_file" "$manifest"
}

require_staging_runtime() {
    local manifest="$1"
    local source_root="$2"
    local expected_lock_sha
    local expected_pom_sha
    local expected_chromium_path
    local expected_chromium_sha
    local actual_commit
    local actual_lock_sha
    local actual_pom_sha
    local actual_chromium_path
    local actual_chromium_sha

    if [[ ! -f "$manifest" || -L "$manifest" ]]; then
        printf 'Refusing: staging runtime manifest is missing. Run bootstrap-staging-runtime.sh.\n' >&2
        return 1
    fi
    actual_lock_sha="$(awk -F= '$1 == "package_lock_sha256" {print $2; exit}' "$manifest")"
    actual_pom_sha="$(awk -F= '$1 == "pom_sha256" {print $2; exit}' "$manifest")"
    actual_chromium_path="$(awk -F= '$1 == "chromium_path" {print $2; exit}' "$manifest")"
    actual_chromium_sha="$(awk -F= '$1 == "chromium_sha256" {print $2; exit}' "$manifest")"
    expected_lock_sha="$(staging_runtime_sha256 "$source_root/package-lock.json")"
    expected_pom_sha="$(staging_runtime_sha256 "$source_root/pom.xml")"

    if [[ "$actual_lock_sha" != "$expected_lock_sha" || "$actual_pom_sha" != "$expected_pom_sha" ]]; then
        printf 'Refusing: staging runtime manifest does not match this checkout. Run bootstrap-staging-runtime.sh.\n' >&2
        return 1
    fi
    if [[ -z "$actual_chromium_path" || ! -x "$actual_chromium_path" ]]; then
        printf 'Refusing: manifest Chromium executable is unavailable.\n' >&2
        return 1
    fi
    expected_chromium_sha="$(staging_runtime_sha256 "$actual_chromium_path")"
    if [[ "$actual_chromium_sha" != "$expected_chromium_sha" ]]; then
        printf 'Refusing: manifest Chromium executable changed. Run bootstrap-staging-runtime.sh.\n' >&2
        return 1
    fi
}
