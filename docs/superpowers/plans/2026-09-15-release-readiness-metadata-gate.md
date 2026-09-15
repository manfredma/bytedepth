# 发布就绪变更记录门禁实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `CHANGELOG.md` 的 `Unreleased` 要求变成覆盖本地、CI、staging、合并和发布入口的 fail-closed 自动门禁，阻止运行时改动在没有变更说明时进入 staging。

**Architecture:** 新增一个无副作用的 Bash 检查器，接收目标提交和基线引用，统一判定变更范围与 `Unreleased` 结构。各入口只调用该检查器，不复制解析逻辑；契约测试用临时 Git 仓库验证真实退出码、错误路径和“检查失败不构建/不部署”。

**Tech Stack:** Bash、Git、GitHub Actions、现有 shell 契约测试、Markdown。

**Spec:** `docs/superpowers/specs/2026-09-15-release-readiness-metadata-gate-design.md`

## Global Constraints

- `CHANGELOG.md` 同时包含 `## Unreleased` 和正式 `## [vX.Y.Z] - YYYY-MM-DD` 条目。
- 任何用户可见、运行时、部署或配置变更在首次 staging 前必须拥有有效 `Unreleased`。
- 纯文档维护改动可豁免；无法判断变更范围或无法取得基线时必须失败。
- 所有 staging、集成、E2E 和生产流程中的 `WARNING` 均必须导致失败。
- 不访问或输出 `.env`、凭据、Docker、数据库或外部服务；不新增 Maven 模块。
- 每个新增行为先写失败测试并确认失败，再写最小实现；实现后运行完整本地质量门禁。

---

### Task 1: 共享发布就绪检查器与单元契约测试

**Files:**
- Create: `scripts/check-release-readiness.sh`
- Create: `scripts/test-check-release-readiness.sh`

**Interfaces:**
- Consumes: optional `--target <ref>` (default `HEAD`), `--base <ref>` (default `origin/main`), and `--mode candidate|release` (default `candidate`); repository root is derived from the script location.
- Produces: exit `0` when the target has no runtime change or a valid `Unreleased`; exit non-zero with a remediation message otherwise.

- [ ] **Step 1: Write failing tests** in a temporary repository created by the test script. Cover these exact cases:

```bash
run_check() {
    (cd "$TEMP_REPO" && "$SOURCE_ROOT/scripts/check-release-readiness.sh" --target HEAD --base base)
}

# Runtime change with no Unreleased: must fail.
git -C "$TEMP_REPO" checkout -q -b candidate base
mkdir -p "$TEMP_REPO/src/main/java"
printf 'class Change {}\n' > "$TEMP_REPO/src/main/java/Change.java"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm runtime-change
if run_check >/dev/null 2>&1; then exit 1; fi

# Valid Unreleased category and list item: must pass.
printf '%s\n' '## Unreleased' '' '### Added' '' '- New capability.' > "$TEMP_REPO/docs/releases/CHANGELOG.md"
git -C "$TEMP_REPO" add .
git -C "$TEMP_REPO" commit -qm changelog
run_check
```

Also add cases for missing category, empty category, comment-only item, docs-only change, missing base ref, and a change under `deploy/`; each assertion must check the expected success/failure status.

- [ ] **Step 2: Run the focused contract test to verify RED**

Run: `bash scripts/test-check-release-readiness.sh`

Expected: failure because `scripts/check-release-readiness.sh` does not exist.

- [ ] **Step 3: Implement the minimal checker**

Implement the following behavior in order:

1. Parse only `--target` and `--base`; reject unknown or missing values.
2. Resolve both refs with `git rev-parse`; if either fails, exit non-zero.
3. Collect changed paths with `git diff --name-only "$BASE...$TARGET"` plus the working-tree/index diff when `TARGET=HEAD`.
4. Treat only paths under `docs/` (except `docs/releases/CHANGELOG.md`), `AGENTS.md`, and root explanation files as documentation-only; treat files under `src/test/`, `tests/`, and `frontend/tests/` as non-runtime; treat every other path as runtime-affecting so unknown paths fail closed.
5. In `candidate` mode, for runtime-affecting paths extract the `## Unreleased` section up to the next `## ` heading and require one allowed `###` category followed by a non-empty `- ` list item. Reject HTML-comment-only content. In `release` mode, accept a clean main whose runtime diff is empty (the existing formal version-title check remains the source of truth for the release entry); if the release tree still has a runtime diff, apply the same `Unreleased` validation.
6. Print the changed paths and the exact remediation (`add a categorized non-empty Unreleased entry`) on failure; never edit files.

