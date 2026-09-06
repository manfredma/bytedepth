# Changelog

本文件记录正式发布版本；每个条目必须与一个不可变 annotated Git Tag 一一对应。格式参考 Keep a Changelog，版本号遵循 Semantic Versioning。

## [v2.12.0] - 2026-09-06

**Tag**：`v2.12.0`
**Commit**：`b3f58afb5272b804e68d38590d22e06944e72f02`
**部署**：2026-09-06 22:49 CST；生产单机（175.24.197.202）已部署。完整 Compose 服务正常（MySQL、Redis、MeiliSearch healthy；app、nginx、file-server Up）；SNI 首页、最新/翻页/热门、文章、旧链接 302→200、专栏、搜索、项目及实际文章图片均返回 200，应用日志无 WARNING/ERROR。
**回滚基线**：`v2.11.12`

### Added

- 移动端浏览器原生选中文本后保留“划线”入口：点击后仅显示颜色选择，避免与浏览器的复制等系统菜单重复；桌面端完整的复制、划线和评论菜单保持不变。

### Fixed

- staging 数据同步使用正确的 `bytedepth-app` 服务名，并将 MySQL、Redis 密码通过容器环境变量传递；MeiliSearch 快照导入使用可控的临时容器，防止同步长时间残留。
- staging 缺失的图片副本已恢复，真实文章页的桌面与移动端划线 E2E 覆盖跨文本节点的划线场景。

### Compatibility

- 无数据库迁移、API 破坏性变更或部署配置格式变更；可回滚至 `v2.11.12`。

## [v2.11.12] - 2026-08-26

**Tag**：`v2.11.12`
**Commit**: b6706feb10f14a1d7a587c93320c0702e4ccb0d4
**部署**: 2026-08-26 21:08 CST；生产单机（175.24.197.202）已部署。`upload-ip` 放宽至 60次/分钟生效：连续 25 次上传 0 个 429（旧 20/h 第 21 次起 429）；首页 HTTPS 200；带文件上传 200 返回 `/images/...`；五容器 Up/Healthy，部署零 ERROR。
**回滚基线**：`v2.11.11`

### Changed

- 图片上传限流 `upload-ip` 从 20次/小时 放宽至 60次/分钟（桶容量 60、每秒补 1 令牌）：单管理员场景下 Obsidian 同步脚本批量上传令牌耗尽后被 429 卡住且恢复极慢，放宽后批量上传基本无感。

## [v2.11.11] - 2026-08-26

**Tag**：`v2.11.11`
**Commit**: a3020e8d5b5a4b89639c25aadc16a5586c48f1c8
**部署**: 2026-08-26 14:40 CST；生产单机（175.24.197.202）已部署。序号修复生产生效（文章左侧面板 1-6、专栏详情 1-9，均从 1 起）；首页/文章列表/详情/专栏列表/详情/搜索/项目 SNI 回归 200；部署零 ERROR。
**回滚基线**：`v2.11.10`

### Fixed

- 专栏文章序号显示修复：序号此前直接取 `post.series_order` 字段原值，文章移出专栏后该字段未重排产生空洞（如 4-9），导致文章详情页左侧专栏面板与专栏详情页序号从 4 开始而非 1。序号改用列表循环位置，与 `series_order` 字段解耦：文章详情页左侧面板（全量）用 `iterStat.count`；专栏详情页（分页）用 `(currentPage-1)*pageSize+postStat.count` 保证跨页连续；后台专栏列表（全量）用 `iter.count`。补 WebMvcTest 覆盖位置序号与跨页全局位置。
- 工程陷阱补充 SSH 断开后远程命令存活规则：同步 `ssh "cmd"` 断开会 SIGHUP 远程前台进程，长任务需 `nohup`/`setsid` 脱离会话并重定向日志再轮询。

## [v2.11.10] - 2026-08-25

**Tag**：`v2.11.10`
**Commit**: d90fd1f4bb41fb968e11632d6d6df4f88bd4f7e9
**部署**: 2026-08-25 11:36 CST；生产单机(175.24.197.202)已部署。nginx conf.d 宿主挂载生效,career 路由 nginx 重建后不丢;certbot deploy hook 已安装。career.bytedepth.cn 证书正确、HTTP 302;bytedepth.cn HTTP 200、连续 3 次无 career 间歇;部署零 ERROR。
**回滚基线**：`v2.11.9`

### Fixed

