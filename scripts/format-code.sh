#!/usr/bin/env bash
set -Eeuo pipefail
# 兼容入口；格式化规则由 tools.bytedepth 统一维护。
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
exec env PYTHONPATH="$SOURCE_ROOT${PYTHONPATH:+:$PYTHONPATH}" python3 -m tools.bytedepth format "$@"
