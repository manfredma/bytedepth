# ADR-0016: 小资源单机环境采用宿主机原生运行时

- **状态**: Proposed
- **日期**: 2026-09-24
- **决策者**: 项目所有者与维护团队

## 上下文

当前生产和 staging 都通过 Docker Compose 运行 Java 应用、Nginx、MySQL、Redis 和 Meilisearch。Docker 提供了环境隔离和一致性，但在小规格单机上，构建、镜像、容器和测试容器会放大内存与磁盘峰值。项目当前需要降低运行和发布期间的资源压力，同时保留 MySQL、Redis 和 Meilisearch 的现有能力。

项目要求生产和 staging 隔离、可回滚、可审计，并且不能因为迁移运行方式而丢失文章、用户、图片或搜索索引数据。

## 决策

生产和 staging 的运行时统一改为宿主机原生服务，由 systemd 管理 Java 25 应用、Nginx、MySQL 8、Redis 7 和 Meilisearch 1.7；Docker 不再承载运行时服务。应用构建在目标主机之外完成，目标主机只接收经过校验的不可变 JAR 发布包。

现有 `/data/mysql`、`/data/redis`、`/data/meilisearch` 和图片目录在完成备份、版本兼容性与权限检查后由宿主机服务接管。迁移先在 staging 停机完成并通过集成、E2E 和只读回归，再迁移生产。

这项决策同时覆盖交付和知识库边界：部署操作仍以 `deploy/README.md` 为唯一权威，版本与 Tag 以 `docs/releases/README.md` 为唯一权威，跨项目门禁以 `docs/engineering/unified-release-pipeline.md` 为权威；`docs/README.md`、`AGENTS.md`、工程陷阱和脚本契约必须同步更新。不能只替换 Compose 文件而保留旧的发布、证据、同步、证书和测试流程。

运行时禁止 Docker/Compose；测试工具是否使用一次性容器不属于运行时架构，默认改为连接 staging 原生服务的隔离测试资源，避免把 Docker 重新变成 staging 验收前提。

集成测试和 E2E 采用串行 test slot，不直接使用 staging 资源：每次 run 创建独立 MySQL 数据库和最小权限账号、Redis 预留 logical DB 加运行级 key namespace、Meilisearch 独立 index；测试应用临时接管 staging URL，完成后由同一编排脚本销毁资源并恢复 staging 应用。只有在逻辑隔离经压测证明不足时，才升级为第二套宿主机中间件服务。

隔离资源配置通过 Spring Profile 管理：IT 使用 `staging-it`，E2E 使用 `staging-e2e`。
profile 文件定义资源映射和必需配置项，运行时 manifest 只向 profile 提供本次 run 的
外部值；不得在各个 runner 中重复维护一套命令行属性名。

放弃的方案：

- **继续全部使用 Docker Compose**：运行方式稳定，但不能满足当前小资源环境降低容器与构建峰值的目标。
- **应用原生运行、中间件保留 Docker**：改动较小，但无法解决中间件容器本身的资源与数据运维问题，形成两套运行时。
- **移除 Meilisearch、改用 MySQL 搜索**：资源消耗更低，但会牺牲当前的全文搜索、高亮、容错和排序体验；暂不改变搜索能力。

## 后果

**正向**

- 运行时不再需要 Docker Engine、Compose、应用镜像和中间件容器。
- Java 应用可以直接使用宿主机 Java 25，发布时不需要在小规格主机上构建镜像。
- 服务、端口、数据目录、日志和资源限制可由 systemd 与宿主机工具直接观测。
- 应用发布可以通过不可变 JAR 目录和 `current` 软链接实现快速切换。

**负向**

- 需要自行维护 systemd unit、包版本、权限、升级和回滚逻辑。
- 宿主机依赖会与其他项目共享，环境漂移风险高于容器。
- 中间件升级和数据目录接管必须严格校验版本、用户权限和备份恢复能力。
- 运行时仍然消耗 Java、MySQL、Redis 和 Meilisearch 自身的资源；原生化不会消除这些基础开销。
- Docker 部署脚本、Compose 文件、Dockerfile 和 Docker 专用发布校验在实现阶段删除；回滚依赖上一份原生 JAR、systemd 配置和已验证的数据备份。主机上是否仍安装 Docker 不属于 bytedepth 的部署方案。
- 发布流程需要从“目标机重新构建镜像”改为“外部构建不可变 JAR、传输、SHA 校验和 systemd 切换”，并同步改造 staging/生产 evidence、数据同步、证书、NFS 和集成测试脚本。
- 项目知识库必须明确运行时、部署、发布和测试的边界；迁移完成前旧 Compose 说明只能作为回退路径，不能继续作为正常操作说明。

## 假设

| 假设 | 可证伪信号（出现时重评） |
|------|--------------------------|
| staging 和生产主机可安装并长期维护 Java 25、MySQL 8、Redis 7、Meilisearch 1.7 | 任一组件无法获得可验证的固定版本安装源或安全更新路径 |
| 现有数据目录可以在停机后由对应宿主机服务安全接管 | 版本、存储格式或权限检查不通过，或恢复演练无法得到一致数据 |
| 目标主机的运行内存足以承载原生服务 | 健康检查失败、发生 OOM、swap 持续增长或发布期间服务互相挤压 |
| 外部构建机可以生成并传输与发布 Tag 对应的 JAR | 无法校验 JAR 的完整 SHA，或无法将构建产物与 Tag 绑定 |

## 退出条件

| 信号 | 预设行动 |
|------|----------|
| 原生运行时在 staging 连续两次无法完成完整集成和 E2E 验收 | 使用上一份原生 release 和数据备份恢复 staging，暂停生产迁移并重新评估方案 |
| 生产出现 OOM、数据损坏或搜索索引无法恢复 | 停止原生迁移，按备份和回滚 runbook 恢复上一运行方式 |
| 原生化后资源峰值仍超过主机容量 | 优先扩容或拆分数据服务，不通过删除备份、放宽安全限制或跳过测试解决 |
