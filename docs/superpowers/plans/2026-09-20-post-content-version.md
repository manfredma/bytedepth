# Article Content Version Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (recommended) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a lightweight `content_version` counter to posts, increment it only when title or Markdown content changes, and show `vN` on public article detail pages.

**Architecture:** Store the counter on the `post` aggregate and primary table. The domain object owns the comparison/increment rule; app DTOs expose the value; MyBatis maps it; Flyway initializes existing rows from timestamps. No snapshot table, diff API, rollback, or concurrency model change.

**Tech Stack:** Java 25, Spring Boot, MyBatis-Plus, Flyway, Thymeleaf, JUnit 5, AssertJ, Mockito, Maven Wrapper 3.9.11.

**Spec:** `docs/superpowers/specs/2026-09-20-post-content-version-design.md`

## Global Constraints

- Work only in the isolated `feat/post-content-version` worktree; never develop directly on `main`.
- Use `./mvnw` with Java 25 and the repository Maven wrapper; do not add a Maven module.
- Add unit tests for every changed business branch and keep changed Java coverage at 100% lines, branches, and methods.
- Add a non-empty categorized `## Unreleased` entry before the first staging deployment.
- Do not claim staging success until integration and E2E evidence match the deployed full SHA and contain no `WARNING`.

### Task 1: Record the architecture and release intent

**Files:**
- Create: `docs/architecture/decisions/0010-post-content-version.md`
- Modify: `docs/architecture/decisions/README.md`
- Create: `docs/superpowers/specs/2026-09-20-post-content-version-design.md`
- Modify: `docs/releases/CHANGELOG.md`

**Interfaces:**
- Produces the accepted storage decision and the `Unreleased` release metadata required by staging gates.

- [ ] **Step 1: Add the ADR, spec, and ADR index entry.**
- [ ] **Step 2: Add `### Added` under `## Unreleased` describing public article version metadata and content-change counting, plus a `### Compatibility` note describing the additive V24 migration and no history/rollback.**
- [ ] **Step 3: Check the documentation for placeholders and contradictory version semantics.**
- [ ] **Step 4: Commit:** `git add docs && git commit -m "docs: design article content versioning"`

### Task 2: Add the domain version invariant with tests

**Files:**
- Modify: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/post/Post.java`
- Test: `bytedepth-domain/src/test/java/manfred/bytedepth/domain/post/PostTest.java`

**Interfaces:**
- Produces `Post#getContentVersion()` and a domain `updateContent` rule that increments only for title/body changes.

- [ ] **Step 1: Add failing tests for create=`1`, title-only increment, body-only increment, and unchanged title/body preserving the version.**
- [ ] **Step 2: Run `./mvnw -pl bytedepth-domain -Dtest=PostTest test`; verify the new assertions fail before implementation.**
- [ ] **Step 3: Add `contentVersion`, initialize it in `create`, preserve it through the full reconstruct overload, and keep legacy reconstruct overloads defaulting to `1`.**
- [ ] **Step 4: Make `updateContent` compare title/body and increment only when either differs; preserve the existing timestamp behavior for a save operation.**
- [ ] **Step 5: Run the focused domain test and verify it passes.**
- [ ] **Step 6: Commit:** `git add bytedepth-domain && git commit -m "feat: track post content version in domain"`

### Task 3: Persist and migrate `content_version`

