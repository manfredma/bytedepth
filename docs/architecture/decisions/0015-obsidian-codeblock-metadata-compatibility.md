# ADR-0015: 兼容 Obsidian Codeblock Customizer 的代码块元数据

- **状态**: Accepted
- **日期**: 2026-09-23
- **决策者**: 项目所有者
- **相关**: [ADR-0014](0014-unified-code-block-component.md)

## 上下文

Obsidian 的 Codeblock Customizer 使用 `group:<name>`、`tab:<label>` 和可选的
`title:<filename>` 描述多语言代码块。bytedepth 之前只识别自己的
`tabs:<name>` 语法；Obsidian 笔记导入时会保留 Markdown 原文，因此 `group/tab`
会被静默忽略，远程页面退化为多个独立代码块。

另外，Codeblock Customizer 对同组代码块的连续性敏感：组内代码围栏之间插入空行
可能导致插件只识别部分 Tab。这个约束必须在 Obsidian 笔记校验阶段被发现，而不能
等同步后由人工发现。

## 决策

bytedepth 的代码块元数据解析器直接兼容 Codeblock Customizer 的元数据，同时保留
现有 `tabs:` 语法：

- `tabs:<group>` 与 `group:<group>` 都映射为 Tab 分组；
- `tab:<label>` 只决定 Tab 页签名称；
- `title:`/`file:` 只决定代码面板标题，不被新的 `tab:` 语法误用为页签名称；
- 没有 `tab:` 时，页签名称回退为现有行为：标题优先、语言其次；
- 普通代码块和旧 `tabs:` 文章继续保持兼容；
- 导入脚本不重写代码围栏，只负责保留 Markdown 并在同步前执行 Obsidian 代码块校验。

Obsidian 代码块校验器必须拒绝同一 `group` 中不连续的代码块、缺少 `tab` 的组成员
以及重复的 Tab 名称，并报告对应笔记和行号。

## 方案取舍

选择 bytedepth 直接兼容插件元数据，而不是在导入脚本中把 `group/tab` 重写成
`tabs`，原因是 Markdown 源文本需要同时在 Obsidian 和 bytedepth 中保持可读，转换
层越多越容易造成源文本、预览和发布结果不一致。导入 skill 只保留跨系统流程和
校验规则，不承载 bytedepth 的 HTML/CSS 渲染实现。

不采用复制 Obsidian 插件生成的 DOM：插件 DOM、样式和 JavaScript 不属于 Markdown
源文件，也不应成为 bytedepth 的运行时依赖。

## 后果

- 现有 `tabs:` 文章无需迁移；
- 使用 Codeblock Customizer 的笔记可以直接同步，远程页面仍由 bytedepth 自己渲染；
- 导入前会更早暴露空行、缺失页签和重复页签问题；
- bytedepth 需要为两套等价元数据维护解析器测试和 staging E2E 覆盖。

## 验收信号

| 场景 | 要求 |
|------|------|
| `group/tab/title` | 生成正确的多语言 Tab，页签名与标题分离 |
| `tabs/title` | 保持现有兼容行为 |
| 普通代码块 | 不分组、不丢失正文 |
| 组内空行 | Obsidian 校验器在同步前失败并报告行号 |
| 重复页签或缺失页签 | Obsidian 校验器失败并报告具体组 |
