# 前端页面体系重构设计草案

## 背景

当前前端页面主要由 Thymeleaf 模板内联样式、少量公共 CSS 和局部组件组成。后台管理页在移动端暴露出布局体系不统一的问题，例如侧栏占用手机内容区、表格和表单在窄屏下缺少稳定的响应式规则。

这份文档只记录未来重构方向，不代表已经进入实施阶段。

## 问题

- 页面级 `.container`、卡片、表格、表单、分页样式分散在各模板内。
- 后台管理页缺少统一的 mobile-first 布局约定。
- 公共组件隔离已有要求，但后台页面还没有形成稳定的组件层。
- 引入 Bootstrap 这类框架可能减少重复劳动，但全站一次性迁移风险较高。

## 初步方向

- 第一阶段：先抽出后台公共布局和组件 class，例如 `admin-shell`、`admin-page`、`admin-card`、`admin-table-wrap`、`admin-form-row`。
- 第二阶段：评估 Bootstrap 5，而不是 Bootstrap 4。Bootstrap 5 去掉 jQuery 依赖，并提供 Offcanvas 等更适合移动端后台菜单的组件。
- 第三阶段：只在后台管理页局部引入框架能力，例如 grid、form、table、offcanvas、modal。
- 前台阅读页暂不 Bootstrap 化，保留当前自定义阅读体验。

## 非目标

- 不做一次性全站视觉重写。
- 不把前台文章阅读页和后台管理页强行统一成同一套布局。
- 不引入 Bootstrap 4。
- 不在没有页面清单和视觉回归验证的情况下批量替换模板 class。

## 后续调研问题

- 后台管理页是否需要独立于前台的 design system。
- 是否通过 WebJars、本地静态资源或构建流程引入 Bootstrap 5。
- 是否需要为后台页面增加 Playwright 级别的移动端布局验证。
- 哪些现有内联样式应优先迁移到公共 CSS。
