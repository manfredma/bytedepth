# 宿主机原生运行时部署实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 staging 和生产从 Docker Compose 运行时迁移到宿主机 systemd 服务，并建立自动化的外部 JAR 发布、数据迁移、测试资源隔离、验收和回滚流程。

**Architecture:** MySQL、Redis、Meilisearch、Java 应用和 Nginx 由宿主机固定版本和 systemd 管理，应用只运行外部构建并经过 SHA 校验的 JAR。staging 的集成测试和 E2E 使用串行 test slot：IT/E2E 各自拥有独立 MySQL 库/账号、Redis logical DB+运行级 namespace、Meilisearch index；隔离配置分别由 `staging-it`/`staging-e2e` Spring Profile 选择，测试结束后自动清理并恢复正常 staging 应用。

**Tech Stack:** Java 25、Maven Wrapper 3.9.11、Spring Boot、多模块 Maven、MySQL 8、Redis 7、Meilisearch 1.7、Nginx、systemd、Bash、Playwright。

**Spec:** `docs/superpowers/specs/2026-09-24-host-native-runtime-deployment-design.md`

## Global Constraints

- 不允许在 `main` 分支直接开发；所有实现、测试和文档改动在本 worktree 的 `feat/host-native-runtime` 分支完成。
- 本机 Maven 只使用 `./mvnw`，版本固定 3.9.11，Java 固定 25；不得使用裸 `mvn` 或浮动 Maven 镜像。
- 本机前端测试、lint 或 Playwright 前先执行 `npm ci --ignore-scripts --no-audit --no-fund`。
- 目标主机不执行 Maven 构建，不执行 Docker/Compose 运行时构建；运行时只接收已校验的不可变 JAR。
- staging 是唯一集成、E2E 和验收环境；staging URL 必须是 `https://staging-bytedepth.bytedepth.cn/`。
- 集成测试和 E2E 不得直接使用 staging MySQL、Redis 或 Meilisearch 资源；必须分别激活 `staging-it`/`staging-e2e` Spring Profile；禁止 `FLUSHALL`、通配符删除和默认/临时管理员账号。
- 每项业务代码改动必须补单元测试；本次业务逻辑分支覆盖率达到 100%；所有构建、测试、静态分析和部署输出中的 WARNING 必须处理。
- 首次 staging 部署前必须有非空且分类明确的 `CHANGELOG.md` `## Unreleased` 条目。
- 发布前必须有当前 `main` 完整 SHA 绑定的 staging integration 和 E2E 两份 `result=passed` evidence。
- 发布生产只能从本机执行 `BYTEDEPTH_PRODUCTION_SSH_KEY="$HOME/.ssh/ubuntu_2.pem" BYTEDEPTH_PRODUCTION_SSH_KNOWN_HOSTS="$HOME/.ssh/known_hosts" ./deploy/deploy-production-remote.sh vX.Y.Z`。

---

## 文件结构和责任边界

