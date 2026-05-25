#!/usr/bin/env python3
"""
Obsidian 笔记通过 bytedepth API 导入博客。

用法：
  # 上传图片目录（本地）
  python3 import_via_api.py upload-images --dir ~/w/w/assets/主题/

  # 上传图片目录（远程）
  python3 import_via_api.py upload-images --dir ~/w/w/assets/主题/ --remote

  # 导入并发布笔记（本地）
  python3 import_via_api.py import \
    --note "05 计算机基础/07 并发与协程/01 从 C10K 到协程.md" \
    --title "从 C10K 到协程：并发模型的演化史" \
    --category 7

  # 导入并发布笔记（远程）
  python3 import_via_api.py import ... --remote
"""

import argparse
import http.cookiejar
import json
import mimetypes
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

VAULT_ROOT = os.path.expanduser("~/w/w")
IMAGE_MAP_FILE = "/tmp/bytedepth_image_map.json"
IMAGE_EXTS = {".png", ".jpg", ".jpeg", ".svg", ".gif", ".webp"}

LOCAL_BASE = "http://localhost:8080"
REMOTE_BASE = "http://175.24.197.202"
ADMIN_USER = "admin"
ADMIN_PASS = "admin2026"


# ---------------------------------------------------------------------------
# HTTP 工具
# ---------------------------------------------------------------------------

def make_session() -> urllib.request.OpenerDirector:
    jar = http.cookiejar.CookieJar()
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def http_get(opener: urllib.request.OpenerDirector, url: str) -> str:
    with opener.open(url) as r:
        return r.read().decode("utf-8")


def http_post_form(opener: urllib.request.OpenerDirector, url: str, data: dict) -> str:
    body = urllib.parse.urlencode(data).encode("utf-8")
    req = urllib.request.Request(
        url, data=body,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    try:
        with opener.open(req) as r:
            return r.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.read().decode("utf-8")


def http_post_multipart(
    opener: urllib.request.OpenerDirector,
    url: str,
    field: str,
    filename: str,
    data: bytes,
    content_type: str,
    csrf: str,
) -> str:
    boundary = "----BytedepthBoundary7MA4YWxkTrZu0gW"
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="{field}"; filename="{filename}"\r\n'
        f"Content-Type: {content_type}\r\n\r\n"
    ).encode() + data + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(
        url, data=body,
        headers={
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "X-CSRF-TOKEN": csrf,
        },
    )
    try:
        with opener.open(req) as r:
            return r.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.read().decode("utf-8")


def get_csrf(opener: urllib.request.OpenerDirector, base: str) -> str:
    html = http_get(opener, f"{base}/admin/posts/new")
    m = re.search(r'name="_csrf"\s+value="([^"]+)"', html)
    if not m:
        raise RuntimeError("无法获取 CSRF token，请检查是否已登录")
    return m.group(1)


def login(opener: urllib.request.OpenerDirector, base: str) -> None:
    html = http_get(opener, f"{base}/login")
    m = re.search(r'name="_csrf"\s+value="([^"]+)"', html)
    if not m:
        raise RuntimeError("登录页面未找到 CSRF token")
    csrf = m.group(1)
    http_post_form(opener, f"{base}/login", {
        "username": ADMIN_USER,
        "password": ADMIN_PASS,
        "_csrf": csrf,
    })
    check = http_get(opener, f"{base}/admin/posts")
    if "文章管理" not in check:
        raise RuntimeError(f"登录失败，请确认 {base} 可访问且密码正确")
    print(f"✅ 已登录 {base}")


# ---------------------------------------------------------------------------
# 图片上传
# ---------------------------------------------------------------------------

def upload_images(directory: str, base: str) -> dict[str, str]:
    """上传目录下所有图片，返回 {原文件名: /images/uuid.ext} 映射"""
    opener = make_session()
    login(opener, base)

    image_map: dict[str, str] = {}
    # 加载已有映射（增量上传）
    if os.path.exists(IMAGE_MAP_FILE):
        with open(IMAGE_MAP_FILE, encoding="utf-8") as f:
            image_map = json.load(f)

    dir_path = Path(os.path.expanduser(directory))
    files = [p for p in dir_path.iterdir() if p.suffix.lower() in IMAGE_EXTS]

    if not files:
        print(f"⚠️  目录 {directory} 下无图片文件")
        return image_map

    print(f"共找到 {len(files)} 张图片，开始上传...")
    for fp in sorted(files):
        if fp.name in image_map:
            print(f"  ⏭  {fp.name}（已上传，跳过）")
            continue
        csrf = get_csrf(opener, base)
        mime = mimetypes.guess_type(fp.name)[0] or "application/octet-stream"
        result_json = http_post_multipart(
            opener, f"{base}/admin/images/upload",
            "file", fp.name, fp.read_bytes(), mime, csrf,
        )
        try:
            result = json.loads(result_json)
            url = result.get("url", "")
            if url:
                image_map[fp.name] = url
                print(f"  ✅ {fp.name} → {url}")
            else:
                print(f"  ❌ {fp.name}：{result_json[:100]}")
        except json.JSONDecodeError:
            print(f"  ❌ {fp.name}：响应解析失败 {result_json[:100]}")

    with open(IMAGE_MAP_FILE, "w", encoding="utf-8") as f:
        json.dump(image_map, f, ensure_ascii=False, indent=2)
    print(f"\n映射已保存到 {IMAGE_MAP_FILE}")
    return image_map


