# 运维监控页（MVP）实施计划

## 目标

在现有 Spring Boot / Thymeleaf 管理后台内新增 `/admin/ops`，安全地展示应用、MySQL、Redis、MeiliSearch 的只读状态和有限的业务数据查询。不得新增独立服务或前端工程。

## 全局约束

- 运行时 Java 仍为 Java 17；所有 Maven 命令必须以 `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn ...` 执行。
- 保持 DDD 依赖方向：adapter 调用 app；infrastructure 实现 app 定义的端口；模板放 start 模块。
- 页面与 JSON API 只能读取状态或数据。严禁实现任意 SQL、任意 Redis 命令、`KEYS *`、Docker socket/容器控制，或返回 Redis 的值。
- MySQL 表明细只允许 `post`、`comment`、`user`，且只能使用白名单字段、最多 50 行、按 `id DESC`；非法表名必须返回 400。
- Redis 指标仅可用 `INFO` 和 `SCAN` 统计固定业务前缀；不得读取 key value。固定前缀为 `pv:post:`、`bytedepth:session:`。
- Redis、MySQL、MeiliSearch 任一调用失败时，概要页仍返回 200，并将该服务状态标为 `DOWN`、携带对管理员安全的简短错误信息，不得输出密码、连接串或堆栈。
- 创建新权限 `ops:monitor:view`（模块 `admin`、说明“查看运维监控”），授予 ADMIN 角色。页面和 API 都必须同时受现有 `/admin/**` 与该方法级权限保护。
- 后台侧栏增加“系统运维”入口；所有新增页面样式必须位于独立 `ops-monitor.css`，选择器以 `.ops-` 开头，不能写裸元素、`*`、`:root`、`body` 或影响其它组件的选择器。
- 需要覆盖权限、成功数据、单服务失败、表白名单/非法表的 MockMvc 或单元测试；改动结束后先运行 `mvn clean install -DskipTests -Dsort.skip=true`，再运行 `mvn test`。

## Task 1: 监控查询模型、端口及基础设施实现

在 `bytedepth-app` 定义运维概览和表明细的 DTO、查询用例和端口；在 `bytedepth-infrastructure` 实现这些端口。概览必须包括应用运行时长（从 JVM 启动时间计算）、MySQL 连通性和数据库名、Redis 连通性/used_memory_human/connected_clients/keyspace_hits/keyspace_misses/两个前缀的 `SCAN` 计数、MeiliSearch `/health` 与 `/stats`（可使用现有搜索配置和 HTTP 客户端）。任何单一依赖失败必须被隔离为服务状态而非让总查询失败。

为三个受控表定义白名单字段：

- `post`: `id`, `title`, `status`, `author_id`, `created_at`, `updated_at`
- `comment`: `id`, `post_id`, `author_id`, `content`, `created_at`
- `user`: `id`, `username`, `email`, `status`, `created_at`, `updated_at`

表明细查询必须使用固定 SQL（表名和列名均从白名单选择）和固定 `LIMIT 50`，不能拼接用户输入。新增单元测试覆盖 Redis INFO 解析、SCAN 计数、失败隔离和表名拒绝。提交本任务的代码与测试。

## Task 2: 运维页面、接口、权限迁移与前端隔离

新增 `AdminOpsController`，提供：

- `GET /admin/ops` 返回 `admin/ops/dashboard`；
- `GET /admin/ops/api/overview` 返回运维概览 JSON；
- `GET /admin/ops/api/tables/{tableName}` 返回受控表明细 JSON。

Controller 必须使用 `@PreAuthorize("hasAuthority('ops:monitor:view')")`，非法表名转成 400；依赖失败的服务不能使概览接口失败。新增 Flyway V16 迁移写入权限并授予 ADMIN。

新增 Thymeleaf 页面，首屏渲染四个服务卡片和 Redis/MySQL 摘要，页面加载后请求 overview API；提供三个表按钮查询最新 50 条安全字段并在页面表格中展示。仅有手动刷新，不做自动轮询。将样式放在 `static/css/ops-monitor.css`，并在页面引入；所有新增 CSS 选择器只能以 `.ops-` 开头。将“系统运维”加入后台侧栏并支持 `active == 'ops'`。

新增或扩展 `bytedepth-start` 的 MockMvc 测试，验证：无权限 403、有权限页面与 JSON 200、概览服务失败仍 200、合法表返回 200、非法表返回 400。提交本任务的代码与测试。

## Task 3: 运维文档

补充 `docs/ops.md` 的“系统运维页”段落，说明入口、最低权限、只读边界和容器/主机监控不属于该页面。文档不得写入任何部署密码、连接 URL 或 API key。提交本任务的文档改动。

## Task 4: 集成验收

在任务 2 和任务 3 合并后，核对页面与 API 的响应数据不包含数据库密码、Redis 值、Meili API key、连接 URL 或异常堆栈。运行项目规定的 Maven 缓存刷新和完整测试。仅在验收发现问题时提交最小修正。
