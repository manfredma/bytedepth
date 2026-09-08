# RSS 发现与发布状态同步设计

- **日期**: 2026-09-08
- **状态**: 待评审
- **关联 ADR**: [ADR-0001：由已发布文章状态动态生成 RSS](../../architecture/decisions/0001-published-post-driven-rss.md)

## 目标

让读者在所有使用公共顶部导航的页面看见并复制标准 RSS 订阅地址 `/feed.xml`；让支持自动发现的阅读器从公开页面的 `<head>` 识别该地址；让 Obsidian 的新建发布和增量更新无需额外操作就反映到 feed。

## 非目标

- 不新增 RSS 数据表、缓存、后台刷新按钮或 Obsidian 同步子命令。
- 不改变文章的发布、草稿、分类、标签、专栏或 slug 规则。
- 不生成 Atom、JSON Feed、按分类/标签拆分的 feed，或邮件推送。
- 不修改每个页面模板；公共片段是唯一入口。

## 现状与约束

- `FeedController` 已以 RSS 2.0 输出 `/feed.xml`，来源为 `PostRepository.findAllPublished()`；文章列表当前按 `published_at` 降序。
- Obsidian `import` 通过后台创建并发布文章，`sync`/`update` 更新已有文章且不改变其发布状态。因此新文章本已能进入 feed，但更新旧文不会按更新时间回到最新项目。
- 前台公共导航是 `templates/fragments/nav.html`，公共 head 是 `templates/fragments/pwa-head.html`。导航 CSS 必须保持 `.nav-*` 命名空间和 `--bd-*` 主题变量，且在 940px 以下已有横向可滚动的主导航。
- 公开 feed 必须只暴露已发布内容；保持 RSS 2.0，响应媒体类型为 `application/rss+xml`。

## 设计

### 1. 发布状态是唯一数据源

`FeedController` 不保存或接收同步脚本推送的 feed 状态。每次请求从已发布文章集合派生最近 20 项：

1. 为每篇文章取 `max(publishedAt, updatedAt)` 作为最近变更时间；任一时间缺失时使用另一时间，均缺失时不输出项目日期。
2. 以该时间倒序排序后取 20 项，保证存量文章、之后新发布的笔记、以及被 Obsidian 增量同步或后台编辑过的文章采用相同规则。
3. 频道 `lastBuildDate` 与项目 `pubDate` 使用该最近变更时间。这样兼容 RSS 2.0 阅读器，并明确表达本站“更新”包含实质性修订。

现有 `findAllPublished()` 仍是唯一 Repository 契约；本轮不新增跨层端口。若文章规模使全量读取成为性能问题，按 ADR-0001 的退出条件另行决策受限查询或缓存。

### 2. 标准发现入口

**公共导航**

- 在 `nav-primary` 的「项目」后加入指向 `/feed.xml` 的链接。
- 链接使用内联、无外部依赖的标准 RSS 波纹 SVG；可见图标保持克制，不引入品牌橙色等新的无语义配色，采用现有导航文字与 hover/focus 颜色。
- 链接具有 `aria-label="订阅 RSS 更新"`；SVG 设为装饰性，键盘焦点与现有导航链接一致。
- 样式只新增 `.nav-rss` 及其子选择器。940px 以下沿用 `nav-primary` 的横向滚动，避免压缩搜索、账户和主题控件。

**公共 head**

- 在 `pwa-head` 片段添加 `<link rel="alternate" type="application/rss+xml" title="bytedepth RSS" href="/feed.xml">`。
- 该片段被公开页面复用，因此无需逐页改动；后台页面不需要订阅自动发现标记。

### 3. 同步契约与知识沉淀

`docs/agent-guides/obsidian-sync.md` 作为同步操作的唯一权威入口，增加 RSS 小节：

- `import` 成功发布后自动进入 RSS。
- `sync`/`update` 成功更新已发布文章后自动成为 RSS 的最近更新；没有 `rss sync` 命令，也不得手动编辑 feed。
- 远程同步后的 `verify` 流程应额外检查 `/feed.xml` 可访问、包含目标文章链接且媒体类型正确。

不将该规则仅写入用户主目录下的 Codex skill；项目文档、ADR 与自动测试是可审查且随 PR 演进的事实来源。

### 4. 路由文档

在 `docs/architecture/routes.md` 的前台页面表记录 `GET /feed.xml`、`FeedController` 和“RSS 2.0 最近更新订阅源”，使入口可发现。

## 测试与验收

### 测试先行

1. 为 `FeedController` 增加失败测试：已发布旧文的 `updatedAt` 晚于新文的 `publishedAt` 时，旧文应排在前面，且项目与频道日期采用更新时间。该测试会捕获按首次发布时间排序、错误选择时间或先截取后排序。
2. 增加失败测试：feed 映射声明 RSS 专用响应媒体类型；通过实际 Spring MVC 响应断言 `application/rss+xml`，而非只匹配源文件文本。
3. 为公共模板新增行为层面的渲染/资源测试：导航链接可由可访问名称定位到 `/feed.xml`，公共 head 输出 RSS 自动发现关系。CSS 测试覆盖 `.nav-rss` 的命名空间和窄屏不覆盖既有布局。
4. 保留现有 XML 转义、摘要截断、无日期和空 feed 覆盖；新增分支覆盖达到 100%。

### 质量门禁与 staging

按项目规则依次执行：本地 Maven 缓存刷新与完整 `mvn test`（Java 25）、`npm test`、`npm run lint`、变更覆盖率脚本以及 staging 部署脚本回归。任何 WARNING 都是阻断项。

门禁后将 `feat/rss-navigation` 部署到 staging。验收时：

1. 在桌面与窄屏公共页面确认 RSS 图标可见、可键盘聚焦、点击至 `/feed.xml`，且不影响搜索和账户操作。
2. 用 RSS 阅读器或浏览器阅读器扩展粘贴 `https://staging.bytedepth.cn/feed.xml`，确认可订阅；检查页面自动发现。
3. 新发布一篇 staging 测试文章，再更新一篇已有已发布文章，确认两者均由 feed 自动反映，且不执行额外 RSS 操作。

项目所有者明确完成 staging 验收前，不创建 PR 或合并 `main`。

## 文件清单

| 文件 | 改动 |
|------|------|
| `bytedepth-adapter/.../FeedController.java` | 最近变更排序、日期选择与 RSS 专用媒体类型 |
| `bytedepth-start/.../fragments/nav.html` | 标准 RSS 图标链接 |
| `bytedepth-start/.../static/css/nav.css` | 隔离的 `.nav-rss` 样式与响应式保证 |
| `bytedepth-start/.../fragments/pwa-head.html` | RSS 自动发现标记 |
| `bytedepth-start/src/test/...` | feed MVC 与公共模板行为测试 |
| `docs/agent-guides/obsidian-sync.md` | 自动纳入与远程验证规则 |
| `docs/architecture/routes.md` | feed 路由参考 |
| `docs/architecture/decisions/*`、`AGENTS.md`、`docs/architecture/overview.md` | ADR 机制与 ADR-0001 |
