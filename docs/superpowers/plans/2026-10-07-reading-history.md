# Reading History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with verification checkpoints.

**Goal:** Add personal article reading history and article-level reading summaries, with server persistence for authenticated readers and versioned local-only storage for anonymous readers.

**Architecture:** Add a small reading-history domain/application boundary separate from `post_view_log`. Authenticated browser events are accepted idempotently into a seven-day event table and asynchronously projected into a long-lived user/article summary; anonymous events and summaries use a browser adapter with a 7-day event queue and 1,000-entry LRU. The detail page and history page render one shared summary/history component while switching data source immediately on authentication changes.

**Tech Stack:** Java 25, Maven Wrapper, Spring Boot MVC/Security, MyBatis, Flyway, Thymeleaf, vanilla JavaScript, Vitest, JUnit/MockMvc.

**Spec:** `docs/superpowers/specs/2026-10-07-reading-history-design.md`

## Global Constraints

- Do not change `post_view_log` PV semantics or mix personal reading history with site analytics.
- Anonymous events must never be uploaded or automatically migrated into an account.
- Server identity comes only from the authenticated security context; no client `userId` is accepted.
- Raw authenticated events and anonymous local events retain seven days; anonymous summaries retain the latest 1,000 articles by LRU.
- Server projection must be idempotent by `eventId`, replayable, and target five-second visibility.
- Every changed business branch needs unit coverage; integration and E2E verification runs in staging through release-platform.
- Use `./mvnw`; before frontend commands in this worktree, run `npm ci --ignore-scripts --no-audit --no-fund`.

---

### Task 1: Add domain contracts and pure projection rules

**Files:**

- Create: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/reading/ReadingEventType.java`
- Create: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/reading/ReadingEvent.java`
- Create: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/reading/ReadingSummary.java`
- Create: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/reading/ReadingHistoryEntry.java`
- Create: `bytedepth-domain/src/main/java/manfred/bytedepth/domain/reading/ReadingHistoryProjector.java`
- Test: `bytedepth-domain/src/test/java/manfred/bytedepth/domain/reading/ReadingHistoryProjectorTest.java`

**Interfaces:**

- `ReadingEvent(UUID eventId, Long userId, Long postId, UUID sessionId, ReadingEventType type, int activeSecondsDelta, int maxScrollDepth, Instant occurredAt, Instant receivedAt)` is immutable and contains no network metadata.
- `ReadingSummary(Long userId, Long postId, long readCount, long totalActiveSeconds, Instant firstReadAt, Instant lastReadAt)` is the long-lived projection value.
- `ReadingHistoryProjector.apply(ReadingSummary current, ReadingEvent event)` returns the next summary; `READ_OPEN` increments count, all event types add only their non-negative delta, and timestamps use `receivedAt`.

- [ ] **Step 1: Write failing projection tests** for open count, delta accumulation, first/last timestamps, and idempotency input rejection.
- [ ] **Step 2: Run** `./mvnw -pl bytedepth-domain -Dtest=ReadingHistoryProjectorTest test`; expect failure because the reading package does not exist.
- [ ] **Step 3: Implement** the records, enum, validation, and pure projector with no Spring dependencies.
- [ ] **Step 4: Re-run** the focused domain test and then `./mvnw -pl bytedepth-domain test`; expect PASS with no warnings.
- [ ] **Step 5: Commit** `git add bytedepth-domain && git commit -m "feat: add reading history domain model"`.

### Task 2: Add Flyway schema and infrastructure ports

**Files:**

- Create: `bytedepth-start/src/main/resources/db/migration/V27__add_reading_history.sql`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/reading/ReadingEventPort.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/reading/ReadingHistoryPort.java`
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/reading/ReadingEventDO.java`
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/reading/ReadingHistoryDO.java`
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/reading/ReadingHistoryMapper.java`
- Create: `bytedepth-infrastructure/src/main/resources/mapper/ReadingHistoryMapper.xml`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/infrastructure/reading/ReadingHistoryMapperContractTest.java`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/FlywayMigrationContractTest.java`

**Interfaces:**