**Files:**
- Create: `bytedepth-start/src/main/resources/db/migration/V24__add_post_content_version.sql`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/PostDO.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/post/PostRepositoryImpl.java`
- Test: `bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/post/PostRepositoryImplTest.java`

**Interfaces:**
- Produces a non-null `post.content_version` column and bidirectional DO/entity mapping.

- [ ] **Step 1: Add repository mapping assertions for saving and reading a non-default version.**
- [ ] **Step 2: Run `./mvnw -pl bytedepth-infrastructure -Dtest=PostRepositoryImplTest test`; verify the new mapping assertions fail.**
- [ ] **Step 3: Add V24 with `INT NOT NULL DEFAULT 1`, then update existing rows to `2` when `updated_at <> created_at`, otherwise `1`.**
- [ ] **Step 4: Add `contentVersion` to `PostDO`, map it in `toDO`, and pass it into the full `Post.reconstruct` path in `toEntity`.**
- [ ] **Step 5: Run the focused infrastructure tests and verify they pass.**
- [ ] **Step 6: Commit:** `git add bytedepth-start/src/main/resources/db/migration/V24__add_post_content_version.sql bytedepth-infrastructure && git commit -m "feat: persist post content version"`

### Task 4: Expose the version through the application query model

**Files:**
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/post/query/PostDTO.java`
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/post/query/GetPostQryExe.java`
- Test: `bytedepth-app/src/test/java/manfred/bytedepth/app/post/query/GetPostQryExeTest.java`
- Test: `bytedepth-app/src/test/java/manfred/bytedepth/app/post/command/UpdatePostCmdExeTest.java`

**Interfaces:**
- Produces `PostDTO#getContentVersion()` for public detail rendering; existing update command continues to invoke the domain invariant.

- [ ] **Step 1: Add a query assertion that a reconstructed post version is copied to `PostDTO`.**
- [ ] **Step 2: Add update-command assertions for version increment on content change and no increment on unchanged content/category-only save.**
- [ ] **Step 3: Run the focused app tests and verify the new assertions fail before mapping/domain changes are present.**
- [ ] **Step 4: Add the DTO field and map `post.getContentVersion()` in `GetPostQryExe`.**
- [ ] **Step 5: Run the focused app tests and verify they pass.**
- [ ] **Step 6: Commit:** `git add bytedepth-app && git commit -m "feat: expose post content version"`

### Task 5: Render the version metadata on public article details

**Files:**
- Modify: `bytedepth-start/src/main/resources/templates/public/posts/detail.html`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/PostControllerTest.java` or the existing detail-rendering test that asserts the template model
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/ThemeAssetsTest.java` only if a static template contract is needed

**Interfaces:**
- Consumes `PostDTO.contentVersion`; renders a localized `版本 vN` item beside the existing article metadata without adding a new shared component or route.

- [ ] **Step 1: Add a template contract assertion for `版本 v` and `post.contentVersion`.**
- [ ] **Step 2: Run the focused start-module test and verify the assertion fails.**
- [ ] **Step 3: Add the metadata span in the existing `.meta` row, guarding against null only for legacy test fixtures.**
- [ ] **Step 4: Run the focused test and verify it passes.**
- [ ] **Step 5: Commit:** `git add bytedepth-start/src/main/resources/templates/public/posts/detail.html bytedepth-start/src/test && git commit -m "feat: show article content version"`

### Task 6: Run quality gates and staging acceptance

**Files:**
- Modify only files already listed above unless a failing contract test identifies a directly related documentation or test update.

**Interfaces:**
- Produces a clean feature branch, local quality evidence, staging integration evidence, and staging E2E evidence for the same deployed SHA.

- [ ] **Step 1: In the new worktree run `npm ci --ignore-scripts --no-audit --no-fund` before any frontend test/lint.**
- [ ] **Step 2: Run `bash scripts/run-local-quality.sh`; stop on any failure or `WARNING`.**
- [ ] **Step 3: Run `bash scripts/verify-changed-coverage.sh`; require 100% changed Java coverage.**
- [ ] **Step 4: Push `feat/post-content-version`, deploy that ref with `./deploy/deploy-staging.sh feat/post-content-version`, and ensure the full compose stack is rebuilt.**
- [ ] **Step 5: Run staging integration and E2E runners against `https://staging-bytedepth.bytedepth.cn/`; verify both result files have the exact deployed SHA, `result=passed`, and no `WARNING`.**
- [ ] **Step 6: Record the staging acceptance result and report the exact SHA and test counts; do not merge or create a production tag in this task unless the owner explicitly starts the release flow.**
