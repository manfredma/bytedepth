# 前端公共组件模式

公共组件必须自隔离：样式、脚本和标记只作用于自身命名空间；组件间只能通过明确的参数和相对位置协作。完整约束见 [前端组件指南](../agent-guides/frontend-components.md)。

## 前台页面基线与页头贴边

`templates/public/` 下每个页面必须显式加载 `theme.css` 和 `theme-switcher.js`。页面基线中的 `body { margin: 0; }` 由 `theme.css` 定义；导航组件只管理自身命名空间，不负责修改宿主页面的 `body`。新增页面不能只加载自身 CSS 和导航 fragment，否则浏览器默认 8px margin 会让共享页头四周出现空隙，主题按钮也缺少初始化。

`ThemeAssetsTest.publicTemplatesLoadThemeAssets` 必须递归枚举实际公共模板，不能维护手写页面名单。这个离线检查验证每个消费页接入基线资源，但不代表浏览器最终布局已经正确。staging 移动端 E2E 必须同时检查 `body` 计算 margin 为 0，以及共享页头左、上边界为 0、右边界等于视口宽度；仅检查无横向溢出或左边界非负不能发现空隙。阅读历史的匿名空态也是公共页面，必须纳入几何检查；登录后的导航控件更多，移动端重设计还需单独验证权限态下的控件可见性和可操作性。

## 共享页头的窄屏布局

所有带公共导航的页面复用 `fragments/nav :: navbar`，页面 CSS 不覆盖 `.nav-*` 或共享导航的 `.theme-*`。导航字体尺寸在组件中显式定义，不随页面根字号变化。`nav-menu.js` 与 fragment 一起加载，断点与 `nav.css` 保持 1100px 一致。

窄屏品牌与菜单按钮在同一行，品牌保持左对齐。菜单初始收起，展开后依次提供导航、搜索/主题和账号操作；搜索输入框保留可读宽度，按钮触控高度至少 44px，账号操作允许换行。Esc 收起菜单并返回菜单按钮焦点；切换到桌面宽度恢复完整导航，再回窄屏默认收起。收起时同时关闭嵌套菜单。脚本不可用时保留完整菜单内容，避免导航入口丢失。

首页窄屏以标题和内容为首屏主角，不把装饰品牌图置于标题之前；内容左边界与页头品牌对齐。`public-header.spec.js` 在 staging 覆盖 320、390、768、1280px、实际发现的文章/专栏详情和登录权限态，检查贴边、字号/高度/颜色一致性、搜索宽度、控件边界和菜单键盘行为；单元测试只证明交互与资源接入，不能替代这些渲染证据。

## 分页组件

位置：`bytedepth-start/src/main/resources/templates/fragments/pagination.html`

```text
pagination(currentPage, totalPages, total, pageSize, baseUrl)
```

| 参数          | 含义                                    |
| ------------- | --------------------------------------- |
| `currentPage` | 从 1 开始的当前页。                     |
| `totalPages`  | 总页数。                                |
| `total`       | 总条数；为 `null` 或 `0` 时不展示计数。 |
| `pageSize`    | 生成 URL 时保留的页大小。               |
| `baseUrl`     | 以 `?` 或 `&` 结尾的 URL 前缀。         |

调用方必须提供这五个模型属性：

```html
<div
  th:if="${totalPages > 1}"
  th:replace="~{fragments/pagination :: pagination(
       ${currentPage}, ${totalPages}, ${total}, ${pageSize}, '/admin/posts?')}"
></div>
```

筛选条件必须编码进 `baseUrl` 并以 `&` 结尾，确保翻页和跳页不丢失查询条件：

```html
th:with="baseUrl='/posts?' + (${activeTag} != null ? 'tag=' + ${activeTag} + '&' : '') + (${activeCategory} != null ?
'category=' + ${activeCategory} + '&' : '')"
```

组件自身使用 `bd-pagination-*` 命名空间，负责页码、上一页/下一页、跳页和响应式样式；消费页不应覆盖这些选择器。

## 确认弹窗

位置：

- `static/css/bd-dialog.css`
- `static/js/bd-dialog.js`

导航 fragment 统一加载资源。表单通过 `data-bd-confirm` 及相关 `data-bd-confirm-*` 属性接入；异步操作调用 `window.BytedepthDialog.confirm(...)`。组件基于原生 `<dialog>`，负责 Esc 关闭、焦点管理和返回焦点；调用方只提供业务文案与后续动作。

## 过滤栏组件

位置：`bytedepth-start/src/main/resources/templates/fragments/filter-bar.html`

```text
filterBar(action, fields)
```

| 参数     | 含义                                                 |
| -------- | ---------------------------------------------------- |
| `action` | 过滤表单提交的 GET URL，即列表页自身路径。           |
| `fields` | `List<FilterField>`，Controller 构建的配置驱动字段。 |

每个 `FilterField` 由 Controller 用静态工厂构建：`text(name, label, value, placeholder)`、`number(...)` 或 `select(name, label, value, options)`；组件零改动即可新增字段。

过滤表单为服务端渲染 GET 表单，URL 可分享、可刷新；翻页时需将当前过滤条件编码进 `filterBaseUrl` 传给分页组件，确保翻页不丢失条件。组件自身使用 `bd-filter-bar` 命名空间；消费页不应覆盖这些选择器。

接入新后台列表页时，Controller 构建 `List<FilterField>` 传入模型即可，不需要改组件；分页 `baseUrl` 必须拼接当前过滤参数。

## 专栏文章导航

文章详情页的专栏导航必须由应用层根据 `series_order ASC, id ASC` 生成统一导航模型，模板不得自行按文章 ID、发布时间或查询结果顺序推断上一篇/下一篇。导航模型同时提供当前序号、总篇数、进度百分比和完整的已发布文章列表；文章开头使用可展开选择器支持跳转专栏内任意文章。

文章级导航必须使用普通链接执行完整页面导航，禁止使用 `fetch()` + DOM 替换（包括替换整篇文章容器）实现局部刷新。文章页包含多个需要在页面加载阶段初始化的脚本，任何局部替换都容易遗漏生命周期；局部 DOM 更新只允许用于独立组件或数据区域，不得替换另一个完整页面。

专栏文章选择器和文章详情元信息统一显示预计阅读时长。时长由正文可见字符数计算，每分钟 500 字，向上取整且至少为 1 分钟；不新增持久化字段，避免文章内容更新后元数据过期。

专栏边界使用明确动作：第一篇左侧返回专栏，最后一篇右侧查看专栏目录，单篇文章两侧均显示边界动作；不属于专栏的文章才使用全站上一篇/下一篇。桌面端导航为紧凑横条（目标高度约 62px），移动端保留至少 76px 的触控高度。样式统一使用 `series-*` 命名空间，禁止恢复会覆盖正文的 fixed 侧栏。
