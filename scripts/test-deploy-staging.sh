#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${1:-}" != "--container" ]]; then
    exec docker run --rm \
        -v "$(cd "$(dirname "$0")/.." && pwd):/workspace:ro" \
        maven:3.9-eclipse-temurin-17 \
        bash /workspace/scripts/test-deploy-staging.sh --container
fi

readonly FIXTURE_ROOT="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_ROOT"' EXIT

readonly ORIGIN="$FIXTURE_ROOT/origin.git"
readonly CHECKOUT="$FIXTURE_ROOT/checkout"
readonly MODE_CAPTURE="$FIXTURE_ROOT/bootstrap-mode"
readonly FAKE_BIN="$FIXTURE_ROOT/bin"

git init --bare --initial-branch=main "$ORIGIN" >/dev/null
git init --initial-branch=main "$CHECKOUT" >/dev/null
git -C "$CHECKOUT" config user.name test
git -C "$CHECKOUT" config user.email test@example.com
git -C "$CHECKOUT" remote add origin "$ORIGIN"

mkdir -p "$CHECKOUT/deploy"
cp /workspace/deploy/deploy-staging.sh "$CHECKOUT/deploy/deploy-staging.sh"
cat > "$CHECKOUT/deploy/bootstrap-ops-deploy.sh" <<SCRIPT
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "\${BYTEDEPTH_DEPLOY_MODE:-unset}" > "$MODE_CAPTURE"
SCRIPT
chmod +x "$CHECKOUT/deploy/deploy-staging.sh" "$CHECKOUT/deploy/bootstrap-ops-deploy.sh"

printf 'fixture\n' > "$CHECKOUT/fixture.txt"
git -C "$CHECKOUT" add .
git -C "$CHECKOUT" commit -m fixture >/dev/null
git -C "$CHECKOUT" push -u origin main >/dev/null 2>&1

mkdir -p "$FAKE_BIN"
cat > "$FAKE_BIN/git" <<'SCRIPT'
#!/usr/bin/env bash
if [[ "$*" == *" remote get-url origin" ]]; then
    printf 'git@github.com:manfredma/bytedepth.git\n'
    exit 0
fi
exec /usr/bin/git "$@"
SCRIPT
chmod +x "$FAKE_BIN/git"

touch /tmp/bytedepth-test-deploy-key
chmod 0600 /tmp/bytedepth-test-deploy-key
printf 'BYTEDEPTH_DEPLOY_MODE=staging\nBYTEDEPTH_DEPLOY_SSH_KEY=/tmp/bytedepth-test-deploy-key\n' \
    > /etc/bytedepth-deploy.conf

(cd "$CHECKOUT" && PATH="$FAKE_BIN:$PATH" ./deploy/deploy-staging.sh main) >/dev/null
actual_mode="$(cat "$MODE_CAPTURE")"
if [[ "$actual_mode" != "staging" ]]; then
    printf 'Expected bootstrap mode staging, got %s\n' "$actual_mode" >&2
    exit 1
fi

rm -f "$MODE_CAPTURE"
printf 'BYTEDEPTH_DEPLOY_MODE=single-host\nBYTEDEPTH_DEPLOY_SSH_KEY=/tmp/bytedepth-test-deploy-key\n' \
    > /etc/bytedepth-deploy.conf

if (cd "$CHECKOUT" && PATH="$FAKE_BIN:$PATH" ./deploy/deploy-staging.sh main) >/tmp/non-staging.out 2>&1; then
    printf 'Expected staging deployment to reject single-host mode\n' >&2
    exit 1
fi
if [[ -e "$MODE_CAPTURE" ]]; then
    printf 'Bootstrap ran after staging deployment rejected the configured mode\n' >&2
    exit 1
fi

printf 'deploy-staging mode propagation tests passed\n'
