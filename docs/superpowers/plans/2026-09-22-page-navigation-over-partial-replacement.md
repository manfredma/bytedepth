# Page-Level Navigation Over Partial Replacement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline execution) to implement this plan task-by-task.

**Goal:** Replace article-level partial DOM navigation with native full-page navigation and encode the boundary in project documentation and tests.

**Architecture:** Article links remain ordinary server-rendered anchors. Component-local data refreshes remain unchanged. The page lifecycle is owned by a full document load so all article scripts initialize consistently.

**Tech Stack:** Thymeleaf, vanilla JavaScript, Vitest, Playwright, Markdown ADR documentation.

**Spec:** `docs/superpowers/specs/2026-09-22-page-navigation-over-partial-replacement-design.md`

## Global Constraints

- Do not modify `main`; work only in the isolated `fix/series-navigation-full-reload` worktree.
- Use repository Maven Wrapper and Java 25 for Maven commands.
- Run `npm ci --ignore-scripts --no-audit --no-fund` before frontend tests or Playwright.
- Component-local DOM/data refreshes remain allowed; page-level article replacement is prohibited.
- All user-visible behavior changes require the non-empty `docs/releases/CHANGELOG.md` `## Unreleased` entry already added in this worktree.

### Task 1: Lock the navigation boundary with a failing unit test

**Files:**
- Create: `bytedepth-start/src/test/js/series-navigation.test.js`

- [ ] **Step 1: Write the failing test**

  Assert that the article detail template contains series links but does not load the page-level `series-navigation.js` interceptor.

- [ ] **Step 2: Run the targeted test**

  Run: `npx vitest run bytedepth-start/src/test/js/series-navigation.test.js`

  Expected: FAIL because the current detail template still references `series-navigation.js`.

### Task 2: Remove the page-level interceptor

**Files:**
- Modify: `bytedepth-start/src/main/resources/templates/public/posts/detail.html`
- Delete: `bytedepth-start/src/main/resources/static/js/series-navigation.js`

- [ ] **Step 1: Remove the script reference and interceptor implementation**
- [ ] **Step 2: Run the targeted unit test**

  Run: `npx vitest run bytedepth-start/src/test/js/series-navigation.test.js`

  Expected: PASS with zero failures and no warnings.

### Task 3: Add the navigation regression E2E

**Files:**
- Modify: `tests/e2e/series-navigation.spec.js`

- [ ] **Step 1: Add a test that waits for a real document navigation**

  The test must enter a series article, open the sidebar, click a non-current `.series-item`, await `page.waitForNavigation({waitUntil: 'domcontentloaded'})`, and assert the target URL and article content.

- [ ] **Step 2: Run the test only against staging**

  Run from the repository's staging runner: `E2E_BASE_URL=https://staging-bytedepth.bytedepth.cn/ npm run test:e2e -- tests/e2e/series-navigation.spec.js`

  Expected: all applicable desktop/mobile series navigation tests pass with no warnings.

### Task 4: Run verification gates

**Files:**
- Verify: ADR, frontend pattern rule, changelog, unit test, E2E test and source diff.

- [ ] **Step 1: Run frontend unit tests and lint**
- [ ] **Step 2: Run `bash scripts/run-local-quality.sh`**
- [ ] **Step 3: Run `bash scripts/check-staging-checklist.sh` before staging deployment**
- [ ] **Step 4: Deploy the candidate to staging and run full staging integration/E2E verification**
