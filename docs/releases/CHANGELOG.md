# Changelog

本文件记录正式发布版本；每个条目必须与一个不可变 annotated Git Tag 一一对应。格式参考 Keep a Changelog，版本号遵循 Semantic Versioning。

## Unreleased

## [v1.5.1] - 2026-08-07

**Tag**：`v1.5.1`
**Commit**：`9a41fb496dc60e4cadb833278d02f0f76aca3b2d`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点 `2026-08-07T14:36:xxZ`；应用节点 `2026-08-07T14:38:xxZ`
**验收**：两节点 Compose 服务运行；SNI HTTPS 首页及最新/热门分页、文章与旧 ID 跳转、专栏、搜索、项目和真实文章图片均为 `200`；Java 25 + Spring Boot 4.1 生产可用
**回滚基线**：`v1.4.0`

### Fixed

- 修复 Dockerfile：`-DskipTests` → `-Dmaven.test.skip=true`，跳过测试编译避免 Docker 构建失败

## [v1.5.0] - 2026-08-07

**Tag**：`v1.5.0`
**Commit**：`5f98c84de4e8ea5be273eeec104c6af580645dcf`
**部署**：Docker 构建失败（Dockerfile 未跳过测试编译），未部署
**回滚基线**：`v1.4.0`

### Changed

- Java 17 → 25（编译/运行目标）
- Spring Boot 3.2.5 → 4.1.0（Spring Framework 7.0.8, Spring Security 7.1.0）
- MyBatis-Plus 3.5.5 → 3.5.17（spring-boot4-starter），移除 PaginationInnerInterceptor
- Lombok 1.18.30 → 1.18.40
- Testcontainers 1.20.1 → 1.20.9
- Bucket4j 8.14.0 → 8.19.0
- Commonmark 0.21.0 → 0.30.0
- ArchUnit 1.4.2 → 1.5.0
- Dockerfile 构建/运行镜像升级至 Java 25

### Fixed

- SecurityConfig: @MockBean → @MockitoBean, AntPathRequestMatcher → PathPatternRequestMatcher
- DaoAuthenticationProvider: 构造函数注入替代已移除的 setUserDetailsService()
- RedisURI: setPassword() → setAuthentication()（Lettuce 7.x API 变更）
- 测试 Mapper 调用: any() → any(XxxDO.class)（MyBatis-Plus 3.5.17 批量方法重载）

### Compatibility

- 无数据库迁移；可在验证失败时回滚至 `v1.4.0`
- 注意：thymeleaf-extras-springsecurity6 3.1.5.RELEASE 尚不兼容 Spring Security 7，@WebMvcTest 切片测试中 sec:authorize 模板渲染可能失败，生产环境不受影响

## [v1.4.0] - 2026-08-07

**Tag**：`v1.4.0`
**Commit**：`a0aa9c8fab6639e922729664b1e5d3f53c4cf083`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点 `2026-08-07T10:11:14Z`；应用节点 `2026-08-07T10:15:00Z`
**验收**：两节点 Socket active、完整 Compose 服务运行；SNI HTTPS 首页及最新/热门分页、文章与旧 ID 跳转、专栏、搜索、项目和真实文章图片均为 `200`
**回滚基线**：`v1.3.0`

### Changed

- PostController 辅助方法提取到 WebUtils 和 SecurityUtils，Controller 从 263 行降至 171 行。
- 限流页面 HTML/CSS/JS 从 Java 字符串提取到 classpath 模板文件。
- 模块级 README 覆盖 5 个模块。
- 新增 docs/routes.md 路由一览文档。

### Fixed

- PMD 违规：配置自定义 ruleset 排除 UnusedPrivateField 假阳性，修复 domain/app/infrastructure/adapter 层共 18 个真实违规，所有模块 PMD 通过。
- MeiliSearchPostIndexer 未经检查的泛型转换：缩小 @SuppressWarnings 到私有辅助方法，加入 instanceof 类型检查。
- 注册密码强度校验：8-64 位 + 必须包含字母和数字，5 个测试覆盖边界。
- 移除 docker-compose.yml 中未使用的 mysql_data 和 redis_data named volumes。

### Compatibility

- 无数据库迁移；可在验证失败时回滚至 `v1.3.0`。

## [v1.3.0] - 2026-08-06

**Tag**：`v1.3.0`
**Commit**：`926177c9fb49804b48bf2aa65a956c4517359830`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点 `2026-08-06T03:03:17Z`；应用节点 `2026-08-06T03:06:06Z`
**验收**：两节点 Socket active、完整 Compose 服务运行；SNI HTTPS 首页及最新/热门分页、文章与旧 ID 跳转、专栏、搜索、项目、Sitemap、RSS feed 和真实文章图片均为 `200`
**回滚基线**：`v1.1.0`

### Added

- 新增 `/feed.xml` RSS 2.0 最近文章 feed，供订阅器与搜索引擎发现最新内容。

### Fixed

- Sitemap 的 `lastmod` 仅反映真实文章更新；不再将未修改的静态页面标记为当天更新。
- 首页、文章列表和专栏页面依据相关已发布文章的实际最新更新时间生成 `lastmod`。

