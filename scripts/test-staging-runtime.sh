#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly LIBRARY="$ROOT/deploy/lib/staging-runtime.sh"
readonly BOOTSTRAP="$ROOT/deploy/bootstrap-staging-runtime.sh"
readonly INTEGRATION_RUNNER="$ROOT/deploy/run-staging-integration-tests.sh"

[[ -x "$BOOTSTRAP" ]] || {
    printf 'Expected executable staging runtime bootstrap.\n' >&2
    exit 1
}
rg -q 'mvnw.*clean install -DskipTests -Dsort.skip=true' "$BOOTSTRAP" || {
    printf 'Bootstrap must prewarm the complete Maven reactor.\n' >&2
    exit 1
}
rg -q 'mvnw.*verify -DskipTests -Dsort.skip=true' "$BOOTSTRAP" || {
    printf 'Bootstrap must resolve verification-lifecycle plugins for offline integration tests.\n' >&2
    exit 1
}
rg -q 'mvnw.*Pstaging-integration verify -DskipTests -Dsort.skip=true' "$BOOTSTRAP" || {
    printf 'Bootstrap must prewarm integration profile dependencies before deployment.\n' >&2
    exit 1
}
rg -q 'dependency:go-offline' "$BOOTSTRAP" || {
    printf 'Bootstrap must pre-resolve Maven plugins and plugin dependencies.\n' >&2
    exit 1
}
rg -Fq 'warning_policy_check_file' "$BOOTSTRAP" || {
    printf 'Bootstrap must apply the shared warning allowlist before rollout.\n' >&2
    exit 1
}
rg -q 'includePlugins=true' "$BOOTSTRAP" || {
    printf 'Bootstrap must pre-resolve Maven plugin transitive dependencies.\n' >&2
    exit 1
}
rg -q 'includePluginDependencies=true' "$BOOTSTRAP" || {
    printf 'Bootstrap must pre-resolve Maven plugin dependencies.\n' >&2
    exit 1
}
rg -q 'staging_bootstrap_dependency_probe' "$BOOTSTRAP" || {
    printf 'Bootstrap must probe dynamic test-runtime resolution without executing tests.\n' >&2
    exit 1
}
rg -q 'surefire.failIfNoSpecifiedTests=false' "$BOOTSTRAP" || {
    printf 'Bootstrap dependency probe must not execute matched unit tests.\n' >&2
    exit 1
}
rg -q 'failsafe.failIfNoSpecifiedTests=false' "$BOOTSTRAP" || {
    printf 'Bootstrap dependency probe must not execute matched integration tests.\n' >&2
    exit 1
}
rg -Uq '(?s)<dependencies>.*?<groupId>org\.junit\.platform</groupId>\s*<artifactId>junit-platform-launcher</artifactId>\s*<scope>test</scope>' "$ROOT/pom.xml" || {
    printf 'The inherited test runtime must explicitly declare JUnit Platform Launcher.\n' >&2
    exit 1
}
rg -q 'mvnw.*-o .*Pstaging-integration verify' "$BOOTSTRAP" || {
    printf 'Bootstrap must validate offline staging-integration command path before test execution.\n' >&2
    exit 1
}
if rg -q 'playwright install' "$BOOTSTRAP"; then
    printf 'Bootstrap must reuse the staged Chromium instead of downloading a browser.\n' >&2
    exit 1
fi
rg -F '"${1:-}" != --lock-held' "$BOOTSTRAP" >/dev/null || {
    printf 'Bootstrap must support an inherited staging lock.\n' >&2
    exit 1
}
rg -F 'exec flock -x "$LOCK_FILE" "$0" --lock-held' "$BOOTSTRAP" >/dev/null || {
    printf 'Bootstrap must re-exec under its own staging lock when not inherited.\n' >&2
    exit 1
}
rg -F -- '--ensure' "$BOOTSTRAP" >/dev/null || {
    printf 'Bootstrap must support validation-only runtime ensure mode.\n' >&2
    exit 1
}
rg -F 'npm ci --ignore-scripts --no-audit --no-fund' "$BOOTSTRAP" >/dev/null || {
    printf 'Bootstrap must install project dependencies without downloading a project-local browser.\n' >&2
    exit 1
}

