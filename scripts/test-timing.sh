#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly LIBRARY="$ROOT/deploy/lib/timing.sh"
readonly TEMP_DIR="$(mktemp -d)"
readonly TIMING_FILE="$TEMP_DIR/timing"
readonly SHA='0123456789abcdef0123456789abcdef01234567'
trap 'rm -rf "$TEMP_DIR"' EXIT

source "$LIBRARY"

TIMING_CLOCK="$TEMP_DIR/clock"
printf '%s\n' 1000 1650 2000 2650 > "$TIMING_CLOCK"
timing_now_epoch_ms() {
    local value
    value="$(head -n 1 "$TIMING_CLOCK")"
    tail -n +2 "$TIMING_CLOCK" > "$TIMING_CLOCK.next"
    mv "$TIMING_CLOCK.next" "$TIMING_CLOCK"
    printf '%s\n' "$value"
}

initialize_timing_file "$TIMING_FILE" "$SHA"
record_timed_phase "$TIMING_FILE" source_fetch true
if record_timed_phase "$TIMING_FILE" integration_verify false; then
    printf 'Expected failing command to preserve its failure status.\n' >&2
    exit 1
fi

readonly EXPECTED="$TEMP_DIR/expected"
printf '%s\n' \
    "identity=$SHA" \
    'phase=source_fetch result=passed started_at_epoch_ms=1000 finished_at_epoch_ms=1650 duration_ms=650' \
    'phase=integration_verify result=failed started_at_epoch_ms=2000 finished_at_epoch_ms=2650 duration_ms=650' \
    > "$EXPECTED"
cmp -s "$EXPECTED" "$TIMING_FILE"

if require_timing_identity "$TIMING_FILE" different-identity; then
    printf 'Expected mismatched identity to be rejected.\n' >&2
    exit 1
fi

if record_timing_phase "$TIMING_FILE" illegal-phase passed 1 2; then
    printf 'Expected invalid phase name to be rejected.\n' >&2
    exit 1
fi

printf 'Timing evidence contract passed.\n'
