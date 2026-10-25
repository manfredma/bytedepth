#!/usr/bin/env bash
set -Eeuo pipefail
# Temporary compatibility adapter; implementation lives in tools.bytedepth.
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
exec env PYTHONPATH="$SOURCE_ROOT${PYTHONPATH:+:$PYTHONPATH}" python3 -m tools.bytedepth release order "$@"
