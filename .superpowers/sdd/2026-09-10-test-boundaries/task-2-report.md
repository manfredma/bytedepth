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

## Review Round 1

- Replaced detached POM text greps with namespace-safe `xmllint` XPath assertions. They require exactly one Failsafe plugin globally and prove that it is nested only in the inactive `staging-integration` profile, uses the full expected `argLine`, includes only `**/*IT.java`, and binds exactly `integration-test` and `verify`.
- The same structural test asserts Surefire's `**/*IT.java` exclusion and rejects any `staging-integration` reference in the unit-only coverage script.
- A copied, deliberately malformed POM renames the staging profile; the test confirms the structural assertion rejects it. This guards against the detached-grep false positive identified in review.
- Re-ran the permitted Java 25 build and test gates: six-module `clean install -DskipTests`, 289 offline tests, and warning scans all passed. No Failsafe, Docker, Redis, or E2E command was run locally.
