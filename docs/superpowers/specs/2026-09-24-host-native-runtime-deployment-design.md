# 宿主机原生运行时部署设计

## 状态

提案，等待项目所有者审阅。本文只定义目标架构与迁移边界，不包含本次文档提交后的运行时改动。

关联决策：[ADR-0016](../../architecture/decisions/0016-host-native-runtime-deployment.md)

## 背景与目标

bytedepth 当前在生产和 staging 的单机节点上通过 Docker Compose 运行完整服务栈：Java 25 + Spring Boot、Nginx、MySQL 8、Redis 7 和 Meilisearch 1.7。小规格主机在 Docker 构建、Testcontainers 和多服务并行时会出现明显的内存、CPU 和磁盘峰值。

本设计将运行时改为宿主机原生服务：所有服务由 systemd 管理，应用以外部构建的不可变 JAR 运行，数据目录在停机备份和兼容性检查后由原生服务接管。生产和 staging 都要迁移，但 staging 必须先完成迁移与验收。

### 目标

- 降低运行时和发布期间的容器、镜像和 Docker 构建开销。
- 保留当前 MySQL、Redis、Meilisearch 和 Java 应用能力。
- 保留 staging/生产隔离、发布 Tag 绑定、健康检查、日志门禁和回滚路径。
- 不丢失 MySQL、Redis、Meilisearch、图片、证书和项目配置数据。
- 让原生运行方式可以由自动化脚本重复部署，而不是依赖人工记忆。

### 非目标

- 本次不更换 Meilisearch 为 Elasticsearch，也不改变搜索 API 和索引模型。
- 本次不新增 Maven 模块、不改变业务模块边界、不改文章和批注业务逻辑。
- 本次不把构建责任转移到生产或 staging；目标主机只运行已构建产物。

## 影响面与文档权威边界

这不是只替换 Compose 文件的部署改动，而是一次运行时、交付链和运维知识库的整体迁移。实施时必须按下面的权威边界修改文档，避免同一规则散落在多个地方：

| 内容 | 唯一权威位置 | 本次必须调整的内容 |
|------|--------------|--------------------|
| 部署、停机迁移、回滚、证书、数据同步 | `deploy/README.md` | 从 Compose 操作手册改为宿主机原生操作手册；保留上一份原生 release 和数据备份回退说明，不保留 Compose 操作路径 |
| 版本、Tag、发布、证据和回滚版本 | `docs/releases/README.md` | 改为外部构建 JAR、SHA 绑定、staging 证据、Tag 发布和 systemd 切换流程 |
| 跨项目质量与发布基线 | `docs/engineering/unified-release-pipeline.md` | 改写部署入口、产物流转、staging integration/E2E 和生产发布门禁 |
| 项目知识库导航 | `docs/README.md` | 增加宿主机运行时、迁移设计、部署与发布文档的入口和交叉链接 |
| 架构决策索引 | `docs/architecture/decisions/README.md` | 保留 ADR-0016，并在实施完成后将状态从 Proposed 更新为 Accepted |
| 工程陷阱和长期约束 | `docs/engineering/gotchas.md` | 删除“正常部署依赖 Compose”的规则，补充 systemd、端口、数据目录、共享宿主机和双运行时风险 |
| Agent 工作约束 | 根目录 `AGENTS.md` | 更新 Maven/构建、staging、生产发布、Docker 使用边界和 host-native 自动检查要求 |
| 版本变更记录 | `CHANGELOG.md` | 首次 staging 迁移前增加分类明确、非空的 `## Unreleased` 条目 |

ADR 只记录不可逆或长期架构取舍；本设计稿记录实施范围和验收标准；具体命令和故障处理只写入 `deploy/README.md`。新增文档不能另建一套平行部署手册。

## 目标架构

```text
staging 124 / production 175
┌─────────────────────────────────────────────┐
│ systemd                                      │
│  nginx.service                               │
│  bytedepth-app.service                       │
│  mysql.service                                │
│  redis.service                                │
│  meilisearch.service                          │
├─────────────────────────────────────────────┤
│ /opt/bytedepth/                              │
│  releases/vX.Y.Z/app.jar                    │
│  current -> releases/vX.Y.Z                  │
├─────────────────────────────────────────────┤
│ /etc/bytedepth/                              │
│  application-<environment>.yml               │
│  secrets/                                    │
├─────────────────────────────────────────────┤
│ /data/                                       │
│  mysql/  redis/  meilisearch/  images/       │
└─────────────────────────────────────────────┘
```

