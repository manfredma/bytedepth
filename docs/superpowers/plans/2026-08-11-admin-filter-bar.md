# 后台列表页统一查询过滤组件实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为全部 7 个后台管理列表页接入统一查询过滤组件 `bd-filter-bar`（配置驱动、服务端渲染 GET 表单），小数据量页面补齐分页，与现有 pagination fragment 联动不丢过滤条件。

**Architecture:** 新增 Thymeleaf fragment `fragments/filter-bar.html`，接收 `action` + `List<FilterField>` 渲染通用过滤栏；每个页面 Controller 构建自己的 `filterFields` 与 `filterBaseUrl`（分页联动）。后端各 Repository 新增带过滤的分页查询方法，业务分支（分类补父链、用户状态过滤、作者权限叠加）集中在 QueryExe / Controller 层。

**Tech Stack:** Java 25、Spring Boot（Thymeleaf + Spring MVC + Security）、MyBatis-Plus（LambdaQueryWrapper + Page）、JUnit 5、MockMvc（@WebMvcTest）。

## Global Constraints

- Maven 命令必须使用 Java 25：`JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn ...`
- 所有构建命令加 `clean`；加 `-Dsort.skip=true`。
- 每项代码改动必须补齐单元测试，本次改动涉及的业务逻辑分支覆盖率必须达到 100%。
- 不得新增 Maven 模块。
- 多模块测试前先刷新缓存：`mvn clean install -DskipTests -Dsort.skip=true`，再跑 `mvn test`。
- 前端公共组件必须自隔离（样式/脚本/标记只作用于 `bd-filter-bar-*` 命名空间），组件间只通过相对位置协作。
- 所有 Thymeleaf 后台页面必须保留 `<nav th:replace="~{fragments/nav :: navbar(false)}"></nav>` 与侧边栏 `fragments/admin-sidebar`，不得遗漏。
- 过滤查询必须叠加 `ContentOwnershipGuard` 作者约束（文章/专栏/用户）。
- 分页 baseUrl 必须以 `&` 结尾，确保翻页不丢过滤条件。

---

### Task 1: FilterField / FilterOption DTO

**Files:**
- Create: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/filter/FilterField.java`
- Create: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/filter/FilterOption.java`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/filter/FilterFieldTest.java`

**Interfaces:**
- Produces:
  - `FilterField`（`name`, `label`, `type`, `value`, `placeholder`, `List<FilterOption> options`），静态工厂 `FilterField.text(name,label,value,placeholder)`、`FilterField.number(name,label,value,placeholder)`、`FilterField.select(name,label,value,List<FilterOption>)`。
  - `FilterOption`（`value`, `label`, `selected`），静态工厂 `FilterOption.of(value,label)`、`FilterOption.of(value,label,selected)`。

- [ ] **Step 1: 写失败测试** `FilterFieldTest.java`

```java
package manfred.bytedepth.adapter.web.filter;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FilterFieldTest {

    @Test
    void textField() {
        FilterField f = FilterField.text("title", "标题", "Spring", "输入关键字");
        assertEquals("title", f.getName());
        assertEquals("标题", f.getLabel());
        assertEquals("TEXT", f.getType());
        assertEquals("Spring", f.getValue());
        assertEquals("输入关键字", f.getPlaceholder());
        assertNull(f.getOptions());
    }

    @Test
    void numberField() {
        FilterField f = FilterField.number("postId", "文章 ID", "12", "数字");
        assertEquals("NUMBER", f.getType());
    }

    @Test
    void selectFieldWithSelectedOption() {
        FilterField f = FilterField.select("status", "状态", "PUBLISHED",
            List.of(FilterOption.of("", "全部"), FilterOption.of("PUBLISHED", "已发布", true)));
        assertEquals("SELECT", f.getType());
        assertTrue(f.getOptions().get(1).isSelected());
        assertFalse(f.getOptions().get(0).isSelected());
    }
}
```

- [ ] **Step 2: 跑测试确认失败** — 类不存在，编译失败。
- [ ] **Step 3: 写实现**

```java
// FilterOption.java
package manfred.bytedepth.adapter.web.filter;

import lombok.Getter;

@Getter
public class FilterOption {
    private final String value;
    private final String label;
    private final boolean selected;

    private FilterOption(String value, String label, boolean selected) {
        this.value = value; this.label = label; this.selected = selected;
    }
    public static FilterOption of(String value, String label) { return new FilterOption(value, label, false); }
    public static FilterOption of(String value, String label, boolean selected) { return new FilterOption(value, label, selected); }
}
```

```java
// FilterField.java
package manfred.bytedepth.adapter.web.filter;

import lombok.Getter;
import java.util.List;

@Getter
public class FilterField {
    private final String name;
    private final String label;
    private final String type;       // TEXT / NUMBER / SELECT
    private final String value;
    private final String placeholder;
    private final List<FilterOption> options;