| 责任 | 文件/目录 | 说明 |
|------|----------|------|
| 应用运行时配置 | `bytedepth-start/src/main/resources/application.yml`、`application-staging-it.yml`、`application-staging-e2e.yml` | 生产默认值保持兼容；隔离资源统一通过 Spring Profile 选择，实际 run 值由 root-only 外部环境文件注入 |
| Redis namespace | `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/redis/RedisKeyNamespace.java` | 统一生成 Session、PV、阅读进度、限流和 Ops 扫描前缀 |
| 搜索 index | `bytedepth-infrastructure/.../search/MeiliSearchPostIndexer.java` | 将硬编码 `posts` 改为配置值 |
| 测试资源编排 | `deploy/lib/staging-test-slot.sh`、`deploy/provision-staging-test-slot.sh`、`deploy/teardown-staging-test-slot.sh` | 生成 manifest、创建/清理 IT/E2E 资源和恢复应用 |
| systemd 运行时 | `deploy/systemd/*.service`、`deploy/systemd/*.timer` | 应用、中间件、测试槽位、挂载和定时任务 unit |
| staging 部署 | `deploy/deploy-staging.sh`、`deploy/bootstrap-ops-deploy.sh` | 外部 JAR 安装、systemd 切换和健康检查 |
| 生产部署 | `deploy/deploy-production-remote.sh`、`deploy/deploy-production.sh` | 外部 JAR 上传、Tag/SHA 校验和 systemd 发布 |
| 同步/证书/NFS | `deploy/sync-prod-to-staging.sh`、`deploy/provision-*.sh`、`deploy/setup-shared-images-nfs.sh` | 从 `docker exec` 改为宿主机 CLI 和 systemd |
| 自动检查 | `scripts/test-host-native-*.sh`、现有 `scripts/test-*` | 脚本契约、资源隔离、证据和旧入口 fail-closed |
| 权威文档 | `deploy/README.md`、`docs/releases/README.md`、`docs/engineering/unified-release-pipeline.md` | 分别维护部署、发布、统一流水线，不另建平行手册 |
| Docker 资产清理 | `Dockerfile`、`.dockerignore`、`deploy/docker-compose*.yml`、`deploy/ctl.sh`、Docker 专用脚本和测试 | 本次删除，不保留 Compose 回退路径；同名功能由宿主机脚本或 systemd 替换 |

## Task 1: 应用配置和 Redis/Meilisearch 隔离能力

**Files:**
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/redis/RedisKeyNamespace.java`
- Create: `bytedepth-start/src/main/resources/application-staging-it.yml`, `application-staging-e2e.yml`
- Modify: `bytedepth-start/src/main/resources/application.yml`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/RedisStatsService.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/RedisReadingProgressTokenAdapter.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/ratelimit/RateLimitRedisProperties.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/ratelimit/RedisRateLimitAdapter.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/ops/RedisOpsAdapter.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/search/MeiliSearchPostIndexer.java`
- Test: corresponding infrastructure tests and `bytedepth-start/src/test/java/manfred/bytedepth/ThemeAssetsTest.java` configuration assertions

**Interfaces:**
- `RedisKeyNamespace` consumes `bytedepth.redis.key-namespace` and exposes `key(String family, String suffix)` plus `prefix(String family)`; an empty namespace preserves existing production key names.
- `staging-it` and `staging-e2e` are the only supported isolated-resource profiles; their required values are supplied through a root-only external environment file generated from the manifest.
- `RateLimitRedisProperties` produces `database` and `keyNamespace` for the separate Bucket4j Redis client.
- `MeiliSearchPostIndexer` consumes `bytedepth.search.index` with default `posts`.

- [ ] **Step 1: Write failing tests for namespacing and index selection.** Assert empty namespace produces existing production keys, `bytedepth:it:r1:` prefixes every Redis family, rate-limit properties carry the test database/namespace, and the search REST URI uses `posts_it_r1` rather than `posts`.
- [ ] **Step 2: Run the focused tests and verify failure.** Run `./mvnw -pl bytedepth-infrastructure,bytedepth-start -am -Dtest=RedisStatsServiceTest,RedisReadingProgressTokenAdapterTest,RedisRateLimitAdapterTest,MeiliSearchPostIndexerTest,ThemeAssetsTest test -Dsort.skip=true`; expected: the new assertions fail against hardcoded prefixes/index.
- [ ] **Step 3: Implement configuration injection.** Add `staging-it` and `staging-e2e` profile files with required JDBC URL/user/password, Redis database/password/namespace, rate-limit database/namespace, Meili index/API key and upload directory mappings. Preserve production defaults exactly where existing data compatibility requires it; do not make runners pass a second ad-hoc property contract.
- [ ] **Step 4: Replace hardcoded Redis families.** Inject one namespace object into stats, reading-progress, rate-limit and Ops adapters; make Ops scans use the same generated prefixes. Ensure `spring.session.redis.namespace`, ordinary Redis database, and rate-limit database can be set independently but are set consistently by the test profile.
- [ ] **Step 5: Make search index configurable.** Replace the static `INDEX = "posts"` with constructor-injected `@Value("${bytedepth.search.index:posts}")`, retain the package-visible RestClient test constructor, and use the configured value for index, delete and search paths.
- [ ] **Step 6: Run focused tests and the module unit suite.** Run the focused command again, then `./mvnw -pl bytedepth-infrastructure,bytedepth-start -am test -Dsort.skip=true`; expected: PASS with zero unregistered WARNING.
- [ ] **Step 7: Commit.** `git add bytedepth-start bytedepth-infrastructure && git commit -m "feat: add isolated test resource configuration"`.