### 服务职责与边界

| 服务 | 版本基线 | 管理方式 | 数据/配置边界 |
|------|----------|----------|---------------|
| Java 应用 | Java 25、项目发布 JAR | `bytedepth-app.service` | `/opt/bytedepth/releases`、`/etc/bytedepth` |
| Nginx | 宿主机固定包版本 | `nginx.service` | `/etc/nginx`、证书目录、站点路由 |
| MySQL | 8.0 | `mysql.service` | `/data/mysql`、root/service 凭据 |
| Redis | 7.x | `redis.service` | `/data/redis`、AOF/RDB、密码 |
| Meilisearch | 1.7 | `meilisearch.service` | `/data/meilisearch`、master key |

MySQL、Redis 和 Meilisearch 只绑定回环地址或受控内网地址，不能暴露到公网。Nginx 是唯一对外提供 80/443 的入口。其他项目的 Nginx 路由继续使用宿主机配置目录，不通过容器注入。

## 集成测试与 E2E 资源隔离

### 隔离目标

集成测试和 E2E 不能直接读写 staging 的数据库、Redis key 或 `posts` 搜索索引。测试通过不代表可以污染 staging；测试失败、超时、进程崩溃和清理失败也不能留下会影响下一次验收的资源。隔离必须同时覆盖：

- 数据库 schema、账号和 Flyway 状态；
- Redis 的 session、PV、阅读进度、限流和其他临时 key；
- Meilisearch 的 index、文档和异步 task；
- 应用实例、测试凭据、图片/上传目录和测试 URL；
- 测试运行记录、日志和清理动作。

### 方案比较

| 方案 | 做法 | 优点 | 代价/风险 | 结论 |
|------|------|------|-----------|------|
| A. 同一中间件的逻辑隔离 | 同一 MySQL/Redis/Meili 守护进程内，为每次运行创建独立 DB、Redis DB+前缀、Meili index；测试应用使用独立配置 | 资源占用最低；不需要 Docker；版本与生产一致 | 需要应用支持运行时注入并增加误连/清理校验；测试期间需串行 | **采用** |
| B. 第二套宿主机服务 | 另起 MySQL、Redis、Meili systemd 实例和端口，使用独立数据目录 | 物理边界更强，误清理风险更低 | 内存、磁盘、启动时间和升级维护成本约翻倍；小规格主机风险高 | 作为 A 无法满足资源边界时的回退 |
| C. 每次 Docker/Testcontainers | 测试时临时生成 MySQL 等容器 | 生命周期隔离天然、现有代码改动较少 | 恢复了 Docker 资源峰值，与宿主机原生运行目标冲突 | 不作为 staging 验收方案 |

### 推荐的测试槽位模型

staging 测试使用一个全局部署/测试锁，当前不允许并发运行。每次测试由自动化脚本生成一个不可预测但可审计的 `RUN_ID`，例如 `20260924_231530_a1b2c3d4`，只允许 `[a-z0-9_]`，并在 root-owned 的 0600 manifest 中记录资源映射。

一次完整 staging run 建立两套测试资源，避免集成测试产生的数据影响 E2E：

| 资源 | 集成测试资源 | E2E 测试资源 | staging 正常资源 |
|------|--------------|--------------|-----------------|
| MySQL | `bytedepth_it_<RUN_ID>` + 每次运行专用账号 | `bytedepth_e2e_<RUN_ID>` + 每次运行专用账号 | `bytedepth` |
| Redis logical DB | 预留测试 DB（例如 14）+ `bytedepth:it:<RUN_ID>:` | 另一预留测试 DB（例如 15）+ `bytedepth:e2e:<RUN_ID>:` | staging 配置的正常 DB 和 namespace |
| Meilisearch | `posts_it_<RUN_ID>` | `posts_e2e_<RUN_ID>` | `posts` |
| 应用 | Failsafe 进程通过测试 profile 连接 | `bytedepth-test-slot.service`，接管 8080 | `bytedepth-app.service` |
| 上传目录 | `/data/images-test/<RUN_ID>/it` | `/data/images-test/<RUN_ID>/e2e` | `/data/images` |
| URL | 不对外暴露，使用本机 test profile | 继续使用 `https://staging-bytedepth.bytedepth.cn/` | 同一 URL |

