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
BYTEDEPTH_TEST_IT_REDIS_DB=1
BYTEDEPTH_TEST_E2E_REDIS_DB=2
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
