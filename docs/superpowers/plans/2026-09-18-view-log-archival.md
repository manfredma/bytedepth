# View Log Archival Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep raw article/page access details to the latest seven days while preserving permanent hourly object PV and daily country PV statistics with safe scheduled archival and tablespace maintenance.

**Architecture:** Add Flyway-managed aggregate tables and per-hour archive state. A Spring application use case computes the retention boundary and delegates each bucket to an infrastructure adapter that aggregates, deletes, and records state in one transaction. Existing analytics mappers read aggregate tables plus still-live raw rows; a dedicated Spring scheduler and MySQL named lock make archival resumable and single-writer.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring Task Scheduling, Spring transactions/JDBC, MyBatis-Plus, MySQL 8, Flyway, JUnit 6, Mockito, JaCoCo.

**Spec:** `docs/superpowers/specs/2026-09-18-view-log-archival-design.md`

## Global Constraints

- Work only in the isolated `feat/view-log-archival` worktree; never develop directly on `main`.
- Do not add a Maven module or a new scheduling framework; use the existing Spring `@EnableScheduling` and `@Scheduled` foundation.
- Maven commands use `./mvnw` with Java 25; before multi-module tests run `./mvnw clean install -DskipTests -Dsort.skip=true`, then `./mvnw test`.
- No local MySQL, Redis, Flyway, Docker, Testcontainers, or other independent process is evidence for integration acceptance; run those checks on staging.
- Any runtime/database/configuration change must have a non-empty categorized `## Unreleased` entry before the first staging deployment.
- Raw `post_view_log` and `page_view_log` details are logically and physically retained for seven days only; aggregate tables must not store IP, User-Agent, Referer, city, visit token, or reading-progress detail.
- Time calculations use `Asia/Shanghai`; archive ranges are half-open `[start, end)` and only complete hours older than the seven-day boundary may be deleted.
- Every production Java branch introduced by this work needs unit tests and 100% changed business branch coverage before commit/release checks.
- Any task failure must be visible in ERROR logs and must not delete raw rows before its corresponding aggregate/state transaction commits.

---

### Task 1: Add the retention policy contract with tests first

**Files:**
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/analytics/ViewLogRetentionPolicy.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/analytics/ViewLogArchivePort.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/analytics/ViewLogArchiveSource.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/analytics/ViewLogArchiveResult.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/analytics/ViewLogArchiveRunResult.java`
- Test: `bytedepth-app/src/test/java/manfred/bytedepth/app/analytics/ViewLogRetentionPolicyTest.java`
- Test: `bytedepth-app/src/test/java/manfred/bytedepth/app/analytics/ViewLogArchiveResultTest.java`

**Interfaces:**
- `ViewLogRetentionPolicy(int retentionDays, ZoneId zone)` exposes `retentionCutoff(LocalDateTime now)`, `detailCutoff(LocalDateTime now)`, and `bucketStart(LocalDateTime value)`.
- `ViewLogArchivePort` exposes `List<LocalDateTime> findCandidateBuckets(ViewLogArchiveSource source, LocalDateTime cutoff, int maxBuckets)` and `ViewLogArchiveResult archiveBucket(ViewLogArchiveSource source, LocalDateTime bucketStart, LocalDateTime bucketEnd)`.
- `ViewLogArchiveSource` has `POST("post")` and `PAGE("page")` values.
- `ViewLogArchiveResult` records `source`, `bucketStart`, `bucketEnd`, `aggregatedRows`, and `deletedRows`.
- `ViewLogArchiveRunResult` records `bucketCount`, `aggregatedRows`, and `deletedRows`.

- [ ] **Step 1: Write failing policy tests.** Cover `now=2026-09-18T17:23`, seven-day cutoff rounded to `2026-09-11T17:00`, exact-hour input, DST-independent Asia/Shanghai behavior, and detail cutoff retaining exactly the last seven days.

```java
@Test
void retentionCutoffRoundsDownToTheCompletedHour() {
    var policy = new ViewLogRetentionPolicy(7, ZoneId.of("Asia/Shanghai"));

    assertEquals(LocalDateTime.of(2026, 9, 11, 17),
            policy.retentionCutoff(LocalDateTime.of(2026, 9, 18, 17, 23)));
}

