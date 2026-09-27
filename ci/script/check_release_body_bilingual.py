#!/usr/bin/env python3
"""
check_release_body_bilingual.py — release_body 中英双写规范校验。

规范(自 v2.3.0 起): `releases/v<版本>/release_body.md` 必须中英双写 —
上段中文(发布原文), 独立一行 `---` 分隔, 下段英文, 标题形如 `# Muse vX.Y.Z (English)`。
英文为忠实对译, 不引入中文段没有的信息, 段内不再夹中文。

本脚本校验(发布前运行):
  1. 文件存在, 且存在 `(English)` 形式的英文段标题;
  2. 英文段标题之前有独立一行 `---` 分隔符;
  3. 中文段包含中文且非空;
  4. 英文段包含英文内容、且不夹中文字符。

用法:
    python ci/script/check_release_body_bilingual.py releases/v2.3.0/release_body.md

退出码:
    0 — 通过
    1 — 不满足规范(CI/发布前检查失败)
"""

import argparse
import re
import sys
from pathlib import Path

# 英文段标题: 1~3 级标题, 以 (English) 结尾
ENGLISH_HEADING_RE = re.compile(r"^#{1,3}\s+.*\(English\)\s*$", re.MULTILINE)
# 中文字符(CJK 统一表意文字基本区)
CJK_RE = re.compile(r"[\u4e00-\u9fff]")
# 英文内容特征(连续 3 个以上拉丁字母)
LATIN_RE = re.compile(r"[A-Za-z]{3,}")
# 分隔符(独立一行)
SEPARATOR = "---"

# 英文段允许出现的例外(极少数必须保留的原文缩写/专名;均为 ASCII, 此处留空备用)
ALLOWED_CJK_IN_ENGLISH: tuple[str, ...] = ()


def check(path: Path) -> list[str]:
    """执行校验, 返回问题列表(空列表 = 通过)。"""
    if not path.exists():
        return [f"文件不存在: {path}"]
    try:
        text = path.read_text(encoding="utf-8")
    except Exception as e:  # noqa: BLE001 - 校验脚本, 统一转为失败信息
        return [f"读取失败: {e}"]

    match = ENGLISH_HEADING_RE.search(text)
    if match is None:
        return ["缺少英文段标题(应为 `# Muse vX.Y.Z (English)` 形式, 独立一行)"]

    zh_part = text[: match.start()]
    en_part = text[match.end():]

    problems: list[str] = []

    # 2. 分隔符: 英文段标题之前应有独立一行 ---
    zh_lines = [line.strip() for line in zh_part.splitlines()]
    if SEPARATOR not in zh_lines:
        problems.append("英文段标题之前缺少独立一行 `---` 分隔符")

    # 3. 中文段
    if not CJK_RE.search(zh_part):
        problems.append("中文段为空或缺少中文字符")

    # 4. 英文段
    if not LATIN_RE.search(en_part):
        problems.append("英文段为空或缺少英文内容")
    cjk_in_en = CJK_RE.findall(en_part)
    if cjk_in_en:
        for ch in set(cjk_in_en):
            if ch not in ALLOWED_CJK_IN_ENGLISH:
                problems.append(f"英文段夹入中文字符 `{ch}`(英文段应为纯英文对译)")
                break

    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description="release_body 中英双写规范校验")
    parser.add_argument("path", type=Path, help="release_body.md 路径, 如 releases/v2.3.0/release_body.md")
    args = parser.parse_args()

    problems = check(args.path)
    if problems:
        print(f"FAIL: {args.path} 不满足中英双写规范:")
        for p in problems:
            print(f"  - {p}")
        return 1

    print(f"PASS: {args.path} 中英双语格式完整(中文段 + 分隔符 + 英文段)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
