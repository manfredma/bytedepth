# 专栏（Series）完整管理功能设计

**日期：** 2026-06-08  
**状态：** 已确认，待实现

---

## 背景

现有系统中专栏（Series）已有基础数据模型（`series` 表 + `post.series_id/series_order` 字段），但缺少：

1. 后台管理界面——无法通过 UI 对专栏文章进行移入、移出、调整顺序
2. 前台入口——读者无法浏览专栏列表或进入专栏详情页
3. 分页统一——各页面分页样式不一致

---

## 目标

- **前台**：新增专栏列表页（`/columns`）和专栏详情页（`/columns/{slug}`），支持分页
- **后台**：专栏详情管理页支持文章移入/移出/↑↓调序；文章列表支持快速绑定/解绑专栏
- **全站**：统一分页样式为样式 B（含页码按钮 + 总数信息），抽取为 Thymeleaf fragment

---

## 方案选择

采用 **方案 B：App 层 CmdExe 聚合业务逻辑**，与现有 `SetPostSeriesCmdExe` 模式完全一致，Controller 只做路由，所有业务逻辑在 App 层可独立测试。

---

## 数据层变更

### 不新增数据库表

现有 `series` 表 + `post.series_id / series_order` 字段已满足所有需求。

### SeriesRepository 新增方法

```java
// 查询专栏下所有文章（含草稿），按 series_order ASC，后台管理用
List<SeriesPostItem> findAllPostsBySeries(Long seriesId);

// 查询可加入专栏的候选文章（已发布或草稿、且尚未加入该专栏），支持分页+关键词
List<Post> findCandidatesForSeries(Long seriesId, String keyword, int page, int size);
long countCandidatesForSeries(Long seriesId, String keyword);
```

### PostRepository 新增方法

```java
// 清除文章的专栏绑定（series_id、series_order 置 null）
void clearPostSeries(Long postId);
```

### SeriesRepository.findAll() 排序变更

原来无排序，改为按 `name ASC` 排序（前台、后台均适用）。

---

## 应用层（App 层）

### 新增 CmdExe

```
bytedepth-app/src/main/java/manfred/bytedepth/app/series/
  ├── SetPostSeriesCmdExe.java              ← 已有，不动（Obsidian 同步时指定 order 用）
  ├── AppendPostToSeriesCmdExe.java         ← 新增：后台手动移入，order 自动追加到末尾
  ├── RemovePostFromSeriesCmdExe.java       ← 新增：移出专栏
  └── MovePostInSeriesCmdExe.java           ← 新增：↑↓ 调序
```

**`AppendPostToSeriesCmdExe.execute(Long postId, Long seriesId)`**
- 查询该专栏当前最大 series_order，自动设为 max + 1
- 调用 `postRepository.setPostSeries(postId, seriesId, newOrder)`

**`RemovePostFromSeriesCmdExe.execute(Long postId)`**
- 调用 `postRepository.clearPostSeries(postId)`

**`MovePostInSeriesCmdExe.execute(Long seriesId, Long postId, Direction direction)`**
- 取该专栏所有文章，按 `series_order` 排序
- UP：与前一篇交换 order；DOWN：与后一篇交换 order
- 边界情况：已是第一篇时 UP 静默忽略，已是最后一篇时 DOWN 静默忽略
- `Direction` 为枚举：`UP / DOWN`

### 新增 QryExe

```
bytedepth-app/src/main/java/manfred/bytedepth/app/series/
  ├── GetSeriesDetailQryExe.java            ← 新增：后台专栏详情（含所有文章+草稿）
  ├── GetSeriesForPortalQryExe.java         ← 新增：前台专栏详情（仅已发布文章+摘要）
  └── ListSeriesQryExe.java                 ← 新增：前台专栏列表（含文章数+摘要）
```

**`GetSeriesDetailQryExe.execute(String slug)`**
- 返回 `SeriesDetailDTO`：专栏基本信息 + 所有文章列表（含草稿，按 order）

**`GetSeriesForPortalQryExe.execute(String slug, int page, int size)`**
- 返回 `SeriesPortalDTO`：专栏信息 + 已发布文章（按 order 分页）+ 每篇 content 前 160 字作摘要

**`ListSeriesQryExe.execute(int page, int size)`**
- 返回 `List<SeriesCardDTO>`：所有专栏（按 name ASC）+ 每个专栏文章数 + 第一篇已发布文章摘要（前 160 字）

---

## Adapter 层（路由设计）

### 前台新增路由

| 方法 | 路径 | Controller | 说明 |
|------|------|-----------|------|
| GET | `/columns` | `ColumnController` | 专栏列表，分页，按名称排序 |
| GET | `/columns/{slug}` | `ColumnController` | 专栏详情，已发布文章分页 |

### 后台新增路由

| 方法 | 路径 | Controller | 说明 |
|------|------|-----------|------|
| GET | `/admin/series/{slug}` | `AdminSeriesDetailController` | 专栏详情管理页 |
| POST | `/admin/series/{slug}/posts` | `AdminSeriesDetailController` | 移入文章（复用 SetPostSeriesCmdExe） |
| POST | `/admin/series/{slug}/posts/{id}/remove` | `AdminSeriesDetailController` | 移出文章 |
| POST | `/admin/series/{slug}/posts/{id}/up` | `AdminSeriesDetailController` | 上移 |
| POST | `/admin/series/{slug}/posts/{id}/down` | `AdminSeriesDetailController` | 下移 |
| GET | `/admin/series/{slug}/candidates` | `AdminSeriesDetailController` | 候选文章列表（分页+关键词，JSON） |

