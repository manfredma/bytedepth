# Staging Preview Route Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redirect ordinary staging traffic to production while preserving a `?preview=true` cookie-based staging preview flow for team validation and E2E.

**Architecture:** Nginx will recognize only the exact `preview=true` query marker or a short-lived `staging_preview=1` cookie. Requests without either state redirect to `https://bytedepth.cn` with the original path and non-control query parameters. A preview request receives the cookie and is proxied to the existing staging app; no authentication is added, so the marker remains an obscurity/routing mechanism rather than an access-control boundary.

**Tech Stack:** Nginx `map`/server directives, Docker Compose staging template, Bash wrappers, Playwright global setup, shell contract tests, Markdown process documentation.

**Spec:** `docs/superpowers/specs/2026-09-16-production-entry-and-staging-preview-design.md`

## Global Constraints

- The exact preview marker is `?preview=true`; parameter names and values are case-sensitive and must not be silently broadened.
- A request without preview state must never proxy to staging; it must redirect to `https://bytedepth.cn`.
- The preview cookie is not a security credential; documentation must say that explicitly.
- The preview cookie must be `Secure`, `HttpOnly`, `SameSite=Lax`, path `/`, and have a bounded lifetime.
- Staging must emit `X-Robots-Tag: noindex, nofollow, noarchive` and its robots response must disallow crawling.
- All staging E2E, sync, health-check, network-map, release, and knowledge-base examples must use `?preview=true` where they request a staging page.
- Do not expose secrets or modify production data during local verification.

### Task 1: Add failing tests for the exact preview contract

**Files:**
- Create: `scripts/test-staging-preview-route.sh`
- Modify: `scripts/test-run-staging-e2e-tests.sh`
- Modify: `scripts/test-staging-checklist.sh`

**Interfaces:**
- Consumes: `deploy/nginx/staging.conf.template`, `deploy/run-staging-e2e-tests.sh`, `deploy/sync-prod-to-staging.sh`, and the documentation constants.
- Produces: a deterministic static contract test for exact query/cookie/redirection behavior.

- [ ] **Step 1: Write the red assertions.**

  Assert the Nginx template contains an exact `preview=true` branch, a cookie named `staging_preview`, cookie attributes `Secure`, `HttpOnly`, `SameSite=Lax`, a bounded `Max-Age`, an explicit `preview=false` clear path, production redirect target `https://bytedepth.cn`, and `X-Robots-Tag` noindex. Assert that the HTTP default server cannot proxy directly to `bytedepth-app`.

  Assert that the E2E runner exposes a preview bootstrap URL/constant, that its public article discovery uses that bootstrap before `/posts`, and that current staging URL checks include `?preview=true`.

  Scan current operational docs and scripts (excluding historical design/spec/plan text and the new contract itself) for bare staging page URLs and fail when a current command uses `https://staging.bytedepth.cn` without `?preview=true`; permit domain-only references that describe DNS, TLS certificates, Nginx `server_name`, or the cookie-routing implementation.

- [ ] **Step 2: Run the focused test to verify it fails.**

  ```bash
  bash scripts/test-staging-preview-route.sh
  ```

  Expected: FAIL because the current Nginx template proxies every request and the E2E/sync scripts use the bare staging URL.

- [ ] **Step 3: Commit the red contract.**

  ```bash
  git add scripts/test-staging-preview-route.sh scripts/test-run-staging-e2e-tests.sh scripts/test-staging-checklist.sh
  git commit -m "test: define staging preview route contract"
  ```

### Task 2: Implement Nginx preview routing and noindex behavior

**Files:**
- Modify: `deploy/nginx/staging-root.conf`
- Modify: `deploy/nginx/staging.conf.template`
- Modify: `deploy/docker-compose.staging.yml` only if the generated Nginx config requires an additional read-only mount
- Test: `scripts/test-staging-preview-route.sh`

**Interfaces:**
- Consumes: request query `$arg_preview`, cookie `$cookie_staging_preview`, and `BYTEDEPTH_DOMAIN`.
- Produces: default public redirect, exact preview bootstrap, preview cookie persistence, and cookie clearing.

- [ ] **Step 1: Add HTTP-level maps in the staging root config.**

  Define maps in `http {}` for exact query matching and cookie state. Treat only `true` as enable and only `false` as clear; all other values are disabled. Keep the maps before the `include /etc/nginx/conf.d/*.conf` line so the generated server template can consume them.

