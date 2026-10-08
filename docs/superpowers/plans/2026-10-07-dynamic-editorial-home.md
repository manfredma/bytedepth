# Dynamic Editorial Home Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将首页改为真实数据驱动的编辑部式入口，并统一公共页头/配色，同时严格保护非首页页面内容与交互。

**Architecture:** 在现有 Post 领域模型上增加推荐时间和推荐理由，沿用现有 `FeaturePostCmdExe`，由查询层提供主推与推荐列表；Thymeleaf 首页只消费已有查询模型。公共页头继续由 `fragments/nav.html` 单一来源提供，非首页只改共享样式。

**Tech Stack:** Java 25, Maven Wrapper 3.9.11, Spring Boot, MyBatis, Thymeleaf, CSS variables, Vitest.

**Spec:** `docs/superpowers/specs/2026-10-07-dynamic-editorial-home-design.md`

## Global Constraints

- 不在 `main` 开发；所有修改在 `feat/dynamic-editorial-home` worktree。
- 修改后先运行 `bash scripts/format-code.sh <paths>`，再运行 `bash scripts/format-check.sh <paths>`。
- 前端测试前必须运行 `npm ci --ignore-scripts --no-audit --no-fund`。
- 不执行仓库内 staging/production 发布、SSH、Tag 或远程命令。
- 非首页模板不改内容结构、Markdown/代码渲染、目录、评论、评分、阅读进度和专栏交互。
- 所有新增业务分支必须有测试，变更业务逻辑分支覆盖率保持 100%。

### Task 1: Persist editorial recommendation metadata

**Files:**

- Create: `bytedepth-start/src/main/resources/db/migration/V27__post_featured_metadata.sql`
- Modify: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/post/Post.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/PostDO.java`
- Modify: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/post/PostRepository.java`
- Test: existing domain and infrastructure post tests

**Interfaces:**

- `Post.featuredAt(): LocalDateTime?`, `Post.featuredReason(): String?`
- `PostRepository.findFeaturedPublished(): List<Post>`
- Existing `feature()` sets `featured=true` and `featuredAt=now`; `unfeature()` clears both and clears reason.

- [ ] Write failing domain tests for feature/unfeature metadata.
- [ ] Run `./mvnw -pl bytedepth-domain -am -Dtest=PostTest test` and confirm failure.
- [ ] Add fields, migration columns (`featured_at DATETIME NULL`, `featured_reason VARCHAR(500) NULL`), and domain transitions.
- [ ] Add repository mapping and query contract tests.
- [ ] Run focused tests and verify green.
- [ ] Commit `feat: add editorial recommendation metadata`.

### Task 2: Expose dynamic featured/recommended query data

**Files:**

- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/post/query/PostDTO.java`
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/post/query/ListPostsQryExe.java`
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/HomeController.java`
- Test: `HomeControllerTest.java`, `ListPostsQryExeTest.java`

**Interfaces:**

- `PostDTO.featuredAt`, `PostDTO.featuredReason`.
- `ListPostsQryExe.executeFeatured()` returns published featured posts in repository order.
- Home model attributes: `featuredPost` nullable and `recommendedPosts` max four.

- [ ] Write failing tests for featured ordering, empty fallback state, and max-four recommendation projection.
- [ ] Run focused tests and confirm failure.
- [ ] Implement DTO mapping, query executor, and controller model projection without changing existing discovery/latest/hot calls.
- [ ] Run focused tests and verify green.
- [ ] Commit `feat: expose dynamic homepage recommendations`.

### Task 3: Connect admin recommendation reason without changing non-home page content

**Files:**

- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminPostController.java`
- Modify: `bytedepth-start/src/main/resources/templates/admin/posts/list.html`
- Modify: `bytedepth-start/src/main/resources/static/css/admin-layout.css` only for the new recommendation form styling if needed
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/admin/AdminPostControllerTest.java`
- Test: `bytedepth-app/src/test/java/manfred/bytedepth/app/post/command/FeaturePostCmdExeTest.java`

**Interfaces:**

- Add `FeaturePostCmdExe` to `AdminPostController` and expose `POST /admin/posts/{id}/feature` with optional `featuredReason`.
- Add `POST /admin/posts/{id}/unfeature` and show the current recommendation state in `admin/posts/list.html`.
- Empty reason is normalized to `null`; unfeature clears metadata.
- No public article detail markup changes.

- [ ] Write failing command/controller tests for reason persistence and clearing.
- [ ] Run focused tests and confirm failure.
- [ ] Implement the smallest request/command extension using existing CSRF and permission boundaries.
- [ ] Run focused tests and verify green.
- [ ] Commit `feat: support editorial recommendation reasons`.

### Task 4: Rebuild homepage from real data

**Files:**

- Replace: `bytedepth-start/src/main/resources/templates/public/index.html`
- Create: `bytedepth-start/src/main/resources/static/css/home.css`
- Modify: homepage/template tests

**Interfaces:**

- Consume `featuredPost`, `recommendedPosts`, `posts`, `sort`, `total`, `allCategories`, `projects`, and existing pagination attributes.
- Preserve existing article URLs, sort query parameters, category links, project links, RSS, login/register and about/version navigation.

- [ ] Write failing MockMvc tests for dynamic featured title/reason, recommended list, empty state, and legacy entry points.
- [ ] Run tests and confirm failure.
- [ ] Implement editorial sections with no fake data and explicit empty states.
- [ ] Add small slogan under the homepage brand mark.
- [ ] Run focused controller/template tests and verify green.
- [ ] Commit `feat: build data-driven editorial homepage`.

### Task 5: Unify public header and palette without changing non-home content

**Files:**

- Modify: `bytedepth-start/src/main/resources/templates/fragments/nav.html`
- Modify: `bytedepth-start/src/main/resources/static/css/nav.css`
- Modify: `bytedepth-start/src/main/resources/static/css/theme.css` only if new tokens are required
- Test: `ThemeAssetsTest.java`

**Interfaces:**

- One shared public header fragment remains the source for all non-admin public pages.
- Admin calls keep their existing `navbar(false)` behavior; public calls keep auth/search/theme/RSS/about/version behavior.

- [ ] Write failing asset/template tests for shared subtitle, palette tokens, and unchanged nav entry points.
- [ ] Run focused test and confirm failure.
- [ ] Implement isolated header styles and token aliases; do not edit detail/columns/search/about/project templates.
- [ ] Run focused tests and verify green.
- [ ] Commit `feat: unify public header visual system`.

### Task 6: Format, quality, and scope verification

**Files:**

- Modify: `docs/releases/CHANGELOG.md`

- [ ] Add a categorized `## Unreleased` entry describing the homepage and public header change.
- [ ] Run `npm ci --ignore-scripts --no-audit --no-fund`.
- [ ] Run format-code and format-check on every changed file.
- [ ] Run `bash scripts/run-local-quality.sh`.
- [ ] Verify `git diff --name-only` contains no non-home public template or post-detail file.
- [ ] Run `git diff --check` and inspect final diff.
- [ ] Commit `chore: verify dynamic editorial home quality gates`.
