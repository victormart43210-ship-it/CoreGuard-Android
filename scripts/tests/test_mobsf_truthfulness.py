#!/usr/bin/env python3
"""Tests for the MobSF truth interpreter (Task 5).

The invariant under test: a MobSF result may only be reported as clean when a
SARIF was actually parsed. Every other state — crash, timeout, skip, missing
file, malformed JSON, wrong shape, unreadable results — must remain visibly
not-PASS.
"""

from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from scripts.ci.mobsf_truthfulness import (  # noqa: E402
    CLASSIFICATION_ADVISORY,
    CLASSIFICATION_EXTERNAL_UNAVAILABLE_ALLOWED,
    CLASSIFICATION_REQUIRED,
    FindingsStatus,
    ScanExecution,
    gate_exit_code,
    interpret,
    main,
    render_summary,
)


def sarif(*results: dict, runs: int = 1) -> dict:
    """Minimal well-formed SARIF document carrying ``results``."""
    return {
        "version": "2.1.0",
        "runs": [
            {"tool": {"driver": {"name": "mobsfscan"}}, "results": list(results)}
            for _ in range(runs)
        ],
    }


class MobsfTruthfulnessTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = TemporaryDirectory()
        self.tmp = Path(self._tmp.name)

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def write(self, content: str | dict, name: str = "results.sarif") -> Path:
        path = self.tmp / name
        path.write_text(
            content if isinstance(content, str) else json.dumps(content),
            encoding="utf-8",
        )
        return path

    # ------------------------------------------------------------------ clean

    def test_clean_valid_sarif_is_pass(self):
        path = self.write(sarif())
        truth = interpret(path, "success")
        self.assertIs(truth.execution, ScanExecution.SUCCESS)
        self.assertIs(truth.findings, FindingsStatus.PASS)
        self.assertEqual(0, gate_exit_code(truth, CLASSIFICATION_REQUIRED))

    # --------------------------------------------------------------- findings

    def test_blocking_finding_fails_even_when_advisory(self):
        path = self.write(sarif({"level": "error", "ruleId": "x"}))
        truth = interpret(path, "success")
        self.assertIs(truth.execution, ScanExecution.SUCCESS)
        self.assertIs(truth.findings, FindingsStatus.BLOCKING_FINDINGS)
        self.assertEqual(1, truth.blocking_count)
        # A real finding blocks regardless of classification.
        self.assertEqual(1, gate_exit_code(truth, CLASSIFICATION_ADVISORY))
        self.assertEqual(1, gate_exit_code(truth, CLASSIFICATION_REQUIRED))
        self.assertEqual(
            1, gate_exit_code(truth, CLASSIFICATION_EXTERNAL_UNAVAILABLE_ALLOWED)
        )

    def test_non_blocking_finding_is_not_pass_but_does_not_fail(self):
        path = self.write(sarif({"level": "warning", "ruleId": "x"}))
        truth = interpret(path, "success")
        self.assertIs(truth.execution, ScanExecution.SUCCESS)
        self.assertIs(truth.findings, FindingsStatus.NON_BLOCKING_FINDINGS)
        self.assertEqual(0, gate_exit_code(truth, CLASSIFICATION_REQUIRED))

    def test_blocking_finding_across_multiple_runs_is_detected(self):
        doc = sarif({"level": "warning"}, runs=2)
        doc["runs"][1]["results"].append({"level": "error"})
        path = self.write(doc)
        truth = interpret(path, "success")
        self.assertIs(truth.findings, FindingsStatus.BLOCKING_FINDINGS)

    # ------------------------------------------------- invalid / absent output

    def test_empty_sarif_file_is_invalid_output(self):
        path = self.write("")
        truth = interpret(path, "success")
        self.assertIs(truth.execution, ScanExecution.INVALID_OUTPUT)
        self.assertIs(truth.findings, FindingsStatus.UNKNOWN)
        self.assertEqual(1, gate_exit_code(truth, CLASSIFICATION_REQUIRED))

    def test_malformed_json_fails_closed(self):
        path = self.write("{ not json")
        truth = interpret(path, "success")
        self.assertIs(truth.execution, ScanExecution.INVALID_OUTPUT)
        self.assertIs(truth.findings, FindingsStatus.UNKNOWN)

    def test_missing_file_with_success_outcome_is_invalid_output(self):
        truth = interpret(self.tmp / "absent.sarif", "success")
        self.assertIs(truth.execution, ScanExecution.INVALID_OUTPUT)
        self.assertIs(truth.findings, FindingsStatus.UNKNOWN)
        self.assertEqual(1, gate_exit_code(truth, CLASSIFICATION_REQUIRED))

    def test_missing_file_is_unavailable_only_under_approved_policy(self):
        truth = interpret(
            self.tmp / "absent.sarif",
            "success",
            classification=CLASSIFICATION_EXTERNAL_UNAVAILABLE_ALLOWED,
        )
        self.assertIs(truth.execution, ScanExecution.UNAVAILABLE)
        self.assertIs(truth.findings, FindingsStatus.UNKNOWN)

    def test_wrong_sarif_shape_is_invalid_output(self):
        for payload in ({"version": "2.1.0"}, {"runs": "nope"}, [1, 2, 3]):
            with self.subTest(payload=payload):
                path = self.write(
                    payload if isinstance(payload, dict) else json.dumps(payload)
                )
                truth = interpret(path, "success")
                self.assertIs(truth.execution, ScanExecution.INVALID_OUTPUT)
                self.assertIs(truth.findings, FindingsStatus.UNKNOWN)

    # ---------------------------------------------------- execution vs findings

    def test_scanner_failure_with_clean_sarif_is_not_a_clean_scan(self):
        # The adversarial case: crash, but leave a well-formed empty report.
        path = self.write(sarif())
        truth = interpret(path, "failure")
        self.assertIs(truth.execution, ScanExecution.FAILED)
        self.assertIs(
            truth.findings,
            FindingsStatus.UNKNOWN,
            "A failed scanner must never yield a PASS findings status",
        )
        self.assertEqual(1, gate_exit_code(truth, CLASSIFICATION_REQUIRED))

    def test_timeout_is_unavailable_not_clean(self):
        path = self.write(sarif())
        for outcome in ("cancelled", "timed_out"):
            with self.subTest(outcome=outcome):
                truth = interpret(path, outcome)
                self.assertIs(truth.execution, ScanExecution.UNAVAILABLE)
                self.assertIs(truth.findings, FindingsStatus.UNKNOWN)

    def test_skipped_scanner_is_not_run(self):
        truth = interpret(self.tmp / "absent.sarif", "skipped")
        self.assertIs(truth.execution, ScanExecution.NOT_RUN)
        self.assertIs(truth.findings, FindingsStatus.UNKNOWN)

    def test_scanner_success_with_blocking_sarif_still_blocks(self):
        path = self.write(sarif({"level": "error"}))
        truth = interpret(path, "success")
        self.assertIs(truth.execution, ScanExecution.SUCCESS)
        self.assertIs(truth.findings, FindingsStatus.BLOCKING_FINDINGS)
        self.assertEqual(1, gate_exit_code(truth, CLASSIFICATION_ADVISORY))

    # ----------------------------------------------------- ambiguous severities

    def test_ambiguous_severity_is_unknown_not_pass(self):
        path = self.write(sarif({"level": "spicy"}))
        truth = interpret(path, "success")
        self.assertIs(truth.execution, ScanExecution.SUCCESS)
        self.assertIs(truth.findings, FindingsStatus.UNKNOWN)
        self.assertEqual(1, truth.unknown_severity_count)
        self.assertEqual(1, gate_exit_code(truth, CLASSIFICATION_REQUIRED))

    def test_result_missing_level_is_unknown_not_pass(self):
        path = self.write(sarif({"ruleId": "no-level"}))
        truth = interpret(path, "success")
        self.assertIs(truth.findings, FindingsStatus.UNKNOWN)
        self.assertEqual(1, truth.unknown_severity_count)

    def test_malformed_result_objects_are_counted_and_not_pass(self):
        doc = sarif()
        doc["runs"][0]["results"] = ["a string", 42, {"level": 5}]
        path = self.write(doc)
        truth = interpret(path, "success")
        self.assertIs(truth.findings, FindingsStatus.UNKNOWN)
        self.assertEqual(3, truth.malformed_result_count)

    def test_blocking_wins_over_unknown(self):
        doc = sarif({"level": "error"}, {"level": "mystery"})
        path = self.write(doc)
        truth = interpret(path, "success")
        self.assertIs(truth.findings, FindingsStatus.BLOCKING_FINDINGS)

    # ------------------------------------------------------------- reporting

    def test_summary_separates_the_two_dimensions_and_avoids_fake_pass(self):
        path = self.write("{ broken")
        truth = interpret(path, "success")
        text = render_summary(truth, CLASSIFICATION_ADVISORY, 0)
        self.assertIn("Scan execution:", text)
        self.assertIn("Findings status:", text)
        self.assertIn("INVALID_OUTPUT", text)
        self.assertIn("no security conclusion is claimed", text)
        # The literal token used by the old workflow must not appear as a claim.
        self.assertNotIn("`PASS`", text)

    def test_summary_for_clean_scan_states_pass_explicitly(self):
        path = self.write(sarif())
        truth = interpret(path, "success")
        text = render_summary(truth, CLASSIFICATION_ADVISORY, 0)
        self.assertIn("`SUCCESS`", text)
        self.assertIn("`PASS`", text)

    # ------------------------------------------------------------------- CLI

    def test_cli_exit_code_blocks_on_blocking_findings(self):
        path = self.write(sarif({"level": "error"}))
        code = main(
            [
                "--sarif",
                str(path),
                "--scanner-outcome",
                "success",
                "--classification",
                CLASSIFICATION_ADVISORY,
                "--json",
            ]
        )
        self.assertEqual(1, code)

    def test_cli_advisory_tolerates_unavailable_without_claiming_pass(self):
        code = main(
            [
                "--sarif",
                str(self.tmp / "absent.sarif"),
                "--scanner-outcome",
                "failure",
                "--classification",
                CLASSIFICATION_ADVISORY,
                "--json",
            ]
        )
        self.assertEqual(0, code, "advisory infrastructure failure does not block")

        truth = interpret(self.tmp / "absent.sarif", "failure")
        self.assertIsNot(truth.findings, FindingsStatus.PASS)

    def test_cli_writes_summary_file(self):
        path = self.write(sarif())
        out = self.tmp / "summary.md"
        main(
            [
                "--sarif",
                str(path),
                "--scanner-outcome",
                "success",
                "--summary-file",
                str(out),
            ]
        )
        self.assertIn("Scan execution:", out.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
