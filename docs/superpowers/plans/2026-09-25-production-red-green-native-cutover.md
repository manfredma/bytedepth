# Production Red-Green Native Cutover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 175 多服务宿主机上实现可验证、可回退的 native 全栈红绿部署，并通过 129 staging 的完整验收后发布新的生产 Tag。

**Architecture:** 现有 Docker bytedepth 栈作为蓝环境继续提供流量；native 绿环境使用独立数据目录、端口和 systemd unit，先完成数据复制、服务启动和只读校验，再在短暂停机窗口内完成最终同步并只 reload 共享 Nginx 的 bytedepth upstream。生产远程入口继续只接受 annotated SemVer Tag，本机不执行生产内部脚本或 sudo。

**Tech Stack:** Bash 5-compatible deployment scripts, systemd, Nginx, MySQL 8, Redis 7, Meilisearch 1.7, Java 25 Spring Boot, Maven Wrapper 3.9.11, shell contract tests.

**Spec:** `docs/superpowers/specs/2026-09-25-production-red-green-native-cutover-design.md`

## Global Constraints

- 175 是多服务宿主机；只操作 bytedepth 的绿服务、数据目录和 Nginx upstream，不停止或重建其他项目。
- native 准备、初始复制、绿启动和预验证阶段不得停止、重建或修改 Docker 蓝环境；切流窗口失败必须恢复蓝应用、蓝 upstream 并验证 Docker 入口。
- 绿环境固定使用 `/data/bytedepth-native-production`、13306、16379、17700、18080、18081 及 `bytedepth-production-green-` 服务前缀。
- 所有远程创建的项目目录、文件、制品、配置和状态归 `ubuntu:ubuntu`；服务账号只获得必要的数据目录组权限。
- 数据复制必须限定到明确目标，禁止无界全库 dump、密码进入命令行、递归删除 `/data` 或覆盖其他项目资源。
- 生产本机只能执行 `BYTEDEPTH_PRODUCTION_SSH_KEY="$HOME/.ssh/ubuntu_2.pem" BYTEDEPTH_PRODUCTION_SSH_KNOWN_HOSTS="$HOME/.ssh/known_hosts" ./deploy/deploy-production-remote.sh vX.Y.Z`。
- 任何 WARNING、健康检查失败、校验失败、清理失败或共享 Nginx `nginx -t` 失败都必须 fail-closed。
- staging 仍是唯一集成/E2E/验收环境；代码或部署脚本改变后，旧 staging evidence 作废。

---

### Task 1: 固化生产绿环境资源契约

**Files:**
- Create: `deploy/production-green.conf.example`
- Create: `deploy/systemd/bytedepth-production-green-mysql.service.in`
- Create: `deploy/systemd/bytedepth-production-green-redis.service.in`
- Create: `deploy/systemd/bytedepth-production-green-meilisearch.service.in`
- Create: `deploy/systemd/bytedepth-production-green-app.service.in`
- Create: `deploy/systemd/bytedepth-production-green-edge.service.in`
- Create: `deploy/lib/production-green-target.sh`
- Create: `deploy/install-production-green-stack.sh`
- Modify: `deploy/install-host-service.sh`
- Test: `scripts/test-production-green-runtime.sh`

**Interfaces:**
- `load_production_green_target()` loads explicit production-green root and ports and rejects staging/default values.
- `BYTEDEPTH_PRODUCTION_GREEN_ROOT`, `BYTEDEPTH_PRODUCTION_GREEN_MYSQL_PORT`, `BYTEDEPTH_PRODUCTION_GREEN_REDIS_PORT`, `BYTEDEPTH_PRODUCTION_GREEN_MEILI_PORT`, `BYTEDEPTH_PRODUCTION_GREEN_APP_PORT`, `BYTEDEPTH_PRODUCTION_GREEN_EDGE_PORT` are the only resource mapping inputs.
- The installer renders units with `ubuntu`-owned files and never enables or starts them automatically before data/config preparation.

- [x] **Step 1: Write contract tests** for required root, six ports, service prefix, no default-port collision, and ownership/mode assertions.
- [x] **Step 2: Run `bash scripts/test-production-green-runtime.sh`** and confirm it fails because the target helper, examples, and units do not exist.
- [x] **Step 3: Implement the target helper, example, and five units** with explicit paths, `EnvironmentFile`, `MemoryMax`, `ProtectSystem`, and `RequiresMountsFor` boundaries.
- [x] **Step 4: Extend the installer** to render only the green units and create green directories with `ubuntu` ownership; keep existing staging and shared production units unchanged.
- [x] **Step 5: Run the contract test and shellcheck/static checks**, confirming all assertions pass with no WARNING.
- [x] **Step 6: Commit** `feat: add production green runtime contract`.