    private FilterField(String name, String label, String type, String value,
                        String placeholder, List<FilterOption> options) {
        this.name = name; this.label = label; this.type = type;
        this.value = value; this.placeholder = placeholder; this.options = options;
    }
    public static FilterField text(String name, String label, String value, String placeholder) {
        return new FilterField(name, label, "TEXT", value, placeholder, null);
    }
    public static FilterField number(String name, String label, String value, String placeholder) {
        return new FilterField(name, label, "NUMBER", value, placeholder, null);
    }
    public static FilterField select(String name, String label, String value, List<FilterOption> options) {
        return new FilterField(name, label, "SELECT", value, null, options);
    }
}
```

- [ ] **Step 4: 跑测试确认通过** — `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-start clean test -Dtest=FilterFieldTest -Dsort.skip=true`
- [ ] **Step 5: 提交**

```bash
git add bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/filter/FilterField.java \
        bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/filter/FilterOption.java \
        bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/filter/FilterFieldTest.java
git commit -m "feat: 新增过滤组件配置 DTO（FilterField/FilterOption）"
```

---

### Task 2: filter-bar.html 组件 fragment

**Files:**
- Create: `bytedepth-start/src/main/resources/templates/fragments/filter-bar.html`
- Test: 通过 Task 3 的 WebMvcTest 渲染验证（组件本身不单独起 Spring 上下文）。

**Interfaces:**
- Produces: Thymeleaf fragment `filterBar(action, fields)`，命名空间 `bd-filter-bar-*`。消费方需提供 model 属性 `filterFields`（List<FilterField>）与 `filterBaseUrl`（String，以 `&` 结尾）。

- [ ] **Step 1: 创建组件模板**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="zh">
<head></head>
<body>
<div th:fragment="filterBar(action, fields)" class="bd-filter-bar">
    <style>
        .bd-filter-bar, .bd-filter-bar * { box-sizing: border-box; }
        .bd-filter-bar {
            display: flex; flex-wrap: wrap; gap: 10px; align-items: flex-end;
            padding: 14px 18px; margin-bottom: 16px;
            background: var(--bd-surface, #fff);
            border-radius: 8px;
            box-shadow: var(--bd-shadow, 0 2px 8px rgba(0,0,0,.08));
            font-family: var(--bd-font-sans, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif);
        }
        .bd-filter-bar-field { display: flex; flex-direction: column; gap: 3px; }
        .bd-filter-bar-field label {
            font-size: 11px; font-weight: 600;
            color: var(--bd-text-muted, #5f6368);
            text-transform: uppercase; letter-spacing: .04em;
        }
        .bd-filter-bar-field input, .bd-filter-bar-field select {
            padding: 6px 10px; border: 1px solid var(--bd-border-strong, #d1d5db);
            border-radius: 5px; font-size: 13px; font-family: inherit;
            color: var(--bd-text, #1a1a2e); background: var(--bd-surface, #fff);
            outline: none; min-width: 0;
        }
        .bd-filter-bar-field input:focus, .bd-filter-bar-field select:focus {
            border-color: var(--bd-accent, #e94560);
            box-shadow: 0 0 0 2px rgba(233,69,96,.15);
        }
        .bd-filter-bar-field input[type="text"] { width: 130px; }
        .bd-filter-bar-field input[type="number"] { width: 110px; }
        .bd-filter-bar-field select { min-width: 100px; }
        .bd-filter-bar-actions { display: flex; gap: 6px; margin-left: auto; flex-shrink: 0; }
        .bd-filter-bar-submit {
            padding: 6px 16px; background: var(--bd-accent, #e94560);
            color: var(--bd-accent-text, #fff); border: none; border-radius: 5px;
            font-size: 13px; font-weight: 600; cursor: pointer; font-family: inherit;
        }
        .bd-filter-bar-submit:hover { background: var(--bd-accent-hover, #c73652); }
        .bd-filter-bar-reset {
            padding: 6px 12px; background: var(--bd-surface-muted, #f3f4f6);
            color: var(--bd-text-muted, #5f6368); border: 1px solid var(--bd-border, #e5e7eb);
            border-radius: 5px; font-size: 13px; cursor: pointer; text-decoration: none;
            font-family: inherit;
        }
        .bd-filter-bar-reset:hover { background: #e5e7eb; color: var(--bd-text, #1a1a2e); }
        @media (max-width: 560px) {
            .bd-filter-bar-actions { margin-left: 0; width: 100%; }
            .bd-filter-bar-actions .bd-filter-bar-submit,
            .bd-filter-bar-actions .bd-filter-bar-reset { flex: 1; text-align: center; }
        }
    </style>
    <form method="get" th:action="${action}" class="bd-filter-bar-form">
        <th:block th:each="f : ${fields}">
            <div class="bd-filter-bar-field">
                <label th:for="${f.name}" th:text="${f.label}">标签</label>
                <input th:if="${f.type == 'TEXT'}" type="text" th:id="${f.name}" th:name="${f.name}"
                       th:value="${f.value}" th:placeholder="${f.placeholder}">
                <input th:if="${f.type == 'NUMBER'}" type="number" th:id="${f.name}" th:name="${f.name}"
                       th:value="${f.value}" min="1" th:placeholder="${f.placeholder}">
                <select th:if="${f.type == 'SELECT'}" th:id="${f.name}" th:name="${f.name}">
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
</body>
</html>
```

- [ ] **Step 2: 自检** — 确认命名空间全部为 `bd-filter-bar-*`；`filterBar(action, fields)` 参数名与消费方一致。
- [ ] **Step 3: 提交**

