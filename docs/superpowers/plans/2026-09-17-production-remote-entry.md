# Production Remote Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `deploy/deploy-production-remote.sh` the only local production deployment entry while keeping `deploy/deploy-production.sh` as a production-host-only implementation.

**Architecture:** The local wrapper validates a new immutable SemVer tag, connects to `175.24.197.202` with an explicitly supplied SSH key, starts the existing host-side deploy detached, polls the same remote log/history, and runs the host-side read-only verification. It must detect an existing deployment or running task before starting another one. The host script's non-root guard will explicitly point operators to the remote wrapper instead of suggesting local sudo.

**Tech Stack:** Bash, OpenSSH, remote `sudo`, existing Docker/Compose deploy scripts, shell contract tests.

**Spec:** `docs/superpowers/specs/2026-09-16-production-entry-and-staging-preview-design.md`

## Global Constraints

- Production accepts only a new annotated stable SemVer tag; never deploy `main`, a branch, a bare SHA, or a previously deployed tag.
- The local wrapper must not require local root; remote privileged operations use non-interactive `sudo -n` and fail fast if remote authorization is unavailable.
- Remote work must be detached from the SSH session and must leave a queryable log/status trail.
- No command output may expose `.env`, SSH private-key contents, or deployment secrets.
- Any WARNING or failure in deployment or verification stops the process and is reported; no output may be called successful while containing WARNING.
- The full Compose deployment remains delegated to `deploy/bootstrap-ops-deploy.sh` through the existing host-side script.

### Task 1: Add a red test for the host-only boundary and local wrapper contract

**Files:**
- Create: `scripts/test-deploy-production-remote.sh`
- Modify: `scripts/test-deploy-production.sh`
- Test: the two shell contract scripts themselves

**Interfaces:**
- Consumes: `deploy/deploy-production.sh` and the future `deploy/deploy-production-remote.sh`.
- Produces: static assertions for later implementation and checklist wiring.

- [ ] **Step 1: Write the failing contract assertions.**

  Assert that `deploy/deploy-production-remote.sh` is executable and contains the exact production host, remote root, explicit `BYTEDEPTH_PRODUCTION_SSH_KEY` and known_hosts requirements, SemVer validation, SSH invocation, remote `nohup`, remote log polling, duplicate-tag guard, and remote `scripts/verify-production-release.sh` invocation. Assert that the host script's root guard says it must run on the production host and names `deploy/deploy-production-remote.sh` as the local entry.

  Add a fixture-driven fake-SSH path to `scripts/test-deploy-production-remote.sh`: the fake SSH command records arguments, returns a clean remote state for the first probe, records the detached start command, returns a successful release-history record and deployment log on polling, and records the verification command. Add failure cases for an unreadable key, invalid tag, already deployed tag, remote busy task, and remote verification failure.

- [ ] **Step 2: Run the focused contract tests to verify they fail.**

  Run:

  ```bash
  bash scripts/test-deploy-production.sh
  bash scripts/test-deploy-production-remote.sh
  ```

  Expected: the existing production contract passes its current checks, while the new test fails because the remote wrapper and host-only wording are not present.

- [ ] **Step 3: Commit the red tests.**

  ```bash
  git add scripts/test-deploy-production.sh scripts/test-deploy-production-remote.sh
  git commit -m "test: define local production deployment entry contract"
  ```

### Task 2: Implement the host-only guard and remote deployment wrapper

**Files:**
- Modify: `deploy/deploy-production.sh:4-7`
- Create: `deploy/deploy-production-remote.sh`
- Test: `scripts/test-deploy-production.sh`, `scripts/test-deploy-production-remote.sh`

**Interfaces:**
- Consumes: `TAG` argument and required `BYTEDEPTH_PRODUCTION_SSH_KEY` plus `BYTEDEPTH_PRODUCTION_SSH_KNOWN_HOSTS` environment variables.
- Produces: `deploy/deploy-production-remote.sh <tag>`; exit 0 only after remote deploy and verification pass.

- [ ] **Step 1: Change the host-only error without weakening the root guard.**

  Keep the `EUID` rejection, but print a message equivalent to:

  ```text
  This is a production-host-only script. From the local checkout run:
  BYTEDEPTH_PRODUCTION_SSH_KEY="$HOME/.ssh/ubuntu_2.pem" BYTEDEPTH_PRODUCTION_SSH_KNOWN_HOSTS="$HOME/.ssh/known_hosts" ./deploy/deploy-production-remote.sh vX.Y.Z
  ```

  Do not suggest `sudo ./deploy/deploy-production.sh` from the local checkout.

- [ ] **Step 2: Implement local argument and credential validation.**

  In `deploy/deploy-production-remote.sh`, set readonly constants `PRODUCTION_USER=ubuntu`, `PRODUCTION_HOST=175.24.197.202`, `REMOTE_ROOT=/opt/bytedepth`, and `REMOTE_LOG=/tmp/bytedepth-production-${TAG}.log`. Require readable `BYTEDEPTH_PRODUCTION_SSH_KEY` and `BYTEDEPTH_PRODUCTION_SSH_KNOWN_HOSTS`; use `ssh -i ... -o IdentitiesOnly=yes -o BatchMode=yes -o UserKnownHostsFile=... -o StrictHostKeyChecking=yes`. Reject any tag that does not match `^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$`.