- `ReadingEventPort.insertIfAbsent(ReadingEvent event)`, `findUnprojected(int limit)`, `markProjected(long eventRowId, Instant projectedAt)`, and `deleteProjectedBefore(Instant cutoff)`.
- `ReadingHistoryPort.upsert(ReadingEvent event)`, `findByUserAndPost(long userId, long postId)`, and `findPageByUser(long userId, String cursor, int limit)`.
- SQL uses unique `event_id`, `(user_id, post_id)` primary key, pending-event and retention indexes, `ON DELETE CASCADE` from the existing user row, and no IP/User-Agent/Referer columns.

- [ ] **Step 1: Write failing migration/SQL contract tests** asserting both tables, unique/event indexes, the seven-day cleanup column, and absence of analytics identity fields.
- [ ] **Step 2: Run** the focused tests; expect failure because V27 and mapper resources do not exist.
- [ ] **Step 3: Add** the migration, immutable persistence records, port signatures, MyBatis mapper, and XML upsert/page SQL.
- [ ] **Step 4: Run** the focused contract tests and infrastructure compilation; expect PASS.
- [ ] **Step 5: Commit** `git add bytedepth-start/src/main/resources/db/migration/V27__add_reading_history.sql bytedepth-app/src/main/java/manfred/bytedepth/app/reading bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/reading bytedepth-infrastructure/src/main/resources/mapper bytedepth-start/src/test/java/manfred/bytedepth/infrastructure/reading bytedepth-start/src/test/java/manfred/bytedepth/FlywayMigrationContractTest.java && git commit -m "feat: persist reading history events"`.

### Task 3: Implement authenticated event intake and async projection

**Files:**

- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/reading/RecordReadingEventCmdExe.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/reading/ProjectReadingEventsJob.java`
- Create: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/ReadingEventController.java`
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/security/SecurityConfig.java`
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/ratelimit/RateLimitFilter.java`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/ReadingEventControllerTest.java`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/app/reading/ProjectReadingEventsJobTest.java`

**Interfaces:**

- `POST /posts/{slug}/reading-events` accepts `{eventId, sessionId, type, activeSecondsDelta, maxScrollDepth, occurredAt}` and obtains the user ID from `SecurityUtils.currentUser()`.
- `RecordReadingEventCmdExe.execute(long userId, String slug, ReadingEventRequest request)` validates article access and returns duplicate-safe acceptance.
- `ProjectReadingEventsJob.runOnce()` claims a bounded batch, applies each event exactly once in a transaction, and exposes pending age/count in logs.

- [ ] **Step 1: Write failing MockMvc tests** for unauthenticated 401, missing client user ID, validation 400, authenticated 202, duplicate 202, and event insertion using security context identity.
- [ ] **Step 2: Write failing projector-job tests** for atomic upsert/mark, retry after transaction failure, and no deletion of unprojected expired events.
- [ ] **Step 3: Run** focused tests; expect failure because controller, command executor, and job do not exist.
- [ ] **Step 4: Implement** controller/security/rate-limit integration, command validation, transaction-scoped projection, seven-day purge guard, and structured logs.
- [ ] **Step 5: Run** focused tests and `./mvnw -pl bytedepth-start -Dtest='*ReadingEvent*' test`; expect PASS without WARNING output.
- [ ] **Step 6: Commit** `git add bytedepth-app/src/main/java/manfred/bytedepth/app/reading bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/ReadingEventController.java bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/security/SecurityConfig.java bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/ratelimit/RateLimitFilter.java bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/ReadingEventControllerTest.java bytedepth-start/src/test/java/manfred/bytedepth/app/reading/ProjectReadingEventsJobTest.java && git commit -m "feat: accept and project authenticated reading events"`.

### Task 4: Add authenticated summary/history queries and page routes

**Files:**

- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/reading/GetReadingSummaryQryExe.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/reading/ListReadingHistoryQryExe.java`
- Create: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/ReadingHistoryController.java`
- Create: `bytedepth-start/src/main/resources/templates/public/reading-history.html`
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/PostController.java`
- Modify: `bytedepth-start/src/main/resources/templates/fragments/nav.html`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/ReadingHistoryControllerTest.java`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/PostControllerReadingSummaryTest.java`

**Interfaces:**

- `GET /reading-history` renders 20 server-paged entries for authenticated readers and a local-storage container for anonymous readers.
- `GET /posts/{slug}/reading-summary` is not a public cross-user endpoint; the detail model receives the current user’s summary from `GetReadingSummaryQryExe`.
- `GET /reading-history/available-posts?slugs=...` returns only currently published, public post metadata for anonymous validation, with bounded slug count.

- [ ] **Step 1: Write failing controller/template tests** for authenticated-only data, anonymous empty shell, 20-item cursor ordering, current-user-only summary, and unavailable-post filtering.
- [ ] **Step 2: Run** the focused tests; expect missing route/template/query failures.
- [ ] **Step 3: Implement** current-user query executors, stable cursor pagination, public availability batch, nav link, history template, and detail-model summary attributes.
- [ ] **Step 4: Run** focused MockMvc/template tests and existing post controller tests; expect PASS.
- [ ] **Step 5: Commit** `git add bytedepth-app/src/main/java/manfred/bytedepth/app/reading bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal bytedepth-start/src/main/resources/templates/public/reading-history.html bytedepth-start/src/main/resources/templates/fragments/nav.html bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal && git commit -m "feat: add reading history queries and page"`.

### Task 5: Replace the timer transport with a shared event client

**Files:**

- Create: `bytedepth-start/src/main/resources/static/js/reading-history-store.js`
- Modify: `bytedepth-start/src/main/resources/static/js/post-reading.js`
- Modify: `bytedepth-start/src/main/resources/templates/public/posts/detail.html`
- Create: `bytedepth-start/src/main/resources/static/css/reading-history.css`
- Test: `tests/unit/reading-history-store.test.js`
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/PostReadingAssetsTest.java`

**Interfaces:**

- `ReadingHistoryStore` exposes `recordOpen`, `recordHeartbeat`, `recordComplete`, `recordClose`, `getSummary`, `listLocalHistory`, and `flushAuthenticatedOutbox`.
- Storage keys are `bytedepth.reading-events.v1` and `bytedepth.reading-history.v1`; all localStorage operations catch quota/private-mode/JSON errors.
- Authenticated event POSTs use the existing CSRF token and `/posts/{slug}/reading-events`; anonymous mode never invokes fetch/sendBeacon for reading events.

- [ ] **Step 1: Write failing Vitest tests** for open/count, heartbeat delta, 7-day event pruning, 1,000-entry LRU, malformed storage reset, storage exception degradation, login/logout source switch, and authenticated retry with the same event ID.
- [ ] **Step 2: Run** `npm test -- tests/unit/reading-history-store.test.js`; expect failure because the store does not exist.
- [ ] **Step 3: Implement** the store and adapt the existing 60-second activity/15-second settlement timer to emit delta events, preserving completion and pagehide behavior.
- [ ] **Step 4: Run** the focused Vitest test and existing frontend tests; expect PASS and no console warnings.
- [ ] **Step 5: Commit** `git add bytedepth-start/src/main/resources/static/js/reading-history-store.js bytedepth-start/src/main/resources/static/js/post-reading.js bytedepth-start/src/main/resources/templates/public/posts/detail.html bytedepth-start/src/main/resources/static/css/reading-history.css tests/unit/reading-history-store.test.js bytedepth-start/src/test/java/manfred/bytedepth/PostReadingAssetsTest.java && git commit -m "feat: track local and authenticated reading events"`.

### Task 6: Render the shared summary/history UI and accessibility states

**Files:**

