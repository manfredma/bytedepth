# Unified Code Block Component Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将文章正文中的所有顶层 fenced code block 统一渲染为可复制、可展开/收起、带行号和语法高亮的代码组件；普通代码、未标注语言代码以及显式多语言 Tab 代码都必须兼容，且不改变嵌套 Markdown 代码块的既有语义。

**Architecture:** 由 CommonMark 自定义渲染器负责输出统一且可安全清洗的 HTML 结构；前端独立组件脚本负责复制、展开/收起、Tab 切换、行号和渐进式语法高亮；组件样式仅放在代码组件自己的资源文件中。每个代码块保持独立状态，Tab 组只负责切换面板，不共享面板内部控制状态。默认展开，只有显式 `fold` 控制初始收起；可选 `title:`/`file:` 只在笔记明确提供时显示。

**Tech Stack:** Spring Boot/CommonMark + OWASP HTML Sanitizer、原生 JavaScript/CSS、PrismJS（提交固定版本的静态资源）、Vitest、JUnit 5、Playwright。

**Spec:** `docs/architecture/decisions/0014-unified-code-block-component.md`

## Global Constraints

- 只在当前 `feat/code-block-enhancements` worktree 开发，不修改 `main`。
- 任何新增或修改的业务分支都必须有单元测试；本次代码组件分支覆盖率达到项目要求的 100%。
- Maven 使用 `./mvnw` 和 Java 25；前端命令使用已安装 lockfile 依赖，不能裸跑 `npm test`。
- 不得引入新的 Maven module；不改变文章 Markdown 存储格式中未约定的语法。
- 生产/集成行为以 staging 为准；本机只做单元测试、静态检查和快速反馈。
- 保留历史 ADR-0013 作为 superseded 记录，新的长期约束以 ADR-0014 为准。

---

## Task 1: Lock the metadata and renderer contract with failing tests

**Files:**
- Modify: `bytedepth-adapter/src/test/java/manfred/bytedepth/adapter/web/util/MarkdownRendererTest.java`
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/util/CodeBlockMetadata.java`
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/util/EnhancedCodeBlockRenderer.java`

- [x] Update renderer tests first so every top-level fenced block has exactly one `.bd-code-block`, copy button, and toggle button; assert an unmarked block uses the neutral `Code` label and retains its literal text.
- [x] Update tests for default-expanded blocks and explicit `fold` blocks; assert the initial `aria-expanded`, button label, and sanitized language class.
- [x] Update Tab tests to assert the accepted two-row shape: top-level tab row, then one independent code-block header/body per panel, with controls and line-number host in every panel.
- [x] Keep/add tests proving only consecutive blocks with the same explicit `tabs:` group are grouped, titles are never invented, and nested quote/list code remains ordinary CommonMark output.
- [x] Run the focused adapter tests and confirm the new assertions fail before changing production code:
      `./mvnw -pl bytedepth-adapter -Dtest=MarkdownRendererTest test`
- [x] Remove the old opt-in branch from `EnhancedCodeBlockRenderer`; render all top-level `FencedCodeBlock` nodes through the unified component while retaining explicit Tab grouping.
- [x] Make the metadata parser return an empty language for unmarked blocks and move the display fallback to `Code`; keep `title:`/`file:` optional and never generate a filename.
- [x] Render the header as one consistent row with left-side language/title and right-side toggle-then-copy actions; render the toggle for every block, with `fold` only changing the initial state.
- [x] Run the focused adapter tests again and require green output with no warnings.

## Task 2: Add safe line-number and syntax-highlight rendering