### Spring Profile 配置契约

隔离资源的应用配置统一通过 Spring Profile 选择，不允许在 Maven、systemd 或
Playwright 命令中散落一组互不一致的 `-D` 配置。仓库内固定提供
`application-staging-it.yml` 和 `application-staging-e2e.yml`，分别由
`staging-it`、`staging-e2e` profile 激活；两个 profile 显式配置各自的 JDBC、Redis
logical DB、Redis namespace、限流 Redis 配置、Meilisearch index 和上传目录。

每次 run 的实际数据库地址、随机凭据、Redis DB、namespace、Meili index 和目录由
manifest 生成 root-only 的外部环境文件注入，profile 文件只负责把这些受控输入映射到
Spring 配置。IT 必须以 `spring.profiles.active=staging-it` 运行，E2E test-slot
必须以 `spring.profiles.active=staging-e2e` 启动；缺少 profile 或缺少 profile 所需值时
直接失败。生产默认配置仍由 `application.yml` 提供，不能误激活测试 profile。

为降低资源开销，E2E 不再另开一套 Nginx 或公开域名：测试编排先停止 staging 应用，再启动 `bytedepth-test-slot.service` 接管 8080，Nginx 和证书保持不变。E2E 仍通过规定的 staging URL 运行；测试结束后，无论成功或失败都必须停止测试服务、销毁测试资源并恢复 `bytedepth-app.service`。恢复失败必须使本次 run 失败并阻止后续验收/发布。

这意味着测试槽位是“串行、短生命周期、可恢复”的临时运行时，不是第二套长期 staging。测试锁持有期间页面可能短暂不可用，属于已知的验收窗口行为；项目所有者验收必须在测试槽位清理完成、正常 staging 应用恢复并通过健康检查后进行。

### 数据库隔离

`provision-staging-test-slot.sh` 在停止 staging 应用并取得迁移锁后执行：

1. 生成 `RUN_ID`，校验数据库名、用户名和目录名只能由脚本生成，拒绝外部注入。
2. 创建 `bytedepth_it_<RUN_ID>` 和 `bytedepth_e2e_<RUN_ID>`，分别创建最小权限账号，只授予对应库的 DDL/DML 权限，不把 root 密码传给 Maven、Playwright 或应用日志。
3. 从版本化、经过脱敏校验的 staging 测试 fixture 导入两套库；fixture 至少包含测试文章、分类和现有管理员哈希，不创建临时管理员、不使用默认密码。若 fixture 缺失或校验失败，立即中止，不退化为连接 staging 库。
4. 通过测试应用执行 Flyway，并在应用实际启动后校验 JDBC metadata 中的数据库名等于 manifest 的目标库。
5. 集成测试和 E2E 各自使用不同库；测试标题、slug、上传路径和 fixture 标识都拼入 `RUN_ID`，避免同一 run 的残留数据互相命中。

测试库在 teardown 时先停止所有使用它的进程，再删除账号和数据库；不能执行 `DROP DATABASE bytedepth`、通配符删除或针对 staging 库的 fallback。清理失败时保留带 RUN_ID 的资源用于人工恢复，但必须把 evidence 标记为 failed。

### Redis 隔离

Redis 采用“双重隔离”，不能只依赖 key 前缀：

- 第一层是预留 logical DB。staging 正常 DB、IT DB、E2E DB 必须在部署配置中显式声明；启动前检查 `CONFIG GET databases` 足够且测试 DB 不等于 staging DB。脚本只允许对当前 manifest 指定的 DB 执行 `SELECT`、`SCAN` 和删除，禁止 `FLUSHALL`。
- 第二层是运行级 namespace。所有由应用创建的 key 都必须经过可配置 namespace，包括 Spring Session、PV 统计、阅读进度、Bucket4j 限流和后续新增 Redis key。生产默认值保持现有兼容前缀；测试值分别为 `bytedepth:it:<RUN_ID>:` 和 `bytedepth:e2e:<RUN_ID>:`。

