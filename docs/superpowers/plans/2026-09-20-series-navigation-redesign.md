# 专栏文章导航重设计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 让文章详情页在开头明确展示专栏上下文并可跳转任意文章，同时按专栏顺序提供轻量上一篇/下一篇导航。

**Architecture:** 应用层新增纯导航计算组件，消费已发布专栏文章列表并输出目录、当前位置、进度和相邻项；控制器只组装模型，Thymeleaf 模板负责展示上下文选择器与响应式底部导航。非专栏文章继续走现有全站发布时间导航，专栏文章不再调用全站上一篇/下一篇。

**Tech Stack:** Java 25、Spring MVC、Thymeleaf、JUnit 5/Mockito、Playwright E2E、现有 CSS 变量与静态资源约束。

**Spec:** `docs/superpowers/specs/2026-09-20-series-navigation-redesign-design.md`

## Global Constraints

- 不新增 Maven 模块；所有改动在 `feat/series-navigation-redesign` worktree。
- 前端测试前先执行 `npm ci --ignore-scripts --no-audit --no-fund`。
- 生产 Java 业务分支覆盖率必须 100%，所有单元、静态、E2E 和 staging 集成测试通过后才能验收。
- 专栏公开列表只包含 `PUBLISHED` 文章，排序稳定为 `series_order ASC, id ASC`。
- 任何运行时/用户可见改动都要在首次 staging 部署前补充 `CHANGELOG.md` 的分类 `Unreleased` 条目。

### Task 1: 建立导航计算模型（TDD）

