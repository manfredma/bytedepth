# 分布式网页认证 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (\`- [ ]\`) syntax for tracking.

**Goal:** 让多实例网页端共享 Redis Session，并提供数据库持久化的 30 天 remember-me 登录。

**Architecture:** Spring Session 将 HttpSession 存入 Redis，保持现有表单登录和 CSRF。Spring Security 的 JdbcTokenRepositoryImpl 将 remember-me Token 存入 MySQL；登录页仅在用户勾选时提交 remember-me 参数。

**Tech Stack:** Spring Boot 3.2、Spring Security、Spring Session Data Redis、Flyway、MySQL、JUnit 5。

## Global Constraints

- Maven 命令必须使用 Java 21。
- 多模块测试前先运行 \`mvn clean install -DskipTests -Dsort.skip=true\`，再运行 \`mvn test -Dsort.skip=true\`。
- Session 存 Redis，空闲 60 分钟。
- remember-me Cookie 名称 \`bytedepth-remember-me\`，有效期 30 天，持久化表为 \`persistent_logins\`。
- 保持现有 Session CSRF、表单登录和权限规则。
- Cookie 使用 HttpOnly、SameSite=Lax；生产环境 Secure 由环境变量控制。

---

### Task 1: 配置 Redis Session 与 remember-me 持久化

**Files:**
- Modify: \`bytedepth-start/pom.xml\`
- Modify: \`bytedepth-adapter/pom.xml\`
- Modify: \`bytedepth-start/src/main/resources/application.yml\`
- Create: \`bytedepth-start/src/main/resources/db/migration/V13__add_persistent_logins.sql\`
- Modify: \`bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/security/SecurityConfig.java\`
- Test: \`bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/security/SecurityConfigTest.java\`

**Interfaces:** produces a \`PersistentTokenRepository\` bean backed by the application \`DataSource\`; configures \`HttpSecurity.rememberMe\` with parameter \`remember-me\`, key from \`BYTEDEPTH_REMEMBER_ME_KEY\`, 30-day validity, and cookie name.

- [ ] **Step 1: Write failing configuration tests**

Create a Spring context test that asserts a PersistentTokenRepository bean exists and the security filter chain accepts a login request with the remember-me parameter. Add an application-context property test asserting session timeout is 60 minutes.

\`\`\`java
assertThat(context.getBean(PersistentTokenRepository.class))
    .isInstanceOf(JdbcTokenRepositoryImpl.class);
\`\`\`

- [ ] **Step 2: Run focused tests and confirm failure**

Run: \`JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start -am test -Dsort.skip=true -Dtest=SecurityConfigTest -Dsurefire.failIfNoSpecifiedTests=false\`

Expected: FAIL because the repository bean and session dependency/configuration do not exist.

- [ ] **Step 3: Implement dependencies, migration, configuration, and security**

Add \`spring-session-data-redis\` to bytedepth-start and direct \`spring-jdbc\` to bytedepth-adapter, because SecurityConfig owns the JdbcTokenRepositoryImpl bean. Add Flyway SQL:

\`\`\`sql
CREATE TABLE persistent_logins (
  username VARCHAR(64) NOT NULL,
  series VARCHAR(64) PRIMARY KEY,
  token VARCHAR(64) NOT NULL,
  last_used TIMESTAMP NOT NULL
);
\`\`\`

Configure \`spring.session.store-type: redis\`, \`spring.session.timeout: 60m\`, namespace \`bytedepth:session\`, and cookie HttpOnly/SameSite/Secure properties. Add a JdbcTokenRepositoryImpl bean; call \`setCreateTableOnStartup(false)\`. Configure rememberMe with the repository, parameter, cookie name, 30-day validity and a required environment-backed key.

- [ ] **Step 4: Run focused tests and confirm pass**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit**

\`\`\`bash
git add bytedepth-start/pom.xml bytedepth-start/src/main/resources/application.yml bytedepth-start/src/main/resources/db/migration/V13__add_persistent_logins.sql bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/security/SecurityConfig.java bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/security/SecurityConfigTest.java
git commit -m "feat: add redis session and persistent remember me"
\`\`\`

### Task 2: 增加登录页 remember-me 控件并验证注销撤销

**Files:**
- Modify: \`bytedepth-start/src/main/resources/templates/public/login.html\`
- Modify: \`bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/security/SecurityConfig.java\`
- Test: \`bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/security/SecurityConfigTest.java\`

**Interfaces:** login form submits \`remember-me=true\`; logout invokes \`PersistentTokenRepository.removeUserTokens(username)\`.

- [ ] **Step 1: Write failing MVC/HTML tests**

Assert login HTML contains a checkbox named remember-me. Mock the PersistentTokenRepository, authenticate a user, perform logout, and assert removeUserTokens is invoked for that username.

- [ ] **Step 2: Run focused tests and confirm failure**

Run: \`JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl bytedepth-start -am test -Dsort.skip=true -Dtest=SecurityConfigTest -Dsurefire.failIfNoSpecifiedTests=false\`

Expected: FAIL because the checkbox and logout handler are missing.

- [ ] **Step 3: Implement minimal UI and logout handler**

Add a label/checkbox to login.html, unchecked by default. Add a LogoutHandler before the standard logout handler; retrieve Authentication name and invoke removeUserTokens only for an authenticated user. Keep logout redirect and CSRF unchanged.

- [ ] **Step 4: Run focused tests and confirm pass**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit**

\`\`\`bash
git add bytedepth-start/src/main/resources/templates/public/login.html bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/security/SecurityConfig.java bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/security/SecurityConfigTest.java
git commit -m "feat: add remember me login option"
\`\`\`

### Task 3: 全量验证

- [ ] **Step 1: Refresh cache**

Run: \`JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true\`

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run all tests**

Run: \`JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -Dsort.skip=true\`

Expected: BUILD SUCCESS; report Docker/Testcontainers skips separately from failures.

- [ ] **Step 3: Inspect changes**

Run: \`git diff --check && git status --short\`

Expected: no whitespace errors and only expected files.

## Self-Review

- Task 1 covers shared Session storage, timeout, cookie settings and persistent Token storage.
- Task 2 covers opt-in UI and logout revocation.
- Task 3 enforces the project build/test policy.