现有硬编码的 `pv:post:`、`bytedepth:reading-progress:`、`bytedepth:rate-limit:` 和 Redis Ops 扫描前缀需要改为由同一个 `RedisKeyNamespace` 配置生成；Spring Session namespace、普通 Redis database 和限流 Redis database 必须统一由测试 profile 注入。这样即使某个测试错误选择了同一个 logical DB，前缀仍能阻止清理和查询越界；反过来，前缀也不能替代 logical DB，因为业务 bug 可能遗漏前缀。

teardown 只按 `SCAN MATCH <namespace>*` 分批删除，并校验删除前后的 key 数量；不得对共享 DB 使用 `KEYS *`、`FLUSHDB` 或 `FLUSHALL`。测试结束后还要扫描 staging namespace，发现 key 数量或采样摘要变化则验收失败。

### Meilisearch 隔离

`MeiliSearchPostIndexer` 当前将 index 名写死为 `posts`，必须改为 `${BYTEDEPTH_SEARCH_INDEX:posts}` 注入。测试应用分别使用 `posts_it_<RUN_ID>` 和 `posts_e2e_<RUN_ID>`，生产/staging 默认仍为 `posts`。

每套测试 index 的生命周期为：创建 → 设置与正式 index 相同的 searchable/filterable/sortable 配置 → 从对应测试库重建测试文档 → 等待 task 完成 → 执行搜索断言 → 删除 index 并等待删除 task 完成。测试脚本必须校验查询 URL 中的 index 名与 manifest 一致，禁止调用 `posts`。

测试 API key 使用 root-only 配置注入；若 Meilisearch 版本支持受限 key，则为测试 index 创建只允许该 index 的 key，禁止把 master key 放入 Playwright、普通日志或 evidence。重建失败、task 超时、index 名不匹配或 staging `posts` 的统计发生变化，都必须 fail-closed。

### 测试槽位生命周期

所有步骤由 `run-staging-test-slot.sh` 统一编排，单独执行 IT/E2E 时也必须复用它的 manifest 和 trap，不能各自实现一套清理：

```text
获取 deployment-test.lock
  -> 作废旧 evidence
  -> 生成 RUN_ID 和 0600 resource manifest
  -> 停止 staging app，确认 8080 已释放
  -> 创建 IT/E2E MySQL 库、账号并导入 fixture
  -> 分配 Redis logical DB + namespace
  -> 创建 IT/E2E Meili index 并重建
  -> 运行 IT
  -> 切换 test-slot service 配置并运行 E2E
  -> 校验 staging 资源未变化
  -> 停止 test-slot，销毁 IT/E2E 资源
  -> 恢复 staging app 并验证正常资源/URL
  -> 仅全部通过时写入包含资源摘要的 evidence
释放锁
```

清理必须注册在脚本的 `EXIT`/异常路径；但 trap 不是唯一保障，主流程还要显式执行 teardown 并验证结果。evidence 只记录 `run_id`、资源类型、数据库/index 名的不可逆摘要、应用 SHA、运行时模式和清理结果，不记录密码、完整 fixture、session key 或用户数据。

### 双套宿主机服务的升级路径

如果压测或故障演练证明同进程 logical DB 隔离不满足性能或安全边界，再切换到第二套宿主机服务：`mysql-test.service`、`redis-test.service`、`meilisearch-test.service` 使用独立数据目录、端口和 unit，应用 test slot 指向它们。这个升级不改变测试 manifest、锁、fixture、evidence 和清理接口；因此先实现资源接口，不把具体的中间件拓扑散落到测试代码中。

## 发布与运行

### 构建产物

构建在本机或受控 CI 完成，使用项目规定的 Maven Wrapper、Java 25 和完整质量门禁。发布产物至少包括：

- 与 annotated Git Tag 对应的 `app.jar`；
- JAR 的完整 SHA-256；
- 版本、commit SHA、构建时间和目标环境元数据；
- 可审计的上传记录。

