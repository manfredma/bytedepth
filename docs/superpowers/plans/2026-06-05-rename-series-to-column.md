# 将「系列」改名为「专栏」实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将博客中用户可见的「系列」改为「专栏」，同步更新知识库与技能文件的相关描述。

**Architecture:** 仅修改用户可见的中文文本（HTML 展示文字、Java 注释、文档说明）。Java 类名/包名/字段名/方法名/URL 路径/CSS class/数据库表名**全部保留** `series`/`Series`——`Column` 在 Java/DB 语境下含义冲突（`@Column` 注解等），重命名技术标识符得不偿失。

**Tech Stack:** Thymeleaf HTML、Java、Python、Markdown

---

## 改动边界说明

| 内容类型 | 操作 |
|---------|------|
| HTML 展示文字（页面标题、按钮、标签） | ✅ 系列 → 专栏 |
| Java Javadoc / 行内注释 | ✅ 系列 → 专栏 |
| Skills / Wiki 文档描述 | ✅ 系列 → 专栏 |
| Java 类名、包名、字段名、方法名 | ❌ 保持 series/Series |
| URL 路径（`/admin/series`） | ❌ 保持不变 |
| CSS class（`series-panel`） | ❌ 保持不变 |
| HTML `name`/`th:` 属性值（表单字段）| ❌ 保持不变 |
| Python API 参数名（`seriesSlug` 等）| ❌ 保持不变 |
| DB 表名 | ❌ 保持不变 |

---

## 文件清单

| # | 文件 | 改动类型 |
|---|------|---------|
| 1 | `bytedepth-start/src/main/resources/templates/admin/dashboard.html` | HTML 展示文字 |
| 2 | `bytedepth-start/src/main/resources/templates/admin/series/list.html` | HTML 展示文字 |
| 3 | `bytedepth-start/src/main/resources/templates/public/posts/detail.html` | HTML 展示文字 + CSS 注释 |
| 4 | `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminSeriesController.java` | Java 注释 |
| 5 | `bytedepth-app/src/main/java/manfred/bytedepth/app/series/SetPostSeriesCmdExe.java` | Java 注释 |
| 6 | `~/.claude/skills/obsidian-to-bytedepth/SKILL.md` | 文档描述 |
| 7 | `~/.claude/skills/obsidian-to-bytedepth/import_via_api.py` | 代码注释 |

---

### Task 1：Admin dashboard — 导航卡片文字

**Files:**
- Modify: `bytedepth-start/src/main/resources/templates/admin/dashboard.html`

- [ ] **Step 1：替换两处展示文字**

  将：
  ```html
  <span class="title">系列管理</span>
  <span class="desc">新建和管理文章系列</span>
  ```
  改为：
  ```html
  <span class="title">专栏管理</span>
  <span class="desc">新建和管理文章专栏</span>
  ```

- [ ] **Step 2：确认修改正确（无其他 series 相关展示文字）**

  ```bash
  grep -n "系列\|series\|Series" bytedepth-start/src/main/resources/templates/admin/dashboard.html
  ```
  期望：只剩 URL 路径 `/admin/series`，无中文「系列」。

---

### Task 2：Admin series/list.html — 管理页全部展示文字

**Files:**
- Modify: `bytedepth-start/src/main/resources/templates/admin/series/list.html`

- [ ] **Step 1：替换页面标题**

  `<title>系列管理 - bytedepth</title>` → `<title>专栏管理 - bytedepth</title>`

- [ ] **Step 2：替换页面内所有中文「系列」**

  目标替换（共约 10 处，全部是展示文字或注释）：

  | 原文 | 改为 |
  |------|------|
  | `<h1>系列管理</h1>` | `<h1>专栏管理</h1>` |
  | `<!-- 系列列表 -->` | `<!-- 专栏列表 -->` |
  | `<h2>所有系列</h2>` | `<h2>所有专栏</h2>` |
  | `暂无系列，在下方新建` | `暂无专栏，在下方新建` |
  | `系列名`（`<td>` 占位文字）| `专栏名` |
  | `<!-- 新建系列 -->` | `<!-- 新建专栏 -->` |
  | `<h2>新建系列</h2>` | `<h2>新建专栏</h2>` |
  | `placeholder="例：GC 垃圾回收系列"` | `placeholder="例：GC 垃圾回收专栏"` |
  | `<button ...>创建系列</button>` | `<button ...>创建专栏</button>` |

  注意：`th:action="@{/admin/series}"` 等 URL/属性值**不改**。

- [ ] **Step 3：验证**

  ```bash
  grep -n "系列" bytedepth-start/src/main/resources/templates/admin/series/list.html
  ```
  期望：0 行输出。

---

### Task 3：public/posts/detail.html — 前台展示文字与 CSS 注释

**Files:**
- Modify: `bytedepth-start/src/main/resources/templates/public/posts/detail.html`

- [ ] **Step 1：替换 CSS 注释（4 处）**

  | 原文 | 改为 |
  |------|------|
  | `/* ── 系列入口（左侧固定） ────────────── */` | `/* ── 专栏入口（左侧固定） ────────────── */` |
  | `/* ── 系列面板 ────────────────────────── */` | `/* ── 专栏面板 ────────────────────────── */` |

- [ ] **Step 2：替换 HTML 展示文字（3 处）**

  | 原文 | 改为 |
  |------|------|
  | `<!-- 系列文章：左侧浮动入口 + fixed 覆盖面板（不影响正文布局）-->` | `<!-- 专栏文章：左侧浮动入口 + fixed 覆盖面板（不影响正文布局）-->` |
  | `<span class="series-trigger-label">系列文章</span>` | `<span class="series-trigger-label">专栏文章</span>` |
  | `<div class="series-panel-header">系列文章</div>` | `<div class="series-panel-header">专栏文章</div>` |
  | `系列名`（`th:text` 旁的占位文字）| `专栏名` |

  注意：`class="series-trigger-label"`、`class="series-panel-header"` 等 CSS class 名**不改**。

