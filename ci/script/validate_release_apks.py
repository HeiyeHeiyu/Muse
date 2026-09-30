#!/usr/bin/env python3
"""Verify every release APK with Android's apksigner before publication.

This check validates the APK signing block without reading or printing keystore
credentials. It is intentionally independent from Gradle signing configuration
so a release cannot pass merely because an APK was produced.
"""

from __future__ import annotations

import argparse
import glob
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

EXPECTED_SIGNING_CERT_SHA256 = "e82f4ecde8304b7d78b530336a48e41a42b80c0ebac8af2d2c10448277644ebf"
CERTIFICATE_SHA256_RE = re.compile(
    r"Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]{64})",
)


def resolve_apksigner(explicit: Path | None = None) -> Path:
    """Find apksigner from an explicit path, Android SDK, or PATH."""
    candidates: list[Path] = []
    if explicit is not None:
        candidates.append(explicit)
    for root_name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        root = os.environ.get(root_name)
        if not root:
            continue
        build_tools = Path(root) / "build-tools"
        if build_tools.is_dir():
            for version_dir in sorted(build_tools.iterdir(), reverse=True):
                if version_dir.is_dir():
                    candidates.append(version_dir / "apksigner")
                    candidates.append(version_dir / "apksigner.bat")
    for command in ("apksigner", "apksigner.bat"):
        found = shutil.which(command)
        if found:
            candidates.append(Path(found))
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise FileNotFoundError("apksigner not found; install/configure Android SDK build-tools")


def verify_apk(apk: Path, apksigner: Path) -> None:
    """Verify APK structure and require the established release signing certificate."""
    command = [str(apksigner), "verify", "--verbose", "--print-certs", str(apk)]
    if apksigner.suffix.lower() == ".bat":
        command = ["cmd", "/c", *command]
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0:
        detail = (result.stderr or result.stdout).strip().splitlines()
        safe_detail = detail[-1] if detail else "apksigner returned a non-zero exit code"
        raise RuntimeError(f"APK 签名校验失败: {apk.name}: {safe_detail}")
    output = f"{result.stdout}\n{result.stderr}"
    fingerprints = [value.lower() for value in CERTIFICATE_SHA256_RE.findall(output)]
    if not fingerprints:
        raise RuntimeError(f"APK 未返回签名证书 SHA-256: {apk.name}")
    if any(value != EXPECTED_SIGNING_CERT_SHA256 for value in fingerprints):
        raise RuntimeError(f"APK 签名证书与发布证书不一致: {apk.name}")


def expand_apks(patterns: list[str]) -> list[Path]:
    """Expand file/glob arguments and return unique regular APK files."""
    files: list[Path] = []
    seen: set[Path] = set()
    for pattern in patterns:
        matches = [Path(item) for item in sorted(glob.glob(pattern))]
        if not matches and Path(pattern).is_file():
            matches = [Path(pattern)]
        for path in matches:
            resolved = path.resolve()
            if resolved in seen:
                continue
            seen.add(resolved)
            if path.is_file() and path.suffix.lower() == ".apk":
                files.append(path)
    return files


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", action="append", required=True, help="APK 文件或 glob，可重复传入")
    parser.add_argument("--apksigner", type=Path, help="可选的 apksigner 路径")
    args = parser.parse_args()
    apks = expand_apks(args.apk)
    if not apks:
        print("FATAL: 没有匹配到任何 APK", file=sys.stderr)
        return 1
    try:
        apksigner = resolve_apksigner(args.apksigner)
        for apk in apks:
            verify_apk(apk, apksigner)
            print(f"[签名校验] PASS: {apk.name}")
    except (FileNotFoundError, OSError, RuntimeError) as error:
        print(f"release APK signing preflight failed: {error}", file=sys.stderr)
        return 1
    print(f"release APK signing preflight PASS: {len(apks)} APK(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
