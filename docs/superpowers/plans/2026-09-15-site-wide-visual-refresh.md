# Site-Wide Visual Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 bytedepth 公开站点与后台迁移到统一的编辑型工程内容视觉系统，并让首页真实渲染主推内容、文章流和探索栏。

**Architecture:** 保留 Spring Boot + Thymeleaf 服务端渲染和原生 JavaScript。共享导航、令牌和页面骨架通过独立 fragment/CSS 组件复用；首页新增最小的 featured 查询能力，既有 discovery feed、权限、搜索、主题和 staging 行为保持不变。

**Tech Stack:** Spring Boot, Thymeleaf, Java 25, vanilla CSS/JavaScript, Vitest, Playwright.

**Spec:** `docs/superpowers/specs/2026-09-15-site-wide-visual-refresh-design.md`

## Global Constraints

- 不引入 React、Vue、Vite、Tailwind 或新的 Maven 模块。
- 所有代码改动在 `feat/site-wide-visual-refresh` worktree 完成，不能直接写 `main`。
- 新建或切换 worktree 后，前端测试前先执行 `npm ci --ignore-scripts --no-audit --no-fund`。
- 本机只运行纯单元和静态检查；集成、E2E、视觉验收只在 staging 执行。
- 不得忽略任何 WARNING；发布前运行完整质量脚本和 staging checklist。
- 每个新业务分支先写失败测试，观察失败后再实现。

### Task 1: Expose the featured post query

**Files:**
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/post/query/PostDTO.java`
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/post/query/ListPostsQryExe.java`
- Modify: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/post/PostRepository.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/PostRepositoryImpl.java`
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/HomeController.java`
- Test: corresponding app, infrastructure, and `HomeControllerTest` test files

**Interfaces:** Add `PostDTO.featured`, a `ListPostsQryExe.executeFeatured()` query returning the newest published featured post (or empty), and expose it as `featuredPost` in the home model. Preserve the existing discovery-feed query signatures.

- [ ] Write tests for featured selection, empty fallback, and controller model exposure.
- [ ] Run the focused Java tests and confirm they fail because the query/model is absent.
- [ ] Implement repository query, DTO mapping, query executor, and controller model attribute.
- [ ] Run focused tests again and confirm they pass without WARNING.
- [ ] Commit `feat: expose featured post for homepage`.

### Task 2: Establish shared visual tokens and page shell

**Files:**
- Modify: `bytedepth-start/src/main/resources/static/css/theme.css`
- Create: `bytedepth-start/src/main/resources/static/css/site-shell.css`
- Create: `bytedepth-start/src/main/resources/static/css/page-header.css`
- Modify: `bytedepth-start/src/main/resources/templates/fragments/nav.html`
- Modify: `bytedepth-start/src/main/resources/static/css/nav.css`
- Test: `bytedepth-start/src/test/js/theme-switcher.test.js` and resource-ownership checks

**Interfaces:** Keep `navbar(showThemeSwitcher)` unchanged. Add namespaced `bd-shell-*` and `bd-page-header-*` selectors and tokens consumed by public and admin templates.

- [ ] Add tests/assertions for theme token presence, shared nav links, auth links, and staging marker.
- [ ] Run focused tests and confirm the new assertions fail.
- [ ] Implement tokens, compact responsive nav, and shared shell/header styles while preserving all existing nav behavior.
- [ ] Run focused tests and lint; resolve every warning.
- [ ] Commit `feat: establish shared site visual system`.

### Task 3: Rebuild the real homepage

**Files:**
- Modify: `bytedepth-start/src/main/resources/templates/public/index.html`
- Create: `bytedepth-start/src/main/resources/static/css/home.css`
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/HomeControllerTest.java`

**Interfaces:** Render the existing `featuredPost`, `posts`, `sort`, pagination, `allCategories`, and `projects` model values. Keep `/`, `/?sort=latest`, `/?sort=hot`, category links, login/register links, and pagination URLs unchanged.

- [ ] Add failing MVC/content assertions for non-empty hero, featured fallback, login link, removal of the top category wall, and discovery tabs.
- [ ] Run `HomeControllerTest` and confirm the assertions fail against the old template.
- [ ] Implement stable brand hero using existing `logo.svg`, featured article block, two-column article/explorer layout, and mobile stacking. Remove the broken random slogan script and all page-level global/inline styles.
- [ ] Run `HomeControllerTest`, inspect rendered markup, and run frontend lint.
- [ ] Commit `feat: rebuild homepage editorial layout`.

### Task 4: Migrate public page shells and content components

**Files:**
- Modify: all public templates under `bytedepth-start/src/main/resources/templates/public/`
- Create/modify: `post-list.css`, `topic-explorer.css`, `project-panel.css`
- Modify: public page-specific CSS assets as needed
- Test: public template/resource ownership checks and existing JS tests

**Interfaces:** Keep existing controller model names, URLs, security conditions, pagination fragment parameters, annotation hooks, and post-reading hooks unchanged.

- [ ] Add static checks proving public templates load shared shell styles and no page introduces global reset or cross-component selectors.
- [ ] Run checks and confirm current inline/global violations are detected.
- [ ] Migrate posts, columns, projects, search, about, releases, profile, network, login, and register pages to the shared shell and typography; retain page-specific content structure.
- [ ] Run all pure frontend tests and lint; fix warnings and selector leakage.
- [ ] Commit `feat: align public pages with shared visual system`.

### Task 5: Migrate admin workbench styling

**Files:**
- Modify: `bytedepth-start/src/main/resources/static/css/admin-layout.css`
- Create: `bytedepth-start/src/main/resources/static/css/admin-components.css`
- Modify: admin templates under `bytedepth-start/src/main/resources/templates/admin/`
- Test: template/resource ownership checks and existing admin MVC tests

**Interfaces:** Preserve all admin forms, CSRF fields, permission conditions, filter-bar fragment calls, pagination calls, dialog attributes, and editor hooks.

- [ ] Add failing static checks for admin token usage, compact workbench regions, and absence of public-home decoration classes.
- [ ] Run checks and confirm they fail before migration.
- [ ] Implement consistent admin headers, controls, tables, forms, empty states, and responsive behavior using the shared tokens.
- [ ] Run pure tests and lint; resolve every warning.
- [ ] Commit `feat: align admin workbench with shared visual system`.

### Task 6: Full verification and staging handoff

**Files:**
- Modify: `tests/e2e/` only where selectors need to reflect stable semantic markup
- Modify: `docs/superpowers/specs/2026-09-15-site-wide-visual-refresh-design.md` only if verified behavior changes

- [ ] Run `npm ci --ignore-scripts --no-audit --no-fund`.
- [ ] Run `bash scripts/run-local-quality.sh` and stop on any WARNING or failure.
- [ ] Run the required Maven cache refresh and test commands with the repository wrapper and Java 25.
- [ ] Deploy the candidate ref using `deploy/deploy-staging.sh feat/site-wide-visual-refresh`.
- [ ] Run complete staging integration and E2E suites at desktop, tablet, 320px, and 375px widths, in light/dark themes and authenticated/anonymous states.
- [ ] Record integration and E2E evidence against the candidate full SHA and obtain owner acceptance before any merge.
