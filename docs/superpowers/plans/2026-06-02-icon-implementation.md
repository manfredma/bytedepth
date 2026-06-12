# bytedepth Icon 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 bytedepth 字节深处生成 SVG icon（favicon + logo），并注入全部 15 个 HTML 模板。

**Architecture:** 生成两个 SVG 文件放入 `static/icons/`；favicon 为静态版（无动效，兼容浏览器），logo 为带动效完整版（供 header 使用）；各模板 `<head>` 直接插入 `<link rel="icon">` 指向 favicon.svg，nav fragment 中的文字 logo 替换为 SVG logo。

**Tech Stack:** SVG、Thymeleaf HTML 模板、Spring Boot static resources（`src/main/resources/static/`）

---

## 文件结构

| 操作 | 路径 | 说明 |
|------|------|------|
| 新建 | `bytedepth-start/src/main/resources/static/icons/favicon.svg` | 静态 icon，无动效，用于 `<link rel="icon">` |
| 新建 | `bytedepth-start/src/main/resources/static/icons/logo.svg` | 带动效完整版，供 nav fragment 使用 |
| 修改 | `templates/fragments/nav.html` | 文字 logo 替换为 `<img>` 引用 logo.svg |
| 修改 | `templates/public/index.html` | `<head>` 加 favicon link |
| 修改 | `templates/public/login.html` | `<head>` 加 favicon link |
| 修改 | `templates/public/search.html` | `<head>` 加 favicon link |
| 修改 | `templates/public/posts/list.html` | `<head>` 加 favicon link |
| 修改 | `templates/public/posts/detail.html` | `<head>` 加 favicon link |
| 修改 | `templates/public/projects/list.html` | `<head>` 加 favicon link |
| 修改 | `templates/admin/dashboard.html` | `<head>` 加 favicon link |
| 修改 | `templates/admin/posts/list.html` | `<head>` 加 favicon link |
| 修改 | `templates/admin/posts/edit.html` | `<head>` 加 favicon link |
| 修改 | `templates/admin/projects/edit.html` | `<head>` 加 favicon link |
| 修改 | `templates/admin/categories/list.html` | `<head>` 加 favicon link |
| 修改 | `templates/admin/comments/list.html` | `<head>` 加 favicon link |
| 修改 | `templates/admin/tags/list.html` | `<head>` 加 favicon link |
| 修改 | `templates/admin/series/list.html` | `<head>` 加 favicon link |

---

## Task 1: 生成 favicon.svg（静态版）

**Files:**
- Create: `bytedepth-start/src/main/resources/static/icons/favicon.svg`

- [ ] **Step 1: 创建 icons 目录并写入 favicon.svg**

```bash
mkdir -p bytedepth-start/src/main/resources/static/icons
```

写入以下内容到 `bytedepth-start/src/main/resources/static/icons/favicon.svg`：

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 110 110" width="110" height="110">
  <defs>
    <linearGradient id="gF" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#34d399"/>
      <stop offset="50%" stop-color="#38bdf8"/>
      <stop offset="100%" stop-color="#818cf8"/>
    </linearGradient>
  </defs>
  <!-- 外圈虚线环 -->
  <circle cx="55" cy="55" r="48"
          fill="none" stroke="url(#gF)" stroke-width="2"
          stroke-dasharray="10 5" opacity="0.55"/>
  <!-- 静态内圈 -->
  <circle cx="55" cy="55" r="38"
          fill="none" stroke="url(#gF)" stroke-width="1.5"
          stroke-dasharray="4 8" opacity="0.35"/>
  <!-- 内圆背景 -->
  <circle cx="55" cy="55" r="28" fill="#062a22"/>
  <circle cx="55" cy="55" r="27" fill="none" stroke="#34d399" stroke-width="1" opacity="0.4"/>
  <!-- bd 字母 -->
  <text x="55" y="63" font-size="22"
        font-family="'SF Mono','Fira Code','Courier New',monospace"
        font-weight="700" text-anchor="middle"
        fill="url(#gF)" letter-spacing="1">bd</text>
  <!-- 四个彩点 -->
  <circle cx="55"  cy="7"   r="3.5" fill="#34d399"/>
  <circle cx="103" cy="55"  r="3.5" fill="#38bdf8"/>
  <circle cx="55"  cy="103" r="3.5" fill="#818cf8"/>
  <circle cx="7"   cy="55"  r="3.5" fill="#a78bfa"/>
