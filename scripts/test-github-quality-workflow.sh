#!/usr/bin/env bash
set -Eeuo pipefail

readonly SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly WORKFLOW="$SOURCE_ROOT/.github/workflows/quality.yml"

[[ -f "$WORKFLOW" ]]
rg -Fq 'pull_request:' "$WORKFLOW"
rg -Fq 'push:' "$WORKFLOW"
rg -Fq -- "- 'main'" "$WORKFLOW"
rg -Fq 'actions/checkout@v5' "$WORKFLOW"
rg -Fq 'actions/setup-node@v5' "$WORKFLOW"
rg -Fq 'actions/setup-java@v5' "$WORKFLOW"
rg -Fq "java-version: '25'" "$WORKFLOW"
rg -Fq 'bash scripts/run-local-quality.sh' "$WORKFLOW"
! rg -qi 'ssh|staging|production|deploy-staging|deploy-production' "$WORKFLOW"

printf 'GitHub quality workflow constraint check passed.\n'
