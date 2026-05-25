#!/usr/bin/env python3
"""
Step 1: 从 Obsidian 笔记中提取标签并写入 bytedepth 数据库
用法：python3 import_tags.py
"""
import re
import subprocess
import os

VAULT_ROOT = "/Users/maxingfang/w/w"

# 笔记文件路径（相对于 VAULT_ROOT）和对应数据库 post_id
NOTE_MAP = {
    # post_id: note_relative_path
    # 根据实际情况修改
}

# 标签中文显示名映射（slug → 中文名）
TAG_DISPLAY = {
    "architecture": "架构",
    "system-design": "系统设计",
    "high-concurrency": "高并发",
    "performance": "性能",
    "optimization": "优化",
    "jvm": "JVM",
    "database": "数据库",
    "cache": "缓存",
    "software-quality": "软件质量",
    "code-quality": "代码质量",
    "technical-debt": "技术债",
    "software-engineering": "软件工程",
    "design-principles": "设计原则",
    "solid": "SOLID",
    "oop": "面向对象",
    "ai": "AI",
    "llm": "大模型",
    "ai-coding": "AI 编程",
    "knowledge-base": "知识库",
    "message-queue": "消息队列",
    "seckill": "秒杀",
    "redis": "Redis",
    "complexity": "复杂度",
    "cognitive-load": "认知负荷",
    "agent": "Agent",
    "cicd": "CI/CD",
}


def run_sql(sql, db="bytedepth"):
    result = subprocess.run(
        ["mysql", "-uroot", "-h127.0.0.1", db, "-N", "-e", sql],
        capture_output=True, text=True
    )
    return result.stdout.strip()


def extract_tags_from_file(note_path):
    """从笔记第一行提取 #tag 标签"""
    full_path = os.path.join(VAULT_ROOT, note_path)
    with open(full_path, 'r', encoding='utf-8') as f:
        first_line = f.readline().strip()
    return re.findall(r'#([a-zA-Z][a-zA-Z0-9_-]*)', first_line)


def get_or_create_tag(raw_slug):
    slug = raw_slug.lower().replace('_', '-')
    name = TAG_DISPLAY.get(slug, slug)
    run_sql(f"INSERT IGNORE INTO tag (name, slug) VALUES ('{name}', '{slug}');")
    row = run_sql(f"SELECT id FROM tag WHERE slug='{slug}';")
    return int(row.strip()) if row.strip() else None


def import_tags(note_map):
    print("开始导入标签...\n")
    total_links = 0
    for post_id, note_rel in note_map.items():
        raw_tags = extract_tags_from_file(note_rel)
        if not raw_tags:
            print(f"  [{post_id}] 无标签: {note_rel}")
            continue
        tag_ids = [get_or_create_tag(raw) for raw in raw_tags if get_or_create_tag(raw)]
        run_sql(f"DELETE FROM post_tag WHERE post_id={post_id};")
        for tid in tag_ids:
            run_sql(f"INSERT IGNORE INTO post_tag (post_id, tag_id) VALUES ({post_id}, {tid});")
        total_links += len(tag_ids)
        tag_names = [TAG_DISPLAY.get(t.lower().replace('_', '-'), t) for t in raw_tags]
        print(f"  ✅ [{post_id}] {len(tag_ids)} 个标签: {', '.join(tag_names)}")
    print(f"\n完成！共写入 {total_links} 条标签关联")


if __name__ == "__main__":
    # 使用时修改 NOTE_MAP，例如：
    # NOTE_MAP = {4: "12 性能工程/01 高并发系统设计.md", ...}
    import_tags(NOTE_MAP)
