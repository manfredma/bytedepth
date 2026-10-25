#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export PYTHONPATH="$root${PYTHONPATH:+:$PYTHONPATH}"
exec python3 -m tools.bytedepth host certificate legacy-edge "$@"
