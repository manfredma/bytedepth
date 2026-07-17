# Account Test Portability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace skipped Docker-dependent account flow tests with a database-free MVC security regression test.

**Architecture:** Delete the redundant full-context Testcontainers class. A focused `@WebMvcTest` imports the real `SecurityConfig`, supplies mocks for its infrastructure collaborators and the comment controller dependencies, and asserts the filter chain redirects unauthenticated comment submissions before controller execution.

**Tech Stack:** Spring Boot WebMvcTest, Spring Security Test, Mockito, JUnit 5.

## Global Constraints

- Do not add H2, embedded database binaries, or a second migration set.
- Maven commands use Java 21; refresh local multi-module artifacts before the full test suite.
- Keep the test independent of Docker, MySQL, Redis, and Meilisearch.

---

### Task 1: Add portable security-route coverage

**Files:**
- Create: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/security/SecurityRoutingTest.java`
- Delete: `bytedepth-start/src/test/java/manfred/bytedepth/AccountFlowE2ETest.java`

- [ ] **Step 1: Write a failing MVC test**

Create a `@WebMvcTest(CommentController.class)` that imports `SecurityConfig`, mocks `DataSource`, `PersistentTokenRepository`, `UserDetailsService`, `PasswordEncoder`, `SubmitCommentCmdExe`, and `PostRepository`, then posts to `/posts/example/comments` with CSRF and expects `redirectedUrl("/login")`.

- [ ] **Step 2: Run the test and observe the missing test class failure**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start -Dtest=SecurityRoutingTest test -Dsort.skip=true`

Expected: Maven fails because `SecurityRoutingTest` is not present.

- [ ] **Step 3: Implement the focused security test and remove the skipped E2E class**

Add the test with the real filter chain and delete `AccountFlowE2ETest`. Verify the mocked comment dependencies are never invoked, proving the request was intercepted by security.

- [ ] **Step 4: Run focused test**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start -Dtest=SecurityRoutingTest test -Dsort.skip=true`

Expected: PASS with one executed test and no Docker startup attempt.

### Task 2: Verify the full suite

- [ ] **Step 1: Refresh artifacts**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true`

- [ ] **Step 2: Run all tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -Dsort.skip=true`

Expected: BUILD SUCCESS and no skipped `AccountFlowE2ETest` methods.
