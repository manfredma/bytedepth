#!/usr/bin/env bash
set -Eeuo pipefail
# Transitional test adapter; the standard suite owns all formatter scenarios.
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
cd "$SOURCE_ROOT"
exec python3 -m unittest discover -s tests/engineering -p test_formatting.py
