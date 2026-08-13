# Changelog

本文件记录正式发布版本；每个条目必须与一个不可变 annotated Git Tag 一一对应。格式参考 Keep a Changelog，版本号遵循 Semantic Versioning。

## Unreleased

## [v2.1.10] - 2026-08-13

**Tag**：`v2.1.10`
**Commit**：（发布后由受控发布工具回填）
**部署**：（待验收）
**回滚基线**：`v2.1.9`

### Added

- 后台「文章管理」列表中的文章标题改为超链接，点击可在新标签页打开已发布文章（`/posts/{slug}`）；`ListAllPostsQryExe` 补齐 `PostDTO.slug` 字段填充。

### Compatibility

- 无数据库迁移或 API 变更；可在验收失败时回滚至 `v2.1.9`。

## [v2.1.9] - 2026-08-13

**Tag**：`v2.1.9`
**Commit**：`e70d0c6bad5400a2b5aed725bd942c53a8fc281a`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点与应用节点均于 `2026-08-13T16:04Z` 完成
**验收**：两节点 SNI HTTPS `200`；查询回归全部通过
**回滚基线**：`v2.1.8`

### Added

- 文章阅读页批注侧栏默认打开：首次访问时侧栏自动展开，便于查看和创建划线评论；已关闭过的用户保持上次偏好（localStorage 记忆）。

### Fixed

- 后台多列表页查询过滤栏布局修复：筛选字段（如「文章 ID」「用户 ID」）现在水平排列（一行），而非垂直堆叠（一列）。

### Compatibility

- 无数据库迁移或 API 变更；`annotation.js` 的 `readSidebarState` 默认值从 `false` 改为 `true`，`filter-bar.html` 的 `<form>` 增加 flex 布局样式。可在验收失败时回滚至 `v2.1.8`。

## [v2.1.8] - 2026-08-13

**Tag**：`v2.1.8`
**Commit**：`414f59773d20388336898c2e81aff93d2ef1ce01`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点与应用节点均于 `2026-08-13T15:53Z` 完成
**验收**：两节点 SNI HTTPS `200`；查询回归全部通过
**回滚基线**：`v2.1.6`

### Fixed

- 修复 v2.1.7 部署失败：`AnnotationRecalculator` 未标注 `@Component`，导致 `UpdatePostCmdExe` 注入失败，应用启动报 `Parameter 3 of constructor...required a bean of type 'AnnotationRecalculator' that could not be found`。加上 `@Component` 后 Spring 自动扫描并注册该 Bean。

### Compatibility

- 无数据库迁移或 API 变更；可在验收失败时回滚至 `v2.1.6`。

## [v2.1.7] - 2026-08-13

**Tag**：`v2.1.7`
**Commit**：`e888b9c541eb622a4f0b0d3d1a9d9d1e36794082`（未部署）
**部署**：❌ 部署失败——`AnnotationRecalculator` 未标注 `@Component`，Spring 无法注入，应用启动后立即退出（502）；修复见 v2.1.8。
**回滚基线**：`v2.1.6`

### Fixed

- 文章内容变更后批注/划线偏移失效问题：`UpdatePostCmdExe` 在内容编辑时，基于字符级 Diff 信息重算所有批注的 `startOffset`/`endOffset`。批注范围内的文本在新内容中被完全删除时，该批注标记为逻辑删除（`deleted = true`），前台查询不再展示。新增 Flyway V22 迁移 `post_annotation.deleted` 列。引入 `java-diff-utils` 库。

### Compatibility

- 新增 Flyway V22 迁移，仅向前执行；已部署迁移后若需回滚代码，应使用兼容 V22 版本。`annotationRepository.findByPostId` 返回所有批注（含已删除的），`findVisibleByPostId` 加 `AND deleted = 0` 过滤。可在验收失败时回滚至 `v2.1.6`。

## [v2.1.6] - 2026-08-13

**Tag**：`v2.1.6`
**Commit**：`dd406bc3e6cc576bc9d716368ca9455175e900d6`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点与应用节点均于 `2026-08-13T08:20Z` 完成
**验收**：两节点 SNI HTTPS `200`；查询回归全部通过；两节点旧 `docs/routes.md` / `docs/ops.md` 已清理，新位置 `docs/architecture/routes.md` / `docs/security/ops.md` 就位
**回滚基线**：`v2.1.5`

