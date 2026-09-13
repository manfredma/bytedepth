# 可观测交付流水线设计

**日期：** 2026-09-12

**状态：** 已获项目所有者确认，待随实现 PR 评审

**关联 ADR：** [ADR-0003](../../architecture/decisions/0003-observable-isolated-delivery-pipeline.md)

## 目标

为本地质量门禁、GitHub CI、staging bootstrap/部署/集成/E2E、release 与生产部署提供逐阶段耗时；每条远端记录可验证地绑定候选 commit 或发布 Tag。缩短 staging 验收的重复准备时间，同时保持 ADR-0002 的测试边界。

## 非目标

- 不部署 Jenkins、自托管 GitHub Runner、指标数据库或仪表盘。
- 不允许 GitHub Actions 访问 staging/生产、Docker Socket 或部署 SSH 凭据。
- 不改变生产发布必须由干净 `main` 的新 annotated SemVer Tag 触发的规则。

## 统一记录契约

`deploy/lib/timing.sh` 提供以下接口：

```bash
initialize_timing_file TIMING_FILE IDENTITY
record_timed_phase TIMING_FILE PHASE command arg...
record_timing_phase TIMING_FILE PHASE passed|failed START_MS END_MS
require_timing_identity TIMING_FILE IDENTITY
```

文件首行是 `identity=<40 位 SHA 或 SemVer Tag>`，每个阶段一行：

```text
phase=integration_maven_verify result=passed started_at_epoch_ms=... finished_at_epoch_ms=... duration_ms=...
```

`record_timed_phase` 无论命令成败都写入阶段；调用方保留原始退出码。每个入口另记录一个 `*_total` 阶段。标准输出使用同值的 `TIMING` 行，便于本地和 CI 收集。阶段名固定为小写 ASCII、数字与下划线，避免证据解析歧义。

## 执行边界与阶段

| 入口 | 阶段 | 证据位置 | 禁止事项 |
| --- | --- | --- | --- |
| `scripts/run-quality-gates.sh` 与 GitHub CI | Maven 缓存刷新、单元测试、覆盖率、前端测试/lint、脚本契约测试、`quality_total` | CI 工件和 stdout | 外部进程、Docker、网络服务 |
| `bootstrap-staging-runtime.sh` | runtime 检查、Maven 缓存预热、Node 依赖、Chromium 校验、`bootstrap_total` | `/var/lib/bytedepth-staging/runtime/manifest` 与 timing 文件 | 部署候选、写验收 evidence、清空卷 |
| `deploy-staging.sh` | fetch、checkout、runtime preflight、Compose build/rollout、Nginx reload、HTTPS ready、`deployment_total` | `/var/lib/bytedepth-staging/timing/<SHA>` | npm install、浏览器下载、Testcontainers |
| staging integration/E2E runner | Maven verify 或 Playwright、各自总耗时 | 同一 `<SHA>` timing 文件 | 下载依赖、创建 Docker/Testcontainers、变更 runtime |
| `prepare-release.sh` | evidence 校验、覆盖率、Maven release prepare、push、`release_total` | stdout 和 `release-timing/<Tag>` | 绕过 SHA 证据 |
| `deploy-release.sh` | fetch/tag 校验、checkout、Compose rollout、ready、`production_deployment_total` | `/var/lib/bytedepth-deploy/timing/<Tag>` | 分支或裸 SHA 部署 |

staging timing 与既有 test evidence 共享锁。新的部署先作废旧 test evidence 和同 SHA 以外的当前记录；只有成功且零告警的 runner 才可写入 `result=passed` 的原有四行 evidence。

## Stable staging runtime

bootstrap 是 root 显式维护动作，只在 `package-lock.json` 或 Maven 依赖描述改变后运行。它准备项目 Node/Maven 依赖并校验由主机维护的既有 Chromium，不下载浏览器；随后写入 root-owned `0600` manifest，记录 commit、Node lockfile SHA、Maven 输入 SHA 和 Chromium 可执行文件/版本。部署与 runner 必须验证 manifest 输入与 checkout 相同，否则失败并提示维护者 bootstrap；不能自行修复环境。

集成 runner 使用 staging 已有 Compose 网络和服务 DNS。由于这些服务不向宿主发布端口，bootstrap 预置一个仅供测试的长期 Maven runner service；验收入口只 `exec` 它，不能临时 `docker run`、挂 Docker Socket 或创建 Testcontainers。`*IT` 连接测试专用 schema/凭据。runner 对 `WARN` 与 `WARNING` 都拒绝，避免当前仅匹配文字 `warning` 而遗漏框架日志级别的问题。

## GitHub Actions CI

新增 `.github/workflows/quality.yml`，在 `pull_request` 和分支推送运行。使用固定 commit SHA 的官方 actions，最小 `contents: read` 权限、无 secrets、无 `pull_request_target`、无自托管标签。workflow 调用 `scripts/run-quality-gates.sh --ci` 并上传无敏感信息的 timing 文本和覆盖率报告。部署、SSH、Docker 和 staging runner 不在 workflow 内。

## 安全与验收

- timing/evidence 目录均为 root `0700`，文件为 root `0600`；不写入密码、环境变量、命令参数或源码内容。
- 所有 grep 门禁同时识别 `WARN`、`WARNING` 及其大小写变体；出现即失败并不保留通过证据。
- 文档更新 `deploy/README.md`，说明 bootstrap → deploy → integration → E2E 的顺序、计时证据和 GitHub CI 的权限边界。
- 每一条库函数和入口约束都有不接触真实 staging 的 shell 契约测试；业务测试改造另有对应的 staging 集成验证。