### Task 2: Implement bounded data preparation and final synchronization

**Files:**
- Create: `deploy/migrate-production-docker-to-native.sh`
- Create: `deploy/lib/production-green-migration.sh`
- Create: `scripts/test-production-green-migration.sh`
- Modify: `deploy/README.md`
- Modify: `docs/engineering/gotchas.md`
- Modify: `docs/architecture/ubiquitous-language.md`

**Interfaces:**
- `sudo ./deploy/migrate-production-docker-to-native.sh prepare` creates only the green directories/config and performs bounded initial copy while blue remains serving.
- `sudo ./deploy/migrate-production-docker-to-native.sh final-sync` requires the production deployment lock and a stopped blue app, then performs final MySQL/Redis/Meilisearch/image synchronization into green.
- `sudo ./deploy/migrate-production-docker-to-native.sh verify` validates data manifests, service readiness, ownership and no cross-project path use.
- `sudo ./deploy/migrate-production-docker-to-native.sh rollback` preserves uncertain green state; the deployment layer restores the blue route and verifies Docker access.

- [x] **Step 1: Write tests** for command argument validation, Docker container identity, exact source/target paths, lock requirements, blue-app stop requirement for final sync, and uncertain-state preservation.
- [x] **Step 2: Run `bash scripts/test-production-green-migration.sh`** and confirm failure.
- [x] **Step 3: Implement initial MySQL copy** using an explicit database target and bounded streaming/temporary file behavior; redact credentials and reject arbitrary database names.
- [x] **Step 4: Implement Redis snapshot copy, Meilisearch snapshot import/wait, and image copy** with per-resource manifests and fail-closed cleanup.
- [x] **Step 5: Implement final-sync state markers** (`prepared`, `syncing`, `verified`, `uncertain`) owned by `ubuntu`; preparation and green preflight must not touch Docker, and destructive cleanup is forbidden when a marker is uncertain.
- [x] **Step 6: Add deployment documentation and unified-language entries** for green environment, cutover, final sync, and uncertain state.
- [x] **Step 7: Run migration contract tests and shellcheck**, then commit `feat: add production green data synchronization`.

### Task 3: Add native green deployment and Nginx cutover state machine

**Files:**
- Modify: `deploy/deploy-production.sh`
- Modify: `deploy/deploy-production-remote.sh`
- Create: `scripts/test-production-red-green-deployment.sh`
- Modify: `scripts/verify-production-release.sh`

**Interfaces:**
- `deploy-production.sh --artifact JAR --manifest MANIFEST TAG` performs preflight, installs the immutable JAR into the green release path, prepares/starts green services, verifies local green `/version`, executes the final sync lock protocol, and cuts only bytedepth traffic.
- The host script saves the Docker upstream, runs `nginx -t` before reload, records the green route, and has an explicit `restore_blue_access` path.
- The remote wrapper continues to upload only `app.jar` and `artifact.manifest`, polls the remote log under the existing warning policy, and invokes read-only production verification after the release history entry is written.

- [x] **Step 1: Write failing deployment contract tests** for Tag-only input, green service ordering, no default-port reuse, blue-route preservation, `nginx -t` before reload, rollback on health failure, and no other project unit/container operations.
- [x] **Step 2: Run `bash scripts/test-production-red-green-deployment.sh`** and confirm failure.
- [x] **Step 3: Implement production preflight** that rejects the wrong deploy mode, missing Docker blue services, existing green uncertainty, port collisions, missing green config, or non-ubuntu project paths.
- [x] **Step 4: Implement green artifact installation and startup** without changing blue traffic; verify artifact SHA, Tag commit, local green `/version`, all middleware readiness, and application logs.
- [x] **Step 5: Implement the short final-sync window** only after green preflight passes: save the blue route, stop only the blue bytedepth app, synchronize data, start green app/edge, and on any failure restore blue app plus the original route before returning failure.
- [x] **Step 6: Implement Nginx route cutover/rollback** through the host-mounted bytedepth route and shared Nginx reload; preserve every other `server_name` and upstream, and verify the Docker route before reporting native failure.
- [x] **Step 7: Update remote polling and production verification** to assert green services, active route, release history, version SHA, stable pages, SNI and no WARNING/ERROR.
- [x] **Step 8: Run deployment contract tests and shellcheck**, then commit `feat: deploy production through native green cutover`.