### Changed

- docs/ 目录重新归置：`docs/routes.md` 移入 `docs/architecture/routes.md`，`docs/ops.md` 移入 `docs/security/ops.md`。docs/ 根目录现在只保留入口 `README.md`，全部文档归入分类子目录。同步更新 README、AGENTS、ubiquitous-language、annotation-write-permissions 与 ops 内部链接。

### Compatibility

- 纯文档移动，无功能变化；文档相对链接已同步更新。可在验收失败时回滚至 `v2.1.5`。

## [v2.1.5] - 2026-08-13

**Tag**：`v2.1.5`
**Commit**：`15e2c1a19a4be7cb557c2152dd2992d5cb61637e`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点与应用节点均于 `2026-08-13T08:07Z` 完成
**验收**：两节点 SNI HTTPS `200`；查询回归全部通过；根目录旧 `pmd-ruleset.xml` / `.githooks/` 已清理，`config/pmd/` / `config/git-hooks/` 就位
**回滚基线**：`v2.1.4`

### Changed

- 新增 `config/` 目录集中存放构建工具配置：`pmd-ruleset.xml` 移入 `config/pmd/pmd-ruleset.xml`，`.githooks/pre-commit` 移入 `config/git-hooks/pre-commit`。`pom.xml` 中 ruleset 路径与 `scripts/configure-git-hooks.sh` 的 hooksPath 同步更新。根目录散落文件减少 2 个。

### Compatibility

- 纯配置文件重排，`pom.xml` 引用路径已同步更新，`configure-git-hooks.sh` 的 hooksPath 已同步更新，不影响功能。可在验收失败时回滚至 `v2.1.4`。

## [v2.1.4] - 2026-08-13

**Tag**：`v2.1.4`
**Commit**：`a4b8923209dbcfad60f878b8bda9c50344d3f85d`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点与应用节点均于 `2026-08-13T07:51Z` 完成
**验收**：两节点 SNI HTTPS 首页 `200`；查询回归通过；两节点根目录旧 `.env.example` 已清理，`deploy/.env.example` 就位
**回滚基线**：`v2.1.3`

### Changed

- 环境变量模板统一到 `deploy/`：根目录 `.env.example` 移入 `deploy/.env.example`，与现有的 `deploy/.env.external.example` 并列。`deploy/README.md` 中 `cp .env.example .env` 同步更新为 `cp deploy/.env.example .env`。

### Compatibility

- 纯部署模板文件移动，无服务定义或功能变化；`cp` 命令路径需改为 `deploy/.env.example`。可在验收失败时回滚至 `v2.1.3`。

## [v2.1.3] - 2026-08-13

**Tag**：`v2.1.3`
**Commit**：`67339ba92291e588de5990e4071fa6c724798cf8`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点与应用节点均于 `2026-08-13T07:39Z` 完成
**验收**：两节点 SNI HTTPS 首页返回 `200`；首页最新/热门及翻页、文章列表与详情、旧 ID 跳转、专栏、搜索、项目回归全部通过，真实文章图片（svg/jpg）两节点均 `200`；数据节点根目录旧 `docker-compose.yml`/`deploy.sh`/`nginx/` 残留已清理，应用节点旧配置亦已清理
**回滚基线**：`v2.1.0`

### Fixed

- 修复 v2.1.2 引入的部署故障：`deploy/ctl.sh` 为各部署模式显式指定 Compose 项目名（`-p`）。此前 compose 文件移入 `deploy/` 后，Docker Compose 默认以首个 `-f` 文件所在目录名作为项目名，单机/数据节点项目名从历史 `bytedepth` 变为 `deploy`，compose 将其视为全新项目重建容器，与既有 `bytedepth-*` 容器端口冲突（`Bind for 10.0.4.15:8081 failed`）。现显式固定：单机/数据节点 `-p bytedepth`、应用节点 `-p deploy`，保证 compose 识别并升级既有容器。

### Compatibility

