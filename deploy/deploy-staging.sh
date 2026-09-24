#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -lt 1 || -z "${1:-}" ]]; then
    printf 'Usage: %s <candidate-ref>\n' "$0" >&2
    exit 2
fi

SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SOURCE_ROOT
readonly STAGING_HOST="${BYTEDEPTH_STAGING_HOST:-124.221.143.25}"
readonly STAGING_USER=ubuntu
readonly STAGING_SSH_KEY="${BYTEDEPTH_SSH_KEY:-${HOME}/.ssh/ubuntu_2.pem}"
readonly STATE_DIR=/var/lib/bytedepth-staging
readonly LOCK_FILE="$STATE_DIR/deployment-test.lock"
readonly HISTORY_FILE="$STATE_DIR/deploy-history"
readonly TIMING_DIR="$STATE_DIR/timing"

source "$SOURCE_ROOT/deploy/lib/artifact.sh"
source "$SOURCE_ROOT/deploy/lib/timing.sh"
source "$SOURCE_ROOT/deploy/lib/warning-policy.sh"

require_named_ref() {
    local ref="$1"
    validate_artifact_ref "$ref" || { printf 'Refusing: candidate ref contains unsafe characters.\n' >&2; exit 1; }
    git -C "$SOURCE_ROOT" ls-remote --heads --tags origin "$ref" | grep -F . >/dev/null || {
        printf 'Refusing: %s is not a named branch or tag on origin.\n' "$ref" >&2
        exit 1
    }
}

build_candidate() {
    local ref="$1" output_dir="$2" commit checkout
    git -C "$SOURCE_ROOT" fetch --force --no-recurse-submodules origin "$ref" main
    commit="$(git -C "$SOURCE_ROOT" rev-parse 'FETCH_HEAD^{commit}')"
    bash "$SOURCE_ROOT/scripts/check-staging-changelog-change.sh" --target "$commit" --base origin/main
    bash "$SOURCE_ROOT/scripts/check-release-readiness.sh" --target "$commit" --base origin/main --mode candidate
    checkout="$(mktemp -d)"
    git -C "$SOURCE_ROOT" archive "$commit" | tar -x -C "$checkout"
    BYTEDEPTH_COMMIT_ID="$commit" BYTEDEPTH_BUILT_AT="$(date -u +%FT%TZ)" \
        build_release_artifact "$checkout" "$ref" "$commit" "$output_dir" >&2
    rm -rf "$checkout"
    printf '%s\n' "$commit"
}

require_staging_host_configuration() {
    local remote_command
    remote_command='set -Eeuo pipefail
test -f /etc/bytedepth/application.env
grep -Fqx BYTEDEPTH_ENVIRONMENT=staging /etc/bytedepth/application.env
grep -Fqx BYTEDEPTH_DOMAIN=staging-bytedepth.bytedepth.cn /etc/bytedepth/application.env'
    ssh -i "$STAGING_SSH_KEY" -o IdentitiesOnly=yes -o BatchMode=yes \
        -o StrictHostKeyChecking=accept-new "$STAGING_USER@$STAGING_HOST" "$remote_command"
}

deploy_external_artifact() {
    local ref="$1" artifact_dir="$2" commit="$3" remote_dir="/tmp/bytedepth-staging-$3" remote_command
    require_staging_host_configuration
    ssh -i "$STAGING_SSH_KEY" -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=accept-new \
        "$STAGING_USER@$STAGING_HOST" "install -d -m 0700 '$remote_dir'"
    scp -i "$STAGING_SSH_KEY" -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=accept-new \
        "$artifact_dir/app.jar" "$artifact_dir/artifact.manifest" "$STAGING_USER@$STAGING_HOST:$remote_dir/"
    printf -v remote_command 'cd /opt/bytedepth && sudo ./deploy/deploy-staging.sh --artifact %q --manifest %q %q' \
        "$remote_dir/app.jar" "$remote_dir/artifact.manifest" "$ref"
    ssh -i "$STAGING_SSH_KEY" -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=accept-new \
        "$STAGING_USER@$STAGING_HOST" "$remote_command"
}

run_remote_install() {
    [[ "${EUID}" -eq 0 ]] || { printf 'Internal staging installation requires root.\n' >&2; exit 1; }
    install -d -o root -g root -m 0700 "$STATE_DIR" "$TIMING_DIR"
    exec flock -x "$LOCK_FILE" "$0" --lock-held "$5" "$2" "$4"
}

