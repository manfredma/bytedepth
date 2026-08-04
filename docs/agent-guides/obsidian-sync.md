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

## 笔记格式关键约束

Obsidian 锚点仅将空格替换为 `%20`，其余字符保持原样：

```python
anchor = heading_text.replace(' ', '%20')
```

新增或重写笔记后，在笔记库执行 `lint_note.py`；有 `ERROR` 必须修复后再同步。详情见笔记库的 `TEMPLATE.md`。

## 专栏与批量变更

专栏绑定、重命名或批量调整会影响已发布内容。操作前先从远程后台核对文章与现有归属，不能只依赖本地目录或 `sync_state.json` 推断；除非任务明确要求，不改变专栏或文章顺序。