- nginx conf.d 持久化挂载：`nginx.conf` 加 `include /etc/nginx/conf.d/*.conf`，compose nginx 挂载宿主 `/opt/nginx-conf.d`，解决同 IP 多站点（career）路由在 nginx 容器重建后丢失导致证书域名不匹配的问题。新增域名的 conf 写入 `/opt/nginx-conf.d/` 自动加载、部署不丢。
- certbot 续期 deploy hook：`/etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh` 续期后自动 reload nginx，所有证书续期生效无需手动操作。
- `deploy/README.md` 增补同 IP 多站点部署约束（service 名带工程前缀、conf.d 注入规则、禁止 docker cp 到容器）。

## [v2.11.9] - 2026-08-25

**Tag**：`v2.11.9`
**Commit**: 26b4fdabaa4b4ea773b2f94f5f98f0ac50bee1e8
**部署**: 2026-08-25 11:09 CST；生产单机(175.24.197.202)已部署。nginx service 名 bytedepth-app 生效,--remove-orphans 清旧容器,部署零 ERROR。首页 HTTPS 200、连续 6 次稳定返回 bytedepth(无 career 间歇)。
**回滚基线**：`v2.11.8`

### Fixed

- 修复 bytedepth.cn 间歇性返回 career 页面：bytedepth 与 career 共用 Docker 网络，两工程 app service 都叫 `app` 导致 nginx 轮询解析。app service 改名 `bytedepth-app`，nginx upstream 用唯一 service 名；bootstrap 加 `--remove-orphans` 清理旧容器。
- Maven 镜像 `mirrorOf` 从 `central` 改为 `*`，覆盖所有仓库（含 Spring），加速服务器端 Docker 构建。
- 2C2G staging 内存限制：app `-Xmx256m`、Redis `maxmemory 64mb`、Dockerfile Maven `-Xmx512m`，避免构建期间内存耗尽导致 SSH 失联。只 staging 生效，生产不受限。

## [v2.11.8] - 2026-08-24

**Tag**：`v2.11.8`
**Commit**：`41aac46fdab5ccf066695134ea85f6d9a280fee4`
**部署**：2026-08-24 11:45 CST；生产单机（`175.24.197.202`）已部署。MySQL start_period 生效，部署零 ERROR（对比 v2.11.7 的启动时序 unhealthy）。首页 HTTPS 200、灯箱脚本含缩放与平移逻辑、图片 HTTPS 200。
**回滚基线**：`v2.11.7`

### Fixed

- MySQL healthcheck 加 `start_period: 60s`：MySQL 8.0 冷启动（InnoDB 初始化 + TLS 配置约 48 秒）期间不计入 retries，避免部署时 app 依赖因 healthcheck 偶发 unhealthy 启动失败。

## [v2.11.7] - 2026-08-24

**Tag**：`v2.11.7`
**Commit**：`462dadcc8da4f9befb5276e7b25923d73e6387db`
**部署**：2026-08-24 11:35–11:37 CST；生产单机（`175.24.197.202`）已部署。MySQL 冷启动期 healthcheck 偶发 unhealthy（启动时序，最终 healthy），app 依赖等待后正常启动。首页 HTTPS 200、灯箱脚本含缩放逻辑、图片 HTTPS 200。
**回滚基线**：`v2.11.6`

### Fixed

- 图片灯箱接管手机双指、Mac 触控板与 Safari gesture 缩放，只缩放预览图片并阻止底层页面跟随缩放；普通滚轮与单指平移保持原行为，缩放限制为 1–4 倍且切换图片时自动重置。
- 图片灯箱尺寸：宽度 `calc(100vw - 48px)` 撑满视窗（留 24px 边距不贴边）`max-height: calc(100dvh - 48px)` 不超出视窗；`overflow: hidden` 裁剪放大溢出在灯箱内，backdrop 始终覆盖视口，溢出部分有深色底色不露底层页面。
- 图片灯箱放大后（scale > 1）支持灯箱内拖动平移查看溢出部分：Pointer Events 统一鼠标与单指拖动，双指缩放仍由 Touch/Gesture 事件处理；关闭重开平移重置。
- staging 部署的 `bootstrap-ops-deploy.sh` 无条件安装部署 Socket（不再按 mode 跳过）：Socket 是远程触发部署的通道，staging 作为测试环境同样安装以验证该通道；`deploy-staging.sh` 仍校验 `BYTEDEPTH_DEPLOY_MODE=staging` 作为护栏，防止误在生产机运行。
- staging 部署脚本测试在非容器环境（宿主）直接执行时拒绝运行，避免覆盖宿主真实 `/etc/bytedepth-deploy.conf`。
- MySQL healthcheck 改用 `MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysqladmin ping --silent`，消除不带密码导致的 `Access denied` 告警（密码走环境变量，不触发命令行密码 insecure 告警）。

