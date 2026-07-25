#!/usr/bin/env python3
"""
Security Gate — CoreGuard Security Swarm
=========================================
Aggregates JSON reports produced by the MASVS, Vulnerability Scan, and RASP
audit agents and writes a single GitHub Markdown summary to stdout.

The script auto-discovers report files from /tmp/reports/ so it can be called
without arguments in CI:

    python scripts/security_gate.py > gate-summary.md

Exit codes:
    0 — no FAIL findings across all reports
    1 — at least one FAIL finding (CI job fails; PR is blocked)
"""

import json
import sys
from pathlib import Path

REPORTS_DIR = Path("/tmp/reports")
REPORT_GLOB = "*.json"

SEVERITY_RANK = {"FAIL": 0, "WARN": 1, "INFO": 2}
SEVERITY_ICON = {"FAIL": "❌", "WARN": "⚠️", "INFO": "ℹ️"}


def load_reports(report_dir: Path):
    reports = []
    paths = sorted(report_dir.glob(REPORT_GLOB)) if report_dir.is_dir() else []
    for p in paths:
        try:
            data = json.loads(p.read_text(encoding="utf-8"))
            reports.append(data)
        except (OSError, json.JSONDecodeError) as exc:
            print(f"[security_gate] WARNING: could not load {p}: {exc}", file=sys.stderr)
    return reports


def build_markdown(reports):
    lines = ["# 🛡️ CoreGuard Security Swarm — PR Gate Report\n"]

    total_fail = 0
    total_warn = 0
    total_info = 0

    for report in reports:
        agent = report.get("agent", "Unknown Agent")
        findings = report.get("findings", [])

        fail = sum(1 for f in findings if f.get("severity") == "FAIL")
        warn = sum(1 for f in findings if f.get("severity") == "WARN")
        info = sum(1 for f in findings if f.get("severity") == "INFO")
        total_fail += fail
        total_warn += warn
        total_info += info

        status_icon = "✅" if fail == 0 else "❌"
        lines.append(f"## {status_icon} {agent}")
        lines.append(f"> {fail} FAIL &nbsp;|&nbsp; {warn} WARN &nbsp;|&nbsp; {info} INFO\n")

        if not findings:
            lines.append("_No findings._\n")
            continue

        sorted_findings = sorted(
            findings, key=lambda f: SEVERITY_RANK.get(f.get("severity", "INFO"), 2)
        )

        lines.append("| Severity | Rule | Location | Message |")
        lines.append("|----------|------|----------|---------|")
        for f in sorted_findings:
            sev = f.get("severity", "INFO")
            icon = SEVERITY_ICON.get(sev, "")
            rule = f.get("rule", "")
            loc_file = f.get("file", "")
            loc_line = f.get("line")
            loc = f"{loc_file}:{loc_line}" if loc_line else loc_file
            msg = f.get("message", "").replace("|", "\\|")
            lines.append(f"| {icon} {sev} | `{rule}` | `{loc}` | {msg} |")
        lines.append("")

    overall_icon = "✅ PASSED" if total_fail == 0 else "❌ BLOCKED"
    lines.append("---")
    lines.append(f"## Overall Gate Decision: {overall_icon}")
    lines.append(
        f"**{total_fail} FAIL** &nbsp;|&nbsp; {total_warn} WARN &nbsp;|&nbsp; {total_info} INFO"
    )
    if total_fail > 0:
        lines.append(
            "\n> ⛔ This PR has security findings that must be resolved before merging."
        )
    else:
        lines.append(
            "\n> ✅ All security swarm agents passed. The PR is cleared for merge review."
        )

    return "\n".join(lines)


def main():
    reports = load_reports(REPORTS_DIR)

    if not reports:
        print(
            "[security_gate] No report files found in"
            f" {REPORTS_DIR} — nothing to aggregate.",
            file=sys.stderr,
        )
        # Emit a minimal summary so the gate-summary.md is never empty.
        print("# 🛡️ CoreGuard Security Swarm — PR Gate Report\n")
        print("_No agent reports were available for this run._")
        sys.exit(0)

    markdown = build_markdown(reports)
    print(markdown)

    total_fail = sum(
        sum(1 for f in r.get("findings", []) if f.get("severity") == "FAIL")
        for r in reports
    )
    sys.exit(1 if total_fail > 0 else 0)


if __name__ == "__main__":
    main()
