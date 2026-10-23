#!/usr/bin/env bash
set -Eeuo pipefail
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
readonly MOBILE_ROOT="$SOURCE_ROOT/apps/mobile"
source "$SOURCE_ROOT/scripts/lib/mobile-worktree-lock.sh"
acquire_mobile_worktree_lock
cd "$MOBILE_ROOT"
unset EXPO_PUBLIC_RELEASE_CHANNEL
[[ "$(node --version)" == "v$(cat .nvmrc)" ]] || {
  printf 'Use the pinned mobile Node version in apps/mobile/.nvmrc.\n' >&2
  exit 1
}
if [[ -L node_modules ]]; then
  printf 'Mobile node_modules must belong to this package.\n' >&2
  exit 1
fi
npm ci --ignore-scripts --no-audit --no-fund
[[ -x node_modules/.bin/jest && -x node_modules/.bin/tsc ]] || {
  printf 'Mobile dependencies are incomplete.\n' >&2
  exit 1
}
rm -rf coverage
npm run typecheck
npm run lint
npm test -- --runInBand --coverage
node "$SOURCE_ROOT/scripts/verify-mobile-coverage.mjs" "$MOBILE_ROOT"
node "$SOURCE_ROOT/scripts/sync-mobile-reader-assets.mjs" --check
mkdir -p .expo
for release_channel in preview production; do
  EXPO_PUBLIC_RELEASE_CHANNEL="$release_channel" npm exec --no -- expo config --type public --json > ".expo/mobile-$release_channel-config.json"
  node "$SOURCE_ROOT/scripts/verify-mobile-config.mjs" ".expo/mobile-$release_channel-config.json"
done
