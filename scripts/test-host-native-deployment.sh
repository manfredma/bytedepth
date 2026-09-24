#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT
readonly ARTIFACT="$ROOT/deploy/lib/artifact.sh"
readonly STAGING="$ROOT/deploy/deploy-staging.sh"
readonly PRODUCTION="$ROOT/deploy/deploy-production.sh"
readonly REMOTE="$ROOT/deploy/deploy-production-remote.sh"

[[ -f "$ARTIFACT" ]] || { printf 'Missing artifact library.\n' >&2; exit 1; }
for function_name in validate_release_tag validate_artifact_manifest install_release_artifact switch_current_release verify_running_release; do
    rg -q "^${function_name}[[:space:]]*\(\)" "$ARTIFACT" || {
        printf 'Missing artifact interface: %s\n' "$function_name" >&2
        exit 1
    }
done

rg -q 'mvnw clean install -DskipTests -Dsort.skip=true' "$ARTIFACT"
rg -q 'sha256sum|shasum -a 256' "$ARTIFACT"
rg -q 'trap .*build_log:-.*\|\| rm -f --.*RETURN' "$ARTIFACT"
if rg -n 'find .*target.*\|[[:space:]]*sort[[:space:]]*\|[[:space:]]*head' "$ARTIFACT" >/dev/null; then
    printf 'Artifact builder must not use a pipefail-unsafe head pipeline.\n' >&2
    exit 1
fi
rg -q 'install_release_artifact|switch_current_release' "$STAGING"
rg -q 'restore_current_release|rollback' "$STAGING"
rg -q 'systemctl restart "\$BYTEDEPTH_STAGING_APP_SERVICE"' "$STAGING"
rg -q 'base_url/version' "$ARTIFACT"
rg -q 'artifact|app\.jar' "$PRODUCTION"
rg -q 'deploy-production\.sh' "$REMOTE"
rg -q 'UserKnownHostsFile=' "$REMOTE"
rg -q 'StrictHostKeyChecking=yes' "$REMOTE"
rg -q 'DEPLOY_LOCK=/var/lock/bytedepth-production-deploy.lock|flock -n 9' "$PRODUCTION"
rg -q 'restore_current_release|rollback' "$PRODUCTION"
if rg -q 'docker|compose|mvn ' "$REMOTE" "$PRODUCTION"; then
    printf 'Native deployment entrypoints must not invoke Docker, Compose, or bare Maven.\n' >&2
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
