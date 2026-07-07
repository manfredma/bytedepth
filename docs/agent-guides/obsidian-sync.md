# Obsidian 同步

`--remote` 必须在子命令前，否则脚本会 `exit 2`。

```bash
python3 ~/.claude/skills/obsidian-to-bytedepth/import_via_api.py --remote sync
python3 ~/.claude/skills/obsidian-to-bytedepth/import_via_api.py --remote update-links
```

Obsidian 锚点规则：空格替换为 `%20`，其余字符原样保留。

```python
anchor = heading_text.replace(' ', '%20')
```
