#!/usr/bin/env bash
set -Eeuo pipefail
# Fixed interface consumed by release-platform mobile/manifest.py.
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
cd "$SOURCE_ROOT"
exec python3 -m tools.bytedepth mobile quality "$@"