### Changed

- 前端单元测试迁移到 Node 22、Vitest 与 Happy DOM，默认 `npm test` 强制执行灯箱脚本行、分支、函数和语句 100% 覆盖门禁；移除误提交的本机绝对路径 `node_modules` 符号链接并清理弃用依赖告警。
- 明确界面交互、视觉和布局改动统一在 staging 由项目所有者验收，验收通过前不得创建生产版本或部署生产。
- staging 验收提前到 PR 合并 `main` 之前：`deploy-staging.sh` 接受 origin 上任意命名分支（默认 `main`）用于预发验收，验收通过后才合并 `main`。

### Compatibility

- 无数据库迁移、API 破坏性变更或部署配置格式变更；包含纯前端 CSS/JS、测试工具链、流程文档及 staging 部署隔离修复。

## [v2.11.6] - 2026-08-22

**Tag**：`v2.11.6`
**Commit**：`d74a8a5909de850acecb3a4f14847f9a4891a098`
**部署**：2026-08-22 14:15–14:25 CST；数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**回滚基线**：`v2.11.5`

### Fixed

- 评注框定位：原 `position:fixed` + JS `requestAnimationFrame` 逐帧 `translate3d` 跟随文字，滚动慢一帧、手机端惯性滚动漂移明显（“框飘字稳”）。改为 `position:absolute` 锚正文文字容器，位置用文档坐标一次算定，滚动时随容器整体带走，0 逐帧漂移。删除 `onScroll` 中 outline 逐帧跟随，仅 load/resize/renderMarks/侧栏开闭/字体加载时重算。`comment-trigger` `top:0→-1px` 修正 active 态边框对齐。`layoutFeed` 卡片在划线滚出视口上方时设 `hidden`。
- 项目规范增补「不带病上线」原则：发布前所有测试必须全绿，既有失败同样不构成放行理由。

### Compatibility

- 无数据库迁移、API 破坏性变更或部署配置变更；纯前端 CSS/JS 修复；可回滚至 `v2.11.5`。

### Deployment Acceptance

- 两节点部署记录的 Tag 与完整 SHA 完全一致（`d74a8a5`）；数据节点完整 Compose 服务（MySQL/Redis/MeiliSearch healthy、app、nginx、file-server）与应用节点 Compose 服务（app、nginx）均正常。
- 两节点应用均成功连接 MySQL 并经 Flyway 验证 schema；本次为纯前端 CSS/JS 修复，无数据库迁移。
- 两节点分别经 IP 与 SNI HTTPS（`https://bytedepth.cn`）验证首页、文章列表与实际文章详情均返回 `200`。

## [v2.11.5] - 2026-08-22

**Tag**：`v2.11.5`
**Commit**：`7be3edb4d22238a8d24f64f8e217cbd2cf7bf357`
**部署**：2026-08-22 11:33–11:38 CST；数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**回滚基线**：`v2.11.4`

### Fixed

- 修复 session 过期（默认 60 分钟）后 CSRF token 随旧 session 失效、页面加载时一次性读取的 token 过期，导致 POST `/annotations` 被后端 CSRF 过滤器拦截返回 403、前端兜底成「划线失败，请重试」的问题。前端现对 403 自动刷新 token（GET 当前页用 DOMParser 取最新 `meta[name="_csrf"]`，带 `X-CSRF-Refresh` 头并 `cache: no-store`）并重试一次；非 403 不重试。Service Worker 放行带 `X-CSRF-Refresh` 头的请求直达网络，避免 cache-first 返回带旧 token 的缓存 HTML。失败时把 HTTP 状态码挂到失败元素的 `data-status` 属性以便诊断，用户可见文案不变。该修复同时覆盖写评注与删除划线。

### Compatibility

- 无数据库迁移、API 破坏性变更或部署配置变更；可回滚至 `v2.11.4`。

### Deployment Acceptance

- 两节点部署记录的 Tag 与完整 SHA 完全一致（`7be3edb`）；数据节点完整 Compose 服务（MySQL/Redis/MeiliSearch healthy、app、nginx、file-server）与应用节点 Compose 服务（app、nginx）均正常，应用节点 NFS 图片挂载就绪。
- 两节点应用均成功连接 MySQL 并经 Flyway 验证 schema，无 Flyway、MySQL、Redis 或 MeiliSearch 连接错误；本次为纯前端修复，无数据库迁移。
- 两节点分别经 SNI HTTPS 验证首页最新/翻页/热门、文章列表与详情、旧 ID 跳转、专栏、搜索、项目及实际文章图片均返回 `200`；实际域名的同名回归同样全部返回 `200`。

