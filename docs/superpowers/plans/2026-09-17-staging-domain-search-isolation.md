# Staging Domain and Search Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move staging to `staging-bytedepth.bytedepth.cn` and ensure RSS, sitemap, and search-engine discovery remain enabled in production but disabled in staging.

**Architecture:** The staging host is selected by the exact `BYTEDEPTH_DOMAIN`/TLS `server_name`; no query parameter or Cookie participates in routing. Nginx adds crawler suppression headers, blocks staging RSS and sitemap endpoints, and serves a robots file without sitemap declarations. Thymeleaf uses the existing `BYTEDEPTH_ENVIRONMENT` model attribute to hide RSS navigation/auto-discovery only when the value is `staging`; production templates remain unchanged.

**Tech Stack:** Spring Boot/Thymeleaf, Nginx templates, Docker Compose, Playwright, Bash contract tests, Markdown deployment knowledge base.

**Spec:** `docs/superpowers/specs/2026-09-16-production-entry-and-staging-preview-design.md`

## Global Constraints

- Production must continue to expose `/feed.xml`, `/sitemap.xml`, RSS navigation, RSS auto-discovery, and production canonical URLs.
- Staging must use `BYTEDEPTH_ENVIRONMENT=staging` and `BYTEDEPTH_SITE_URL=https://bytedepth.cn`.
- Staging must return `X-Robots-Tag: noindex, nofollow, noarchive`, `Referrer-Policy: no-referrer`, and a `robots.txt` containing only `User-agent: *` and `Disallow: /`.
- Staging `/feed.xml` and `/sitemap.xml` must return `404`; no staging page may advertise a sitemap or RSS alternate link.
- The exact staging hostname is `staging-bytedepth.bytedepth.cn`; the old `staging.bytedepth.cn` is not a staging content URL.
- The new hostname is not an authentication boundary; DNS and TLS certificate transparency can reveal it.
- All current operational docs, scripts, E2E configuration, and acceptance commands must use the new staging hostname.
- No Maven module may be added; all modified runtime behavior must have unit or contract coverage before staging deployment.

---

### Task 1: Replace the preview-route contract with environment-aware search-isolation tests

**Files:**
- Modify: `scripts/test-staging-preview-route.sh`
- Modify: `scripts/test-run-staging-e2e-tests.sh`
- Modify: `scripts/test-staging-checklist.sh`
- Create: `scripts/test-staging-search-isolation.sh`
- Test: `scripts/test-staging-preview-route.sh`, `scripts/test-staging-search-isolation.sh`

**Interfaces:**
- Consumes: Nginx staging templates, staging compose environment, Thymeleaf RSS fragments, and current staging operational commands.
- Produces: deterministic assertions for the exact hostname, environment-specific RSS behavior, blocked sitemap/feed endpoints, and no stale preview Cookie/query contract.

- [ ] **Step 1: Write failing contract assertions.**

  Assert the staging search-isolation test requires `staging-bytedepth.bytedepth.cn`, `X-Robots-Tag`, `Referrer-Policy`, exact `robots.txt` body, `/feed.xml` and `/sitemap.xml` 404 locations, `BYTEDEPTH_ENVIRONMENT`, production `BYTEDEPTH_SITE_URL`, and `environment == 'staging'` conditions around RSS links. Assert the old Cookie/query routing symbols are absent from current runtime files.

- [ ] **Step 2: Run the focused tests and confirm RED.**

  ```bash
  bash scripts/test-staging-search-isolation.sh
  bash scripts/test-staging-preview-route.sh
  ```

  Expected: FAIL because the current branch still contains preview Cookie/query routing and the old staging hostname.

- [ ] **Step 3: Commit the failing contract.**

  ```bash
  git add scripts/test-staging-preview-route.sh scripts/test-staging-search-isolation.sh scripts/test-run-staging-e2e-tests.sh scripts/test-staging-checklist.sh
  git commit -m "test: define environment-aware staging search isolation"
  ```

### Task 2: Implement Nginx and Compose staging isolation

**Files:**
- Modify: `deploy/nginx/staging-root.conf`
- Modify: `deploy/nginx/staging.conf.template`
- Modify: `deploy/docker-compose.staging.yml`
- Modify: `deploy/deploy-staging.sh`
- Modify: `deploy/.env.example` only if its staging guidance needs the explicit site URL
- Test: `scripts/test-staging-search-isolation.sh`, `scripts/test-deploy-staging.sh`