## Task 2: Test resource manifest and lifecycle library

**Files:**
- Create: `deploy/lib/staging-test-slot.sh`
- Create: `deploy/provision-staging-test-slot.sh`
- Create: `deploy/teardown-staging-test-slot.sh`
- Create: `scripts/test-staging-test-slot.sh`
- Create: `scripts/test-test-resource-isolation.sh`
- Modify: `.gitignore` only if required for local fixture outputs

**Interfaces:**
- `provision-staging-test-slot.sh --run-id <RUN_ID> --manifest <path>` creates IT/E2E resources and writes a root-owned 0600 manifest with `run_id`, MySQL DB/user names, Redis DB/namespace, Meili index names, test app port and source SHA; it never writes passwords to stdout.
- `teardown-staging-test-slot.sh --manifest <path>` stops consumers, deletes only manifest-owned databases/users/indexes/keys/directories, and returns non-zero if any cleanup or verification fails.
- `staging-test-slot.sh` provides `validate_run_id`, `require_manifest`, `assert_not_staging_resource`, `mysql_exec`, `redis_scan_delete`, `meili_wait_task` and `write_resource_digest` helpers.

- [ ] **Step 1: Write shell contract tests.** Use temporary fake `mysql`, `redis-cli`, `curl` and `systemctl` executables to assert unsafe RUN_IDs, staging DB/index names, missing manifests, `FLUSHALL`, wildcard deletion, and missing Redis DB capacity are rejected before any external command.
- [ ] **Step 2: Run the contract tests and verify failure.** Run `bash scripts/test-staging-test-slot.sh` and `bash scripts/test-test-resource-isolation.sh`; expected: scripts do not exist or fail the required assertions.
- [ ] **Step 3: Implement manifest validation and lock expectations.** Require a generated RUN_ID matching `^[0-9]{8}_[0-9]{6}_[a-z0-9]{8}$`, a candidate SHA, staging mode, reserved Redis DBs, and a writable root-only state directory. Reject `bytedepth`, `posts`, staging Redis DB, and any resource not derived from the manifest.
- [ ] **Step 4: Implement MySQL provisioning.** Create two databases named `bytedepth_it_<RUN_ID>` and `bytedepth_e2e_<RUN_ID>`, two per-run users with random root-only passwords, import the validated sanitized fixture, and verify each connection reports the manifest database. No fallback to staging credentials or database is allowed.
- [ ] **Step 5: Implement Redis provisioning and cleanup.** Verify reserved DB count, use one DB for IT and one for E2E, write only namespaced keys, delete with `SCAN MATCH <namespace>*`, count before/after, and reject `FLUSHALL`, `FLUSHDB`, `KEYS *`, and prefixes without RUN_ID.
- [ ] **Step 6: Implement Meilisearch provisioning and cleanup.** Create per-run indexes, apply the production index settings, wait for task completion, verify the configured index name, and delete only the manifest indexes after tests. Do not touch `posts`.
- [ ] **Step 7: Implement fixture validation.** Require a root-owned fixture checksum and verify it contains the required article/category/admin records without default passwords or production-only environment values. A missing or invalid fixture stops the run.
- [ ] **Step 8: Run both shell contract suites and shellcheck-equivalent project checks.** Expected: PASS; all negative tests must prove no fake destructive command was invoked.
- [ ] **Step 9: Commit.** `git add deploy/lib/staging-test-slot.sh deploy/provision-staging-test-slot.sh deploy/teardown-staging-test-slot.sh scripts/test-staging-test-slot.sh scripts/test-test-resource-isolation.sh && git commit -m "test: isolate staging integration resources"`.

