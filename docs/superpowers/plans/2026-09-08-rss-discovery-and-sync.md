# RSS Discovery and Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose a standard, discoverable RSS 2.0 feed whose contents automatically track published and later-updated blog posts.

**Architecture:** Keep `FeedController` as the sole dynamic projection of published post state; sort items by the later of publication and update timestamps before limiting. Add the reader-facing link only through the reusable navigation and head fragments, then protect the contract with MVC, controller, template, JavaScript, and staging checks.

**Tech Stack:** Java 25, Spring Boot MVC, Thymeleaf, AssertJ/JUnit 5, Vitest, CSS.

**Spec:** `docs/superpowers/specs/2026-09-08-rss-discovery-and-sync-design.md`

## Global Constraints

- Work only on `feat/rss-navigation`; no direct `main` changes.
- Keep RSS 2.0 and return `application/rss+xml`; only published posts may be emitted.
- Do not add RSS state, a Maven module, a sync subcommand, dependencies, or a manual feed-refresh workflow.
- Use test-first red/green cycles. Every Java production branch touched must reach 100% line, branch, and method coverage.
- Use Java 25 for every Maven command and stop on any `WARNING`.
- Keep navigation CSS fully under `.nav-*`, reuse existing `--bd-*` tokens, preserve 940px navigation behavior, and use an accessible standard RSS SVG.
- Do not create a PR or merge until staging has been deployed and the project owner explicitly accepts the visual change.

---

### Task 1: Record the decision and its durable operating contract

**Files:**
- Create: `docs/architecture/decisions/0000-template.md`
- Create: `docs/architecture/decisions/0001-published-post-driven-rss.md`
- Create: `docs/architecture/decisions/README.md`
- Modify: `AGENTS.md`, `docs/README.md`, `docs/architecture/overview.md`, `docs/architecture/routes.md`, `docs/agent-guides/obsidian-sync.md`

**Interfaces:**
- Produces: ADR-0001 as the authority for dynamic RSS derivation; the sync guide states no separate RSS synchronization action exists.

- [ ] **Step 1: Document the architectural decision**

Create ADR-0001 in `Proposed` state using the new template. State that `FeedController` dynamically derives RSS from published post persistence state, that `max(publishedAt, updatedAt)` drives recency, and that static feed regeneration is rejected because it creates a second source of truth.

- [ ] **Step 2: Add discoverable ADR governance**

Create the ADR index and template, link the index from `docs/README.md` and `docs/architecture/overview.md`, and add the spec-before-ADR decision rule to `AGENTS.md`.

- [ ] **Step 3: Add the operational references**

Add `/feed.xml` to the public-route table. In the Obsidian sync guide, state that successful `import` publishes into RSS automatically, successful `sync`/`update` reprioritizes the updated published item automatically, and remote verification includes fetching the feed with the expected media type and target link.

- [ ] **Step 4: Validate documentation shape**

Run: `git diff --check && rg -n 'feed\.xml|ADR-0001|RSS' AGENTS.md docs`

Expected: no whitespace error; every new decision/documentation entry is reachable from an existing knowledge-base index.

- [ ] **Step 5: Commit the decision records**

```bash
git add AGENTS.md docs
git commit -m "docs: record RSS publication decision"
```

### Task 2: Make RSS recency and media type observable through tests

**Files:**
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/SimplePortalControllerCoverageTest.java`
- Create: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/FeedControllerWebMvcTest.java`

**Interfaces:**
- Consumes: `FeedController.feed()` and `GET /feed.xml`.
- Produces: failing tests that require latest-change ordering and an RSS-specific HTTP representation.

- [ ] **Step 1: Write the failing controller test for update recency**

Add a test with two real reconstructed published `Post` values: a newer publication and an older publication whose `updatedAt` is later. Assert the updated post's `<item>` begins before the newer post, and `lastBuildDate` plus its `pubDate` equal the update timestamp in `+0800` RFC-1123 form. Include an undated post to retain the omitted-date branch.

- [ ] **Step 2: Run the controller test to verify red**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-start -Dtest=SimplePortalControllerCoverageTest#feedOrdersByMostRecentPublicationOrUpdate test -Dsort.skip=true`

Expected: FAIL because the current controller limits input order before it sorts by the latest timestamp and chooses `publishedAt` ahead of `updatedAt`.

- [ ] **Step 3: Write the failing MVC representation test**

Use `@WebMvcTest(FeedController.class)` with a mocked `PostRepository` and property `bytedepth.site.url=https://example.test`. Request `/feed.xml` and assert status 200, `Content-Type` compatible with `application/rss+xml`, and an RSS body containing the published fixture link.