```bash
git add bytedepth-start/src/main/resources/templates/fragments/filter-bar.html
git commit -m "feat: 新增统一查询过滤组件 fragment（bd-filter-bar）"
```

---

### Task 3: 文章管理页过滤接入

**Files:**
- Modify: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/post/PostRepository.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/PostRepositoryImpl.java`
- Modify: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/post/AuthorPostRepository.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/AuthorPostRepositoryImpl.java`（若存在，先确认文件名）
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/post/query/ListAllPostsQryExe.java`
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminPostController.java`
- Modify: `bytedepth-start/src/main/resources/templates/admin/posts/list.html`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/admin/AdminPostControllerTest.java`
- Test: `bytedepth-app/src/test/java/manfred/bytedepth/app/post/query/ListAllPostsQryExeTest.java`

**Interfaces:**
- Consumes: `FilterField` / `FilterOption`（Task 1）。
- Produces:
  - `PostRepository.findPage(int page, int size, String title, String status, Long seriesId, Long categoryId)` + `countFiltered(...)`
  - `AuthorPostRepository.findPageByAuthorId(Long authorId, int page, int size, String title, String status, Long seriesId, Long categoryId)` + `countByAuthorIdFiltered(...)`
  - `ListAllPostsQryExe.execute(int page, int size, String title, String status, Long seriesId, Long categoryId)` / `executeByAuthor(authorId, page, size, ...)`（旧签名保留，重载）
  - `AdminPostController.list` 新增可选参数 `title/status/seriesId/categoryId`；model 增加 `filterFields`、`filterBaseUrl`。

**过滤字段（顺序）：** 标题(text, name=title)、状态(select, name=status, 选项: 全部/PUBLISHED已发布/DRAFT草稿/DELETED已删除)、专栏(select, name=seriesId, 选项来自 allSeries)、分类(select, name=categoryId, 选项来自 listCategoriesQryExe)。

- [ ] **Step 1: Repository 层过滤方法（先写实现，TDD 侧重 controller 与 query exe）**

`PostRepository` 接口新增：

```java
List<Post> findPage(int page, int size, String title, String status, Long seriesId, Long categoryId);
long countFiltered(String title, String status, Long seriesId, Long categoryId);
```

`PostRepositoryImpl` 实现（动态 wrapper，status 用 String 匹配列名）：

```java
private LambdaQueryWrapper<PostDO> filteredWrapper(String title, String status, Long seriesId, Long categoryId) {
    LambdaQueryWrapper<PostDO> w = new LambdaQueryWrapper<PostDO>()
            .ne(PostDO::getStatus, PostStatus.DELETED.name())
            .orderByDesc(PostDO::getCreatedAt);
    if (title != null && !title.isBlank()) w.like(PostDO::getTitle, title.trim());
    if (status != null && !status.isBlank()) w.eq(PostDO::getStatus, status);
    if (seriesId != null) w.eq(PostDO::getSeriesId, seriesId);
    if (categoryId != null) w.eq(PostDO::getCategoryId, categoryId);
    return w;
}

@Override
public List<Post> findPage(int page, int size, String title, String status, Long seriesId, Long categoryId) {
    return postMapper.selectPage(new Page<>(page, size), filteredWrapper(title, status, seriesId, categoryId))
            .getRecords().stream().map(this::toEntity).collect(Collectors.toList());
}

