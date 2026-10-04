#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
exec bash "$SOURCE_ROOT/scripts/format-code.sh" --check "$@"
