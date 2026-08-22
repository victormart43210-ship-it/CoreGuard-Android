#!/usr/bin/env python3
"""Security-workflow meta-verifier (CoreGuard V3.1A Task 4).

Parses GitHub Actions security workflows and fails closed when a REQUIRED
security gate's critical step is silently suppressed (``continue-on-error:
true``) or a security scanner is silenced with ``|| true``.

Non-naive rules:
  * YAML is parsed with PyYAML (no regex on workflow files).
  * Only **security-critical** steps are flagged: ``uses`` of a known scanner
    action, or ``run`` invoking a known security-gate script. A plain
    ``continue-on-error`` on checkout/setup/upload-artifact is allowed.
  * Advisory and externally-unavailable gates may suppress by classification.
  * Intentional cases are allowlisted with a human-readable reason.

Exit 0 only when there are no FAIL issues; exit 1 otherwise.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError:  # pragma: no cover
    yaml = None  # type: ignore[assignment]

# Scanner actions referenced under `uses:`.
SECURITY_ACTION_PATTERNS = (
    "github/codeql-action",
    "MobSF/mobsfscan",
    "actions/dependency-review-action",
    "snyk/actions",
    "aquasecurity/trivy-action",
    "anchore/scan-action",
    "github/super-linter",
    "sigstore",
)

# Security-gate scripts invoked under `run:`.
SECURITY_RUN_PATTERNS = (
    "security_gate.py",
    "verify_codeql_coverage",
    "verify_security_workflows",
    "verify_play_policy",
    "verify_data_safety",
    "rasp_audit",
    "masvs_agent",
    "vuln_scan",
    "gh attestation verify",
    "dependency-review",
    "mobsf_truthfulness",
)

SUPPRESSION_RE = re.compile(r"\|\|\s*(?:true|:)\b", re.IGNORECASE)


@dataclass
class WorkflowIssue:
    workflow: str
    job: str
    step: str
    severity: str
    message: str


def _truthy(val: Any) -> bool:
    if isinstance(val, bool):
        return val
    if isinstance(val, str):
        return val.strip().lower() in {"true", "yes", "on"}
    return False


def _is_security_critical_step(step: dict) -> bool:
    uses = str(step.get("uses", "") or "")
    if any(p in uses for p in SECURITY_ACTION_PATTERNS):
        return True
    run = str(step.get("run", "") or "")
    if any(p in run for p in SECURITY_RUN_PATTERNS):
        return True
    return False


def _is_diagnostic_command(command: str, diagnostics: set[str]) -> bool:
    # Strip env-prefix chains (FOO=bar BAZ=qux cmd ...) until a real command.
    tokens = command.strip().split()
    i = 0
    while i < len(tokens) - 1 and re.match(r"^[A-Z_][A-Z0-9_]*=", tokens[i]):
        i += 1
    return bool(tokens) and tokens[i] in diagnostics


def _allowlisted(workflow: str, step: dict, allowlist: list[dict]) -> bool:
    haystack = " ".join(
        str(step.get(k, "") or "") for k in ("uses", "name", "run", "id")
    )
    return any(
        entry.get("workflow") == workflow and entry.get("pattern", "") in haystack
        for entry in allowlist
    )


def _load_gates(gates_path: Path) -> dict:
    with gates_path.open(encoding="utf-8") as fh:
        data = json.load(fh)
    return data


def verify_security_workflows(
    repo_root: Path, gates_path: Path | None = None
) -> list[WorkflowIssue]:
    issues: list[WorkflowIssue] = []
    root = Path(repo_root)
    gpath = gates_path or root / ".github" / "security-gates.json"

    if yaml is None:
        issues.append(
            WorkflowIssue(
                workflow="-",
                job="-",
                step="-",
                severity="FAIL",
                message="PyYAML is not installed; cannot parse workflows.",
            )
        )
        return issues
    if not gpath.exists():
        issues.append(
            WorkflowIssue(
                workflow="-",
                job="-",
                step="-",
                severity="FAIL",
                message="security-gates.json missing; gate authority is unverifiable.",
            )
        )
        return issues

    gates = _load_gates(gpath)
    diagnostics = set(gates.get("diagnostic_commands", []))
    allowlist = gates.get("allowlist", [])

    # workflow filename -> classification
    wf_to_class: dict[str, str] = {}
    for entry in gates.get("gates", {}).values():
        wf = entry.get("workflow")
        cls = entry.get("classification")
        if wf and cls:
            wf_to_class.setdefault(wf, cls)

    wfdir = root / ".github" / "workflows"
    if not wfdir.exists():
        return issues

    for wfpath in sorted(wfdir.glob("*.yml")):
        wf_name = wfpath.name
        classification = wf_to_class.get(wf_name, "ADVISORY")
        try:
            doc = yaml.safe_load(wfpath.read_text(encoding="utf-8")) or {}
        except yaml.YAMLError as exc:
            issues.append(
                WorkflowIssue(wf_name, "-", "-", "FAIL", f"YAML parse error: {exc}")
            )
            continue
        if not isinstance(doc, dict):
            continue
        jobs = doc.get("jobs", {}) or {}
        for job_name, job in jobs.items():
            if not isinstance(job, dict):
                continue
            for step in job.get("steps", []) or []:
                if not isinstance(step, dict):
                    continue
                step_label = str(
                    step.get("name") or step.get("id") or step.get("uses") or "step"
                )
                # continue-on-error on a REQUIRED security-critical step
                if "continue-on-error" in step and _truthy(step["continue-on-error"]):
                    if (
                        classification == "REQUIRED"
                        and _is_security_critical_step(step)
                        and not _allowlisted(wf_name, step, allowlist)
                    ):
                        issues.append(
                            WorkflowIssue(
                                wf_name,
                                job_name,
                                step_label,
                                "FAIL",
                                message=(
                                    "REQUIRED security gate step has continue-on-error: true "
                                    "without an allowlist entry — failures are silently swallowed. "
                                    f"(step: {step_label})"
                                ),
                            )
                        )
                # || true suppression inside run blocks
                run = str(step.get("run", "") or "")
                if run:
                    for line in run.splitlines():
                        if SUPPRESSION_RE.search(line):
                            pre = SUPPRESSION_RE.split(line, 1)[0]
                            if _is_diagnostic_command(pre, diagnostics):
                                continue
                            if (
                                classification == "REQUIRED"
                                and any(p in pre for p in SECURITY_RUN_PATTERNS)
                                and not _allowlisted(wf_name, step, allowlist)
                            ):
                                issues.append(
                                    WorkflowIssue(
                                        wf_name,
                                        job_name,
                                        step_label,
                                        "FAIL",
                                        message=(
                                            "Security-gate command suppressed with '|| true' in a "
                                            f"REQUIRED gate — failures are swallowed. (line: {line.strip()})"
                                        ),
                                    )
                                )
    return issues


def _format_issues(issues: list[WorkflowIssue]) -> str:
    if not issues:
        return "SECURITY_WORKFLOWS_OK\n0 issues"
    lines = [
        f"{i.severity}\t{i.workflow}/{i.job}/{i.step}\t{i.message}" for i in issues
    ]
    return "SECURITY_WORKFLOWS_ISSUES\n" + "\n".join(lines)


def main() -> int:
    ap = argparse.ArgumentParser(
        description="Verify security workflows are not silently suppressed."
    )
    ap.add_argument("--repo-root", default=str(Path.cwd()))
    ap.add_argument("--gates-path", default=None)
    ap.add_argument("--json", action="store_true", help="Emit JSON instead of text.")
    args = ap.parse_args()

    issues = verify_security_workflows(
        Path(args.repo_root), Path(args.gates_path) if args.gates_path else None
    )
    if args.json:
        print(json.dumps([i.__dict__ for i in issues], indent=2))
    else:
        print(_format_issues(issues))
    return 1 if any(i.severity == "FAIL" for i in issues) else 0


if __name__ == "__main__":
    sys.exit(main())
