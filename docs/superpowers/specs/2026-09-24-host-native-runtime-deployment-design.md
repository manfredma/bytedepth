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

## 发布与运行

### 构建产物

构建在本机或受控 CI 完成，使用项目规定的 Maven Wrapper、Java 25 和完整质量门禁。发布产物至少包括：

- 与 annotated Git Tag 对应的 `app.jar`；
- JAR 的完整 SHA-256；
- 版本、commit SHA、构建时间和目标环境元数据；
- 可审计的上传记录。

目标主机不执行 Maven 构建，不从浮动分支构建，不以裸 commit 作为发布输入。远程部署入口仍只接受经过验证的稳定 Tag。

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
3. 停止应用和中间件 Compose 服务，确认 80/443/3306/6379/7700 已释放。
4. 安装固定版本宿主机软件和 systemd unit。
5. 检查数据格式、版本兼容性和目录权限；不满足时从备份恢复到原生服务专用目录。
6. 启动 MySQL、Redis、Meilisearch，逐项执行健康检查和只读查询。
7. 启动 Java 应用，执行 Flyway，并确认应用连接的是本节点原生服务。
8. 启动或 reload Nginx，执行完整 staging 集成、E2E 和只读回归。
9. 验收窗口内保留 Docker 配置、镜像和回滚备份，不立即卸载 Docker。

### 回滚

- 应用版本回滚：停止应用，切换 `current` 到上一 JAR，启动并验证；
- Nginx 回滚：恢复上一份配置并 reload；
- 中间件回滚：停止原生服务，按已验证的备份恢复；不得让 Docker 和原生服务同时使用同一数据目录；
- 数据库迁移回滚：只允许在 schema 兼容且已验证的情况下回退代码；不执行未经验证的逆向 Flyway；
- 原生迁移验收失败时，优先恢复 Docker Compose 基线，再分析问题。

## 迁移阶段与验收

### 阶段 1：实现和静态门禁

新增 host-only 部署入口、systemd unit 模板、配置校验、备份/恢复脚本、资源和端口检查。补充单元测试、脚本契约测试和零 WARNING 检查。所有变更必须先有 `CHANGELOG.md` 的 `Unreleased` 条目。

### 阶段 2：staging 停机迁移

在 124 执行备份、停机、原生服务接管和数据校验。执行：

- staging 集成测试；
- 全量 Playwright E2E；
- `/version`、首页、文章、专栏、搜索、项目、图片和环境隔离检查；
- 应用、systemd 和中间件日志 WARNING/ERROR 门禁。

只有 staging 全部通过并由项目所有者验收后，才能进入生产。

### 阶段 3：生产停机迁移

在 175 使用同一版本、同一套已验证脚本和已确认备份执行迁移。生产只接受新的 annotated Tag；迁移后完成版本、SNI、搜索、图片、数据库连接、Redis、Meilisearch 和日志回归。

### 阶段 4：稳定观察与清理

至少完成一个发布周期的运行观察后，才评估删除 Docker 镜像、Compose 配置和旧运行时。清理必须是独立、可恢复的变更，不能与首次原生迁移绑定。

## 自动化门禁

实现阶段必须增加以下可重复检查：

- systemd unit 的服务用户、端口、依赖、资源限制和固定版本检查；
- host-only 部署不得调用 Docker、Compose 或裸 `mvn`；
- 目标主机不得从分支或裸 commit 运行 JAR；
- 数据迁移前必须存在备份和校验记录；
- 原生服务与 Docker 不得同时绑定相同端口或数据目录；
- staging/生产环境变量、域名、密钥权限和监听地址必须 fail-closed；
- 发布前仍必须通过现有本地质量、staging integration/E2E、CHANGELOG、SHA 和日志门禁。

## 成功标准

- staging 和生产均能在无 Docker 运行时的情况下启动完整服务栈；
- 现有文章、用户、图片、搜索索引和配置数据可读；
- Java 应用、MySQL、Redis、Meilisearch、Nginx 均有明确 systemd 状态和健康检查；
- 发布、回滚、备份恢复和日志验收均可由脚本重复执行；
- 资源峰值低于当前主机容量，且没有通过跳过测试、删除备份或放宽安全限制获得“成功”。
