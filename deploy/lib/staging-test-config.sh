#!/usr/bin/env bash

# Project-owned test inputs, separate from the Host Agent's global environment.
# Values are literal data; project configuration cannot replace signed identity.
load_staging_test_configuration() {
  local config="${BYTEDEPTH_STAGING_TEST_CONFIG:-/etc/bytedepth/staging-test.env}"
  local key value password_file
  [[ -e "$config" || -L "$config" ]] || return 0
  slot_root_private "$config" || return 1
  while IFS='=' read -r key value || [[ -n "$key" ]]; do
    [[ -z "$key" || "$key" == \#* ]] && continue
    case "$key" in
      BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE | BYTEDEPTH_TEST_REDIS_SECRET_FILE | BYTEDEPTH_TEST_MEILI_SECRET_FILE | \
        BYTEDEPTH_TEST_FIXTURE | BYTEDEPTH_TEST_FIXTURE_SHA256_FILE | BYTEDEPTH_TEST_STAGING_REDIS_DB | \
        BYTEDEPTH_TEST_IT_REDIS_DB | BYTEDEPTH_TEST_E2E_REDIS_DB | BYTEDEPTH_STAGING_E2E_USERNAME | \
        BYTEDEPTH_STAGING_E2E_PASSWORD_FILE) ;;
      *)
        printf 'Refusing: unsupported staging test configuration key: %s\n' "$key" >&2
        return 1
        ;;
    esac
    [[ -n "$value" ]] || return 1
    [[ -n "${!key:-}" ]] || export "$key=$value"
  done < "$config"
  password_file="${BYTEDEPTH_STAGING_E2E_PASSWORD_FILE:-}"
  if [[ -z "${BYTEDEPTH_STAGING_E2E_PASSWORD:-}" && -n "$password_file" ]]; then
    slot_root_private "$password_file" || return 1
    BYTEDEPTH_STAGING_E2E_PASSWORD="$(< "$password_file")"
    [[ -n "$BYTEDEPTH_STAGING_E2E_PASSWORD" ]] || return 1
    export BYTEDEPTH_STAGING_E2E_PASSWORD
  fi
}
