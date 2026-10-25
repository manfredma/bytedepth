#!/usr/bin/env bash
set -Eeuo pipefail
# Compatibility entrypoint; formatting rules live in tools.bytedepth.
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
exec env PYTHONPATH="$SOURCE_ROOT${PYTHONPATH:+:$PYTHONPATH}" python3 -m tools.bytedepth format "$@"