- 无服务定义语义变化、无数据库迁移或 API 变更；`deploy/ctl.sh` 现在对三种部署模式统一加载根目录 `.env` 并固定历史项目名。可在验收失败时回滚至 `v2.1.0`。

## [v2.1.2] - 2026-08-13

**Tag**：`v2.1.2`
**Commit**：`ce02a08`（未部署）
**部署**：❌ 部署失败——compose 项目名从历史 `bytedepth` 变为 `deploy`，新容器与既有 `bytedepth-*` 容器端口冲突（`Bind for 10.0.4.15:8081 failed`）；已清理误建容器，修复见 v2.1.3。
**回滚基线**：`v2.1.0`

### Fixed

- 修复 v2.1.1 引入的部署故障：`deploy/ctl.sh` 显式指定 `--env-file .env`。此前 compose 文件移入 `deploy/` 后，Docker Compose 的项目目录随之变为 `deploy/`，不再默认读取仓库根目录的 `.env`，导致 `DB_PASSWORD`/`REDIS_PASSWORD` 等变量缺失、`docker compose up` 失败。

### Compatibility

- 无服务定义语义变化、无数据库迁移或 API 变更；`deploy/ctl.sh` 现在对三种部署模式统一加载根目录 `.env`。可在验收失败时回滚至 `v2.1.0`。

## [v2.1.1] - 2026-08-13

**Tag**：`v2.1.1`
**Commit**：`3dad4f1`（未部署）
**部署**：❌ 部署失败——`deploy/ctl.sh` 未显式加载根目录 `.env`，数据节点 `docker compose up` 阶段报 `DB_PASSWORD is missing`；按不可变 Tag 规则弃用该版本，修复见 v2.1.2。
**回滚基线**：`v2.1.0`

### Changed

- Docker 部署文件收敛到 `deploy/` 单一目录：`docker-compose.yml` 移入 `deploy/docker-compose.single-host.yml`，`nginx/nginx.conf` 移入 `deploy/nginx/nginx.conf`；删除废弃的根目录 `deploy.sh`（`git pull main` 旧发布方式）。
- 新增 `deploy/ctl.sh` 统一入口，按 `BYTEDEPTH_DEPLOY_MODE` 自动选择正确的 Compose 文件；`bootstrap-ops-deploy.sh` 统一经 `ctl.sh` 执行。此前应用节点裸跑 `docker compose` 会误读单机版编排并误报 `MEILI_MASTER_KEY` 缺失，收敛后从结构上消除该误读。

### Fixed

- 修复 `AdminAnalyticsControllerTest.toDateFormat_within2Days_returnsHourFormat` 时间敏感的 flaky 测试：原使用 `LocalDateTime.now()`，在 0:00–10:00 运行时 `now-10h` 跨天导致断言失败，改为固定时间戳。

### Compatibility

- 纯部署文件重排，无服务定义语义变化，无数据库迁移或 API 变更；`deploy-release.sh` → `bootstrap-ops-deploy.sh` 部署链路不变，服务器 checkout 新 tag 后自动使用新目录结构。可在验收失败时回滚至 `v2.1.0`。

## [v2.1.0] - 2026-08-12

**Tag**：`v2.1.0`
**Commit**：`4b8a94816054e029515eb38eb28f9ea4086bf3a2`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点与应用节点均于 `2026-08-13T06:42Z` 完成
**验收**：两节点 SNI HTTPS 首页返回 `200`；首页最新/热门及翻页、文章列表与详情、旧 ID 跳转、专栏、搜索、项目和真实文章图片（svg/jpg）回归全部通过；两节点应用日志无 Flyway/MySQL/Redis/MeiliSearch 连接错误
**回滚基线**：`v2.0.0`

### Added

- 首页标语轮播从 20 条扩展至 120 条，新增 100 条覆盖 SOLID 设计原则、架构原则、分布式系统谬误、安全原则、API 设计、测试哲学、数据库设计、运维工程等主题。

### Compatibility

- 纯前端静态变更，无数据库迁移或 API 变更；可在验收失败时回滚至 `v2.0.0`。

## [v2.0.0] - 2026-08-12

**Tag**：`v2.0.0`
**Commit**：（发布后由受控发布工具回填）
**部署**：（待验收）
**回滚基线**：`v1.10.0`

