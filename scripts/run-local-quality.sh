#!/usr/bin/env bash
set -Eeuo pipefail
# 固定本地入口；所有编排均由 tools.bytedepth 实现。
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
cd "$SOURCE_ROOT"
exec python3 -m tools.bytedepth quality "$@"
