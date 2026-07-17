# Compact Pagination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the shared Thymeleaf pagination fragment into an isolated, compact, responsive reading-oriented component.

**Architecture:** Keep the fragment signature and all URL-building expressions unchanged. Encapsulate styling under `bd-pagination-*`, replace the permanent jump form with a native disclosure, and use a resource-level JUnit test to prevent the old unscoped structure from returning.

**Tech Stack:** Thymeleaf, inline component CSS, Java 17 source / Java 21 Maven runtime, JUnit 5.

## Global Constraints

- Maven commands use `JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
- Before multi-module tests, run `mvn clean install -DskipTests -Dsort.skip=true`, then `mvn test -Dsort.skip=true`.
- Public component selectors must be self-isolated and must not rely on page-level pagination styles.

---

### Task 1: Lock down the compact component contract

**Files:**
- Create: `bytedepth-start/src/test/java/manfred/bytedepth/PaginationFragmentTest.java`

**Interfaces:**
- Consumes: classpath resource `templates/fragments/pagination.html`.
- Produces: assertions for the isolated component class names and the collapsed jump affordance.

- [ ] **Step 1: Write the failing test**

```java
@Test
void usesOnlyIsolatedClassesAndCollapsesPageJump() throws IOException {
    String template = new String(getClass().getResourceAsStream("/templates/fragments/pagination.html").readAllBytes(), StandardCharsets.UTF_8);

    assertThat(template).contains("bd-pagination-wrap", "bd-pagination-nav", "bd-pagination-jump");
    assertThat(template).contains("<details class=\"bd-pagination-jump\"");
    assertThat(template).doesNotContain("class=\"pagination\"");
    assertThat(template).doesNotContain("class=\"page-btn");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start -Dtest=PaginationFragmentTest test -Dsort.skip=true`

Expected: FAIL because the existing fragment contains unscoped `pagination` and `page-btn` classes.

- [ ] **Step 3: Implement the isolated compact markup and styles**

Modify the fragment so all classes and CSS selectors use `bd-pagination-*`; preserve the existing fragment parameters and every `th:href` URL expression. Use `<details>`/`<summary>` for the page-jump disclosure.

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start -Dtest=PaginationFragmentTest test -Dsort.skip=true`

Expected: PASS.

### Task 2: Verify repository integration

**Files:**
- Verify: `bytedepth-start/src/main/resources/templates/fragments/pagination.html`

- [ ] **Step 1: Refresh multi-module artifacts**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true`

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run all tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -Dsort.skip=true`

Expected: BUILD SUCCESS with zero test failures.
