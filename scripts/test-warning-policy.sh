#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly POLICY="$ROOT/deploy/lib/warning-policy.sh"
readonly TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

source "$POLICY"

cat > "$TEMP_DIR/allowlisted.log" <<'LOG'
[INFO] dependency collection started
[WARNING] 1 problem was encountered while building the effective model for org.javassist:javassist:jar:3.21.0-GA during dependency collection step for
Problem
* 'dependencies.dependency.systemPath' for com.sun:tools:jar refers to a non-existing file
LOG
warning_policy_check_file "$TEMP_DIR/allowlisted.log"

cat > "$TEMP_DIR/unknown.log" <<'LOG'
[WARNING] an unregistered dependency warning
LOG
if warning_policy_check_file "$TEMP_DIR/unknown.log"; then
    printf 'Expected an unknown WARNING to fail the policy.\n' >&2
    exit 1
fi

cat > "$TEMP_DIR/framework.log" <<'LOG'
WARN a framework warning must remain blocking
LOG
if warning_policy_check_file "$TEMP_DIR/framework.log"; then
    printf 'Expected an unknown WARN to fail the policy.\n' >&2
    exit 1
fi

printf 'Warning policy contract passed.\n'
