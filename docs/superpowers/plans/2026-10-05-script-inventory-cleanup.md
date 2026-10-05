# Script Inventory Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove obsolete project-side deployment contracts, repair the active repository checklist contracts, and document the ownership and invocation boundary of scripts/configuration that remain.

**Architecture:** Keep local formatter/quality gates in `scripts/`; keep `deploy/` only where it provides release-platform Host Agent runtime or staging integration/E2E helpers; remove tests that assert deleted project deployment entrypoints. Add short file-header contracts rather than duplicating operational instructions in every script.

**Tech Stack:** Bash, Python, Maven Wrapper, Prettier, JUnit static asset contracts.

**Spec:** Existing release-platform-only rules in `AGENTS.md`, `docs/engineering/release-platform-only.md`, and `deploy/README.md`.

## Global Constraints

- The project repository must not deploy staging or production directly.
- Release-platform and Host Agent remain the only release execution path.
- Do not delete `deploy/` runtime/test assets until their active references and platform boundary are checked.
- Every retained operational script must state its caller, side-effect boundary, and whether it is safe for local execution.
- All changes must pass `bash scripts/check-staging-checklist.sh` before merge; no WARNING may be ignored.

### Task 1: Remove obsolete project deployment contract tests

**Files:**

- Delete: `scripts/test-host-native-runtime.sh`
- Delete: `scripts/test-production-runtime.sh`
- Delete: `scripts/test-project-ownership.sh`
- Delete: `scripts/test-staging-native-stack.sh`
- Delete: `scripts/test-staging-preview-route.sh`
- Delete: `scripts/test-sync-staging-certificate.sh`
- Delete: `scripts/test-merge-main-after-quality.sh`

- [ ] Verify each deleted test only references removed project-side installers/deployers or the superseded merge flow.
- [ ] Run `rg` to ensure active docs/checklists no longer invoke the deleted tests.
- [ ] Commit the deletion as one focused cleanup commit.

### Task 2: Repair active contract checks

**Files:**

- Modify: `scripts/test-host-native-docs.sh`
- Modify: `scripts/test-run-local-quality.sh`
- Modify: `docs/releases/README.md` only if the contract wording is genuinely missing.

- [ ] Make the documentation contract match the authoritative wording without weakening the SHA requirement.
- [ ] Replace any empty-string arithmetic check with an explicit fail-closed presence check.
- [ ] Run the affected contract tests and `bash scripts/check-staging-checklist.sh`.
- [ ] Commit the contract repair separately.

### Task 3: Document retained entry points and runtime assets

**Files:**

- Modify: `scripts/format-code.sh`, `scripts/format-check.sh`, `scripts/run-local-quality.sh`, `scripts/verify-changed-coverage.sh`, `scripts/check-release-readiness.sh`, `scripts/check-changelog-order.sh`, `scripts/check-staging-changelog-change.sh`.
- Modify: `scripts/check-staging-checklist.sh`, `scripts/test-release-platform-only.sh`, `scripts/test-maven-runtime.sh`, `scripts/test-platform-portability.sh`, `scripts/test-host-native-docs.sh`, `scripts/test-run-local-quality.sh`, `scripts/test-staging-checklist.sh`.
- Modify: `deploy/bootstrap-staging-runtime.sh`, `deploy/run-staging-integration-tests.sh`, `deploy/run-staging-e2e-tests.sh`, and their directly sourced `deploy/lib/*.sh` files.
- Modify: `deploy/README.md` with a concise ownership table if needed.

- [ ] Add file-header comments stating purpose, caller, side effects, and local/Host-Agent boundary.
- [ ] Do not add duplicate step-by-step deployment instructions to scripts; link to `deploy/README.md` or `release-platform-only.md`.
- [ ] Preserve executable modes and shell portability.
- [ ] Commit documentation comments separately from behavior changes.

### Task 4: Verify and review

- [ ] Run `bash scripts/check-staging-checklist.sh`.
- [ ] Run the retained script contract tests affected by the cleanup.
- [ ] Run `bash scripts/format-check.sh --changed` and `git diff --check`.
- [ ] Inspect `git diff --stat`, deleted-file list, and all remaining references before creating the PR.
- [ ] Request code review before merge.