### 后台现有路由改造

- `GET /admin/series`：专栏列表每行加「管理文章」按钮，链接到 `/admin/series/{slug}`；按 name ASC 排序
- `GET /admin/posts`：文章列表新增「专栏」列
  - 已绑定：显示专栏名 badge + `✕` 移出按钮
  - 未绑定：显示「+ 加入专栏」`<select>`（全量查出专栏，数量少无需分页）+ 提交按钮

**文章列表新增路由（AdminPostController）：**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/admin/posts/{id}/series/remove` | 从文章列表页移出专栏（调用 RemovePostFromSeriesCmdExe） |
| POST | `/admin/posts/{id}/series/assign` | 从文章列表页快速绑定专栏（调用 AppendPostToSeriesCmdExe） |

---

## 前台页面设计

### `/columns` — 专栏列表页

- 设计语言：与文章详情页一致（serif 字体、米白色 `#f7f5f0` 背景、白色卡片、`box-shadow`）
- 每张卡片：专栏名（display 字体）、文章数量 badge、第一篇已发布文章 content 前 160 字摘要、「进入专栏 →」链接
- 支持分页（统一样式 B fragment）
- 专栏按名称 ASC 排序

### `/columns/{slug}` — 专栏详情页

- 页头：专栏名、专栏描述、共 N 篇文章
- 文章卡片列表：序号（accent 色 `#e94560`）、标题、摘要前 160 字、发布日期
- 卡片 hover 上浮 2px，点击跳转 `/posts/{id}`
- 支持分页（统一样式 B fragment）

---

## 后台页面设计

### `/admin/series/{slug}` — 专栏详情管理页（新增）

**上半部分：当前专栏文章列表**

| # | 标题 | 状态 | 操作 |
|---|------|------|------|
| 1 | 深入理解CMS收集器 | 已发布 | [↑（禁用）][↓] [移出] |
| 2 | G1收集器原理 | 草稿 | [↑][↓] [移出] |
| 3 | ZGC详解 | 已发布 | [↑][↓（禁用）] [移出] |

- 第一篇 ↑ 禁用，最后一篇 ↓ 禁用（灰色不可点）
- 「移出」有 `confirm` 确认对话框
- ↑↓ 和移出均为普通 `<form method="post">` 提交，操作后 redirect 回本页

**下半部分：加入文章（候选列表）**

- 搜索框（关键词过滤标题）+ 分页文章列表
- 每行显示：标题、状态、「加入」按钮（提交时自动追加到末尾，order = 当前最大 order + 1）
- 候选文章 = 尚未加入本专栏的已发布/草稿文章，按 created_at DESC
- 分页使用统一样式 B fragment

### `/admin/posts` — 文章列表（改造）

- 新增「专栏」列（行内）
- 已绑定：`[专栏名 ✕]` badge，✕ 点击后 form submit 移出
- 未绑定：`<select>` 下拉（全量专栏，按 name ASC）+ `[加入]` 按钮

---

## 分页统一（全站）

### Thymeleaf Fragment

新建 `fragments/pagination.html`，参数：

```
currentPage   当前页
totalPages    总页数
total         总条数
pageSize      每页条数
baseUrl       基础 URL（含查询参数前缀，如 /posts? 或 /columns?）
```

输出样式：
```
共 N 篇，第 X / Y 页
[← 上一页]  1  2  ③  4  5  [下一页 →]
（当前页码高亮 accent 色，前后各展示 2 页，超出用省略号）
```

### 改造范围

| 页面 | 当前状态 | 改造动作 |
|------|---------|---------|
| `/posts` 前台文章列表 | 样式 A（仅上下页） | 替换为 fragment |
| `/search` 搜索页 | 样式 A | 替换为 fragment |
| `/admin/posts` 后台文章列表 | 样式 B（手写） | 替换为 fragment |
| `/columns` 专栏列表 | 新建 | 直接用 fragment |
| `/columns/{slug}` 专栏详情 | 新建 | 直接用 fragment |
| `/admin/series/{slug}` 候选文章 | 新建 | 直接用 fragment |

---

## 不在本期范围内

- 文章封面图字段（暂不新增，摘要从 content 截取）
- 专栏的增删改（已有新建，不做编辑/删除）
- 专栏排序方式变更（当前固定 name ASC，不做自定义排序）

---

## 实现顺序建议

1. **分页 fragment**（基础设施，其他所有页面依赖）
2. **数据层**：Repository 新增方法 + SQL
3. **App 层**：CmdExe + QryExe
4. **后台**：`AdminSeriesDetailController` + 模板
5. **后台**：`AdminPostController` 改造（专栏列、移出）
6. **前台**：`ColumnController` + 两个模板
7. **分页替换**：`/posts`、`/search`、`/admin/posts` 替换为 fragment
