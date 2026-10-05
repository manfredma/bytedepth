#!/usr/bin/env bash
set -Eeuo pipefail

# Local checks for the project-to-platform boundary.  Staging deployment,
# integration, E2E, acceptance and production promotion are platform tasks.
# Called before a PR/platform handoff; this script is read-only and must not
# be used as a project-side deployment entry point.
SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

bash "$SOURCE_ROOT/scripts/test-release-platform-only.sh"
bash "$SOURCE_ROOT/scripts/test-run-local-quality.sh"
bash "$SOURCE_ROOT/scripts/test-maven-runtime.sh"
bash "$SOURCE_ROOT/scripts/test-host-native-docs.sh"
bash "$SOURCE_ROOT/scripts/test-platform-portability.sh"
bash "$SOURCE_ROOT/scripts/test-staging-checklist.sh"

printf 'Staging checklist passed.\n'