@Test
void detailCutoffKeepsTheExactSevenDayWindow() {
    var policy = new ViewLogRetentionPolicy(7, ZoneId.of("Asia/Shanghai"));
    var now = LocalDateTime.of(2026, 9, 18, 17, 23);

    assertEquals(LocalDateTime.of(2026, 9, 11, 17, 23), policy.detailCutoff(now));
}
```

- [ ] **Step 2: Run the focused test and verify the expected missing-type failure.**

Run: `./mvnw -pl bytedepth-app -am -Dtest=ViewLogRetentionPolicyTest test -Dsort.skip=true`

Expected: FAIL because `ViewLogRetentionPolicy` does not exist.

- [ ] **Step 3: Implement the minimal immutable policy and result record.** Use `ChronoUnit.HOURS` only for the physical archive cutoff; do not use the rounded cutoff for the detail page.

- [ ] **Step 4: Run the focused tests and inspect output for warnings.**

Run: `./mvnw -pl bytedepth-app -am -Dtest=ViewLogRetentionPolicyTest,ViewLogArchiveResultTest test -Dsort.skip=true`

Expected: PASS with no `WARNING` output.

- [ ] **Step 5: Commit the policy contract.**

```bash
git add bytedepth-app/src/main/java/manfred/bytedepth/app/analytics/ViewLogRetentionPolicy.java \
  bytedepth-app/src/main/java/manfred/bytedepth/app/analytics/ViewLogArchivePort.java \
  bytedepth-app/src/main/java/manfred/bytedepth/app/analytics/ViewLogArchiveSource.java \
  bytedepth-app/src/main/java/manfred/bytedepth/app/analytics/ViewLogArchiveResult.java \
  bytedepth-app/src/main/java/manfred/bytedepth/app/analytics/ViewLogArchiveRunResult.java \
  bytedepth-app/src/test/java/manfred/bytedepth/app/analytics/ViewLogRetentionPolicyTest.java \
  bytedepth-app/src/test/java/manfred/bytedepth/app/analytics/ViewLogArchiveResultTest.java
git commit -m "feat: define view log retention policy"
```

### Task 2: Add the Flyway aggregate and archive-state schema

**Files:**
- Create: `bytedepth-start/src/main/resources/db/migration/V25__add_view_log_archive_stats.sql`
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/MigrationScriptsTest.java`
- Modify: `docs/architecture/database-schema.md`

**Interfaces:**
- The migration creates `post_view_hourly_stat`, `page_view_hourly_stat`, `post_view_country_daily_stat`, `page_view_country_daily_stat`, `view_log_archive_bucket`, and `view_log_tablespace_state` exactly as specified in Section 4 of the spec.
- All aggregate counts are `BIGINT NOT NULL DEFAULT 0`; all aggregate tables use InnoDB and composite primary keys that support their query ranges.

- [ ] **Step 1: Add failing migration contract assertions.** Assert V25 exists, uses all six table names, has both hourly primary keys, both daily country keys, both state-table primary keys, and does not contain `DROP TABLE` or `DELETE FROM`.

```java
@Test
void viewLogArchiveMigrationIsAdditiveAndDefinesAllAggregateKeys() throws IOException {
    String sql;
    try (var stream = getClass().getResourceAsStream("/db/migration/V25__add_view_log_archive_stats.sql")) {
        assertThat(stream).isNotNull();
        sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    assertThat(sql).contains(
            "CREATE TABLE post_view_hourly_stat",
            "CREATE TABLE page_view_hourly_stat",
            "CREATE TABLE post_view_country_daily_stat",
            "CREATE TABLE page_view_country_daily_stat",
            "CREATE TABLE view_log_archive_bucket",
            "CREATE TABLE view_log_tablespace_state",
            "PRIMARY KEY (stat_hour, post_id)",
            "PRIMARY KEY (stat_hour, page_path)",
            "PRIMARY KEY (stat_date, post_id, country)",
            "PRIMARY KEY (stat_date, page_path, country)",
            "PRIMARY KEY (source, bucket_start)",
            "PRIMARY KEY (source)",
            "deleted_rows_since_optimize");
    assertThat(sql).doesNotContain("DROP TABLE", "DELETE FROM");
}
```

