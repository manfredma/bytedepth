# 统一三项目发布流水线设计

**关联 ADR：** [ADR-0006](../../architecture/decisions/0006-unified-release-pipeline.md)

## 目标与边界

bytedepth、Career、Toolbox 对齐相同发布顺序、入口名称、证据格式和失败规则。GitHub Actions 只验证可在无凭据 runner 中运行的质量门禁；它绝不连接 staging、生产、Docker、数据库或共享浏览器。staging 是唯一集成与 E2E 环境，生产只接受 `main` 上受控脚本创建的新 annotated SemVer Tag。

访问 staging 页面必须使用 `https://staging-bytedepth.bytedepth.cn/`。staging 按 `BYTEDEPTH_ENVIRONMENT=staging` 关闭 RSS、sitemap 和 RSS 自动发现，并返回 noindex；生产环境保持这些入口。新域名只是环境入口，不是安全认证。

## 统一的 16 步顺序

1. 在功能分支执行 `scripts/run-local-quality.sh`。
2. 推送分支并创建 PR；`.github/workflows/quality.yml` 重跑同一纯本机质量门禁。
3. `deploy/deploy-staging.sh <branch>` 部署候选。
4. `deploy/bootstrap-staging-runtime.sh --ensure` 验证或预热共享运行时。
5. `deploy/run-staging-integration-tests.sh` 写候选 SHA 的集成 evidence。
6. `deploy/run-staging-e2e-tests.sh` 写候选 SHA 的 E2E evidence。
7. 项目所有者在 staging 完成功能/界面验收；纯交付基础设施改动只需审阅 PR 和自动证据。
8. 所有必需检查通过后合并 PR 到 `main`。
9. `deploy/deploy-staging.sh main` 部署合并后的 main。
10. 再执行 `deploy/bootstrap-staging-runtime.sh --ensure`。
11. 再执行 staging 集成测试。
12. 再执行 staging E2E。
13. `scripts/prepare-release.sh <release> <next-snapshot>` 校验 main SHA evidence、覆盖率、Changelog、工作区和 Tag 唯一性，创建 annotated Tag。
14. 本机执行 `deploy/deploy-production-remote.sh <tag>`；它在 175 远端调用 host-only 的 `deploy/deploy-production.sh <tag>`，并拒绝分支、裸 SHA、轻量 Tag 和已部署版本。
15. `scripts/verify-production-release.sh <tag>` 执行项目特有的 HTTPS、版本、查询链路和日志回归。
16. 项目所有者完成生产验收；记录版本、完整 SHA、时间、验收结论和回滚基线。

任何阶段失败或输出 WARNING 均停止；部署会作废旧 evidence，失败不得复用历史 passed 记录。

## 9 个标准入口

| 入口 | 职责 |
|---|---|
| `scripts/run-local-quality.sh` | Node 安装、Java 单测、前端测试/lint、覆盖率与静态检查；不访问外部进程。 |
| `scripts/check-staging-checklist.sh` | 测试所有 staging/发布脚本的静态自动化约束；由质量与发版流程调用。 |
| `.github/workflows/quality.yml` | PR、main push 与 feat/fix/docs 开发分支 push 的无凭据质量 workflow，只调用本机质量和静态约束。 |
| `deploy/deploy-staging.sh` | 部署命名分支或 main，锁定转换并作废 evidence。 |
| `deploy/bootstrap-staging-runtime.sh --ensure` | 在共享锁下验证 manifest；缺失或失配才预热 Maven、项目 node_modules 和共享浏览器。 |
| `deploy/run-staging-integration-tests.sh` | 仅 staging 的跨进程集成测试。 |
| `deploy/run-staging-e2e-tests.sh` | 仅 staging、使用共享 Chromium 的 E2E。 |
| `scripts/prepare-release.sh` | 仅干净 main；校验两份 main-SHA evidence 后创建 Tag。 |
| `deploy/deploy-production-remote.sh`、远端 `deploy/deploy-production.sh` 与 `scripts/verify-production-release.sh` | 从本机部署不可变 Tag，并以统一入口执行项目特有生产回归。 |

## 验证

每个项目为上述入口增加静态测试；`check-staging-checklist.sh` 必须覆盖所有入口。三个 `quality.yml` 使用相同触发条件（`pull_request` 与 `push` 到 `main`、`feat/**`、`fix/**`、`docs/**`）、相同 Java 25、Maven Wrapper、Node 安装约束和零 WARNING 规则。实现完成后，分别走完整 16 步，证据 SHA、Tag 和生产状态必须逐项一致。