### Changed

- 重构文章阅读批注体验：桌面端侧栏与正文并排浏览，关闭时保留原有紧凑阅读布局。
- 评注卡片突出评论正文；公开范围、编辑与删除收敛为弱化的单行辅助操作，编辑改为卡片内原地完成。
- 评注以与划线颜色一致的引用竖线标识，去除干扰性圆点；正文与侧栏评注位置同步，进入或离开侧栏内容区时平滑出现、离开。
- 评注框、角标、划线与侧栏开关交互增加浏览器端回归覆盖，避免静态资源或滚动定位回退。
- 点击正文评注触发点时，仅当再次点击同一批注才回收侧栏；切换到不同批注时保持打开并高亮聚焦当前卡片（彩色左条 + 轻染背景）。
- 宽屏批注侧栏打开时，正文左侧预留避让专栏收起按钮的间距，避免按钮遮挡正文。
- 划线与评论写操作（删除、创建划线）失败时向用户显式反馈，不再静默。

### Fixed

- 自托管 Google Fonts（woff2 随站点静态资源分发），消除 fonts.gstatic.com 文件轮换导致的 404 与外部依赖。
- 修复中屏（769–1359px）布局下评注卡片被离场逻辑整体隐藏的问题，展开为常规可读列表。
- 升级 Service Worker 缓存版本以清除仍引用外部字体的旧 HTML 缓存。

### Compatibility

- 无新增数据库迁移；现有划线与评论数据保持兼容。
- 本次为阅读批注交互与视觉的重大迭代，客户端应加载带内容指纹的最新静态资源。

## [v1.10.0] - 2026-08-11

**Tag**：`v1.10.0`
**Commit**：（发布后由受控发布工具回填）
**部署**：（待验收）
**回滚基线**：`v1.9.0`

### Added

- 文章阅读页新增可开关的划线评论侧栏；侧栏打开时可集中查看、创建、编辑和删除自己的划线评论，关闭时保留紧凑划线交互。
- 支持匿名读者划线：浏览器以 HttpOnly、Secure、SameSite Cookie 维护归属，数据库只保存其 SHA-256 摘要。
- 划线默认仅自己可见；带评论的批注默认公开，并可改为仅自己可见。

### Fixed

- 消除 Java 25 下 Maven、PMD、Mockito、Netty 与 Spring Security 的构建、测试告警；Redis 运维与限流实现迁移至非弃用 API。
- 修复限流规则未配置时被误判为 Redis 故障的问题，未配置规则现在安全跳过。

### Compatibility

- 新增 Flyway V21，为 `post_annotation` 增加匿名归属和可见性字段，并允许纯划线不填写评论；迁移仅向前执行。
- 已部署迁移后不可直接回滚数据库结构；若需回滚应用代码，应使用能兼容 V21 的版本。

## [v1.9.0] - 2026-08-11

**Tag**：`v1.9.0`
**Commit**：（发布后由受控发布工具回填）
**部署**：（待验收）
**回滚基线**：`v1.8.0`

### Added

- 文章阅读页新增可开关的划线评论侧栏；侧栏打开时可集中查看、创建、编辑和删除自己的划线评论，关闭时保留紧凑划线交互。
- 支持匿名读者划线：浏览器以 HttpOnly、Secure、SameSite Cookie 维护归属，数据库只保存其 SHA-256 摘要。
- 划线默认仅自己可见；带评论的批注默认公开，并可改为仅自己可见。

### Changed

- 批注查询只返回公开记录和当前读者自己的私有记录，接口不再暴露批注作者用户 ID。

### Compatibility

- 新增 Flyway V21，为 `post_annotation` 增加匿名归属和可见性字段，并允许纯划线不填写评论；迁移仅向前执行。
- 已部署迁移后不可直接回滚数据库结构；若需回滚应用代码，应使用能兼容 V21 的版本。

## [v1.8.0] - 2026-08-11

**Tag**：`v1.8.0`
**Commit**：（发布后由受控发布工具回填）
**部署**：（待验收）
**回滚基线**：`v1.7.4`

### Added