- [ ] **Step 2: Run the migration test and confirm it fails because V25 is absent.**

Run: `./mvnw -pl bytedepth-start -am -Dtest=MigrationScriptsTest test -Dsort.skip=true`

Expected: FAIL with the V25 resource assertion.

- [ ] **Step 3: Write the additive V25 migration.** Include indexes for `(post_id, stat_hour)`, `(page_path, stat_hour)`, `(country, stat_date)`, and archive candidate lookup by `(source, bucket_start)`; do not partition the existing raw tables.

- [ ] **Step 4: Update the schema reference.** Add the six tables and V25 to `docs/architecture/database-schema.md`; document that raw tables remain the only source for detail fields, aggregate tables are PV-only, and the tablespace state stores only maintenance counters.

- [ ] **Step 5: Run the migration test and verify it passes without warnings.**

Run: `./mvnw -pl bytedepth-start -am -Dtest=MigrationScriptsTest test -Dsort.skip=true`

Expected: PASS.

- [ ] **Step 6: Commit the additive schema.**

```bash
git add bytedepth-start/src/main/resources/db/migration/V25__add_view_log_archive_stats.sql \
  bytedepth-start/src/test/java/manfred/bytedepth/MigrationScriptsTest.java \
  docs/architecture/database-schema.md
git commit -m "feat: add view log aggregate tables"
```

### Task 3: Implement the application archive use case with pure unit tests

**Files:**
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/analytics/ArchiveViewLogsCmdExe.java`
- Test: `bytedepth-app/src/test/java/manfred/bytedepth/app/analytics/ArchiveViewLogsCmdExeTest.java`

**Interfaces:**
- `ArchiveViewLogsCmdExe(ViewLogArchivePort, ViewLogRetentionPolicy)` exposes `ViewLogArchiveRunResult archive(LocalDateTime now, int maxBucketsPerSource)`.
- The use case processes `ViewLogArchiveSource.POST` and `ViewLogArchiveSource.PAGE` independently, asks the port for at most `maxBucketsPerSource` candidates older than `retentionCutoff`, calls `archiveBucket(source, start, start.plusHours(1))` once per candidate, and returns total bucket/aggregate/deleted counts.
- The use case never calls deletion directly; atomic aggregate/delete/state handling remains inside the infrastructure port implementation.

- [ ] **Step 1: Write failing tests for the use-case branches.** Cover no candidates, one candidate, max-bucket limiting, and propagation of an adapter exception without processing later buckets.

```java
@Test
void archiveProcessesOnlyTheReturnedBucketLimit() {
    var port = mock(ViewLogArchivePort.class);
    var policy = new ViewLogRetentionPolicy(7, ZoneId.of("Asia/Shanghai"));
    var first = LocalDateTime.of(2026, 9, 1, 10, 0);
    var second = first.plusHours(1);
    when(port.findCandidateBuckets(eq(ViewLogArchiveSource.POST), any(), eq(1))).thenReturn(List.of(first));
    when(port.findCandidateBuckets(eq(ViewLogArchiveSource.PAGE), any(), eq(1))).thenReturn(List.of());
    when(port.archiveBucket(ViewLogArchiveSource.POST, first, second))
            .thenReturn(new ViewLogArchiveResult(ViewLogArchiveSource.POST, first, second, 3, 3));

    var result = new ArchiveViewLogsCmdExe(port, policy)
            .archive(LocalDateTime.of(2026, 9, 18, 17, 23), 1);

    assertEquals(1, result.bucketCount());
    assertEquals(3, result.aggregatedRows());
    assertEquals(3, result.deletedRows());
    verify(port).archiveBucket(ViewLogArchiveSource.POST, first, second);
}
```

- [ ] **Step 2: Run the focused tests and confirm the missing-use-case failure.**

Run: `./mvnw -pl bytedepth-app -am -Dtest=ArchiveViewLogsCmdExeTest test -Dsort.skip=true`

Expected: FAIL because `ArchiveViewLogsCmdExe` is not implemented.

- [ ] **Step 3: Implement the minimal coordinator and result aggregation.** Keep time rounding in `ViewLogRetentionPolicy`; do not put SQL or Spring annotations in `bytedepth-app`.

- [ ] **Step 4: Run the focused tests and verify all branches pass.**

Run: `./mvnw -pl bytedepth-app -am -Dtest=ArchiveViewLogsCmdExeTest test -Dsort.skip=true`

Expected: PASS with no `WARNING` output.

- [ ] **Step 5: Commit the use case.**

```bash
git add bytedepth-app/src/main/java/manfred/bytedepth/app/analytics/ArchiveViewLogsCmdExe.java \
  bytedepth-app/src/test/java/manfred/bytedepth/app/analytics/ArchiveViewLogsCmdExeTest.java
