# 部署说明

ByteDepth 的 staging、production 构建、制品生成、环境绑定、发布、验证和回滚统一由 release-platform 负责。

本仓库不再提供可执行的发布入口，也不允许从项目工作区直接发布。保留的 `deploy/` 内容只服务于运行时依赖准备、集成测试和 E2E 测试，由 release-platform 的 Host Agent 按平台编排调用，不是人工发布入口。

## 发布操作

Agent 调用平台 API 前，先执行[发布 Agent 鉴权步骤](../docs/engineering/release-platform-only.md#发布-agent-鉴权先读在线手册再验证)，读取并执行其中 canonical 在线手册，取得实际验证结果。构建、staging、验收与 production 操作继续按[平台发布说明](../docs/engineering/release-platform-only.md)进入对应平台手册；人类可在控制面页面查看状态。

项目仓库、完整候选 SHA、版本准备与人工验收授权由 bytedepth 定义；环境绑定、平台鉴权、任务和制品状态由 canonical control plane 定义。确认 staging 与 production 绑定既有主机，不在 ByteDepth 仓库中修改主机、目录或 systemd 配置。

平台使用项目与环境元数据配置模块布局、制品、runner、native 运行时、端口、数据目录和 systemd unit，阶段算法不按项目名称分流。历史发布说明只用于追溯，不得照此执行。

## staging 测试 runner 契约

Host Agent 调用 `deploy/run-staging-integration-tests.sh --commit-sha <40 位 SHA>` 和 `deploy/run-staging-e2e-tests.sh --commit-sha <40 位 SHA>`。缺失、重复或未知参数均失败；runner 不再读取 `/opt/bytedepth` 的 Git HEAD 来证明候选身份。

平台提供以下上下文：

| 变量                                                                    | 说明                                                                                    |
| ----------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| `RELEASE_PLATFORM_TEST_SOURCE_ROOT`                                     | 本次已部署 release 的 `source` 快照目录，包含 `.release-platform-source-commit`         |
| `RELEASE_PLATFORM_TEST_RELEASE_MANIFEST`                                | 本次 release manifest，含 `commit`、`source_commit`、`source_sha256`、`artifact_digest` |
| `RELEASE_PLATFORM_TEST_SOURCE_ARCHIVE`                                  | 与 manifest 校验和一致的 `staging-source.tar.gz`                                        |
| `ARTIFACT_DIGEST` / `RELEASE_PLATFORM_TEST_ARTIFACT_DIGEST`             | 签名任务制品 digest（后者为 verifier 显式传入的同值别名）                               |
| `RELEASE_PLATFORM_TASK_NONCE`                                           | 当前任务 attempt nonce                                                                  |
| `RELEASE_PLATFORM_TEST_DEPLOY_HISTORY`                                  | 本次部署记录，含唯一 `commit` 和 `artifact_digest`                                      |
| `RELEASE_PLATFORM_TEST_EVIDENCE_DIR`                                    | 当前 verifier 的证据目录                                                                |
| `RELEASE_PLATFORM_TEST_LOCK_FILE` / `RELEASE_PLATFORM_TEST_LOCK_HELD=1` | 统一测试锁及外层已持锁标记；已持锁时 runner 不再次 flock                                |
| `RELEASE_PLATFORM_TEST_RUNTIME_MANIFEST`                                | 与候选 npm/Maven 依赖输入、Chromium 和 ffmpeg 一致的既有依赖 manifest                   |
| `RELEASE_PLATFORM_TEST_CONFIG_FILE`                                     | 既有 staging 配置；必须声明 staging 模式                                                |
| `RELEASE_PLATFORM_TEST_STATE_DIR` / `RELEASE_PLATFORM_TEST_WORK_DIR`    | 隔离测试 slot 状态和 integration 临时工作目录                                           |
| `RELEASE_PLATFORM_TEST_JAR`                                             | 已部署 E2E test-slot JAR；默认 release 下的 `app.jar`                                   |

隔离 MySQL、Redis、Meili、fixture、管理员账号和 native target 配置仍须由环境显式注入，原有资源隔离、清理和依赖校验保持生效。源快照没有 `.git`，也不自带 `node_modules`；E2E runner 在隔离快照中按 lockfile 执行 `npm ci --ignore-scripts --no-audit --no-fund`；宿主机须预备一致的 runtime manifest 与离线 Maven 仓库。依赖准备或校验失败时失败，不能退回版本探针。

runtime manifest 的 `maven_inputs_sha256` 覆盖根 POM、递归声明的 reactor 模块 POM、`.mvn/` 下的 settings/config/extensions/wrapper 文件及 `mvnw`、`mvnw.cmd`，按稳定的相对路径和文件字节计算；不扫描 `target`、`node_modules` 或 Android 生成目录。该计算不依赖 `.git`，适用于平台的 source 快照。仅含旧 `pom_sha256` 的 manifest 不可复用；模块依赖、Maven 配置或 wrapper 改变时，由 Host Agent 在既有锁内调用 `ensure_staging_runtime` 重新准备同一候选的依赖，成功后才写新 manifest。声明的模块 POM 缺失或无法读取时拒绝校验和写入，不能沿用旧指纹或放宽离线 Maven 策略。

integration 从本次 Maven Failsafe summary 统计 `completed - skipped`（执行前清除旧 summary）；E2E 使用本次 Playwright JSON reporter 的成功用例数。用例数为零或报告失败时，不生成 PASS。证据包含 `commit`、`artifact_digest`、`attempt_nonce`、`tests_total`、`command`、`result` 与清理结果；这些文件仅由正式 Host Agent 任务生成，本地回归测试不上传为 staging evidence。

本地防复发检查：`bash scripts/test-staging-platform-contract.sh`、`bash scripts/test-run-staging-integration-tests.sh` 和 `bash scripts/test-run-staging-e2e-tests.sh`，也纳入 `scripts/run-local-quality.sh`。
