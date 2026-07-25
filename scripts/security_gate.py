#!/usr/bin/env python3
"""
Security Swarm gate
===================
Aggregates JSON reports from the MASVS / vuln / RASP agents and prints a
single Markdown summary to stdout.

Intended CI usage (keeps a durable file for the PR-comment step):

  python scripts/security_gate.py > gate-summary.md
  cat gate-summary.md >> "$GITHUB_STEP_SUMMARY"

Exit codes:
  0 — no FAIL findings across all reports
  1 — at least one FAIL finding
"""

from __future__ import annotations

import sys
from pathlib import Path

AGENTS_DIR = Path(__file__).resolve().parent / "agents"
sys.path.insert(0, str(AGENTS_DIR))

from pr_gatekeeper import build_markdown, load_reports  # noqa: E402

DEFAULT_REPORTS = (
    Path("/tmp/reports/masvs_report.json"),
    Path("/tmp/reports/vuln_report.json"),
    Path("/tmp/reports/rasp_report.json"),
)


def resolve_report_paths(argv: list[str]) -> list[str]:
    if argv:
        return argv
    return [str(path) for path in DEFAULT_REPORTS if path.exists()]


def main() -> None:
    report_paths = resolve_report_paths(sys.argv[1:])

    if not report_paths:
        print(
            "## :shield: Security Swarm Gate\n\n"
            "_No agent reports found to aggregate._"
        )
        sys.exit(0)

    reports = load_reports(report_paths)
    if not reports:
        print(
            "## :shield: Security Swarm Gate\n\n"
            "_Agent report files were present but could not be parsed._"
        )
        sys.exit(1)

    print(build_markdown(reports))

    total_fail = sum(
        sum(1 for finding in report.get("findings", []) if finding.get("severity") == "FAIL")
        for report in reports
    )
    sys.exit(1 if total_fail > 0 else 0)


if __name__ == "__main__":
    main()