run_locked_install() {
    local ref="$1" jar="$2" manifest="$3" commit timing_file deployment_started_at
    commit="$(artifact_manifest_value commit "$manifest")"
    timing_file="$TIMING_DIR/$commit"
    [[ "$(awk -F= '$1 == "BYTEDEPTH_DEPLOY_MODE" {value=$2} END {print value}' /etc/bytedepth-deploy.conf 2>/dev/null || true)" == staging ]] || {
        printf 'Refusing: BYTEDEPTH_DEPLOY_MODE must be staging.\n' >&2
        exit 1
    }
    validate_artifact_manifest "$manifest" "$jar" || { printf 'Refusing: uploaded artifact failed manifest validation.\n' >&2; exit 1; }
    [[ "$(artifact_manifest_value release_ref "$manifest")" == "$ref" ]] || { printf 'Refusing: artifact reference does not match candidate reference.\n' >&2; exit 1; }
    require_staging_host_configuration
    initialize_timing_file "$timing_file" "$commit"
    deployment_started_at="$(timing_now_epoch_ms)"
    rm -f "$STATE_DIR/test-history/staging-integration" "$STATE_DIR/test-history/staging-e2e"

    if ! record_timed_phase "$timing_file" native_service_install ./deploy/bootstrap-ops-deploy.sh; then
        record_timing_phase "$timing_file" deployment_total failed "$deployment_started_at" "$(timing_now_epoch_ms)"; exit 1
    fi
    backup_database_preflight() {
        local backup_dir="$STATE_DIR/backups"
        install -d -o root -g root -m 0700 "$backup_dir"
        command -v mysqldump >/dev/null
        mysqladmin --protocol=socket ping >/dev/null
        mysqldump --protocol=socket --all-databases --single-transaction --routines --events > "$backup_dir/mysql-$commit.sql"
        chmod 0600 "$backup_dir/mysql-$commit.sql"
    }
    if ! record_timed_phase "$timing_file" database_backup_preflight backup_database_preflight; then
        record_timing_phase "$timing_file" deployment_total failed "$deployment_started_at" "$(timing_now_epoch_ms)"; exit 1
    fi
    if ! record_timed_phase "$timing_file" artifact_install install_release_artifact "$ref" "$jar" "$manifest"; then
        record_timing_phase "$timing_file" deployment_total failed "$deployment_started_at" "$(timing_now_epoch_ms)"; exit 1
    fi
    if ! record_timed_phase "$timing_file" release_switch switch_current_release "$ref"; then
        record_timing_phase "$timing_file" deployment_total failed "$deployment_started_at" "$(timing_now_epoch_ms)"; exit 1
    fi
    if ! record_timed_phase "$timing_file" app_restart systemctl restart bytedepth-app.service; then
        record_timing_phase "$timing_file" deployment_total failed "$deployment_started_at" "$(timing_now_epoch_ms)"; exit 1
    fi
    if ! record_timed_phase "$timing_file" app_health verify_running_release "$commit"; then
        record_timing_phase "$timing_file" deployment_total failed "$deployment_started_at" "$(timing_now_epoch_ms)"; exit 1
    fi
    if ! record_timed_phase "$timing_file" nginx_reload systemctl reload nginx.service; then
        record_timing_phase "$timing_file" deployment_total failed "$deployment_started_at" "$(timing_now_epoch_ms)"; exit 1
    fi
    install -d -m 0700 "$STATE_DIR"
    printf 'ref=%s\ncommit=%s\ndeployed_at=%s\n---\n' "$ref" "$commit" "$(date -u +%FT%TZ)" >> "$HISTORY_FILE"
    record_timing_phase "$timing_file" deployment_total passed "$deployment_started_at" "$(timing_now_epoch_ms)"
    printf 'Deployed staging %s (%s) using native artifact.\n' "$ref" "$commit"
}

if [[ "${1:-}" == --lock-held ]]; then
    shift
    run_locked_install "$@"
    exit 0
fi
if [[ "${1:-}" == --artifact ]]; then
    [[ $# -eq 5 ]] || { printf 'Usage: %s --artifact JAR --manifest MANIFEST REF\n' "$0" >&2; exit 2; }
    run_remote_install "$5" "$2" "$4"
    exit 0
fi

readonly REF="$1"
require_named_ref "$REF"
[[ -r "$STAGING_SSH_KEY" ]] || { printf 'Staging SSH key is missing or unreadable.\n' >&2; exit 1; }
ARTIFACT_DIR="$(mktemp -d)"
readonly ARTIFACT_DIR
trap 'rm -rf "$ARTIFACT_DIR"' EXIT
COMMIT="$(build_candidate "$REF" "$ARTIFACT_DIR")"
readonly COMMIT
deploy_external_artifact "$REF" "$ARTIFACT_DIR" "$COMMIT"