- 后台文章、评论、分类、标签、专栏、用户与访问日志列表统一接入可复用的服务端查询过滤栏；分页查询可保留筛选条件。
- 分类树筛选保留父级路径；用户管理扩展为全量用户并支持状态筛选；专栏与文章筛选继续遵守作者归属权限。
- 新增前端 Jest 测试基础设施与后台过滤组件配置 DTO。

### Fixed

- 精确识别 `172.16.0.0/12` 私有网段，避免将 `172.32.0.0/12` 之外的公网地址误判为内网。
- 限流页面模板缓存使用 `volatile`，保证并发读取的可见性。
- PMD 升级至支持 Java 25 的版本，并迁移项目规则集；Maven 使用仓库级无凭据镜像配置。

### Compatibility

- 无数据库迁移或破坏性 API 变更；可在验收失败时回滚至 `v1.7.4`。

## [v1.7.4] - 2026-08-11

**Tag**：`v1.7.4`
**Commit**：（发布后由受控发布工具回填）
**部署**：（待验收）
**回滚基线**：`v1.7.3`

### Fixed

- remember-me 持久登录 Cookie 显式设置 `SameSite=Lax`，限制跨站请求自动携带该长期凭证。

### Compatibility

- 无数据库迁移或 API 变更；既有 remember-me Cookie 在后续成功登录或自动登录轮换时获得该属性，可在验收失败时回滚至 `v1.7.3`。

## [v1.7.3] - 2026-08-11

**Tag**：`v1.7.3`
**Commit**：（发布后由受控发布工具回填）
**部署**：（待验收）
**回滚基线**：`v1.7.2`

### Fixed

- 修复搜索结果页在 Thymeleaf/Spring EL 安全限制下无法渲染的问题；搜索请求不再返回 `200` 后中断连接。

### Compatibility

- 无数据库迁移或 API 变更；可在验收失败时回滚至 `v1.7.2`。

## [v1.7.2] - 2026-08-11

**Tag**：`v1.7.2`
**Commit**：（发布后由受控发布工具回填）
**部署**：（待验收）
**回滚基线**：`v1.7.1`

### Fixed

- 批注弹框与提示框改为相对视口定位，修复页面滚动后划线选区与弹框坐标系不一致、弹框可能出现在视口外的问题。
- 批注弹框增加视口上下边界限制，并扩大输入框、颜色选择与提示框尺寸，提升批注编辑可用性。

### Compatibility

- 无数据库迁移或 API 变更；可在验收失败时回滚至 `v1.7.1`。

## [v1.7.1] - 2026-08-11

**Tag**：`v1.7.1`
**Commit**：`8cda6d20e94d0cf5192ae5850d78fed6ed8ad74c`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点与应用节点均于 `2026-08-11T03:1xZ` 完成
**验收**：两节点 SNI HTTPS 首页与文章详情页均返回 `200`；Flyway 补跑 V19/V20 迁移成功（now at version v20，`page_view_log`/`post_annotation` 表已创建）；批注 API 返回 `200` 空数组；`page_view_log` 已有真实访问数据；首页/文章/专栏/搜索/项目/图片查询回归全部通过
**回滚基线**：`v1.7.0`

### Fixed

- **关键修复**：Spring Boot 4 将 Flyway 自动配置移入 `spring-boot-flyway` 模块，此前缺失导致 V19（page_view_log）/V20（post_annotation）迁移从未执行。添加依赖后 Flyway 迁移恢复运行（已验证 20 个迁移全部执行）
- `PostRepositoryIT` 修复：`Post.create` 补充 `authorId` 与唯一 `slug`，适配 schema 约束

### Compatibility

- 无数据库迁移变更；部署 v1.7.1 后 Flyway 会补跑 V19/V20，创建 `page_view_log` 与 `post_annotation` 表
- 可在验收失败时回滚至 `v1.7.0`

## [v1.7.0] - 2026-08-11

**Tag**：`v1.7.0`
**Commit**：（发布后回填）
**部署**：（待验收）
**回滚基线**：`v1.6.0`

### Added

- 文章批注功能：在文章阅读页选中文本 → 弹出批注框 → 多色高亮 → 悬停显示批注 → 作者可删除
- 新增 `post_annotation` 表（Flyway V20），按正文文本偏移存储批注
- `AnnotationController` REST API：GET 公开列表、POST 需登录创建、DELETE 仅作者
- 批注领域校验（文本长度、颜色白名单、偏移边界）失败返回 400

