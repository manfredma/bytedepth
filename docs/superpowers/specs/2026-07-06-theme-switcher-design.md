# 前台主题切换设计文档

**日期**：2026-07-06  
**范围**：前台公共页面主题切换  
**状态**：已批准，待实现

---

## 1. 背景与目标

当前 bytedepth 前台以深蓝黑导航和红色强调色为主要识别，部分阅读页已有偏纸张的浅色变量。用户希望保留现有黑色调作为默认风格，同时允许访客在浏览器中选择其他颜色主题。

本功能目标：

- 默认主题保持当前黑色调，不改变未选择用户的视觉体验。
- 新增 5 个可选主题：纸张阅读、清爽蓝、森林绿、深夜代码、玫瑰暖色。
- 主题选择保存在浏览器 `localStorage`，刷新页面和重启浏览器后保持。
- 不新增数据库字段，不要求登录，不做跨设备同步。
- 第一版只覆盖前台公共页面，后台管理页面保持现有管理台样式。

---

## 2. 主题集合

| 主题 key | 显示名 | 定位 |
|---|---|---|
| `default` | 默认 | 保留当前深蓝黑 + 红色强调色 |
| `paper` | 纸张阅读 | 长文阅读优先，偏暖纸张背景 |
| `blue` | 清爽蓝 | 明亮、干净，适合日间浏览 |
| `green` | 森林绿 | 低刺激、自然感，适合长时间阅读 |
| `midnight` | 深夜代码 | 深色代码编辑器气质，区别于默认品牌深色 |
| `rose` | 玫瑰暖色 | 暖色柔和风格，保持轻量克制 |

主题只改变颜色、边框、阴影、代码块、卡片和表单质感；不改变页面布局、导航结构、内容密度和交互路径。

---

## 3. 交互设计

### 3.1 导航入口

在前台顶部导航右侧加入主题选择控件：

- 控件位于搜索和用户区附近，保持紧凑。
- 入口使用调色板图标或文字短按钮，避免占用太多导航宽度。
- 点击后展开主题菜单。
- 每个主题项包含色块和显示名。
- 当前主题项有选中态。
- 提供“默认”选项用于恢复当前黑色调。

移动端导航已经会折行，主题控件需要跟随 `.nav-right` 布局，不引入新的整行说明文字。

### 3.2 保存与恢复

用户点击主题后：

1. 将主题 key 写入 `localStorage`，键名为 `bytedepth.theme`。
2. 在 `<html>` 上设置 `data-theme="<key>"`。
3. 更新主题菜单的选中态。

页面加载时：

1. 读取 `localStorage.getItem('bytedepth.theme')`。
2. 如果值属于允许列表，则设置到 `<html data-theme>`。
3. 如果值缺失或非法，则移除 `data-theme`，使用默认主题。

### 3.3 无导航页面

登录、注册页面没有顶部导航。它们需要读取并应用已保存主题，但第一版不放主题选择器，避免认证表单页变复杂。用户可以回到有导航的页面切换主题。

---

## 4. 技术方案

### 4.1 静态资源

新增两个前台资源：

```
bytedepth-start/src/main/resources/static/css/theme.css
bytedepth-start/src/main/resources/static/js/theme-switcher.js
```

`theme.css` 负责：

- 定义默认 CSS 变量。
- 通过 `html[data-theme="paper"]` 等选择器覆写变量。
- 提供导航主题选择器样式。
- 提供公共页面常用样式变量，如背景、卡片、文字、边框、强调色、代码块、表单、分页。

`theme-switcher.js` 负责：

- 在页面加载前尽早应用保存的主题，减少闪烁。
- 初始化主题菜单。
- 处理主题切换点击。
- 对非法主题值回退默认主题。

### 4.2 CSS 变量

第一版使用一组公共变量覆盖现有页面：

```css
--bd-bg
--bd-surface
--bd-surface-muted
--bd-text
--bd-text-muted
--bd-text-subtle
--bd-border
--bd-accent
--bd-accent-hover
--bd-nav-bg
--bd-nav-text
--bd-code-bg
--bd-code-text
--bd-shadow
```

现有模板中的硬编码颜色逐步替换为这些变量。局部已有变量的页面，例如文章详情、专栏页，可以在页面内把原变量映射到 `--bd-*`，减少大范围重写。

### 4.3 模板接入

前台页面在 `<head>` 引入：

```html
<link rel="stylesheet" th:href="@{/css/theme.css}">
<script th:src="@{/js/theme-switcher.js}"></script>
```

前台导航片段 `fragments/nav.html` 增加主题菜单。因为大多数公共页面都引用该导航，菜单只需要维护一处。

覆盖页面：

- `public/index.html`
- `public/posts/list.html`
- `public/posts/detail.html`
- `public/columns/list.html`
- `public/columns/detail.html`
- `public/search.html`
- `public/about.html`
- `public/projects/list.html`
- `public/login.html`
- `public/register.html`
- `fragments/nav.html`
- `fragments/pagination.html`

后台 `admin/**` 模板和 `static/css/admin-layout.css` 不纳入第一版。

---

## 5. 错误处理与边界

- `localStorage` 不可用时，脚本静默回退默认主题，不阻塞页面。
- 保存的主题 key 不在允许列表时，清除该值并回退默认主题。
- 未加载 JavaScript 时，页面仍使用默认主题，导航菜单可退化为普通按钮区域。
- 主题切换不向服务端发送请求，不影响缓存策略、SEO 和登录状态。
- 页面已有内联样式较多，第一版只做主题相关变量替换，不做 unrelated 布局重构。

---

## 6. 测试与验收

### 6.1 自动化验证

按仓库要求执行：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean install -DskipTests -Dsort.skip=true
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -Dsort.skip=true
```

补充前台静态资源测试：

- 验证 `/css/theme.css` 和 `/js/theme-switcher.js` 能被 Spring Boot 静态资源处理。
- 验证关键公共模板渲染时包含主题 CSS/JS 引用。

### 6.2 手动验收

本地启动应用后检查：

- 未选择主题时，首页、文章列表、文章详情的默认风格保持当前黑色调。
- 选择 5 个可选主题后，当前页面立即变色。
- 刷新页面后主题保持。
- 选择“默认”后恢复当前黑色调，并清理保存值或保存为 `default`。
- 登录、注册页面能应用已保存主题，但不显示主题菜单。
- 后台管理页面不受影响。

---

## 7. 非目标

- 不做登录用户跨设备主题同步。
- 不新增后台主题配置。
- 不引入前端构建工具或大型 UI 框架。
- 不重设计信息架构、导航内容或页面布局。
- 不修改文章 Markdown 渲染逻辑。
