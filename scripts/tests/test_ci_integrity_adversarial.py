#!/usr/bin/env python3
"""Adversarial tests against the V3.1A CI integrity mechanisms.

Coverage is not the goal here. The goal is to show that a *false security claim
cannot become authoritative*: each test takes the position of someone trying to
make a broken or unverified state look green, and asserts the gate refuses.
"""

from __future__ import annotations

import json
import sys
import textwrap
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

import yaml

REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from scripts.ci.generate_release_manifest import (  # noqa: E402
    ManifestError,
    build_manifest,
    serialize,
)
from scripts.ci.mobsf_truthfulness import (  # noqa: E402
    CLASSIFICATION_ADVISORY,
    CLASSIFICATION_REQUIRED,
    FindingsStatus,
    ScanExecution,
    gate_exit_code,
    interpret,
)
from scripts.ci.verify_security_workflows import (  # noqa: E402
    verify_security_workflows,
)

COMMIT = "b" * 40
REPO = "victormart43210-ship-it/CoreGuard-Android"
WHEN = "2026-08-22T21:30:00Z"


class MobsfAdversarialTest(unittest.TestCase):
    """Attempts to make an untrustworthy MobSF run look clean."""

    def setUp(self) -> None:
        self._tmp = TemporaryDirectory()
        self.tmp = Path(self._tmp.name)

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def write(self, text: str) -> Path:
        p = self.tmp / "results.sarif"
        p.write_text(text, encoding="utf-8")
        return p

    def test_fake_pass_text_inside_sarif_is_not_believed(self):
        # Embedding reassuring words in the report must not influence the gate.
        path = self.write(
            json.dumps(
                {
                    "runs": [
                        {
                            "results": [
                                {
                                    "level": "error",
                                    "message": {"text": "PASS clean safe"},
                                }
                            ]
                        }
                    ]
                }
            )
        )
        truth = interpret(path, "success")
        self.assertIs(truth.findings, FindingsStatus.BLOCKING_FINDINGS)

    def test_empty_file_cannot_masquerade_as_clean_scan(self):
        truth = interpret(self.write(""), "success")
        self.assertIsNot(truth.findings, FindingsStatus.PASS)

    def test_sarif_shaped_like_success_but_scanner_crashed(self):
        path = self.write(json.dumps({"runs": [{"results": []}]}))
        truth = interpret(path, "failure")
        self.assertIs(truth.execution, ScanExecution.FAILED)
        self.assertIsNot(truth.findings, FindingsStatus.PASS)

    def test_path_substitution_to_a_different_clean_file_is_still_judged(self):
        # Pointing the gate at an unrelated clean file yields a real parse, but
        # the scanner outcome still governs execution truth.
        decoy = self.tmp / "decoy.sarif"
        decoy.write_text(json.dumps({"runs": [{"results": []}]}), encoding="utf-8")
        truth = interpret(decoy, "failure")
        self.assertIsNot(truth.findings, FindingsStatus.PASS)

    def test_required_classification_cannot_be_satisfied_by_unknown(self):
        path = self.write(json.dumps({"runs": [{"results": [{"level": "???"}]}]}))
        truth = interpret(path, "success")
        self.assertEqual(1, gate_exit_code(truth, CLASSIFICATION_REQUIRED))

    def test_advisory_classification_never_relabels_failure_as_pass(self):
        truth = interpret(self.tmp / "missing.sarif", "failure")
        # Advisory means "does not block", not "counts as clean".
        self.assertEqual(0, gate_exit_code(truth, CLASSIFICATION_ADVISORY))
        self.assertIsNot(truth.findings, FindingsStatus.PASS)
        self.assertIsNot(truth.execution, ScanExecution.SUCCESS)


class ReleaseManifestAdversarialTest(unittest.TestCase):
    """Attempts to forge release evidence."""

    def setUp(self) -> None:
        self._tmp = TemporaryDirectory()
        self.tmp = Path(self._tmp.name)
        self.artifact = self.tmp / "app.aab"
        self.artifact.write_bytes(b"bundle")

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def test_mismatched_hash_cannot_be_written_into_the_manifest(self):
        with self.assertRaises(ManifestError):
            build_manifest(
                repository=REPO,
                commit_sha=COMMIT,
                artifact_path=self.artifact,
                generated_at=WHEN,
                expected_sha256="0" * 64,
            )

    def test_detached_sha_evidence_is_rejected(self):
        for bogus in ("HEAD", "latest", "main", "12345"):
            with self.subTest(bogus=bogus):
                with self.assertRaises(ManifestError):
                    build_manifest(
                        repository=REPO,
                        commit_sha=bogus,
                        artifact_path=self.artifact,
                        generated_at=WHEN,
                    )

    def test_absent_attestation_cannot_yield_a_complete_release(self):
        manifest = build_manifest(
            repository=REPO,
            commit_sha=COMMIT,
            artifact_path=self.artifact,
            generated_at=WHEN,
            build_status="PASS",
            security_gate_status="PASS",
        )
        self.assertFalse(manifest["release_evidence_complete"])

    def test_invented_gate_status_is_rejected_rather_than_coerced(self):
        for invented in ("PASSED", "GREEN", "OK", "SUCCESS"):
            with self.subTest(invented=invented):
                with self.assertRaises(ManifestError):
                    build_manifest(
                        repository=REPO,
                        commit_sha=COMMIT,
                        artifact_path=self.artifact,
                        generated_at=WHEN,
                        security_gate_status=invented,
                    )

    def test_tampered_manifest_no_longer_matches_the_artifact(self):
        manifest = build_manifest(
            repository=REPO,
            commit_sha=COMMIT,
            artifact_path=self.artifact,
            generated_at=WHEN,
        )
        tampered = json.loads(serialize(manifest))
        tampered["artifact_sha256"] = "f" * 64

        # Re-deriving from the artifact exposes the edit.
        rebuilt = build_manifest(
            repository=REPO,
            commit_sha=COMMIT,
            artifact_path=self.artifact,
            generated_at=WHEN,
        )
        self.assertNotEqual(tampered["artifact_sha256"], rebuilt["artifact_sha256"])

    def test_missing_provenance_field_defaults_to_not_verified(self):
        manifest = build_manifest(
            repository=REPO,
            commit_sha=COMMIT,
            artifact_path=self.artifact,
            generated_at=WHEN,
        )
        self.assertEqual("NOT_VERIFIED", manifest["provenance_verified"])


