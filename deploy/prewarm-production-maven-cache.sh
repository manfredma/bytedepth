#!/usr/bin/env bash
set -Eeuo pipefail

# Populate the production host's shared Maven repository before Docker's
# immutable offline build, using the same Java 25 Maven image as Dockerfile.
# Usage: sudo ./deploy/prewarm-production-maven-cache.sh /opt/bytedepth

RELEASE_DIR="${1:?Usage: $0 RELEASE_DIR}"
CACHE_DIR="${MAVEN_CACHE_DIR:-/opt/shared-maven/repository}"
MAVEN_IMAGE="${MAVEN_IMAGE:-maven:3.9.11-eclipse-temurin-25}"
[[ -d "$RELEASE_DIR" && -f "$RELEASE_DIR/pom.xml" ]] || { echo "ERROR: invalid release directory" >&2; exit 1; }
source "$RELEASE_DIR/deploy/lib/warning-policy.sh"
install -d -o root -g root -m 0755 "$CACHE_DIR"
log_file="$(mktemp)"
trap 'rm -f "$log_file"' EXIT
if ! docker run --rm --network host \
    -v "$RELEASE_DIR:/workspace" \
    -v "$CACHE_DIR:/root/.m2/repository" \
    -w /workspace "$MAVEN_IMAGE" \
    mvn -s .mvn/settings.xml -B clean install -DskipTests -Dsort.skip=true 2>&1 | tee "$log_file"; then
    echo "ERROR: production Maven cache prewarm failed" >&2
    exit 1
fi
if ! warning_policy_check_file "$log_file"; then
    echo "ERROR: production Maven cache prewarm emitted an unallowlisted WARNING" >&2
    exit 1
fi
echo "Production Maven cache prewarm passed for $RELEASE_DIR."