### Changed

- 版本升级至 `1.7.0`（MINOR，向后兼容）

### Compatibility

- 新增 Flyway V20 迁移，不含破坏性变更；可在验收失败时回滚至 `v1.6.0`

## [v1.6.0] - 2026-08-10

**Tag**：`v1.6.0`
**Commit**：（发布后回填）
**部署**：（待验收）
**回滚基线**：`v1.5.3`

### Added

- 页面访问统计：新增 `page_view_log` 表，通过 `PageViewInterceptor` 自动记录首页、关于页、文章列表、专栏、搜索、项目、版本发布、个人主页等公开页面的访问情况
- 新增 `PageViewStatsPort` 与 `MyBatisPageViewStatsAdapter`，提供页面排名、国家分布、趋势与下钻分析
- 管理后台 `/admin/analytics` 支持「📄 文章统计 / 🗂 页面统计」维度切换，数据由前端 AJAX 按维度拉取
- 新增 `PageViewEventHandler` 异步消费 `PageViewedEvent`，复用 `GeoIpService` 解析 IP 地理位置

### Changed

- 版本升级至 `1.6.0`（MINOR，向后兼容）

### Compatibility

- 新增 Flyway V19 迁移，不含破坏性变更；可在验收失败时回滚至 `v1.5.3`

## [v1.5.3] - 2026-08-10

**Tag**：`v1.5.3`
**Commit**：`82d50a80acb4c1c136aaf1cfd67e0bc2f7b6c194`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点 `2026-08-10T04:15Z`；应用节点 `2026-08-10T04:17Z`
**验收**：两节点 SNI HTTPS 首页与关于页均返回 `200`；关于页技术栈显示 Java 25 / Spring Boot 4；首页最新/热门、文章列表与详情、旧 ID 跳转（302→200）、专栏、搜索、项目和文章图片查询回归全部通过
**回滚基线**：`v1.5.2`

### Fixed

- 关于页（`/about`）技术栈文案：Java 21 → Java 25、Spring Boot 3 → Spring Boot 4
- 项目文档与 `AGENTS.md` 的 JDK 版本说明统一更新为 Java 25（Maven 指南、架构概览、代码质量、工程陷阱）

### Compatibility

- 无数据库迁移；可在验收失败时回滚至 `v1.5.2`

## [v1.5.2] - 2026-08-10

**Tag**：`v1.5.2`
**Commit**：`630cc572fa50ef792859ed5007b90d35312d003e`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点 `2026-08-10T03:55Z`；应用节点 `2026-08-10T03:57Z`
**验收**：两节点容器健康、应用日志无 Flyway/MySQL/Redis/MeiliSearch 错误；SNI HTTPS 首页最新/热门及翻页、文章列表与详情、旧 ID 跳转（302→200）、专栏列表与详情、搜索、项目和真实文章图片均返回 `200`
**回滚基线**：`v1.5.1`

### Fixed

- 修复 Spring Boot 4.1 下 `@WebMvcTest` 切片测试无法运行：后台相关测试导入 `SecurityConfig` 提供 `springSecurityFilterChain`，补全 `RateLimitPort` / `RateLimitProperties` / `PersistentTokenRepository` mock；补齐 `Import` 与 `ThymeleafSecurityHandlerConfig` import
- 未认证访问后台的断言对齐真实行为：期望 302 重定向到 `/login`（此前断言 4xx）
- 新增 `ThymeleafSecurityExpressionHandler` 与 `SecurityMockMvcConfig`：恢复 `thymeleaf-extras-springsecurity6` 在 Spring Security 7 下的 `sec:authorize` 模板渲染，以及 Boot 4 `@WebMvcTest` 下 `@WithMockUser` 认证生效
- 更新 `verify-changed-coverage.sh` 使用 JDK 25（项目编译目标已升级）

### Compatibility

- 无数据库迁移；可在验收失败时回滚至 `v1.5.1`
- 解决 v1.5.0 标注的"thymeleaf-extras-springsecurity6 尚不兼容 Spring Security 7"问题

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