@Override
public long countFiltered(String title, String status, Long seriesId, Long categoryId) {
    return postMapper.selectCount(filteredWrapper(title, status, seriesId, categoryId));
}
```

`AuthorPostRepository` 接口新增同参数签名（拼接 authorId 约束），实现类参照 `findPageByAuthorId` 动态拼接。若实现类文件名未确认，用 `grep -rl "implements AuthorPostRepository" bytedepth-infrastructure` 定位。

- [ ] **Step 2: QueryExe 扩展（保留旧方法）**

`ListAllPostsQryExe` 新增重载：

```java
public PageResult execute(int page, int size, String title, String status, Long seriesId, Long categoryId) {
    List<PostDTO> posts = postRepository.findPage(page, size, title, status, seriesId, categoryId)
            .stream().map(this::toDTO).collect(Collectors.toList());
    long total = postRepository.countFiltered(title, status, seriesId, categoryId);
    return new PageResult(posts, total);
}
public PageResult executeByAuthor(Long authorId, int page, int size,
        String title, String status, Long seriesId, Long categoryId) {
    List<PostDTO> posts = authorPostRepository.findPageByAuthorId(authorId, page, size, title, status, seriesId, categoryId)
            .stream().map(this::toDTO).collect(Collectors.toList());
    long total = authorPostRepository.countByAuthorIdFiltered(authorId, title, status, seriesId, categoryId);
    return new PageResult(posts, total);
}
```

- [ ] **Step 3: Controller 扩展** — `AdminPostController.list`：

```java
@GetMapping
public String list(Authentication authentication, Model model,
                   @RequestParam(defaultValue = "1") int page,
                   @RequestParam(defaultValue = "20") int size,
                   @RequestParam(required = false) String title,
                   @RequestParam(required = false) String status,
                   @RequestParam(required = false) Long seriesId,
                   @RequestParam(required = false) Long categoryId) {
    boolean canManage = contentOwnershipGuard.canManagePosts(authentication);
    Long authorId = canManage ? null : contentOwnershipGuard.currentUserId(authentication);
    var result = canManage
            ? listAllPostsQryExe.execute(page, size, title, status, seriesId, categoryId)
            : listAllPostsQryExe.executeByAuthor(authorId, page, size, title, status, seriesId, categoryId);
    int totalPages = (int) Math.ceil((double) result.total() / size);
    model.addAttribute("posts", result.posts());
    model.addAttribute("currentPage", page);
    model.addAttribute("totalPages", totalPages);
    model.addAttribute("total", result.total());
    model.addAttribute("pageSize", size);
    model.addAttribute("allSeries", canManage ? seriesRepository.findAll() : seriesRepository.findByAuthorId(authorId));
    model.addAttribute("allCategories", listCategoriesQryExe.execute());

    List<FilterOption> statusOpts = List.of(
        FilterOption.of("", "全部"),
        FilterOption.of("PUBLISHED", "已发布", "PUBLISHED".equals(status)),
        FilterOption.of("DRAFT", "草稿", "DRAFT".equals(status)),
        FilterOption.of("DELETED", "已删除", "DELETED".equals(status)));
    List<FilterField> fields = new ArrayList<>();
    fields.add(FilterField.text("title", "标题", title == null ? "" : title, "输入关键字"));
    fields.add(FilterField.select("status", "状态", status == null ? "" : status, statusOpts));
    fields.add(FilterField.select("seriesId", "专栏", seriesId == null ? "" : String.valueOf(seriesId),
            allSeriesOptions(seriesId)));
    fields.add(FilterField.select("categoryId", "分类", categoryId == null ? "" : String.valueOf(categoryId),
            allCategoryOptions(categoryId)));
    model.addAttribute("filterFields", fields);

    StringBuilder b = new StringBuilder("/admin/posts?");
    if (title != null && !title.isBlank()) b.append("title=").append(title).append('&');
    if (status != null && !status.isBlank()) b.append("status=").append(status).append('&');
    if (seriesId != null) b.append("seriesId=").append(seriesId).append('&');
    if (categoryId != null) b.append("categoryId=").append(categoryId).append('&');
    model.addAttribute("filterBaseUrl", b.toString());
    return "admin/posts/list";
}

private List<FilterOption> allSeriesOptions(Long selectedId) {
    List<FilterOption> opts = new ArrayList<>();
    opts.add(FilterOption.of("", "全部"));
    // allSeries 已放入 model；此处基于 seriesRepository.findAll() 生成
    for (var s : seriesRepository.findAll()) opts.add(FilterOption.of(String.valueOf(s.getId()), s.getName(), s.getId().equals(selectedId)));
    return opts;
}
private List<FilterOption> allCategoryOptions(Long selectedId) {
    List<FilterOption> opts = new ArrayList<>();
    opts.add(FilterOption.of("", "全部"));
    for (var c : listCategoriesQryExe.execute()) opts.add(FilterOption.of(String.valueOf(c.getId()), c.getName(), c.getId().equals(selectedId)));
    return opts;
}
```

注意：为避免重复查询，`allSeriesOptions`/`allCategoryOptions` 应从已放入 model 的 `allSeries`/`allCategories` 取值（在 controller 内先查一次、复用）。实现时改为传入 List 生成 options。

- [ ] **Step 4: 模板接入组件 + 分页联动** — `admin/posts/list.html`：在 `.page-header` 之后插入：

```html
<div th:replace="~{fragments/filter-bar :: filterBar('/admin/posts', ${filterFields})}"></div>
```

分页 baseUrl 改为使用 `filterBaseUrl`：

```html
<div th:if="${totalPages > 1}">
    <div th:replace="~{fragments/pagination :: pagination(
        ${currentPage}, ${totalPages}, ${total}, ${pageSize}, ${filterBaseUrl})}"></div>
</div>
```

- [ ] **Step 5: 扩展 Controller 测试** — `AdminPostControllerTest` 新增用例：传入 `title=Spring&status=PUBLISHED` 时 verify `listAllPostsQryExe.execute(eq(1), eq(20), eq("Spring"), eq("PUBLISHED"), isNull(), isNull())`；model 含 `filterFields`（4 项）与 `filterBaseUrl`（含 title 与 status）。
- [ ] **Step 6: 扩展 QueryExe 测试** — `ListAllPostsQryExeTest` 新增：`execute(page,size,title,...)` 委托带过滤的 repository 方法并返回过滤后 total。
- [ ] **Step 7: 全量编译+测试**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn clean install -DskipTests -Dsort.skip=true`（先刷缓存，因多模块）
Run: `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn test -Dsort.skip=true`（跑相关测试）
Expected: 全部 PASS

- [ ] **Step 8: 提交**

```bash
git add -A && git commit -m "feat: 文章管理页接入统一过滤组件与状态/专栏/分类/标题过滤"
```

---

### Task 4: 评论管理页过滤接入 + 分页 UI 补齐

