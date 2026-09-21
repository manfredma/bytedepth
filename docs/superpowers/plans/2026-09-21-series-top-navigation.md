# 专栏文章开头导航 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (or equivalent inline execution) to implement this plan task-by-task.

**Goal:** 在专栏文章标题之前增加紧凑的上一篇/下一篇导航，让读者进入页面即可按专栏目录继续阅读。

**Architecture:** 复用现有 `SeriesNavigation` 查询模型和专栏排序结果，不新增持久化字段或查询分支。详情模板新增独立的 `series-top-nav` 组件，边界动作沿用底部导航语义；现有左侧专栏目录和底部完整导航保持不变。样式继续放在文章详情页当前组件样式块中，并通过桌面/移动媒体查询隔离。

**Tech Stack:** Java 25、Spring MVC、Thymeleaf、JUnit 5、AssertJ、现有文章详情页 CSS/模板。

**Spec:** `docs/superpowers/specs/2026-09-20-series-navigation-redesign-design.md`

## Global Constraints

- 不新增 Maven 模块；改动只在 `feat/series-top-navigation` worktree。
- 公开专栏上一篇/下一篇继续消费 `SeriesNavigation.previous/next`，不得在模板中按文章 ID 或发布时间重新推导顺序。
- 保留现有左侧专栏目录、阅读进度和底部导航，不删除已验收的入口。
- 任何用户可见改动在首次 staging 部署前必须拥有非空且分类明确的 `CHANGELOG.md` `## Unreleased` 条目。
- 改动完成后必须运行单元测试、静态检查和 staging 验收；所有 WARNING 都必须处理。

### Task 1: 锁定顶部导航渲染契约（TDD）

**Files:**
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/adapter/web/portal/PostControllerSeriesDetailRenderingTest.java`
- Modify: `bytedepth-start/src/test/java/manfred/bytedepth/PostReadingAssetsTest.java`

- [ ] **Step 1: 添加失败测试**：断言专栏详情 HTML 在“正在阅读专栏”之后渲染 `series-top-nav`，包含上一篇/下一篇标题、`aria-label="专栏上一篇下一篇"`，并在首篇/末篇使用“返回专栏”“查看专栏目录”。
- [ ] **Step 2: 运行测试确认失败**：执行 `./mvnw -pl bytedepth-start -Dtest=PostControllerSeriesDetailRenderingTest,PostReadingAssetsTest test`，预期因顶部组件尚不存在失败。

### Task 2: 实现顶部导航组件

**Files:**
- Modify: `bytedepth-start/src/main/resources/templates/public/posts/detail.html`

- [ ] **Step 1: 写最小模板实现**：在专栏上下文块与文章标题之间增加 `nav.series-top-nav`；中间文章输出同专栏上一篇/下一篇，首篇/末篇输出边界动作，链接使用 slug 或专栏 slug。
- [ ] **Step 2: 增加组件样式**：桌面端采用 62px 左右的双列紧凑横条，单行标题省略；移动端保留至少 76px 触控高度并允许标题换行；使用现有主题变量，不影响底部 `.series-post-nav` 和左侧 `.series-panel`。
- [ ] **Step 3: 运行聚焦测试**：重复 Task 1 命令，确认顶部中间态和边界态均通过，且输出无 WARNING。

### Task 3: 质量门禁与 staging 验收

**Files:**
- Modify: `docs/releases/CHANGELOG.md`

- [ ] **Step 1: 增加 `## Unreleased` 分类条目**：记录文章详情页新增顶部专栏上一篇/下一篇导航。
- [ ] **Step 2: 运行完整质量检查**：按仓库规则执行 Maven 缓存刷新、`./mvnw test`、前端 `npm ci --ignore-scripts --no-audit --no-fund` 和 `bash scripts/run-local-quality.sh`，修复全部失败与 WARNING。
- [ ] **Step 3: 部署候选分支到 staging**：执行 `./deploy/deploy-staging.sh feat/series-top-navigation`，使用 `https://staging-bytedepth.bytedepth.cn/` 验证专栏中间、首篇、末篇及 PC/移动布局。
- [ ] **Step 4: 生成 commit-bound staging evidence**：运行 staging 集成与 E2E，确认候选 SHA 稳定且记录 `result=passed`，再提交验收结果。