**Interfaces:**
- Consumes: `BYTEDEPTH_DOMAIN=staging-bytedepth.bytedepth.cn` and `BYTEDEPTH_ENVIRONMENT=staging`.
- Produces: exact-host staging proxy, crawler suppression headers, no-sitemap robots response, and 404 RSS/sitemap endpoints.

- [ ] **Step 1: Remove preview maps and Cookie routing.**

  Delete the `staging_preview`/`preview` maps and make the staging template proxy only the exact `${BYTEDEPTH_DOMAIN}` server. Keep the HTTP default server non-proxying and preserve TLS `server_name` validation.

- [ ] **Step 2: Add search-isolation headers and endpoints.**

  Add `X-Robots-Tag "noindex, nofollow, noarchive" always` and `Referrer-Policy "no-referrer" always` to the staging HTTPS server. Add exact locations returning 404 for `/feed.xml` and `/sitemap.xml`. Make `/robots.txt` return only `User-agent: *` and `Disallow: /` without a `Sitemap:` line.

- [ ] **Step 3: Pin staging application environment.**

  Ensure the staging Compose service passes `BYTEDEPTH_ENVIRONMENT=staging` and defaults `BYTEDEPTH_SITE_URL` to `https://bytedepth.cn`; preserve the production Compose configuration unchanged.

- [ ] **Step 4: Fail fast on an unprepared staging host.**

  In `deploy/deploy-staging.sh`, require `.env` to name `staging-bytedepth.bytedepth.cn`, reject a non-production `BYTEDEPTH_SITE_URL`, and require a readable TLS certificate/key whose SAN covers the exact staging hostname. This prevents the new Host from falling through to a shared career/toolbox Nginx route.

- [ ] **Step 5: Run Nginx syntax and focused contracts.**

  ```bash
  bash scripts/test-staging-search-isolation.sh
  docker run --rm -v "$PWD/deploy/nginx/staging-root.conf:/etc/nginx/nginx.conf:ro" nginx:alpine nginx -t
  ```

- [ ] **Step 6: Commit Nginx/Compose isolation.**

  ```bash
  git add deploy/nginx deploy/docker-compose.staging.yml deploy/.env.example scripts/test-staging-search-isolation.sh
  git commit -m "feat: isolate staging search discovery at nginx"
  ```

### Task 3: Make RSS discovery environment-aware in application templates

**Files:**
- Modify: `bytedepth-start/src/main/resources/templates/fragments/nav.html`
- Modify: `bytedepth-start/src/main/resources/templates/fragments/pwa-head.html`
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/ThemeAssetsTest.java`
- Delete: `tests/e2e/staging-preview-setup.js`
- Delete: `bytedepth-start/src/test/js/staging-preview-setup.test.js`
- Modify: `playwright.config.mjs`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/ThemeAssetsTest.java`

**Interfaces:**
- Consumes: `EnvironmentAttributeAdvice` model attribute `environment`.
- Produces: RSS navigation and RSS alternate link in production, absent in staging; Playwright configuration without Cookie bootstrap state.

- [ ] **Step 1: Add failing Java asset assertions.**

  Require the RSS nav anchor and RSS alternate link to carry `th:if="${environment != 'staging'}"`; require staging meta robots in the common head fragment. Run the targeted Maven test and confirm it fails against the current unconditional templates.

- [ ] **Step 2: Implement the environment conditions.**

  Wrap only RSS navigation and RSS auto-discovery with the staging condition. Add staging-only HTML meta robots. Do not conditionally remove production sitemap generation in Java; Nginx blocks those endpoints only in staging.

- [ ] **Step 3: Remove obsolete preview bootstrap code.**

  Restore Playwright to its normal `E2E_BASE_URL` behavior, remove preview global setup/storage state, and delete its unit test. The staging runner will use the dedicated hostname directly.

- [ ] **Step 4: Run targeted tests.**

  ```bash
  ./mvnw -pl bytedepth-start -am -Dtest=ThemeAssetsTest test
  npm test -- --run bytedepth-start/src/test/js
  ```

- [ ] **Step 5: Commit environment-aware templates.**

  ```bash
  git add bytedepth-start/src/main/resources/templates bytedepth-start/src/test/java/manfred/bytedepth/ThemeAssetsTest.java playwright.config.mjs tests/e2e bytedepth-start/src/test/js
  git commit -m "feat: hide staging rss discovery by environment"
  ```

