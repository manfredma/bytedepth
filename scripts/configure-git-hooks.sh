#!/usr/bin/env bash
set -euo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
git -C "$SOURCE_ROOT" config core.hooksPath .githooks
printf 'Configured repository Git hooks from .githooks.\n'