- [ ] **Step 2: Replace direct public proxying with redirect-first server blocks.**

  For both HTTP and HTTPS requests to `${BYTEDEPTH_DOMAIN}`, route requests without enabled preview state to `301 https://bytedepth.cn$request_uri`. For preview state, continue to the existing `bytedepth-app:8080` proxy with the current forwarded headers. Change the HTTP `default_server` so it cannot serve the staging app directly by IP.

- [ ] **Step 3: Set and clear the preview cookie.**

  On exact `preview=true`, the HTTPS staging server must add `Set-Cookie: staging_preview=1; Max-Age=604800; Path=/; Secure; HttpOnly; SameSite=Lax` while proxying the request. On exact `preview=false`, it must add `Set-Cookie: staging_preview=; Max-Age=0; Path=/; Secure; HttpOnly; SameSite=Lax` and return a `302` to the production URL. Do not treat a manually supplied cookie as a secret; it only selects the same preview route.

- [ ] **Step 4: Add noindex headers and staging robots behavior.**

  Add `X-Robots-Tag: noindex, nofollow, noarchive` to staging responses. Ensure the staging `robots.txt` path cannot advertise production sitemap crawling while in preview mode; the public no-preview request should already redirect to production.

- [ ] **Step 5: Run the focused contract test.**

  ```bash
  bash scripts/test-staging-preview-route.sh
  ```

  Expected: PASS for exact directive checks and FAIL for no bare staging proxy path.

- [ ] **Step 6: Commit the Nginx change.**

  ```bash
  git add deploy/nginx/staging-root.conf deploy/nginx/staging.conf.template deploy/docker-compose.staging.yml scripts/test-staging-preview-route.sh
  git commit -m "feat: route public staging traffic to production"
  ```

### Task 3: Bootstrap the preview cookie in Playwright and staging E2E

**Files:**
- Modify: `playwright.config.mjs`
- Create: `tests/e2e/staging-preview-setup.js`
- Modify: `deploy/run-staging-e2e-tests.sh`
- Modify: `scripts/test-run-staging-e2e-tests.sh`
- Modify: `scripts/test-staging-preview-route.sh`

**Interfaces:**
- Consumes: `E2E_BASE_URL=https://staging.bytedepth.cn`, `E2E_PREVIEW_BOOTSTRAP_URL=https://staging.bytedepth.cn/?preview=true`, and Playwright `storageState`.
- Produces: every E2E browser context starts with a valid staging preview Cookie while local E2E remains unchanged when the bootstrap variable is unset.

- [ ] **Step 1: Add a failing Playwright setup test/contract.**

  Assert that the config enables `globalSetup` only when `E2E_PREVIEW_BOOTSTRAP_URL` is set, and that the setup opens the exact bootstrap URL before saving a storage state consumed by all projects. Assert that the runner exports the bootstrap URL and keeps `E2E_BASE_URL` as the origin without a query string.

- [ ] **Step 2: Implement the preview global setup.**

  In `tests/e2e/staging-preview-setup.js`, use Playwright's request/browser context to visit `process.env.E2E_PREVIEW_BOOTSTRAP_URL`, require a successful response, save storage state to a temporary path, and return/close the context without logging cookies. The setup must fail if the URL does not end in the exact `?preview=true` marker.

- [ ] **Step 3: Wire storage state into Playwright projects.**

  In `playwright.config.mjs`, set `globalSetup` and `use.storageState` only for the staging bootstrap mode; local `npm run test:e2e` must retain the current localhost behavior without a staging dependency.

- [ ] **Step 4: Change the host E2E runner to bootstrap preview state.**

  Keep `readonly E2E_BASE_URL=https://staging.bytedepth.cn` and add `readonly E2E_PREVIEW_BOOTSTRAP_URL=https://staging.bytedepth.cn/?preview=true`. Use the bootstrap URL for article discovery, export both variables for Playwright, and ensure the evidence command records the preview contract without recording credentials or cookies.

- [ ] **Step 5: Update the fixture test and run it.**

  Extend `scripts/test-run-staging-e2e-tests.sh` fake npm/curl assertions to require the origin plus exact bootstrap URL. Run:

  ```bash
  bash scripts/test-run-staging-e2e-tests.sh
  ```

  Expected: PASS, with local fixture output proving that the runner does not use the public no-preview route.

