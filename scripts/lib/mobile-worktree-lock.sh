#!/usr/bin/env bash
# Shared mutable inputs: npm ci and native Gradle must never run concurrently.
acquire_mobile_worktree_lock() {
  mkdir -p "$MOBILE_ROOT/.expo"
  readonly MOBILE_INPUT_LOCK="$MOBILE_ROOT/.expo/toolchain.lock"
  if ! mkdir "$MOBILE_INPUT_LOCK" 2> /dev/null; then
    printf 'Mobile dependency/native inputs are in use: %s. Wait for the owning command; inspect interrupted processes before removing a stale lock.\n' "$MOBILE_INPUT_LOCK" >&2
    exit 1
  fi
  # On signals the child/descendants may outlive this shell. Keep the lock until
  # their terminal state is inspected; normal command failures still release it.
  trap 'trap - EXIT; exit 129' HUP
  trap 'trap - EXIT; exit 130' INT
  trap 'trap - EXIT; exit 143' TERM
  trap 'rmdir "$MOBILE_INPUT_LOCK"' EXIT
}
