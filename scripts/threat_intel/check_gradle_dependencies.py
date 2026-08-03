#!/usr/bin/env python3
"""Fail when Gradle dependency coordinates are duplicated in gradle/android-app.gradle."""

from __future__ import annotations

import re
import sys
from collections import Counter
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
APP_BUILD = REPO_ROOT / "gradle" / "android-app.gradle"


DEPENDENCY_RE = re.compile(r'^\s*(?:implementation|kapt|testImplementation|androidTestImplementation|debugImplementation)\s+"([^"]+)"')


def main() -> int:
    lines = APP_BUILD.read_text(encoding="utf-8").splitlines()
    coords = [m.group(1) for line in lines if (m := DEPENDENCY_RE.match(line))]
    counts = Counter(coords)
    duplicates = sorted([coord for coord, count in counts.items() if count > 1])
    if duplicates:
        print("[check_gradle_dependencies] duplicate coordinates found:", file=sys.stderr)
        for coord in duplicates:
            print(f"  - {coord}", file=sys.stderr)
        return 1
    print("[check_gradle_dependencies] OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
