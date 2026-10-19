# 前端公共组件模式

公共组件必须自隔离：样式、脚本和标记只作用于自身命名空间；组件间只能通过明确的参数和相对位置协作。完整约束见 [前端组件指南](../agent-guides/frontend-components.md)。

## 前台页面基线与页头贴边

`templates/public/` 下每个页面必须显式加载 `theme.css` 和 `theme-switcher.js`。页面基线中的 `body { margin: 0; }` 由 `theme.css` 定义；导航组件只管理自身命名空间，不负责修改宿主页面的 `body`。新增页面不能只加载自身 CSS 和导航 fragment，否则浏览器默认 8px margin 会让共享页头四周出现空隙，主题按钮也缺少初始化。

`ThemeAssetsTest.publicTemplatesLoadThemeAssets` 必须递归枚举实际公共模板，不能维护手写页面名单。这个离线检查验证每个消费页接入基线资源，但不代表浏览器最终布局已经正确。staging 移动端 E2E 必须同时检查 `body` 计算 margin 为 0，以及共享页头左、上边界为 0、右边界等于视口宽度；仅检查无横向溢出或左边界非负不能发现空隙。阅读历史的匿名空态也是公共页面，必须纳入几何检查；登录后的导航控件更多，移动端重设计还需单独验证权限态下的控件可见性和可操作性。

## 匿名阅读历史的空态

匿名历史的有效文章标识列表为空时，页面直接清空展示列表并显示空态，不调用 `/reading-history/available-posts`；该接口的 `slugs` 必填约束不放宽。首次访问、空存储、无有效标识、存储读取失败和损坏 JSON 均按此边界降级；不删除本地历史，也不上传或迁移匿名记录。非空记录仍查询最多 20 个最新标识，只显示服务端确认可访问的已发布文章。

`reading-history-page.test.js` 覆盖无网络空态、非空请求与渲染、失败降级及存储保留，并纳入前端 100% 覆盖率门禁。`reading-history-empty.spec.js` 在 staging 桌面和移动端验证匿名空历史页面不产生元数据请求；服务端日志仍须核对缺少 `slugs` 的异常不再出现，不能因前端吞掉失败而认为流程正常。

## 共享页头的窄屏布局

所有带公共导航的页面复用 `fragments/nav :: navbar`，页面 CSS 不覆盖 `.nav-*` 或共享导航的 `.theme-*`。导航字体尺寸在组件中显式定义，不随页面根字号变化。`nav-menu.js` 在导航标记后同步初始化、先于正文解析，断点与 `nav.css` 保持 1100px 一致。不能将该小脚本移回 `defer` 队列：正文大图与高亮包的下载会延迟折叠导航，使正文在首次访问时大幅跳动。

窄屏品牌与菜单按钮在同一行，品牌保持左对齐。菜单按钮带图标和明确字重，展开时显示“收起”。展开内容依次为全宽搜索、两列主导航、主题/订阅/关于共用工具行、账号操作；作者入口与个人账号分别分组，允许换行。工具的文字标签仅在窄屏显示，桌面保留原有图标与排列。搜索输入框保留可读宽度，按钮触控高度至少 44px。Esc 收起菜单并返回菜单按钮焦点；切换到桌面宽度恢复完整导航，再回窄屏默认收起。收起时同时关闭嵌套菜单。脚本不可用时保留完整菜单内容，避免导航入口丢失。

账号按钮的变体必须使用 `.nav-auth .nav-write` 等组件内同等或更高优先级选择器，避免账号基础 reset 的 `padding: 0` 覆盖按钮留白。`public-header.spec.js` 检查登录态“写文章”的双侧内边距、菜单状态文字、搜索位于主导航之前、主导航两列，以及主题与关于同一行；仅验证控件可见和不溢出不能发现分组或留白退化。

首页窄屏以标题和内容为首屏主角，不把装饰品牌图置于标题之前；内容左边界与页头品牌对齐。`public-header.spec.js` 在 staging 覆盖 320、390、768、1280px、实际发现的文章/专栏详情和登录权限态，检查贴边、字号/高度/颜色一致性、搜索宽度、控件边界和菜单键盘行为；单元测试只证明交互与资源接入，不能替代这些渲染证据。

首页统计同时作为阅读入口：文章数量链接 `/posts`，项目数量链接 `/projects`，主题数量按现有分类语义定位首页 `#home-topics`，不链接代表专栏的 `/columns`。整个数量和说明区可点击，并以箭头和细下划线提示去向。没有主推文章时不输出推荐占位区和重复边框，介绍区以一条底边衔接最近更新。有主推时仍显示真实推荐内容。`home-navigation.test.js` 检查模板入口与分类锚点，`home-navigation.spec.js` 在 staging 检查真实跳转、锚点避开 sticky 页头及无主推时的区块间距。

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

