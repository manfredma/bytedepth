# Task 3 report

Status: PASS for the local Task 3 implementation and contract gates.

## Implementation

- Removed the MySQL Testcontainers lifecycle from `PostRepositoryIT` and
  `AnnotationContentUpdateIT`; both continue to use `@SpringBootTest` and now
  obtain datasource settings from the active `staging-it` profile.
- Kept `RedisRateLimitAdapterIT` as a direct Redis integration test and wired
  its host, port, password, logical DB and run namespace through the Maven
  profile environment contract.
- Updated the `staging-integration` Failsafe profile to activate the exported
  Spring profile and pass only the manifest path plus profile-derived Redis
  system properties; credentials are never command-line arguments.
- Replaced the Docker/Testcontainers runner with a host-native transaction:
  shared deployment lock, explicit root-owned test inputs, app stop, isolated
  manifest provisioning, root-only `staging-it.env` loading, wrapper Maven
  execution, mandatory teardown/restoration, warning gate and delayed evidence
  write.
- Evidence now records `runtime_mode=host-native`, `run_id`,
  `test_resource_manifest_sha`, `cleanup=result=passed`, the full candidate
  commit SHA, command, UTC timestamp and `result=passed`. Existing evidence is
  invalidated before every run.

## TDD and verification

- RED: the new contract script failed against the old runner on the retained
  `MySQLContainer` declaration.
- GREEN: `bash scripts/test-run-staging-integration-tests.sh` passed after the
  runner and IT changes.
- `shellcheck deploy/run-staging-integration-tests.sh
  scripts/test-run-staging-integration-tests.sh`: passed with no warnings.
- `git diff --check`: passed.
- `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./mvnw clean install
  -DskipTests -Dsort.skip=true`: `BUILD SUCCESS`.
- `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./mvnw -Dsort.skip=true test`:
  `BUILD SUCCESS`; module results were 43, 274, 231, 79 and 317 tests with
  zero failures/errors/skips and zero `WARNING`/`WARN` lines.

No real staging or production services were contacted locally. The real
MySQL/Redis/Meilisearch provisioning and teardown remains a staging-only
verification step; no implementation blocker remains in Task 3.
