# Test Boundary Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make offline unit tests deterministic on any machine and move every cross-process integration test plus E2E execution to staging-only gates.

**Architecture:** Maven Surefire owns only `*Test` unit tests. Maven Failsafe owns `*IT` integration tests behind an explicit `staging-integration` profile. A staging-only shell entrypoint launches a disposable Java 25 Maven container on the existing Compose network, passes only test-scoped service DNS and credentials, then runs Failsafe; browser E2E remains a separate staging-host command.

**Tech Stack:** Java 25, Maven Surefire/Failsafe, JaCoCo, Docker Compose, Bash, JUnit 5, staging Docker network.

**Spec:** `docs/superpowers/specs/2026-09-10-test-boundary-design.md`

## Global Constraints

- Do not add Maven modules or run Redis/MySQL/Flyway/Docker/Testcontainers/Nginx integration tests locally.
- `*Test` must execute offline and without external processes; `*IT` and all E2E execute only on staging.
- Use the staging Compose service DNS names `redis`, `mysql`, and `meilisearch`; never publish data-service ports or depend on `localhost`.
- All Maven, static-check, staging integration and E2E output must contain zero WARNING.
- All changed production Java lines, branches and methods remain 100% covered by unit tests; integration execution is not a substitute for unit coverage.
- Candidate branches deploy to staging before integration/E2E; owner acceptance precedes main merge and release.

---

### Task 1: Make the Redis adapter test an explicit staging integration test

**Files:**
- Rename: `bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/ratelimit/RedisRateLimitAdapterTest.java` → `RedisRateLimitAdapterIT.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/ratelimit/RateLimitRedisProperties.java`
- Create: `bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/ratelimit/RedisRateLimitAdapterIT.java`

**Interfaces:**
- Consumes: system properties `bytedepth.it.redis.host`, `bytedepth.it.redis.port`, `bytedepth.it.redis.password` supplied only by the staging runner.
- Produces: a `*IT` class that proves real Redis bucket consumption and always removes its UUID test key.

- [ ] **Step 1: Write the failing isolated configuration test**

Add a test that constructs `RateLimitRedisProperties`, calls a package-visible test helper on `RedisRateLimitAdapterIT`, and asserts it assigns `redis`, `6379`, and a supplied test password without consulting `localhost`.

```java
assertThat(RedisRateLimitAdapterIT.stagingProperties("redis", "secret").getHost()).isEqualTo("redis");
```

- [ ] **Step 2: Run the test as a unit compile check**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-infrastructure -Dtest=RedisRateLimitAdapterITTest test`

Expected: FAIL because the helper does not exist.

- [ ] **Step 3: Rename and isolate the real-service case**

Rename the class to `RedisRateLimitAdapterIT`. Move only `consumesAndRejectsDistributedBuckets` into the integration class. Construct properties from the three required system properties; reject missing values with an explanatory exception. Generate a UUID rule, consume once, reject once, then use `RedisClient`/connection cleanup in `finally` to delete the exact rate-limit key. Keep reflection, password-construction, SHA-256 and concurrency checks in a new `RedisRateLimitAdapterTest` using mocks/fakes so they remain offline.

```java
static RateLimitRedisProperties stagingProperties(String host, String password) {
    RateLimitRedisProperties properties = new RateLimitRedisProperties();
    properties.setHost(requireNonBlank(host, "bytedepth.it.redis.host"));
    properties.setPassword(requireNonBlank(password, "bytedepth.it.redis.password"));
    return properties;
}
```

- [ ] **Step 4: Verify offline and staging-only discovery boundaries**

Run locally only: `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-infrastructure -Dtest=RedisRateLimitAdapterTest test`.

Expected: offline tests pass without a Redis connection; do not run `RedisRateLimitAdapterIT` locally.

- [ ] **Step 5: Commit**

```bash
git add bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/ratelimit \
  bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/ratelimit/RateLimitRedisProperties.java