## 代码语言高亮包

文章统一加载本地 `vendor/prism/prism-common.min.js`，然后初始化代码块交互。高亮包由 lockfile 固定的 Prism 1.29.0 构建，语言清单和自定义别名唯一来源是 `scripts/prism-languages.json`；使用 Prism 自带的依赖解析器确定加载顺序，不能手工拼接依赖或只增加语言标签。

更新方法：先执行 `npm ci --ignore-scripts --no-audit --no-fund`，修改语言清单后执行 `npm run build:prism`，将生成物一并提交。`node scripts/build-prism.mjs --check` 校验生成物与固定依赖一致，已由 `prism-bundle.test.js` 纳入本地质量门禁。浏览器无需访问 CDN 或按文章请求语言模块；Spring 静态资源指纹随内容更新。

2026-10-15 扫描 Obsidian 笔记库的围栏代码块后，除现有 Java、Go、Python、JavaScript/TypeScript 等语言外，补齐 C/C++、Rust、Coq、Scheme/Racket、YAML、Dockerfile、Kotlin 等已使用且 Prism 支持的语言。扫描中 C 为 1878 块、Go 为 319 块、Rust 为 55 块；清单包含 48 种显式语言，连同自动依赖共 50 个语法模块。Prism 原生别名保留，额外兼容 `make` → `makefile` 和 `proto` → `protobuf`。

`text`、未标记及未知语言保留纯文本。笔记中的 `asm` 混用 x86、RISC-V 和 Hack，不能统一冒充 NASM；明确标记 `nasm` 时提供高亮。当前 Prism 不支持的 Forth、TLA、Cython、BibTeX 及教学用 `jack`/`vm`/`hdl`/`pseudo` 不做猜测映射。Mermaid 继续走图表渲染。新增语言先核对固定 Prism 的组件和别名，再补真实 token 输出回归，避免“标签正常但内容无高亮”复发。

验证入口：`prism-bundle.test.js` 执行页面实际引用的高亮资源，检查 C、Rust、Go 等 token 和未知语言回退；staging 的 `tests/e2e/code-blocks.spec.js` 用临时草稿检查桌面/手机、三种主题的实际颜色、代码文本和复制完整性，并清理草稿。

## 批注与正文渲染

定位协议以[版本化批注设计](../architecture/annotation-anchoring.md)为准。代码渲染完成发送 `bd:content-rendered`（`detail.segmentIds`）；折叠、tab 等仅改变布局时发送 `bd:content-geometry`。批注观察器批量重核验纯内容片段，不缓存原生 Range；自身绘制通过 `mutate` 暂停观察并先保留待处理的外部记录，避免循环或吞掉渲染变化。实例销毁后，旧请求及渲染回调不得写回当前页面。

`post-mermaid.js` 保留源码并逐块处理成功与失败，不让一张图阻塞正文；失败显示源码提示。图形文字不属于可提交选区。`annotation.test.js`、`annotation-render-lifecycle.test.js` 和 `post-mermaid.test.js` 覆盖渲染、异步乱序及销毁。Prism 接缝测试必须使用真正的 `#post-article .content` 与代码复制按钮；仅导入一个提前返回的组件不能证明集成行为。单测中的 SVG 结构样本只证明替换与排除规则，实际 SVG 几何仍由 staging 浏览器验证。

## 文章图片的首次加载

正文渲染器在服务端输出图片加载提示，不能等浏览器下载图片后再由脚本设置。文章首段的图片保持 `loading="eager"`，避免首图等待；后续段落、标题或引用内的图片使用原生 `loading="lazy"`，由浏览器按距离视口加载；解码使用 `decoding="async"`。原始 URL、alt 和经过校验的 width 保留，不改写 Markdown 或批注投影，不生成低清替代图。已有尺寸保持原样；缺少高度信息时不猜测宽高比。无脚本环境继续使用浏览器原生图片加载。

`ArticleDocumentServiceTest` 与 `MarkdownRendererTest` 验证真实渲染和安全清洗后的提示、宽度、文本映射；staging 的 `article-loading.spec.js` 验证首图下载、远处图片滚动后下载、灯箱放大/关闭、无脚本导航，以及阻塞正文高亮脚本时导航仍可操作。验收不能只比较 `window.load`，还应记录首次绘制、DOMContentLoaded、布局偏移和首屏下载量；懒加载会把正文图片下载移到滚动时，不能称为图片本身体积变小。

懒加载回归以“初始没有发起远处图片请求 → 滚动后成功加载 → 原图预览可用”为实际断言。离屏位置只与当前视口比较，不依赖正文必须超过某个固定像素值；字体、断点与主题都可能改变段落高度。图片已经保持离屏且没有提前请求时，固定 5000px 门槛会把正确的手机布局误判为功能失败。
