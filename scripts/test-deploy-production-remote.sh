#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT
readonly SCRIPT="$ROOT/deploy/deploy-production-remote.sh"
readonly HOST_SCRIPT="$ROOT/deploy/deploy-production.sh"

[[ -x "$SCRIPT" ]] || { printf 'Expected executable local production deployment wrapper.\n' >&2; exit 1; }
grep -Fqx 'readonly PRODUCTION_HOST=175.24.197.202' "$SCRIPT"
grep -Fqx 'readonly REMOTE_ROOT=/opt/bytedepth' "$SCRIPT"
grep -Fq 'BYTEDEPTH_PRODUCTION_SSH_KEY' "$SCRIPT"
grep -Fq 'BYTEDEPTH_PRODUCTION_SSH_KNOWN_HOSTS' "$SCRIPT"
grep -Fq 'UserKnownHostsFile=' "$SCRIPT"
grep -Fq 'StrictHostKeyChecking=yes' "$SCRIPT"
grep -Fq 'scp ' "$SCRIPT"
if grep -Fq 'git -C "$SOURCE_ROOT" fetch' "$SCRIPT"; then
    printf 'Local production wrapper must not refetch an already validated release Tag.\n' >&2
    exit 1
fi
grep -Fq "pom_version=\"\$(git -C \"\$SOURCE_ROOT\" show \"\$commit:pom.xml\" | sed -n 's@^[[:space:]]*<version>\\([^<]*\\)</version>[[:space:]]*\$@\\1@p' | head -n 1)\"" "$SCRIPT"
if grep -Fq 'GIT_SSH_COMMAND="$git_ssh_command"' "$SCRIPT"; then
    printf 'Local GitHub fetch must not use the production host SSH key.\n' >&2
    exit 1
fi
grep -Fq "git checkout --detach '\$TAG'" "$SCRIPT"
grep -Fq "git status --porcelain=v1 --untracked-files=no" "$SCRIPT"
grep -Fq -- '--artifact' "$SCRIPT"
grep -Fq -- '--manifest' "$SCRIPT"
grep -Fq 'verify-production-release.sh' "$SCRIPT"
grep -Fq 'release-history' "$SCRIPT"
grep -Fq 'build_release_artifact "$CHECKOUT_DIR" "$TAG" "$commit" "$ARTIFACT_DIR" "${TAG#v}"' "$SCRIPT"
grep -Fq 'WARNING' "$SCRIPT"
if rg -n -i 'compose|mvn ' "$SCRIPT" >/dev/null; then
    printf 'Local production deployment wrapper must not invoke Compose or Maven.\n' >&2
    exit 1
fi
grep -Fq 'restore_current_release' "$HOST_SCRIPT"
grep -Fq 'runtime_mode=production-native' "$HOST_SCRIPT"

printf 'Local production deployment contract passed.\n'