## [v2.11.4] - 2026-08-22

**Tag**：`v2.11.4`
**Commit**：`7103025f47085cf9a6ae8637a781bbcf0a725d15`
**部署**：2026-08-22 08:01–08:03 CST；数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**回滚基线**：`v2.11.3`

### Fixed

- 修复新建或编辑阅读评注后，接口返回的高精度 ISO 时间直接挤压卡片页脚的问题；列表、创建和更新响应现统一使用 `yyyy-MM-dd HH:mm`，与首屏评注时间保持一致。

### Compatibility

- 无数据库迁移、API 破坏性变更或部署配置变更；可回滚至 `v2.11.3`。

### Deployment Acceptance

- 两节点部署记录的 Tag 与完整 SHA 一致；数据节点完整 Compose 服务、应用节点 Compose 服务与 NFS 图片挂载均正常。
- 两节点应用均成功连接 MySQL，Flyway 验证 23 个迁移且 schema 保持 v23；数据节点已加载 GeoIP 数据库，应用节点按既有可选能力配置降级为未启用 GeoIP。
- 两节点分别经 SNI HTTPS 验证首页最新/翻页/热门、文章列表与详情、旧 ID 跳转、专栏、搜索、项目及实际文章图片均返回 `200`；实际域名的首页、文章详情和图片回归同样返回 `200`。

## [v2.11.3] - 2026-08-21

**Tag**：`v2.11.3`
**Commit**：`d565a6156dc395517888d78fcb7e742cd1e424b7`
**部署**：2026-08-21 11:39 CST；数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**回滚基线**：`v2.11.2`

### Fixed

- 阅读评注的 PC 跟随卡片、正文划线框与移动端批注统一为帧级位置更新，复用已创建的划线框节点，并避免滚动时修改侧栏占位高度导致的布局反馈；滚动时不再出现抖动或弹簧式尺寸变化。
- 灯箱预览中的 SVG 使用白色画布承托透明区域，文章内原图和其他格式图片保持不变。

### Changed

- 明确关键代码注释规范：维护性注释说明非直观原因、约束和取舍，而非重复代码本身。

### Compatibility

- 无数据库迁移、API 破坏性变更或部署配置变更；可回滚至 `v2.11.2`。

## [v2.11.2] - 2026-08-21

**Tag**：`v2.11.2`
**Commit**：`1307cf176722192277ce1b2d2dcd53602a93b795`
**部署**：2026-08-21 11:17 CST；数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**回滚基线**：`v2.11.1`

### Added

- 文章正文图片支持独立、可关闭且支持键盘操作的灯箱预览；该能力只作用于正文图片，不影响其他页面组件。

### Fixed

- 移动端阅读批注会随滚动与窗口尺寸重新锚定到划线位置，划线离开视口时自动收起，滚回时恢复；主动关闭后不会因滚动再次弹出。
- 移动端批注移除冗余标题，仅保留由服务端安全生成的 Markdown 正文。

### Compatibility

- 无数据库迁移、API 破坏性变更或部署配置变更；可回滚至 `v2.11.1`。

## [v2.11.1] - 2026-08-20

**Tag**：`v2.11.1`
**Commit**：`c8e83fc59d31719951c81179de8a32154e93955f`
**部署**：2026-08-20 19:29 CST；数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**回滚基线**：`v2.11.0`

### Fixed

- 修复阅读评注侧栏的裁切边界：评论列表改由纵向布局紧接标题区，跟随评论在「划线评论」分隔线处精确消失，不再受独立固定顶部偏移影响；同时收紧标题与分隔线之间的留白。

### Compatibility

- 无数据库迁移、API 破坏性变更或部署配置变更；可回滚至 `v2.11.0`。

## [v2.11.0] - 2026-08-20

**Tag**：`v2.11.0`
**Commit**：`97a17b013fb0c7febf7318f1ef7c77fe6e9c3e20`
**部署**：2026-08-20 19:00 CST；数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**回滚基线**：`v2.10.0`

### Fixed

