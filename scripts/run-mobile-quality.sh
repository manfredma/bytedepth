#!/usr/bin/env bash
set -Eeuo pipefail
# release-platform 的 mobile/manifest.py 使用的固定接口。
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
cd "$SOURCE_ROOT"
exec python3 -m tools.bytedepth mobile quality "$@"
