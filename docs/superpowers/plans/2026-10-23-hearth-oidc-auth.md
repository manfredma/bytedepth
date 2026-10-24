# Hearth OIDC Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add Hearth OIDC authentication to bytedepth while preserving bytedepth-owned users, sessions, and business authorization.

**Architecture:** Spring Security handles Hearth OIDC protocol flow. A bytedepth application port provisions or resolves an `issuer + subject` identity mapping and returns the local user authentication record. The web adapter converts the resolved local identity into the existing `SiteUserDetails`, so all existing permission and ownership checks remain unchanged.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring Security 7 OAuth2 Client, Spring Session Redis, MyBatis-Plus, Flyway, JUnit/MockMvc.

**Spec:** `docs/superpowers/specs/2026-10-23-hearth-oidc-auth-design.md`

## Global Constraints

- Hearth only authenticates identities and controls application access; bytedepth owns roles, permissions, resources, and data authorization.
- Identity lookup uses `issuer + subject`; username and email are not identity keys.
- Keep local form/mobile login and local RBAC compatible.
- Use Java 25, Maven Wrapper, existing module boundaries, and zero-warning quality gates.
- Add a classified `Unreleased` changelog entry for runtime/authentication behavior.

## Review Focus

- A new Hearth identity must not inherit another local account because an email or username matches; test unique `issuer + subject` mapping.
- Existing local permissions must be present after OIDC login; test the resulting principal is `SiteUserDetails` with database permissions.
- PENDING and BANNED local users must not receive an active business session; test both states.
- OIDC being disabled or misconfigured must not silently expose a local fallback as Hearth login; test configuration and route behavior.
- Local logout must clear the bytedepth session and use the Hearth end-session redirect without sharing cookies.

---

### Task 1: Add the identity mapping model and provisioning port

**Files:**

- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/user/OidcIdentityAuthenticationPort.java`
- Create: `bytedepth-app/src/main/java/manfred/bytedepth/app/user/OidcIdentityAuthentication.java`
- Create: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/user/MyBatisOidcIdentityAuthenticationAdapter.java`
- Modify: `bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/user/UserMapper.java`
- Create: `bytedepth-start/src/main/resources/db/migration/V30__add_hearth_oidc_identity.sql`
- Test: `bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/user/MyBatisOidcIdentityAuthenticationAdapterTest.java`

**Interfaces:**

- Produces `OidcIdentityAuthenticationPort.authenticate(String issuer, String subject, String preferredUsername, String email)` returning local `UserAuthentication` or a typed pending/blocked result.
- Persists one unique mapping per `issuer + subject`; provisioning creates a `PENDING` user with a collision-safe local username and no role assignment.

- [ ] Write failing tests for existing mapping, first-login provisioning, username collision, issuer separation, PENDING, and BANNED.
- [ ] Run the focused test and confirm it fails for the missing port/adapter/schema behavior.
- [ ] Add the migration with a unique `(issuer, subject)` key and foreign key to `user`.
- [ ] Implement the adapter using the existing MyBatis user/permission loading path and a transaction for first-login user plus identity creation.
- [ ] Run the focused tests and the infrastructure module tests.

### Task 2: Add Hearth OAuth2 client configuration and local-principal conversion

**Files:**

- Modify: `bytedepth-start/pom.xml`
- Modify: `bytedepth-start/src/main/resources/application.yml`
- Create: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/security/HearthOidcAuthenticationSuccessHandler.java`
- Create: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/security/HearthOidcAuthenticationFailureHandler.java`
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/security/SecurityConfig.java`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/security/HearthOidcAuthenticationTest.java`

**Interfaces:**

- Consumes `OidcIdentityAuthenticationPort` and existing local `UserDetails`/permission loading.
- Produces an OIDC registration named `hearth`, a local `SiteUserDetails` authentication, and a safe disabled/misconfigured behavior.

- [ ] Write failing MockMvc/unit tests for OIDC success, local permission retention, pending/blocked accounts, and disabled configuration.
- [ ] Run the focused tests and verify the expected red failures.
- [ ] Add `spring-boot-starter-oauth2-client` and environment-backed registration/provider properties with no committed secret.
- [ ] Implement success handling that resolves local identity, replaces the temporary OIDC authentication with `SiteUserDetails`, saves the local SecurityContext, and preserves saved-request redirects.
- [ ] Keep form login, mobile login, CSRF, rate limits, and local remember-me behavior unchanged.
- [ ] Run focused tests and the full offline Java test suite.

### Task 3: Implement logout, login surface, documentation, and regression contracts

**Files:**

- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/security/SecurityConfig.java`
- Modify: `bytedepth-start/src/main/resources/templates/public/login.html`
- Modify: `docs/security/authentication.md`
- Modify: `docs/releases/CHANGELOG.md`
- Create or modify: `scripts/test-hearth-oidc-contract.sh`
- Modify: `scripts/run-local-quality.sh`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/security/SecurityRoutingTest.java`

- [ ] Add a failing contract test for the registration name, environment variables, no committed secret, local authorization boundary, and logout behavior.
- [ ] Implement RP-Initiated Logout with a validated Hearth post-logout redirect and local session invalidation first.
- [ ] Add the Hearth login link without removing the existing local login path or exposing implementation details unnecessarily.
- [ ] Document the `iss + sub` mapping, first-login PENDING behavior, environment configuration, and authorization boundary.
- [ ] Add the categorized changelog entry and include the new contract in local quality.
- [ ] Run formatter on changed Java files, then focused tests, full tests, changed-coverage, and local quality.

### Task 4: Review and handoff

- [ ] Inspect the complete diff and verify no credentials, cross-module dependency violations, or Hearth role-to-permission mapping were introduced.
- [ ] Run `git diff --check` and the complete local quality command with fresh output.
- [ ] Record the full commit SHA and verification commands; staging OIDC integration/E2E remains a release-platform step requiring real Hearth credentials and environment configuration.