- [ ] **Step 4: Run the MVC test to verify red**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-start -Dtest=FeedControllerWebMvcTest test -Dsort.skip=true`

Expected: FAIL because the mapping currently declares `application/xml`.

### Task 3: Implement the dynamic RSS contract

**Files:**
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/FeedController.java`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/SimplePortalControllerCoverageTest.java`, `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/FeedControllerWebMvcTest.java`

**Interfaces:**
- Consumes: `PostRepository.findAllPublished(): List<Post>` and nullable `Post.getPublishedAt()/getUpdatedAt()`.
- Produces: `GET /feed.xml` as RSS 2.0 in descending `latestChange` order, up to 20 entries, with `application/rss+xml`.

- [ ] **Step 1: Implement only the code required by the red tests**

Change the mapping to `produces = "application/rss+xml"`. Replace the current stream limit with a sort by a nullable-safe `latestChange(Post)` helper, descending, followed by `limit(20)`. Reuse this helper for item and channel dates; it returns the later non-null timestamp or empty if both timestamps are absent. Preserve existing XML escaping and summary behavior.

- [ ] **Step 2: Run targeted Java tests to verify green**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-start -Dtest=SimplePortalControllerCoverageTest,FeedControllerWebMvcTest test -Dsort.skip=true`

Expected: PASS with no warning output.

- [ ] **Step 3: Commit the RSS behavior**

```bash
git add bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/FeedController.java bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal
git commit -m "feat: refresh RSS from published post changes"
```

### Task 4: Add visual and automatic RSS discovery through shared fragments

**Files:**
- Modify: `bytedepth-start/src/main/resources/templates/fragments/nav.html`
- Modify: `bytedepth-start/src/main/resources/templates/fragments/pwa-head.html`
- Modify: `bytedepth-start/src/main/resources/static/css/nav.css`
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/ThemeAssetsTest.java`
- Create: `bytedepth-start/src/test/js/rss-navigation.test.js`

**Interfaces:**
- Produces: an accessible `/feed.xml` link in the shared navigation and an `application/rss+xml` alternate link in the shared public head fragment.

- [ ] **Step 1: Write failing template and stylesheet tests**

Add Java resource assertions for an RSS alternate link in `pwa-head.html`, and add a Vitest resource test that reads the navigation/CSS assets and asserts: an anchor to `/feed.xml` carries `aria-label="订阅 RSS 更新"`; it contains an SVG with `aria-hidden="true"`; CSS selectors start with `.nav-rss`; and the existing `@media (max-width:940px)` rule remains present.

- [ ] **Step 2: Run the new test to verify red**

Run: `npm test -- rss-navigation.test.js`

Expected: FAIL because no RSS navigation asset exists.

- [ ] **Step 3: Implement the shared reader-facing entry points**

Add an inline standard RSS icon anchor after 「项目」 in `nav-primary`. Add a concise `.nav-rss` rule that matches the existing 36px navigation-control target, uses `currentColor`, and supplies `:hover`/`:focus-visible` states with navigation theme variables. Do not add a global selector or hard-coded orange. Add the alternate RSS `<link>` to `pwa-head.html`.

- [ ] **Step 4: Run front-end tests to verify green**

Run: `npm test -- rss-navigation.test.js`

Expected: PASS with no warning output.

- [ ] **Step 5: Commit the shared UI**

```bash
git add bytedepth-start/src/main/resources bytedepth-start/src/test/java/manfred/bytedepth/ThemeAssetsTest.java bytedepth-start/src/test/js/rss-navigation.test.js
git commit -m "feat: expose RSS in shared navigation"
```

### Task 5: Run full gates and hand off to staging

**Files:**
- Modify: `docs/releases/CHANGELOG.md` only if the repository's release process requires an unreleased entry before review.

**Interfaces:**
- Verifies: Java RSS behavior, Java production coverage, frontend tests/lint, deployment-script regression, and staging behavior.

- [ ] **Step 1: Remove the temporary visual-preview artifact**

Delete `work/rss-navigation-preview.html` and `work/rss-navigation-preview-standalone.html`; they were only for owner design review and are not project deliverables.

- [ ] **Step 2: Refresh Maven artifacts and run full Java tests**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn clean install -DskipTests -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn test -Dsort.skip=true
```

Expected: both commands exit 0 with no `WARNING`.

- [ ] **Step 3: Run the remaining quality gates**

Run:

```bash
npm test
npm run lint
bash scripts/verify-changed-coverage.sh
bash scripts/test-deploy-staging.sh
```

Expected: every command exits 0 with no warning output.

- [ ] **Step 4: Review the delivery state**

Run: `git status --short && git log --oneline origin/main..HEAD && git diff origin/main...HEAD --check`

Expected: only the planned, committed changes are ahead of `origin/main`; no whitespace error or residual prototype artifact remains.

- [ ] **Step 5: Push and stage the feature branch**

Run:

```bash
git push -u origin feat/rss-navigation
ssh -i ~/.ssh/ubuntu_2.pem ubuntu@124.221.143.25 "cd /opt/bytedepth && sudo ./deploy/deploy-staging.sh feat/rss-navigation"
```

Expected: staging deploys the feature ref. Then request project-owner acceptance of the desktop and narrow-screen RSS icon, automatic discovery, fresh publish, and updated published article behavior before creating a PR.
