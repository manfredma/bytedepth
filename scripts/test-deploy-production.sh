#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT
exec "$ROOT/scripts/test-production-red-green-deployment.sh"