- [ ] **Step 6: Commit the E2E preview bootstrap.**

  ```bash
  git add playwright.config.mjs tests/e2e/staging-preview-setup.js deploy/run-staging-e2e-tests.sh scripts/test-run-staging-e2e-tests.sh scripts/test-staging-preview-route.sh
  git commit -m "test: bootstrap staging preview state for E2E"
  ```

### Task 4: Update sync, health checks, and all knowledge-base addresses

**Files:**
- Modify: `deploy/sync-prod-to-staging.sh`
- Modify: `docs/agent-guides/maven.md`
- Modify: `docs/engineering/git-workflow.md`
- Modify: `docs/engineering/unified-release-pipeline.md`
- Modify: `docs/releases/README.md`
- Modify: `deploy/README.md`
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-08-23-staging-environment-design.md`
- Modify: `docs/superpowers/plans/2026-08-23-staging-environment.md`
- Modify: `docs/superpowers/plans/2026-09-10-network-map.md`
- Modify: `docs/superpowers/plans/2026-09-10-test-boundaries.md`
- Modify: `docs/superpowers/specs/2026-09-08-rss-discovery-and-sync-design.md`
- Modify: `docs/superpowers/specs/2026-09-13-unified-release-pipeline-design.md`
- Modify: `docs/superpowers/specs/2026-09-16-production-entry-and-staging-preview-design.md`
- Test: `scripts/test-staging-preview-route.sh`, `scripts/test-staging-checklist.sh`

**Interfaces:**
- Consumes: exact preview URL contract from Tasks 2–3.
- Produces: no current operational instruction that requests staging content without `?preview=true`.

- [ ] **Step 1: Change machine-run staging health checks.**

  In `deploy/sync-prod-to-staging.sh`, change the final staging request to `https://staging.bytedepth.cn/?preview=true`; make the same change in network-map and staging-environment command examples. Keep DNS, TLS certificate paths, and Nginx `server_name` references domain-only because those are configuration identifiers, not public content requests.

- [ ] **Step 2: Change E2E, integration, release, and owner-acceptance instructions.**

  Every command that fetches a staging page, RSS feed, network map, article list, or health endpoint must include `?preview=true` (preserving any existing query string with `&preview=true`). Explain once in each canonical process document that the first preview request establishes the Cookie and later browser navigation can use clean paths.

- [ ] **Step 3: Add the exact wording rule to the knowledge base.**

  Add a prominent rule to `AGENTS.md` and `deploy/README.md`: “访问 staging 页面必须使用 `https://staging.bytedepth.cn/?preview=true`；不带 `?preview=true` 的公网请求会 301 到 `https://bytedepth.cn`；该参数不是安全认证。” Use the same wording in release and testing guides.

- [ ] **Step 4: Run address and checklist contracts.**

  ```bash
  bash scripts/test-staging-preview-route.sh
  bash scripts/test-run-staging-e2e-tests.sh
  bash scripts/test-staging-checklist.sh
  git diff --check
  ```

  Expected: no current operational script or instruction contains a bare staging page request.

- [ ] **Step 5: Commit synchronized operational knowledge.**

  ```bash
  git add AGENTS.md deploy docs scripts playwright.config.mjs tests/e2e
  git commit -m "docs: standardize staging preview URL across scripts"
  ```

### Task 5: Verify staging-preview subsystem locally

**Files:**
- Test: all files from Tasks 1–4

- [ ] **Step 1: Run static contract checks.**

  ```bash
  bash scripts/test-staging-preview-route.sh
  bash scripts/test-run-staging-e2e-tests.sh
  bash scripts/test-staging-checklist.sh
  git diff --check
  ```

- [ ] **Step 2: Run the repository local quality entry after dependency setup.**

  ```bash
  npm ci --ignore-scripts --no-audit --no-fund
  bash scripts/run-local-quality.sh
  ```

  Expected: all unit tests, frontend tests, lint, coverage and static contracts pass with zero WARNING.

- [ ] **Step 3: Record staging-only acceptance commands.**

  After the branch is deployed to staging, verify the public route returns a 301 to production, the exact preview bootstrap returns staging content and a Cookie, clean preview navigation remains on staging, `preview=false` clears state, and E2E/integration evidence is bound to the deployed SHA. Do not use local results as staging acceptance evidence.

- [ ] **Step 4: Commit only final corrections.**

  ```bash
  git add deploy docs scripts AGENTS.md playwright.config.mjs tests/e2e
  git commit -m "test: finalize staging preview route safeguards"
  ```
