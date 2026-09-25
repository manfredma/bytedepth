#!/usr/bin/env bash

readonly SHARED_CHROMIUM_EXECUTABLE=/opt/shared-e2e/chrome-linux64/chrome

staging_runtime_sha256() {
    shasum -a 256 "$1" | awk '{print $1}'
}

staging_ensure_ubuntu_owner() {
    if [[ "${EUID:-}" -eq 0 ]] && getent passwd ubuntu >/dev/null 2>&1 && getent group ubuntu >/dev/null 2>&1; then
        chown ubuntu:ubuntu "$@"
    fi
}

# Deployment must be able to move to a new checkout before bootstrap writes its
# dependency-bound manifest. Keep this preflight limited to immutable shared
# infrastructure; require_staging_runtime verifies the prepared dependencies.
require_staging_runtime_prerequisites() {
    if [[ ! -x "$SHARED_CHROMIUM_EXECUTABLE" ]]; then
        printf 'Refusing: staging Chromium executable is unavailable.\n' >&2
        return 1
    fi
}

write_runtime_manifest() {
    local manifest="$1"
    local source_root="$2"
    local runtime_directory
    local temporary_file
    local lockfile_sha
    local pom_sha
    local chromium_version

    runtime_directory="$(dirname "$manifest")"
    lockfile_sha="$(staging_runtime_sha256 "$source_root/package-lock.json")"
    pom_sha="$(staging_runtime_sha256 "$source_root/pom.xml")"
    if [[ ! -x "$SHARED_CHROMIUM_EXECUTABLE" ]]; then
        printf 'Refusing: staging Chromium executable is unavailable.\n' >&2
        return 1
    fi
    chromium_version="$("$SHARED_CHROMIUM_EXECUTABLE" --version)"

    if [[ "${EUID:-}" -eq 0 ]] && getent passwd ubuntu >/dev/null 2>&1 && getent group ubuntu >/dev/null 2>&1; then
        install -d -o ubuntu -g ubuntu -m 0700 "$runtime_directory"
    else
        install -d -m 0700 "$runtime_directory"
    fi
    temporary_file="$(mktemp "$runtime_directory/.manifest.XXXXXX")"
    staging_ensure_ubuntu_owner "$temporary_file"
    printf 'package_lock_sha256=%s\npom_sha256=%s\nchromium_version=%s\n' \
        "$lockfile_sha" "$pom_sha" "$chromium_version" > "$temporary_file"
    staging_ensure_ubuntu_owner "$runtime_directory" "$temporary_file"
    chmod 0600 "$temporary_file"
    mv -f "$temporary_file" "$manifest"
}

require_staging_runtime() {
    local manifest="$1"
    local source_root="$2"
    local expected_lock_sha
    local expected_pom_sha
    local actual_lock_sha
    local actual_pom_sha
    local actual_chromium_version

    if [[ ! -f "$manifest" || -L "$manifest" ]]; then
        printf 'Refusing: staging runtime manifest is missing. Run bootstrap-staging-runtime.sh.\n' >&2
        return 1
    fi
    actual_lock_sha="$(awk -F= '$1 == "package_lock_sha256" {print $2; exit}' "$manifest")"
    actual_pom_sha="$(awk -F= '$1 == "pom_sha256" {print $2; exit}' "$manifest")"
    actual_chromium_version="$(awk -F= '$1 == "chromium_version" {print $2; exit}' "$manifest")"
    expected_lock_sha="$(staging_runtime_sha256 "$source_root/package-lock.json")"
    expected_pom_sha="$(staging_runtime_sha256 "$source_root/pom.xml")"

    if [[ "$actual_lock_sha" != "$expected_lock_sha" || "$actual_pom_sha" != "$expected_pom_sha" ]]; then
        printf 'Refusing: staging runtime manifest does not match dependency inputs. Run bootstrap-staging-runtime.sh.\n' >&2
        return 1
    fi
    if [[ ! -x "$SHARED_CHROMIUM_EXECUTABLE" || "$actual_chromium_version" != "$("$SHARED_CHROMIUM_EXECUTABLE" --version)" ]]; then
        printf 'Refusing: manifest Chromium executable changed. Run bootstrap-staging-runtime.sh.\n' >&2
        return 1
    fi
}
