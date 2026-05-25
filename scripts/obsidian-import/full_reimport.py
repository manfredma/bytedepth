#!/usr/bin/env python3
"""
Step 2: 从 Obsidian 笔记完整导入文章内容，处理：
  1. 去掉第一行标签行（如 #architecture #performance ...）
  2. 替换 Obsidian wiki 图片链接 ![[image.png]] → ![image.png](/images/image.png)
  3. 替换相对路径图片 ![alt](../assets/x.svg) → ![alt](/images/x.svg)
  4. 转换 Obsidian 内部链接 [[XX 标题]] → [标题](/posts/ID)（无匹配则删除）

用法：python3 full_reimport.py
"""
import os, re, subprocess
from urllib.parse import unquote

VAULT_ROOT = "/Users/maxingfang/w/w"
IMAGE_DIR = os.path.expanduser("~/bytedepth/images")

# 笔记文件路径列表：(post_id, note_relative_path)
# 根据实际情况修改
NOTES = [
    # (4, "12 性能工程/01 高并发系统设计.md"),
    # (5, "12 性能工程/05 性能优化方法论.md"),
    # ...
]


def run_sql_query(sql):
    r = subprocess.run(["mysql", "-uroot", "-h127.0.0.1", "bytedepth", "-N", "-e", sql],
                       capture_output=True, text=True)
    return r.stdout.strip()


def load_post_map():
    """读取数据库中所有文章的 id → title 映射"""
    rows = run_sql_query("SELECT id, title FROM post WHERE status='PUBLISHED';")
    return {int(l.split('\t')[0]): l.split('\t')[1].strip()
            for l in rows.split('\n') if '\t' in l}


def build_image_index():
    """构建笔记库中所有图片文件名 → 绝对路径的索引"""
    idx = {}
    for root, _, files in os.walk(VAULT_ROOT):
        for f in files:
            if f.lower().endswith(('.png', '.jpg', '.jpeg', '.svg', '.gif', '.webp')):
                idx.setdefault(f, os.path.join(root, f))
    return idx


def find_post_by_wiki(wiki_text, posts):
    """根据 wiki 链接文字找对应文章（去掉数字前缀后模糊匹配）"""
    clean = re.sub(r'^\d+\s+', '', wiki_text.strip())
    for pid, title in posts.items():
        if clean in title:
            return pid, clean
    return None, clean


def process_content(content, note_rel, posts, image_idx):
    note_dir = os.path.dirname(os.path.join(VAULT_ROOT, note_rel))

    # ① 替换 Obsidian wiki 图片 ![[filename.ext|width?]]
    def replace_wiki_img(m):
        fn = m.group(1).strip()
        if os.path.exists(os.path.join(IMAGE_DIR, fn)):
            return f"![{fn}](/images/{fn})"
        return m.group(0)  # 找不到则保留原样
    content = re.sub(r'!\[\[([^\]|]+?)(?:\|[^\]]+)?\]\]', replace_wiki_img, content)

    # ② 替换相对路径图片 ![alt](../assets/x.svg)
    def replace_rel_img(m):
        full, alt, path = m.group(0), m.group(1), m.group(2).strip()
        if path.startswith('/images/') or path.startswith('http'):
            return full
        fn = os.path.basename(os.path.normpath(os.path.join(note_dir, unquote(path))))
        return f"![{alt}](/images/{fn})" if os.path.exists(os.path.join(IMAGE_DIR, fn)) else full
    content = re.sub(r'!\[([^\]]*)\]\(([^)]+)\)', replace_rel_img, content)

    # ③ 转换内部链接 [[XX 标题]] → [标题](/posts/ID) 或删除
    def replace_wiki_link(m):
        pid, clean = find_post_by_wiki(m.group(1), posts)
        return f"[{clean}](/posts/{pid})" if pid else ""
    content = re.sub(r'\[\[([^\]]+)\]\]', replace_wiki_link, content)

    return content


def reimport(notes):
    posts = load_post_map()
    image_idx = build_image_index()

    print(f"开始处理 {len(notes)} 篇文章...\n")
    for post_id, note_rel in notes:
        with open(os.path.join(VAULT_ROOT, note_rel), 'r', encoding='utf-8') as f:
            raw = f.read()

        # 去掉第一行标签行
        lines = raw.split('\n')
        first = lines[0].strip()
        if first and all(p.startswith('#') for p in first.split() if p):
            content = '\n'.join(lines[1:]).lstrip('\n')
        else:
            content = raw

        content = process_content(content, note_rel, posts, image_idx)

        sql_file = f"/tmp/reimport_{post_id}.sql"
        with open(sql_file, 'w', encoding='utf-8') as f:
            escaped = content.replace("\\", "\\\\").replace("'", "\\'")
            f.write(f"SET NAMES utf8mb4;\nUPDATE post SET content='{escaped}' WHERE id={post_id};\n")

        r = subprocess.run(
            ["mysql", "-uroot", "-h127.0.0.1", "bytedepth"],
            stdin=open(sql_file, 'r', encoding='utf-8'),
            capture_output=True, text=True
        )
        status = "✅" if r.returncode == 0 else "❌"
        print(f"  {status} [{post_id}] {os.path.basename(note_rel)}")

    print("\n完成！")


if __name__ == "__main__":
    # 使用时填充 NOTES 列表，例如：
    # NOTES = [(4, "12 性能工程/01 高并发系统设计.md"), ...]
    reimport(NOTES)