## Task 3: Replace Testcontainers integration tests with the IT resource profile

**Files:**
- Modify: `pom.xml` staging-integration profile
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/integration/PostRepositoryIT.java`
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/integration/AnnotationContentUpdateIT.java`
- Modify: `bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/ratelimit/RedisRateLimitAdapterIT.java`
- Modify: `deploy/run-staging-integration-tests.sh`
- Modify: `scripts/test-run-staging-integration-tests.sh`

**Interfaces:**
- Failsafe activates `staging-it` and consumes the profile's root-only external environment file; the runner passes only the profile selector and manifest path, not a duplicate list of Spring resource properties.
- `run-staging-integration-tests.sh` obtains the existing deployment-test lock, provisions/loads the IT resources, runs Maven against the deployed candidate SHA, and invalidates evidence on every failure.

- [ ] **Step 1: Add failing contract assertions that no integration test declares `MySQLContainer` and the runner does not require a Docker socket.** Extend `scripts/test-run-staging-integration-tests.sh` with negative checks for Docker/Testcontainers and positive checks for manifest variables.
- [ ] **Step 2: Run the contract script and confirm it fails against the current Docker runner.** Run `bash scripts/test-run-staging-integration-tests.sh`; expected: failure identifying MySQLContainer/Docker socket requirements.
- [ ] **Step 3: Remove MySQLContainer lifecycle from the IT classes.** Keep `@SpringBootTest` and repository assertions, but make all connection properties come from the staging-integration environment. Do not add in-memory database fallbacks.
- [ ] **Step 4: Add explicit Maven system properties.** Wire Failsafe to the environment variables and configure Spring test properties without logging passwords. Keep `-Pstaging-integration` exclusive to staging and preserve target/classes loading.
- [ ] **Step 5: Rework the runner around the manifest.** Provision the IT resource set, export only root-owned 0600 credentials into the Maven process, run `./mvnw -o -Pstaging-integration verify`, capture the resource digest and warning policy, and call teardown in both success and failure paths.
- [ ] **Step 6: Write evidence only after cleanup and staging-restore checks.** Add `runtime_mode=host-native`, `run_id`, `test_resource_manifest_sha`, `cleanup=result=passed`, candidate SHA, command, timestamp and `result=passed`; redact credentials and database passwords.
- [ ] **Step 7: Run the shell contract test and local Maven unit test path.** Run `bash scripts/test-run-staging-integration-tests.sh` and `./mvnw -Dsort.skip=true test`; expected: PASS locally without Docker or external processes for the unit path.
- [ ] **Step 8: Commit.** `git add pom.xml bytedepth-start/src/test bytedepth-infrastructure/src/test deploy/run-staging-integration-tests.sh scripts/test-run-staging-integration-tests.sh && git commit -m "test: run staging integration tests on isolated resources"`.

## Task 4: Add the native test slot and E2E orchestration

**Files:**
- Create: `deploy/systemd/bytedepth-test-slot.service`
- Modify: `deploy/run-staging-e2e-tests.sh`
- Modify: `scripts/test-run-staging-e2e-tests.sh`
- Modify: `deploy/lib/staging-test-slot.sh`
- Modify: `tests/e2e/*.spec.js` only where fixture/run-id cleanup is required

**Interfaces:**
- `bytedepth-test-slot.service` activates `staging-e2e` and consumes a root-owned EnvironmentFile generated from the E2E manifest; it listens on the existing application port only after `bytedepth-app.service` is stopped.
- `run-staging-e2e-tests.sh` consumes the E2E manifest, uses the unchanged staging HTTPS URL, and exports the existing admin credentials from the controlled staging secret path without creating an account.