- [ ] **Step 3：验证**

  ```bash
  grep -n "系列" bytedepth-start/src/main/resources/templates/public/posts/detail.html
  ```
  期望：0 行输出。

---

### Task 4：Java 注释

**Files:**
- Modify: `bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminSeriesController.java`
- Modify: `bytedepth-app/src/main/java/manfred/bytedepth/app/series/SetPostSeriesCmdExe.java`

- [ ] **Step 1：更新 AdminSeriesController.java 的 Javadoc**

  ```java
  // 原
  * 给文章绑定系列。系列不存在时自动创建。
  * @param seriesSlug  系列标识（URL slug）
  * @param seriesName  系列显示名（可选，默认 = slug）
  * @param seriesOrder 文章在系列中的序号（从 1 开始）

  // 改
  * 给文章绑定专栏。专栏不存在时自动创建。
  * @param seriesSlug  专栏标识（URL slug）
  * @param seriesName  专栏显示名（可选，默认 = slug）
  * @param seriesOrder 文章在专栏中的序号（从 1 开始）
  ```

- [ ] **Step 2：更新 SetPostSeriesCmdExe.java 的注释**

  ```java
  // 原
  * 给文章设置系列。seriesSlug 不存在时自动创建（name = slug）。

  // 改
  * 给文章设置专栏。seriesSlug 不存在时自动创建（name = slug）。
  ```

- [ ] **Step 3：验证**

  ```bash
  grep -n "系列" \
    bytedepth-adapter/src/main/java/manfred/bytedepth/adapter/web/admin/AdminSeriesController.java \
    bytedepth-app/src/main/java/manfred/bytedepth/app/series/SetPostSeriesCmdExe.java
  ```
  期望：0 行输出。

---

### Task 5：SKILL.md — 文档描述更新

**Files:**
- Modify: `~/.claude/skills/obsidian-to-bytedepth/SKILL.md`

- [ ] **Step 1：批量替换概念描述**

  所有「系列」→「专栏」，**但以下两类不改**：
  - URL 路径中的 `series`：`/admin/posts/{id}/series`、`/admin/series`
  - Python API 参数名：`seriesSlug`、`seriesName`、`seriesOrder`

  关键替换点：
  | 原文 | 改为 |
  |------|------|
  | `## 系列文章命名规范` | `## 专栏文章命名规范` |
  | `同一主题的多篇文章属于**系列**` | `同一主题的多篇文章属于**专栏**` |
  | `**格式**：\`{系列名} #{序号...}\`` | `**格式**：\`{专栏名} #{序号...}\`` |
  | `GC 系列 #01：...`（示例标题） | `GC 专栏 #01：...` |
  | `协程系列 #01：...`（示例标题） | `协程专栏 #01：...` |
  | `系列名统一用中文` | `专栏名统一用中文` |
  | `**系列文章**必须在标题中加系列序号` | `**专栏文章**必须在标题中加专栏序号` |
  | `### 系列绑定 API` | `### 专栏绑定 API` |
  | `# 绑定文章到系列，系列不存在时自动创建` | `# 绑定文章到专栏，专栏不存在时自动创建` |
  | `'seriesName':  '并发算法系列',` 注释中的示例名 | `'seriesName':  '并发算法专栏',` |
  | `# 在系列中的序号` | `# 在专栏中的序号` |
  | `查看所有系列 slug：访问...` | `查看所有专栏 slug：访问...` |
  | `# 一键导入协程系列到远程`（文件顶部注释）| `# 一键导入协程专栏到远程` |
  | `系列侧栏出现重复文章` | `专栏侧栏出现重复文章` |

- [ ] **Step 2：验证保留了 URL 和参数名**

  ```bash
  grep -n "series\|系列" ~/.claude/skills/obsidian-to-bytedepth/SKILL.md | head -30
  ```
  期望：只剩 `/admin/series`、`seriesSlug`、`seriesName`、`seriesOrder` 等技术标识符，无独立中文「系列」。

---

### Task 6：import_via_api.py — 代码注释更新

**Files:**
- Modify: `~/.claude/skills/obsidian-to-bytedepth/import_via_api.py`

- [ ] **Step 1：更新代码注释（2 处）**

  ```python
  # 原（line ~338）
  'seriesName':  '并发算法系列',              # 显示名（可选）
  # 原（line ~340）
  'seriesOrder': '4',                       # 在系列中的序号（从 1 开始）
  # 原（line ~348）
  查看所有系列 slug：访问 `http://175.24.197.202/admin/series`

  # 改
  'seriesName':  '并发算法专栏',              # 显示名（可选）
  'seriesOrder': '4',                       # 在专栏中的序号（从 1 开始）
  查看所有专栏 slug：访问 `http://175.24.197.202/admin/series`
  ```

  注意：`系列笔记` / `**系列笔记**`（line 355-356, 743）是 Obsidian 笔记内的一种格式标签，**与博客专栏功能无关，不改**。

- [ ] **Step 2：验证**

  ```bash
  grep -n "系列" ~/.claude/skills/obsidian-to-bytedepth/import_via_api.py
  ```
  期望：只剩 `系列笔记` 相关行（Obsidian 格式，非博客专栏）。

---

### Task 7：运行测试，确认无回归

- [ ] **Step 1：编译 + 测试**

  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean test -Dsort.skip=true
  ```
  期望：BUILD SUCCESS，0 failures。

- [ ] **Step 2：提交**

  ```bash
  git add -A
  git commit -m "feat: 将用户可见的「系列」改名为「专栏」"
  ```
