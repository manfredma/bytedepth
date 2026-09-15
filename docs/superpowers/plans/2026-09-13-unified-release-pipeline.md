# Unified Release Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make bytedepth expose the same quality, staging, release, production deployment and production verification interfaces as Career and Toolbox.

**Architecture:** Merge the already accepted staging-pipeline PR before changing this repository, then implement the common entrypoints on a fresh branch from that main. GitHub Actions runs only pure local quality; staging and production stay in root-owned scripts with commit-bound evidence and no credentials in CI.

**Tech Stack:** Bash, GitHub Actions, Java 25, Maven Wrapper 3.9.11, Docker Compose, Playwright, SSH.

**Spec:** `docs/superpowers/specs/2026-09-13-unified-release-pipeline-design.md`

## Global Constraints

- Merge accepted PR #97 before this work; do not implement against the older main.
- All stages reject WARNING; integration/E2E run only in staging.
- Workflow triggers are `pull_request` and `push` to `main`, `feat/**`, `fix/**`, and `docs/**`; it has no staging/production credentials.
- Production accepts only a new annotated SemVer tag created by `scripts/prepare-release.sh`.

---

### Task 1: Establish the correct base and executable local-quality interface

**Files:**
- Modify: `scripts/run-local-quality.sh`, `scripts/test-run-local-quality.sh`, `scripts/check-staging-checklist.sh`, `scripts/test-staging-checklist.sh`

- [ ] Merge accepted #97 with `gh pr merge 97 --squash`, fetch `origin/main`, create `feat/unified-release-pipeline` worktree from it, and rebase this documentation branch before copying its ADR/spec/guide.
- [ ] Write a failing static test asserting `run-local-quality.sh` runs Java 25 Wrapper cache refresh, unit tests, frontend checks, changed coverage, checklist and `git diff --check`.
- [ ] Extend `run-local-quality.sh` with those exact commands and use a stream-based zero-WARNING guard for Maven-producing commands.
- [ ] Add the static test to `check-staging-checklist.sh`; run the test and `bash scripts/run-local-quality.sh`.
- [ ] Commit `build: align local quality entrypoint`.

### Task 2: Add the common GitHub quality workflow

**Files:**
- Create: `.github/workflows/quality.yml`
- Create: `scripts/test-github-quality-workflow.sh`
- Modify: `scripts/check-staging-checklist.sh`, `scripts/test-staging-checklist.sh`

- [ ] Write `test-github-quality-workflow.sh` to require `pull_request`, `push` restricted to `main`, `feat/**`, `fix/**`, and `docs/**`, checkout, Node setup, Temurin Java 25, `bash scripts/run-local-quality.sh`, and forbid SSH host/key, staging domains and production domains.
- [ ] Run the test and confirm it fails because the workflow is absent.
- [ ] Create `quality.yml` with exactly one `quality` job that checks out code, installs ripgrep, configures Node and Temurin 25, then invokes only `bash scripts/run-local-quality.sh`.
- [ ] Add the static test to the checklist; run it plus `bash scripts/check-staging-checklist.sh`.
- [ ] Commit `ci: add shared quality workflow`.

### Task 3: Make staging runtime bootstrap idempotent through `--ensure`

**Files:**
- Modify: `deploy/bootstrap-staging-runtime.sh`, `deploy/lib/staging-runtime.sh`
- Modify: `scripts/test-staging-runtime.sh`

- [ ] Write tests for two commands: no argument remains an explicit refresh, `--ensure` exits successfully without downloads only when manifest, checkout SHA, lockfile and shared Chromium version match; otherwise it performs the existing refresh path.
- [ ] Run the tests to show the missing `--ensure` behavior.
- [ ] Implement argument validation and the manifest fast path under the existing shared lock; preserve existing zero-WARNING scanning and timing evidence on refresh.
- [ ] Run staging-runtime static tests and `bash scripts/check-staging-checklist.sh`.
- [ ] Commit `build: add idempotent staging runtime ensure`.

### Task 4: Standardize production script names and verification

**Files:**
- Rename: `deploy/deploy-release.sh` to `deploy/deploy-production.sh`
- Create: `scripts/verify-production-release.sh`
- Create: `scripts/test-verify-production-release.sh`
- Modify: `deploy/README.md`, `docs/releases/README.md`, `scripts/check-staging-checklist.sh`

- [ ] Write a static test requiring the verifier to accept one stable tag, reject other refs, use the real SNI domain, inspect recorded tag/SHA, call `deploy/ctl.sh` rather than bare Compose, and exercise the documented latest/hot/pagination, post, legacy redirect, column, search, project and image read-only queries.
- [ ] Rename the deploy script and update every repository reference; it must retain annotated-tag, version/POM and duplicate-deployment rejection.
- [ ] Implement `verify-production-release.sh <tag>` so it derives existing post/column/image paths from the live site, rejects non-200 responses and scans relevant service logs for WARNING/errors without printing secrets.
- [ ] Add the verifier test to the checklist; run shell tests and `git diff --check`.
- [ ] Commit `build: standardize production verification`.

### Task 5: Align documentation and enforce the implementation

**Files:**
- Modify: `AGENTS.md`, `docs/README.md`, `docs/engineering/git-workflow.md`, `docs/releases/README.md`, `deploy/README.md`
- Copy from approved docs branch: ADR, design spec and `docs/engineering/unified-release-pipeline.md`

- [ ] Update all references to the canonical production script and 16-step order.
- [ ] Add a checklist/static test that fails if required common entrypoints are missing or workflow triggers differ.
- [ ] Run all shell constraint tests, local quality, and `git diff --check` with zero WARNING.
- [ ] Commit `docs: record unified release pipeline`.

### Task 6: Validate, merge and release

- [ ] Push the branch, open PR, require green `quality`, deploy the candidate to staging, run `bootstrap --ensure`, integration and E2E, and obtain owner acceptance for the changed release infrastructure.
- [ ] Merge; deploy main to staging and recreate both main-SHA evidence records.
- [ ] Copy the two root-owned evidence files to a new local temporary directory, run `prepare-release.sh` with the next SemVer MINOR and next snapshot, then deploy only the resulting tag.
- [ ] Run `verify-production-release.sh <tag>`, record production acceptance/rollback baseline, and remove the merged worktree.