目标主机不执行 Maven 构建，不从浮动分支构建，不以裸 commit 作为发布输入。远程部署入口仍只接受经过验证的稳定 Tag。

### 交付链和发布顺序

正常发布顺序改为：

1. 在功能分支完成代码、单元测试、静态检查和 `CHANGELOG.md` 的 `Unreleased` 检查。
2. 生成候选 JAR、SHA-256 和构建元数据；候选 ref 只能是完整 commit SHA 或受控分支。
3. 使用 `deploy/deploy-staging.sh <ref>` 将候选产物部署到 124 的宿主机原生运行时，而不是在目标机执行 Docker 构建。
4. 在 `https://staging-bytedepth.bytedepth.cn/` 执行 staging integration、全量 E2E、日志/WARNING 门禁和只读回归，生成与候选完整 SHA 绑定的两份 `result=passed` evidence。
5. 项目所有者在 staging 验收通过后，才以 fast-forward 合并 `main`。
6. 在干净的 `main` 上执行版本记录和 annotated Tag 创建；生产入口仍为本机的 `deploy/deploy-production-remote.sh vX.Y.Z`，但传输并校验外部构建的 JAR，不在 175 构建镜像。
7. 生产部署完成后验证 `/version`、健康探针、只读业务回归、证书/SNI、搜索、图片和日志，并把产物 SHA、运行时模式、服务状态和 Tag 写入发布记录。

候选 evidence 必须同时包含：`candidate_sha`、`tag`（发布阶段）、`runtime_mode=host-native`、JAR SHA-256、服务版本/状态、测试命令、环境 URL、开始/结束时间、WARNING 检查结果、`test_resource_manifest_sha`、`cleanup=result=passed` 和 `result=passed`。合并或发布不能复用缺少这些字段的旧 Docker evidence，也不能复用资源清理失败的测试记录。

### 自动化编排原则

完整流程必须由可重复的脚本编排，人工只做代码评审、staging 验收确认和必要的发布触发，不手工登录主机逐条执行服务命令。编排至少形成下面的单向流水线：

```text
run-local-quality
  -> build-release-artifact
  -> deploy-staging(ref)
  -> run-staging-integration
  -> run-staging-e2e
  -> check-staging-checklist
  -> owner-acceptance
  -> fast-forward main
  -> prepare-release(tag)
  -> deploy-production-remote(tag)
  -> verify-production-release(tag)
```

每一步都必须：

- 接收明确的 ref、环境和产物路径，禁止隐式默认值；
- 使用同一把部署/迁移锁，失败时立即作废本次 evidence，不继承上次成功记录；
- 输出结构化、无凭据的日志和 evidence，供下一步校验，而不是靠人复制一句“成功”；
- 在下一步开始前校验上一步的完整 SHA、产物 SHA、环境和运行时模式；
- 对外部状态使用 fail-fast 检查，对 WARNING、端口占用、服务未 ready、证书不匹配、磁盘不足和权限异常直接中止；
- 支持 `--dry-run` 或 fixture 模式供本机契约测试使用，但 dry-run 结果不能作为 staging/生产 evidence。

部署脚本需要在事务边界内完成“上传临时文件 → 校验 → 安装版本目录 → 切换 current → 重启/健康检查 → 写入部署记录”；中途失败不得留下看似成功的 current 或半写入 evidence。回滚也必须由脚本执行并重新生成回滚后的验证记录。

### systemd 约束

每个服务必须有版本化 unit 模板和静态契约检查。应用 unit 至少需要：

- 固定 Java 25 可执行路径；
- `WorkingDirectory`、`User`、`Group` 和 root-only `EnvironmentFile`；
- `After`/`Requires` 依赖和启动超时；
- `Restart=on-failure`、资源上限和文件描述符上限；
- 私有临时目录、禁止不必要的写权限和明确的日志目标。

服务启动顺序为 MySQL/Redis/Meilisearch 健康 → Java 应用 → Nginx。应用启动后必须通过 `/version` 和只读业务探针，Nginx 切换前必须确认后端健康。

### 发布切换