**Files:**
- Modify: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/comment/CommentRepository.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/comment/CommentRepositoryImpl.java`
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/comment/ListCommentsQryExe.java`
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminCommentController.java`
- Modify: `bytedepth-start/src/main/resources/templates/admin/comments/list.html`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/admin/AdminCommentControllerTest.java`（新建）
- Test: `bytedepth-app/src/test/java/manfred/bytedepth/app/comment/ListCommentsQryExeTest.java`

**Interfaces:**
- Consumes: `FilterField` / `FilterOption`（Task 1）、`pagination` fragment。
- Produces:
  - `CommentRepository.findAll(int page, int size, String authorName, Long postId)` + `countFiltered(authorName, postId)`
  - `ListCommentsQryExe.findAll(int page, int size, String authorName, Long postId)` 返回 `List<CommentDTO>`；新增 `count(authorName, postId)`（或返回带 total 的 record）
  - `AdminCommentController.list` 新增可选参数 `authorName/postId`；model 增加 `filterFields`、`filterBaseUrl`、`total`、`totalPages`。

**过滤字段（顺序）：** 作者名(text, name=authorName)、文章 ID(number, name=postId)。

- [ ] **Step 1: Repository 过滤** — `CommentRepository` 新增 `findAll(int page, int size, String authorName, Long postId)` 与 `countFiltered(...)`；`CommentRepositoryImpl` 用 `LambdaQueryWrapper`（authorName like、postId eq，`orderByDesc(createdAt)`）动态拼接。
- [ ] **Step 2: QueryExe 扩展** — `ListCommentsQryExe` 新增带过滤的 `findAll` 重载 + `count`；返回 `CommentDTO` 列表，slug 批量补齐逻辑复用现有。
- [ ] **Step 3: Controller 改造** — 新增可选参数 `authorName/postId`；计算 `total/totalPages`（PAGE_SIZE 保持默认 50）；构建 `filterFields`（作者名 text + 文章 ID number）与 `filterBaseUrl`。
- [ ] **Step 4: 模板接入** — `admin/comments/list.html`：`.comment-card` 列表上方插入 filter-bar；卡片下方新增分页（用 pagination fragment，`baseUrl=${filterBaseUrl}`）。保留导航与侧边栏。
- [ ] **Step 5: 测试** — 新建 `AdminCommentControllerTest`（参照 AdminPostControllerTest 的 @WebMvcTest 模式）：验证过滤参数透传、total/totalPages 计算、`filterFields`/`filterBaseUrl` 渲染、翻页 baseUrl 保留条件。
- [ ] **Step 6: 编译+测试** — `mvn clean install -DskipTests` → `mvn test`，全 PASS。
- [ ] **Step 7: 提交** — `git add -A && git commit -m "feat: 评论管理页接入统一过滤组件并补齐分页 UI"`

---

### Task 5: 分类管理页过滤 + 补父链（不引入分页）

**Files:**
- Modify: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/category/CategoryRepository.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/category/CategoryRepositoryImpl.java`
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/category/ListCategoriesQryExe.java`
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminCategoryController.java`
- Modify: `bytedepth-start/src/main/resources/templates/admin/categories/list.html`
- Test: `bytedepth-app/src/test/java/manfred/bytedepth/app/category/CategoryQueriesAndCommandsTest.java`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/admin/AdminCategoryControllerTest.java`（新建）

**Interfaces:**
- Consumes: `FilterField` / `FilterOption`（Task 1）。
- Produces:
  - `CategoryRepository.findByNameOrSlugLike(String keyword)` → `List<Category>`
  - `ListCategoriesQryExe.executeFiltered(String name, String slug)` → `List<CategoryDTO>`（先过滤，再**补父链**，保持树形）
  - `AdminCategoryController.list` 新增可选参数 `name/slug`；model 增加 `filterFields`、`filterBaseUrl`（无分页）。

**过滤字段（顺序）：** 名称(text, name=name)、Slug(text, name=slug)。

- [ ] **Step 1: Repository 过滤** — `CategoryRepository` 新增 `findByNameOrSlugLike(String keyword)`；实现用 `LambdaQueryWrapper` `(like(name) OR like(slug))`。
- [ ] **Step 2: QueryExe 补父链** — `ListCategoriesQryExe` 新增：

```java
public List<CategoryDTO> executeFiltered(String name, String slug) {
    boolean hasName = name != null && !name.isBlank();
    boolean hasSlug = slug != null && !slug.isBlank();
    if (!hasName && !hasSlug) return execute();   // 无过滤时走原树形
    List<Category> all = categoryRepository.findAll();
    // 匹配集合：按 name 或 slug 过滤
    String nm = hasName ? name.trim() : null;
    String sl = hasSlug ? slug.trim() : null;
    Set<Long> matched = all.stream()
        .filter(c -> (nm != null && c.getName().toLowerCase().contains(nm.toLowerCase()))
                  || (sl != null && c.getSlug().toLowerCase().contains(sl.toLowerCase())))
        .map(Category::getId).collect(Collectors.toSet());
    // 补父链：递归补上父级 id
    boolean changed = true;
    while (changed) {
        changed = false;
        for (Category c : all) {
            if (matched.contains(c.getId()) && c.getParentId().isPresent()) {
                if (matched.add(c.getParentId().get())) changed = true;
            }
        }
    }
    List<Category> kept = all.stream().filter(c -> matched.contains(c.getId())).collect(Collectors.toList());
    return toTreeDTO(kept);   // 复用 execute() 的树形组装逻辑（抽取私有方法）
}
```

