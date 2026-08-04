# 后台页面布局

后台页面使用共享侧边栏和布局样式，避免页面各自实现导航或响应式行为。

## 使用方式

```html
<head>
  <link rel="stylesheet" th:href="@{/css/admin-layout.css}">
</head>
<body>
<nav th:replace="~{fragments/nav :: navbar(false)}"></nav>
<div class="admin-shell">
  <aside th:replace="~{fragments/admin-sidebar :: sidebar('your-key')}"></aside>
  <main class="admin-main">
    <div class="container"><!-- 页面内容 --></div>
  </main>
</div>
</body>
```

共享实现：

- `bytedepth-start/src/main/resources/static/css/admin-layout.css`
- `bytedepth-start/src/main/resources/templates/fragments/admin-sidebar.html`

## active key

`dashboard`、`posts`、`comments`、`categories`、`tags`、`series`、`users`、`analytics`、`view-logs`、`projects`、`ops`。

编辑页复用所属列表的 key，例如文章编辑页使用 `posts`。

## 新增入口

在 `admin-sidebar.html` 的对应分区新增导航项，并同时完成路由权限与 active key：

```html
<a href="/admin/new-page" class="admin-sidebar-item"
   th:classappend="${active == 'new-key'} ? ' active' : ''">
  <span class="si-icon">🔧</span>新功能
</a>
```

不要在页面内容中额外添加“返回后台首页”链接；侧边栏已经提供完整导航。移动端开关和 Esc 关闭行为由 fragment 统一处理。
