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
import hashlib
import http.cookiejar
import json
import mimetypes
import os
import re
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path

VAULT_ROOT = os.path.expanduser("~/w/w")
IMAGE_MAP_FILE = "/tmp/bytedepth_image_map.json"
SYNC_STATE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sync_state.json")
IMAGE_EXTS = {".png", ".jpg", ".jpeg", ".svg", ".gif", ".webp"}

LOCAL_BASE = "http://localhost:8080"
REMOTE_BASE = "https://bytedepth.cn"
ADMIN_USER = "admin"
ADMIN_PASS = "admin2026"


# ---------------------------------------------------------------------------
# 同步状态管理
# ---------------------------------------------------------------------------

def compute_note_hash(note_rel: str) -> str:
    note_path = os.path.join(VAULT_ROOT, note_rel)
    with open(note_path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()[:16]


def load_sync_state() -> dict:
    if os.path.exists(SYNC_STATE_FILE):
        with open(SYNC_STATE_FILE, encoding="utf-8") as f:
            return json.load(f)
    return {}


def save_sync_state(state: dict) -> None:
    with open(SYNC_STATE_FILE, "w", encoding="utf-8") as f:
        json.dump(state, f, ensure_ascii=False, indent=2)


def record_sync(note_rel: str, post_id: int, title: str, category_id: int | None, env: str) -> None:
    state = load_sync_state()
    state[note_rel] = {
        "post_id": post_id,
        "title": title,
        "category_id": category_id,
        "last_hash": compute_note_hash(note_rel),
        "last_sync": datetime.now().isoformat(timespec="seconds"),
        "env": env,
    }
    save_sync_state(state)
    print(f"   📝 同步状态已记录 ({SYNC_STATE_FILE})")


# ---------------------------------------------------------------------------
# HTTP 工具
# ---------------------------------------------------------------------------

def make_session() -> urllib.request.OpenerDirector:
    jar = http.cookiejar.CookieJar()
    # 服务器用 IP 直连，无有效域名证书，跳过 SSL 验证
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    return urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(jar),
        urllib.request.HTTPSHandler(context=ctx),
    )


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
    # Spring Security multipart 需要 CSRF 作为 URL 参数传递
    url_with_csrf = f"{url}?_csrf={urllib.parse.quote(csrf)}"
    boundary = "----BytedepthBoundary7MA4YWxkTrZu0gW"
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="{field}"; filename="{filename}"\r\n'
        f"Content-Type: {content_type}\r\n\r\n"
    ).encode() + data + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(
        url_with_csrf, data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
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


def update_post(
    opener: urllib.request.OpenerDirector,
    base: str,
    post_id: int,
    title: str,
    content: str,
    category_id: int | None,
) -> None:
    """更新已有文章内容，调用 POST /admin/posts/{id}"""
    data: dict[str, str] = {
        "title": title,
        "content": content,
        "_csrf": get_csrf(opener, base),
    }
    if category_id is not None:
        data["categoryId"] = str(category_id)
    http_post_form(opener, f"{base}/admin/posts/{post_id}", data)


# ---------------------------------------------------------------------------
# CLI 入口
# ---------------------------------------------------------------------------

def cmd_upload_images(args: argparse.Namespace) -> None:
    base = REMOTE_BASE if args.remote else LOCAL_BASE
    upload_images(args.dir, base)


def _prepare_content(opener: urllib.request.OpenerDirector, base: str, note: str, title: str) -> str:
    note_path = os.path.join(VAULT_ROOT, note)
    if not os.path.exists(note_path):
        print(f"❌ 笔记文件不存在：{note_path}", file=sys.stderr)
        sys.exit(1)
    image_map = load_image_map()
    post_map = fetch_post_map(opener, base)
    with open(note_path, encoding="utf-8") as f:
        raw = f.read()
    content = convert_obsidian(raw, image_map, post_map)
    print(f"📄 {title}（{len(content)} 字符）")
    return content


def cmd_import(args: argparse.Namespace) -> None:
    base = REMOTE_BASE if args.remote else LOCAL_BASE
    opener = make_session()
    login(opener, base)
    content = _prepare_content(opener, base, args.note, args.title)

    env = "remote" if args.remote else "local"
    if args.post_id:
        update_post(opener, base, args.post_id, args.title, content, args.category)
        print(f"✅ 更新成功  {base}/posts/{args.post_id}")
        record_sync(args.note, args.post_id, args.title, args.category, env)
    else:
        post_id = create_and_publish(opener, base, args.title, content, args.category)
        print(f"✅ 创建并发布成功  {base}/posts/{post_id}")
        record_sync(args.note, post_id, args.title, args.category, env)


def cmd_update(args: argparse.Namespace) -> None:
    """单独的 update 子命令：更新已有文章（不改变发布状态）"""
    base = REMOTE_BASE if args.remote else LOCAL_BASE
    opener = make_session()
    login(opener, base)
    content = _prepare_content(opener, base, args.note, args.title)
    update_post(opener, base, args.post_id, args.title, content, args.category)
    env = "remote" if args.remote else "local"
    print(f"✅ 更新成功  {base}/posts/{args.post_id}")
    record_sync(args.note, args.post_id, args.title, args.category, env)


def cmd_status(args: argparse.Namespace) -> None:
    """显示本地笔记与博客的同步状态"""
    env = "remote" if args.remote else "local"
    state = load_sync_state()
    entries = {k: v for k, v in state.items() if v.get("env") == env}

    if not entries:
        print(f"暂无同步记录（{env}）")
        return

    needs_update: list[tuple[str, dict]] = []
    for note_rel, info in sorted(entries.items()):
        note_path = os.path.join(VAULT_ROOT, note_rel)
        if not os.path.exists(note_path):
            print(f"  ❓ 文件不存在   {note_rel}")
            continue
        current_hash = compute_note_hash(note_rel)
        tag = info["last_sync"][:10]
        if current_hash == info["last_hash"]:
            print(f"  ✅ 已同步 [{tag}]  {note_rel}  →  posts/{info['post_id']}")
        else:
            print(f"  ⚠️  本地有修改   {note_rel}  →  posts/{info['post_id']}")
            needs_update.append((note_rel, info))

    if needs_update:
        flag = "--remote" if args.remote else ""
        print(f"\n共 {len(needs_update)} 篇需要更新，运行以下命令自动同步：")
        print(f"  python3 import_via_api.py sync {flag}")


def cmd_sync(args: argparse.Namespace) -> None:
    """自动更新所有本地有修改（hash 变化）的文章"""
    env = "remote" if args.remote else "local"
    base = REMOTE_BASE if args.remote else LOCAL_BASE
    state = load_sync_state()
    entries = {k: v for k, v in state.items() if v.get("env") == env}

    to_update = [
        (note_rel, info) for note_rel, info in entries.items()
        if os.path.exists(os.path.join(VAULT_ROOT, note_rel))
        and compute_note_hash(note_rel) != info["last_hash"]
    ]

    if not to_update:
        print(f"✅ 所有笔记均已同步（{env}），无需更新")
        return

    print(f"发现 {len(to_update)} 篇需要更新，开始同步...\n")
    opener = make_session()
    login(opener, base)

    for note_rel, info in to_update:
        print(f"📄 {note_rel}")
        content = _prepare_content(opener, base, note_rel, info["title"])
        update_post(opener, base, info["post_id"], info["title"], content, info.get("category_id"))
        record_sync(note_rel, info["post_id"], info["title"], info.get("category_id"), env)
        print(f"   ✅ 更新完成  {base}/posts/{info['post_id']}\n")

    print(f"同步完成，共更新 {len(to_update)} 篇")


def main() -> None:
    parser = argparse.ArgumentParser(description="Obsidian → bytedepth API 导入工具")
    parser.add_argument("--remote", action="store_true", help="目标为腾讯云远程环境")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_upload = sub.add_parser("upload-images", help="上传图片目录")
    p_upload.add_argument("--dir", required=True, help="图片目录路径")
    p_upload.set_defaults(func=cmd_upload_images)

    p_import = sub.add_parser("import", help="创建新文章并发布；若指定 --post-id 则更新已有文章")
    p_import.add_argument("--note", required=True, help="笔记相对路径（相对于 ~/w/w/）")
    p_import.add_argument("--title", required=True, help="文章标题")
    p_import.add_argument("--category", type=int, required=True, help="分类 ID")
    p_import.add_argument("--post-id", type=int, default=None, help="已有文章 ID（提供则更新，不提供则新建）")
    p_import.set_defaults(func=cmd_import)

    p_update = sub.add_parser("update", help="更新已有文章内容（不改变发布状态）")
    p_update.add_argument("--post-id", type=int, required=True, help="要更新的文章 ID")
    p_update.add_argument("--note", required=True, help="笔记相对路径（相对于 ~/w/w/）")
    p_update.add_argument("--title", required=True, help="文章标题")
    p_update.add_argument("--category", type=int, default=None, help="分类 ID（可选）")
    p_update.set_defaults(func=cmd_update)

    p_status = sub.add_parser("status", help="查看本地笔记与博客的同步状态")
    p_status.set_defaults(func=cmd_status)

    p_sync = sub.add_parser("sync", help="自动更新所有本地有修改（hash 变化）的文章")
    p_sync.set_defaults(func=cmd_sync)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
