#!/usr/bin/env python3
"""Ensure UI gate scripts reject missing baselines instead of passing silently."""

import contextlib
import io
import sys
import tempfile
from pathlib import Path
from unittest.mock import patch

SCRIPT_DIR = Path(__file__).resolve().parents[1] / "script"
sys.path.insert(0, str(SCRIPT_DIR))

import check_design_tokens
import check_hardcoded_cjk
import check_hardcoded_font_size
import check_icon_content_description
import check_touch_target


def assert_missing_baseline_fails(module, check_scan_stats: bool = False) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        missing = Path(tmp) / "missing-baseline.txt"
        patches = [patch.object(module, "scan_all", return_value={})]
        if check_scan_stats:
            patches.append(patch.object(module, "count_info_stats", return_value=(0, 0)))
        with patch.object(sys, "argv", [module.__name__, "--baseline", str(missing)]):
            with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                for patcher in patches:
                    patcher.start()
                try:
                    assert module.main() == 2, module.__name__
                finally:
                    for patcher in reversed(patches):
                        patcher.stop()


def test_all_ui_gates_fail_when_baseline_is_missing():
    for module in (
        check_hardcoded_cjk,
        check_touch_target,
        check_icon_content_description,
        check_hardcoded_font_size,
        check_design_tokens,
    ):
        assert_missing_baseline_fails(module, check_scan_stats=module is check_design_tokens)


if __name__ == "__main__":
    test_all_ui_gates_fail_when_baseline_is_missing()
    print("all baseline fail-closed tests passed")
