# 可观测交付流水线 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为本地质量、staging bootstrap/部署/集成/E2E、release 和生产提供阶段耗时，并分离可变 runtime。

**Architecture:** `deploy/lib/timing.sh` 是唯一记录格式；staging bootstrap 写 manifest，部署与 runner 只读验证。GitHub 托管 CI 调用纯单元质量门禁，受控主机继续执行部署与验收。

**Tech Stack:** Bash、Maven 25、npm/Playwright、Docker Compose、GitHub Actions。

**Spec:** `docs/superpowers/specs/2026-09-12-observable-delivery-pipeline-design.md`

## Global Constraints

- Maven 使用 Java 25；多模块测试先 `mvn clean install -DskipTests -Dsort.skip=true`。
- 只有无外部进程的测试可在本机或 GitHub 托管 CI 运行。
- GitHub CI 为最小 `contents: read`、无 secrets、无自托管 Runner、无 `pull_request_target`。
- 所有 WARN/WARNING 均失败；staging evidence 绑定完整 SHA，生产 timing 绑定 SemVer Tag。
- 远端 timing 文件为 root `0600`，目录为 root `0700`，不得记录凭据。

---

### Task 1: 时间记录库

**Files:** Create `deploy/lib/timing.sh`; Create `scripts/test-timing.sh`.

**Interfaces:** Produces `initialize_timing_file FILE IDENTITY`, `require_timing_identity FILE IDENTITY`, `record_timed_phase FILE PHASE COMMAND...`, `record_timing_phase FILE PHASE RESULT START_MS END_MS`.

- [ ] **Step 1: Write the failing test**

```bash
initialize_timing_file "$TIMING_FILE" "$SHA"
record_timed_phase "$TIMING_FILE" source_fetch true
! record_timed_phase "$TIMING_FILE" integration_verify false
cmp -s "$EXPECTED_FILE" "$TIMING_FILE"
```

- [ ] **Step 2: Verify RED** — Run `bash scripts/test-timing.sh`; expected failure: library absent.
- [ ] **Step 3: Implement minimal behavior** — atomically create `identity=<SHA>` file; write each `phase=... result=... started_at_epoch_ms=... finished_at_epoch_ms=... duration_ms=...`; preserve wrapped command status and emit matching `TIMING` output.
- [ ] **Step 4: Verify GREEN** — Run `bash scripts/test-timing.sh`; expected pass with deterministic passed/failed 650 ms fixture records.
- [ ] **Step 5: Commit** — `git add deploy/lib/timing.sh scripts/test-timing.sh && git commit -m "feat: add delivery timing evidence library"`.

### Task 2: Stable staging runtime

**Files:** Create `deploy/lib/staging-runtime.sh`, `deploy/bootstrap-staging-runtime.sh`, `scripts/test-staging-runtime.sh`; Modify `deploy/README.md`.

**Interfaces:** Consumes Task 1. Produces `require_staging_runtime` and root-only manifest with commit, lockfile SHA, Maven inputs, Chromium path/version.

- [ ] **Step 1: Write failing tests**

```bash
run_bootstrap
grep -Fqx "commit=$SHA" "$MANIFEST"
grep -Fqx "package_lock_sha256=$LOCK_SHA" "$MANIFEST"
if run_with_mismatched_manifest; then exit 1; fi
```

- [ ] **Step 2: Verify RED** — Run `bash scripts/test-staging-runtime.sh`; expected failure: bootstrap absent.
- [ ] **Step 3: Implement** — root-only bootstrap prewarms Maven/Node and validates Chromium; manifest is atomic `0600`; validator does not download/install/delete anything.
- [ ] **Step 4: Verify GREEN** — Run `bash scripts/test-staging-runtime.sh`; expected pass, including rejection of deploy/runner attempts to bootstrap.
- [ ] **Step 5: Commit** — `git add deploy/lib/staging-runtime.sh deploy/bootstrap-staging-runtime.sh scripts/test-staging-runtime.sh deploy/README.md && git commit -m "feat: bootstrap stable staging runtime"`.

### Task 3: Timed staging deployment

**Files:** Modify `deploy/deploy-staging.sh`, `scripts/test-deploy-staging.sh`.

**Interfaces:** Consumes Tasks 1–2. Produces SHA timing phases `source_fetch`, `source_checkout`, `runtime_preflight`, `docker_build_and_rollout`, `edge_reload`, `application_https_ready`, `deployment_total`.

- [ ] **Step 1: Write failing fixture checks**

```bash
grep -Fqx "identity=$SHA" "$TIMING_FILE"
grep -Eq '^phase=deployment_total result=passed .*duration_ms=[0-9]+$' "$TIMING_FILE"
if fixture_deploy_with_runtime_mismatch; then exit 1; fi
```

- [ ] **Step 2: Verify RED** — Run `bash scripts/test-deploy-staging.sh`; expected timing assertion failure.
- [ ] **Step 3: Implement** — wrap fetch, checkout, preflight, Compose, edge reload and readiness; failed paths write both failed phase and `deployment_total`; keep locking/evidence invalidation.
- [ ] **Step 4: Verify GREEN** — Run `bash scripts/test-deploy-staging.sh`.
- [ ] **Step 5: Commit** — `git add deploy/deploy-staging.sh scripts/test-deploy-staging.sh && git commit -m "feat: record staging deployment phases"`.

