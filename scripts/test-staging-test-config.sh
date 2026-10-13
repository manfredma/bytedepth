#!/usr/bin/env bash
set -Eeuo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
source "$root/deploy/lib/staging-test-slot.sh"
# The fixture's current owner replaces only the host's ubuntu ownership fact.
slot_stat_user() { printf 'ubuntu\n'; }
if [[ -f "$root/deploy/lib/staging-test-config.sh" ]]; then
  source "$root/deploy/lib/staging-test-config.sh"
else
  load_staging_test_configuration() { :; }
fi
printf 'fixture secret\n' > "$tmp/password"
chmod 600 "$tmp/password"
cat > "$tmp/config" << CONFIG
BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE=$tmp/mysql.defaults
BYTEDEPTH_TEST_STAGING_REDIS_DB=0
BYTEDEPTH_TEST_IT_REDIS_DB=14
BYTEDEPTH_TEST_E2E_REDIS_DB=15
BYTEDEPTH_STAGING_E2E_USERNAME=fixture-admin
BYTEDEPTH_STAGING_E2E_PASSWORD_FILE=$tmp/password
CONFIG
chmod 600 "$tmp/config"
export BYTEDEPTH_STAGING_TEST_CONFIG="$tmp/config"
unset BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE BYTEDEPTH_STAGING_E2E_PASSWORD
load_staging_test_configuration
[[ "${BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE:-}" == "$tmp/mysql.defaults" ]] || {
  echo 'Configured test inputs were not loaded' >&2
  exit 1
}
[[ "$BYTEDEPTH_STAGING_E2E_PASSWORD" == 'fixture secret' ]]
BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE=explicit-input
load_staging_test_configuration
[[ "$BYTEDEPTH_TEST_MYSQL_DEFAULTS_FILE" == explicit-input ]]
# Shared infrastructure credentials take precedence over legacy project inputs.
export E2E_ADMIN_USERNAME=shared-account E2E_ADMIN_PASSWORD=shared-secret
load_staging_test_configuration
[[ "$BYTEDEPTH_STAGING_E2E_USERNAME" == shared-account && "$BYTEDEPTH_STAGING_E2E_PASSWORD" == shared-secret ]] || {
  echo 'Shared infrastructure account was not used' >&2
  exit 1
}
if E2E_ADMIN_PASSWORD='' load_staging_test_configuration; then
  echo 'Partial shared account accepted' >&2
  exit 1
fi
printf 'RELEASE_PLATFORM_TASK_NONCE=forged\n' >> "$tmp/config"
if load_staging_test_configuration; then
  echo 'Task identity accepted from project test configuration' >&2
  exit 1
fi
sed '/RELEASE_PLATFORM_TASK_NONCE=/d' "$tmp/config" > "$tmp/clean"
mv "$tmp/clean" "$tmp/config"
chmod 644 "$tmp/config"
if load_staging_test_configuration; then
  echo 'Public test configuration accepted' >&2
  exit 1
fi
for phase in integration e2e; do
  grep -Fq 'load_staging_test_configuration' "$root/deploy/run-staging-$phase-tests.sh"
done
printf 'staging test configuration tests passed\n'
