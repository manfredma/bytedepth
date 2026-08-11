# 后台列表页统一查询过滤组件 `bd-filter-bar` 设计

日期：2026-08-11
状态：已确认
版本：待定（v1.7.0 起，实施前与 owner 确认）

## 1. 背景与目标

后台管理列表页目前只有翻页能力，缺少查询过滤。仅 `view-logs` 有手写 filter-bar，且各页不统一、未抽象为公共组件。目标：

1. 提供一个统一的前端过滤组件 `bd-filter-bar`，接入全部 7 个后台管理列表页。
2. 过滤字段按页面定制（有的按 ID、有的按名称、有的按状态）。
3. 与现有分页 fragment 联动，翻页/跳页不丢失过滤条件。

## 2. 范围

| 页面 | 路径 | 处理 |
|------|------|------|
| 文章管理 | `/admin/posts` | 分页(已有) + 过滤 |
| 评论管理 | `/admin/comments` | 分页(补 UI) + 过滤 |
| 分类管理 | `/admin/categories` | 不分页 + 过滤(补父链) |
| 标签管理 | `/admin/tags` | 新引入分页 + 过滤 |
| 专栏管理 | `/admin/series` | 新引入分页 + 过滤 |
| 用户管理 | `/admin/users` | 新引入分页 + 过滤(全部用户) |
| 访问日志 | `/admin/view-logs` | 分页(已有) + 迁移到公共组件 |

公开列表页（posts/columns/projects）不在本次范围。

## 3. 交互形态

**服务端渲染 GET 表单**：过滤表单 `method="get"` 提交，URL 携带参数（可刷新、可分享），与现有 `pagination` fragment 完全兼容。沿用项目现有纯 Thymeleaf 服务端渲染模式，不引入前端框架或 AJAX。

## 4. 组件设计

### 4.1 位置与命名

- 模板：`bytedepth-start/src/main/resources/templates/fragments/filter-bar.html`
- CSS 命名空间：`bd-filter-bar-*`（与 `bd-pagination-*` 一致），自隔离，消费页不得覆盖。
- 样式：方案 A 紧凑单行。flex wrap 水平排列，字段自动换行；窄屏时动作按钮占满整行。

### 4.2 参数化方式（配置驱动）

组件只渲染一次通用结构，7 个页面的字段差异由 Controller 构建 `List<FilterField>` 传入。组件零改动，加字段只改 Controller。

```text
filterBar(action, fields)
  action  → 表单提交地址，如 /admin/posts
  fields  → List<FilterField>
```

### 4.3 新增 DTO（adapter 层）

`bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/filter/FilterField.java`

```java
public class FilterField {
    private String name;          // 表单字段名（即 URL 参数名）
    private String label;         // 标签文案
    private String type;          // TEXT / NUMBER / SELECT
    private String value;         // 当前值（回填输入框）
    private String placeholder;   // 占位提示
    private List<FilterOption> options;  // SELECT 类型用
    // 静态工厂：text(name, label, value, placeholder)
    //          number(name, label, value, placeholder)
    //          select(name, label, value, List<FilterOption>)
}
```

`FilterOption.java`：`value` / `label` / `selected`。

### 4.4 组件模板结构

```html
<div th:fragment="filterBar(action, fields)" class="bd-filter-bar">
  <style>/* bd-filter-bar-* 自隔离样式，方案 A */</style>
  <form method="get" th:action="${action}" class="bd-filter-bar-form">
    <th:block th:each="f : ${fields}">
      <div class="bd-filter-bar-field">
        <label th:for="${f.name}" th:text="${f.label}">标题</label>
        <input  th:if="${f.type == 'TEXT'}"   type="text"   th:name="${f.name}"
                th:value="${f.value}" th:placeholder="${f.placeholder}">
        <input  th:if="${f.type == 'NUMBER'}" type="number" th:name="${f.name}"
                th:value="${f.value}" min="1" th:placeholder="${f.placeholder}">
        <select th:if="${f.type == 'SELECT'}" th:name="${f.name}">
          <option th:each="opt : ${f.options}" th:value="${opt.value}"
                  th:selected="${opt.selected}" th:text="${opt.label}">全部</option>
        </select>
      </div>
    </th:block>
    <div class="bd-filter-bar-actions">
      <button type="submit" class="bd-filter-bar-submit">筛选</button>
      <a th:href="${action}" class="bd-filter-bar-reset">重置</a>
    </div>
  </form>
</div>
```

### 4.5 分页联动

Controller 构建 `filterBaseUrl`（`/admin/posts?title=xx&status=yy&`，以 `&` 结尾）传入 model，模板直接传给 pagination fragment，翻页/跳页保留过滤条件。

## 5. 各页面接入设计

### 5.1 文章 `/admin/posts`

