#!/usr/bin/env bash

validate_release_tag() {
    [[ "${1:-}" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]
}

validate_artifact_ref() {
    [[ "${1:-}" =~ ^[A-Za-z0-9][A-Za-z0-9._/-]{0,127}$ && "${1: -1}" != '/' ]]
}

artifact_manifest_value() {
    local key="$1"
    local manifest="$2"
    awk -F= -v wanted="$key" '$1 == wanted {value = substr($0, index($0, "=") + 1)} END {print value}' "$manifest"
}

validate_artifact_manifest() {
    local manifest="$1"
    local jar="$2"
    local expected_sha actual_sha commit release_ref

    [[ -f "$manifest" && ! -L "$manifest" && -f "$jar" && ! -L "$jar" ]] || return 1
    [[ "$(stat -c '%a' "$manifest" 2>/dev/null || stat -f '%Lp' "$manifest")" == 600 ]] || return 1
    release_ref="$(artifact_manifest_value release_ref "$manifest")"
    commit="$(artifact_manifest_value commit "$manifest")"
    expected_sha="$(artifact_manifest_value sha256 "$manifest")"
    validate_artifact_ref "$release_ref" || return 1
    [[ "$commit" =~ ^[0-9a-f]{40}$ ]] || return 1
    [[ "$expected_sha" =~ ^[0-9a-f]{64}$ ]] || return 1
    actual_sha="$(sha256sum "$jar" | awk '{print $1}')"
    [[ "$actual_sha" == "$expected_sha" ]]
}

install_release_artifact() {
    local release_ref="$1"
    local jar="$2"
    local manifest="$3"
    local release_root="${BYTEDEPTH_RELEASE_ROOT:-/opt/bytedepth/releases}"
    local release_dir="$release_root/$release_ref"

    [[ "${EUID}" -eq 0 ]] || { printf 'Artifact installation requires root.\n' >&2; return 1; }
    validate_artifact_ref "$release_ref" || { printf 'Invalid release reference.\n' >&2; return 1; }
    validate_artifact_manifest "$manifest" "$jar" || { printf 'Invalid artifact manifest.\n' >&2; return 1; }
    install -d -o root -g root -m 0755 "$release_dir"
    install -o root -g root -m 0644 "$jar" "$release_dir/app.jar"
    install -o root -g root -m 0600 "$manifest" "$release_dir/artifact.manifest"
}

switch_current_release() {
    local release_ref="$1"
    local release_root="${BYTEDEPTH_RELEASE_ROOT:-/opt/bytedepth/releases}"
    local current_link="${BYTEDEPTH_CURRENT_LINK:-/opt/bytedepth/current}"
    local release_dir="$release_root/$release_ref"
    local temporary_link="${current_link}.new.$$"

    [[ -d "$release_dir" && -f "$release_dir/app.jar" ]] || return 1
    ln -s "$release_dir" "$temporary_link"
    mv -Tf "$temporary_link" "$current_link"
}

current_release_path() {
    local current_link="${BYTEDEPTH_CURRENT_LINK:-/opt/bytedepth/current}"
    readlink -f -- "$current_link" 2>/dev/null || true
}

restore_current_release() {
    local previous_path="$1"
    local current_link="${BYTEDEPTH_CURRENT_LINK:-/opt/bytedepth/current}"
    local temporary_link="${current_link}.rollback.$$"

    [[ -d "$previous_path" && -f "$previous_path/app.jar" && ! -L "$previous_path/app.jar" ]] || return 1
    ln -s "$previous_path" "$temporary_link"
    mv -Tf "$temporary_link" "$current_link"
}

verify_running_release() {
    local expected_commit="$1"
    local base_url="${BYTEDEPTH_HEALTH_URL:-http://127.0.0.1:8080}"
    systemctl is-active --quiet bytedepth-app.service || return 1
    curl --fail --silent --show-error --retry 12 --retry-delay 2 --retry-connrefused \
        --connect-timeout 5 "$base_url/version" | grep -F "$expected_commit" >/dev/null
}

build_release_artifact() {
    local source_root="$1"
    local release_ref="$2"
    local commit="$3"
    local output_dir="$4"
    local build_log jar built_at sha version build_properties

    validate_artifact_ref "$release_ref" || return 1
    [[ "$commit" =~ ^[0-9a-f]{40}$ ]] || return 1
    install -d -m 0700 "$output_dir"
    build_properties="$source_root/bytedepth-start/src/main/resources/bytedepth-build.properties"
    version="$(sed -n 's@^[[:space:]]*<version>\([^<]*\)</version>[[:space:]]*$@\1@p' "$source_root/pom.xml" | head -n 1)"
    install -d "$(dirname "$build_properties")"
    printf 'version=%s\ncommitId=%s\nbuiltAt=%s\n' "$version" "$commit" "${BYTEDEPTH_BUILT_AT:-$(date -u +%FT%TZ)}" > "$build_properties"
    build_log="$(mktemp)"
    # RETURN runs after the function-local scope has ended under some bash
    # versions. Keep cleanup safe with nounset enabled.
    trap '[[ -z "${build_log:-}" ]] || rm -f -- "$build_log"' RETURN
    (
        cd "$source_root" || return 1
        ./mvnw clean install -DskipTests -Dsort.skip=true
        ./mvnw verify -DskipTests -Dsort.skip=true
    ) 2>&1 | tee "$build_log"
    if declare -F warning_policy_check_file >/dev/null 2>&1; then
        warning_policy_check_file "$build_log"
    elif rg -n -i '(^|[^A-Za-z])WARN(ING)?([^A-Za-z]|$)' "$build_log" >/dev/null; then
        printf 'Artifact build emitted WARNING.\n' >&2
        return 1
    fi
    jar="$(find "$source_root/bytedepth-start/target" -maxdepth 1 -type f -name '*.jar' ! -name '*original*' -print | sort | head -n 1)"
    [[ -n "$jar" && -f "$jar" ]] || { printf 'Release JAR was not produced.\n' >&2; return 1; }
    install -m 0644 "$jar" "$output_dir/app.jar"
    built_at="$(date -u +%FT%TZ)"
    sha="$(sha256sum "$output_dir/app.jar" | awk '{print $1}')"
    printf 'release_ref=%s\ncommit=%s\nbuilt_at=%s\nsha256=%s\n' \
        "$release_ref" "$commit" "$built_at" "$sha" > "$output_dir/artifact.manifest"
    chmod 0600 "$output_dir/artifact.manifest"
}
