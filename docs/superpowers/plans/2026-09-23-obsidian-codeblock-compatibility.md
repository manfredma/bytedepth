# Obsidian Codeblock Customizer Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 bytedepth 直接渲染 Obsidian Codeblock Customizer 的 `group/tab/title` 代码块，并在 Obsidian 导入前阻止空行、缺失页签和重复页签造成的分组错误。

**Architecture:** 服务器端将 `tabs` 与 `group` 归一为同一 Tab 分组，将 `tab` 单独保存为页签标签；保留现有 `tabs` 行为。Obsidian 校验器负责 Markdown 源文件的 Codeblock Customizer 分组约束，导入脚本只保留源文本并引用该校验器，不新增 Markdown 重写层。

**Tech Stack:** Java 25, Spring Boot, CommonMark, JUnit 5, Python 3, pytest, Playwright.

**Spec:** `docs/architecture/decisions/0015-obsidian-codeblock-metadata-compatibility.md`

## Global Constraints

- 不新增 Maven 模块。
- 所有代码改动必须先有失败测试，再实现最小修复。
- 普通代码块、旧 `tabs:` 语法和 Mermaid 必须继续渲染。
- 代码块功能改动必须补单元测试和 staging E2E 覆盖。
- 远程验收只使用 staging，不以本机页面作为 E2E 依据。

---

### Task 1: Extend the metadata contract

**Files:**
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/util/CodeBlockMetadata.java`
- Test: `bytedepth-adapter/src/test/java/manfred/bytedepth/adapter/web/util/CodeBlockMetadataTest.java`

- [x] **Step 1: Write failing parser tests** for `group`, `tab`, quoted tab labels, and the empty/default fields.
- [x] **Step 2: Run the focused test and verify it fails** because `tabLabel()` does not exist and `group/tab` are ignored.
- [x] **Step 3: Add `tabLabel` to the immutable metadata record**; map `group` as an alias of `tabs`, map `tab` to the label, and keep existing `tabs`, `title`, `file`, and `fold` behavior.
- [x] **Step 4: Run the focused parser tests** and verify all pass without warnings.

### Task 2: Render plugin-compatible tabs without breaking legacy tabs

**Files:**
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/util/EnhancedCodeBlockRenderer.java`
- Test: `bytedepth-adapter/src/test/java/manfred/bytedepth/adapter/web/util/MarkdownRendererTest.java`

- [x] **Step 1: Write failing renderer tests** proving `tab:Java title:Rule.java` renders `Java` as the tab label and `Rule.java` inside the panel, while `tabs:install title:Example.java` retains the legacy label.
- [x] **Step 2: Run the focused renderer tests and verify the plugin-compatible assertion fails** because the current renderer uses the title as every tab label.
- [x] **Step 3: Update tab label selection** to prefer `tab`, then preserve the existing title-or-language fallback for legacy `tabs` blocks.
- [x] **Step 4: Add a regression assertion** for ordinary blocks and group interruption; run the adapter unit tests.

### Task 3: Add Obsidian-side Codeblock Customizer validation

**Files:**
- Modify: `/Users/maxingfang/.codex/skills/obsidian-note-checker/checker.py`
- Modify: `/Users/maxingfang/.codex/skills/obsidian-note-checker/test_checker.py`
- Modify: `/Users/maxingfang/.codex/skills/obsidian-note-checker/SKILL.md`
- Modify: `/Users/maxingfang/.codex/skills/obsidian-to-bytedepth/SKILL.md`

- [x] **Step 1: Write failing checker tests** for valid contiguous `group/tab` blocks, a blank line inside a group, a paragraph interruption, missing `tab`, duplicate tab labels, and legacy `tabs` blocks.
- [x] **Step 2: Run the checker tests and verify the invalid fixtures fail** with line-specific diagnostics.
- [x] **Step 3: Implement the fenced-block scanner** with quoted metadata parsing and explicit group state; emit errors without changing note contents.
- [x] **Step 4: Document the rule** in the Obsidian checker skill and make the Obsidian-to-bytedepth skill require this check before `import`, `update`, or `sync`.
- [x] **Step 5: Run the checker test suite and the target note checker**.

### Task 4: Add commit-bound E2E coverage

**Files:**
- Modify: `tests/e2e/code-blocks.spec.js`

- [x] **Step 1: Extend the generated staging article** with a Codeblock Customizer-style `group/tab/title` Java/Go/Python group while retaining the legacy `tabs` case.
- [x] **Step 2: Assert the remote page** has `Java`, `Go`, and `Python` tabs, panel titles, line numbers, and working tab switching; legacy fold/copy behavior remains covered.
- [ ] **Step 3: Run the staging E2E only after the candidate has been deployed** and record the commit-bound evidence.

### Task 5: Quality gates and handoff

- [x] **Step 1: Run focused Java and Python tests after each task.**
- [x] **Step 2: Run `bash scripts/run-local-quality.sh` from the isolated worktree and resolve every warning or failure.**
- [ ] **Step 3: Run the required staging deployment, full integration tests, and full E2E suite.**
- [x] **Step 4: Review the diff, verify the ADR/Changelog/documentation ownership boundaries, and request code review before merge.**
