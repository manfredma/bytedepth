#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SCRIPT="$ROOT/deploy/deploy-production-remote.sh"
readonly HOST_SCRIPT="$ROOT/deploy/deploy-production.sh"

[[ -x "$SCRIPT" ]] || {
    printf 'Expected executable local production deployment wrapper.\n' >&2
    exit 1
}
grep -Fqx 'readonly PRODUCTION_HOST=175.24.197.202' "$SCRIPT"
grep -Fqx 'readonly REMOTE_ROOT=/opt/bytedepth' "$SCRIPT"
grep -Fq 'BYTEDEPTH_PRODUCTION_SSH_KEY' "$SCRIPT"
grep -Fq 'BYTEDEPTH_PRODUCTION_SSH_KNOWN_HOSTS' "$SCRIPT"
grep -Fq 'IdentitiesOnly=yes' "$SCRIPT"
grep -Fq 'BatchMode=yes' "$SCRIPT"
grep -Fq 'UserKnownHostsFile=' "$SCRIPT"
grep -Fq 'StrictHostKeyChecking=yes' "$SCRIPT"
grep -Fq 'deploy-production.sh' "$SCRIPT"
grep -Fq 'nohup' "$SCRIPT"
grep -Fq 'verify-production-release.sh' "$SCRIPT"
grep -Fq 'release-history' "$SCRIPT"
grep -Fq 'WARNING' "$SCRIPT"
grep -Fq 'This is a production-host-only script' "$HOST_SCRIPT"
grep -Fq 'deploy-production-remote.sh' "$HOST_SCRIPT"
if grep -Fq 'Run this script with sudo: sudo ./deploy/deploy-production.sh' "$HOST_SCRIPT"; then
    printf 'Host-only production script must not suggest local sudo execution.\n' >&2
    exit 1
fi

fixture_root="$(mktemp -d)"
trap 'rm -rf "$fixture_root"' EXIT
touch "$fixture_root/production-key"
touch "$fixture_root/known_hosts"
cat > "$fixture_root/ssh" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

remote_command="${@: -1}"
case "$remote_command" in
    *"printf 'READY\\n'"*)
        printf 'READY\n'
        ;;
    *"tail -n 80"*)
        printf 'Deployed v9.9.9 (fixture)\n'
        ;;
    *"grep -Fqx 'version=v9.9.9'"*)
        ;;
    *"verify-production-release.sh 'v9.9.9'"*)
        ;;
    *)
        ;;
esac
EOF
chmod +x "$fixture_root/ssh"
fixture_output="$fixture_root/output.log"
PATH="$fixture_root:$PATH" BYTEDEPTH_PRODUCTION_SSH_KEY="$fixture_root/production-key" BYTEDEPTH_PRODUCTION_SSH_KNOWN_HOSTS="$fixture_root/known_hosts" \
    "$SCRIPT" v9.9.9 > "$fixture_output"
grep -Fq 'Production deployment and verification passed for v9.9.9' "$fixture_output"

printf 'Local production deployment contract passed.\n'
