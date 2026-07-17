# Post Rating and View Log Title Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-browser 1–5 star ratings to article details and show article titles in the admin view-log table.

**Architecture:** A `PostRatingRepository` owns rating persistence and summary aggregation; app command/query executors validate posts and expose a presentation DTO. The adapter supplies a visitor cookie and renders an isolated fragment twice. View logs add their title through the existing paginated mapper query.

**Tech Stack:** Spring Boot, Thymeleaf, MyBatis-Plus, MySQL/Flyway, JUnit 5/Mockito.

## Global Constraints

- Valid scores are integers 1 through 5.
- A visitor has one mutable rating per post, identified by a server-generated HttpOnly cookie.
- Public-component selectors use only the `post-rating-*` namespace and live in a separate CSS file.
- Maven uses Java 21 and full tests follow a `clean install -DskipTests` cache refresh.

---

### Task 1: Rating persistence and application boundary

**Files:**
- Create migration `V14__add_post_rating.sql`; domain rating repository/stat classes; infrastructure rating DO/mapper/repository; app rating command/query/DTO.
- Test application command in `bytedepth-app/src/test/java/.../rating/RatePostCmdExeTest.java`.

- [ ] Write a failing test proving a published post invokes `upsert(postId, visitorToken, score)` and invalid score is rejected.
- [ ] Implement the migration, repository contract, MyBatis upsert/aggregation, and app command/query.
- [ ] Run the focused application test.

### Task 2: Rating endpoint and article component

**Files:**
- Create `PostRatingController`, `fragments/post-rating.html`, and `static/css/post-rating.css`.
- Modify `PostController`, `public/posts/detail.html`, and `PostControllerTest`.
- Test adapter behavior in `PostRatingControllerTest`.

- [ ] Write failing MVC tests for cookie issuance, 400 validation, and detail model summary.
- [ ] Implement controller, cookie handling, fragment rendering, isolated styling, and controller-model wiring.
- [ ] Run focused adapter tests.

### Task 3: View-log title join

**Files:**
- Modify `PostViewLogDO`, `PostViewLogMapper`, and `admin/view-logs/list.html`.
- Add/extend an admin view-log controller/template contract test.

- [ ] Write a failing test asserting `postTitle` reaches the view model/template.
- [ ] Join post titles in the existing pagination query and render the new title column.
- [ ] Run focused test.

### Task 4: Full verification

- [ ] Run `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true`.
- [ ] Run `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -Dsort.skip=true` and confirm no failures.