1. 从 Tag 生成或取得已校验 JAR，上传到新的版本目录。
2. 校验文件 SHA、版本、commit SHA 和目标环境配置。
3. 执行数据库备份前置检查。
4. 停止或重启应用服务，保持数据服务运行；只有数据迁移或运行方式切换才进入维护窗口。
5. 切换 `current` 软链接并启动应用。
6. 等待 `/version`、数据库、Redis、Meilisearch 健康检查通过。
7. reload Nginx，执行生产只读回归和日志门禁。
8. 将版本、完整 SHA、时间、节点、验收结论和回滚基线写入发布记录。

### 入口脚本改造矩阵

实现阶段必须逐一处理现有入口，不能只增加一个新脚本而让旧入口继续执行另一套流程：

| 现有入口 | 当前职责 | 宿主机原生目标行为 |
|----------|----------|--------------------|
| `deploy/deploy-staging.sh` | 构建/推送/重建 staging Compose | 安装候选 JAR、校验 SHA、切换 release、`systemctl restart bytedepth-app`，再执行健康检查 |
| `deploy/deploy-production-remote.sh` | 本机生产发布包装器 | 保留唯一本机入口；在远端部署已上传且已校验的 Tag JAR，禁止远端 Maven/Docker 构建 |
| `deploy/deploy-production.sh` | 生产 Compose 构建和重启 | 改为版本目录、`current`、systemd 服务切换和发布后检查 |
| `deploy/bootstrap-ops-deploy.sh` | 安装 Docker/Compose 和部署控制面 | 改为安装/校验 Java、Nginx、MySQL、Redis、Meilisearch、unit、目录、用户和权限；部署 socket 若保留，执行 host-native 发布事务 |
| `deploy/ctl.sh` | Compose 服务控制 | 删除或改名为只操作白名单 systemd unit 的 `hostctl.sh`，禁止保留会误操作 Compose 的正常入口 |
| `deploy/prewarm-production-maven-cache.sh` | 为目标机 Docker 构建预热 Maven 缓存 | 退出正常发布链；目标机不构建，改为构建机/CI 产物校验 |
| `deploy/sync-prod-to-staging.sh` | `docker exec` 执行数据库、Redis、Meili 和图片同步 | 改为受控 host CLI、systemctl 停机/启动、备份校验和专用同步锁 |
| `deploy/provision-*-certificate.sh`、`sync-staging-certificate-to-production.sh` | 容器内 Nginx/证书操作 | 改为宿主机证书目录、`nginx -t` 和 `systemctl reload nginx`，保留 SAN、有效期、私钥匹配和 SSH 校验 |
| `deploy/setup-shared-images-nfs.sh` | 为容器写 Docker mount/drop-in | 改为宿主机 mount unit 或明确的 `RequiresMountsFor`，并让 app/nginx unit 显式依赖挂载 |
| `deploy/run-staging-integration-tests.sh` | 在 Maven 容器/Testcontainers 中运行集成测试 | 默认连接 staging 原生服务的隔离数据库/schema、Redis DB/前缀和 Meili index；若保留一次性容器，必须明确为测试工具而非运行时依赖，并单独标注资源预算 |
| `scripts/verify-production-release.sh` | 检查 Compose 容器和 Docker 运行状态 | 检查 Tag、JAR SHA、systemd active 状态、监听端口、`/version`、健康探针和日志门禁 |

所有 Docker 部署资产必须在本次实现中删除或由宿主机实现替换，不能通过“暂时不调用”来视为完成。迁移和回滚只使用宿主机脚本、systemd、上一份 JAR 和已验证备份；项目中不保留 Compose 回退脚本。

## 数据迁移与停机切换

### 迁移前备份

迁移每个节点前必须暂停写操作，并生成可验证的备份：

- MySQL：一致性 dump、文件级信息和恢复校验；
- Redis：确认 AOF/RDB 完成并保存副本；
- Meilisearch：固定版本 snapshot/dump，并在隔离目录验证可导入；
- 图片：文件数量、字节数和校验摘要；
- Nginx、systemd、应用配置、证书和密钥元数据（不把密钥内容写入证据）。

备份必须记录时间、源版本、文件大小和 SHA-256。不能只因为容器中的数据目录仍然存在，就认为备份可恢复。

### 接管步骤