### Task 4: Add end-to-end migration rehearsal and operational guards

**Files:**
- Create: `scripts/test-production-red-green-rehearsal.sh`
- Modify: `scripts/check-staging-checklist.sh`
- Modify: `scripts/test-deploy-production.sh`
- Modify: `scripts/test-deploy-production-remote.sh`
- Modify: `docs/releases/README.md`
- Modify: `AGENTS.md`
- Modify: `docs/engineering/technical-debt.md`

**Interfaces:**
- The rehearsal uses fake Docker/systemd/Nginx commands and a temporary root; it must prove the state machine can prepare, verify, cut, fail, and restore without touching `/data`, `/etc`, or a real host.
- Release readiness must reject production deployment unless the green cutover contract and warning policy checks pass.

- [x] **Step 1: Write fake-host rehearsal cases** for success, green health failure, final-sync failure, `nginx -t` failure, cutover failure, and uncertain cleanup; every failure case must assert Docker access remains available or is restored before the script exits.
- [x] **Step 2: Run the rehearsal and existing production script tests** to observe failures.
- [x] **Step 3: Implement fake-host adapters and guard checks** without weakening real-host validation.
- [x] **Step 4: Record the staging migration/OOM/dubious-ownership pitfalls and the production red-green rule** in project docs, including that 175 is not 129.
- [x] **Step 5: Run all deployment/static contract tests and `bash scripts/check-staging-checklist.sh`**, blocking on every WARNING.
- [ ] **Step 6: Commit** `test: rehearse production red-green cutover`.

### Task 5: Re-deploy and re-validate the candidate on staging

**Files:**
- No source files; use the committed candidate and staging evidence paths.

- [ ] **Step 1: Push the feature branch and deploy the exact candidate SHA to 129** with `deploy/deploy-staging.sh <ref>`; verify the native staging services and shared Nginx boundary before tests.
- [ ] **Step 2: Run the complete staging integration suite** and require `result=passed`, `runtime_mode=host-native`, cleanup passed, and exact candidate SHA.
- [ ] **Step 3: Run the complete staging E2E suite** with the existing Keychain-injected administrator credential; do not create a temporary account.
- [ ] **Step 4: Perform read-only staging verification** using the canonical staging URL and `/version`; confirm all service owners and the test slot are restored.
- [ ] **Step 5: Obtain owner acceptance on staging** and preserve exactly two copied evidence files for release preparation.

### Task 6: Controlled release and 175 production cutover

**Files:**
- No new source files after staging acceptance; only controlled release metadata and generated Tag commits are allowed.

- [ ] **Step 1: Fast-forward `main` to the accepted candidate SHA** and prove the SHA is unchanged from both staging evidence files.
- [ ] **Step 2: Run `prepare-release.sh 2.25.3 2.25.4-SNAPSHOT`** with a fresh evidence directory; require a new annotated `v2.25.3` Tag and a clean working tree.
- [ ] **Step 3: Push `main` and the Tag** using the release script output; verify Tag type, commit SHA and POM version.
- [ ] **Step 4: Deploy only through the required 175 remote entry** with explicit SSH key and known_hosts; monitor for warnings and do not invoke the internal script locally.
- [ ] **Step 5: Verify the 175 green cutover**: native units active, blue baseline retained, bytedepth upstream points to green, other projects unchanged, SNI/version/read-only paths pass, and production logs contain no WARNING/ERROR.
- [ ] **Step 6: Record production acceptance and rollback baseline** in the controlled release record; do not delete Docker/data until the explicit post-acceptance cleanup phase is separately authorized.

## Self-review

- The ADR/spec requirements map to Tasks 1–4; staging and release requirements map to Tasks 5–6.
- No task depends on a hidden checkout SHA or credential. The only remote production mutation is through the required wrapper.
- The blue/green data boundary is explicit: active data directories are never shared by Docker and native middleware.
- Failure paths preserve uncertain state and restore blue traffic before any cleanup.
- The plan contains no placeholder or unspecified “handle edge cases” steps.
