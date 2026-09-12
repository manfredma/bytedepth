#!/usr/bin/env bash

timing_now_epoch_ms() {
    date -u +%s%3N
}

is_valid_timing_phase() {
    [[ "$1" =~ ^[a-z0-9_]+$ ]]
}

initialize_timing_file() {
    local timing_file="$1"
    local identity="$2"
    local timing_directory
    local temporary_file

    timing_directory="$(dirname "$timing_file")"
    install -d -m 0700 "$timing_directory"
    temporary_file="$(mktemp "$timing_directory/.timing.XXXXXX")"
    printf 'identity=%s\n' "$identity" > "$temporary_file"
    if [[ "${EUID}" -eq 0 ]]; then
        chown root:root "$timing_directory" "$temporary_file"
    fi
    chmod 0600 "$temporary_file"
    mv -f "$temporary_file" "$timing_file"
}

require_timing_identity() {
    local timing_file="$1"
    local expected_identity="$2"
    local recorded_identity

    recorded_identity="$(awk -F= '$1 == "identity" { print $2; exit }' "$timing_file" 2>/dev/null || true)"
    if [[ "$recorded_identity" != "$expected_identity" ]]; then
        printf 'Refusing: timing evidence does not match the expected identity.\n' >&2
        return 1
    fi
}

record_timing_phase() {
    local timing_file="$1"
    local phase="$2"
    local result="$3"
    local started_at="$4"
    local finished_at="$5"
    local elapsed

    if ! is_valid_timing_phase "$phase"; then
        printf 'Refusing: invalid timing phase %s.\n' "$phase" >&2
        return 1
    fi
    if [[ "$result" != 'passed' && "$result" != 'failed' ]]; then
        printf 'Refusing: invalid timing result %s.\n' "$result" >&2
        return 1
    fi
    if [[ ! "$started_at" =~ ^[0-9]+$ || ! "$finished_at" =~ ^[0-9]+$ || "$finished_at" -lt "$started_at" ]]; then
        printf 'Refusing: invalid timing interval.\n' >&2
        return 1
    fi

    elapsed=$((finished_at - started_at))
    printf 'phase=%s result=%s started_at_epoch_ms=%s finished_at_epoch_ms=%s duration_ms=%s\n' \
        "$phase" "$result" "$started_at" "$finished_at" "$elapsed" >> "$timing_file"
    printf 'TIMING phase=%s result=%s duration_ms=%s\n' "$phase" "$result" "$elapsed"
}

record_timed_phase() {
    local timing_file="$1"
    local phase="$2"
    local started_at
    local finished_at
    local result
    local status

    shift 2
    started_at="$(timing_now_epoch_ms)"
    if "$@"; then
        result=passed
        status=0
    else
        status=$?
        result=failed
    fi
    finished_at="$(timing_now_epoch_ms)"
    record_timing_phase "$timing_file" "$phase" "$result" "$started_at" "$finished_at"
    return "$status"
}
