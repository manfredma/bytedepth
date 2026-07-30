# Task 1 report: operations query model, ports, and infrastructure

## Implementation

- Added `app.ops` query DTOs, query executors, and ports for the operations overview and controlled table detail queries.
- The overview derives JVM start time and uptime from `RuntimeMXBean`, and independently degrades MySQL, Redis, and MeiliSearch failures to unavailable status DTOs.
- Added JDBC adapters for MySQL connectivity/database name and fixed, `LIMIT 50` table queries.
- Added a Redis adapter that parses `INFO` and counts `pv:post:` plus `bytedepth:session:` keys with prefix-scoped `SCAN`; it issues no other Redis commands.
- Added a MeiliSearch adapter using the existing URL/API-key configuration and authenticated `RestClient` pattern to request `/health` and `/stats`.
- The table whitelist is an enum containing exactly the required table and column lists. Infrastructure maps each enum constant to a fixed SQL literal; no request input is concatenated into SQL.

## Tests and results

- Added unit coverage for Redis `INFO` parsing, `SCAN` counting, dependency-failure isolation, table-name rejection, and fixed post-table SQL selection.
- Ran cache refresh: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true` — passed.
- Ran full suite: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -Dsort.skip=true` — passed, 99 tests, 0 failures, 0 errors, 0 skipped.

## Files changed

- `bytedepth-app/src/main/java/manfred/bytedepth/app/ops/*`
- `bytedepth-app/src/test/java/manfred/bytedepth/app/ops/*`
- `bytedepth-infrastructure/pom.xml`
- `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/ops/*`
- `bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/ops/*`

## Self-review

- DDD dependency direction is preserved: the app module owns the ports and use cases; infrastructure depends on app to implement the ports.
- Dependency exceptions do not leak their messages into operations DTOs or stop the overview query.
- SQL has no dynamic table/column component: all three statements are immutable literals with `LIMIT 50`.
- `git diff --check` reported no whitespace errors.

## Concerns

- Redis `RedisConnection.execute("INFO")` and `scan(ScanOptions)` are marked deprecated by the current Spring Data Redis API. They remain compatible with the installed Spring Boot 3.2.5 stack and directly satisfy the raw INFO/SCAN requirements; a future Spring Data upgrade may require moving to its newer command APIs.
- The Maven model reports a pre-existing missing explicit Surefire plugin version warning in `bytedepth-start`; this task does not alter that build configuration.

## Review fix round 1

- Added `ORDER BY id DESC LIMIT 50` to each of the three fixed table-detail SQL statements and updated the exact post-query unit-test expectation.
- Removed Redis `PING`; availability now follows successful INFO parsing and the two prefix-limited SCAN counts only.
- Expanded Redis SCAN tests to capture and assert `pv:post:*` and `bytedepth:session:*` patterns independently.
- Refreshed local module artifacts with `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true` — passed.
- Ran `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-infrastructure test -Dsort.skip=true` — passed, 5 tests, 0 failures, 0 errors, 0 skipped.