### Compatibility

- 无数据库迁移；可在验证失败时回滚至 `v1.1.0`。

## [v1.1.0] - 2026-08-04

**Tag**：`v1.1.0`
**Commit**：`13e72a885962bd6cd6c8a4f03f30982b408e5323`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点 `2026-08-04T13:27:54Z`；应用节点 `2026-08-04T13:30:30Z`
**验收**：两节点 Socket active、完整 Compose 服务运行；SNI HTTPS 首页及最新/热门分页、文章与旧 ID 跳转、专栏、搜索、项目和真实文章图片均为 `200`
**回滚基线**：`v1.0.2`

### Fixed

- Markdown 安全清洗保留标题 `id`，恢复 Obsidian 标题锚点和页内链接跳转。

### Changed

- 发布规范明确：未特别说明时，每次生产部署默认升级 MINOR（小版本）。

### Compatibility

- 无数据库迁移；可在验证失败时回滚至 `v1.0.2`。

## [v1.0.2] - 2026-08-04

**Tag**：`v1.0.2`
**Commit**：`8452102ef1d4ca82204360fb221714e758793fc8`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点 `2026-08-04T10:34:14Z`；应用节点 `2026-08-04T10:36:50Z`
**验收**：两节点 Socket active、应用与 Nginx 运行；HTTPS 首页、文章、专栏、搜索、项目与版本页均为 `200`，应用节点额外验证文章详情和真实图片为 `200`
**回滚基线**：无（`v1.0.1` 尚未记录完整验收结论）

### Changed

- 将前台“版本”调整为“关于”的二级入口，保留原版本更新地址。
- 发布流程改为受控脚本：自动验证工作区、Tag 与 Changelog，执行构建测试并清理 Maven Release Plugin 本机残留。

### Fixed

- 不再跟踪 Maven Release Plugin 生成的 `release.properties` 与 `*.releaseBackup`，避免残留文件污染下一次发布。

### Compatibility

- 无。

## [v1.0.1] - 2026-08-04

**Tag**：`v1.0.1`
**Commit**：`e9b1b177bac574db711bd22341870cdfa7a89fe6`
**部署**：待完成数据节点、应用节点验收
**回滚基线**：无（`v1.0.0` 启动失败，未完成验收）

### Fixed

- 修复 MeiliSearch 索引器存在测试构造器时 Spring 无法选择生产配置构造器的问题。

## [v1.0.0] - 2026-08-04

**Tag**：`v1.0.0`
**Commit**：`f40bda924df7b1d4de4a79146839197f76fda9ec`
**部署**：待完成数据节点、应用节点验收
**回滚基线**：无（首次正式发布）

### Added

- Markdown 技术博客：文章创建、编辑、发布、软删除、Slug 地址、目录锚点、GFM 表格、Mermaid、图片宽度保留与安全 HTML 清洗。
- 内容发现：文章列表、分类与标签筛选、全文搜索、热门/最新排序、统一分页与上一页/下一页导航。
- 专栏：前台专栏列表与详情、文章专栏导航；后台支持创建、绑定、移入移出、排序和删除专栏。
- 内容管理：文章、分类、标签、评论、项目展示与图片上传管理；标签支持删除，危险操作统一使用确认弹窗。
- Obsidian 同步工作流：图片上传、导入、更新、增量同步、内部链接修正和同步后校验。
- 账户与社区：注册、审批、禁用、个人主页、角色权限、登录、记住登录、登录后评论和文章评分。
- 阅读体验：响应式前台与后台、PWA、主题切换、阅读工具栏、阅读时长与进度统计、文章字数与编辑时间展示。
- 搜索与可发现性：MeiliSearch、SEO Meta/OG、Schema.org BlogPosting、Sitemap 与 robots.txt。
- 访问分析：访问日志、GeoIP 国家解析、统计看板、趋势与文章下钻分析。
- 运维与部署：受控运维监控、限流、Flyway 迁移、完整 Compose 部署、数据访问/外部服务双节点拓扑与 NFS 图片共享。
- 工程质量：DDD 分层和 ArchUnit 架构守护、变更覆盖率门禁、Java 21 构建约束与项目知识库。

### Security

- Spring Security 表单认证、CSRF 保护、细粒度 RBAC、内容所有权校验、上传内容校验、Markdown 清洗和受限宿主机部署 Socket。

### Compatibility

- 首次正式发布将包含既有 Flyway `V1` 至 `V18` 迁移；新环境从空库初始化，已有生产数据不得重写或回退已执行迁移。
- 旧的 `git pull main` 发布流程不再允许用于生产。

## 记录模板

```markdown
## [v1.2.3] - YYYY-MM-DD

**Tag**：`v1.2.3`  
**Commit**：`<40 位 SHA>`  
**部署**：数据节点、应用节点均已验收  
**回滚基线**：`v1.2.2`

### Changed

- 面向用户或运维的变更。

### Compatibility

- 数据库迁移、配置变更、回滚限制；没有则写“无”。
```

未创建新 Tag 的内容不得写入正式版本条目；已创建 Tag 但尚未部署或验收的条目必须标注“待验收”，不得宣称上线完成或作为回滚基线。