### Task 4: Isolated timed staging tests

**Files:** Modify `deploy/run-staging-integration-tests.sh`, `deploy/run-staging-e2e-tests.sh`, both `scripts/test-run-staging-*.sh`, `pom.xml`, and staging `*IT` configuration.

**Interfaces:** Consumes SHA timing file and runtime manifest. Produces `integration_maven_verify`, `integration_total`, `e2e_playwright`, `e2e_total`; IT connects to existing Compose services only.

- [ ] **Step 1: Write failing tests**

```bash
! rg -q 'Testcontainers|/var/run/docker.sock|docker run' deploy/run-staging-integration-tests.sh bytedepth-start/src/test
grep -Eq '^phase=integration_maven_verify result=passed .*duration_ms=[0-9]+$' "$TIMING_FILE"
grep -Eq '^phase=e2e_playwright result=failed .*duration_ms=[0-9]+$' "$TIMING_FILE"
```

- [ ] **Step 2: Verify RED** — Run both runner script tests; expected failure due Docker/Testcontainers and missing records.
- [ ] **Step 3: Implement** — runner uses Java 25/Maven offline and Compose DNS test configuration; it creates no Docker/Testcontainers; runner rejects case-insensitive `warn|warning`; write pass evidence only after stable SHA checks.
- [ ] **Step 4: Verify GREEN** — Run both script tests and `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-start -Dtest='*Test' test`.
- [ ] **Step 5: Commit** — `git add deploy/run-staging-* scripts/test-run-staging-* pom.xml bytedepth-start/src/test && git commit -m "feat: isolate timed staging validation"`.

### Task 5: Local and hosted CI quality gate

**Files:** Create `scripts/run-quality-gates.sh`, `scripts/test-run-quality-gates.sh`, `.github/workflows/quality.yml`; Modify `deploy/README.md`.

**Interfaces:** Produces phase records for Maven refresh, unit test, coverage, npm test/lint, script tests and `quality_total`.

- [ ] **Step 1: Write failing contracts**

```bash
grep -Fqx 'permissions:' .github/workflows/quality.yml
grep -Fqx '  contents: read' .github/workflows/quality.yml
! rg -q 'self-hosted|pull_request_target|secrets\.|ssh |docker ' .github/workflows/quality.yml
```

- [ ] **Step 2: Verify RED** — Run `bash scripts/test-run-quality-gates.sh`; expected absent workflow/wrapper.
- [ ] **Step 3: Implement** — immutable-SHA-pinned actions run wrapper on `pull_request`/push; wrapper uses no staging profile/external service and emits artifact-safe timing output.
- [ ] **Step 4: Verify GREEN** — Run `bash scripts/test-run-quality-gates.sh && bash scripts/run-quality-gates.sh --ci`.
- [ ] **Step 5: Commit** — `git add .github scripts/run-quality-gates.sh scripts/test-run-quality-gates.sh deploy/README.md && git commit -m "ci: run isolated quality gates on GitHub Actions"`.

### Task 6: Release and production timings

**Files:** Modify `scripts/prepare-release.sh`, `scripts/test-prepare-release.sh`, `deploy/deploy-release.sh`, `deploy/README.md`, `docs/releases/README.md`; Create `scripts/test-deploy-release.sh`.

**Interfaces:** Produces Tag identity timing records `release_total` and `production_deployment_total` under protected release/deploy state directories.

- [ ] **Step 1: Write failing tests**

```bash
grep -Fqx 'identity=v1.2.3' "$RELEASE_TIMING_FILE"
grep -Eq '^phase=release_total result=passed .*duration_ms=[0-9]+$' "$RELEASE_TIMING_FILE"
grep -Eq '^phase=production_deployment_total result=failed .*duration_ms=[0-9]+$' "$PROD_TIMING_FILE"
```

- [ ] **Step 2: Verify RED** — Run `bash scripts/test-prepare-release.sh && bash scripts/test-deploy-release.sh`.
- [ ] **Step 3: Implement** — initialize after Tag validation, wrap evidence/coverage/release/push and fetch/checkout/rollout/ready operations; retain existing refusal semantics.
- [ ] **Step 4: Verify all gates** — Run all six shell contracts, Java cache refresh, `mvn test`, changed coverage, `npm test`, and `npm run lint`; require zero warnings.
- [ ] **Step 5: Commit and stage** — Push `feat/staging-timing`; bootstrap only if manifest inputs changed, then deploy → integration → E2E → inspect timing/evidence → owner accepts before PR.

## Plan self-review

- Tasks 1–3 implement the format, runtime separation and deploy timing; Task 4 removes transient test infrastructure; Task 5 supplies hosted CI; Task 6 completes release/production coverage.
- Every task names its files, interfaces, failure assertion, RED/GREEN command and commit.