- [ ] **Step 3: Implement remote preflight and idempotency checks.**

  Use one remote shell probe to verify `/opt/bytedepth`, remote `sudo -n true`, the annotated tag fetch path, and the root-owned release history. If `version=$TAG` already exists, exit with a duplicate-deployment message. If the remote state file reports `RUNNING` or the matching log has an active deploy process, exit with a status-only message and do not start a second task.

- [ ] **Step 4: Implement detached start, polling, and verification.**

  Start exactly one remote command of the form:

  ```bash
  cd /opt/bytedepth && sudo -n nohup ./deploy/deploy-production.sh "$TAG" >"/tmp/bytedepth-production-$TAG.log" 2>&1 </dev/null &
  ```

  Poll the same log and release history every 10 seconds for at most 3600 seconds. Treat the exact history record with the requested tag and 40-character commit as deploy success; treat a completed process without that record, a log containing `WARNING`, or an explicit remote verification failure as failure. After the history record appears, run `sudo -n ./scripts/verify-production-release.sh "$TAG"` remotely and return its exit status. Print the remote log path on both success and failure without printing credentials.

- [ ] **Step 5: Run focused tests to verify the implementation passes.**

  Run:

  ```bash
  bash scripts/test-deploy-production.sh
  bash scripts/test-deploy-production-remote.sh
  ```

  Expected: both contract tests pass, including all duplicate, busy, invalid-input, and verification-failure cases.

- [ ] **Step 6: Commit the implementation.**

  ```bash
  git add deploy/deploy-production.sh deploy/deploy-production-remote.sh scripts/test-deploy-production.sh scripts/test-deploy-production-remote.sh
  git commit -m "fix: add canonical local production deployment entry"
  ```

### Task 3: Wire the checklist and all production instructions to the canonical entry

**Files:**
- Modify: `scripts/check-staging-checklist.sh`
- Modify: `scripts/test-staging-checklist.sh`
- Modify: `deploy/README.md`
- Modify: `docs/releases/README.md`
- Modify: `docs/engineering/unified-release-pipeline.md`
- Modify: `docs/superpowers/specs/2026-09-13-unified-release-pipeline-design.md`
- Modify: `docs/engineering/gotchas.md`
- Modify: `AGENTS.md` if the canonical local-entry rule is not already explicit

**Interfaces:**
- Consumes: `deploy/deploy-production-remote.sh` and its contract test.
- Produces: one documented local command and one explicitly labeled host-internal command.

- [ ] **Step 1: Add the remote wrapper to the one-command checklist.**

  Add `bash "$SOURCE_ROOT/scripts/test-deploy-production-remote.sh"` to `scripts/check-staging-checklist.sh`, and assert the exact line in `scripts/test-staging-checklist.sh`.

- [ ] **Step 2: Replace ambiguous production commands in current process docs.**

  Document the local command as:

  ```bash
  BYTEDEPTH_PRODUCTION_SSH_KEY="$HOME/.ssh/ubuntu_2.pem" BYTEDEPTH_PRODUCTION_SSH_KNOWN_HOSTS="$HOME/.ssh/known_hosts" ./deploy/deploy-production-remote.sh vX.Y.Z
  ```

  Label `cd /opt/bytedepth && sudo ./deploy/deploy-production.sh vX.Y.Z` as the remote implementation detail only. Explain that running the host script from the local checkout is invalid.

- [ ] **Step 3: Add a static no-bare-command check.**

  Make the contract test reject current process documentation that presents `deploy/deploy-production.sh <tag>` as the operator command without the `/opt/bytedepth` remote context or the canonical remote wrapper. Keep historical Changelog entries unchanged unless they are factual release records.

- [ ] **Step 4: Run documentation and checklist tests.**

  ```bash
  bash scripts/test-deploy-production-remote.sh
  bash scripts/test-staging-checklist.sh
  bash scripts/check-staging-checklist.sh
  git diff --check
  ```

- [ ] **Step 5: Commit the documentation wiring.**

  ```bash
  git add AGENTS.md deploy/README.md docs/releases/README.md docs/engineering/unified-release-pipeline.md docs/engineering/gotchas.md docs/superpowers/specs/2026-09-13-unified-release-pipeline-design.md scripts/check-staging-checklist.sh scripts/test-staging-checklist.sh
  git commit -m "docs: standardize local production deployment instructions"
  ```

### Task 4: Verify the production-entry subsystem

**Files:**
- Test: all files from Tasks 1–3

- [ ] **Step 1: Run the subsystem gate.**

  ```bash
  bash scripts/test-deploy-production.sh
  bash scripts/test-deploy-production-remote.sh
  bash scripts/test-staging-checklist.sh
  git diff --check
  ```

- [ ] **Step 2: Confirm no production command was run from the local worktree.**

  Use `git status --short --branch` and preserve the worktree state; actual production deployment is not part of local verification. Production execution remains a separate staging-accepted release operation through the new wrapper.

- [ ] **Step 3: Commit any final test-only correction.**

  ```bash
  git add deploy scripts docs AGENTS.md
  git commit -m "test: finalize production entry safeguards"
  ```