注：将 `execute()` 中树形组装抽为私有 `toTreeDTO(List<Category>)`，`execute()` 与 `executeFiltered()` 共用。
- [ ] **Step 3: Controller 改造** — 新增可选参数 `name/slug`；`filterFields`（名称+Slug text）与 `filterBaseUrl`；model 用 `executeFiltered(name, slug)`。
- [ ] **Step 4: 模板接入** — `admin/categories/list.html`：在"所有分类"卡片上方插入 filter-bar。分类卡片内保留树形表格展示。
- [ ] **Step 5: 测试** — QueryExe 测试覆盖：无过滤走树形；过滤命中子分类时父链被补全；大小写不敏感；Slug 命中。Controller 测试覆盖过滤参数透传与 `filterFields` 渲染。
- [ ] **Step 6: 编译+测试** — 全 PASS。
- [ ] **Step 7: 提交** — `git add -A && git commit -m "feat: 分类管理页接入统一过滤组件（树形补父链，不引入分页）"`

---

### Task 6: 标签管理页过滤 + 新引入分页

**Files:**
- Modify: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/tag/TagRepository.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/tag/TagRepositoryImpl.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/tag/TagMapper.java`
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/tag/ListTagsQryExe.java`
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminTagListController.java`
- Modify: `bytedepth-start/src/main/resources/templates/admin/tags/list.html`
- Test: `bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/tag/TagRepositoryImplTest.java`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/admin/AdminTagListControllerTest.java`

**Interfaces:**
- Consumes: `FilterField` / `FilterOption`（Task 1）、`pagination` fragment。
- Produces:
  - `TagRepository.findPageWithCount(String name, int page, int size)` → `List<TagWithCount>` + `countWithName(String name)`
  - `TagMapper.findPageWithCount(name, offset, size)` / `countWithName(name)`（@Select 动态 SQL）
  - `ListTagsQryExe.findPageWithCount(String name, int page, int size)` 返回 `TagPageResult(records, total)`（record）
  - `AdminTagListController.list` 新增可选参数 `name` + `page/size`；model 增加 `filterFields`、`filterBaseUrl`、分页属性。

**过滤字段（顺序）：** 名称(text, name=name)。

- [ ] **Step 1: Mapper 分页过滤** — `TagMapper` 新增：

```java
@Select("<script>SELECT t.id, t.name, t.slug, COUNT(pt.post_id) AS post_count " +
        "FROM tag t LEFT JOIN post_tag pt ON t.id = pt.tag_id " +
        "LEFT JOIN post p ON pt.post_id = p.id AND p.status = 'PUBLISHED' " +
        "<where><if test='name != null and name != \"\"'>AND t.name LIKE CONCAT('%', #{name}, '%')</if></where> " +
        "GROUP BY t.id, t.name, t.slug ORDER BY post_count DESC LIMIT #{offset}, #{size}</script>")
List<TagWithCountDO> findPageWithCount(@Param("name") String name, @Param("offset") int offset, @Param("size") int size);

@Select("<script>SELECT COUNT(*) FROM (SELECT t.id FROM tag t " +
        "<where><if test='name != null and name != \"\"'>AND t.name LIKE CONCAT('%', #{name}, '%')</if></where>) tc</script>")
long countWithName(@Param("name") String name);
```

- [ ] **Step 2: Repository** — `TagRepository` 新增 `findPageWithCount(name, page, size)` + `countWithName(name)`；实现调用 mapper（`(page-1)*size` 转 offset）。
- [ ] **Step 3: QueryExe** — `ListTagsQryExe` 新增 `findPageWithCount`，定义 `record TagPageResult(List<TagDTO> records, long total)`。
- [ ] **Step 4: Controller 改造** — `list` 新增 `name/page/size`；计算 totalPages；`filterFields`（名称 text）；`filterBaseUrl`。
- [ ] **Step 5: 模板接入** — `admin/tags/list.html`：列表上方插入 filter-bar；下方新增 pagination fragment。保留现有操作（删除）。
- [ ] **Step 6: 测试** — Repository/QueryExe 测试覆盖分页与名称过滤；Controller 测试覆盖透传与渲染。
- [ ] **Step 7: 编译+测试** — 全 PASS。
- [ ] **Step 8: 提交** — `git add -A && git commit -m "feat: 标签管理页接入统一过滤组件并引入分页"`

---

### Task 7: 专栏管理页过滤 + 新引入分页（保留作者权限）

