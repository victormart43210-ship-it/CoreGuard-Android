#!/usr/bin/env python3
"""Structural integrity tests for the release workflow (Tasks 6 and 7).

These tests exist so the provenance-verification and manifest steps cannot be
quietly removed, renamed into a no-op, reordered after publication, or
suppressed. Every assertion keys off the *command content* rather than the step
name, so renaming a step does not neutralise the check.
"""

from __future__ import annotations

import json
import re
import sys
import unittest
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

RELEASE_WF = REPO_ROOT / ".github" / "workflows" / "release.yml"
GATES_JSON = REPO_ROOT / ".github" / "security-gates.json"

SUPPRESSION_RE = re.compile(r"\|\|\s*(?:true|:)\b", re.IGNORECASE)

VERIFY_COMMAND = "gh attestation verify"
MANIFEST_COMMAND = "generate_release_manifest.py"
ATTEST_ACTION = "actions/attest-build-provenance"
PUBLISH_ACTION = "action-gh-release"
UPLOAD_ACTION = "actions/upload-artifact"


def _steps() -> list[dict]:
    doc = yaml.safe_load(RELEASE_WF.read_text(encoding="utf-8"))
    return list(doc["jobs"]["release"]["steps"])


def _truthy(value) -> bool:
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in {"true", "yes", "on"}


def _index_of_run(steps: list[dict], needle: str) -> int:
    for i, step in enumerate(steps):
        if needle in str(step.get("run", "") or ""):
            return i
    return -1


def _index_of_uses(steps: list[dict], needle: str) -> int:
    for i, step in enumerate(steps):
        if needle in str(step.get("uses", "") or ""):
            return i
    return -1


class ReleaseWorkflowIntegrityTest(unittest.TestCase):
    def setUp(self) -> None:
        self.steps = _steps()

    # ------------------------------------------------- attestation verification

    def test_attestation_verification_step_exists(self):
        self.assertGreaterEqual(
            _index_of_run(self.steps, VERIFY_COMMAND),
            0,
            "release.yml must verify the attestation, not only generate it",
        )

    def test_verification_is_not_suppressed(self):
        idx = _index_of_run(self.steps, VERIFY_COMMAND)
        step = self.steps[idx]
        self.assertFalse(
            _truthy(step.get("continue-on-error", False)),
            "attestation verification must not be continue-on-error",
        )
        for line in str(step["run"]).splitlines():
            if VERIFY_COMMAND in line:
                self.assertIsNone(
                    SUPPRESSION_RE.search(line),
                    f"verification must not be suppressed: {line.strip()}",
                )

    def test_verification_pins_the_signer_repository(self):
        idx = _index_of_run(self.steps, VERIFY_COMMAND)
        run = str(self.steps[idx]["run"])
        self.assertIn(
            "--repo",
            run,
            "verification must pin the expected repository identity, otherwise an "
            "attestation from another repo could satisfy the gate",
        )

    def test_verification_targets_the_built_artifact(self):
        idx = _index_of_run(self.steps, VERIFY_COMMAND)
        run = str(self.steps[idx]["run"])
        self.assertIn(
            "AAB_PATH",
            run,
            "verification must target the exact produced artifact path",
        )

    def test_verification_runs_after_attestation_generation(self):
        attest = _index_of_uses(self.steps, ATTEST_ACTION)
        verify = _index_of_run(self.steps, VERIFY_COMMAND)
        self.assertGreaterEqual(attest, 0)
        self.assertGreater(
            verify, attest, "verification must follow attestation generation"
        )

    def test_verification_runs_before_upload_and_publish(self):
        verify = _index_of_run(self.steps, VERIFY_COMMAND)
        for action in (UPLOAD_ACTION, PUBLISH_ACTION):
            idx = _index_of_uses(self.steps, action)
            if idx >= 0:
                self.assertLess(
                    verify,
                    idx,
                    f"verification must precede {action}; otherwise an unverified "
                    "artifact can be published",
                )

    def test_verification_step_has_no_always_condition(self):
        # `if: always()` would let the job continue past a failed verification.
        idx = _index_of_run(self.steps, VERIFY_COMMAND)
        condition = str(self.steps[idx].get("if", "") or "")
        self.assertNotIn("always()", condition)

    # -------------------------------------------------------- release manifest

    def test_manifest_generation_step_exists_and_is_not_suppressed(self):
        idx = _index_of_run(self.steps, MANIFEST_COMMAND)
        self.assertGreaterEqual(idx, 0, "release.yml must emit an evidence manifest")
        step = self.steps[idx]
        self.assertFalse(_truthy(step.get("continue-on-error", False)))
        for line in str(step["run"]).splitlines():
            if MANIFEST_COMMAND in line:
                self.assertIsNone(SUPPRESSION_RE.search(line))

    def test_manifest_binds_commit_artifact_and_digest(self):
        idx = _index_of_run(self.steps, MANIFEST_COMMAND)
        run = str(self.steps[idx]["run"])
        for flag in ("--commit-sha", "--artifact", "--expected-sha256", "--repository"):
            self.assertIn(flag, run, f"manifest must be bound via {flag}")

    def test_manifest_consumes_real_verification_output(self):
        idx = _index_of_run(self.steps, MANIFEST_COMMAND)
        run = str(self.steps[idx]["run"])
        self.assertIn(
            "provenance_verified",
            run,
            "manifest must record the actual verification result, not a literal",
        )
        # A hardcoded success value would be a fabricated claim.
        self.assertNotIn("--provenance-verified true", run.lower())
        self.assertNotIn("--provenance-verified verified", run.lower())

    def test_manifest_runs_after_verification(self):
        verify = _index_of_run(self.steps, VERIFY_COMMAND)
        manifest = _index_of_run(self.steps, MANIFEST_COMMAND)
        self.assertGreater(
            manifest, verify, "manifest must be generated after verification"
        )

    # ------------------------------------------------------------ gate authority

    def test_release_gate_is_declared_required(self):
        gates = json.loads(GATES_JSON.read_text(encoding="utf-8"))["gates"]
        release_gates = [
            g for g in gates.values() if g.get("workflow") == "release.yml"
        ]
        self.assertTrue(release_gates, "release.yml must be declared in security-gates")
        for gate in release_gates:
            self.assertEqual("REQUIRED", gate.get("classification"))

    def test_no_secret_values_are_echoed_in_verification_or_manifest(self):
        for needle in (VERIFY_COMMAND, MANIFEST_COMMAND):
            idx = _index_of_run(self.steps, needle)
            run = str(self.steps[idx]["run"]).lower()
            for token in (
                "signing_key_password",
                "signing_store_password",
                "keystore_base64",
            ):
                self.assertNotIn(token, run)


if __name__ == "__main__":
    unittest.main()
