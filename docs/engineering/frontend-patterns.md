# 前端公共组件模式

公共组件必须自隔离：样式、脚本和标记只作用于自身命名空间；组件间只能通过明确的参数和相对位置协作。完整约束见 [前端组件指南](../agent-guides/frontend-components.md)。

## 分页组件

位置：`bytedepth-start/src/main/resources/templates/fragments/pagination.html`

```text
pagination(currentPage, totalPages, total, pageSize, baseUrl)
```

| 参数 | 含义 |
| --- | --- |
| `currentPage` | 从 1 开始的当前页。 |
| `totalPages` | 总页数。 |
| `total` | 总条数；为 `null` 或 `0` 时不展示计数。 |
| `pageSize` | 生成 URL 时保留的页大小。 |
| `baseUrl` | 以 `?` 或 `&` 结尾的 URL 前缀。 |

调用方必须提供这五个模型属性：

```html
<div th:if="${totalPages > 1}"
     th:replace="~{fragments/pagination :: pagination(
       ${currentPage}, ${totalPages}, ${total}, ${pageSize}, '/admin/posts?')}"></div>
```

筛选条件必须编码进 `baseUrl` 并以 `&` 结尾，确保翻页和跳页不丢失查询条件：

```html
th:with="baseUrl='/posts?' +
  (${activeTag} != null ? 'tag=' + ${activeTag} + '&' : '') +
  (${activeCategory} != null ? 'category=' + ${activeCategory} + '&' : '')"
```

组件自身使用 `bd-pagination-*` 命名空间，负责页码、上一页/下一页、跳页和响应式样式；消费页不应覆盖这些选择器。

## 确认弹窗

位置：

- `static/css/bd-dialog.css`
- `static/js/bd-dialog.js`

导航 fragment 统一加载资源。表单通过 `data-bd-confirm` 及相关 `data-bd-confirm-*` 属性接入；异步操作调用 `window.BytedepthDialog.confirm(...)`。组件基于原生 `<dialog>`，负责 Esc 关闭、焦点管理和返回焦点；调用方只提供业务文案与后续动作。
