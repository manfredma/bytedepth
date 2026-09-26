#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT
readonly SCRIPT="$ROOT/deploy/deploy-production.sh"
readonly MIGRATION_LIB="$ROOT/deploy/lib/production-green-migration.sh"
readonly PUBLIC_ROUTE_HELPER="$ROOT/deploy/lib/production-staging-edge.sh"

[[ -x "$SCRIPT" ]] || { printf 'Expected executable production deployment script.\n' >&2; exit 1; }

require_text() {
    local needle="$1" file="$2"
    rg -F -- "$needle" "$file" >/dev/null || {
        printf 'Missing production red-green contract in %s: %s\n' "$file" "$needle" >&2
        exit 1
    }
}

for contract in \
    'production_green_require_blue' \
    'production_green_prepare' \
    'production_green_final_sync' \
    'production_green_verify' \
    'backup_blue_route' \
    'restore_blue_route' \
    'docker inspect -f' \
    "docker stop \"\$DOCKER_APP\"" \
    "docker start \"\$DOCKER_APP\"" \
    "docker exec \"\$DOCKER_NGINX\" nginx -t" \
    "docker exec \"\$DOCKER_NGINX\" nginx -s reload" \
    'nginx -t' \
    'verify_blue_public_access' \
    'production_green_mark_uncertain' \
    'stop_green_services'; do
    require_text "$contract" "$SCRIPT"
done

require_text 'This is a production-host-only script' "$SCRIPT"
require_text 'deploy-production-remote.sh' "$SCRIPT"
require_text 'validate_artifact_manifest' "$SCRIPT"
require_text 'install_release_artifact' "$SCRIPT"
require_text 'production-green' "$SCRIPT"
require_text 'production_install_staging_edge_routes' "$SCRIPT"
require_text 'production_install_staging_edge_routes()' "$PUBLIC_ROUTE_HELPER"
require_text 'release-history' "$SCRIPT"
require_text 'production_green_prepare()' "$MIGRATION_LIB"
require_text '"$SOURCE_ROOT/deploy/install-production-green-stack.sh"' "$MIGRATION_LIB"

if rg -n -i 'docker compose|docker-compose|docker rm|docker system prune|systemctl (stop|restart|disable) nginx' "$SCRIPT" >/dev/null; then
    printf 'Production red-green deployment must not rebuild/remove Docker or stop shared Nginx.\n' >&2
    exit 1
fi

line_number() {
    local pattern="$1"
    awk -v pattern="$pattern" '$0 == pattern { print NR; exit }' "$SCRIPT"
}

prepare_line="$(line_number 'production_green_prepare')"
preflight_line="$(line_number 'verify_green_preflight')"
backup_line="$(line_number 'backup_blue_route')"
blue_stop_line="$(line_number "docker stop \"\$DOCKER_APP\"")"
final_sync_line="$(line_number 'production_green_final_sync')"
route_switch_line="$(line_number 'switch_green_route')"
rollback_guard_line="$(line_number 'rollback_required=1')"
[[ -n "$prepare_line" && -n "$preflight_line" && -n "$backup_line" && -n "$blue_stop_line" && \
    -n "$final_sync_line" && -n "$route_switch_line" && -n "$rollback_guard_line" ]] || {
    printf 'Could not resolve production cutover order.\n' >&2
    exit 1
}
(( prepare_line < preflight_line ))
(( preflight_line < backup_line ))
(( backup_line < rollback_guard_line ))
(( rollback_guard_line < blue_stop_line ))
(( blue_stop_line < final_sync_line ))
(( final_sync_line < route_switch_line ))
require_text 'trap rollback_on_failure EXIT' "$SCRIPT"
require_text 'if (( route_changed )); then' "$SCRIPT"
require_text 'if (( blue_stopped )); then' "$SCRIPT"
require_text 'if (( deployment_succeeded == 0 )); then' "$SCRIPT"
require_text 'if (( green_prepare_started )) || [[ -e "$GREEN_STATE_DIR/syncing" ]]; then' "$SCRIPT"
require_text 'green_prepare_started=1' "$SCRIPT"
if rg -n -F 'if ! production_green_final_sync; then' "$SCRIPT" >/dev/null; then
    printf 'Final green synchronization must not run in an errexit-suppressed conditional context.\n' >&2
    exit 1
fi

install_stack_line="$(awk '/\"\$SOURCE_ROOT\/deploy\/install-production-green-stack\.sh\"/ { print NR; exit }' "$MIGRATION_LIB")"
prepared_marker_line="$(awk '/BYTEDEPTH_PRODUCTION_GREEN_STATE_ROOT\/prepared/ { print NR; exit }' "$MIGRATION_LIB")"
[[ -n "$install_stack_line" && -n "$prepared_marker_line" && "$install_stack_line" -lt "$prepared_marker_line" ]] || {
    printf 'Production green must recheck native prerequisites before honoring the prepared marker.\n' >&2
    exit 1
}

printf 'Production red-green deployment contract passed.\n'