- 修复文章列表的数据库分页：显式启用 MyBatis-Plus MySQL 分页拦截器，`page` 与 `size` 参数重新生成正确的分页查询，不再在任意页返回全部文章。
- 修复阅读评注跟随模式的离屏表现：评注在「划线评论」分隔线处裁切，与正文在导航栏下沿的消失方式一致，不再以低透明度残留。

### Added

- 阅读评注正文支持安全的 Markdown 渲染；服务端使用 CommonMark 与 HTML Sanitizer 生成展示内容，同时保留原始 Markdown 供原地编辑。

### Compatibility

- 无数据库迁移、API 破坏性变更或部署配置变更；可回滚至 `v2.10.0`。

## [v2.10.0] - 2026-08-20

**Tag**：`v2.10.0`
**Commit**：`5f74fd811a452f44dc3c1e2f104972eccd19d516`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点 `2026-08-20T10:02:24Z`；应用节点 `2026-08-20T10:04:59Z`
**验收**：两节点部署记录的 Tag 与完整 SHA 一致；数据节点完整 Compose 服务、应用节点 Compose 服务与 NFS 图片挂载均正常。两节点应用均成功连接 MySQL、Redis、MeiliSearch，Flyway 验证 23 个迁移且 schema 保持 v23。两节点分别经 SNI HTTPS 验证首页最新/翻页/热门、文章列表与详情、旧 ID 跳转、专栏、搜索、项目及实际图片均返回预期状态（`200`，旧 ID 首次为 `302` 后跟随为 `200`）；实际域名文章回归为 `200`。两节点公开的 `annotation.js` 均已确认包含原地编辑入口 `openInlineComposer` 与挂载逻辑 `mountInlineComposer`。
**回滚基线**：`v2.9.0`

### Fixed

- 编辑已有阅读评注时，紧凑编辑器在被点击评注卡片的原位置展开，不再跳转为侧栏底部的独立编辑卡片；取消或保存后均恢复原位置卡片。

### Changed

- 建立面向开发者与编程 Agent 的前端设计语言基线，知识库入口要求在改动页面、组件、视觉或交互前先阅读；明确阅读优先、语义色彩、书籍式批注、稳定动效与组件隔离规则。

### Compatibility

- 无数据库迁移、API 或部署配置变更；可回滚至 `v2.9.0`。

## [v2.9.0] - 2026-08-20

**Tag**：`v2.9.0`
**Commit**：`5e3d47ed819becf7d430801ded53e79556f5edce`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点 `2026-08-20T09:41:02Z`；应用节点 `2026-08-20T09:43:19Z`
**验收**：两节点部署记录的 Tag 与完整 SHA 一致；数据节点完整 Compose 服务、应用节点 Compose 服务与 NFS 图片挂载均正常。两节点应用均成功连接 MySQL、Redis、MeiliSearch，Flyway 验证 23 个迁移且 schema 保持 v23。两节点分别经 SNI HTTPS 验证首页最新/翻页/热门、文章列表与详情、旧 ID 跳转、专栏、搜索、项目及实际图片均返回预期状态（`200`，旧 ID 首次为 `302` 后跟随为 `200`）；实际域名文章回归也为 `200`。文章引导数据已确认包含评注创建时间字段 `createdAt`。
**回滚基线**：`v2.8.0`

### Changed

- 已有阅读评注采用更紧凑的书籍批注式排版：正文使用较小的衬线字体与常规字重，引用文字下方紧贴一条细的分类色横线，保留与评注类型一致的左侧色线。
- 编辑已有评注复用新建评注的编辑框，统一评注类型、可见范围与取消/保存操作布局；卡片页脚以流式布局在左侧显示类型、可见范围和创建时间，右侧提供操作按钮。

### Added

- 本人评注增加删除操作，并在实际删除前要求确认；服务端既有归属校验继续限制为仅删除本人评注。

### Compatibility

- 无数据库迁移、API 或部署配置变更；既有评注继续使用已保存的颜色值分类，回滚基线为 `v2.8.0`。

## [v2.8.0] - 2026-08-20

**Tag**：`v2.8.0`
**Commit**：`65d1b9de54cc5cf23eeae95b66cb7ad72dde061f`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点 `2026-08-20T08:57:54Z`；应用节点 `2026-08-20T09:01:02Z`
**验收**：两节点部署记录与完整 SHA 一致；数据节点完整 Compose 服务、应用节点 Compose 服务与 NFS 图片挂载均正常；Flyway 校验 23 个迁移并确认 schema 已是 v23，MySQL、Redis、MeiliSearch 连接正常。两节点分别经 SNI HTTPS 验证首页最新/翻页/热门、文章列表与详情、旧 ID 跳转、专栏、搜索、项目及文章实际图片均返回预期状态（`200`，旧 ID 首次为 `302` 后跟随为 `200`）；实际域名回归亦为 `200`。
**回滚基线**：`v2.7.0`

