"""Shared baseline validation helpers for CI gate scripts."""

from pathlib import Path
import sys


def require_baseline(path: Path, checker: str) -> bool:
    if path.is_file():
        return True
    print(f"[{checker}] ERROR: baseline 缺失或不是文件: {path}", file=sys.stderr)
    return False