# ---------------------------------------------------------------------------
# 内容转换
# ---------------------------------------------------------------------------

def load_image_map() -> dict[str, str]:
    if not os.path.exists(IMAGE_MAP_FILE):
        return {}
    with open(IMAGE_MAP_FILE, encoding="utf-8") as f:
        return json.load(f)


def convert_obsidian(content: str, image_map: dict[str, str], post_map: dict[str, int]) -> str:
    lines = content.split("\n")
    # 去掉纯 #tag 首行
    if lines and re.match(r'^(#\w+\s*)+$', lines[0].strip()):
        lines = lines[1:]
    content = "\n".join(lines)

    # ![[filename|size]] → ![alt](/images/uuid)
    def replace_wiki_img(m: re.Match) -> str:
        name = m.group(1).split("|")[0].strip()
        url = image_map.get(name, "")
        if not url:
            return f"<!-- image not found: {name} -->"
        alt = re.sub(r'\.(drawio\.svg|svg|png|jpg|jpeg|gif|webp)$', '', name).replace("-", " ")
        return f"![{alt}]({url})"

    content = re.sub(r'!\[\[([^\]]+)\]\]', replace_wiki_img, content)

    # ![alt](../assets/xxx/file.ext) → ![alt](/images/uuid)
    def replace_rel_img(m: re.Match) -> str:
        alt, path = m.group(1), urllib.parse.unquote(m.group(2))
        if path.startswith("/images/") or path.startswith("http"):
            return m.group(0)
        name = os.path.basename(path)
        url = image_map.get(name, "")
        return f"![{alt}]({url})" if url else m.group(0)

    content = re.sub(r'!\[([^\]]*)\]\(([^)]+)\)', replace_rel_img, content)

    # [[内链]] → [文字](/posts/ID) 或纯文字
    def replace_wiki_link(m: re.Match) -> str:
        text = m.group(1).strip()
        clean = re.sub(r'^\d+\s+', '', text)
        for title, pid in post_map.items():
            if clean in title or title in clean:
                return f"[{clean}](/posts/{pid})"
        return clean  # 无匹配则保留文字，去掉双括号

    content = re.sub(r'\[\[([^\]]+)\]\]', replace_wiki_link, content)
    return content.strip()


# ---------------------------------------------------------------------------
# 文章创建与发布
# ---------------------------------------------------------------------------

def fetch_post_map(opener: urllib.request.OpenerDirector, base: str) -> dict[str, int]:
    """从文章列表页抓取 {标题: id} 映射，用于内部链接转换"""
    html = http_get(opener, f"{base}/admin/posts")
    pairs = re.findall(r'/admin/posts/(\d+)/edit[^>]*>[^<]*</a>\s*(?:<[^>]+>)*([^<]{3,})', html)
    return {title.strip(): int(pid) for pid, title in pairs}


def create_and_publish(
    opener: urllib.request.OpenerDirector,
    base: str,
    title: str,
    content: str,
    category_id: int,
) -> int:
    csrf = get_csrf(opener, base)
    http_post_form(opener, f"{base}/admin/posts", {
        "title": title,
        "content": content,
        "categoryId": str(category_id),
        "_csrf": csrf,
    })
    html = http_get(opener, f"{base}/admin/posts")
    ids = re.findall(r'/admin/posts/(\d+)/edit', html)
    if not ids:
        raise RuntimeError("文章创建后无法获取 ID")
    post_id = int(ids[0])

    csrf = get_csrf(opener, base)
    http_post_form(opener, f"{base}/admin/posts/{post_id}/publish", {"_csrf": csrf})
    return post_id


# ---------------------------------------------------------------------------
# CLI 入口
# ---------------------------------------------------------------------------

def cmd_upload_images(args: argparse.Namespace) -> None:
    base = REMOTE_BASE if args.remote else LOCAL_BASE
    upload_images(args.dir, base)


def cmd_import(args: argparse.Namespace) -> None:
    base = REMOTE_BASE if args.remote else LOCAL_BASE
    note_path = os.path.join(VAULT_ROOT, args.note)
    if not os.path.exists(note_path):
        print(f"❌ 笔记文件不存在：{note_path}", file=sys.stderr)
        sys.exit(1)

    opener = make_session()
    login(opener, base)

    image_map = load_image_map()
    post_map = fetch_post_map(opener, base)

    with open(note_path, encoding="utf-8") as f:
        raw = f.read()

    content = convert_obsidian(raw, image_map, post_map)
    print(f"📄 {args.title}（{len(content)} 字符）")

    post_id = create_and_publish(opener, base, args.title, content, args.category)
    print(f"✅ 创建并发布成功  {base}/posts/{post_id}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Obsidian → bytedepth API 导入工具")
    parser.add_argument("--remote", action="store_true", help="目标为腾讯云远程环境")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_upload = sub.add_parser("upload-images", help="上传图片目录")
    p_upload.add_argument("--dir", required=True, help="图片目录路径")
    p_upload.set_defaults(func=cmd_upload_images)

    p_import = sub.add_parser("import", help="导入笔记并发布")
    p_import.add_argument("--note", required=True, help="笔记相对路径（相对于 ~/w/w/）")
    p_import.add_argument("--title", required=True, help="文章标题")
    p_import.add_argument("--category", type=int, required=True, help="分类 ID")
    p_import.set_defaults(func=cmd_import)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
