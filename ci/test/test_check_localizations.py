#!/usr/bin/env python3
"""Regression tests for strict locale matching."""

import contextlib
import io
import sys
import tempfile
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parents[1] / "script"
sys.path.insert(0, str(SCRIPT_DIR))

from check_localizations import check_locale


def write_resource(path: Path, key: str | None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    body = f'<string name="{key}">value</string>' if key else ""
    path.write_text(f"<resources>{body}</resources>\n", encoding="utf-8")


def test_plain_locale_codes_match_android_resource_dirs():
    with tempfile.TemporaryDirectory() as tmp:
        res_dir = Path(tmp)
        write_resource(res_dir / "values" / "strings.xml", "required_key")
        write_resource(res_dir / "values-en" / "strings.xml", None)
        write_resource(res_dir / "values-es" / "strings.xml", None)

        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            assert check_locale(res_dir, strict_locales={"en"}) == 1
            assert check_locale(res_dir, strict_locales={"es"}) == 1
            assert check_locale(res_dir, strict_locales={"values-es"}) == 1


def test_unselected_locale_remains_warning_only():
    with tempfile.TemporaryDirectory() as tmp:
        res_dir = Path(tmp)
        write_resource(res_dir / "values" / "strings.xml", "required_key")
        write_resource(res_dir / "values-en" / "strings.xml", "required_key")
        write_resource(res_dir / "values-es" / "strings.xml", None)

        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            assert check_locale(res_dir, strict_locales={"en"}) == 0


if __name__ == "__main__":
    test_plain_locale_codes_match_android_resource_dirs()
    test_unselected_locale_remains_warning_only()
    print("all localization tests passed")