### Changed

- 阅读批注编辑框改为紧凑操作行：移除标题行，将评注类型、可见范围、取消与保存合并到文本框下方；在最窄侧栏宽度仍保持单行。
- 评注类型改为向上展开的自定义菜单，避免固定在侧栏底部的编辑框发生裁切；菜单及已选项按分类颜色呈现。
- 已有评注卡片在页脚展示与划线颜色一致的分类标签：蓝色「补充说明」、黄色「重点摘录」、绿色「实践结论」、红色「疑问待办」。

### Fixed

- 禁用根页面纵向 overscroll，避免高强度滚动时浏览器橡皮筋效果使固定批注侧栏与正文产生视觉脱离。

### Compatibility

- 无数据库迁移、API 或部署配置变更；既有评注继续复用已保存的颜色值映射分类，可回滚至 `v2.7.0`。

## [v2.7.0] - 2026-08-20

**Tag**：`v2.7.0`
**Commit**：`5ea0847883df73233fff350f7cad0efa0e5fa931`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点 `2026-08-20T08:16:00Z`；应用节点 `2026-08-20T08:18:00Z`
**验收**：两节点版本与完整 SHA 一致（`5ea0847`）；MySQL（HikariPool/Flyway）、Redis、MeiliSearch 连接正常，应用日志无 Flyway/连接错误（仅有公网扫描器请求不存在路径的 404 噪声）；两节点 SNI HTTPS 查询回归（首页、翻页、文章详情、专栏、搜索、图片）及实际域名 HTTPS 均为 `200`，应用节点 NFS 图片可读
**回滚基线**：`v2.6.0`

### Added

- 阅读批注侧栏新增「跟随/紧凑」两种展示模式切换控件（默认跟随型）：跟随型下评注卡片钉在对应划线高度，随正文滚动联动；紧凑型为铺开列表不联动。
- 点击评注卡片可定位正文：自动滚动到对应划线位置，卡片边框与正文划线框边框同步加深表示选中；再次点击取消选中。点击正文「评注」标签与点击卡片双向同步选中态。

### Changed

- 屏幕断点重构为两档：手机屏（≤768px）与 PC 屏（≥769px），删除 1600px 中屏/大屏细分；PC 屏统一并排布局，侧栏 fixed 浮在 container 右沿内侧（`right: 10vw`），宽度随屏宽 `clamp(240px, 24vw, 360px)` 自适应。
- 侧栏打开时整体横向不再收缩：container 保持与关闭时一致的 `max-width: 80vw`，正文 `1fr` 填满剩余空间，不再被硬限 980px。
- 侧栏顶部与文章卡片顶部齐平（`top: 92px`），不再紧贴导航栏上沿；底部保留约 36px 留白。
- 正文文字到侧栏的横向间距收窄（右 padding `0.875vw` + grid gap `0.5vw`）。
- 侧栏关闭时原地立即消失（无平移过渡），打开时淡入。
- 悬浮阅读工具栏（目录/评论/主题/顶部）位置不再随侧栏开关改变。

### Fixed

- 批注侧栏默认不再自动打开：移除 localStorage 偏好记忆，每次进入文章页侧栏保持关闭，需主动点击工具栏「评论」按钮展开。

## [v2.6.0] - 2026-08-20

**Tag**：`v2.6.0`
**Commit**：`02c4e87d9908c7c2a9a981c3c0809ff0d8171b4e`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点 `2026-08-20T02:40:22Z`；应用节点 `2026-08-20T02:42:04Z`
**验收**：两节点 Socket、完整 Compose 服务与应用节点 NFS 挂载正常；应用日志无 WARNING，MySQL、Redis、MeiliSearch 与 Flyway 均正常；两节点 SNI HTTPS 查询回归及实际域名 HTTPS 均为 `200`（首页最新/热门及翻页、文章列表与详情、旧 ID 跳转、专栏、搜索、项目和文章图片）
**回滚基线**：`v2.5.0`

### Fixed

- 统一至少 1600px 宽屏阅读页的批注工具栏与 360px 批注栏坐标；工具栏固定在批注栏外侧，不再因本地与远程浏览器的视口宽度差异落入不同的布局基准。
- 阅读时固定批注栏位置，并将批注卡片改为栏内静态列表；文章滚动不再重排、淡出或隐藏批注卡片，长列表仅在批注栏内部滚动。

