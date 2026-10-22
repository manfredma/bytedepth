#!/usr/bin/env bash
# Shared mutable inputs: npm ci and native Gradle must never run concurrently.
acquire_mobile_worktree_lock() {
  mkdir -p "$MOBILE_ROOT/.expo"
  readonly MOBILE_INPUT_LOCK="$MOBILE_ROOT/.expo/toolchain.lock"
  if ! mkdir "$MOBILE_INPUT_LOCK" 2> /dev/null; then
    printf 'Mobile dependency/native inputs are in use: %s. Wait for the owning command; inspect interrupted processes before removing a stale lock.\n' "$MOBILE_INPUT_LOCK" >&2
    exit 1
  fi
  trap 'rmdir "$MOBILE_INPUT_LOCK"' EXIT
}