**Files:**
- Modify: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/series/SeriesRepository.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/series/SeriesRepositoryImpl.java`
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminSeriesListController.java`
- Modify: `bytedepth-start/src/main/resources/templates/admin/series/list.html`
- Test: `bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/series/SeriesRepositoryImplTest.java`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/admin/AdminSeriesListControllerTest.java`

**Interfaces:**
- Consumes: `FilterField` / `FilterOption`（Task 1）、`pagination` fragment。
- Produces:
  - `SeriesRepository.findPage(String name, int page, int size)` + `count(String name)`
  - `SeriesRepository.findPageByAuthorId(Long authorId, String name, int page, int size)` + `countByAuthorId(authorId, name)`
  - `AdminSeriesListController.list` 新增可选参数 `name/page/size`；model 增加 `filterFields`、`filterBaseUrl`、分页属性。保留 `ContentOwnershipGuard` 权限分支。

**过滤字段（顺序）：** 名称(text, name=name)。

- [ ] **Step 1: Repository** — `SeriesRepository` 新增 4 个方法；`SeriesRepositoryImpl` 用 `LambdaQueryWrapper`（name like，可选 authorId eq，`orderByAsc(name)`）+ `Page`。
- [ ] **Step 2: Controller 改造** — `list` 保留 `canManageSeries` 分支：

```java
@GetMapping
public String list(Authentication authentication, Model model,
                   @RequestParam(required = false) String name,
                   @RequestParam(defaultValue = "1") int page,
                   @RequestParam(defaultValue = "20") int size) {
    boolean canManage = contentOwnershipGuard.canManageSeries(authentication);
    var result = canManage
            ? new PageResult(seriesRepository.findPage(name, page, size), seriesRepository.count(name))
            : new PageResult(seriesRepository.findPageByAuthorId(contentOwnershipGuard.currentUserId(authentication), name, page, size),
                             seriesRepository.countByAuthorId(contentOwnershipGuard.currentUserId(authentication), name));
    int totalPages = (int) Math.ceil((double) result.total() / size);
    model.addAttribute("seriesList", result.records());
    model.addAttribute("currentPage", page);
    model.addAttribute("totalPages", totalPages);
    model.addAttribute("total", result.total());
    model.addAttribute("pageSize", size);
    model.addAttribute("filterFields", List.of(FilterField.text("name", "名称", name == null ? "" : name, "输入名称")));
    StringBuilder b = new StringBuilder("/admin/series?");
    if (name != null && !name.isBlank()) b.append("name=").append(name).append('&');
    model.addAttribute("filterBaseUrl", b.toString());
    return "admin/series/list";
}
```

（`PageResult` 为 controller 局部 record 或复用已有定义。）
- [ ] **Step 3: 模板接入** — `admin/series/list.html`：列表上方插入 filter-bar；下方新增 pagination fragment。保留创建/删除表单。
- [ ] **Step 4: 测试** — Repository 测试覆盖 name 过滤 + 分页 + authorId 约束；Controller 测试覆盖 canManage 分支与作者分支、过滤参数透传、`filterFields` 渲染。
- [ ] **Step 5: 编译+测试** — 全 PASS。
- [ ] **Step 6: 提交** — `git add -A && git commit -m "feat: 专栏管理页接入统一过滤组件并引入分页（保留作者权限）"`

---

### Task 8: 用户管理页过滤 + 新引入分页（扩展为全部用户）

**Files:**
- Modify: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/user/UserRepository.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/user/UserRepositoryImpl.java`
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/user/ListPendingUsersQryExe.java`（扩展，保留 execute() 兼容）
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminUserController.java`
- Modify: `bytedepth-start/src/main/resources/templates/admin/users/list.html`
- Test: `bytedepth-app/src/test/java/manfred/bytedepth/app/user/ListPendingUsersQryExeTest.java`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/admin/AdminUserControllerTest.java`

**Interfaces:**
- Consumes: `FilterField` / `FilterOption`（Task 1）、`pagination` fragment。
- Produces:
  - `UserRepository.findPage(String username, String status, int page, int size)` + `countFiltered(username, status)`
  - `ListPendingUsersQryExe.findPage(String username, String status, int page, int size)` 返回 `record UserPageResult(List<UserDTO> users, long total)`（保留 `execute()` 旧方法）
  - `AdminUserController.list` 新增可选参数 `username/status/page/size`；model 增加 `filterFields`、`filterBaseUrl`、分页属性、`users`（替代 `pendingUsers`）。

**过滤字段（顺序）：** 用户名(text, name=username)、状态(select, name=status, 选项: 全部/PENDING待审核/ACTIVE已激活/BANNED已封禁)。

- [ ] **Step 1: Repository** — `UserRepository` 新增 `findPage(username, status, page, size)` + `countFiltered(...)`；`UserRepositoryImpl` 用 `LambdaQueryWrapper`（username like、status eq、`orderByAsc(createdAt)`）+ `Page`。
- [ ] **Step 2: QueryExe** — `ListPendingUsersQryExe` 新增 `findPage(...)`（DTO 映射复用现有），定义 `record UserPageResult`；保留 `execute()` 供旧调用/测试。
- [ ] **Step 3: Controller 改造** — `list`：

```java
@GetMapping
@PreAuthorize("hasAuthority('system:user:approve')")
public String list(Model model,
                   @RequestParam(required = false) String username,
                   @RequestParam(required = false) String status,
                   @RequestParam(defaultValue = "1") int page,
                   @RequestParam(defaultValue = "20") int size) {
    var result = listPendingUsersQryExe.findPage(username, status, page, size);
    int totalPages = (int) Math.ceil((double) result.total() / size);
    model.addAttribute("users", result.users());
    model.addAttribute("currentPage", page);
    model.addAttribute("totalPages", totalPages);
    model.addAttribute("total", result.total());
    model.addAttribute("pageSize", size);
    model.addAttribute("filterFields", List.of(
        FilterField.text("username", "用户名", username == null ? "" : username, "输入用户名"),
        FilterField.select("status", "状态", status == null ? "" : status,
            List.of(FilterOption.of("", "全部"),
                    FilterOption.of("PENDING", "待审核", "PENDING".equals(status)),
                    FilterOption.of("ACTIVE", "已激活", "ACTIVE".equals(status)),
                    FilterOption.of("BANNED", "已封禁", "BANNED".equals(status)))));
    StringBuilder b = new StringBuilder("/admin/users?");
    if (username != null && !username.isBlank()) b.append("username=").append(username).append('&');
    if (status != null && !status.isBlank()) b.append("status=").append(status).append('&');
    model.addAttribute("filterBaseUrl", b.toString());
    return "admin/users/list";
}
```

- [ ] **Step 4: 模板改造** — `admin/users/list.html`：`pendingUsers` 迭代改为 `users`；列表上方插入 filter-bar；下方新增 pagination fragment。用户卡片需显示状态徽标（待审核/已激活/已封禁）。保留激活/删除/封禁操作。
- [ ] **Step 5: 测试** — Repository 测试覆盖 username like + status 过滤 + 分页；QueryExe 测试覆盖 `findPage` 与 `execute` 兼容；Controller 测试覆盖全部用户展示、状态过滤、分页、操作保留。
- [ ] **Step 6: 编译+测试** — 全 PASS。
- [ ] **Step 7: 提交** — `git add -A && git commit -m "feat: 用户管理页扩展为全部用户并接入统一过滤组件与分页"`

---

### Task 9: 访问日志迁移到统一组件

**Files:**
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminViewLogController.java`
- Modify: `bytedepth-start/src/main/resources/templates/admin/view-logs/list.html`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/admin/SimpleAdminControllerCoverageTest.java`

**Interfaces:**
- Consumes: `FilterField` / `FilterOption`（Task 1）。
- Produces: `AdminViewLogController.list` model 增加 `filterFields`（文章 ID number + 用户 ID number），`filterBaseUrl` 替代模板内 `th:with` 拼接。

- [ ] **Step 1: Controller 改造** — 保持 `postId/userId/page` 参数；构建 `filterFields`（文章 ID number(name=postId)、用户 ID number(name=userId)）与 `filterBaseUrl`（`/admin/view-logs?postId=..&userId=..&`）。
- [ ] **Step 2: 模板迁移** — 删除手写 `.filter-bar` 表单，替换为 `<div th:replace="~{fragments/filter-bar :: filterBar('/admin/view-logs', ${filterFields})}"></div>`；分页 baseUrl 改用 `filterBaseUrl`。
- [ ] **Step 3: 测试** — 更新 `SimpleAdminControllerCoverageTest`（或其覆盖 view-logs 的用例）验证过滤参数透传与 `filterFields`/`filterBaseUrl` 渲染；删除不再适用的手写 filter-bar 断言。
- [ ] **Step 4: 编译+测试** — 全 PASS。
- [ ] **Step 5: 提交** — `git add -A && git commit -m "refactor: 访问日志页迁移到统一过滤组件"`

---

### Task 10: 全量回归 + 覆盖率验证

**Files:**
- 无新增；全部为验证。

- [ ] **Step 1: 刷新缓存并全量测试**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn clean install -DskipTests -Dsort.skip=true`
Run: `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn clean test -Dsort.skip=true`
Expected: 全部模块测试 PASS，无编译错误。

- [ ] **Step 2: 覆盖率验证** — 对本次改动涉及的方法（Controller list 各过滤分支、QueryExe 过滤/补父链、Repository 过滤方法）确认测试覆盖 100% 业务分支。若项目配置了 jacoco，检查报告；否则人工核对新增测试断言覆盖每个 `if` 分支。
- [ ] **Step 3: 补充缺口测试** — 若发现未覆盖分支，补齐测试后重跑。
- [ ] **Step 4: 提交（如有测试补充）** — `git add -A && git commit -m "test: 补齐过滤组件相关分支覆盖"`

---

## 自审

**Spec 覆盖：**
- 组件 + 配置驱动 DTO → Task 1、2 ✅
- 文章/评论/分类/标签/专栏/用户/访问日志 7 页 → Task 3-9 ✅
- 分类补父链不引入分页 → Task 5 ✅
- 用户扩展全部用户+状态过滤 → Task 8 ✅
- 作者权限叠加过滤 → Task 3（文章）、7（专栏）✅
- 分页联动 baseUrl → 各 Task Controller 步骤 ✅
- 覆盖率 100% + 全量回归 → Task 10 ✅

**占位符扫描：** 无 TBD/TODO；Task 3/7 中有"若实现类文件名未确认用 grep 定位"、`PageResult` 为局部 record 或复用——这些是明确的可执行指引，非占位。补父链抽私有方法 `toTreeDTO` 明确声明。

**类型一致性：** `FilterField.text/number/select`、`FilterOption.of` 在所有 Task 中签名一致；Repository 新增方法签名在 Task 内自洽；`filterFields`/`filterBaseUrl` 命名全篇统一。