git commit -m "feat: add view log archive use case"
```

### Task 4: Implement atomic MySQL aggregation, late-row handling, and named locking

**Files:**
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogArchiveMapper.java`
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogArchiveAdapter.java`
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogArchiveBucket.java`
- Create: `bytedepth-infrastructure/src/main/resources/mapper/ViewLogArchiveMapper.xml`
- Test: `bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/stats/ViewLogArchiveAdapterTest.java`

**Interfaces:**
- `ViewLogArchiveAdapter` implements `ViewLogArchivePort`.
- `ViewLogArchiveMapper` provides source-specific candidate lookup, state lookup, exact first-archive inserts, late-row increment inserts, source-specific deletes, archive-state insert/update, and tablespace-deletion-counter increment.
- The mapper contract is explicit: `findCandidateBuckets(source, cutoff, max)`, `findBucketState(source, bucketStart)`, `insertExactAggregates(source, start, end)`, `incrementAggregates(source, start, end)`, `deleteBucket(source, start, end)`, `insertBucketState(bucket)`, `updateBucketState(bucket)`, and `incrementTablespaceDeletedRows(source, deletedRows)`.
- `archiveBucket(source, start, end)` is `@Transactional` and executes that source's aggregate writes, raw delete, and state update on the same transaction.
- Named lock acquisition/release uses a single `JdbcTemplate.execute(ConnectionCallback)` connection around the scheduled run; lock name is the fixed string `bytedepth:view-log-archive`.

- [ ] **Step 1: Write failing adapter tests using mocks.** Assert first archival calls exact aggregate SQL paths followed by both deletes and state insert; assert an existing bucket uses increment paths and state update; assert an exception prevents any later operation from being reported as success.

```java
@Test
void archiveBucketUsesIncrementPathForAPreviouslyArchivedBucket() {
    var mapper = mock(ViewLogArchiveMapper.class);
    when(mapper.findBucketState(ViewLogArchiveSource.POST, BUCKET)).thenReturn(
            new ViewLogArchiveBucket(ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1), 12));
    when(mapper.incrementAggregates(ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1))).thenReturn(2);
    when(mapper.deleteBucket(ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1))).thenReturn(2);

    TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    when(transactionTemplate.execute(any())).thenAnswer(invocation ->
            ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
    var result = new ViewLogArchiveAdapter(mapper, transactionTemplate).archiveBucket(
            ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1));

    assertEquals(2, result.deletedRows());
    verify(mapper).incrementAggregates(ViewLogArchiveSource.POST, BUCKET, BUCKET.plusHours(1));
    verify(mapper).incrementTablespaceDeletedRows(ViewLogArchiveSource.POST, 2);
    verify(mapper, never()).insertExactAggregates(any(), any(), any());
}
```

The test fixture must execute the `TransactionCallback` synchronously as shown; it must not mock a successful result while skipping the callback, otherwise the test would not prove transaction ordering.

- [ ] **Step 2: Run the focused adapter test and confirm it fails because the mapper/adapter is absent.**

Run: `./mvnw -pl bytedepth-infrastructure -am -Dtest=ViewLogArchiveAdapterTest test -Dsort.skip=true`

Expected: FAIL with missing production types.

