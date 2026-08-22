#!/usr/bin/env python3
"""Tests for the CodeQL Kotlin coverage fail-closed invariant."""

from __future__ import annotations

import sys
import textwrap
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from scripts.ci.verify_codeql_coverage import (  # noqa: E402
    Issue,
    verify_codeql_coverage,
)

CODEQL_WORKFLOW = textwrap.dedent(
    """\
    name: CodeQL
    on:
      push:
        branches: ["main"]
      pull_request:
        branches: ["main"]
    jobs:
      analyze:
        runs-on: ubuntu-latest
        steps:
          - uses: actions/checkout@v4
          - uses: github/codeql-action/init@v3
            with:
              config-file: .github/codeql/codeql-config.yml
              languages: ${{ matrix.language }}
          - uses: github/codeql-action/analyze@v3
    """
)

NO_CODEQL_WORKFLOW = textwrap.dedent(
    """\
    name: Other
    on: [push]
    jobs:
      build:
        runs-on: ubuntu-latest
        steps:
          - run: echo hi
    """
)


def _write_repo(
    root: Path,
    *,
    codeql_workflow: str | None,
    config_yaml: str | None,
    kotlin: bool,
) -> Path:
    workflows = root / ".github" / "workflows"
    workflows.mkdir(parents=True, exist_ok=True)
    if codeql_workflow is not None:
        (workflows / "codeql.yml").write_text(codeql_workflow, encoding="utf-8")
    if config_yaml is not None:
        cfg_dir = root / ".github" / "codeql"
        cfg_dir.mkdir(parents=True, exist_ok=True)
        (cfg_dir / "codeql-config.yml").write_text(config_yaml, encoding="utf-8")
    if kotlin:
        src = root / "app" / "src" / "main" / "java" / "com" / "coldboar"
        src.mkdir(parents=True, exist_ok=True)
        (src / "GuardVpnService.kt").write_text(
            "class GuardVpnService\n", encoding="utf-8"
        )
    return root


def _issues(root: Path) -> list[Issue]:
    return verify_codeql_coverage(root)


def test_kotlin_present_autobuild_passes() -> None:
    root = _write_repo(
        Path("/tmp") / "cg_codeql_autobuild",
        codeql_workflow=CODEQL_WORKFLOW,
        config_yaml=textwrap.dedent(
            """\
            name: CoreGuard CodeQL Config
            languages:
              - java-kotlin
              - cpp
            build-mode:
              java-kotlin: autobuild
              cpp: autobuild
            """
        ),
        kotlin=True,
    )
    assert _issues(root) == []


def test_kotlin_present_manual_passes() -> None:
    root = _write_repo(
        Path("/tmp") / "cg_codeql_manual",
        codeql_workflow=CODEQL_WORKFLOW,
        config_yaml=textwrap.dedent(
            """\
            languages:
              - java-kotlin
            build-mode:
              java-kotlin: manual
            """
        ),
        kotlin=True,
    )
    assert _issues(root) == []


def test_kotlin_present_no_codeql_workflow_fails() -> None:
    root = _write_repo(
        Path("/tmp") / "cg_codeql_nowf",
        codeql_workflow=None,
        config_yaml=None,
        kotlin=True,
    )
    issues = _issues(root)
    assert any("no codeql workflow" in i.message.lower() for i in issues)


def test_kotlin_present_java_kotlin_absent_fails() -> None:
    root = _write_repo(
        Path("/tmp") / "cg_codeql_nokt",
        codeql_workflow=CODEQL_WORKFLOW,
        config_yaml=textwrap.dedent(
            """\
            languages:
              - cpp
            build-mode:
              cpp: autobuild
            """
        ),
        kotlin=True,
    )
    issues = _issues(root)
    assert any("java-kotlin" in i.message.lower() for i in issues)
    assert any(i.severity == "FAIL" for i in issues)


def test_kotlin_present_build_mode_none_fails() -> None:
    root = _write_repo(
        Path("/tmp") / "cg_codeql_none",
        codeql_workflow=CODEQL_WORKFLOW,
        config_yaml=textwrap.dedent(
            """\
            languages:
              - java-kotlin
            build-mode:
              java-kotlin: none
            """
        ),
        kotlin=True,
    )
    issues = _issues(root)
    assert any("none" in i.message.lower() for i in issues)
    assert any(i.severity == "FAIL" for i in issues)


def test_kotlin_present_build_mode_missing_fails() -> None:
    root = _write_repo(
        Path("/tmp") / "cg_codeql_nobm",
        codeql_workflow=CODEQL_WORKFLOW,
        config_yaml=textwrap.dedent(
            """\
            languages:
              - java-kotlin
            """
        ),
        kotlin=True,
    )
    issues = _issues(root)
    assert any(i.severity == "FAIL" for i in issues)


def test_no_kotlin_no_codeql_passes() -> None:
    root = _write_repo(
        Path("/tmp") / "cg_codeql_nokotlin",
        codeql_workflow=None,
        config_yaml=None,
        kotlin=False,
    )
    assert _issues(root) == []


def test_non_codeql_workflow_does_not_count() -> None:
    root = _write_repo(
        Path("/tmp") / "cg_codeql_fake",
        codeql_workflow=NO_CODEQL_WORKFLOW,
        config_yaml=None,
        kotlin=True,
    )
    issues = _issues(root)
    assert any("no codeql workflow" in i.message.lower() for i in issues)


def test_integration_real_repo_is_compliant() -> None:
    """The actual CoreGuard repo (with the new CodeQL config) must pass."""
    issues = verify_codeql_coverage(REPO_ROOT)
    fails = [i for i in issues if i.severity == "FAIL"]
    assert fails == [], [i.message for i in fails]
