#!/usr/bin/env bash
set -Eeuo pipefail
# Stable local entrypoint; all orchestration lives in tools.bytedepth.
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
cd "$SOURCE_ROOT"
exec python3 -m tools.bytedepth quality "$@"