- Controller 新增参数：`title`(like)、`status`、`seriesId`、`categoryId`（均可选）。
- 后端：`ListAllPostsQryExe` 的 `execute`/`executeByAuthor` 扩展过滤；`PostRepository.findPage`、`AuthorPostRepository.findPageByAuthorId` 支持过滤。
- 过滤字段：标题(text)、状态(select: 全部/已发布/草稿/已删除)、专栏(select: allSeries)、分类(select: allCategories)。
- 下拉数据：`allSeries`(已有)、`allCategories`(通过 `listCategoriesQryExe.execute()`)。
- 权限：保留 `ContentOwnershipGuard` 作者约束。

### 5.2 评论 `/admin/comments`

- Controller 新增参数：`authorName`(like)、`postId`。
- 后端：`ListCommentsQryExe.findAll(page, size)` 扩展过滤；`CommentRepository.findAll` 支持过滤。
- 页面补分页 UI（Controller 已有 page/size 与 total 逻辑，需补 totalPages/total 计算）。
- 过滤字段：作者名(text)、文章 ID(number)。

### 5.3 分类 `/admin/categories`（特殊：树形）

- Controller 新增参数：`name`(like)、`slug`(like)。
- **不引入分页**。过滤逻辑：先匹配目标分类，再**补父链**（递归补上父级分类），保持树形完整可读。
- 后端：`CategoryRepository` 新增按 name/slug 过滤的方法；补父链逻辑放 QueryExe 层。
- 过滤字段：名称(text)、Slug(text)。

### 5.4 标签 `/admin/tags`

- Controller 新增参数：`name`(like)、`page`、`size`。
- 后端：`TagRepository` 新增分页过滤查询（返回 TagWithCount）；`ListTagsQryExe` 新增对应方法。
- 过滤字段：名称(text)。
- 新引入分页 UI。

### 5.5 专栏 `/admin/series`

- Controller 新增参数：`name`(like)、`page`、`size`。
- 后端：`SeriesRepository` 新增分页过滤查询；`AdminSeriesListController.list` 保留 `ContentOwnershipGuard` 权限（管理员全量、作者只看自己的）。
- 过滤字段：名称(text)。
- 新引入分页 UI。

### 5.6 用户 `/admin/users`（特殊：范围扩展）

- Controller 新增参数：`username`(like)、`status`、`page`、`size`。
- **范围扩展**：从仅展示 `PENDING` 改为展示全部用户，按状态过滤，操作（激活/封禁/删除）保留。
- 后端：`ListPendingUsersQryExe` 扩展为按 username/status 过滤 + 分页（保留原类名或更名，实施时定）；`UserRepository` 新增分页过滤查询。
- 过滤字段：用户名(text)、状态(select: 全部/待审核/已激活/已封禁)。

### 5.7 访问日志 `/admin/view-logs`

- 过滤参数保持 `postId`、`userId`。
- 将现有手写 filter-bar 迁移为 `bd-filter-bar` 组件，行为不变。

## 6. 后端改造明细

### Repository 层（domain 接口 + infrastructure 实现）

| 接口 | 新增方法 |
|------|---------|
| `PostRepository` / `AuthorPostRepository` | `findPage(page, size, title, status, seriesId, categoryId)` 或 Filter 对象重载 + count |
| `CommentRepository` | `findAll(page, size, authorName, postId)` 或重载 + count |
| `CategoryRepository` | `findByNameOrSlugLike(keyword)` 或按 name/slug 过滤 |
| `TagRepository` | `findAllWithCount(name, page, size)` + count |
| `SeriesRepository` | `findPage(name, page, size)` / `findPageByAuthorId(authorId, name, page, size)` + count |
| `UserRepository` | `findPage(username, status, page, size)` + count |

注：MyBatis-Plus `LambdaQueryWrapper` + `Page` 可实现，尽量复用现有 mapper。方法签名以实施时最小改动为准（优先重载而非破坏现有调用方）。

### QueryExe 层

- `ListAllPostsQryExe`、`ListCommentsQryExe`、`ListTagsQryExe`、`ListPendingUsersQryExe` 扩展对应过滤/分页方法。
- 分类补父链逻辑放入 `ListCategoriesQryExe` 或新建方法。

### Controller 层

- 7 个 Controller 增加过滤参数，构建 `filterFields` 与 `filterBaseUrl`。
- 下拉选项（状态/专栏/分类）在 Controller 构建。

## 7. 测试计划

- 每个 Controller 扩展/新增 WebMvcTest：覆盖各过滤参数（有值/空值/组合）、分页参数、`filterFields`/`filterBaseUrl` 渲染。
- Repository 层新增过滤分页方法的单元测试。
- 关键业务分支（分类补父链、用户状态过滤、专栏作者权限叠加过滤、文章作者权限叠加过滤）覆盖率 100%。
- 全量 `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn clean test -Dsort.skip=true`。

## 8. 迭代方式

在 git worktree 隔离实施（用户明确要求），版本号实施前与 owner 确认。实施前先 `git status --short` 确认干净。

## 9. 不做的事（YAGNI）

- 不引入前端框架 / AJAX / 异步过滤。
- 不改造公开列表页。
- 不为过滤增加复杂操作符（范围、模糊高级语法）。
- 不新增 Maven 模块。