- [ ] **Step 3: Implement MyBatis SQL for both sources.** Use half-open ranges. First archival aggregates exact raw counts into hourly object tables and daily country tables, then deletes the same bucket. Existing state uses `view_count = view_count + VALUES(view_count)` only for rows inserted after the state was committed.

- [ ] **Step 4: Implement transaction and named-lock boundaries.** Use Spring `TransactionTemplate` or a public transactional adapter method; do not use `@Async`. A failed aggregate, delete, or state write must roll back the bucket.

- [ ] **Step 5: Add tests for lock-not-acquired, rollback, no-row bucket, first archive, and late archive.** Assert a lock miss performs no mapper calls and returns an empty run result; assert a mapper exception is rethrown after rollback.

- [ ] **Step 6: Run focused tests and inspect output.**

Run: `./mvnw -pl bytedepth-infrastructure -am -Dtest=ViewLogArchiveAdapterTest test -Dsort.skip=true`

Expected: PASS with no `WARNING` output.

- [ ] **Step 7: Commit the atomic adapter.**

```bash
git add bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogArchiveMapper.java \
  bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogArchiveAdapter.java \
  bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogArchiveBucket.java \
  bytedepth-infrastructure/src/main/resources/mapper/ViewLogArchiveMapper.xml \
  bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/stats/ViewLogArchiveAdapterTest.java
git commit -m "feat: archive view logs atomically"
```

### Task 5: Switch analytics queries to aggregate-plus-raw sources

**Files:**
- Modify: `bytedepth-infrastructure/src/main/resources/mapper/ViewLogStatsMapper.xml`
- Modify: `bytedepth-infrastructure/src/main/resources/mapper/PageViewStatsMapper.xml`
- Modify: `bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/stats/MyBatisViewLogStatsAdapterTest.java`
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/infrastructure/stats/MyBatisPageViewStatsAdapterTest.java`
- Create: `bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/stats/ViewLogStatsSqlContractTest.java`

**Interfaces:**
- Keep every existing `ViewLogStatsMapper` and `PageViewStatsMapper` Java method signature unchanged.
- XML queries union aggregate rows and raw rows, then group by the existing DTO dimensions.

- [ ] **Step 1: Add SQL contract tests before changing XML.** Assert every article/page query references its corresponding hourly or daily table and raw table, uses half-open end conditions, and does not reference IP/User-Agent/Referer in aggregate queries.

```java
@Test
void analyticsSqlReadsHourlyAndDailyAggregatesAlongsideRawRows() throws IOException {
    String postSql = readResource("/mapper/ViewLogStatsMapper.xml");
    String pageSql = readResource("/mapper/PageViewStatsMapper.xml");

    assertThat(postSql).contains("post_view_hourly_stat", "post_view_country_daily_stat", "post_view_log");
    assertThat(pageSql).contains("page_view_hourly_stat", "page_view_country_daily_stat", "page_view_log");
    assertThat(postSql + pageSql).doesNotContain("user_agent", "referer", "ip");
}