### Task 4: Switch E2E, sync, docs, and knowledge-base addresses

**Files:**
- Modify: `deploy/run-staging-e2e-tests.sh`
- Modify: `scripts/test-run-staging-e2e-tests.sh`
- Modify: `deploy/sync-prod-to-staging.sh`
- Modify: `AGENTS.md`, `deploy/README.md`, `docs/releases/README.md`
- Modify: `docs/engineering/git-workflow.md`, `docs/engineering/unified-release-pipeline.md`, `docs/agent-guides/maven.md`
- Modify: `docs/superpowers/specs/2026-08-23-staging-environment-design.md`, `docs/superpowers/plans/2026-08-23-staging-environment.md`, `docs/superpowers/plans/2026-09-10-network-map.md`, `docs/superpowers/plans/2026-09-10-test-boundaries.md`, `docs/superpowers/specs/2026-09-08-rss-discovery-and-sync-design.md`, `docs/superpowers/specs/2026-09-13-unified-release-pipeline-design.md`
- Modify: `docs/releases/CHANGELOG.md`
- Test: `scripts/test-staging-search-isolation.sh`, `scripts/test-staging-checklist.sh`

**Interfaces:**
- Consumes: exact hostname and environment-specific search isolation from Tasks 2–3.
- Produces: no current operational command or instruction using the old preview URL or old staging hostname.

- [ ] **Step 1: Change machine-run URLs.**

  Set `E2E_BASE_URL=https://staging-bytedepth.bytedepth.cn`, change sync health checks, release checks, network-map checks, and acceptance examples. Remove bootstrap/storage-state exports and preview query suffixes.

- [ ] **Step 2: Update the canonical knowledge-base rule.**

  State that staging acceptance uses `https://staging-bytedepth.bytedepth.cn/`, production keeps RSS/sitemap, staging returns noindex and 404 for RSS/sitemap, and the hostname is not authentication. Preserve historical facts only where needed; current instructions must use the new hostname.

- [ ] **Step 3: Add an Unreleased changelog entry.**

  Add a categorized `### Changed` entry describing the new staging hostname and environment-aware search isolation before the first staging deployment.

- [ ] **Step 4: Run address and checklist contracts.**

  ```bash
  bash scripts/test-staging-search-isolation.sh
  bash scripts/test-run-staging-e2e-tests.sh
  bash scripts/test-staging-checklist.sh
  git diff --check
  ```

- [ ] **Step 5: Commit synchronized operations knowledge.**

  ```bash
  git add AGENTS.md deploy docs scripts
  git commit -m "docs: switch staging operations to dedicated hostname"
  ```

### Task 5: Run full quality, deploy staging, and verify production separation

**Files:**
- Test: all files changed by Tasks 1–4

- [ ] **Step 1: Run local dependency preflight and quality.**

  ```bash
  npm ci --ignore-scripts --no-audit --no-fund
  bash scripts/run-local-quality.sh
  ```

  Stop on any failure or WARNING.

- [ ] **Step 2: Verify DNS and TLS prerequisites before deployment.**

  Confirm `staging-bytedepth.bytedepth.cn` resolves to the staging host and the staging host has a certificate covering the exact name. If either prerequisite is absent, stop and report the external DNS/cert action required; do not deploy a hostname that will serve the wrong certificate.

- [ ] **Step 3: Deploy the branch to staging.**

  ```bash
  ssh -i ~/.ssh/ubuntu_2.pem ubuntu@124.221.143.25 \
    "cd /opt/bytedepth && sudo ./deploy/deploy-staging.sh fix/production-entry-staging-preview"
  ```

- [ ] **Step 4: Run real staging checks.**

  Verify the new hostname returns 200 with noindex headers, exact robots body, RSS/sitemap 404, and no RSS alternate link; verify production still returns its RSS and sitemap. Then run `sudo ./deploy/run-staging-integration-tests.sh` and `sudo ./deploy/run-staging-e2e-tests.sh` on staging and confirm both evidence records bind the deployed full SHA.

- [ ] **Step 5: Report the staging acceptance URL and stop before merge.**

  Provide `https://staging-bytedepth.bytedepth.cn/` for owner acceptance. Do not merge or publish production until explicit staging acceptance.