</svg>
```

- [ ] **Step 2: 验证文件存在**

```bash
ls -lh bytedepth-start/src/main/resources/static/icons/favicon.svg
```

期望输出：文件存在，大小约 1KB。

- [ ] **Step 3: Commit**

```bash
git add bytedepth-start/src/main/resources/static/icons/favicon.svg
git commit -m "feat: 新增 favicon.svg 静态图标"
```

---

## Task 2: 生成 logo.svg（带动效完整版）

**Files:**
- Create: `bytedepth-start/src/main/resources/static/icons/logo.svg`

- [ ] **Step 1: 写入 logo.svg**

写入以下内容到 `bytedepth-start/src/main/resources/static/icons/logo.svg`：

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 110 110" width="36" height="36">
  <defs>
    <linearGradient id="gL1" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#34d399"/>
      <stop offset="50%" stop-color="#38bdf8"/>
      <stop offset="100%" stop-color="#818cf8"/>
    </linearGradient>
    <linearGradient id="gL2" x1="1" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="#818cf8"/>
      <stop offset="50%" stop-color="#38bdf8"/>
      <stop offset="100%" stop-color="#34d399"/>
    </linearGradient>
    <style>
      @keyframes spin-slow { from{transform:rotate(0deg)} to{transform:rotate(360deg)} }
      @keyframes spin-rev  { from{transform:rotate(0deg)} to{transform:rotate(-360deg)} }
      @keyframes glow      { 0%,100%{filter:drop-shadow(0 0 3px #34d399)} 50%{filter:drop-shadow(0 0 8px #38bdf8)} }
      @keyframes pd        { 0%,100%{opacity:1} 50%{opacity:0.4} }
      .r-outer { animation: spin-slow 20s linear infinite; transform-origin: 55px 55px; }
      .r-inner { animation: spin-rev  12s linear infinite; transform-origin: 55px 55px; }
      .bd      { animation: glow 3s ease-in-out infinite; }
      .d0 { animation: pd 1.8s infinite 0s; }
      .d1 { animation: pd 1.8s infinite 0.45s; }
      .d2 { animation: pd 1.8s infinite 0.9s; }
      .d3 { animation: pd 1.8s infinite 1.35s; }
    </style>
  </defs>
  <!-- 外圈：20s 慢转 -->
  <circle class="r-outer" cx="55" cy="55" r="48"
          fill="none" stroke="url(#gL1)" stroke-width="2"
          stroke-dasharray="10 5" opacity="0.55"/>
  <!-- 内圈：12s 逆转 -->
  <circle class="r-inner" cx="55" cy="55" r="38"
          fill="none" stroke="url(#gL2)" stroke-width="1.5"
          stroke-dasharray="4 8" opacity="0.35"/>
  <!-- 内圆 -->
  <circle cx="55" cy="55" r="28" fill="#062a22"/>
  <circle cx="55" cy="55" r="27" fill="none" stroke="#34d399" stroke-width="1" opacity="0.4"/>
  <!-- bd -->
  <text class="bd" x="55" y="63" font-size="22"
        font-family="'SF Mono','Fira Code','Courier New',monospace"
        font-weight="700" text-anchor="middle"
        fill="url(#gL1)" letter-spacing="1">bd</text>
  <!-- 四个呼吸彩点 -->
  <circle class="d0" cx="55"  cy="7"   r="3.5" fill="#34d399"/>
  <circle class="d1" cx="103" cy="55"  r="3.5" fill="#38bdf8"/>
  <circle class="d2" cx="55"  cy="103" r="3.5" fill="#818cf8"/>
  <circle class="d3" cx="7"   cy="55"  r="3.5" fill="#a78bfa"/>
</svg>
```

- [ ] **Step 2: 验证文件存在**

```bash
ls -lh bytedepth-start/src/main/resources/static/icons/logo.svg
```

期望输出：文件存在，大小约 2KB。

- [ ] **Step 3: Commit**

```bash
git add bytedepth-start/src/main/resources/static/icons/logo.svg
git commit -m "feat: 新增 logo.svg 带动效图标"
```

---

## Task 3: 注入 favicon 到全部公共页模板

**Files:**  
- Modify: `templates/public/index.html`  
- Modify: `templates/public/login.html`  
- Modify: `templates/public/search.html`  
- Modify: `templates/public/posts/list.html`  
- Modify: `templates/public/posts/detail.html`  
- Modify: `templates/public/projects/list.html`

- [ ] **Step 1: 在每个文件的 `<head>` 第一行 `<meta charset>` 之后插入 favicon link**

每个文件找到：
```html
<meta charset="UTF-8">
```

