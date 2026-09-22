# Code Block Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add opt-in code-block titles, copy, folding, and explicit multi-language tabs without changing existing ordinary code blocks.

**Architecture:** Keep CommonMark as the source parser. A focused metadata parser recognizes only `title`, `file`, `fold`, and `tabs`; a custom HTML renderer handles only opted-in fenced blocks and delegates all other nodes to the existing renderer. A page-scoped JavaScript component enhances the generated opt-in markup, while its CSS is scoped to the new classes.

**Tech Stack:** Java 25, Spring Boot, CommonMark 0.30.0, OWASP HTML Sanitizer, Thymeleaf, browser JavaScript, Vitest, Playwright.

**Spec:** `docs/architecture/decisions/0013-opt-in-code-block-enhancements.md`

## Global Constraints

- 普通代码块必须继续使用现有 `<pre><code class="language-*">` 路径，不得增加增强包装或 Tab 分组。
- 只有显式 `title`/`file`/`fold`/`tabs` 元数据可以启用增强行为。
- 只有拥有相同 `tabs` 标识且连续的 fenced code blocks 才能组成 Tab。
- 不引入新的 Maven 模块，不把 Obsidian 插件运行时作为网站依赖。
- 新建或切换 worktree 后已先执行 `npm ci --ignore-scripts --no-audit --no-fund`。
- 任何用户可见运行时改动必须保留 `CHANGELOG.md` 的非空 `Unreleased` 条目。

---

### Task 1: Lock metadata parsing and compatibility behavior with unit tests

**Files:**
- Create: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/util/CodeBlockMetadata.java`
- Test: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/util/CodeBlockMetadataTest.java`
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/util/MarkdownRendererTest.java`

**Interfaces:**
- Produces `CodeBlockMetadata.parse(String info)` with immutable fields `language`, `title`, `fold`, and `tabGroup`.
- `parse` treats the first info token as the language, recognizes `title:` and `file:` values, recognizes the bare `fold` flag, recognizes `tabs:<id>`, and returns an unenhanced result for ordinary language-only info.

- [ ] **Step 1: Write failing parser tests** for language-only input, title/file aliases, quoted titles with spaces, fold, tab groups, malformed/unknown parameters, and empty input.
- [ ] **Step 2: Run the focused Maven test and verify it fails** because the parser type does not exist.
- [ ] **Step 3: Implement the smallest parser** with bounded tokenization and no interpretation of unknown parameters.
- [ ] **Step 4: Run parser and renderer tests** and verify all pass without warnings.
- [ ] **Step 5: Add renderer regression assertions** proving ordinary single and adjacent language-only fenced blocks do not contain enhancement classes or tab wrappers.

### Task 2: Render only opted-in blocks and explicit contiguous tab groups

**Files:**
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/util/MarkdownRenderer.java`
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/util/MarkdownRendererTest.java`

**Interfaces:**
- `MarkdownRenderer.render(String markdown)` continues to return the existing output for ordinary Markdown.
- Opted-in output uses `bd-code-block`, `bd-code-block__header`, and `bd-code-tabs` classes, with escaped labels and code text.
- Same-group tabs are emitted only for adjacent fenced blocks with the same non-empty `tabs` identifier; unrelated nodes or identifier changes terminate a group.

- [ ] **Step 1: Add failing HTML tests** for an opted-in titled block, folded block, explicit two-language group, group interruption by a paragraph, and unsafe title/code text escaping.
- [ ] **Step 2: Run focused renderer tests and verify the expected failures.**
- [ ] **Step 3: Add a CommonMark HTML node renderer** that walks document siblings, delegates ordinary nodes to the existing renderer, and handles only recognized fenced-block metadata.
- [ ] **Step 4: Extend the sanitizer policy** only for the generated enhancement tags, classes, roles, states, and controlled group metadata; do not broaden raw HTML support.
- [ ] **Step 5: Run the focused renderer tests and verify ordinary output remains unchanged.**

### Task 3: Add isolated copy, fold, and tab interaction

**Files:**
- Create: `bytedepth-start/src/main/resources/static/js/code-blocks.js`
- Create: `bytedepth-start/src/main/resources/static/css/code-blocks.css`
- Create: `bytedepth-start/src/test/js/code-blocks.test.js`
- Modify: `bytedepth-start/src/main/resources/templates/public/posts/detail.html`
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/PostReadingAssetsTest.java`

**Interfaces:**
- `code-blocks.js` binds only `#post-article .bd-code-block` and `.bd-code-tabs`; it must not query or mutate ordinary `pre` elements.
- Copy uses `navigator.clipboard.writeText` with a tested fallback path and exposes an accessible status label.
- Folding uses native `details` state where possible; tabs use buttons with `role="tab"`, `aria-selected`, and panel visibility state.

- [ ] **Step 1: Write failing Vitest tests** proving ordinary `pre` is untouched, enhanced copy copies code text, fold state remains local, tabs switch only their own group, and keyboard/ARIA state updates.
- [ ] **Step 2: Run the focused Vitest file and verify it fails.**
- [ ] **Step 3: Implement the scoped script** with no global listeners except the minimum clipboard fallback behavior.
- [ ] **Step 4: Add scoped CSS** under `.content .bd-code-block*` and responsive overflow rules; do not modify existing `.content pre` declarations.
- [ ] **Step 5: Include the CSS and JS on the article template** and add asset-contract assertions.
- [ ] **Step 6: Run the focused Vitest suite and static asset tests.**

### Task 4: Add regression documentation and quality coverage

**Files:**
- Modify: `docs/releases/CHANGELOG.md`
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/ThemeAssetsTest.java`
- Create or modify: `tests/e2e/code-blocks.spec.js`

- [ ] **Step 1: Add a categorized Unreleased entry** describing opt-in code-block enhancements and ordinary-block compatibility.
- [ ] **Step 2: Add staging E2E coverage** for ordinary code rendering, explicit title/fold controls, copy, and a two-language group; the test must assert no enhancement wrapper for a plain code block.
- [ ] **Step 3: Run Java unit tests, JavaScript tests with coverage, lint, and the repository local quality entrypoint.**
- [ ] **Step 4: Review all output for warnings and stop on any unresolved warning.**
- [ ] **Step 5: Prepare the staging candidate only after the local gates are clean; run full staging integration and E2E according to `deploy/README.md`.**
