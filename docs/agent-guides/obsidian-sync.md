# Obsidian 同步

本指南只说明项目与笔记库的协作契约；脚本实现细节以 `obsidian-to-bytedepth` skill 为准。笔记格式的权威来源是 `~/w/w/TEMPLATE.md`，同步状态是本机运行数据，不提交到本仓库。

## 日常同步

`--remote` 是全局参数，必须在子命令前，否则脚本会 `exit 2`。

```bash
SCRIPT='python3 ~/.codex/skills/obsidian-to-bytedepth/import_via_api.py'

# 上传所有内容变更；若有未上传图片，先按脚本提示 upload-images。
$SCRIPT --remote sync

# 所有文章都同步完成后，修正 [[wiki 链接]]。
$SCRIPT --remote update-links

# 对文章可访问性、图片和替换结果做复查。
$SCRIPT --remote verify
```

`sync_state.json` 由脚本维护，只能作为本地笔记与文章 ID 的同步记录，不能当作远程文章清单或专栏归属的权威来源。

## RSS 自动更新

RSS 订阅源是 `https://bytedepth.cn/feed.xml`。它由博客中已发布文章的当前状态动态生成，**没有 `rss sync` 命令，也不得手动编辑或刷新 feed**：

- `import` 创建并发布文章成功后，文章自动进入 RSS；
- `update` 或 `sync` 成功更新已发布文章后，文章会按最后变更时间自动回到 RSS 最近更新；
- 草稿不会进入 RSS。

远程同步完成后，除 `$SCRIPT --remote verify` 外还应检查 feed：请求 `https://bytedepth.cn/feed.xml` 必须返回 `application/rss+xml`，并包含本次发布或更新文章的公开链接。

## 笔记格式关键约束

Obsidian 锚点仅将空格替换为 `%20`，其余字符保持原样：

```python
anchor = heading_text.replace(' ', '%20')
```

新增或重写笔记后，在笔记库执行 `lint_note.py`；有 `ERROR` 必须修复后再同步。详情见笔记库的 `TEMPLATE.md`。

## 专栏与批量变更

专栏绑定、重命名或批量调整会影响已发布内容。操作前先从远程后台核对文章与现有归属，不能只依赖本地目录或 `sync_state.json` 推断；除非任务明确要求，不改变专栏或文章顺序。

## Markdown 脚注

正文解析与 HTML 渲染启用 CommonMark 官方 `commonmark-ext-footnotes`，与 GFM 表格共用 `MarkdownTextExtractor.EXTENSIONS`。支持命名脚注和 Obsidian 行内脚注：

```markdown
内存事务[^memory] 可以包含多拍传输。另一处也可引用[^memory]。

直接写补充说明^[这里是行内脚注内容]。

[^memory]: 内存系统某一层接口上的一次硬件读写请求及其传输、响应过程。

    缩进四个空格可继续写脚注的第二段。
```

脚注按正文首次引用顺序编号，统一显示在正文末尾；重复引用共用定义并各有返回链接。命名标签采用 UTF-8 十六进制编码，与引用次数后缀和行内编号分开，避免 `n` 的第二引用与 `n-2` 的首引用发生锚点碰撞；特殊字符和长标签也无需放宽安全过滤。未定义引用保持普通文本，未引用定义不展示。脚注用于补充解释，影响当前推理的关键前提仍在首次出现处就地解释。

安全过滤只保留生成脚注所需的 `section`、`sup`、受限 class、`bd-fn`/`bd-fnref` 锚点和返回链接标签；原始 HTML 仍转义，危险链接协议仍被过滤。`MarkdownHtmlPolicy` 同时用于文章 HTML 和服务端批注文本：过滤器会改变列表空白，不能用未过滤 HTML 计算偏移。摘要与搜索正文不包含脚注定义或行内补充，阅读字数只包含实际引用到的脚注文本，不计脚注语法或未引用定义。

回归入口：`MarkdownRendererTest`、`MarkdownTextExtractorTest` 与 staging 的 `tests/e2e/footnotes.spec.js`。E2E 用真实管理员创建临时草稿，验证桌面/手机、三种主题、脚注往返跳转和编辑后的批注位置，并清理草稿与批注；本机不启动浏览器替代 staging 验证。

增强代码块与批注文本的既有表示差异仍见 [TD-0005](../engineering/technical-debt.md#td-0005增强代码块与服务端批注文本的表示不同)；本次脚注回归覆盖普通正文，不宣称已修复代码框之后的批注。

本功能先在 PR 候选 staging 验收；production 晋级前，笔记库模板和检查器应继续保护不支持脚注的 production 同步目标，不能仅因本地 Obsidian 可显示就向旧版网站批量发布。
