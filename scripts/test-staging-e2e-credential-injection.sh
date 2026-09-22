#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly DEPLOY_DOC="$SOURCE_ROOT/deploy/README.md"

require_doc_line() {
    rg -F -- "$1" "$DEPLOY_DOC" >/dev/null || {
        printf 'Missing staging E2E credential injection contract: %s\n' "$1" >&2
        exit 1
    }
}

require_doc_line 'SSH 默认不会转发任意环境变量'
require_doc_line '通过 SSH 标准输入传到远端 shell'
require_doc_line 'IFS= read -r BYTEDEPTH_STAGING_E2E_USERNAME'
require_doc_line 'IFS= read -r BYTEDEPTH_STAGING_E2E_PASSWORD'
require_doc_line 'sudo --preserve-env=BYTEDEPTH_STAGING_E2E_USERNAME,BYTEDEPTH_STAGING_E2E_PASSWORD'
require_doc_line 'unset staging_e2e_username staging_e2e_password'

if awk '
    /^```/ { in_code = !in_code; next }
    in_code && /BYTEDEPTH_STAGING_E2E_PASSWORD=.*ssh/ { found = 1 }
    END { exit found ? 0 : 1 }
' "$DEPLOY_DOC"; then
    printf 'Staging E2E password must not be placed in an SSH command environment prefix.\n' >&2
    exit 1
fi

printf 'Staging E2E credential injection contract passed.\n'
