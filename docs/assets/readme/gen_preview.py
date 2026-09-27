#!/usr/bin/env python3
"""
gen_preview.py — 把仓库根 README.md 渲染成 GitHub 风格预览页(本地审阅用)。

- 支持本项目 README 用到的构造: 标题 / 段落 / 列表 / 表格 / 代码块 / 引用 /
  GitHub 警告块([!WARNING] / [!NOTE]) / 原生 HTML 透传(<p> <details> <img> 等)
- 图片与链接路径重写为相对 preview.html 所在目录
- 输出: preview.html(同目录)

用法:
    python docs/assets/readme/gen_preview.py
"""

import html
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]          # 仓库根(= 1muse/)
SRC = ROOT / "README.md"
OUT = Path(__file__).resolve().parent / "preview.html"

INLINE_CODE_RE = re.compile(r"`([^`]+)`")
BOLD_RE = re.compile(r"\*\*([^*]+)\*\*")
LINK_RE = re.compile(r"\[([^\]]+)\]\(([^)]+)\)")
IMG_RE = re.compile(r"!\[([^\]]*)\]\(([^)]+)\)")


def esc(text: str) -> str:
    return html.escape(text, quote=False)


def inline(text: str) -> str:
    """行内元素: 先保护 code, 再 bold / 图片 / 链接。"""
    codes: list[str] = []

    def stash(m):
        codes.append(m.group(1))
        return f"\x00{len(codes)-1}\x00"

    text = INLINE_CODE_RE.sub(stash, text)
    text = esc(text)
    text = IMG_RE.sub(lambda m: f'<img src="{m.group(2)}" alt="{m.group(1)}">', text)
    text = LINK_RE.sub(lambda m: f'<a href="{m.group(2)}">{m.group(1)}</a>', text)
    text = BOLD_RE.sub(lambda m: f"<strong>{m.group(1)}</strong>", text)
    text = re.sub(
        r"\x00(\d+)\x00",
        lambda m: f"<code>{esc(codes[int(m.group(1))])}</code>",
        text,
    )
    return text


def rewrite_paths(text: str) -> str:
    """把仓库根相对路径重写为 preview.html 所在目录的相对路径。"""
    depth = "../../.."  # docs/assets/readme -> 仓库根
    text = text.replace('src="docs/assets/readme/', 'src="')
    text = text.replace('src="screenshots/', f'src="{depth}/screenshots/')
    text = text.replace('src="art/', f'src="{depth}/art/')
    text = text.replace('href="README_EN.md', f'href="{depth}/README_EN.md')
    text = text.replace('href="用户手册.md', f'href="{depth}/用户手册.md')
    text = text.replace('href="CONTRIBUTING.md', f'href="{depth}/CONTRIBUTING.md')
    text = text.replace('href="SECURITY.md', f'href="{depth}/SECURITY.md')
    text = text.replace('href="UI组件库维护规范.md', f'href="{depth}/UI组件库维护规范.md')
    text = text.replace('href="LICENSE"', f'href="{depth}/LICENSE"')
    text = text.replace('href="NOTICE"', f'href="{depth}/NOTICE"')
    return text


def convert(md: str) -> str:
    lines = md.splitlines()
    out: list[str] = []
    i = 0
    n = len(lines)

    def is_table_start(idx: int) -> bool:
        if idx + 1 >= n:
            return False
        first, second = lines[idx], lines[idx + 1]
        return ("|" in first) and bool(re.match(r"^\s*\|?[\s:\-|]+\|[\s:\-|]*$", second))

    while i < n:
        line = lines[i]

        # 代码块
        if line.strip().startswith("```"):
            lang = line.strip()[3:].strip()
            code_lines = []
            i += 1
            while i < n and not lines[i].strip().startswith("```"):
                code_lines.append(lines[i])
                i += 1
            i += 1
            cls = f' class="language-{lang}"' if lang else ""
            out.append(f"<pre><code{cls}>{esc(chr(10).join(code_lines))}</code></pre>")
            continue

        # 原生 HTML 行(含缩进的子行)直接透传
        if line.lstrip().startswith("<") and line.strip():
            out.append(rewrite_paths(line))
            i += 1
            continue

        # 空行
        if not line.strip():
            i += 1
            continue

        # 水平线
        if re.match(r"^\s*-{3,}\s*$", line):
            out.append("<hr>")
            i += 1
            continue

        # 标题
        m = re.match(r"^(#{1,4})\s+(.*)$", line)
        if m:
            level = len(m.group(1))
            out.append(f"<h{level}>{inline(m.group(2))}</h{level}>")
            i += 1
            continue

        # 表格
        if is_table_start(i):
            header = [c.strip() for c in lines[i].strip().strip("|").split("|")]
            i += 2
            rows = []
            while i < n and "|" in lines[i] and lines[i].strip():
                rows.append([c.strip() for c in lines[i].strip().strip("|").split("|")])
                i += 1
            thead = "".join(f"<th>{inline(c)}</th>" for c in header)
            tbody = "".join(
                "<tr>" + "".join(f"<td>{inline(c)}</td>" for c in r) + "</tr>" for r in rows
            )
            out.append(f"<table><thead><tr>{thead}</tr></thead><tbody>{tbody}</tbody></table>")
            continue

        # 引用块(含 GitHub 警告)
        if line.lstrip().startswith(">"):
            quote_lines = []
            while i < n and lines[i].lstrip().startswith(">"):
                quote_lines.append(re.sub(r"^\s*>\s?", "", lines[i]))
                i += 1
            first = quote_lines[0].strip()
            alert = re.match(r"^\[!(WARNING|NOTE|TIP|IMPORTANT|CAUTION)\]$", first)
            body = "\n".join(quote_lines[1:] if alert else quote_lines).strip()
            if alert:
                kind = alert.group(1).lower()
                title = alert.group(1).capitalize()
                out.append(
                    f'<div class="alert alert-{kind}">'
                    f'<p class="alert-title">{title}</p>'
                    f"<p>{inline(body)}</p></div>"
                )
            else:
                out.append(f"<blockquote><p>{inline(body)}</p></blockquote>")
            continue

        # 无序列表
        if re.match(r"^\s*-\s+", line):
            items = []
            while i < n and re.match(r"^\s*-\s+", lines[i]):
                items.append(re.sub(r"^\s*-\s+", "", lines[i]))
                i += 1
            lis = "".join(f"<li>{inline(it)}</li>" for it in items)
            out.append(f"<ul>{lis}</ul>")
            continue

        # 有序列表
        if re.match(r"^\s*\d+\.\s+", line):
            items = []
            while i < n and re.match(r"^\s*\d+\.\s+", lines[i]):
                items.append(re.sub(r"^\s*\d+\.\s+", "", lines[i]))
                i += 1
            lis = "".join(f"<li>{inline(it)}</li>" for it in items)
            out.append(f"<ol>{lis}</ol>")
            continue

        # 段落
        para = [line]
        i += 1
        while (
            i < n
            and lines[i].strip()
            and not lines[i].lstrip().startswith("<")
            and not re.match(r"^(#{1,4})\s+", lines[i])
            and not lines[i].strip().startswith("```")
            and not re.match(r"^\s*-{3,}\s*$", lines[i])
            and not lines[i].lstrip().startswith(">")
            and not re.match(r"^\s*-\s+", lines[i])
            and not re.match(r"^\s*\d+\.\s+", lines[i])
            and "|" not in lines[i]
        ):
            para.append(lines[i])
            i += 1
        out.append(f"<p>{inline(' '.join(para))}</p>")

    return "\n".join(out)