readonly TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT
readonly SOURCE_ROOT="$TEMP_DIR/source"
readonly STATE_DIR="$TEMP_DIR/state"
readonly FIXTURE_CHROMIUM="$TEMP_DIR/shared-e2e/chrome-linux64/chrome"
readonly RUNTIME_LIBRARY="$TEMP_DIR/staging-runtime.sh"
mkdir -p "$SOURCE_ROOT" "$(dirname "$FIXTURE_CHROMIUM")" "$STATE_DIR/runtime"
printf 'lockfile\n' > "$SOURCE_ROOT/package-lock.json"
printf '<project/>\n' > "$SOURCE_ROOT/pom.xml"
cat > "$FIXTURE_CHROMIUM" <<'SCRIPT'
#!/usr/bin/env bash
printf 'Google Chrome for Testing 151.0.7922.34\n'
SCRIPT
chmod +x "$FIXTURE_CHROMIUM"

grep -Fqx 'readonly SHARED_CHROMIUM_EXECUTABLE=/opt/shared-e2e/chrome-linux64/chrome' "$LIBRARY"
grep -Fqx 'readonly SHARED_MAVEN_REPOSITORY=/opt/shared-maven/repository' "$LIBRARY"
grep -Fqx 'readonly SHARED_MAVEN_LOCK=/opt/shared-maven/repository.lock' "$LIBRARY"
if rg -q '\.e2e/chrome-linux64|chromium_path|chromium_sha' "$LIBRARY"; then
    printf 'Staging runtime must not retain a project-local Chromium contract.\n' >&2
    exit 1
fi
rg -F -- '-Dmaven.repo.local="$SHARED_MAVEN_REPOSITORY"' "$BOOTSTRAP" >/dev/null
rg -F 'flock -x 8' "$BOOTSTRAP" >/dev/null
grep -Fqx 'readonly STAGING_MAVEN_IMAGE=maven:3.9.11-eclipse-temurin-25' "$LIBRARY"
rg -F -- '"$SOURCE_ROOT/mvnw"' "$BOOTSTRAP" >/dev/null || {
    printf 'Bootstrap must use the pinned Maven Wrapper.\n' >&2
    exit 1
}
rg -F -- '"$STAGING_MAVEN_IMAGE"' "$INTEGRATION_RUNNER" >/dev/null || {
    printf 'Integration runner must use the shared staging Maven container image.\n' >&2
    exit 1
}
rg -F 'git -c safe.directory="$SOURCE_ROOT" -C "$SOURCE_ROOT" archive --format=tar "$tested_commit"' "$INTEGRATION_RUNNER" >/dev/null || {
    printf 'Integration runner must archive the tested commit instead of copying Git metadata and caches.\n' >&2
    exit 1
}
if rg -Fq 'cp -a "$SOURCE_ROOT/." "$WORK_DIR/source/"' "$INTEGRATION_RUNNER"; then
    printf 'Integration runner must not copy .git, node_modules, or build output into its disposable workspace.\n' >&2
    exit 1
fi
rg -q 'MINIMUM_WORKSPACE_FREE_KIB=2097152' "$INTEGRATION_RUNNER" || {
    printf 'Integration runner must fail before workspace creation when staging disk headroom is unsafe.\n' >&2
    exit 1
}
if rg -q 'JAVA_HOME=.*mvn ' "$BOOTSTRAP"; then
    printf 'Bootstrap must not prewarm dependencies with the host Maven runtime.\n' >&2
    exit 1
fi
sed 's@^readonly SHARED_CHROMIUM_EXECUTABLE=/opt/shared-e2e/chrome-linux64/chrome$@readonly SHARED_CHROMIUM_EXECUTABLE='"$FIXTURE_CHROMIUM"'@' \
    "$LIBRARY" > "$RUNTIME_LIBRARY"
source "$RUNTIME_LIBRARY"

readonly MANIFEST="$STATE_DIR/runtime/manifest"
write_runtime_manifest "$MANIFEST" "$SOURCE_ROOT"
require_staging_runtime "$MANIFEST" "$SOURCE_ROOT"
if rg -q '^commit=' "$MANIFEST"; then
    printf 'Runtime manifest must describe reusable dependency inputs, not a source checkout.\n' >&2
    exit 1
fi
printf 'source-only change\n' > "$SOURCE_ROOT/network.css"
if ! require_staging_runtime "$MANIFEST" "$SOURCE_ROOT"; then
    printf 'Expected runtime manifest to be reusable for a source-only checkout change.\n' >&2
    exit 1
fi

printf 'changed lockfile\n' >> "$SOURCE_ROOT/package-lock.json"
if ! require_staging_runtime "$MANIFEST" "$SOURCE_ROOT"; then
    # lockfile changed, manifest should be rejected
    :
else
    printf 'Expected stale runtime manifest to be rejected after lockfile change.\n' >&2
    exit 1
fi

printf 'Staging runtime contract passed.\n'
