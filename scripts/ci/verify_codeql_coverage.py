#!/usr/bin/env python3
"""Fail-closed verifier: CodeQL must actually analyze Kotlin.

CoreGuard's primary Android security logic is Kotlin (java-kotlin). A CodeQL
configuration that silently excludes Kotlin (e.g. ``build-mode: none`` or
omitting ``java-kotlin`` from ``languages``) would let a security gate report
"PASS" while skipping the primary language — worse than no gate.

This script is repository-owned and inspectable: it does not depend on GitHub
UI state. It fails closed (exit 1) when Kotlin source exists but the CodeQL
configuration effectively excludes it.

Usage:
    python3 scripts/ci/verify_codeql_coverage.py [--repo-root PATH] [--json]
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[2]

WORKFLOW_GLOB = ".github/workflows/*.yml"
CODEQL_CONFIG_DIR = ".github/codeql"
KOTLIN_GLOBS = ("app/src/**/*.kt", "core/**/*.kt", "cli/**/*.kt")
KOTLIN_EXCLUDE_DIRS = ("/build/", "/.gradle/", "/node_modules/")


@dataclass
class Issue:
    check: str
    severity: str  # "FAIL" | "WARN"
    message: str


@dataclass
class CoverageReport:
    kotlin_present: bool = False
    kotlin_file_count: int = 0
    codeql_workflows: list[str] = field(default_factory=list)
    configured_languages: list[str] = field(default_factory=list)
    java_kotlin_build_mode: str | None = None
    issues: list[Issue] = field(default_factory=list)


def _import_yaml() -> Any:
    """Import PyYAML; fail closed with a clear message if unavailable."""
    try:
        import yaml  # type: ignore[import-untyped]
    except ImportError:  # pragma: no cover - exercised in CI without pyyaml
        return None
    return yaml


def _has_kotlin_source(repo_root: Path) -> tuple[bool, int]:
    count = 0
    for pattern in KOTLIN_GLOBS:
        for path in repo_root.glob(pattern):
            if path.is_file():
                posix = str(path)
                if any(excl in posix for excl in KOTLIN_EXCLUDE_DIRS):
                    continue
                count += 1
    return count > 0, count


def _find_codeql_workflows(repo_root: Path) -> list[Path]:
    workflows_dir = repo_root / ".github" / "workflows"
    if not workflows_dir.is_dir():
        return []
    found: list[Path] = []
    for path in sorted(workflows_dir.glob("*.y*ml")):
        try:
            text = path.read_text(encoding="utf-8")
        except OSError:
            continue
        if (
            "github/codeql-action/init" in text
            or "github/codeql-action/autobuild" in text
        ):
            found.append(path)
    return found


def _config_paths_from_workflows(workflows: list[Path], yaml_module: Any) -> list[Path]:
    """Extract referenced CodeQL config-file paths from workflow init steps."""
    if yaml_module is None:
        return []
    paths: list[Path] = []
    seen: set[str] = set()
    for wf in workflows:
        try:
            doc = yaml_module.safe_load(wf.read_text(encoding="utf-8")) or {}
        except Exception:  # noqa: BLE001 - malformed workflow => skip, surfaced elsewhere
            continue
        if not isinstance(doc, dict):
            continue
        for job in doc.get("jobs", {}).values():
            if not isinstance(job, dict):
                continue
            for step in job.get("steps", []) or []:
                if not isinstance(step, dict):
                    continue
                uses = str(step.get("uses", ""))
                if "github/codeql-action/init" not in uses:
                    continue
                with_block = step.get("with") or {}
                cfg = with_block.get("config-file")
                if isinstance(cfg, str) and cfg not in seen:
                    seen.add(cfg)
                    paths.append(Path(cfg))
    return paths


def _parse_codeql_config(
    repo_root: Path, config_rel: Path, yaml_module: Any
) -> tuple[list[str], str | None]:
    """Return (languages, build_mode_for_java_kotlin) from a CodeQL config file."""
    if yaml_module is None:
        return [], None
    cfg_path = (
        (repo_root / config_rel).resolve()
        if not config_rel.is_absolute()
        else config_rel
    )
    if not cfg_path.is_file():
        return [], None
    try:
        doc = yaml_module.safe_load(cfg_path.read_text(encoding="utf-8")) or {}
    except Exception:  # noqa: BLE001
        return [], None
    if not isinstance(doc, dict):
        return [], None
    languages_raw = doc.get("languages", [])
    languages: list[str] = []
    if isinstance(languages_raw, list):
        languages = [str(lang) for lang in languages_raw]
    build_mode = doc.get("build-mode")
    jk_mode: str | None = None
    if isinstance(build_mode, dict):
        jk_mode = build_mode.get("java-kotlin")
        if jk_mode is not None:
            jk_mode = str(jk_mode).strip().lower()
    return languages, jk_mode


def verify_codeql_coverage(repo_root: Path) -> list[Issue]:
    yaml_module = _import_yaml()
    issues: list[Issue] = []

    kotlin_present, kotlin_count = _has_kotlin_source(repo_root)

    workflows = _find_codeql_workflows(repo_root)

    config_paths = _config_paths_from_workflows(workflows, yaml_module)

    configured_languages: list[str] = []
    jk_mode: str | None = None
    for cfg_rel in config_paths:
        langs, mode = _parse_codeql_config(repo_root, cfg_rel, yaml_module)
        configured_languages.extend(langs)
        if mode is not None:
            jk_mode = mode

    if not kotlin_present:
        # No Kotlin to protect; nothing to enforce. Still note config if present.
        return issues

    if not workflows:
        issues.append(
            Issue(
                check="codeql_workflow_exists",
                severity="FAIL",
                message=(
                    f"Kotlin source present ({kotlin_count} .kt files) but no CodeQL "
                    "workflow references github/codeql-action."
                ),
            )
        )
        return issues

    if "java-kotlin" not in configured_languages:
        issues.append(
            Issue(
                check="java_kotlin_language_configured",
                severity="FAIL",
                message=(
                    "Kotlin source present but 'java-kotlin' is not in the CodeQL "
                    f"config languages (configured: {configured_languages or 'none'}). "
                    "A CodeQL gate that skips the primary language is worse than no gate."
                ),
            )
        )

    if jk_mode is None:
        issues.append(
            Issue(
                check="java_kotlin_build_mode_set",
                severity="FAIL",
                message=(
                    "CodeQL config does not set build-mode for java-kotlin. Without "
                    "an explicit build-mode, Kotlin analysis may be silently dropped."
                ),
            )
        )
    elif jk_mode == "none":
        issues.append(
            Issue(
                check="java_kotlin_build_mode_none",
                severity="FAIL",
                message=(
                    "CodeQL build-mode for java-kotlin is 'none' — Kotlin analysis is "
                    "silently disabled. Use 'autobuild' or 'manual'."
                ),
            )
        )
    elif jk_mode not in ("autobuild", "manual"):
        issues.append(
            Issue(
                check="java_kotlin_build_mode_valid",
                severity="FAIL",
                message=(
                    f"CodeQL build-mode for java-kotlin is '{jk_mode}' — must be "
                    "'autobuild' or 'manual', never 'none'."
                ),
            )
        )

    return issues


def _build_report(repo_root: Path) -> CoverageReport:
    kotlin_present, kotlin_count = _has_kotlin_source(repo_root)
    workflows = _find_codeql_workflows(repo_root)
    yaml_module = _import_yaml()
    config_paths = _config_paths_from_workflows(workflows, yaml_module)
    configured_languages: list[str] = []
    jk_mode: str | None = None
    for cfg_rel in config_paths:
        langs, mode = _parse_codeql_config(repo_root, cfg_rel, yaml_module)
        configured_languages = list(dict.fromkeys(configured_languages + langs))
        if mode is not None:
            jk_mode = mode
    issues = verify_codeql_coverage(repo_root)
    return CoverageReport(
        kotlin_present=kotlin_present,
        kotlin_file_count=kotlin_count,
        codeql_workflows=[
            str(w.relative_to(repo_root)) if w.is_relative_to(repo_root) else str(w)
            for w in workflows
        ],
        configured_languages=configured_languages,
        java_kotlin_build_mode=jk_mode,
        issues=issues,
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", default=str(REPO_ROOT))
    parser.add_argument(
        "--json", action="store_true", help="Emit machine-readable JSON."
    )
    args = parser.parse_args(argv)

    repo_root = Path(args.repo_root).resolve()
    issues = verify_codeql_coverage(repo_root)

    if args.json:
        report = _build_report(repo_root)
        print(json.dumps(asdict(report), indent=2, sort_keys=True))
    else:
        if not issues:
            print(f"PASS: CodeQL Kotlin coverage verified for {repo_root}")
        else:
            print(
                f"FAIL: CodeQL Kotlin coverage invariants violated for {repo_root}",
                file=sys.stderr,
            )
            for issue in issues:
                print(
                    f"[{issue.severity}] {issue.check}: {issue.message}",
                    file=sys.stderr,
                )

    return 1 if any(i.severity == "FAIL" for i in issues) else 0


if __name__ == "__main__":
    raise SystemExit(main())