CSS = """
  * { box-sizing: border-box; }
  body {
    margin: 0; background: #ffffff; color: #1f2328;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans", Helvetica, Arial,
      "Microsoft YaHei", sans-serif;
    font-size: 16px; line-height: 1.5;
  }
  .gh-topbar {
    background: #f6f8fa; border-bottom: 1px solid #d1d9e0; color: #59636e;
    font-size: 14px; padding: 10px 24px;
  }
  .gh-topbar b { color: #1f2328; }
  .markdown-body { max-width: 1012px; margin: 0 auto; padding: 40px 45px 80px; }
  h1, h2, h3 { font-weight: 600; line-height: 1.25; margin: 24px 0 16px; }
  h2 { font-size: 1.5em; padding-bottom: .3em; border-bottom: 1px solid #d1d9e0b3; }
  h3 { font-size: 1.25em; }
  p { margin: 0 0 16px; }
  a { color: #0969da; text-decoration: none; }
  a:hover { text-decoration: underline; }
  code {
    font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
    font-size: 85%; background: rgba(129,139,152,.12); border-radius: 6px;
    padding: .2em .4em;
  }
  pre {
    background: #f6f8fa; border-radius: 6px; padding: 16px; overflow: auto;
    margin: 0 0 16px;
  }
  pre code { background: none; padding: 0; font-size: 85%; line-height: 1.45; white-space: pre; }
  ul, ol { margin: 0 0 16px; padding-left: 2em; }
  li { margin: .25em 0; }
  hr { border: 0; height: 1px; background: #d1d9e0; margin: 24px 0; }
  img { max-width: 100%; }
  table { border-collapse: collapse; margin: 0 0 16px; display: block; overflow: auto; }
  th, td { border: 1px solid #d1d9e0; padding: 6px 13px; }
  th { background: #f6f8fa; font-weight: 600; }
  tr:nth-child(2n) td { background: #f6f8fa; }
  blockquote {
    margin: 0 0 16px; padding: 0 1em; color: #59636e;
    border-left: .25em solid #d1d9e0;
  }
  blockquote p:last-child { margin-bottom: 0; }
  .alert { border-radius: 6px; padding: 8px 16px; margin: 0 0 16px; border-left: .25em solid; }
  .alert p { margin: 0; }
  .alert .alert-title { font-weight: 600; margin-bottom: 2px; }
  .alert-warning { border-color: #9a6700; background: #fff8c5; }
  .alert-warning .alert-title { color: #9a6700; }
  .alert-note { border-color: #0969da; background: #ddf4ff; }
  .alert-note .alert-title { color: #0969da; }
  details {
    border: 1px solid #d1d9e0; border-radius: 6px; margin: 0 0 16px;
    padding: 8px 16px;
  }
  details summary {
    cursor: pointer; font-weight: 600; padding: 4px 0;
  }
  details[open] summary { margin-bottom: 8px; border-bottom: 1px solid #d1d9e0b3; padding-bottom: 8px; }
"""

HTML_TMPL = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>Muse README 预览（GitHub 风格）</title>
<style>{css}</style>
</head>
<body>
<div class="gh-topbar">预览 · <b>Zer0Qing/Muse</b> — README 渲染效果（本地生成，未上传）</div>
<article class="markdown-body">
{body}
</article>
</body>
</html>
"""


def main() -> int:
    md = SRC.read_text(encoding="utf-8")
    body = convert(md)
    html_out = HTML_TMPL.format(css=CSS, body=body)
    OUT.write_text(html_out, encoding="utf-8")
    print(f"OK: {OUT} ({len(html_out)} chars)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