- Create: `bytedepth-start/src/main/resources/static/js/reading-history-page.js`
- Modify: `bytedepth-start/src/main/resources/templates/public/reading-history.html`
- Modify: `bytedepth-start/src/main/resources/templates/public/posts/detail.html`
- Modify: `bytedepth-start/src/main/resources/templates/fragments/nav.html`
- Modify: `bytedepth-start/src/main/resources/static/css/reading-history.css`
- Test: `tests/unit/reading-history-page.test.js`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/ReadingHistoryAssetsTest.java`

- [ ] **Step 1: Write failing UI tests** for empty unread metadata, summary formatting, article title/path rendering, inaccessible local entries being hidden, pagination, and mobile layout hooks.
- [ ] **Step 2: Run** the focused Vitest test; expect failure because the page renderer does not exist.
- [ ] **Step 3: Implement** one shared summary renderer used by detail metadata and history rows, with accessible labels and no personal fields for unread articles.
- [ ] **Step 4: Run** focused frontend and asset contract tests; expect PASS.
- [ ] **Step 5: Commit** `git add bytedepth-start/src/main/resources/static/js/reading-history-page.js bytedepth-start/src/main/resources/templates/public/reading-history.html bytedepth-start/src/main/resources/templates/public/posts/detail.html bytedepth-start/src/main/resources/templates/fragments/nav.html bytedepth-start/src/main/resources/static/css/reading-history.css tests/unit/reading-history-page.test.js bytedepth-start/src/test/java/manfred/bytedepth/ReadingHistoryAssetsTest.java && git commit -m "feat: render reading history UI"`.

### Task 7: Remove obsolete progress transport and update project knowledge

**Files:**

- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/PostReadingController.java`
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/analytics/PostViewLogPort.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/MyBatisPostViewLogAdapter.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/PostViewLogMapper.java`
- Modify: `bytedepth-start/src/main/resources/templates/admin/view-logs/list.html`
- Modify: `docs/engineering/view-log-and-analytics.md`
- Modify: `docs/architecture/database-schema.md`
- Modify: `docs/architecture/routes.md`
- Test: update/remove `PostReadingControllerTest` and `RedisReadingProgressTokenAdapterTest` only where old endpoint is intentionally deleted.

- [ ] **Step 1: Write failing static tests** proving no article template calls `/reading-progress`, anonymous detail pages do not emit a server reading endpoint, and the route/docs/schema describe the new boundary.
- [ ] **Step 2: Run** the focused static tests; expect failure while the old transport remains.
- [ ] **Step 3: Remove** obsolete personal-progress code and Redis token wiring only after the new event path is green; preserve old admin data display and independent PV logging.
- [ ] **Step 4: Run** `rg -n "reading-progress|visitToken|RedisReadingProgressToken"` over production code and classify any remaining references as legacy admin display or delete them.
- [ ] \*\*Step 5: Format docs/assets and run all focused tests; expect no stale route or contradictory knowledge.
- [ ] **Step 6: Commit** `git add bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/portal/PostReadingController.java bytedepth-app/src/main/java/manfred/bytedepth/app/analytics/PostViewLogPort.java bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/MyBatisPostViewLogAdapter.java bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/PostViewLogMapper.java bytedepth-start/src/main/resources/templates/admin/view-logs/list.html docs/engineering/view-log-and-analytics.md docs/architecture/database-schema.md docs/architecture/routes.md bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/PostReadingControllerTest.java bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/stats/RedisReadingProgressTokenAdapterTest.java && git commit -m "refactor: separate reading history from view logs"`.

### Task 8: Full local verification and handoff

**Files:**

- Modify: `CHANGELOG.md` with a categorized non-empty `## Unreleased` entry.
- Modify: implementation docs only if verification discovers a durable rule not already captured.

- [ ] **Step 1: Run** `./mvnw clean install -DskipTests -Dsort.skip=true`.
- [ ] **Step 2: Run** `./mvnw test` and inspect all output for warnings.
- [ ] **Step 3: Run** `npm run format:check`, `npm test`, and `npm run lint`.
- [ ] **Step 4: Run** `bash scripts/run-local-quality.sh` and `bash scripts/check-staging-checklist.sh`; resolve every failure or WARNING before proceeding.
- [ ] **Step 5: Review** `git diff main...HEAD`, branch/worktree status, and changed-business-branch coverage evidence.
- [ ] **Step 6: Commit** the changelog and plan with `git add CHANGELOG.md docs/superpowers/plans/2026-10-07-reading-history.md && git commit -m "docs: record reading history release note"`.
