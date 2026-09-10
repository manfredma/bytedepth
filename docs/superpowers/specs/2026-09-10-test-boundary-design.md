# 测试边界重构设计

**状态：** 待项目所有者评审

**关联 ADR：** [ADR-0002](../../architecture/decisions/0002-staging-integration-test-boundary.md)

## 目标

让本机单元测试可在断网、无 Docker、无 Redis/MySQL/Flyway/Nginx 的机器稳定运行；所有跨进程依赖验证与 E2E 只在 staging 执行，并让 Maven 命令、测试文件名和发布门禁准确表达该边界。

## 分类规则

| 类别 | 定义 | 命名 / 入口 | 执行位置 |
| --- | --- | --- | --- |
| 单元测试 | 断网、单进程可运行；允许 mock、fake、内存数据库和进程内 Redis | `*Test`；Surefire 默认 `mvn test` | 本机或 staging |
| 集成测试 | 连接独立 Redis、MySQL、Flyway、Nginx、Testcontainers 或其他跨进程服务 | `*IT`；Failsafe `verify -Pstaging-integration` | 仅 staging |
| E2E | 浏览器驱动真实部署页面和交互 | `tests/e2e/*.spec.js` | 仅 staging 主机 |

文件名与构建规则双重约束：Surefire 默认排除 `*IT`，Failsafe 只包含 `*IT`。集成测试不得再以 `*Test` 命名；本机覆盖率脚本只调用单元测试生命周期。

## 构建设计

根 POM 增加 Failsafe 插件和 `staging-integration` profile。默认生命周期只执行 Surefire 的 `*Test`；profile 启用 Failsafe 的 `integration-test` / `verify`，仅发现并执行 `*IT`。现有 `RedisRateLimitAdapterTest` 改为 `RedisRateLimitAdapterIT`，其余不连接独立服务的 Redis mock 测试保留 `*Test`。

`scripts/verify-changed-coverage.sh` 保持离线：执行缓存刷新与单元测试覆盖率，不激活集成 profile。`scripts/prepare-release.sh` 不再把本机全量 Maven 当作集成放行证据；生产 Tag 前须已完成 staging 集成脚本和 staging E2E。

## staging 执行设计

新增受控 staging 验证脚本，部署候选 ref 后从 `/opt/bytedepth` 执行。脚本启动一个 `--rm` Maven Java 25 容器，将工作树只读挂载、连接 `bytedepth_default` 网络，并通过服务 DNS 名访问依赖。测试配置通过受限环境变量或 Maven system properties 获取服务名与临时测试命名空间；禁止 `localhost`、禁止暴露 Redis/MySQL 端口、禁止读取生产密钥。

对于当前 Redis 限流集成测试，测试容器访问 `bytedepth-redis:6379`。每次测试使用 UUID 规则键并在 finally 清理；测试容器退出即销毁。MySQL/Flyway 集成测试后续遵循同一入口，使用 staging 的临时 schema 或事务隔离，不共享生产数据。

## 失败和发布行为

- 本机 `*Test` 失败：阻止提交与发布；先修复。
- staging `*IT` 或 E2E 失败：阻止 PR 合并与生产 Tag；先修复或由项目所有者明确暂缓该独立待办。
- staging 测试基础设施不可用：输出容器、网络和服务健康状态，停止发布；不回退到本机 Docker。
- Maven、静态检查和 staging 脚本输出中的 WARNING 均为失败。

## 验收

1. 断网且无 Docker 的机器能执行 `mvn test` 与变更覆盖率，不尝试连接 `localhost:6379`。
2. staging `staging-integration` profile 执行 Redis `*IT` 并真实写入、拒绝第二次消费、清理测试键。
3. staging 完整 E2E 保持在 staging 主机运行。
4. 发布脚本在缺少 staging 集成成功记录时拒绝创建 Tag；记录存在且所有门禁绿时才允许发布。
5. 文档、AGENTS 与部署说明使用同一分类词汇和命令。
