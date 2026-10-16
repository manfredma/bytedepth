#!/usr/bin/env bash
set -Eeuo pipefail
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 "$SOURCE_ROOT/scripts/test-annotation-unit-evidence.py"