- [ ] **Step 4: Run the focused contract test to verify GREEN**

Run: `bash scripts/test-check-release-readiness.sh`

Expected: all focused cases pass with no `WARNING` output.

- [ ] **Step 5: Commit the checker and its contract test**

```bash
git add scripts/check-release-readiness.sh scripts/test-check-release-readiness.sh
git commit -m "test: define release readiness metadata gate"
```

### Task 2: Enforce the gate in local quality and CI

**Files:**
- Modify: `scripts/run-local-quality.sh`
- Modify: `scripts/test-run-local-quality.sh`
- Modify: `.github/workflows/quality.yml` only if its command does not already invoke `run-local-quality.sh`

**Interfaces:**
- Consumes: `scripts/check-release-readiness.sh` with default `HEAD`/`origin/main`.
- Produces: local and PR quality fail before build/test when runtime changes lack `Unreleased`.

- [ ] **Step 1: Extend the contract test first**

Add a static assertion that `run-local-quality.sh` contains:

```bash
bash scripts/check-release-readiness.sh
```

and that it appears before `npm ci`, Maven, `npm test`, and lint. Add an assertion that the workflow calls the local quality entry rather than implementing a second Changelog parser.

- [ ] **Step 2: Run the focused contract test to verify RED**

Run: `bash scripts/test-run-local-quality.sh`

Expected: failure because the entry point does not call the checker.

- [ ] **Step 3: Add the checker call**

Insert immediately after `cd "$SOURCE_ROOT"`:

```bash
bash scripts/check-release-readiness.sh
```

Do not add a second implementation to the workflow.

- [ ] **Step 4: Run the focused contract test to verify GREEN**

Run: `bash scripts/test-run-local-quality.sh`

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add scripts/run-local-quality.sh scripts/test-run-local-quality.sh .github/workflows/quality.yml
git commit -m "ci: enforce release metadata in quality gate"
```

### Task 3: Enforce the gate before staging build and deployment

**Files:**
- Modify: `deploy/deploy-staging.sh`
- Modify: `scripts/test-deploy-staging.sh`

**Interfaces:**
- Consumes: fetched candidate SHA and `origin/main` from the shared checker.
- Produces: staging deployment refusal before runtime preflight/build/rollout when the candidate is not release-ready.

- [ ] **Step 1: Add failing static and behavioral assertions**

Assert the staging script fetches both candidate and `main`, calls the shared checker after candidate resolution/checkout, and calls it before `bootstrap-ops-deploy.sh`. Add a mocked deployment test where checker returns non-zero and assert the rollout command is never reached.

- [ ] **Step 2: Run `bash scripts/test-deploy-staging.sh` and verify RED**

Expected: failure because the staging script currently has no release-readiness call.

- [ ] **Step 3: Implement the gate**

Change the fetch to include `main`:

```bash
git_cmd fetch --force --no-recurse-submodules origin "$REF" main
```

After the named-ref validation and detached checkout, call:

```bash
bash scripts/check-release-readiness.sh --target "$COMMIT" --base origin/main
```

Place this before runtime preflight and before any build/rollout; do not invalidate evidence or append deployment history until the check passes.

- [ ] **Step 4: Run the focused contract test to verify GREEN**

Run: `bash scripts/test-deploy-staging.sh`

Expected: all static and mocked refusal assertions pass.

- [ ] **Step 5: Commit**

```bash
git add deploy/deploy-staging.sh scripts/test-deploy-staging.sh
git commit -m "deploy: gate staging on release metadata"
```

### Task 4: Enforce the gate at merge and release preparation

**Files:**
- Modify: `scripts/merge-main-after-quality.sh`
- Modify: `scripts/prepare-release.sh`
- Modify: `scripts/test-prepare-release.sh`
- Create or modify: `scripts/test-merge-main-after-quality.sh`

**Interfaces:**
- Consumes: remote candidate SHA before merge and clean `main` after merge.
- Produces: merge refusal for an unready candidate and release-time defense-in-depth without replacing formal version-title/evidence checks.

- [ ] **Step 1: Add failing assertions**

Test that merge preparation calls `bash scripts/check-release-readiness.sh --target "$SHA" --base origin/main --mode candidate` after fetching the candidate and before `git push ...:refs/heads/main`. Test that `prepare-release.sh` calls `bash scripts/check-release-readiness.sh --target HEAD --base origin/main --mode release` before `release:prepare`. Add a fixture with a versioned Changelog and no `Unreleased` plus an empty runtime diff to prove release mode accepts a correctly frozen main; keep the existing formal version-title assertion as a separate check.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `bash scripts/test-prepare-release.sh` and `bash scripts/test-merge-main-after-quality.sh`.

Expected: new assertions fail because neither entry point invokes the shared checker.

- [ ] **Step 3: Implement calls and preserve ordering**

In `merge-main-after-quality.sh`, call the candidate-mode command immediately after resolving `SHA` and before the quality wait/push sequence. In `prepare-release.sh`, call the release-mode command after the clean-tree check and before `release:prepare`; retain the existing formal `## [$TAG]`, evidence, coverage, and Tag checks. No Release Plugin command may run after a failed check.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run: `bash scripts/test-prepare-release.sh && bash scripts/test-merge-main-after-quality.sh`.