- [ ] **Step 1: Add failing service and runner contract checks.** Assert the test unit requires `bytedepth-app.service` to be stopped, uses the manifest JAR/SHA, references test DB/Redis/index variables, and restores `bytedepth-app.service` on failure.
- [ ] **Step 2: Run `bash scripts/test-run-staging-e2e-tests.sh` and verify the current runner fails the new isolation checks.**
- [ ] **Step 3: Implement the test-slot systemd unit.** Use Java 25, the candidate release JAR, root-only EnvironmentFile, private temporary directory, bounded resources, `/version` health check, and no Docker dependency. The unit must fail if staging app remains active or the test fixture is incomplete.
- [ ] **Step 4: Implement E2E resource handoff.** Under the same lock, provision E2E resources, stop staging app, start test slot, verify URL/version/index/database identities, run Playwright, then always stop test slot, teardown E2E resources, restart staging app, and verify normal staging resources.
- [ ] **Step 5: Preserve current E2E coverage and cleanup behavior.** Keep annotation, code-block, analytics, series-navigation and mobile tests; add RUN_ID to generated test titles/slugs and ensure article/annotation cleanup remains inside E2E while final database drop remains the hard boundary.
- [ ] **Step 6: Run shell contract tests and local frontend setup in the required order.** Execute `npm ci --ignore-scripts --no-audit --no-fund`, then `bash scripts/test-run-staging-e2e-tests.sh`; expected: PASS with fixture mode and no real credentials.
- [ ] **Step 7: Commit.** `git add deploy/systemd/bytedepth-test-slot.service deploy/run-staging-e2e-tests.sh scripts/test-run-staging-e2e-tests.sh deploy/lib/staging-test-slot.sh tests/e2e && git commit -m "test: run staging E2E in isolated native slot"`.

## Task 5: Implement host-native service units, artifact deployment and Docker asset removal

**Files:**
- Create/modify: `deploy/systemd/bytedepth-app.service`, `mysql.service`, `redis.service`, `meilisearch.service`, `nginx.service` templates
- Modify: `deploy/bootstrap-ops-deploy.sh`
- Modify: `deploy/install-host-service.sh`
- Modify: `deploy/deploy-staging.sh`
- Modify: `deploy/deploy-production.sh`
- Modify: `deploy/deploy-production-remote.sh`
- Create: `deploy/lib/artifact.sh`
- Create: `scripts/test-host-native-runtime.sh`
- Create: `scripts/test-host-native-deployment.sh`
- Delete: `Dockerfile`, `.dockerignore`, `deploy/docker-compose.app-external.yml`, `deploy/docker-compose.data-access.yml`, `deploy/docker-compose.single-host.yml`, `deploy/docker-compose.staging.yml`, `deploy/ctl.sh`, `deploy/prewarm-production-maven-cache.sh`
- Delete: Docker-only contract tests after replacing their assertions with host-native tests (`scripts/test-maven-runtime.sh` Docker sections, old Docker/Testcontainers sections in `scripts/test-run-staging-integration-tests.sh`)
- Modify: existing `scripts/test-deploy-*.sh` and `scripts/test-verify-production-release.sh`

**Interfaces:**
- `deploy/lib/artifact.sh` exposes `validate_release_tag`, `validate_artifact_manifest`, `install_release_artifact`, `switch_current_release`, and `verify_running_release`.
- `deploy/deploy-staging.sh <candidate-ref>` builds/uploads the JAR externally, installs `/opt/bytedepth/releases/<ref>/app.jar`, and records the full candidate SHA.
- `deploy/deploy-production-remote.sh vX.Y.Z` remains the only local production entry; the remote script receives an artifact and SHA, never invokes Maven or Docker.