git commit -m "test: isolate Redis integration coverage"
```

### Task 2: Separate Maven Surefire and Failsafe lifecycles

**Files:**
- Modify: `pom.xml:163-169, profiles`
- Modify: `scripts/verify-changed-coverage.sh:58-66`
- Test: `scripts/test-prepare-release.sh`

**Interfaces:**
- Consumes: `*Test` and `*IT` naming convention.
- Produces: default `mvn test` runs `*Test` only; `mvn verify -Pstaging-integration` runs `*IT` via Failsafe.

- [ ] **Step 1: Add a failing script-level Maven configuration assertion**

Extend `scripts/test-prepare-release.sh` with grep assertions for a Failsafe `staging-integration` profile and Surefire exclusion `**/*IT.java`. Run the shell test and observe failure.

- [ ] **Step 2: Configure the lifecycles**

In root POM plugin management add:

```xml
<plugin>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration><excludes><exclude>**/*IT.java</exclude></excludes></configuration>
</plugin>
```

Add profile `staging-integration` with `maven-failsafe-plugin` executions `integration-test` and `verify`, includes `**/*IT.java`, and the same Java agent/native-access argLine. Ensure it is inactive by default. Do not change JaCoCo’s default unit coverage lifecycle.

- [ ] **Step 3: Make the coverage script unit-only by construction**

Keep its default Maven calls profile-free and add an explicit comment explaining that Failsafe is staging-only. Add a guard that rejects `staging-integration` in `MAVEN_ARGS` if such an escape hatch exists; otherwise avoid adding one.

- [ ] **Step 4: Verify permitted local gates**

Run `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn clean install -DskipTests -Dsort.skip=true`, then `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn test -Dsort.skip=true`, `bash scripts/test-prepare-release.sh`, and `git diff --check`.

Expected: no test attempts to connect external Redis; report any WARNING as failure.

- [ ] **Step 5: Commit**

```bash
git add pom.xml scripts/verify-changed-coverage.sh scripts/test-prepare-release.sh
git commit -m "build: separate unit and integration test lifecycles"
```

### Task 3: Add the staging-only integration runner

**Files:**
- Create: `deploy/run-staging-integration-tests.sh`
- Modify: `deploy/README.md:152-196`
- Test: `scripts/test-run-staging-integration-tests.sh`

**Interfaces:**
- Consumes: a deployed `/opt/bytedepth` checkout, `/etc/bytedepth-deploy.conf` staging mode, `.env`, Compose network `bytedepth_default` and service DNS.
- Produces: a zero-warning Failsafe invocation inside `maven:3.9-eclipse-temurin-25` with `bytedepth.it.redis.host=redis` and test-only credentials.

- [ ] **Step 1: Write failing shell tests with mocked docker/env commands**

Create a temporary tree test like `test-prepare-release.sh`. Assert the runner refuses non-staging mode, uses `docker run --rm --network bytedepth_default`, mounts `/opt/bytedepth` read-only, and invokes `mvn verify -Pstaging-integration` with `bytedepth.it.redis.host=redis`. Assert it never contains `--publish`, `localhost`, or a production host.

- [ ] **Step 2: Implement minimal safe runner**

Read only `BYTEDEPTH_DEPLOY_MODE` from `/etc/bytedepth-deploy.conf`; require `staging`. Source required test credentials only through root-readable `.env` without echoing them. Copy the checked-out source into a `mktemp -d` work directory owned by the runner, then mount that copy writable in a disposable container; never let Maven write target files into the deployed checkout. Capture Maven output with `tee`, reject any case-insensitive WARNING, and remove the temporary directory with a trap.

```bash
sudo docker run --rm --network bytedepth_default \
  -v "$work_dir/source":/workspace -v "$work_dir/m2":/root/.m2 -w /workspace \
  maven:3.9-eclipse-temurin-25 \
  mvn -Pstaging-integration verify \
    -Dbytedepth.it.redis.host=redis -Dbytedepth.it.redis.port=6379
```

- [ ] **Step 3: Run shell tests and static checks**

Run: `bash scripts/test-run-staging-integration-tests.sh && bash -n deploy/run-staging-integration-tests.sh && git diff --check`.

Expected: all shell tests pass; no staging command is run locally.

- [ ] **Step 4: Commit**

```bash
git add deploy/run-staging-integration-tests.sh deploy/README.md scripts/test-run-staging-integration-tests.sh
git commit -m "test: add staging integration runner"
```

### Task 4: Enforce release evidence and align project documentation

**Files:**
- Modify: `scripts/prepare-release.sh`
- Create: `deploy/run-staging-e2e-tests.sh`
- Modify: `AGENTS.md`
- Modify: `docs/agent-guides/maven.md`
- Modify: `docs/releases/README.md`
- Test: `scripts/test-prepare-release.sh`

**Interfaces:**
- Consumes: staging integration result marker created by the runner for the candidate commit and staging E2E result marker.
- Produces: release preparation refuses to create a Tag unless both markers match current main SHA.

- [ ] **Step 1: Add failing release-script cases**

Extend `scripts/test-prepare-release.sh` so mocked release preparation fails when either `staging-integration` or `staging-e2e` evidence is absent/mismatched, and succeeds only when both files contain the mocked current SHA. Add `scripts/test-run-staging-e2e-tests.sh` to assert the wrapper fixes `E2E_BASE_URL`, uses the staging Chromium executable, records the checked-out SHA only after Playwright succeeds, and rejects WARNING output.

- [ ] **Step 2: Implement commit-bound evidence checks**

Make staging runner and a new staging-host E2E wrapper write root-owned evidence files under `/var/lib/bytedepth-staging/test-history/` containing `commit=<full SHA>`, command name, UTC timestamp and `result=passed`. The E2E wrapper fixes `E2E_BASE_URL=https://staging.bytedepth.cn` and the installed Chromium path, then records evidence only after Playwright succeeds. Before release, the operator copies both records over SSH into a local `mktemp -d` directory and passes that directory through the explicit `BYTEDEPTH_STAGING_EVIDENCE_DIR` environment variable. Make `prepare-release.sh` require both records to match `HEAD`; reject absent, malformed or mismatched SHA before Release Plugin invocation. Never accept a bare “green” string or an E2E run against another ref.

- [ ] **Step 3: Update the authoritative documentation**

Document exact offline unit commands, staging `*IT` command and staging-host E2E command. State that in-memory implementations are unit tests, while any independent process is integration. Update release flow so the two commit-bound staging records precede Tag creation.

- [ ] **Step 4: Run local permitted tests**

Run `bash scripts/test-prepare-release.sh`, `bash scripts/test-run-staging-integration-tests.sh`, `npm test`, `npm run lint`, and `git diff --check`. Do not run Failsafe integration or Playwright locally.

- [ ] **Step 5: Commit**

```bash
git add scripts/prepare-release.sh scripts/test-prepare-release.sh scripts/test-run-staging-e2e-tests.sh \
  deploy/run-staging-e2e-tests.sh AGENTS.md \
  docs/agent-guides/maven.md docs/releases/README.md
git commit -m "docs: require staging test evidence for release"
```

### Task 5: Deploy candidate and execute staging acceptance

**Files:**
- No repository changes expected.

**Interfaces:**
- Consumes: named candidate branch, `deploy/deploy-staging.sh`, staging integration runner, existing Playwright config.
- Produces: evidence records matching candidate full SHA and staging E2E report.

- [ ] **Step 1: Push candidate and deploy it to staging**

```bash
git push -u origin feat/test-boundaries
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@124.221.143.25 \
  "cd /opt/bytedepth && sudo ./deploy/deploy-staging.sh feat/test-boundaries"
```

- [ ] **Step 2: Run Failsafe integration suite only on staging**

```bash
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@124.221.143.25 \
  "cd /opt/bytedepth && sudo ./deploy/run-staging-integration-tests.sh"
```

Expected: Redis IT writes/denies/cleans its UUID key; every `*IT` passes; evidence contains deployed full SHA and zero warnings.

- [ ] **Step 3: Run E2E only on staging host**

```bash
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@124.221.143.25 \
  "cd /opt/bytedepth && E2E_BASE_URL=https://staging.bytedepth.cn PLAYWRIGHT_CHROMIUM_EXECUTABLE=/opt/bytedepth/.e2e/chrome-linux64/chrome npm run test:e2e"
```

Expected: all applicable tests pass; device-scoped skips only; E2E evidence records the deployed SHA.

- [ ] **Step 4: Review staging output before PR**

Check Compose services, evidence SHA equality, runner logs and E2E report. Stop on any WARNING, failed/omitted test, missing evidence or mismatched ref.