在其后插入（Thymeleaf 用 `@{/icons/favicon.svg}` 确保路径正确）：
```html
<link rel="icon" type="image/svg+xml" th:href="@{/icons/favicon.svg}">
```

6 个文件都需要插入，路径固定为 `/icons/favicon.svg`。

- [ ] **Step 2: 验证插入结果**

```bash
grep -l 'favicon.svg' \
  bytedepth-start/src/main/resources/templates/public/index.html \
  bytedepth-start/src/main/resources/templates/public/login.html \
  bytedepth-start/src/main/resources/templates/public/search.html \
  bytedepth-start/src/main/resources/templates/public/posts/list.html \
  bytedepth-start/src/main/resources/templates/public/posts/detail.html \
  bytedepth-start/src/main/resources/templates/public/projects/list.html
```

期望输出：6 个文件都列出。

- [ ] **Step 3: Commit**

```bash
git add bytedepth-start/src/main/resources/templates/public/
git commit -m "feat: 注入 favicon 到公共页模板"
```

---

## Task 4: 注入 favicon 到全部 admin 模板

**Files:**  
- Modify: `templates/admin/dashboard.html`  
- Modify: `templates/admin/posts/list.html`  
- Modify: `templates/admin/posts/edit.html`  
- Modify: `templates/admin/projects/edit.html`  
- Modify: `templates/admin/categories/list.html`  
- Modify: `templates/admin/comments/list.html`  
- Modify: `templates/admin/tags/list.html`  
- Modify: `templates/admin/series/list.html`

- [ ] **Step 1: 在每个文件的 `<meta charset="UTF-8">` 之后插入 favicon link**

```html
<link rel="icon" type="image/svg+xml" th:href="@{/icons/favicon.svg}">
```

8 个 admin 模板都需插入。

- [ ] **Step 2: 验证插入结果**

```bash
grep -rl 'favicon.svg' bytedepth-start/src/main/resources/templates/admin/ | wc -l
```

期望输出：`8`

- [ ] **Step 3: Commit**

```bash
git add bytedepth-start/src/main/resources/templates/admin/
git commit -m "feat: 注入 favicon 到 admin 模板"
```

---

## Task 5: nav fragment 替换文字 logo 为 SVG

**Files:**  
- Modify: `templates/fragments/nav.html`

当前 nav.html 第 5 行：
```html
<a th:href="@{/}" style="color:#e94560;font-weight:bold;font-size:1.2em;text-decoration:none;">bytedepth</a>
```

- [ ] **Step 1: 将文字 logo 替换为 SVG logo + 文字组合**

替换为：
```html
<a th:href="@{/}" style="display:flex;align-items:center;gap:8px;text-decoration:none;">
    <img th:src="@{/icons/logo.svg}" alt="bytedepth" width="28" height="28" style="display:block;">
    <span style="color:#e94560;font-weight:bold;font-size:1.2em;letter-spacing:1px;">bytedepth</span>
</a>
```

- [ ] **Step 2: 验证修改正确**

```bash
grep -A4 'th:href="@{/}"' bytedepth-start/src/main/resources/templates/fragments/nav.html | head -8
```

期望输出包含 `logo.svg` 和 `bytedepth` 文字。

- [ ] **Step 3: Commit**

```bash
git add bytedepth-start/src/main/resources/templates/fragments/nav.html
git commit -m "feat: nav logo 替换为 SVG 动效图标"
```

---

## Task 6: 本地启动验证

- [ ] **Step 1: 启动应用**

```bash
cd bytedepth-start
mvn clean spring-boot:run -Dsort.skip=true
```

- [ ] **Step 2: 浏览器验证**

打开 `http://localhost:8080`，检查：
1. 浏览器标签页显示 SVG favicon（绿色圆形图标）
2. 导航栏 logo 区域显示旋转动效 SVG + "bytedepth" 文字
3. 刷新页面动效正常播放

- [ ] **Step 3: 验证静态资源可访问**

```bash
curl -I http://localhost:8080/icons/favicon.svg
curl -I http://localhost:8080/icons/logo.svg
```

期望：两个请求均返回 `HTTP/1.1 200`，`Content-Type: image/svg+xml`。

- [ ] **Step 4: 停止应用**

`Ctrl+C` 停止 Spring Boot。

---

## Task 7: 关闭 visual companion 服务器

- [ ] **Step 1: 停止 brainstorm 服务器**

```bash
~/.claude/plugins/cache/claude-plugins-official/superpowers/5.1.0/skills/brainstorming/scripts/stop-server.sh \
  /Users/maxingfang/IdeaProjects/github/bytedepth/.superpowers/brainstorm/3307-1780381578
```
