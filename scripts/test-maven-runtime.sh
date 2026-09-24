#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly ROOT
readonly VERSION=3.9.11

[[ -x "$ROOT/mvnw" ]] || { printf 'Maven Wrapper must be executable.\n' >&2; exit 1; }
grep -Fqx "distributionUrl=https://maven.aliyun.com/repository/public/org/apache/maven/apache-maven/${VERSION}/apache-maven-${VERSION}-bin.zip" "$ROOT/.mvn/wrapper/maven-wrapper.properties"
grep -Fqx -- '--enable-native-access=ALL-UNNAMED' "$ROOT/.mvn/jvm.config"
grep -Fqx -- '--sun-misc-unsafe-memory-access=allow' "$ROOT/.mvn/jvm.config"
grep -Fqx -- '-Xshare:off' "$ROOT/.mvn/jvm.config"
rg -F -- '"$SOURCE_ROOT/mvnw"' "$ROOT/scripts/run-local-quality.sh" >/dev/null
rg -F -- '"$SOURCE_ROOT/mvnw"' "$ROOT/scripts/prepare-release.sh" >/dev/null
rg -F -- './mvnw -o -Pstaging-integration verify' "$ROOT/deploy/run-staging-integration-tests.sh" >/dev/null
if rg -n --regexp '(^|[[:space:]])mvn([[:space:]]|$)|MAVEN_OPTS=|maven:[0-9]' \
    "$ROOT/deploy/run-staging-integration-tests.sh" "$ROOT/scripts/run-local-quality.sh" "$ROOT/scripts/prepare-release.sh"; then
    printf 'Maven invocations must use the repository Maven Wrapper without host Maven or floating images.\n' >&2
    exit 1
fi
printf 'Maven Wrapper runtime constraint check passed.\n'
