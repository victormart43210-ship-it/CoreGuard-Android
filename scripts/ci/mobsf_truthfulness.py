#!/usr/bin/env python3
"""MobSF result truth interpreter (CoreGuard V3.1A Task 5).

The MobSF job previously printed ``PASS — SARIF written to results.sarif``
whenever the file merely existed. That conflated two independent facts:

  1. did the scanner actually run to completion (SCAN_EXECUTION), and
  2. what did it find (FINDINGS_STATUS).

A crashed scanner, a truncated upload, a malformed SARIF, or a SARIF full of
blocking findings could all be summarised as PASS. This module models the two
dimensions separately and fails closed: findings are only ever evaluated after a
SARIF has been successfully parsed, so "we could not tell" can never render as
"nothing was found".

Exit codes:
  0  gate satisfied for the declared classification
  1  gate violated (blocking findings, or a required gate that did not run)
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Any


class ScanExecution(str, Enum):
    """Did the scanner produce a trustworthy result at all?"""

    SUCCESS = "SUCCESS"
    FAILED = "FAILED"
    UNAVAILABLE = "UNAVAILABLE"
    NOT_RUN = "NOT_RUN"
    INVALID_OUTPUT = "INVALID_OUTPUT"


class FindingsStatus(str, Enum):
    """What did the scanner find? Only meaningful when execution SUCCEEDED."""

    PASS = "PASS"
    BLOCKING_FINDINGS = "BLOCKING_FINDINGS"
    NON_BLOCKING_FINDINGS = "NON_BLOCKING_FINDINGS"
    UNKNOWN = "UNKNOWN"


# Gate classifications, mirroring .github/security-gates.json.
CLASSIFICATION_REQUIRED = "REQUIRED"
CLASSIFICATION_ADVISORY = "ADVISORY"
CLASSIFICATION_EXTERNAL_UNAVAILABLE_ALLOWED = "EXTERNAL_UNAVAILABLE_ALLOWED"

# SARIF `level` values treated as blocking.
DEFAULT_BLOCKING_LEVELS = frozenset({"error"})

# SARIF `level` values that are real, non-blocking observations.
KNOWN_NON_BLOCKING_LEVELS = frozenset({"warning", "note", "none"})

# Scanner step outcomes, as reported by GitHub Actions `steps.<id>.outcome`.
OUTCOME_SUCCESS = "success"
OUTCOME_FAILURE = "failure"
OUTCOME_CANCELLED = "cancelled"
OUTCOME_SKIPPED = "skipped"
OUTCOME_TIMED_OUT = "timed_out"
OUTCOME_UNKNOWN = "unknown"


@dataclass
class MobsfTruth:
    """The separated truth of one MobSF run."""

    execution: ScanExecution
    findings: FindingsStatus
    reason: str
    blocking_count: int = 0
    non_blocking_count: int = 0
    unknown_severity_count: int = 0
    malformed_result_count: int = 0
    notes: list[str] = field(default_factory=list)

    def as_dict(self) -> dict[str, Any]:
        return {
            "scan_execution": self.execution.value,
            "findings_status": self.findings.value,
            "reason": self.reason,
            "blocking_count": self.blocking_count,
            "non_blocking_count": self.non_blocking_count,
            "unknown_severity_count": self.unknown_severity_count,
            "malformed_result_count": self.malformed_result_count,
            "notes": list(self.notes),
        }


def _classify_results(
    runs: list[Any], blocking_levels: frozenset[str]
) -> tuple[int, int, int, int]:
    """Counts (blocking, non_blocking, unknown_severity, malformed) results."""
    blocking = non_blocking = unknown = malformed = 0

    for run in runs:
        if not isinstance(run, dict):
            malformed += 1
            continue
        results = run.get("results", [])
        if results is None:
            results = []
        if not isinstance(results, list):
            malformed += 1
            continue

        for result in results:
            if not isinstance(result, dict):
                malformed += 1
                continue

            raw_level = result.get("level")
            if raw_level is None:
                # SARIF allows level to be omitted, but then severity is only
                # recoverable from the rule metadata, which mobsfscan does not
                # reliably emit. Treat as unknown rather than harmless.
                unknown += 1
                continue
            if not isinstance(raw_level, str):
                malformed += 1
                continue

            level = raw_level.strip().lower()
            if level in blocking_levels:
                blocking += 1
            elif level in KNOWN_NON_BLOCKING_LEVELS:
                non_blocking += 1
            else:
                unknown += 1

    return blocking, non_blocking, unknown, malformed


def interpret(
    sarif_path: Path | None,
    scanner_outcome: str,
    classification: str = CLASSIFICATION_ADVISORY,
    blocking_levels: frozenset[str] = DEFAULT_BLOCKING_LEVELS,
) -> MobsfTruth:
    """Derives the separated execution/findings truth for a MobSF run.

    ``scanner_outcome`` is the raw GitHub Actions step outcome. It is authoritative
    for execution failure: a scanner that exited non-zero cannot be reported as a
    clean scan even when it left a well-formed, empty SARIF behind.
    """
    outcome = (scanner_outcome or OUTCOME_UNKNOWN).strip().lower()

    # 1. The scanner never ran.
    if outcome == OUTCOME_SKIPPED:
        return MobsfTruth(
            execution=ScanExecution.NOT_RUN,
            findings=FindingsStatus.UNKNOWN,
            reason="Scanner step was skipped; no scan was attempted.",
        )

    # 2. The scanner ran but did not complete. Its output cannot be trusted even
    #    if a SARIF file is present, so findings stay UNKNOWN.
    if outcome in {OUTCOME_FAILURE, OUTCOME_CANCELLED, OUTCOME_TIMED_OUT}:
        execution = (
            ScanExecution.UNAVAILABLE
            if outcome in {OUTCOME_CANCELLED, OUTCOME_TIMED_OUT}
            else ScanExecution.FAILED
        )
        note = (
            "A SARIF file is present but is not trusted: the scanner did not "
            "exit successfully."
            if sarif_path is not None and sarif_path.exists()
            else "No trustworthy SARIF output."
        )
        return MobsfTruth(
            execution=execution,
            findings=FindingsStatus.UNKNOWN,
            reason=f"Scanner outcome was '{outcome}'; results are not trustworthy.",
            notes=[note],
        )

    # 3. The scanner claims success. It must have produced parseable output.
    if sarif_path is None or not sarif_path.exists():
        if classification == CLASSIFICATION_EXTERNAL_UNAVAILABLE_ALLOWED:
            return MobsfTruth(
                execution=ScanExecution.UNAVAILABLE,
                findings=FindingsStatus.UNKNOWN,
                reason=(
                    "Expected SARIF is absent and this gate is classified "
                    "EXTERNAL_UNAVAILABLE_ALLOWED."
                ),
            )
        return MobsfTruth(
            execution=ScanExecution.INVALID_OUTPUT,
            findings=FindingsStatus.UNKNOWN,
            reason=(
                "Scanner reported success but produced no SARIF; output is missing, "
                "so nothing was verified."
            ),
        )

    try:
        raw = sarif_path.read_text(encoding="utf-8")
    except OSError as exc:
        return MobsfTruth(
            execution=ScanExecution.INVALID_OUTPUT,
            findings=FindingsStatus.UNKNOWN,
            reason=f"SARIF could not be read: {type(exc).__name__}.",
        )

    if not raw.strip():
        return MobsfTruth(
            execution=ScanExecution.INVALID_OUTPUT,
            findings=FindingsStatus.UNKNOWN,
            reason="SARIF file is empty; no scan result was recorded.",
        )

    try:
        doc = json.loads(raw)
    except json.JSONDecodeError as exc:
        return MobsfTruth(
            execution=ScanExecution.INVALID_OUTPUT,
            findings=FindingsStatus.UNKNOWN,
            reason=f"SARIF is not valid JSON (line {exc.lineno}); nothing was verified.",
        )

    if not isinstance(doc, dict):
        return MobsfTruth(
            execution=ScanExecution.INVALID_OUTPUT,
            findings=FindingsStatus.UNKNOWN,
            reason="SARIF root is not a JSON object; shape is unrecognised.",
        )

    runs = doc.get("runs")
    if runs is None or not isinstance(runs, list):
        return MobsfTruth(
            execution=ScanExecution.INVALID_OUTPUT,
            findings=FindingsStatus.UNKNOWN,
            reason="SARIF has no 'runs' array; shape is unrecognised.",
        )

    # 4. Parsed successfully — only now may findings be evaluated.
    blocking, non_blocking, unknown, malformed = _classify_results(
        runs, blocking_levels
    )

    notes: list[str] = []
    if malformed:
        notes.append(f"{malformed} result object(s) were malformed.")
    if unknown:
        notes.append(f"{unknown} result(s) had missing or unrecognised severity.")

    if blocking:
        findings = FindingsStatus.BLOCKING_FINDINGS
        reason = f"{blocking} blocking finding(s) reported by MobSF."
    elif malformed or unknown:
        # Cannot claim a clean result while part of the report is unreadable.
        findings = FindingsStatus.UNKNOWN
        reason = (
            "Scan parsed, but some results were unreadable or had unrecognised "
            "severity; a clean result cannot be claimed."
        )
    elif non_blocking:
        findings = FindingsStatus.NON_BLOCKING_FINDINGS
        reason = f"{non_blocking} non-blocking finding(s) reported by MobSF."
    else:
        findings = FindingsStatus.PASS
        reason = "Scan completed and parsed with zero findings."

    return MobsfTruth(
        execution=ScanExecution.SUCCESS,
        findings=findings,
        reason=reason,
        blocking_count=blocking,
        non_blocking_count=non_blocking,
        unknown_severity_count=unknown,
        malformed_result_count=malformed,
        notes=notes,
    )


def gate_exit_code(truth: MobsfTruth, classification: str) -> int:
    """Maps separated truth onto a gate decision.

    Blocking findings always fail, regardless of classification: a real finding
    is a real finding. Execution problems fail REQUIRED gates and are tolerated
    (but never relabelled PASS) for ADVISORY / externally-unavailable gates.
    """
    if truth.findings is FindingsStatus.BLOCKING_FINDINGS:
        return 1

    if classification == CLASSIFICATION_REQUIRED:
        if truth.execution is not ScanExecution.SUCCESS:
            return 1
        if truth.findings is FindingsStatus.UNKNOWN:
            return 1
        return 0

    # ADVISORY / EXTERNAL_UNAVAILABLE_ALLOWED: scanner infrastructure problems
    # do not block the pipeline, but they are reported as what they are.
    return 0


def render_summary(truth: MobsfTruth, classification: str, exit_code: int) -> str:
    """Human-readable summary that never merges the two dimensions."""
    lines = [
        "## MobSF static scan",
        "",
        f"- **Scan execution:** `{truth.execution.value}`",
        f"- **Findings status:** `{truth.findings.value}`",
        f"- **Gate classification:** `{classification}`",
        f"- **Reason:** {truth.reason}",
    ]
    if truth.blocking_count or truth.non_blocking_count:
        lines.append(
            f"- **Counts:** blocking={truth.blocking_count}, "
            f"non-blocking={truth.non_blocking_count}"
        )
    for note in truth.notes:
        lines.append(f"- Note: {note}")

    if truth.execution is not ScanExecution.SUCCESS:
        lines += [
            "",
            "Scan execution did not succeed, so **no security conclusion is "
            "claimed**. This is not a clean result; it is an absent result.",
        ]
    lines += ["", f"Gate decision: {'FAIL' if exit_code else 'OK'}"]
    return "\n".join(lines) + "\n"


def _write_github_output(truth: MobsfTruth) -> None:
    path = os.environ.get("GITHUB_OUTPUT")
    if not path:
        return
    with open(path, "a", encoding="utf-8") as fh:
        fh.write(f"scan_execution={truth.execution.value}\n")
        fh.write(f"findings_status={truth.findings.value}\n")


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        description="Interpret MobSF SARIF output without collapsing execution "
        "truth into findings truth."
    )
    ap.add_argument("--sarif", default="results.sarif", help="Path to SARIF output.")
    ap.add_argument(
        "--scanner-outcome",
        default=OUTCOME_UNKNOWN,
        help="GitHub Actions outcome of the scanner step.",
    )
    ap.add_argument(
        "--classification",
        default=CLASSIFICATION_ADVISORY,
        choices=[
            CLASSIFICATION_REQUIRED,
            CLASSIFICATION_ADVISORY,
            CLASSIFICATION_EXTERNAL_UNAVAILABLE_ALLOWED,
        ],
    )
    ap.add_argument("--json", action="store_true", help="Emit JSON instead of text.")
    ap.add_argument(
        "--summary-file",
        default=None,
        help="Append the rendered summary to this file (e.g. GITHUB_STEP_SUMMARY).",
    )
    args = ap.parse_args(argv)

    truth = interpret(
        sarif_path=Path(args.sarif) if args.sarif else None,
        scanner_outcome=args.scanner_outcome,
        classification=args.classification,
    )
    code = gate_exit_code(truth, args.classification)

    if args.json:
        payload = truth.as_dict()
        payload["classification"] = args.classification
        payload["gate_exit_code"] = code
        print(json.dumps(payload, indent=2, sort_keys=True))
    else:
        print(render_summary(truth, args.classification, code))

    if args.summary_file:
        with open(args.summary_file, "a", encoding="utf-8") as fh:
            fh.write(render_summary(truth, args.classification, code))

    _write_github_output(truth)
    return code


if __name__ == "__main__":
    sys.exit(main())