- [ ] **Step 1: Add failing runtime contract tests.** Assert units use fixed users/paths/ports, app depends on MySQL/Redis/Meili, Nginx depends on app health, data services bind only to controlled addresses, and the tracked project contains no Docker deployment assets or Docker-only release checks.
- [ ] **Step 2: Run `bash scripts/test-host-native-runtime.sh bash scripts/test-host-native-deployment.sh` using the project’s actual invocation convention and verify failures against Compose scripts.**
- [ ] **Step 3: Add versioned systemd units and directory ownership.** Define `/opt/bytedepth/releases`, `/opt/bytedepth/current`, `/etc/bytedepth`, root-only secrets, service users, `RequiresMountsFor=/data/images`, restart/resource limits, and Java 25 executable path.
- [ ] **Step 4: Implement external artifact creation and SHA manifest.** Use `./mvnw clean install -DskipTests -Dsort.skip=true` followed by the required verification lifecycle on the build machine, extract the release JAR, record tag/commit/build time/SHA, and fail on WARNING.
- [ ] **Step 5: Implement staging artifact rollout.** Fetch candidate ref, build outside the target runtime, upload to a temporary root-owned path, verify SHA and manifest, install the release directory, run database backup preflight, switch `current`, restart app, wait for probes, reload Nginx, and write deploy history.
- [ ] **Step 6: Implement production remote rollout.** Keep explicit known_hosts and SSH key checks, upload the immutable artifact, verify annotated SemVer Tag and SHA, run the same transaction on 175, and invoke host-native production verification.
- [ ] **Step 7: Remove the Docker assets and update all references.** Delete the listed files, remove Docker-specific workflow assertions, replace Nginx Docker DNS with localhost/systemd upstreams, and make `rg -i 'docker|compose' deploy scripts Dockerfile .dockerignore` return only intentional migration/context references in documentation.
- [ ] **Step 8: Run deployment contract tests and static warnings checks.** Run all changed `scripts/test-*.sh`, `bash scripts/check-staging-checklist.sh`, and `git diff --check`; expected: PASS without contacting staging or production.
- [ ] **Step 9: Commit.** `git add -A && git commit -m "feat: deploy native runtime artifacts with systemd"`.

## Task 6: Convert data sync, certificates, images and timers

**Files:**
- Modify: `deploy/sync-prod-to-staging.sh`
- Modify: `deploy/provision-staging-certificate.sh`
- Modify: `deploy/provision-production-edge-staging-certificate.sh`
- Modify: `deploy/sync-staging-certificate-to-production.sh`
- Modify: `deploy/nginx/reload-nginx-deploy-hook.sh`
- Modify: `deploy/setup-shared-images-nfs.sh`
- Create/modify: backup and systemd timer units referenced by `deploy/README.md`
- Create: `scripts/test-host-native-data-migration.sh`

**Interfaces:**
- Data sync uses host `mysqldump/mysql`, `redis-cli`, Meili HTTP/snapshot and `rsync`; it holds the migration lock and validates destination environment before import.
- Certificate scripts use `nginx -t` and `systemctl reload nginx`, and retain SAN, expiry, certificate/private-key match, known_hosts and SSH permission checks.

- [ ] **Step 1: Add failing static checks for `docker exec`, `docker cp`, `docker run`, and Docker restart in normal sync/certificate/NFS paths.**
- [ ] **Step 2: Run `bash scripts/test-host-native-data-migration.sh` and verify it rejects the existing scripts.**
- [ ] **Step 3: Replace MySQL/Redis/Meili sync commands with host-native commands.** Use explicit source/destination names, temporary files with 0600 permissions, checksums, timeout bounds, service state checks, and no production secret output.
- [ ] **Step 4: Convert NFS dependency and image serving.** Use host mount units/`RequiresMountsFor`, make app and Nginx refuse startup when the expected mount is absent, and preserve UID/GID and read-only behavior.
- [ ] **Step 5: Convert certificate and timer operations.** Implement reload-only certificate renewal, single-run locks, failure logging and no duplicate Spring scheduled jobs.
- [ ] **Step 6: Run shell tests and fixture-based dry runs.** Expected: PASS; no actual production or staging data transfer from the local worktree.
- [ ] **Step 7: Commit.** `git add deploy scripts/test-host-native-data-migration.sh && git commit -m "ops: automate native data and certificate lifecycle"`.

