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
    if rg -n -i 'testcontainers|spring-boot-testcontainers' "$POM" "$SOURCE_ROOT/bytedepth-start/pom.xml"; then
        printf 'Task 3 must remove Testcontainers dependencies from the integration test path.\n' >&2
        exit 1
    fi
    [[ ! -e "$SOURCE_ROOT/bytedepth-start/src/test/resources/testcontainers.properties" ]]
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
    if rg -n 'bytedepth\.it\.(manifest|redis\.(host|port|password|database|key-namespace))' <<< "$profile_block"; then
        return 1
    fi
    if rg -n 'BYTEDEPTH_STAGING_IT_REDIS_PASSWORD' <<< "$profile_block"; then
        return 1
    fi
    rg -q 'BYTEDEPTH_STAGING_IT_REDIS_HOST|BYTEDEPTH_STAGING_IT_REDIS_DATABASE' "$SOURCE_ROOT/bytedepth-start/src/main/resources/application-staging-it.yml"
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
    rg -q 'state-uncertain' "$RUNNER"
    if rg -n -- '-Dbytedepth\.it\.(redis|mysql)|-Dspring\.datasource|TESTCONTAINERS' "$RUNNER"; then
        return 1
    fi
    rg -q 'BYTEDEPTH_STAGING_IT_REDIS_PASSWORD' "$RUNNER"
    rg -q 'require_memory_headroom' "$RUNNER"
    rg -q 'MINIMUM_MEMORY_AVAILABLE_KIB=262144' "$RUNNER"
    rg -q 'MINIMUM_MEMORY_AFTER_APP_STOP_KIB=524288' "$RUNNER"
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
