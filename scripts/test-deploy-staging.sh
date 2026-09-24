#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT
readonly SCRIPT="$ROOT/deploy/deploy-staging.sh"

[[ -x "$SCRIPT" ]] || { printf 'Expected executable staging deployment script.\n' >&2; exit 1; }
for contract in \
    'deployment-test.lock' \
    'staging-integration' \
    'staging-e2e' \
    'check-staging-changelog-change.sh' \
    'check-release-readiness.sh' \
    'build_release_artifact' \
    'scp' \
    'install_release_artifact' \
    'switch_current_release' \
    'BYTEDEPTH_STAGING_APP_SERVICE' \
    'BYTEDEPTH_STAGING_EDGE_SERVICE' \
    'load_staging_native_target' \
    'git fetch --force --no-recurse-submodules origin' \
    'git checkout --detach' \
    'verify_running_release' \
    'BYTEDEPTH_REMOTE_INSTALL=1' \
    'BYTEDEPTH_STAGING_SSH_KNOWN_HOSTS' \
    'staging-bytedepth.bytedepth.cn'; do
    rg -F -- "$contract" "$SCRIPT" >/dev/null || {
        printf 'Missing staging deployment contract: %s\n' "$contract" >&2
        exit 1
    }
done
rg -q 'check-staging-changelog-change.sh.*>&2' "$SCRIPT"
rg -q 'check-release-readiness.sh.*>&2' "$SCRIPT"
rg -q 'sudo -n grep -Fqx BYTEDEPTH_ENVIRONMENT=staging' "$SCRIPT"
rg -q 'local ref="\$1" jar="\$2" manifest="\$3"' "$SCRIPT"
rg -q '"\$0" --lock-held "\$ref" "\$jar" "\$manifest"' "$SCRIPT"
if rg -n -i 'docker|compose|docker_build_and_rollout|bootstrap-staging-runtime|mvn ' "$SCRIPT" >/dev/null; then
    printf 'Staging deployment must build externally and install a native artifact.\n' >&2
    exit 1
fi
rg -Fq 'ls-remote --exit-code --heads' "$SCRIPT"
rg -Fq 'ls-remote --exit-code --tags' "$SCRIPT"
if rg -Fq 'ls-remote --heads --tags' "$SCRIPT"; then
    printf 'Staging deployment must check remote branches and tags separately.\n' >&2
    exit 1
fi

printf 'Staging deployment contract passed.\n'
