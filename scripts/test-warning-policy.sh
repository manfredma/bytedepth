#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly POLICY="$ROOT/deploy/lib/warning-policy.sh"
readonly TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

source "$POLICY"

cat > "$TEMP_DIR/allowlisted.log" << 'LOG'
[INFO] dependency collection started
[WARNING] 1 problem was encountered while building the effective model for org.javassist:javassist:jar:3.21.0-GA during dependency collection step for
Problem
* 'dependencies.dependency.systemPath' for com.sun:tools:jar refers to a non-existing file
LOG
warning_policy_check_file "$TEMP_DIR/allowlisted.log"

cat > "$TEMP_DIR/unknown.log" << 'LOG'
[WARNING] an unregistered dependency warning
LOG
if warning_policy_check_file "$TEMP_DIR/unknown.log"; then
  printf 'Expected an unknown WARNING to fail the policy.\n' >&2
  exit 1
fi

cat > "$TEMP_DIR/framework.log" << 'LOG'
WARN a framework warning must remain blocking
LOG
if warning_policy_check_file "$TEMP_DIR/framework.log"; then
  printf 'Expected an unknown WARN to fail the policy.\n' >&2
  exit 1
fi

# The known diagnostic measures seeding latency, not an entropy or provider error.
cat > "$TEMP_DIR/random.log" << 'LOG'
2026-10-17T11:00:09+08:00 host java[123]: 2026-10-17T11:00:09.276+08:00  WARN 123 --- [bytedepth] [exec-3] o.a.c.util.SessionIdGeneratorBase : Creation of SecureRandom instance for session ID generation using [SHA1PRNG] took [109] milliseconds.
LOG
warning_policy_check_file "$TEMP_DIR/random.log"
for invalid in \
  'Creation of SecureRandom instance for session ID generation using [SHA1PRNG] took [1000] milliseconds.' \
  'Creation of SecureRandom instance for session ID generation using [unknown] took [109] milliseconds.' \
  'SecureRandom initialization failed' \
  'Creation of SecureRandom instance for session ID generation using [SHA1PRNG] took [109] milliseconds. WARNING additional failure'; do
  printf 'WARN 123 --- [bytedepth] [exec-3] o.a.c.util.SessionIdGeneratorBase : %s\n' "$invalid" > "$TEMP_DIR/random-invalid.log"
  if warning_policy_check_file "$TEMP_DIR/random-invalid.log"; then
    printf 'Expected altered or slow SecureRandom diagnostic to remain blocking.\n' >&2
    exit 1
  fi
done
printf 'WARN other.Logger : Creation of SecureRandom instance for session ID generation using [SHA1PRNG] took [109] milliseconds.\n' > "$TEMP_DIR/random-other.log"
if warning_policy_check_file "$TEMP_DIR/random-other.log"; then
  printf 'Expected a different logger to remain blocking.\n' >&2
  exit 1
fi

printf 'Warning policy contract passed.\n'
