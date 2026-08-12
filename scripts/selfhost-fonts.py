#!/usr/bin/env python3
"""将 Google Fonts CSS2 引用的 woff2 自托管到 static/fonts/，生成本地 fonts.css。

用法：先抓取 googleapis CSS 到 /tmp/google-fonts.css（带现代 Chrome UA），再运行本脚本。
下载失败的 @font-face 规则会被剔除，避免引用不存在的本地文件。
采用大括号配对扫描，避免正则 split 重组导致的结构错乱。
"""
import os
import re
import urllib.request

BASE_STATIC = "bytedepth-start/src/main/resources/static"
FONTS_DIR = os.path.join(BASE_STATIC, "fonts")
CSS_IN = "/tmp/google-fonts.css"
CSS_OUT = os.path.join(BASE_STATIC, "css", "fonts.css")
HEADERS = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                          "(KHTML, like Gecko) Chrome/151.0.7922.34 Safari/537.36"}

FAMILY_SLUG = {
    "DM Serif Display": "dm-serif-display",
    "Outfit": "outfit",
    "Source Serif 4": "source-serif-4",
    "JetBrains Mono": "jetbrains-mono",
}


def find_matching_brace(text: str, open_index: int) -> int:
    """从 open_index 处的 '{' 出发，返回配对 '}' 的索引。"""
    depth = 0
    i = open_index
    n = len(text)
    while i < n:
        ch = text[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def find_preceding_comment(text: str, at_index: int) -> tuple[str, int]:
    """返回紧邻 at_index 之前的 /* ... */ 注释文本（含分界），及其起始索引；无则返回 ('', at_index)。"""
    # 跳过 at_index 之前的空白
    j = at_index
    while j > 0 and text[j - 1] in " \t\r\n":
        j -= 1
    if j <= 0 or text[j - 1] != "/":
        return ("", at_index)
    # 往前找注释闭合 */
    close = j - 1  # 指向 '/'
    # text[close-1] 应是 '*', 再往前找 '/*'
    k = close - 2  # 跳过 '*/'
    while k > 1 and not (text[k] == "/" and text[k + 1] == "*"):
        k -= 1
    if k <= 1:
        return ("", at_index)
    return (text[k:close + 1], k)


def main() -> None:
    with open(CSS_IN, encoding="utf-8") as f:
        css = f.read()

    out_rules: list[str] = []
    total = downloaded = failed = 0
    for m in re.finditer(r"@font-face\s*\{", css):
        brace_open = m.end() - 1  # 指向 '{'
        brace_close = find_matching_brace(css, brace_open)
        if brace_close < 0:
            print("skip: unmatched brace")
            continue
        body = css[brace_open + 1:brace_close]
        total += 1

        fam = re.search(r"font-family:\s*'([^']+)'", body)
        url_match = re.search(r"url\((https://fonts\.gstatic\.com/[^)]+\.woff2)\)", body)
        if not fam or not url_match:
            continue
        family = fam.group(1)
        slug = FAMILY_SLUG.get(family)
        if not slug:
            print(f"skip unknown family: {family}")
            continue
        gstatic_url = url_match.group(1)
        filename = gstatic_url.rsplit("/", 1)[1]
        local_dir = os.path.join(FONTS_DIR, slug)
        os.makedirs(local_dir, exist_ok=True)
        local_file = os.path.join(local_dir, filename)
        rel_url = f"/fonts/{slug}/{filename}"

        if not os.path.exists(local_file):
            try:
                req = urllib.request.Request(gstatic_url, headers=HEADERS)
                with urllib.request.urlopen(req, timeout=30) as resp:
                    data = resp.read()
                if resp.status != 200 or not data:
                    raise RuntimeError(f"status {resp.status}")
                with open(local_file, "wb") as out:
                    out.write(data)
                downloaded += 1
                print(f"ok   {slug}/{filename} ({len(data)} bytes)")
            except Exception as e:
                failed += 1
                print(f"FAIL {gstatic_url} -> {e}")
                continue
        else:
            downloaded += 1

        body_local = body.replace(gstatic_url, rel_url)
        rule = "@font-face {\n" + body_local.strip() + "\n}\n"
        out_rules.append(rule)

    os.makedirs(os.path.dirname(CSS_OUT), exist_ok=True)
    header = ("/* 自托管 Google Fonts：woff2 随站点静态资源分发，避免 fonts.gstatic.com\n"
              " * 文件轮换导致的 404。Family 名与 theme.css 的 --bd-font-* 变量保持一致。\n"
              " * 由 scripts/selfhost-fonts.py 从 googleapis CSS2 生成。 */\n")
    with open(CSS_OUT, "w", encoding="utf-8") as f:
        f.write(header)
        f.write("\n".join(out_rules))
    print(f"\n@font-face rules: total={total} written={len(out_rules)} "
          f"downloaded={downloaded} failed={failed}")
    print(f"output: {CSS_OUT}")


if __name__ == "__main__":
    main()
