#!/usr/bin/env bash
set -Eeuo pipefail

SOURCE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
readonly SOURCE_ROOT
readonly RUNNER="$SOURCE_ROOT/deploy/run-staging-integration-tests.sh"
readonly POM="$SOURCE_ROOT/pom.xml"
readonly IT_TESTS=(
    "$SOURCE_ROOT/bytedepth-start/src/test/java/manfred/bytedepth/integration/PostRepositoryIT.java"
    "$SOURCE_ROOT/bytedepth-start/src/test/java/manfred/bytedepth/integration/AnnotationContentUpdateIT.java"
    "$SOURCE_ROOT/bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/ratelimit/RedisRateLimitAdapterIT.java"
)

[[ -x "$RUNNER" ]] || { printf 'Expected executable staging integration runner.\n' >&2; exit 1; }

assert_no_docker_or_testcontainers() {
    local file

    for file in "${IT_TESTS[@]}" "$RUNNER"; do
        if rg -n -i 'docker|compose|testcontainers|mysqlcontainer|serviceconnection|docker\.sock|STAGING_MAVEN_IMAGE' "$file"; then
            printf 'Task 3 must not retain Docker/Testcontainers integration execution: %s\n' "$file" >&2
            exit 1
        fi
    done
}

assert_it_tests_use_external_profile() {
    local file

    for file in "${IT_TESTS[0]}" "${IT_TESTS[1]}"; do
        rg -q 'SpringBootTest' "$file" || {
            printf 'Integration test must remain a Spring Boot test: %s\n' "$file" >&2
            exit 1
        }
    done
    rg -q 'RedisRateLimitAdapter' "${IT_TESTS[2]}"
    ! rg -n 'jdbc:mysql://|spring\.datasource|MYSQL|localhost:3306' "${IT_TESTS[@]}"
}

assert_failsafe_uses_staging_profile() {
    local profile_block

    profile_block="$(awk '
        /<id>staging-integration<\/id>/ { in_profile = 1 }
        in_profile { print }
        in_profile && /<\/profile>/ { exit }
    ' "$POM")"
    [[ "$profile_block" == *"<spring.profiles.active>\${env.SPRING_PROFILES_ACTIVE}</spring.profiles.active>"* ]]
    [[ "$profile_block" == *"<bytedepth.it.manifest>\${env.BYTEDEPTH_TEST_MANIFEST}</bytedepth.it.manifest>"* ]]
    [[ "$profile_block" == *"<bytedepth.it.redis.host>\${env.BYTEDEPTH_STAGING_IT_REDIS_HOST}</bytedepth.it.redis.host>"* ]]
    [[ "$profile_block" == *"<bytedepth.it.redis.database>\${env.BYTEDEPTH_STAGING_IT_REDIS_DATABASE}</bytedepth.it.redis.database>"* ]]
    [[ "$profile_block" == *"<bytedepth.it.redis.key-namespace>\${env.BYTEDEPTH_STAGING_IT_REDIS_KEY_NAMESPACE}</bytedepth.it.redis.key-namespace>"* ]]
}

assert_runner_uses_manifest_transaction() {
    rg -q 'deployment-test\.lock' "$RUNNER"
    rg -q 'provision-staging-test-slot\.sh' "$RUNNER"
    rg -q 'teardown-staging-test-slot\.sh' "$RUNNER"
    rg -q 'BYTEDEPTH_TEST_MANIFEST' "$RUNNER"
    rg -q 'SPRING_PROFILES_ACTIVE.*staging-it|== staging-it' "$RUNNER"
    rg -q '\./mvnw -o -Pstaging-integration verify' "$RUNNER"
    rg -q 'trap on_exit EXIT' "$RUNNER"
    rg -q 'cleanup_slot' "$RUNNER"
    ! rg -n -- '-Dbytedepth\.it\.(redis|mysql)|-Dspring\.datasource|TESTCONTAINERS' "$RUNNER"
}

assert_evidence_contract() {
    rg -q 'runtime_mode=host-native' "$RUNNER"
    rg -q 'run_id=' "$RUNNER"
    rg -q 'test_resource_manifest_sha=' "$RUNNER"
    rg -q 'cleanup=result=passed' "$RUNNER"
    rg -q 'commit=%s|candidate_sha|tested_commit' "$RUNNER"
    rg -q 'result=passed' "$RUNNER"
    rg -q 'invalidate_evidence' "$RUNNER"
    ! rg -n 'default.*password|temporary.*account|admin123|changeme' "$RUNNER"
}

assert_no_docker_or_testcontainers
assert_it_tests_use_external_profile
assert_failsafe_uses_staging_profile
assert_runner_uses_manifest_transaction
assert_evidence_contract

printf 'staging integration runner contract tests passed\n'