class WorkflowSuppressionAdversarialTest(unittest.TestCase):
    """Attempts to silence a REQUIRED gate in a synthetic repository."""

    def setUp(self) -> None:
        self._tmp = TemporaryDirectory()
        self.root = Path(self._tmp.name)
        (self.root / ".github" / "workflows").mkdir(parents=True)
        gates = {
            "schema_version": "1.0.0",
            "diagnostic_commands": ["echo", "ls"],
            "gates": {
                "android-build": {
                    "classification": "REQUIRED",
                    "workflow": "android.yml",
                    "job": "build",
                }
            },
            "allowlist": [],
        }
        (self.root / ".github" / "security-gates.json").write_text(
            json.dumps(gates), encoding="utf-8"
        )

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def write_wf(self, body: str, name: str = "android.yml") -> None:
        (self.root / ".github" / "workflows" / name).write_text(
            textwrap.dedent(body), encoding="utf-8"
        )

    def test_continue_on_error_on_required_gate_is_caught(self):
        self.write_wf(
            """
            name: Android CI
            jobs:
              build:
                runs-on: ubuntu-latest
                steps:
                  - name: Security gate
                    continue-on-error: true
                    run: python3 scripts/ci/verify_security_workflows.py
            """
        )
        issues = verify_security_workflows(self.root)
        self.assertTrue(any(i.severity == "FAIL" for i in issues))

    def test_or_true_suppression_on_required_gate_is_caught(self):
        self.write_wf(
            """
            name: Android CI
            jobs:
              build:
                runs-on: ubuntu-latest
                steps:
                  - name: Security gate
                    run: python3 scripts/ci/verify_play_policy.py || true
            """
        )
        issues = verify_security_workflows(self.root)
        self.assertTrue(any(i.severity == "FAIL" for i in issues))

    def test_renaming_the_step_does_not_evade_detection(self):
        self.write_wf(
            """
            name: Android CI
            jobs:
              build:
                runs-on: ubuntu-latest
                steps:
                  - name: Totally routine housekeeping
                    continue-on-error: true
                    run: python3 scripts/ci/verify_codeql_coverage.py
            """
        )
        issues = verify_security_workflows(self.root)
        self.assertTrue(
            any(i.severity == "FAIL" for i in issues),
            "detection must key off the command, not the step name",
        )

    def test_malformed_yaml_fails_closed(self):
        self.write_wf("name: [unclosed\njobs: {")
        issues = verify_security_workflows(self.root)
        self.assertTrue(any(i.severity == "FAIL" for i in issues))

    def test_missing_gates_file_fails_closed(self):
        (self.root / ".github" / "security-gates.json").unlink()
        self.write_wf("name: Android CI\njobs: {}\n")
        issues = verify_security_workflows(self.root)
        self.assertTrue(any(i.severity == "FAIL" for i in issues))


class LiveWorkflowInvariantTest(unittest.TestCase):
    """The real repository must satisfy the invariants, not just fixtures."""

    def test_repository_workflows_have_no_suppressed_required_gates(self):
        issues = verify_security_workflows(REPO_ROOT)
        failures = [i for i in issues if i.severity == "FAIL"]
        self.assertEqual([], failures, f"unexpected suppressions: {failures}")

    def test_mobsf_workflow_no_longer_claims_pass_from_file_existence(self):
        wf = (REPO_ROOT / ".github" / "workflows" / "mobsf.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn("mobsf_truthfulness.py", wf)
        self.assertNotIn("PASS — SARIF written", wf)

    def test_mobsf_interpreter_receives_the_real_scanner_outcome(self):
        doc = yaml.safe_load(
            (REPO_ROOT / ".github" / "workflows" / "mobsf.yml").read_text(
                encoding="utf-8"
            )
        )
        steps = doc["jobs"]["mobile-security"]["steps"]
        interp = [s for s in steps if "mobsf_truthfulness.py" in str(s.get("run", ""))]
        self.assertEqual(1, len(interp))
        run = str(interp[0]["run"])
        self.assertIn("--scanner-outcome", run)
        self.assertIn("steps.mobsf.outcome", run)


if __name__ == "__main__":
    unittest.main()
