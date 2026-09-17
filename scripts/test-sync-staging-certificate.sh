#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SCRIPT="$ROOT/deploy/sync-staging-certificate-to-production.sh"
readonly LEGACY_SCRIPT="$ROOT/deploy/provision-production-edge-staging-certificate.sh"
readonly EDGE_CONFIG="$ROOT/deploy/nginx/staging-edge-certificate.conf"
readonly DATA_SYNC_SCRIPT="$ROOT/deploy/sync-prod-to-staging.sh"

[[ -x "$SCRIPT" ]] || { printf 'Expected executable staging certificate sync script.\n' >&2; exit 1; }
[[ -x "$LEGACY_SCRIPT" ]] || { printf 'Expected executable legacy staging certificate provisioning script.\n' >&2; exit 1; }
[[ -f "$EDGE_CONFIG" ]] || { printf 'Expected production edge certificate route.\n' >&2; exit 1; }
[[ -f "$DATA_SYNC_SCRIPT" ]] || { printf 'Expected production-to-staging sync script.\n' >&2; exit 1; }

for contract in \
    'SYNC_SSH_KEY' \
    'STAGING_IP' \
    'staging-bytedepth.bytedepth.cn' \
    'staging.bytedepth.cn' \
    '/etc/letsencrypt/live/staging-bytedepth.bytedepth.cn/fullchain.pem' \
    '/etc/letsencrypt/live/staging-bytedepth.bytedepth.cn/privkey.pem' \
    '/etc/letsencrypt/live/staging.bytedepth.cn/fullchain.pem' \
    '/etc/letsencrypt/live/staging.bytedepth.cn/privkey.pem' \
    'subjectAltName' \
    'nginx -t' \
    'nginx -s reload'; do
    rg -F -- "$contract" "$SCRIPT" "$EDGE_CONFIG" >/dev/null || {
        printf 'Missing staging certificate sync contract: %s\n' "$contract" >&2
        exit 1
    }
done

for contract in \
    'staging.bytedepth.cn' \
    'staging-bytedepth.bytedepth.cn' \
    'certbot certonly' \
    '--standalone' \
    'https://bytedepth.cn' \
    'subjectAltName'; do
    rg -F -- "$contract" "$LEGACY_SCRIPT" "$EDGE_CONFIG" >/dev/null || {
        printf 'Missing legacy staging certificate contract: %s\n' "$contract" >&2
        exit 1
    }
done

rg -F 'sync-staging-certificate-to-production.sh' "$DATA_SYNC_SCRIPT" >/dev/null || {
    printf 'Production-to-staging sync must refresh the production edge certificate.\n' >&2
    exit 1
}

if rg -n 'proxy_pass|bytedepth-app|career-app|toolbox-app' "$EDGE_CONFIG"; then
    printf 'Production staging certificate route must not proxy staging content.\n' >&2
    exit 1
fi

printf 'Staging certificate sync contract passed.\n'
