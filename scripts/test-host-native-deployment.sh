#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT
readonly ARTIFACT="$ROOT/deploy/lib/artifact.sh"
readonly STAGING="$ROOT/deploy/deploy-staging.sh"
readonly PRODUCTION="$ROOT/deploy/deploy-production.sh"
readonly REMOTE="$ROOT/deploy/deploy-production-remote.sh"
readonly PRODUCTION_GREEN_EDGE_UNIT="$ROOT/deploy/systemd/bytedepth-production-green-edge.service.in"
readonly PRODUCTION_GREEN_PUBLIC_NGINX_UNIT="$ROOT/deploy/systemd/bytedepth-production-green-public-nginx.service.in"
readonly TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT

[[ -f "$ARTIFACT" ]] || { printf 'Missing artifact library.\n' >&2; exit 1; }
for function_name in validate_release_tag validate_artifact_manifest install_release_artifact switch_current_release verify_running_release; do
    rg -q "^${function_name}[[:space:]]*\(\)" "$ARTIFACT" || {
        printf 'Missing artifact interface: %s\n' "$function_name" >&2
        exit 1
    }
done

rg -q 'mvnw clean install -DskipTests -Dsort.skip=true' "$ARTIFACT"
rg -q 'source "\$source_root/scripts/lib/java-25.sh"' "$ARTIFACT"
rg -q 'resolve_java_25' "$ARTIFACT"
rg -q 'JAVA_HOME="\$java_25_home"' "$ARTIFACT"
rg -q 'PIPESTATUS\[0\]' "$ARTIFACT"
rg -q 'Release artifact Maven build failed' "$ARTIFACT"
rg -q 'readlink -- "\$current_link"' "$ARTIFACT"
rg -q 'chown -h ubuntu:ubuntu "\$current_link"' "$ARTIFACT"
rg -q 'sha256sum|shasum -a 256' "$ARTIFACT"
rg -q 'application_version=%s' "$ARTIFACT"
rg -q 'release_ref.*application_version' "$ARTIFACT"
rg -q 'version.*commitId' "$ARTIFACT"
rg -Fq '.version == $expected_version' "$ARTIFACT"
rg -q 'trap .*build_log:-.*\|\| rm -f --.*RETURN' "$ARTIFACT"
if rg -n 'find .*target.*\|[[:space:]]*sort[[:space:]]*\|[[:space:]]*head' "$ARTIFACT" >/dev/null; then
    printf 'Artifact builder must not use a pipefail-unsafe head pipeline.\n' >&2
    exit 1
fi
rg -q 'install_release_artifact|switch_current_release' "$STAGING"
rg -q 'restore_current_release|rollback' "$STAGING"
rg -q 'systemctl restart "\$BYTEDEPTH_STAGING_APP_SERVICE"' "$STAGING"
rg -q 'base_url/version' "$ARTIFACT"
rg -q '^ExecReload=/bin/kill -HUP \$MAINPID$' "$PRODUCTION_GREEN_EDGE_UNIT"
rg -q '^ExecStop=/bin/kill -QUIT \$MAINPID$' "$PRODUCTION_GREEN_EDGE_UNIT"
rg -q '^ExecReload=/bin/kill -HUP \$MAINPID$' "$PRODUCTION_GREEN_PUBLIC_NGINX_UNIT"
rg -q '^ExecStop=/bin/kill -QUIT \$MAINPID$' "$PRODUCTION_GREEN_PUBLIC_NGINX_UNIT"
rg -q 'artifact|app\.jar' "$PRODUCTION"
rg -q 'deploy-production\.sh' "$REMOTE"
rg -q 'UserKnownHostsFile=' "$REMOTE"
rg -q 'StrictHostKeyChecking=yes' "$REMOTE"
rg -q 'DEPLOY_LOCK=/var/lock/bytedepth-production-deploy.lock|flock -n 9' "$PRODUCTION"
rg -q 'restore_current_release|rollback' "$PRODUCTION"
if rg -n 'mysqldump|backup_dir|mysqladmin.*ping' "$PRODUCTION" >/dev/null; then
    printf '普通生产发布不得执行无界全库数据库备份。\n' >&2
    exit 1
fi
if rg -q 'docker|compose|mvn ' "$REMOTE"; then
    printf 'Local native deployment entrypoint must not invoke Docker, Compose, or bare Maven.\n' >&2
    exit 1
fi
if rg -q 'compose|mvn ' "$PRODUCTION"; then
    printf 'Production native deployment must not invoke Compose or bare Maven.\n' >&2
    exit 1
fi
rg -q 'docker stop "\$DOCKER_APP"' "$PRODUCTION"
rg -q 'restore_blue_access' "$PRODUCTION"

jar="$TEMP_ROOT/app.jar"
manifest="$TEMP_ROOT/artifact.manifest"
printf 'test artifact\n' > "$jar"
jar_sha="$(sha256sum "$jar" | awk '{print $1}')"
write_manifest() {
    local ref="$1"
    local application_version="$2"
    printf 'release_ref=%s\ncommit=0123456789abcdef0123456789abcdef01234567\nbuilt_at=2026-09-26T00:00:00Z\napplication_version=%s\nsha256=%s\n' \
        "$ref" "$application_version" "$jar_sha" > "$manifest"
    chmod 0600 "$manifest"
}
if ! (source "$ARTIFACT"; write_manifest "fix/mobile-release-page-layout" 2.26.0; validate_artifact_manifest "$manifest" "$jar"); then
    printf 'A branch candidate manifest with an explicit release version should validate.\n' >&2
    exit 1
fi
write_manifest v2.26.0 2.26.0
if ! (source "$ARTIFACT"; validate_artifact_manifest "$manifest" "$jar"); then
    printf 'A production Tag manifest with matching release and application versions should validate.\n' >&2
    exit 1
fi
write_manifest v2.26.0 2.25.15
if (source "$ARTIFACT"; validate_artifact_manifest "$manifest" "$jar"); then
    printf 'A release Tag with a mismatched application version must be refused.\n' >&2
    exit 1
fi
write_manifest "fix/mobile-release-page-layout" ''
if (source "$ARTIFACT"; validate_artifact_manifest "$manifest" "$jar"); then
    printf 'A manifest without an application version must be refused.\n' >&2
    exit 1
fi

for path in \
    "$ROOT/Dockerfile" \
    "$ROOT/.dockerignore" \
    "$ROOT/deploy/docker-compose.app-external.yml" \
    "$ROOT/deploy/docker-compose.data-access.yml" \
    "$ROOT/deploy/docker-compose.single-host.yml" \
    "$ROOT/deploy/docker-compose.staging.yml" \
    "$ROOT/deploy/ctl.sh" \
    "$ROOT/deploy/prewarm-production-maven-cache.sh"; do
    if [[ -e "$path" ]]; then
        printf 'Docker deployment asset still exists: %s\n' "$path" >&2
        exit 1
    fi
done

printf 'Host-native deployment contract passed.\n'