1. 取得迁移锁，停止部署、同步和定时任务。
2. 记录所有 Docker 容器、镜像、端口、数据目录 owner 和版本。
3. 停止当前 Docker 运行时服务，确认 80/443/3306/6379/7700 已释放；这只是一次性迁移动作，项目实施后不保留 Docker 停止/启动脚本。
4. 安装固定版本宿主机软件和 systemd unit。
5. 检查数据格式、版本兼容性和目录权限；不满足时从备份恢复到原生服务专用目录。
6. 启动 MySQL、Redis、Meilisearch，逐项执行健康检查和只读查询。
7. 启动 Java 应用，执行 Flyway，并确认应用连接的是本节点原生服务。
8. 启动或 reload Nginx，执行完整 staging 集成、E2E 和只读回归。
9. 验收窗口内保留原始数据备份、配置备份和上一份可启动原生 release；不把 Docker 配置或镜像作为回滚条件。

### 定时任务、同步和证书

应用内已有的 Spring 定时任务继续由 `bytedepth-app.service` 承载，不得因为去掉容器再创建一份重复 cron。需要独立生命周期的任务（备份、生产到 staging 同步、证书续期、清理）使用版本化 systemd timer 或受控脚本，并满足：

- 任务有唯一锁，不能与发布、迁移或手工同步并发；
- 任务使用 root-only 配置/凭据文件，输出带环境和时间戳；
- 任务失败必须进入发布/运维告警，不能只写进被轮转掉的容器日志；
- 证书续期完成后只 reload Nginx，reload 前执行配置和证书匹配检查；
- 同步脚本不得把生产密钥、用户数据或完整备份内容写入 Git、evidence 或普通日志。

图片、MySQL、Redis、Meilisearch 的生产到 staging 同步必须继续保持环境隔离；导入 staging 前清理或替换环境标识，不能把生产回调地址、公开入口或凭据带入 staging。

### 回滚

- 应用版本回滚：停止应用，切换 `current` 到上一 JAR，启动并验证；
- Nginx 回滚：恢复上一份配置并 reload；
- 中间件回滚：停止原生服务，按已验证的备份恢复；不得让 Docker 和原生服务同时使用同一数据目录；
- 数据库迁移回滚：只允许在 schema 兼容且已验证的情况下回退代码；不执行未经验证的逆向 Flyway；
- 原生迁移验收失败时，使用已验证的原生上一版本、配置备份和数据备份脚本恢复；如果数据服务尚未完成接管，则停止迁移并保持服务停机，不能重新引入项目 Compose 回退路径。

## 迁移阶段与验收

### 阶段 1：实现和静态门禁

新增 host-only 部署入口、systemd unit 模板、配置校验、备份/恢复脚本、资源和端口检查；同时删除 `Dockerfile`、`.dockerignore`、Compose 文件、Compose 控制入口、目标机 Maven 预热脚本和 Docker 专用测试契约。补充单元测试、脚本契约测试和零 WARNING 检查。所有变更必须先有 `CHANGELOG.md` 的 `Unreleased` 条目。

同时完成知识库和发布流程改造：`deploy/README.md`、`docs/releases/README.md`、`docs/engineering/unified-release-pipeline.md`、`docs/README.md`、`docs/engineering/gotchas.md`、`AGENTS.md` 和相关脚本契约必须在首次 staging 迁移前同步更新。文档链接检查和部署入口检查必须纳入本地质量门禁。

### 阶段 2：staging 停机迁移

在 124 执行备份、停机、原生服务接管和数据校验。执行：

- staging 集成测试；
- 全量 Playwright E2E；
- `/version`、首页、文章、专栏、搜索、项目、图片和环境隔离检查；
- 应用、systemd 和中间件日志 WARNING/ERROR 门禁。

只有 staging 全部通过并由项目所有者验收后，才能进入生产。

### 阶段 3：生产停机迁移

在 175 使用同一版本、同一套已验证脚本和已确认备份执行迁移。生产只接受新的 annotated Tag；迁移后完成版本、SNI、搜索、图片、数据库连接、Redis、Meilisearch 和日志回归。

### 阶段 4：稳定观察

