# 统一发布流程

本流程是 bytedepth、Career 与 Toolbox 的共同发布约束，也是在新 Java 项目初始化时必须复制的发布基线。它不是可选建议：任何 WARNING、失败、证据缺失或 SHA 漂移都会停止后续步骤。任何用户可见、运行时、部署或配置变更，必须在首次 staging 部署前拥有 `CHANGELOG.md` 中非空且分类明确的 `## Unreleased` 条目。

## 为什么分为 workflow 与受控主机脚本

GitHub `quality` workflow 只运行无凭据、无外部进程的质量门禁：Java 25、Maven Wrapper、项目 lockfile 安装、单元测试、前端测试/lint、覆盖率和静态自动化约束。它在 PR、`main` push，以及 `feat/**`、`fix/**`、`docs/**` 开发分支 push 时运行；开发分支 push 是合并前检查的正常触发路径，不能访问 SSH、staging/生产环境文件、数据库、Docker、共享浏览器或部署锁。

staging 是唯一跨进程集成与 E2E 环境；生产操作只在受控主机执行。两类操作分离，避免把生产权限交给普通 CI runner。

访问 staging 页面必须使用 `https://staging.bytedepth.cn/?preview=true` 建立预览状态；不带 `?preview=true` 的公网请求会 301 到 `https://bytedepth.cn`。该参数只是流量路由标记，不是安全认证。E2E runner 的 `E2E_BASE_URL` 保持 staging origin，另由 `E2E_PREVIEW_BOOTSTRAP_URL` 固定写成上述完整预览地址。

## 固定顺序

1. 功能分支先完成 `CHANGELOG.md` 的 `Unreleased` 条目，再运行 `scripts/run-local-quality.sh`；缺少条目时门禁失败。
2. PR 的 `.github/workflows/quality.yml` 通过。
3. 部署候选：`deploy/deploy-staging.sh <branch>`。
4. `deploy/bootstrap-staging-runtime.sh --ensure` 确认共享运行时，只有失配才预热。
5. 运行 staging 集成与 E2E；两份 evidence 必须绑定候选完整 SHA。
6. 所有者完成 staging 验收；纯交付基础设施改动审阅 PR 与自动证据即可。
7. 合并 `main` 后先比较完整 SHA：若采用 Fast-forward 且 `main` HEAD 与候选验收 SHA 完全一致，直接复用候选 evidence，跳过重复 staging 部署、集成和 E2E；若 SHA 发生变化，必须按步骤 3–5 为 `main` 重新部署并验收。
8. `scripts/prepare-release.sh <release> <next-snapshot>` 校验 main evidence、工作区、Changelog、覆盖率及 Tag 唯一性，创建 annotated Tag。
9. 从本机执行 `BYTEDEPTH_PRODUCTION_SSH_KEY=\"$HOME/.ssh/ubuntu_2.pem\" ./deploy/deploy-production-remote.sh <tag>`；该入口在 175 远端执行 host-only 的 `deploy/deploy-production.sh <tag>`。
10. `scripts/verify-production-release.sh <tag>` 完成 HTTPS、版本、项目查询链路和日志回归；所有者记录生产验收与回滚基线。

## 标准入口

新 Java 项目必须提供并在知识库链接以下入口：

- `scripts/run-local-quality.sh`
- `scripts/check-staging-checklist.sh`
- `.github/workflows/quality.yml`
- `deploy/deploy-staging.sh`
- `deploy/bootstrap-staging-runtime.sh --ensure`
- `deploy/run-staging-integration-tests.sh`
- `deploy/run-staging-e2e-tests.sh`
- `scripts/prepare-release.sh`
- `deploy/deploy-production-remote.sh`（本机唯一生产部署入口）
- `deploy/deploy-production.sh`（175 生产主机内部实现）
- `scripts/verify-production-release.sh`

项目可以在生产回归脚本中验证不同的业务查询，但不能改名、跳过入口或把集成/E2E 移回本机。新项目创建时还必须把这份流程、ADR 和各入口的静态检查一并纳入仓库；不要依赖任何 agent 的个人记忆。
