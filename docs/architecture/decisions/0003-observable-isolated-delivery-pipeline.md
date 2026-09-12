# ADR-0003: 可观测且隔离的交付流水线

- **状态**: Accepted
- **日期**: 2026-09-12
- **决策者**: 项目所有者

## 上下文

当前 staging 部署、集成测试和 E2E 使用同一互斥锁，但没有统一阶段耗时记录。集成 runner 还会临时创建 Maven 工作区和 Testcontainers，重复下载或创建基础设施，既延长验收，也将测试结果绑定到一次性运行环境。现有 Git 托管仓库没有 CI 质量门禁。

项目必须保留单元测试与跨进程测试的边界：只有断网、无外部进程的测试可在本机或托管 CI 运行；Docker、MySQL、Redis、Flyway、浏览器和 staging HTTP 场景只能在 staging。124/175 是运行环境，不能成为执行来自任意分支代码的通用 CI Runner。

## 决策

采用脚本优先、证据优先的流水线，阶段记录统一使用 `phase`、`result`、`started_at_epoch_ms`、`finished_at_epoch_ms` 和 `duration_ms`。staging 记录以完整 commit SHA 命名；生产记录以不可变 SemVer Tag 命名；本地与 GitHub Actions 记录作为该次运行的日志/工件输出。

staging 明确拆为四个入口：受维护者显式调用的 runtime bootstrap、候选部署、集成 runner 与 E2E runner。bootstrap 负责依赖和浏览器等可缓存运行时；部署和 runner 只读验证该运行时，禁止下载依赖、安装浏览器、创建 Testcontainers 或重置数据卷。跨进程测试改为连接已部署的 staging Compose 服务。

GitHub Actions 使用 GitHub 托管 Runner，仅运行纯单元质量门禁及脚本契约测试；不得拥有 staging、生产、SSH 或部署密钥。staging/生产仍由现有受控脚本和人工审批运行。暂不引入 Jenkins 或自托管 Runner。

放弃只在终端输出耗时：它无法将失败、结果和提交绑定。放弃 Jenkins：当前单仓库/单 staging 的编排复杂度不足以抵消 Controller、插件、凭据、备份和 Agent 隔离成本。放弃将 GitHub Runner 安装到 124/175：PR 代码不能在持有环境凭据和 Docker 权限的主机上运行。

## 后果

**正向**

- 每次交付可定位慢阶段，并能区分 bootstrap、部署、验证和测试耗时。
- staging 验收不再因临时依赖安装或 Testcontainers 拉起基础设施而变慢。
- CI 在 PR 早期反馈单元测试、覆盖率和脚本回归，不扩大运行环境权限。

**负向**

- 需要维护 runtime manifest、阶段契约测试和受控证据目录。
- staging 集成测试必须显式管理测试数据隔离，不能依赖 Testcontainers 的临时数据库。
- GitHub Actions 引入 workflow 维护和托管分钟消耗。

## 假设

| 假设 | 可证伪信号（出现时重评） |
|------|--------------------------|
| GitHub 托管 Runner 能在可接受时间内完成纯单元门禁 | 连续一个月 P95 时长超过团队可接受阈值或托管额度不可用 |
| staging 主机可以预置并复用 Maven/Node/Chromium 运行时 | lockfile 或运行时频繁变化导致 bootstrap 成为主要等待时间 |
| 现有 staging Compose 服务可供集成测试隔离使用 | 测试数据互相污染或服务 DNS/权限无法满足测试契约 |

## 退出条件

| 信号 | 预设行动 |
|------|----------|
| 多仓库、多环境或并发部署需要集中排队、审批和可视化编排 | 评估独立 Jenkins Controller 与隔离 Agent，禁止复用 124/175 |
| staging 集成测试 P95 仍长期超过部署耗时 | 评估专用 staging 测试节点或按服务拆分并行 runner |
| GitHub 托管 CI 无法满足合规或网络访问限制 | 评估一次性、无生产网络访问的专用 Runner |
