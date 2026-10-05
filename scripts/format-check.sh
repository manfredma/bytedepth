#!/usr/bin/env bash
set -Eeuo pipefail

# Read-only wrapper around format-code.sh --check for local quality gates.

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
exec bash "$SOURCE_ROOT/scripts/format-code.sh" --check "$@"