## Task 7: Update release evidence, quality gates and workflow contracts

**Files:**
- Modify: `scripts/check-staging-checklist.sh`
- Modify: `scripts/prepare-release.sh`
- Modify: `scripts/verify-production-release.sh`
- Modify: `scripts/test-prepare-release.sh`
- Modify: `scripts/test-release-sequence.sh`
- Modify: `scripts/test-staging-checklist.sh`
- Modify: `.github/workflows/quality.yml` only for host-native static contracts that remain offline
- Modify: `docs/engineering/unified-release-pipeline.md`

**Interfaces:**
- Evidence parser accepts `runtime_mode=host-native`, `test_resource_manifest_sha`, `cleanup=result=passed`, full commit SHA, command, timestamp, WARNING result and `result=passed`.
- Release preparation rejects old Docker-only evidence, mismatched resource manifests, missing cleanup proof, changed main SHA, dirty worktree, missing Changelog and reused tags.

- [ ] **Step 1: Add failing evidence fixtures.** Extend release tests with missing runtime mode, missing resource digest, cleanup failure, staging DB/index name equal to production, mismatched artifact SHA and stale commit cases.
- [ ] **Step 2: Run `bash scripts/test-prepare-release.sh bash scripts/test-release-sequence.sh bash scripts/test-staging-checklist.sh` according to the repository’s test invocation convention and verify new cases fail.**
- [ ] **Step 3: Implement the evidence contract and stale-record invalidation.** Make every staging run invalidate old records before provisioning and write new records only after tests and restore checks pass.
- [ ] **Step 4: Update release sequence and production verification.** Replace Compose status checks with systemd/artifact/SHA/port/version/health/log checks; retain current main SHA and annotated SemVer/known_hosts guards.
- [ ] **Step 5: Update offline quality workflow contracts.** Keep CI free of SSH, staging, production, Docker and external processes; add only static host-native script/documentation checks.
- [ ] **Step 6: Run the full local quality entrypoint.** First refresh the Maven cache with `./mvnw clean install -DskipTests -Dsort.skip=true`, then run `bash scripts/run-local-quality.sh`; resolve every WARNING.
- [ ] **Step 7: Commit.** `git add scripts .github/workflows/quality.yml docs/engineering/unified-release-pipeline.md && git commit -m "ci: enforce native runtime release evidence"`.

## Task 8: Rewrite the project knowledge base and mandatory rules

**Files:**
- Modify: `deploy/README.md`
- Modify: `docs/releases/README.md`
- Modify: `docs/README.md`
- Modify: `docs/engineering/gotchas.md`
- Modify: `docs/agent-guides/maven.md`
- Modify: `docs/architecture/overview.md`
- Modify: `AGENTS.md`
- Modify: `CHANGELOG.md`
- Modify: `docs/architecture/decisions/README.md`

**Interfaces:**
- `deploy/README.md` remains the only deployment runbook and must describe host-native normal operation plus Docker only as migration rollback.
- `docs/releases/README.md` remains the only release/tag authority and must describe external JAR build, evidence, Tag and production remote wrapper.
- `AGENTS.md` and automated checks express the same rules without relying on session memory.

