#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SCRIPT="$ROOT/scripts/verify-production-release.sh"

[[ -x "$SCRIPT" ]] || { printf 'Expected executable production verifier.\n' >&2; exit 1; }
rg -F 'https://bytedepth.cn' "$SCRIPT" >/dev/null
rg -F 'release-history' "$SCRIPT" >/dev/null
rg -F 'git -c safe.directory' "$SCRIPT" >/dev/null
rg -F 'deploy/ctl.sh' "$SCRIPT" >/dev/null
if ! rg -F 'logs bytedepth-app --tail=300' "$SCRIPT" >/dev/null; then
    printf 'Production verification must query the project-prefixed bytedepth-app service.\n' >&2
    exit 1
fi
rg -F -- '--fail' "$SCRIPT" >/dev/null
rg -F -- '--silent' "$SCRIPT" >/dev/null
rg -F -- '--show-error' "$SCRIPT" >/dev/null
rg -F -- '--retry 12' "$SCRIPT" >/dev/null
rg -F -- '--retry-delay 5' "$SCRIPT" >/dev/null
rg -F -- '--retry-connrefused' "$SCRIPT" >/dev/null
rg -F '/posts' "$SCRIPT" >/dev/null
rg -F '/columns' "$SCRIPT" >/dev/null
rg -F '/projects' "$SCRIPT" >/dev/null
rg -F '/search' "$SCRIPT" >/dev/null
rg -F 'WARNING' "$SCRIPT" >/dev/null
if rg -F 'request "/blog${post_path}"' "$SCRIPT" >/dev/null; then
    printf 'Production verification must request the discovered post path without a /blog prefix.\n' >&2
    exit 1
fi
if rg -F 'logs app --tail=300' "$SCRIPT" >/dev/null; then
    printf 'Production verification must use the project-prefixed bytedepth-app service.\n' >&2
    exit 1
fi
if rg -F 'docker compose' "$SCRIPT" >/dev/null; then
    printf 'Production verification must use deploy/ctl.sh rather than bare Compose.\n' >&2
    exit 1
fi

printf 'Production verification contract passed.\n'