至少完成一个发布周期的运行观察，确认原生服务、发布、回滚、同步、证书和测试槽位稳定。Docker 部署脚本和 Compose 文件已在实现阶段删除，不作为后续清理事项。

### 阶段 5：迁移后收口

稳定观察通过后，在单独变更中更新 ADR 状态为 Accepted，移除迁移专用开关。此阶段仍需保留可验证的数据备份和上一 JAR 回滚能力；不恢复任何 Docker 部署资产。

## 自动化门禁

实现阶段必须增加以下可重复检查：

- systemd unit 的服务用户、端口、依赖、资源限制和固定版本检查；
- host-only 部署不得调用 Docker、Compose 或裸 `mvn`；
- 目标主机不得从分支或裸 commit 运行 JAR；
- 数据迁移前必须存在备份和校验记录；
- 原生服务与 Docker 不得同时绑定相同端口或数据目录；
- staging/生产环境变量、域名、密钥权限和监听地址必须 fail-closed；
- 发布前仍必须通过现有本地质量、staging integration/E2E、CHANGELOG、SHA 和日志门禁。

需要新增或改造的检查至少包括：

- `scripts/test-host-native-runtime.sh`：校验 unit、服务用户、固定版本、端口、监听地址、目录权限和依赖关系；
- `scripts/test-host-native-deployment.sh`：校验部署入口只接受 Tag/ref 规则、JAR SHA、`current` 切换和 systemd 白名单；
- `scripts/test-host-native-data-migration.sh`：校验备份前置、同步锁、数据目录不被双运行时同时使用，以及恢复证据字段；
- `scripts/test-staging-test-slot.sh`：校验 RUN_ID、MySQL 库/账号、Redis logical DB/namespace、Meili index、test-slot unit、清理 trap 和 staging 恢复顺序；
- `scripts/test-test-resource-isolation.sh`：使用 fixture/假 CLI 验证测试配置绝不落到 `bytedepth`、`posts` 或 staging namespace，并拒绝 `FLUSHALL`/通配符删除；
- `scripts/check-staging-checklist.sh`：改为检查 host-native runtime evidence、候选 SHA 稳定性和 staging URL，并继续拒绝 WARNING；
- 资源归属、证书、NFS 挂载、共享主机端口和知识库链接检查；
- release 相关脚本：没有 staging integration/E2E 两份当前 main SHA 的 `result=passed` 记录时 fail-closed。

本机仍只承担断网、无独立进程的单元和静态检查；连接宿主 MySQL、Redis、Meilisearch、Nginx 或实际 systemd 的测试必须在 staging 运行。若集成测试继续使用 Testcontainers，它必须被记录为测试工具，并不能成为部署或运行时前提。

## 需要在实施前锁定的三个默认选择

以下是本设计采用的默认值，若项目所有者不修改，实施按此执行：

1. **测试资源生命周期**：采用串行 test slot；每次 run 为集成测试和 E2E 分别创建独立 MySQL 库/账号、Redis logical DB+运行级 namespace、Meili index，测试期间由 test-slot 应用接管 staging URL，结束后自动清理并恢复正常 staging。不能直接连接 staging 资源，也不能用 `FLUSHALL`/删除固定业务 key 的方式清理。
2. **JAR 传输方式**：本机发布包装器或受控 CI 在外部构建 JAR，生成 SHA 和元数据后上传到目标机的临时目录；远端脚本只接受与 Tag/ref 匹配且 SHA 校验通过的产物。
3. **宿主机软件来源**：MySQL、Redis、Meilisearch、Nginx 和 Java 使用已验证的固定版本安装源/包；安装源、版本、checksum、升级策略和 unit 模板都必须纳入部署脚本和静态检查，禁止使用浮动 `latest`。

## 成功标准

- staging 和生产均能在无 Docker 运行时的情况下启动完整服务栈；
- 现有文章、用户、图片、搜索索引和配置数据可读；
- Java 应用、MySQL、Redis、Meilisearch、Nginx 均有明确 systemd 状态和健康检查；
- 发布、回滚、备份恢复和日志验收均可由脚本重复执行；
- 资源峰值低于当前主机容量，且没有通过跳过测试、删除备份或放宽安全限制获得“成功”。