**Files:**
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/series/SeriesNavigation.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/series/SeriesNavigationQryExe.java`
- Create: `bytedepth-app/src/test/java/manfred/bytedepth/app/series/SeriesNavigationQryExeTest.java`

**Interfaces:**
- Consumes: `SeriesRepository.findPublishedPostsBySeries(Long)` 和当前文章 ID。
- Produces: `SeriesNavigationQryExe.execute(Long seriesId, Long currentPostId)`，返回不可变导航记录，包含 `posts`、`position`、`total`、`previous`、`next` 和 `progressPercent`。

- [ ] **Step 1: Write failing tests** covering middle, first, last, single-item, series order gaps, deterministic ID tie-break, and current ID absent from published list.
- [ ] **Step 2: Run** `./mvnw -pl bytedepth-app -Dtest=SeriesNavigationQryExeTest test` and verify the missing type/behavior failure.
- [ ] **Step 3: Implement** a pure calculation that sorts by `seriesOrder` then ID, derives one-based position from list index, uses null previous/next at boundaries, and returns 0/100 progress for empty/single lists without division errors.
- [ ] **Step 4: Run** the focused test and verify all branches pass with no warnings.
- [ ] **Step 5: Commit** `feat(series): calculate ordered series navigation`.

### Task 2: 接入文章详情控制器（TDD）

**Files:**
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/PostController.java`
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/PostControllerSeriesDetailRenderingTest.java`
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/PostControllerTest.java`
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/PostControllerCoverageTest.java`

**Interfaces:**
- Consumes: `SeriesNavigationQryExe` and existing `SeriesRepository`.
- Produces: model attributes `seriesNavigation`, `series`, and `isSeriesPost`; non-series requests still receive `prevPost`/`nextPost` from `PostRepository`.

- [ ] **Step 1: Add failing MVC tests** asserting series requests expose navigation model and do not invoke global previous/next; non-series requests retain global navigation.
- [ ] **Step 2: Run** `./mvnw -pl bytedepth-start -Dtest='*PostController*' test` and verify failure because the new query is not wired.
- [ ] **Step 3: Inject** `SeriesNavigationQryExe`; when the current post has a series, load the series and navigation model, otherwise retain current global calls. Do not perform order calculations in the controller.
- [ ] **Step 4: Run** focused MVC tests and verify all boundary models pass.
- [ ] **Step 5: Commit** `feat(series): expose navigation model on post detail`.

### Task 3: 重做专栏详情页组件与桌面/移动导航样式（TDD）

**Files:**
- Modify: `bytedepth-start/src/main/resources/templates/public/posts/detail.html`
- Modify: `bytedepth-start/src/main/resources/static/css/public-posts.css` (or the existing public post component stylesheet that owns these styles)
- Delete or stop loading: `bytedepth-start/src/main/resources/static/js/series-navigation.js` if no other page uses it
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/ThemeAssetsTest.java` if asset ownership changes
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/PostControllerSeriesDetailRenderingTest.java`

**Interfaces:**
- Consumes: `seriesNavigation.posts`, `position`, `total`, `progressPercent`, `previous`, `next`, and `series.slug`.
- Produces: accessible top selector (`aria-expanded`, current item marker), compact PC bottom nav, enlarged mobile touch targets, and boundary action labels.

- [ ] **Step 1: Add failing template assertions** for selector label, current item, `第 N 篇 · 共 M 篇`, series-only adjacent links, boundary actions, and absence of the old fixed drawer markup.
- [ ] **Step 2: Run** the focused MVC/rendering tests and confirm they fail against the old template.
- [ ] **Step 3: Replace** the fixed left drawer with a self-contained top series context block and a small toggleable scrollable article list. Render compact bottom nav as a two-column strip; use media queries to increase mobile height and allow title wrapping.
- [ ] **Step 4: Run** focused rendering tests and inspect `git diff --check`.
- [ ] **Step 5: Commit** `feat(series): redesign article navigation UI`.

### Task 4: 补齐前端交互与 E2E 覆盖（TDD）

**Files:**
- Create or modify: `bytedepth-start/src/main/resources/static/js/series-navigation.js` only if the selector needs behavior; keep it scoped to the component.
- Modify: `bytedepth-frontend/tests/e2e/series-navigation.spec.js` (create if absent)
- Modify: `bytedepth-frontend/package.json` only if an existing script needs no new dependency

**Interfaces:**
- Consumes: semantic selector markup rendered by the detail template.
- Produces: keyboard/click toggle, current item focus, direct slug navigation, and responsive assertions.

- [ ] **Step 1: Add failing E2E cases** for opening the selector, jumping from the middle article to an arbitrary chapter, first/last boundary labels, and compact desktop versus touch-friendly mobile geometry.
- [ ] **Step 2: Run** `npm ci --ignore-scripts --no-audit --no-fund` then the focused Playwright spec against the current staging URL and verify expected failures.
- [ ] **Step 3: Implement** only component-local toggle and focus behavior; no global selectors or unrelated theme changes.
- [ ] **Step 4: Run** focused E2E locally only as feedback; staging remains the acceptance environment.
- [ ] **Step 5: Commit** `test(series): cover series navigation interactions`.

### Task 5: 文档、质量门禁与 staging 验收

**Files:**
- Modify: `docs/releases/CHANGELOG.md` with a categorized `## Unreleased` entry.
- Modify: `docs/engineering/frontend-patterns.md` with the series navigation rule and component ownership guidance.

- [ ] **Step 1: Run** `./mvnw clean install -DskipTests -Dsort.skip=true` then `./mvnw test` in the worktree.
- [ ] **Step 2: Run** `npm ci --ignore-scripts --no-audit --no-fund` and `bash scripts/run-local-quality.sh`; fix every failure or WARNING.
- [ ] **Step 3: Deploy** the feature branch with `./deploy/deploy-staging.sh feat/series-navigation-redesign`.
- [ ] **Step 4: Run** staging integration and all E2E suites on `https://staging-bytedepth.bytedepth.cn/`, verify the current candidate SHA, and inspect selector, boundaries, PC/mobile layout.
- [ ] **Step 5: Record** commit-bound staging evidence and report the staging acceptance result.
