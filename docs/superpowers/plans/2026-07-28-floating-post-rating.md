# 悬浮文章评分组件 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将文章页评分收敛为一个低打扰的悬浮胶囊，并仅在存在评分时展示平均分。

**Architecture:** 保持现有评分表单、查询 DTO 和 POST 接口不变。详情模板只保留一个 fragment，fragment 用现有 `ratingCount` 控制平均分输出；独立的 `post-rating-` CSS 负责桌面右下角与移动端底部居中布局。

**Tech Stack:** Spring Boot MVC、Thymeleaf、CSS、JUnit 5、MockMvc。

---

## File Structure

- Modify: `bytedepth-start/src/main/resources/templates/public/posts/detail.html` — 删除重复 fragment，保留唯一悬浮入口。
- Modify: `bytedepth-start/src/main/resources/templates/fragments/post-rating.html` — 输出紧凑标签、星级与条件化平均分。
- Modify: `bytedepth-start/src/main/resources/static/css/post-rating.css` — 自隔离的桌面/移动端悬浮样式及与既有浮动控件的层级关系。
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/PostControllerTest.java` — 保证详情页仍提供评分模型且只有唯一入口。
- Create: `bytedepth-start/src/test/java/manfred/bytedepth/templates/PostRatingFragmentTest.java` — 在 Thymeleaf 上下文中验证无评分/有评分文本输出。

### Task 1: 为评分摘要写失败模板测试

**Files:**
- Create: `bytedepth-start/src/test/java/manfred/bytedepth/templates/PostRatingFragmentTest.java`

- [ ] **Step 1: 写失败测试，描述无评分不输出摘要**

```java
@Test
void rendersNoAverageWhenThereAreNoRatings() {
    String html = render(new PostRatingDTO(0D, 0L, null));

    assertThat(html).doesNotContain("/ 5");
    assertThat(html).doesNotContain("成为第一个评分的人");
}
```

- [ ] **Step 2: 写失败测试，描述有评分显示一位小数**

```java
@Test
void rendersAverageWhenRatingsExist() {
    String html = render(new PostRatingDTO(4.64D, 12L, null));

    assertThat(html).contains("4.6 / 5");
}
```

- [ ] **Step 3: 执行测试并确认失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start test -Dsort.skip=true -Dtest=PostRatingFragmentTest`

Expected: FAIL，因为现有 fragment 会输出“成为第一个评分的人”，且平均分格式不符合新文案。

### Task 2: 收敛模板并实现条件得分展示

**Files:**
- Modify: `bytedepth-start/src/main/resources/templates/public/posts/detail.html:791,815`
- Modify: `bytedepth-start/src/main/resources/templates/fragments/post-rating.html`

- [ ] **Step 1: 删除顶部评分 fragment，保留唯一锚点**

删除正文后的调用，仅保留上下篇导航后的唯一调用，并将锚点统一为 `post-rating`：

```html
<div th:replace="~{fragments/post-rating :: rating(${post}, ${rating}, 'post-rating')}"></div>
```

- [ ] **Step 2: 将 fragment 改为紧凑的悬浮内容**

```html
<section th:fragment="rating(post, rating, anchor)" class="post-rating" th:id="${anchor}">
    <span class="post-rating-label">文章有帮助吗？</span>
    <form class="post-rating-stars" method="post" th:action="@{/posts/{slug}/rating(slug=${post.slug})}">
        <input th:if="${_csrf != null}" type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}">
        <button th:each="score : ${#numbers.sequence(1, 5)}" type="submit" name="score" th:value="${score}"
                class="post-rating-star" th:classappend="${rating.visitorScore != null and score <= rating.visitorScore} ? ' is-selected' : ''"
                th:title="${score} + ' 星'" th:text="${score <= (rating.visitorScore ?: 0)} ? '★' : '☆'">☆</button>
    </form>
    <span th:if="${rating.ratingCount > 0}" class="post-rating-average"
          th:text="${#numbers.formatDecimal(rating.averageRating, 1, 1)} + ' / 5'">4.6 / 5</span>
</section>
```

- [ ] **Step 3: 重新执行模板测试并确认通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start test -Dsort.skip=true -Dtest=PostRatingFragmentTest`

Expected: PASS。

- [ ] **Step 4: 提交模板变更**

```bash
git add bytedepth-start/src/main/resources/templates/public/posts/detail.html bytedepth-start/src/main/resources/templates/fragments/post-rating.html bytedepth-start/src/test/java/manfred/bytedepth/templates/PostRatingFragmentTest.java
git commit -m "feat: consolidate post rating entry"
```

### Task 3: 实现自隔离的悬浮样式与回归测试

**Files:**
- Modify: `bytedepth-start/src/main/resources/static/css/post-rating.css`
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/PostControllerTest.java`

- [ ] **Step 1: 写失败 MVC 断言，确保页面只含一个组件锚点**

在现有详情页测试的响应体断言中加入：

```java
.andExpect(content().string(containsString("id=\"post-rating\"")))
.andExpect(content().string(not(containsString("post-rating-top"))))
.andExpect(content().string(not(containsString("post-rating-end"))));
```

- [ ] **Step 2: 执行 MVC 测试并确认失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start test -Dsort.skip=true -Dtest=PostControllerTest`

Expected: FAIL，旧模板还会输出两个评分锚点。

- [ ] **Step 3: 写入命名空间隔离的响应式 CSS**

```css
.post-rating { position: fixed; right: 24px; bottom: 24px; z-index: 20; display: flex; align-items: center; gap: 10px; padding: 10px 14px; border: 1px solid var(--bd-border, #e7e4df); border-radius: 999px; background: var(--bd-surface, #fff); box-shadow: 0 8px 24px rgb(28 25 23 / 12%); }
.post-rating-label { font-size: 13px; font-weight: 600; white-space: nowrap; }
.post-rating-stars { display: flex; gap: 2px; }
.post-rating-star { padding: 0; border: 0; background: transparent; color: #c9c5bd; cursor: pointer; font-size: 22px; line-height: 1; }
.post-rating-star:hover, .post-rating-star.is-selected { color: var(--bd-accent, #e94560); }
.post-rating-average { color: var(--bd-text-subtle, #888); font-size: 12px; white-space: nowrap; }
@media (max-width: 768px) { .post-rating { right: auto; bottom: calc(16px + env(safe-area-inset-bottom)); left: 50%; transform: translateX(-50%); max-width: calc(100vw - 32px); } }
```

- [ ] **Step 4: 执行 MVC 与模板测试并确认通过**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start test -Dsort.skip=true -Dtest=PostControllerTest,PostRatingFragmentTest`

Expected: PASS。

- [ ] **Step 5: 提交样式与回归测试**

```bash
git add bytedepth-start/src/main/resources/static/css/post-rating.css bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/PostControllerTest.java
git commit -m "style: make post rating unobtrusive"
```

### Task 4: 完整验证

**Files:** 无新增文件。

- [ ] **Step 1: 刷新多模块缓存**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true`

Expected: `BUILD SUCCESS`。

- [ ] **Step 2: 执行所有测试**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -Dsort.skip=true`

Expected: `Failures: 0, Errors: 0`。
