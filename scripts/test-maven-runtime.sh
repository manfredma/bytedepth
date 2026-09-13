#!/usr/bin/env bash
set -Eeuo pipefail

readonly ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly VERSION=3.9.11
readonly IMAGE="maven:${VERSION}-eclipse-temurin-25"

[[ -x "$ROOT/mvnw" ]] || { printf 'Maven Wrapper must be executable.\n' >&2; exit 1; }
grep -Fqx "distributionUrl=https://maven.aliyun.com/repository/public/org/apache/maven/apache-maven/${VERSION}/apache-maven-${VERSION}-bin.zip" "$ROOT/.mvn/wrapper/maven-wrapper.properties"
grep -Fqx -- '--enable-native-access=ALL-UNNAMED' "$ROOT/.mvn/jvm.config"
grep -Fqx -- '--sun-misc-unsafe-memory-access=allow' "$ROOT/.mvn/jvm.config"
grep -Fqx -- '-Xshare:off' "$ROOT/.mvn/jvm.config"
grep -Fqx "FROM ${IMAGE} AS build" "$ROOT/Dockerfile"
grep -Fqx 'COPY .mvn/jvm.config .mvn/jvm.config' "$ROOT/Dockerfile"
grep -Fqx "ENV MAVEN_OPTS='-Xmx512m'" "$ROOT/Dockerfile"
grep -Fqx "readonly STAGING_MAVEN_IMAGE=${IMAGE}" "$ROOT/deploy/lib/staging-runtime.sh"
rg -F -- '"$SOURCE_ROOT/mvnw"' "$ROOT/deploy/bootstrap-staging-runtime.sh" >/dev/null
rg -F -- '"$STAGING_MAVEN_IMAGE"' "$ROOT/deploy/run-staging-integration-tests.sh" >/dev/null
rg -F -- '"$SOURCE_ROOT/mvnw"' "$ROOT/scripts/run-local-quality.sh" >/dev/null
rg -F -- '"$SOURCE_ROOT/mvnw"' "$ROOT/scripts/prepare-release.sh" >/dev/null
if rg -q 'maven:3\.9-eclipse-temurin-25|JAVA_HOME=.*mvn ' "$ROOT/deploy" "$ROOT/Dockerfile"; then
    printf 'Maven runtime must not use a floating image or host Maven.\n' >&2
    exit 1
fi
printf 'Maven runtime constraint check passed.\n'
