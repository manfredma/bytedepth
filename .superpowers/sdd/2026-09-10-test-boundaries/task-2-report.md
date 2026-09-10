# Task 2 Report: Separate Maven Test Lifecycles

## Result

- Root Surefire configuration now excludes `**/*IT.java`, retaining its existing defaults and Java/Mockito agent `argLine`.
- The inactive-by-default `staging-integration` profile binds Failsafe's `integration-test` and `verify` goals, discovers only `**/*IT.java`, and uses the same agent/native-access `argLine`.
- The changed-coverage gate remains profile-free. Its comment records why Failsafe must not be activated there: it is staging-only while local coverage is unit-only.
- The release-script test now enforces these POM invariants.

## TDD Evidence

1. Added the shell assertions for the Surefire exclusion, staging profile, Failsafe plugin/goals, and IT include.
2. Ran `bash scripts/test-prepare-release.sh` before changing the POM; it failed at the new missing-configuration assertion (expected red).
3. Added the minimal POM lifecycle configuration and coverage-script comment.
4. Re-ran `bash scripts/test-prepare-release.sh`; it passed (green).

## Offline Verification

| Command | Result |
| --- | --- |
| `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn clean install -DskipTests -Dsort.skip=true` | PASS — six-module build, no warnings |
| `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn test -Dsort.skip=true` | PASS — 289 tests, 0 failures/errors; log checked for warnings |
| `bash scripts/test-prepare-release.sh` | PASS |
| Surefire report check for `RedisRateLimitAdapterIT` | PASS — no `RedisRateLimitAdapterIT` report exists locally |
| `git diff --check` | PASS — no whitespace errors |

No Failsafe integration, Docker, Redis, or E2E command was run locally. The matching `RedisRateLimitAdapterITTest` is a deliberately offline `*Test` helper and remains eligible for Surefire.

## Concern

The Failsafe profile has intentionally not been executed locally. Task 3 must supply the required Redis properties and invoke `mvn verify -Pstaging-integration` from the staging Compose network.
