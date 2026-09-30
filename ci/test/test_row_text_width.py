#!/usr/bin/env python3
"""
test_row_text_width.py — check_row_text_width.py 的单元测试。

运行: py -3 ci/test/test_row_text_width.py
"""

import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parents[1] / "script"
sys.path.insert(0, str(SCRIPT_DIR))

from check_row_text_width import scan_file  # noqa: E402

# 反例：Row 内两个子项，文本没有取宽 → 放大字号时会被压成一列
RISKY = """
@Composable
fun Row(row: Server) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = row.name,
            style = MaterialTheme.typography.bodyLarge,
        )
        MuseSwitch(checked = row.enabled, onCheckedChange = {})
    }
}
"""

# 已修：文本加了 weight(1f) 取剩余宽度
SAFE_WEIGHT = """
@Composable
fun Row(row: Server) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.name)
            Text(text = row.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MuseSwitch(checked = row.enabled, onCheckedChange = {})
    }
}
"""

# 已修：单行文本 + maxLines 兜底（不会撑成几十行）
SAFE_MAXLINES = """
@Composable
fun Row(row: Server) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = row.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        MuseIcon(row.icon)
    }
}
"""

# 已修：取了宽且限行 → 安全
SAFE_WEIGHT_AND_LINES = """
@Composable
fun Row(row: Server) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = row.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MuseSwitch(checked = row.enabled, onCheckedChange = {})
    }
}
"""

# 只取宽、不限行：剩余宽度极小时仍会逐字换行撑高整行 → 仍需 maxLines 兜底
RISKY_WEIGHT_ONLY = """
@Composable
fun Row(row: Server) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = row.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        MuseSwitch(checked = row.enabled, onCheckedChange = {})
    }
}
"""

# 单子项 Row 且文本未显式取宽：Row 会按最大宽度给唯一子项，无兄弟竞争 → 不判定
SINGLE_CHILD_UNBOUNDED = """
@Composable
fun Row(row: Server) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = row.name,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
"""

# 槽位参数里的 Text（TextField 的 label）不是行内直接子项，不判定
SAFE_SLOT_LAMBDA = """
@Composable
fun Row() {
    Row(modifier = Modifier.fillMaxWidth()) {
        MuseTextField(
            label = { Text("名称") },
            value = name,
            onValueChange = {},
        )
        MuseSwitch(checked = true, onCheckedChange = {})
    }
}
"""

# 嵌套：Row 内的 Column 里有 Text，Text 不是 Row 的直接子项但从父级取宽 → 不判定
SAFE_NESTED_COLUMN = """
@Composable
fun Row() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "标题")
        }
        MuseSwitch(checked = true, onCheckedChange = {})
    }
}
"""

# Row 跨行写法：参数换行后 `) {` 另起一行
RISKY_MULTILINE = """
@Composable
fun Row(row: Server) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = row.name,
            style = MaterialTheme.typography.bodyLarge,
        )
        Icon(imageVector = MuseIcons.x, contentDescription = null)
    }
}
"""

CASES = [
    ("risky_no_width_no_lines", RISKY, 1),
    ("risky_weight_only_no_lines", RISKY_WEIGHT_ONLY, 1),
    ("safe_single_child_no_competition", SINGLE_CHILD_UNBOUNDED, 0),
    ("safe_weight_with_lines", SAFE_WEIGHT, 0),
    ("safe_maxlines_one_liner", SAFE_MAXLINES, 0),
    ("safe_weight_and_lines", SAFE_WEIGHT_AND_LINES, 0),
    ("safe_slot_lambda", SAFE_SLOT_LAMBDA, 0),
    ("safe_nested_column", SAFE_NESTED_COLUMN, 0),
    ("risky_multiline_row", RISKY_MULTILINE, 1),
]


def main() -> int:
    import tempfile

    failed = 0
    with tempfile.TemporaryDirectory() as tmp:
        for name, source, expected in CASES:
            path = Path(tmp) / f"{name}.kt"
            path.write_text(source, encoding="utf-8")
            hits = scan_file(path)
            ok = len(hits) == expected
            status = "OK  " if ok else "FAIL"
            print(f"[{status}] {name}: 期望 {expected} 处, 实际 {len(hits)} 处")
            if not ok:
                failed += 1
                for ln, text in hits:
                    print(f"        L{ln}: {text[:90]}")
    if failed:
        print(f"\n{failed} 个用例失败")
        return 1
    print(f"\n全部 {len(CASES)} 个用例通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