**Files:**
- Add: `bytedepth-start/src/main/resources/static/vendor/prism/prism.js`
- Add: `bytedepth-start/src/main/resources/static/vendor/prism/prism-java.min.js`
- Add: `bytedepth-start/src/main/resources/static/vendor/prism/prism-python.min.js`
- Add: `bytedepth-start/src/main/resources/static/vendor/prism/prism-json.min.js`
- Add: `bytedepth-start/src/main/resources/static/vendor/prism/prism-bash.min.js`
- Add: `bytedepth-start/src/main/resources/static/vendor/prism/prism-css.min.js`
- Add: `bytedepth-start/src/main/resources/static/vendor/prism/prism-markup.min.js`
- Add: `bytedepth-start/src/main/resources/static/vendor/prism/prism-sql.min.js`
- Add: `bytedepth-start/src/main/resources/static/vendor/prism/prism-go.min.js`
- Add: `bytedepth-start/src/main/resources/static/vendor/prism/prism-typescript.min.js`
- Add: `bytedepth-start/src/main/resources/static/vendor/prism/prism.css`
- Modify: `bytedepth-start/src/main/resources/templates/public/posts/detail.html`
- Modify: `bytedepth-start/src/main/resources/static/js/code-blocks.js`
- Modify: `bytedepth-start/src/main/resources/static/css/code-blocks.css`
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/util/MarkdownRenderer.java`

- [x] Pin one PrismJS version and vendor only the required core/common language assets; add a short license/version comment beside the vendor files and do not depend on a runtime CDN.
- [x] Add the Prism stylesheet before the component stylesheet and load Prism core/language files before `code-blocks.js`; preserve the existing Mermaid ordering and enhanced Mermaid source-copy behavior.
- [x] Extend the sanitizer allowlist only for the component’s required classes/attributes (`actions`, `lines`, `line`, collapsed state, Prism token classes, and `hidden` where needed); keep language and class values constrained by patterns.
- [x] Render a separate `aria-hidden` line-number rail so line numbers never enter copied code text; ensure blank lines and a trailing newline produce stable, readable numbering.
- [x] In `code-blocks.js`, initialize every rendered block exactly once, preserve raw `code.textContent` for copy, set the collapsed class/body state from server markup, and run Prism only when the language is supported; unsupported or unmarked code must stay readable plain text.
- [x] Add keyboard-safe Tab behavior and keep each panel’s toggle/copy independent; do not merge ordinary blocks or infer languages.
- [x] Write failing Vitest cases for line-number creation, default expanded/collapsed state, per-block copy isolation, unsupported-language fallback, and per-panel Tab isolation.
- [x] Run the focused frontend test file and ESLint, then fix all failures and warnings before moving on.

## Task 3: Align visual behavior and regression coverage

**Files:**
- Modify: `bytedepth-start/src/test/js/code-blocks.test.js`
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/PostReadingAssetsTest.java`
- Modify: `tests/e2e/code-blocks.spec.js`
- Modify: `bytedepth-start/src/main/resources/static/css/code-blocks.css`

- [x] Replace the obsolete “ordinary code remains untouched” assertions with the new compatibility contract: ordinary and unmarked blocks use the same component shell, while their source text remains unchanged.
- [x] Add CSS contract assertions that the code component is scoped under `.content`, preserves a light/default code surface, aligns controls consistently on the right, and does not style arbitrary `.content pre` blocks.
- [x] Add regression tests that expanded headers use the normal neutral treatment and collapsed headers use the pale accent treatment with a visually stronger `展开` button; do not rely on color alone for state communication.
- [x] Extend E2E coverage to create one article containing unmarked code, ordinary language code, a folded long block, an expanded block, and a two-language Tab group; verify line numbers, highlighting hooks, per-block controls, copy text, Tab switching, and fold state.
- [x] Keep E2E cleanup in `finally`, use the existing administrator credential path, and do not add fallback/temp accounts.
- [x] Run focused JUnit/Vitest/E2E tests locally where allowed; record that real integration/E2E acceptance must be rerun on staging for the candidate SHA.

## Task 4: Documentation, changelog, and quality gates

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `docs/architecture/decisions/README.md` if ADR indexing needs consistency
- Modify: `docs/architecture/decisions/0014-unified-code-block-component.md` only if implementation reveals a contract mismatch
- Add/update: project docs under `docs/` for any newly discovered invariant or test guard

- [x] Add a non-empty categorized `## Unreleased` entry describing the unified code-block component and compatibility behavior.
- [x] Run `bash scripts/test-maven-runtime.sh` and `bash scripts/run-local-quality.sh`; resolve every warning rather than treating it as informational.
- [x] Refresh Maven cache before multi-module tests as required, then run the complete unit/static suite with `./mvnw` and the frontend suite with `npm ci --ignore-scripts --no-audit --no-fund`, `npm test`, and `npm run lint`.
- [x] Inspect `git diff --check`, test reports, coverage output, sanitizer output, and generated HTML; confirm no accidental prototype-only assets or temporary credentials are included.
- [ ] Commit the implementation on the feature branch only after all local gates are green, then deploy the candidate ref to `https://staging-bytedepth.bytedepth.cn/` using the repository staging flow.
- [ ] On staging, run the complete integration and E2E suites, require commit-bound `result=passed` evidence for the exact candidate SHA, and wait for owner visual acceptance before any PR merge.

## Verification Checklist

- [x] Top-level fenced blocks: unmarked, marked, folded, titled, unsafe-language, Mermaid, and Tab variants all render safely.
- [x] Every block has its own `复制` and `收起/展开`; no block’s state or copy action affects another block.
- [x] Initial state is expanded unless `fold` is explicit; collapsed header/button visibly communicates hidden content.
- [x] Line numbers appear in ordinary blocks and every Tab panel, but never contaminate clipboard text.
- [x] Prism highlighting is progressive enhancement; unavailable/unsupported language assets do not remove or alter source text.
- [x] Existing Mermaid conversion still sees enhanced source and does not break copy behavior.
- [x] No arbitrary article `pre`/`code` styling leaks outside the component.
- [ ] All required tests, coverage, warnings checks, staging integration, staging E2E, and owner acceptance are complete before merge/release.
