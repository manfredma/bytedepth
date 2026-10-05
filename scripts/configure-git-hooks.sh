#!/usr/bin/env bash
set -euo pipefail

# Optional local developer setup for the repository-owned hooks. This only
# changes the local Git config and never participates in release execution.

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
git -C "$SOURCE_ROOT" config core.hooksPath config/git-hooks
printf 'Configured repository Git hooks from config/git-hooks.\n'