### Compatibility

- 无数据库迁移、API 或部署配置变更；仅调整宽屏阅读页前端布局。验收失败时可回滚至 `v2.5.0`。

## [v2.5.0] - 2026-08-20

**Tag**：`v2.5.0`
**Commit**：`ee4068d91a25d44dc217f0ddfb5dfc1e8c2e1126`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点 `2026-08-20T02:22:21Z`；应用节点 `2026-08-20T02:24:41Z`
**验收**：两节点 SNI HTTPS `200`；查询回归全部通过（首页最新/热门及翻页、文章列表与详情、旧 ID 跳转、专栏、搜索、项目和文章图片）；实际域名 HTTPS `200`
**回滚基线**：`v2.4.0`

### Fixed

- 修复文章正文内的直线划线在加粗等内联文本节点交界处出现断口：去除分段标记的横向内边距，并禁用字形避让，使同一选区的直线视觉连续。

### Compatibility

- 无数据库迁移、API 或部署配置变更；仅调整前端划线渲染样式。可在验收失败时回滚至 `v2.4.0`。

## [v2.4.0] - 2026-08-19

**Tag**：`v2.4.0`
**Commit**：`e14e1860ef7e907cbd97fa1a4ac44d5b3539fdae`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点 `2026-08-19T11:04:10Z`；应用节点 `2026-08-19T11:06:44Z`
**验收**：两节点 SNI HTTPS `200`；查询回归全部通过（首页最新/热门及翻页、文章列表与详情、旧 ID 跳转、专栏、搜索、项目和文章图片）；实际域名 HTTPS `200`
**回滚基线**：`v2.3.0`

### Changed

- 优化文章阅读页的划线评论布局：首次进入默认收起批注侧栏，并记住用户显式的开关偏好；769–1599px 宽度将侧栏排到正文之后，避免压缩阅读行宽。
- 在至少 1600px 的宽屏上，以 980px 正文、360px 批注栏的居中双栏阅读轨道展示；批注栏随视口高度延展，阅读工具栏保持可用并移至批注栏外侧。

### Compatibility

- 无数据库迁移、API 或部署配置变更。已有用户的批注侧栏开关偏好会继续保留；未保存偏好的用户将使用默认收起状态。可在验收失败时回滚至 `v2.3.0`。

## [v2.3.0] - 2026-08-19

**Tag**：`v2.3.0`
**Commit**：`c656ce16a5c508e18f6a7a0666c987328aa25710`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点与应用节点均于 `2026-08-19T08:5xZ` 完成
**验收**：两节点 SNI HTTPS `200`；查询回归全部通过（首页/文章详情/搜索/文章图片）；数据节点 Flyway V23 已执行，`persistent_logins` 表已删除
**回滚基线**：`v2.2.0`

### Changed

- Flyway V23 删除 `persistent_logins` 表：v2.1.14 remember-me 已改用 `TokenBasedRememberMeServices`（自包含签名 cookie），不再读写该表，彻底清除残留技术债。同步清理 `SecurityConfig` 注释与 `database-schema.md` 中的 persistent_logins 引用。

### Compatibility

- 新增 Flyway V23 迁移，仅向前执行（`DROP TABLE`）；表已无代码引用，删除无功能影响。若回滚到 `v2.1.13`（`PersistentTokenBasedRememberMeServices`），需先手动 `CREATE TABLE persistent_logins`（V13 不会重跑）。可在验收失败时回滚至 `v2.2.0`。

## [v2.2.0] - 2026-08-19

**Tag**：`v2.2.0`
**Commit**：`e001e70063f6976e9ee3a3805b10118fc273ceb0`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点于 `2026-08-19T08:35Z`、应用节点于 `2026-08-19T08:38Z` 完成
**验收**：两节点 SNI HTTPS `200`；查询回归全部通过（首页最新/热门及翻页、文章列表与详情、旧 ID 跳转、专栏、搜索、项目和文章图片）
**回滚基线**：`v2.1.14`

### Added

- 新增 `docs/architecture/bytedepth-system-architecture.html`：以自包含 HTML/SVG 展示当前生产双机拓扑、私网数据服务、NFS 图片共享、Obsidian API 同步，以及 Spring Boot DDD 模块依赖方向。

### Compatibility

- 纯文档新增，无数据库迁移、API 或部署配置变更；发布后可回滚至 `v2.1.14`。

