#!/usr/bin/env bash
set -Eeuo pipefail

# Guards local/CI entry points against unguarded macOS-only shell commands.
readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly JAVA_RUNTIME="$SOURCE_ROOT/scripts/lib/java-25.sh"
readonly PORTABLE_SCRIPTS=(
    "$SOURCE_ROOT/scripts/run-local-quality.sh"
    "$SOURCE_ROOT/scripts/prepare-release.sh"
    "$SOURCE_ROOT/scripts/verify-changed-coverage.sh"
)

[[ -f "$JAVA_RUNTIME" ]]
grep -Fqx 'resolve_java_25() {' "$JAVA_RUNTIME"
for script in "${PORTABLE_SCRIPTS[@]}"; do
    grep -Fqx 'source "$SOURCE_ROOT/scripts/lib/java-25.sh"' "$script"
done
if rg -l '/usr/libexec/java_home' "${PORTABLE_SCRIPTS[@]}" >/dev/null; then
    printf 'Portable entry points must use scripts/lib/java-25.sh, not macOS-only Java discovery.\n' >&2
    exit 1
fi
if rg -F "sed -i ''" "$SOURCE_ROOT/scripts" "$SOURCE_ROOT/deploy" --glob '*.sh' --glob '!test-platform-portability.sh' >/dev/null; then
    printf 'Do not use BSD-only sed -i; rewrite through a temporary file instead.\n' >&2
    exit 1
fi

printf 'Platform portability contract passed.\n'
