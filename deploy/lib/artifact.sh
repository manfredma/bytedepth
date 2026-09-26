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
    local expected_sha actual_sha commit release_ref application_version

    [[ -f "$manifest" && ! -L "$manifest" && -f "$jar" && ! -L "$jar" ]] || return 1
    [[ "$(stat -c '%a' "$manifest" 2>/dev/null || stat -f '%Lp' "$manifest")" == 600 ]] || return 1
    release_ref="$(artifact_manifest_value release_ref "$manifest")"
    commit="$(artifact_manifest_value commit "$manifest")"
    application_version="$(artifact_manifest_value application_version "$manifest")"
    expected_sha="$(artifact_manifest_value sha256 "$manifest")"
    validate_artifact_ref "$release_ref" || return 1
    [[ "$commit" =~ ^[0-9a-f]{40}$ ]] || return 1
    [[ "$application_version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || return 1
    if validate_release_tag "$release_ref"; then
        [[ "$application_version" == "${release_ref#v}" ]] || return 1
    fi
    [[ "$expected_sha" =~ ^[0-9a-f]{64}$ ]] || return 1
    actual_sha="$(sha256sum "$jar" | awk '{print $1}')"
    [[ "$actual_sha" == "$expected_sha" ]]
}

validate_rollback_manifest() {
    local manifest="$1"
    local jar="$2"
    local expected_ref="$3"
    local expected_sha actual_sha commit release_ref application_version
    [[ -f "$manifest" && ! -L "$manifest" && -f "$jar" && ! -L "$jar" ]] || return 1
    [[ "$(stat -c '%a' "$manifest" 2>/dev/null || stat -f '%Lp' "$manifest")" == 600 ]] || return 1
    validate_release_tag "$expected_ref" || return 1
    [[ "$(basename "$(dirname "$manifest")")" == "$expected_ref" ]] || return 1
    release_ref="$(artifact_manifest_value release_ref "$manifest")"
    commit="$(artifact_manifest_value commit "$manifest")"
    application_version="$(artifact_manifest_value application_version "$manifest")"
    expected_sha="$(artifact_manifest_value sha256 "$manifest")"
    [[ "$release_ref" == "$expected_ref" ]] || return 1
    [[ "$commit" =~ ^[0-9a-f]{40}$ ]] || return 1
    [[ -z "$application_version" || "$application_version" == "${expected_ref#v}" ]] || return 1
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
    install -d -o ubuntu -g ubuntu -m 0755 "$release_dir" || return 1
    install -o ubuntu -g ubuntu -m 0644 "$jar" "$release_dir/app.jar" || return 1
    install -o ubuntu -g ubuntu -m 0600 "$manifest" "$release_dir/artifact.manifest" || return 1
}

switch_current_release() {
    local release_ref="$1"
    local release_root="${BYTEDEPTH_RELEASE_ROOT:-/opt/bytedepth/releases}"
    local current_link="${BYTEDEPTH_CURRENT_LINK:-/opt/bytedepth/current}"
    local release_dir="$release_root/$release_ref"
    local temporary_link="${current_link}.new.$$"

    [[ -d "$release_dir" && -f "$release_dir/app.jar" && "$release_dir" != "$current_link" ]] || return 1
    ln -s "$release_dir" "$temporary_link"
    mv -Tf "$temporary_link" "$current_link"
    chown -h ubuntu:ubuntu "$current_link"
    [[ "$(readlink -- "$current_link")" == "$release_dir" ]] || {
        printf 'Refusing: current release link does not target the requested release.\n' >&2
        return 1
    }
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
    chown -h ubuntu:ubuntu "$current_link"
}

verify_running_release() {
    local expected_commit="$1"
    local expected_version="${2:-}"
    local base_url="${BYTEDEPTH_HEALTH_URL:-http://127.0.0.1:8080}"
    local app_service="${BYTEDEPTH_APP_SERVICE:-bytedepth-app.service}"
    [[ "$expected_commit" =~ ^[0-9a-f]{40}$ ]] || return 1
    [[ "$expected_version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || return 1
    systemctl is-active --quiet "$app_service" || return 1
    curl --fail --silent --show-error --retry 12 --retry-delay 2 --retry-connrefused \
        --connect-timeout 5 "$base_url/version" \
        | jq --exit-status --arg expected_commit "$expected_commit" --arg expected_version "$expected_version" \
            '.commitId == $expected_commit and .version == $expected_version' >/dev/null
}

build_release_artifact() {
    local source_root="$1"
    local release_ref="$2"
    local commit="$3"
    local output_dir="$4"
    local application_version="${5:-}"
    local build_log jar built_at sha pom_version changelog_version build_properties maven_status candidate java_25_home

    validate_artifact_ref "$release_ref" || return 1
    [[ "$commit" =~ ^[0-9a-f]{40}$ ]] || return 1
    [[ "$application_version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || {
        printf 'A frozen application version is required to build the release artifact.\n' >&2
        return 1
    }
    # shellcheck disable=SC1091
    source "$source_root/scripts/lib/java-25.sh"
    java_25_home="$(resolve_java_25)"
    [[ -n "$java_25_home" && -x "$java_25_home/bin/java" ]] || {
        printf 'Java 25 is required to build a release artifact.\n' >&2
        return 1
    }
    install -d -m 0700 "$output_dir"
    build_properties="$source_root/bytedepth-start/src/main/resources/bytedepth-build.properties"
    pom_version="$(awk '
        /^[[:space:]]*<version>[^<]*<\/version>[[:space:]]*$/ {
            line = $0
            sub(/^[[:space:]]*<version>/, "", line)
            sub(/<\/version>[[:space:]]*$/, "", line)
            print line
            exit
        }
    ' "$source_root/pom.xml")"
    if validate_release_tag "$release_ref"; then
        [[ "$application_version" == "${release_ref#v}" && "$pom_version" == "$application_version" ]] || {
            printf 'Refusing: production artifact version, Tag, and POM version must match.\n' >&2
            return 1
        }
    else
        changelog_version="$(awk '
            /^## \[v[0-9]+\.[0-9]+\.[0-9]+\] - [0-9]{4}-[0-9]{2}-[0-9]{2}[[:space:]]*$/ {
                sub(/^## \[v/, "")
                sub(/\].*$/, "")
                print
                exit
            }
        ' "$source_root/docs/releases/CHANGELOG.md")"
        [[ "$pom_version" == *-SNAPSHOT && "$application_version" == "$changelog_version" ]] || {
            printf 'Refusing: staging artifact version must match the frozen Changelog and the source POM must remain a SNAPSHOT.\n' >&2
            return 1
        }
    fi
    install -d "$(dirname "$build_properties")"
    printf 'version=%s\ncommitId=%s\nbuiltAt=%s\n' "$application_version" "$commit" "${BYTEDEPTH_BUILT_AT:-$(date -u +%FT%TZ)}" > "$build_properties"
    build_log="$(mktemp)"
    # RETURN runs after the function-local scope has ended under some bash
    # versions. Keep cleanup safe with nounset enabled.
    trap '[[ -z "${build_log:-}" ]] || rm -f -- "$build_log"' RETURN
    set +e
    (
        cd "$source_root" || return 1
        JAVA_HOME="$java_25_home" ./mvnw clean install -DskipTests -Dsort.skip=true
        JAVA_HOME="$java_25_home" ./mvnw verify -DskipTests -Dsort.skip=true
    ) 2>&1 | tee "$build_log"
    maven_status="${PIPESTATUS[0]}"
    set -e
    [[ "$maven_status" -eq 0 ]] || {
        printf 'Release artifact Maven build failed with status %s.\n' "$maven_status" >&2
        return "$maven_status"
    }
    if declare -F warning_policy_check_file >/dev/null 2>&1; then
        warning_policy_check_file "$build_log"
    elif rg -n -i '(^|[^A-Za-z])WARN(ING)?([^A-Za-z]|$)' "$build_log" >/dev/null; then
        printf 'Artifact build emitted WARNING.\n' >&2
        return 1
    fi
    jar=''
    for candidate in "$source_root/bytedepth-start/target"/*.jar; do
        [[ -f "$candidate" && "$candidate" != *original* ]] || continue
        jar="$candidate"
        break
    done
    [[ -n "$jar" && -f "$jar" ]] || { printf 'Release JAR was not produced.\n' >&2; return 1; }
    install -m 0644 "$jar" "$output_dir/app.jar"
    built_at="$(date -u +%FT%TZ)"
    sha="$(sha256sum "$output_dir/app.jar" | awk '{print $1}')"
    printf 'release_ref=%s\ncommit=%s\nbuilt_at=%s\napplication_version=%s\nsha256=%s\n' \
        "$release_ref" "$commit" "$built_at" "$application_version" "$sha" > "$output_dir/artifact.manifest"
    chmod 0600 "$output_dir/artifact.manifest"
}
