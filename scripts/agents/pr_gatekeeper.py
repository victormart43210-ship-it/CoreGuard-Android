#!/usr/bin/env python3
"""
PR Gatekeeper — Swarm Agent 4
==============================
Aggregates JSON reports produced by Agents 1–3 and writes a single GitHub
Markdown summary to $GITHUB_STEP_SUMMARY (or stdout when run locally).

Usage:
  pr_gatekeeper.py [report1.json report2.json ...]

Exit codes:
  0 — no FAIL findings across all reports
  1 — at least one FAIL finding (CI job fails; PR is blocked)
"""

import json
import os
import sys
from pathlib import Path


SEVERITY_RANK = {"FAIL": 0, "WARN": 1, "INFO": 2}
SEVERITY_ICON = {"FAIL": "❌", "WARN": "⚠️", "INFO": "ℹ️"}


def load_reports(paths):
    reports = []
    for p in paths:
        try:
            data = json.loads(Path(p).read_text())
            reports.append(data)
        except (OSError, json.JSONDecodeError) as exc:
            print(f"[Gatekeeper] WARNING: could not load {p}: {exc}", file=sys.stderr)
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

        # Sort: FAIL first, then WARN, then INFO
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

    # Overall summary
    overall_icon = "✅ PASSED" if total_fail == 0 else "❌ BLOCKED"
    lines.append("---")
    lines.append(f"## Overall Gate Decision: {overall_icon}")
    lines.append(f"**{total_fail} FAIL** &nbsp;|&nbsp; {total_warn} WARN &nbsp;|&nbsp; {total_info} INFO")
    if total_fail > 0:
        lines.append("\n> ⛔ This PR has security findings that must be resolved before merging.")
    else:
        lines.append("\n> ✅ All security swarm agents passed. The PR is cleared for merge review.")

    return "\n".join(lines)


def main():
    report_paths = sys.argv[1:] if len(sys.argv) > 1 else []

    if not report_paths:
        print("[Gatekeeper] No report files supplied — nothing to aggregate.", file=sys.stderr)
        sys.exit(0)

    reports = load_reports(report_paths)
    markdown = build_markdown(reports)

    # Write to GitHub step summary if available
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as fh:
            fh.write(markdown + "\n")
        print("[Gatekeeper] Summary written to $GITHUB_STEP_SUMMARY")
    else:
        print(markdown)

    total_fail = sum(
        sum(1 for f in r.get("findings", []) if f.get("severity") == "FAIL")
        for r in reports
    )
    sys.exit(1 if total_fail > 0 else 0)


if __name__ == "__main__":
    main()