## [v2.1.14] - 2026-08-19

**Tag**：`v2.1.14`
**Commit**：`7bd57ca60ea80c02230152fb3eb1bf5743b88660`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点与应用节点均于 `2026-08-19T08:xxZ` 完成
**验收**：两节点 SNI HTTPS `200`；查询回归全部通过（首页最新/热门及翻页、文章列表与详情、专栏、搜索、项目、文章图片）
**回滚基线**：`v2.1.13`

### Fixed

- 修复「记住30天」仍频繁被踢下线（尤其第二天）：`PersistentTokenBasedRememberMeServices` 每次 remember-me 自动登录轮换 token，session 过期（60m）后浏览器并发请求各自用旧 cookie 触发盗用检测，误删 `persistent_logins` 该用户全部记录导致强制登出。改用 `TokenBasedRememberMeServices`，cookie 自包含 `user + expiry + HMAC` 签名，不查库、不轮换、无并发竞态；30 天有效期与 SameSite=Lax/Secure/HttpOnly 不变，改密码仍使旧 cookie 失效。删除 `PersistentTokenRepositoryConfiguration` 与 `JdbcTokenRepositoryImpl` 依赖（`persistent_logins` 表保留不动）。

### Changed

- `application.yml` 新增 `server.forward-headers-strategy: native`：nginx 终止 SSL 后反代 HTTP，开启后 Tomcat 信任 `X-Forwarded-Proto`，`request.isSecure()`/重定向 https 正确（原缺此配置，应用自认 HTTP）。

### Compatibility

- 无数据库迁移或 API 变更；`persistent_logins` 表保留不动（不再被读写，可后续手动清理）。可在验收失败时回滚至 `v2.1.13`。

## [v2.1.13] - 2026-08-14

**Tag**：`v2.1.13`
**Commit**：（发布后由受控发布工具回填）
**部署**：（待验收）
**回滚基线**：`v2.1.12`

### Added

- 数据库表结构知识库 `docs/architecture/database-schema.md`：包含 Mermaid ER 概览图、19 张表的逐字段说明（列名/类型/约束/索引）、表间关系基数对照表、Flyway V1-V22 迁移版本与表结构变更映射。已与线上生产 MySQL 实际 DDL 二次校对。

### Compatibility

- 纯文档新增，无功能变化；`docs/README.md` 加入导航入口。可在验收失败时回滚至 `v2.1.12`。

## [v2.1.12] - 2026-08-14

**Tag**：`v2.1.12`
**Commit**：（发布后由受控发布工具回填）
**部署**：（待验收）
**回滚基线**：`v2.1.11`

### Fixed

- 修复通过左侧专栏导航切换文章后页面布局错乱：`series-navigation.js` 替换 `#post-article` 后，旧 `annotation.js` 的 DOM 引用全部失效但未重新初始化。改造 `annotation.js` 暴露 `window.initAnnotations()`，将 `document`/`window` 级监听器改为具名函数并注册 cleanup，重新初始化前先移除旧监听器避免重复绑定。`series-navigation.js` 在替换文章后提取新批注数据并调用 `initAnnotations()` 重新绑定。

### Compatibility

- 无数据库迁移或 API 变更；纯前端 JS 改造。可在验收失败时回滚至 `v2.1.11`。

## [v2.1.11] - 2026-08-13

**Tag**：`v2.1.11`
**Commit**：（发布后由受控发布工具回填）
**部署**：（待验收）
**回滚基线**：`v2.1.10`

### Fixed

- 文章阅读页批注侧栏默认打开（v2.1.9 引入）对已访问用户未生效：`annotation.js` 以无内容指纹的裸路径引用，Service Worker（cache-first）持续提供旧版缓存。升级 `CACHE_NAME` 至 `bytedepth-v7`，activate 时清除旧缓存、强制拉取新版 `annotation.js`。

### Compatibility

- 无数据库迁移或 API 变更；Service Worker 缓存版本升级，首次访问新版本后自动清除旧缓存。可在验收失败时回滚至 `v2.1.10`。

## [v2.1.10] - 2026-08-13

**Tag**：`v2.1.10`
**Commit**：`c29dad9cf85ed255f0ca4cfd28e71b596cae4bb2`
**部署**：数据节点（`175.24.197.202`）与应用节点（`124.221.143.25`）均已部署并验收
**部署时间（UTC）**：数据节点与应用节点均于 `2026-08-13T16:15Z` 完成
**验收**：两节点 SNI HTTPS `200`；查询回归全部通过
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