Expected: all assertions pass.

- [ ] **Step 5: Commit**

```bash
git add scripts/merge-main-after-quality.sh scripts/prepare-release.sh scripts/test-prepare-release.sh scripts/test-merge-main-after-quality.sh
git commit -m "release: enforce readiness before merge and tag"
```

### Task 5: Make the written process unambiguous and wire the checklist

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/releases/README.md`
- Modify: `docs/engineering/git-workflow.md`
- Modify: `docs/engineering/unified-release-pipeline.md`
- Modify: `scripts/check-staging-checklist.sh`
- Modify: `scripts/test-staging-checklist.sh`

**Interfaces:**
- Consumes: the final shared checker and its exact failure contract.
- Produces: one canonical wording and static checks proving every required entry point remains wired.

- [ ] **Step 1: Add documentation/checklist assertions first**

Assert the canonical sentence appears in the process docs and that the checklist tests the checker file plus calls from local quality, staging deploy, merge, and release scripts. Run the checklist test and verify RED before documentation/wiring changes.

- [ ] **Step 2: Rewrite contradictory wording**

Replace the Changelog introduction with the two-kind definition from the spec. Add an explicit ordered gate to all process documents:

```text
运行时代码/模板/前端/部署/配置变更 → 必须先有有效 CHANGELOG Unreleased → 本地与 CI 门禁 → staging 部署与验收 → 合并 main → 正式版本条目与 Tag。
```

State that any commit after staging invalidates both evidence files and requires a new staging run.

- [ ] **Step 3: Wire checklist coverage**

Make `check-staging-checklist.sh` execute `test-check-release-readiness.sh` and assert all five callers; do not let the checklist merely grep for a filename without checking call order.

- [ ] **Step 4: Run focused checklist tests and verify GREEN**

Run: `bash scripts/test-staging-checklist.sh && bash scripts/check-staging-checklist.sh`

Expected: pass with zero `WARNING` output.

- [ ] **Step 5: Commit**

```bash
git add AGENTS.md docs/releases/README.md docs/engineering/git-workflow.md docs/engineering/unified-release-pipeline.md scripts/check-staging-checklist.sh scripts/test-staging-checklist.sh
git commit -m "docs: make release readiness gate unambiguous"
```

### Task 6: Full verification and staging refusal/acceptance proof

**Files:**
- Modify only if a test exposes a defect; otherwise no additional files.

- [ ] **Step 1: Run all focused shell contracts**

```bash
bash scripts/test-check-release-readiness.sh
bash scripts/test-run-local-quality.sh
bash scripts/test-deploy-staging.sh
bash scripts/test-merge-main-after-quality.sh
bash scripts/test-prepare-release.sh
bash scripts/test-staging-checklist.sh
```

Expected: every command exits `0`; no output contains `WARNING`.

- [ ] **Step 2: Run the complete local quality entry**

Run: `bash scripts/run-local-quality.sh`

Expected: Maven, frontend, coverage, lint, static contracts and checklist all pass.

- [ ] **Step 3: Verify the staging refusal path without touching production data**

Run the mocked refusal case from `scripts/test-deploy-staging.sh` and assert all three observable boundaries: the checker is called with the exact fetched SHA, `bootstrap-ops-deploy.sh` is not invoked, and neither test-history evidence nor deployment history is changed. Do not create a real invalid branch or alter the staging database for this refusal proof.

- [ ] **Step 4: Verify the real staging acceptance path**

On the final feature SHA with valid `Unreleased`, deploy staging, run integration and E2E, and verify both evidence files contain exactly that full SHA and `result=passed`.

- [ ] **Step 5: Run the project’s final pre-release checklist**

Run: `bash scripts/check-staging-checklist.sh` and inspect the full output for warnings before any release evidence, merge, Tag or production action.
