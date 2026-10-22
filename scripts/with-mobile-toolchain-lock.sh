#!/usr/bin/env bash
set -Eeuo pipefail
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
readonly MOBILE_ROOT="$SOURCE_ROOT/apps/mobile"
source "$SOURCE_ROOT/scripts/lib/mobile-worktree-lock.sh"
acquire_mobile_worktree_lock
cd "$MOBILE_ROOT"
"$@"