private String readResource(String path) throws IOException {
    try (var stream = getClass().getResourceAsStream(path)) {
        assertThat(stream).isNotNull();
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 2: Run the contract test and confirm it fails because the aggregate table names are absent from current XML.**

Run: `./mvnw -pl bytedepth-infrastructure -am -Dtest=ViewLogStatsSqlContractTest test -Dsort.skip=true`

Expected: FAIL with missing table-name assertions.

- [ ] **Step 3: Rewrite article/page XML queries.** Use hourly tables for ranking and trends; use daily country tables for country distribution and country drill-down; union raw rows for recent and late data. Keep DTO aliases (`post_title`, `page_path`, `view_count`, `percent`, `label`) unchanged.

- [ ] **Step 4: Add adapter delegation tests for all unchanged mapper signatures.** Verify the adapter still forwards the same start/end/limit/format arguments; the aggregation behavior belongs in XML, not in controller code.

- [ ] **Step 5: Run the SQL contract and existing adapter tests.**

Run: `./mvnw -pl bytedepth-infrastructure -am -Dtest=ViewLogStatsSqlContractTest,MyBatisViewLogStatsAdapterTest,MyBatisPageViewStatsAdapterTest test -Dsort.skip=true`

Expected: PASS with no `WARNING` output.

- [ ] **Step 6: Commit the query switch.**

```bash
git add bytedepth-infrastructure/src/main/resources/mapper/ViewLogStatsMapper.xml \
  bytedepth-infrastructure/src/main/resources/mapper/PageViewStatsMapper.xml \
  bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/stats/MyBatisViewLogStatsAdapterTest.java \
  bytedepth-start/src/test/java/manfred/bytedepth/infrastructure/stats/MyBatisPageViewStatsAdapterTest.java \
  bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/stats/ViewLogStatsSqlContractTest.java
git commit -m "feat: query archived view statistics"
```

### Task 6: Enforce seven-day detail visibility and add scheduled job configuration

**Files:**
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/analytics/PostViewLogPort.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/PostViewLogMapper.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/MyBatisPostViewLogAdapter.java`
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminViewLogController.java`
- Modify: `bytedepth-start/src/main/java/manfred/bytedepth/BytedepthApplication.java`
- Modify: `bytedepth-start/src/main/resources/application.yml`
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/admin/SimpleAdminControllerCoverageTest.java`
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogArchiveJob.java`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/admin/AdminViewLogControllerRetentionTest.java`
- Test: `bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/stats/ViewLogArchiveJobTest.java`

**Interfaces:**
- Change `PostViewLogPort.findPage` to `findPage(Long postId, Long userId, LocalDateTime cutoff, int offset, int size)`.
- Change `PostViewLogPort.countPage` to `countPage(Long postId, Long userId, LocalDateTime cutoff)`.
- `AdminViewLogController` receives a fixed-in-test `Clock`, computes `retentionPolicy.detailCutoff(LocalDateTime.now(clock))`, and passes it to both methods.
- Update `SimpleAdminControllerCoverageTest`'s existing controller fixture from `new AdminViewLogController(logs)` to `new AdminViewLogController(logs, retentionPolicy, fixedClock)` so the production constructor remains covered.
- `BytedepthApplication` exposes a production `Clock` bean fixed to `Asia/Shanghai`.
- `ViewLogArchiveJob` calls `ArchiveViewLogsCmdExe.archive(LocalDateTime.now(clock), maxBuckets)` under the named lock using the dedicated scheduler.

- [ ] **Step 1: Update tests to assert the new cutoff argument before changing production signatures.** Cover a page request with an old row boundary and verify both page and count calls receive the same exact seven-day cutoff.

```java
@Test
void detailPagePassesExactSevenDayCutoffToBothQueries() {
    var now = LocalDateTime.of(2026, 9, 18, 17, 23);
    var fixedClock = Clock.fixed(
            now.atZone(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai"));
    var port = mock(PostViewLogPort.class);
    var retentionPolicy = new ViewLogRetentionPolicy(7, ZoneId.of("Asia/Shanghai"));
    var controller = new AdminViewLogController(port, retentionPolicy, fixedClock);

    controller.list(new ExtendedModelMap(), null, null, 1);

    verify(port).findPage(isNull(), isNull(), eq(now.minusDays(7)), eq(0), eq(20));
    verify(port).countPage(isNull(), isNull(), eq(now.minusDays(7)));
}
```

- [ ] **Step 2: Run affected controller tests and confirm the expected signature mismatch failure.**

Run: `./mvnw -pl bytedepth-start -am -Dtest=AdminViewLogControllerRetentionTest,SimpleAdminControllerCoverageTest test -Dsort.skip=true`

Expected: FAIL until the port, adapter, mapper, and controller signatures are updated together.

- [ ] **Step 3: Update raw detail SQL with `visited_at >= #{cutoff}` for both list and count.** Preserve post/user filters and pagination behavior.

- [ ] **Step 4: Add the retention policy bean and scheduler properties.** Configure:

```yaml
bytedepth:
  analytics:
    retention-days: 7
    archive-fixed-delay: 10m
    archive-max-buckets-per-run: 24
    archive-lock-name: bytedepth:view-log-archive
    optimize-min-deleted-rows: 100000
spring:
  task:
    scheduling:
      pool:
        size: 2
```

Register the shared production clock in `BytedepthApplication` so controllers and archive jobs use one timezone source:

```java
@Bean
Clock analyticsClock() {
    return Clock.system(ZoneId.of("Asia/Shanghai"));
}
```

Use a dedicated scheduler bean for `ViewLogArchiveJob`; do not annotate it with `@Async`.

- [ ] **Step 5: Add job tests for successful run, lock miss, and exception logging/rethrow.** Verify the Redis stats task remains on its existing scheduler and archive scheduling uses the dedicated scheduler bean name.

- [ ] **Step 6: Run affected tests and verify no warnings.**

Run: `./mvnw -pl bytedepth-start -am -Dtest=AdminViewLogControllerRetentionTest,SimpleAdminControllerCoverageTest,ViewLogArchiveJobTest test -Dsort.skip=true`

Expected: PASS.

- [ ] **Step 7: Commit the retention and scheduling integration.**

```bash
git add bytedepth-app/src/main/java/manfred/bytedepth/app/analytics/PostViewLogPort.java \
  bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/PostViewLogMapper.java \
  bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/MyBatisPostViewLogAdapter.java \
  bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminViewLogController.java \
  bytedepth-start/src/main/java/manfred/bytedepth/BytedepthApplication.java \
  bytedepth-start/src/main/resources/application.yml \
  bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogArchiveJob.java \
  bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/admin/AdminViewLogControllerRetentionTest.java \
  bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/stats/ViewLogArchiveJobTest.java
git commit -m "feat: schedule seven-day view log retention"
```

### Task 7: Add low-peak tablespace maintenance with safe thresholds

**Files:**
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogTablespaceMaintenanceJob.java`
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogTablespaceMaintenanceMapper.java`
- Create: `bytedepth-infrastructure/src/main/resources/mapper/ViewLogTablespaceMaintenanceMapper.xml`
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogTablespaceMaintenanceProperties.java`
- Test: `bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/stats/ViewLogTablespaceMaintenanceJobTest.java`
- Modify: `bytedepth-start/src/main/resources/application.yml`

**Interfaces:**
- The job checks `view_log_tablespace_state.deleted_rows_since_optimize` and table fragmentation metadata, then only invokes fixed SQL identifiers `OPTIMIZE TABLE post_view_log` and `OPTIMIZE TABLE page_view_log` when configured thresholds are met; it resets each source counter only after that source succeeds.
- It never builds a table name from request input and never runs inside the archive transaction.

- [ ] **Step 1: Write failing tests for below-threshold, threshold, per-table failure, and retry behavior.** Assert no optimize SQL below threshold and two fixed optimize statements at/above threshold.

- [ ] **Step 2: Run the focused test and confirm the missing job failure.**

Run: `./mvnw -pl bytedepth-infrastructure -am -Dtest=ViewLogTablespaceMaintenanceJobTest test -Dsort.skip=true`

Expected: FAIL because the maintenance job does not exist.

- [ ] **Step 3: Implement the mapper, property object, and weekly Spring schedule.** Read each source's persisted deletion counter and `information_schema.tables` size/free-space metrics; default schedule is Sunday 03:30 Asia/Shanghai. Use the same MySQL named lock family with a distinct lock name `bytedepth:view-log-tablespace`. Execute each fixed `OPTIMIZE TABLE` statement separately and reset only the successfully optimized source counter.

- [ ] **Step 4: Run focused maintenance tests and inspect output.**

Run: `./mvnw -pl bytedepth-infrastructure -am -Dtest=ViewLogTablespaceMaintenanceJobTest test -Dsort.skip=true`

Expected: PASS with no `WARNING` output.

- [ ] **Step 5: Commit the maintenance job.**

```bash
git add bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogTablespaceMaintenanceJob.java \
  bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogTablespaceMaintenanceMapper.java \
  bytedepth-infrastructure/src/main/resources/mapper/ViewLogTablespaceMaintenanceMapper.xml \
  bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/ViewLogTablespaceMaintenanceProperties.java \
  bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/stats/ViewLogTablespaceMaintenanceJobTest.java \
  bytedepth-start/src/main/resources/application.yml
git commit -m "feat: maintain view log tablespaces"
```

### Task 8: Add release documentation, operational checks, and query-contract coverage

**Files:**
- Modify: `docs/releases/CHANGELOG.md`
- Modify: `docs/engineering/view-log-and-analytics.md`
- Modify: `docs/architecture/database-schema.md`
- Modify: `deploy/README.md` to state that archival and tablespace jobs run inside the application after the normal full-compose deployment; no host cron or second scheduler is required.
- Create: `scripts/test-view-log-archive.sh`, using the existing `scripts/test-*.sh` convention.

- [ ] **Step 1: Add a categorized non-empty Unreleased entry before staging.** Use `### Changed` and state that raw access details are retained for seven days while historical hourly PV and daily country aggregates remain available; include migration and rollback compatibility notes.

- [ ] **Step 2: Update the engineering analytics guide.** Document the two-level retention policy, Spring schedule, seven-day detail boundary, late-row handling, source-level tablespace counters, and that historical country statistics are daily.

- [ ] **Step 3: Add a repeatable static check.** It must fail if:
  - V25 is missing;
  - archive SQL lacks aggregate-before-delete ordering markers;
  - detail SQL lacks a cutoff parameter;
  - the job does not use fixed delay and the named lock;
  - Unreleased lacks a categorized non-empty entry.

- [ ] **Step 4: Run the script test and inspect every output line for WARNING.**

Run: `bash scripts/test-view-log-archive.sh`

Expected: PASS and no warning text.

- [ ] **Step 5: Commit documentation and static constraints.**

```bash
git add docs/releases/CHANGELOG.md docs/engineering/view-log-and-analytics.md \
  docs/architecture/database-schema.md deploy/README.md scripts/test-view-log-archive.sh
git commit -m "docs: document view log retention policy"
```

### Task 9: Run local quality, coverage, and staging acceptance

**Files:**
- Modify only files required by failed verification; do not broaden scope.
- Evidence: staging integration and E2E records tied to the candidate commit SHA.

- [ ] **Step 1: Refresh the Maven reactor and run the complete unit test suite.**

Run: `./mvnw clean install -DskipTests -Dsort.skip=true`

Then run: `./mvnw test -Dsort.skip=true`

Expected: all tests pass, no `WARNING`, and no independent process is used locally.

- [ ] **Step 2: Run changed-code coverage.**

Run: `bash scripts/verify-changed-coverage.sh`

Expected: every changed production Java class has 100% line, branch, and method coverage.

- [ ] **Step 3: Run the unified local quality gate.**

Before the first frontend or Playwright-related command in this new worktree, run:

Run: `npm ci --ignore-scripts --no-audit --no-fund`

Expected: lockfile installation succeeds with no `WARNING` output.

Run: `bash scripts/run-local-quality.sh`

Expected: Java tests, frontend checks, deployment contracts, migration checks, release-readiness checks, and zero-warning checks all pass.

- [ ] **Step 4: Run the staging checklist before deployment.**

Run: `bash scripts/check-staging-checklist.sh`

Expected: PASS, including the migration warning-safety and archive contract checks.

- [ ] **Step 5: Deploy the candidate ref to staging using the repository wrapper.**

Run: `./deploy/deploy-staging.sh feat/view-log-archival`

Use `https://staging-bytedepth.bytedepth.cn/` for all validation. Do not use the local application as integration or acceptance evidence.

- [ ] **Step 6: Execute staging integration verification.** Verify Flyway V25, archive task startup, MySQL named lock behavior, aggregate-plus-raw counts, seven-day detail visibility, failed-transaction retry, late-row reconciliation, and tablespace size reporting.

- [ ] **Step 7: Execute staging E2E verification.** Exercise the admin analytics page and detail page across a range that crosses the archive boundary; verify hourly trend labels and daily country results.

- [ ] **Step 8: Record two commit-bound `result=passed` evidence files.** Both complete candidate SHA values must equal the deployed staging candidate; do not merge or release before both records are present.

- [ ] **Step 9: Ask the project owner to accept the change on staging.** Only after staging integration, E2E, and owner acceptance may the branch be PR-merged into `main`.