- [ ] **Step 1: Add the Unreleased migration entry.** Categorize runtime, deployment, testing and release-process changes before the first staging deployment.
- [ ] **Step 2: Rewrite deployment instructions.** Replace Compose topology, `ctl.sh`, Docker bootstrap, container health, `docker exec` sync, and Docker certificate operations with systemd services, artifact directories, host commands, test slot and rollback instructions.
- [ ] **Step 3: Rewrite release and unified pipeline instructions.** Document the candidate→staging test slot→owner acceptance→fast-forward→annotated Tag→production artifact flow and evidence fields.
- [ ] **Step 4: Update navigation and architecture docs.** Add links to ADR-0016/spec/plan without duplicating the deployment runbook; state that runtime architecture belongs to project docs, while Obsidian-to-bytedepth rules remain separate.
- [ ] **Step 5: Replace Docker runtime gotchas with native-service gotchas.** Retain Docker only where explicitly used as a rollback or non-runtime test tool, if any remains.
- [ ] **Step 6: Update AGENTS mandatory rules.** Remove target-host Docker/Maven prewarm rules, add artifact/SHA/systemd/test-slot/evidence/cleanup rules, and preserve exact production SSH and staging URL constraints.
- [ ] **Step 7: Run documentation/link/contract checks.** Run `git diff --check`, existing documentation checks, `bash scripts/check-staging-checklist.sh`, and `rg` assertions proving no normal-operation document still instructs Compose deployment.
- [ ] **Step 8: Commit.** `git add AGENTS.md CHANGELOG.md deploy/README.md docs/README.md docs/releases/README.md docs/engineering/unified-release-pipeline.md docs/engineering/gotchas.md docs/agent-guides/maven.md docs/architecture && git commit -m "docs: switch deployment knowledge base to native runtime"`.

## Task 9: Complete local verification and staging migration

**Files:**
- Modify only if verification discovers a documented defect; otherwise no source changes.
- Evidence: `/var/lib/bytedepth-staging/test-history/staging-integration` and `staging-e2e` on host 124, copied to a fresh local temporary directory for release preparation.

- [ ] **Step 1: Verify clean branch and tracked changes.** Run `git status --short`, `git diff --check`, and all changed shell contract tests; no WARNING is acceptable.
- [ ] **Step 2: Refresh Maven cache and run local quality.** Run `./mvnw clean install -DskipTests -Dsort.skip=true`, then `bash scripts/run-local-quality.sh`; run frontend setup before any frontend test.
- [ ] **Step 3: Deploy the candidate to staging.** Use `deploy/deploy-staging.sh <candidate-ref>`; the script must install native units, build/upload the external artifact, invalidate old evidence and report service/artifact/resource identities.
- [ ] **Step 4: Run integration and E2E on staging.** Use the automated runners; verify IT/E2E manifests differ from staging names, Redis namespace scans are clean after teardown, Meili `posts` is unchanged, and normal app is restored.
- [ ] **Step 5: Run `bash scripts/check-staging-checklist.sh`.** Confirm both commit-bound evidence records have `runtime_mode=host-native`, resource manifest digest, cleanup success, zero unallowlisted WARNING and the candidate full SHA.
- [ ] **Step 6: Project owner acceptance.** Acceptance occurs only on restored `https://staging-bytedepth.bytedepth.cn/`, after test slot cleanup and normal staging health checks.
- [ ] **Step 7: Stop on any failure.** Do not merge or create a Tag. If restoration or cleanup fails, use the scripted recovery path, preserve evidence as failed, and add the discovered rule and regression check to project docs before retrying.

## Self-review checklist

- [ ] Every spec section maps to one or more tasks: runtime, external artifact, systemd, data migration, test isolation, Redis namespace, Meili index, E2E handoff, certificates/NFS, evidence, knowledge base, release, rollback and Docker asset removal.
- [ ] No task permits a fallback to staging resources, a default/temp account, Docker deployment assets, target-host Maven build, or unchecked warning.
- [ ] All names used by later tasks are defined earlier: `RUN_ID`, manifest, `RedisKeyNamespace`, configured search index, test-slot unit and evidence fields.
- [ ] Every destructive action is manifest-scoped, locked, checksum/identity-verified and followed by restoration checks.
- [ ] Local tests remain process-free; only staging tests connect to MySQL, Redis, Meilisearch, Nginx or systemd.
