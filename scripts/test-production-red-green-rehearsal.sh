#!/usr/bin/env bash
set -Eeuo pipefail

# This is a side-effect-free cutover rehearsal. It models the same transaction
# boundaries as deploy/deploy-production.sh with fake Docker, systemd, Nginx
# and public-access operations. The production script's ordering contract is
# checked separately by test-production-red-green-deployment.sh.
REHEARSAL_ROOT="$(mktemp -d)"
readonly REHEARSAL_ROOT
trap 'rm -rf -- "$REHEARSAL_ROOT"' EXIT

reset_state() {
    route=blue
    blue_app=running
    shared_nginx=running
    green_services=running
    nginx_loaded_route=blue
    route_changed=0
    blue_stopped=0
    deployment_succeeded=0
    : > "$REHEARSAL_ROOT/events"
    rm -f -- "$REHEARSAL_ROOT/uncertain"
}

event() {
    printf '%s\n' "$1" >> "$REHEARSAL_ROOT/events"
}

fake_docker_stop_blue() {
    event docker-stop-blue
    blue_app=stopped
    blue_stopped=1
}

fake_docker_start_blue() {
    event docker-start-blue
    blue_app=running
    blue_stopped=0
}

fake_green_stop() {
    event stop-green
    green_services=stopped
}

fake_nginx_restart_and_test() {
    event nginx-restart
    nginx_loaded_route="$route"
}

blue_public_access() {
    [[ "$route" == blue && "$nginx_loaded_route" == blue && "$blue_app" == running && "$shared_nginx" == running ]]
}

restore_blue_access() {
    if (( route_changed )); then
        route=blue
        route_changed=0
        fake_nginx_restart_and_test
    fi
    if (( blue_stopped )); then
        fake_docker_start_blue
    fi
    blue_public_access
}

run_cutover() {
    local failure_stage="$1"
    reset_state

    # Preparation and preflight do not have permission to touch Docker blue.
    if [[ "$failure_stage" == prepare || "$failure_stage" == green-health ]]; then
        fake_green_stop
        return 1
    fi

    event backup-blue-route
    fake_docker_stop_blue

    if [[ "$failure_stage" == final-sync ]]; then
        : > "$REHEARSAL_ROOT/uncertain"
        fake_green_stop
        restore_blue_access
        return 1
    fi

    event start-green
    green_services=running
    route=green
    route_changed=1
    if [[ "$failure_stage" == nginx-test ]]; then
        fake_nginx_restart_and_test
        nginx_loaded_route=blue
        fake_green_stop
        restore_blue_access
        return 1
    fi
    fake_nginx_restart_and_test
    if [[ "$failure_stage" == green-public ]]; then
        fake_green_stop
        restore_blue_access
        return 1
    fi

    deployment_succeeded=1
    return 0
}

assert_failed_case_restores_blue() {
    local stage="$1"
    if run_cutover "$stage"; then
        printf 'Expected rehearsal failure: %s\n' "$stage" >&2
        exit 1
    fi
    blue_public_access || {
        printf 'Docker public access was not restored after %s failure.\n' "$stage" >&2
        exit 1
    }
    [[ "$route" == blue && "$blue_app" == running && "$green_services" == stopped ]] || {
        printf 'Docker blue state was not restored after %s failure.\n' "$stage" >&2
        exit 1
    }
    if [[ "$stage" == final-sync && ! -e "$REHEARSAL_ROOT/uncertain" ]]; then
        printf 'Final-sync failure did not preserve uncertain migration state.\n' >&2
        exit 1
    fi
}

for stage in prepare green-health final-sync nginx-test green-public; do
    assert_failed_case_restores_blue "$stage"
done

grep -Fqx docker-stop-blue "$REHEARSAL_ROOT/events" >/dev/null

run_cutover success
[[ "$deployment_succeeded" -eq 1 && "$route" == green && "$blue_app" == stopped && "$green_services" == running ]] || {
    printf 'Successful rehearsal did not leave traffic on green with Docker retained as rollback baseline.\n' >&2
    exit 1
}
if grep -Fqx stop-green "$REHEARSAL_ROOT/events"; then
    printf 'Successful rehearsal stopped green services on exit.\n' >&2
    exit 1
fi

printf 'Production red-green failure rehearsal passed.\n'
