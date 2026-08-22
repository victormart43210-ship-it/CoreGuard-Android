#!/usr/bin/env python3
"""Tests for the security-workflow meta-verifier (Task 4)."""

from __future__ import annotations

import json
import sys
import textwrap
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from scripts.ci.verify_security_workflows import (  # noqa: E402
    WorkflowIssue,
    verify_security_workflows,
)

GATES_JSON = {
    "schema_version": "1.0.0",
    "diagnostic_commands": [
        "ls",
        "cat",
        "grep",
        "find",
        "stat",
        "uname",
        "id",
        "groups",
        "echo",
        "printf",
        "yes",
        "true",
        "test",
        "wc",
        "head",
        "tail",
        "sed",
        "awk",
        "cut",
        "tr",
        "basename",
        "dirname",
        "pwd",
        "env",
        "which",
        "command",
        "file",
        "du",
        "df",
        "sleep",
    ],
    "gates": {
        "android-build": {
            "classification": "REQUIRED",
            "workflow": "android.yml",
            "job": "build",
        },
        "security-swarm": {
            "classification": "REQUIRED",
            "workflow": "security-swarm.yml",
            "job": "gatekeeper",
        },
        "mobsf": {
            "classification": "ADVISORY",
            "workflow": "mobsf.yml",
            "job": "mobile-security",
        },
    },
    "allowlist": [
        {
            "workflow": "security-swarm.yml",
            "pattern": "scripts/security_gate.py",
            "reason": "gate decision is enforced by a later 'Enforce gate decision' step",
        }
    ],
}


def _write_repo(
    root: Path, workflows: dict[str, str], gates: dict | None = None
) -> Path:
    wfdir = root / ".github" / "workflows"
    wfdir.mkdir(parents=True, exist_ok=True)
    for name, body in workflows.items():
        (wfdir / name).write_text(body, encoding="utf-8")
    gpath = root / ".github" / "security-gates.json"
    gpath.parent.mkdir(parents=True, exist_ok=True)
    gpath.write_text(
        json.dumps(gates if gates is not None else GATES_JSON), encoding="utf-8"
    )
    return root


REQUIRED_GATE_CONTINUE_ERROR = textwrap.dedent(
    """\
    name: Swarm
    on: [pull_request]
    jobs:
      gatekeeper:
        runs-on: ubuntu-latest
        steps:
          - uses: actions/checkout@v4
          - name: Gate decision
            continue-on-error: true
            run: python scripts/security_gate.py
          - run: echo done
    """
)

REQUIRED_GATE_CONTINUE_ERROR_NOT_ALLOWLISTED = textwrap.dedent(
    """\
    name: Android
    on: [pull_request]
    jobs:
      build:
        runs-on: ubuntu-latest
        steps:
          - uses: actions/checkout@v4
          - name: Run scanner
            continue-on-error: true
            run: python scripts/ci/verify_codeql_coverage.py
    """
)

OR_TRUE_SUPPRESSING_SCANNER = textwrap.dedent(
    """\
    name: Android
    on: [pull_request]
    jobs:
      build:
        runs-on: ubuntu-latest
        steps:
          - uses: actions/checkout@v4
          - run: |
              python scripts/ci/verify_codeql_coverage.py || true
              echo done
    """
)

OR_TRUE_DIAGNOSTIC_OK = textwrap.dedent(
    """\
    name: Android
    on: [pull_request]
    jobs:
      build:
        runs-on: ubuntu-latest
        steps:
          - uses: actions/checkout@v4
          - run: |
              ls /dev/kvm 2>&1 || true
              stat /dev/kvm 2>&1 || true
    """
)

ADVISORY_CONTINUE_ERROR_OK = textwrap.dedent(
    """\
    name: MobSF
    on: [pull_request]
    jobs:
      mobile-security:
        runs-on: ubuntu-latest
        steps:
          - uses: actions/checkout@v4
          - name: mobsfscan
            continue-on-error: true
            uses: MobSF/mobsfscan@v1
            with:
              args: '. --sarif'
    """
)

NON_SECURITY_CONTINUE_ERROR_OK = textwrap.dedent(
    """\
    name: Android
    on: [pull_request]
    jobs:
      build:
        runs-on: ubuntu-latest
        steps:
          - uses: actions/checkout@v4
          - name: Upload evidence
            if: always()
            continue-on-error: true
            uses: actions/upload-artifact@v4
            with:
              path: reports
    """
)


def _issues(root: Path) -> list[WorkflowIssue]:
    return verify_security_workflows(root)


def test_required_gate_continue_on_error_not_allowlisted_fails() -> None:
    root = _write_repo(
        Path("/tmp") / "cg_wf_ce",
        {"android.yml": REQUIRED_GATE_CONTINUE_ERROR_NOT_ALLOWLISTED},
    )
    issues = [i for i in _issues(root) if i.severity == "FAIL"]
    assert any("continue-on-error" in i.message.lower() for i in issues)


def test_required_gate_continue_on_error_allowlisted_passes() -> None:
    root = _write_repo(
        Path("/tmp") / "cg_wf_ce_ok",
        {"security-swarm.yml": REQUIRED_GATE_CONTINUE_ERROR},
    )
    assert _issues(root) == []


def test_or_true_suppressing_scanner_fails() -> None:
    root = _write_repo(
        Path("/tmp") / "cg_wf_ortrue", {"android.yml": OR_TRUE_SUPPRESSING_SCANNER}
    )
    issues = [i for i in _issues(root) if i.severity == "FAIL"]
    assert any(
        "|| true" in i.message.lower() or "suppression" in i.message.lower()
        for i in issues
    )


def test_or_true_diagnostic_passes() -> None:
    root = _write_repo(
        Path("/tmp") / "cg_wf_diag", {"android.yml": OR_TRUE_DIAGNOSTIC_OK}
    )
    assert _issues(root) == []


def test_advisory_continue_on_error_passes() -> None:
    root = _write_repo(
        Path("/tmp") / "cg_wf_adv", {"mobsf.yml": ADVISORY_CONTINUE_ERROR_OK}
    )
    assert _issues(root) == []


def test_non_security_continue_on_error_passes() -> None:
    root = _write_repo(
        Path("/tmp") / "cg_wf_nonsec", {"android.yml": NON_SECURITY_CONTINUE_ERROR_OK}
    )
    assert _issues(root) == []


def test_missing_gates_file_fails() -> None:
    root = Path("/tmp") / "cg_wf_nogates"
    import shutil

    shutil.rmtree(root, ignore_errors=True)
    wfdir = root / ".github" / "workflows"
    wfdir.mkdir(parents=True, exist_ok=True)
    (wfdir / "android.yml").write_text(
        REQUIRED_GATE_CONTINUE_ERROR_NOT_ALLOWLISTED, encoding="utf-8"
    )
    issues = _issues(root)
    assert any(i.severity == "FAIL" for i in issues)


def test_integration_real_repo_has_no_unallowlisted_suppression() -> None:
    """The real CoreGuard repo must pass the meta-verifier."""
    issues = [i for i in _issues(REPO_ROOT) if i.severity == "FAIL"]
    assert issues == [], [i.message for i in issues]
