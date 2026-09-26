#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT SCRIPT="$ROOT/deploy/deploy-production.sh" TARGET="$ROOT/deploy/lib/production-target.sh"
[[ -x "$SCRIPT" ]] || { printf 'Production deployment entrypoint is missing.\n' >&2; exit 1; }
for contract in \
    'preflight_production_baseline()' \
    'normalize_live_layout()' \
    'LEGACY_RELEASE_IS_ALIAS=1' \
    'rollback_layout_on_exit()' \
    'systemctl enable "$service" || rollback_status=1' \
    'systemctl start "$service" || rollback_status=1' \
    'LEGACY_CURRENT_TARGET="$(readlink -- "$previous_link")"' \
    'ln -sfnT -- "$normalized_target" /opt/bytedepth/production/current' \
    'mv -- "${old_release_roots[0]}" /opt/bytedepth/production' \
    'mv -- "$LEGACY_STATE_ROOT" /var/lib/bytedepth-production' \
    'chown -R ubuntu:ubuntu /var/lib/bytedepth-production' \
    'load_production_target' \
    'install -o ubuntu -g ubuntu -m 0600 "$PREVIOUS_APP_ENV_FILE" /etc/bytedepth/production.env' \
    'current_release_path' \
    'install_release_artifact' \
    'switch_current_release' \
    'systemctl reload "$BYTEDEPTH_PRODUCTION_EDGE_SERVICE"' \
    'systemctl reload "$BYTEDEPTH_PRODUCTION_PUBLIC_NGINX_SERVICE"' \
    'restore_current_release' \
    'restore_previous_release()' \
    'verify_running_release "$previous_commit" "$previous_version" || return 1' \
    'verify_running_release' \
    'runtime_mode=production-native' \
    'for candidate in /var/lib/bytedepth-production-*' \
    'rm -f -- "/etc/systemd/system/$service"' \
    'rm -f -- "$candidate"'; do
    rg -F -- "$contract" "$SCRIPT" >/dev/null || { printf 'Missing native deployment contract: %s\n' "$contract" >&2; exit 1; }
done
preflight_line="$(rg -n '^preflight_production_baseline$' "$SCRIPT" | head -n 1 | cut -d: -f1)"
normalize_line="$(rg -n '^normalize_live_layout$' "$SCRIPT" | head -n 1 | cut -d: -f1)"
[[ -n "$preflight_line" && -n "$normalize_line" && "$preflight_line" -lt "$normalize_line" ]] || {
    printf 'Production baseline health must be verified before changing live paths or services.\n' >&2
    exit 1
}
rg -F 'BYTEDEPTH_PRODUCTION_RUNTIME_MODE=production-native' "$TARGET" >/dev/null
rg -F 'install-production-stack.sh' "$SCRIPT" >/dev/null
if rg --files "$ROOT/deploy" "$ROOT/scripts" "$ROOT/docs" \
    | awk -F/ '{name=tolower($NF); if (name ~ /(docker|red.?green|production-green|blue.?green)/) {print; found=1}} END {exit !found}'; then
    printf 'Deployment, script, or knowledge-base filenames retain removed runtime labels.\n' >&2
    exit 1
fi
if rg -n -i 'docker|production-green|production-blue|red.?green deployment|红绿部署|蓝环境|绿环境' \
    "$ROOT/deploy" "$ROOT/docs" --glob '*.md' --glob '*.sh' --glob '*.in' \
    --glob '!CHANGELOG.md' --glob '!test-*' >/dev/null; then
    printf 'Active deployment guidance retains removed runtime or cutover details.\n' >&2
    exit 1
fi
if rg -n -i 'docker|compose|red.?green|blue|green' "$SCRIPT" "$TARGET"; then
    printf 'Production deployment path retains a removed runtime or cutover concept.\n' >&2
    exit 1
fi
if rg -n 'chown (root|0):|install .* -o root|install .* -g root' "$ROOT/deploy" --glob '*.sh' --glob '*.in'; then
    printf 'Deployment scripts must not assign project resources to root.\n' >&2
    exit 1
fi
printf 'Production native deployment contract passed.\n'
