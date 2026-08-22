#!/usr/bin/env python3
"""Structural tests for the Android meta-gate ordering (Task 11).

The point of the meta-gates is that they run *before* expensive Android build
and emulator work. That ordering is only real if the dependency edge exists, so
these tests assert the edge and the gate commands directly. Deleting the
`needs:` line, dropping a verifier, or suppressing one fails this suite.
"""

from __future__ import annotations

import re
import sys
import unittest
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

ANDROID_WF = REPO_ROOT / ".github" / "workflows" / "android.yml"

SUPPRESSION_RE = re.compile(r"\|\|\s*(?:true|:)\b", re.IGNORECASE)

META_GATE_JOB = "meta-gates"

# Each required meta-gate, keyed by the script it must invoke.
REQUIRED_GATE_SCRIPTS = (
    "scripts/ci/verify_codeql_coverage.py",
    "scripts/ci/verify_security_workflows.py",
    "scripts/policy/verify_play_policy.py",
    "scripts/policy/verify_data_safety_drift.py",
)

# Jobs that must not start before the meta-gates pass.
EXPENSIVE_JOBS = ("build", "instrumented")


def _workflow() -> dict:
    return yaml.safe_load(ANDROID_WF.read_text(encoding="utf-8"))


def _needs(job: dict) -> list[str]:
    needs = job.get("needs", [])
    if isinstance(needs, str):
        return [needs]
    return list(needs or [])


def _truthy(value) -> bool:
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in {"true", "yes", "on"}


class AndroidMetaGateOrderingTest(unittest.TestCase):
    def setUp(self) -> None:
        self.doc = _workflow()
        self.jobs = self.doc["jobs"]

    def test_meta_gate_job_exists(self):
        self.assertIn(META_GATE_JOB, self.jobs)

    def test_every_required_verifier_is_invoked(self):
        steps = self.jobs[META_GATE_JOB]["steps"]
        commands = "\n".join(str(s.get("run", "") or "") for s in steps)
        for script in REQUIRED_GATE_SCRIPTS:
            self.assertIn(script, commands, f"meta-gate job must invoke {script}")

    def test_no_meta_gate_step_is_suppressed(self):
        for step in self.jobs[META_GATE_JOB]["steps"]:
            label = step.get("name") or step.get("uses") or "step"
            self.assertFalse(
                _truthy(step.get("continue-on-error", False)),
                f"meta-gate step must not be continue-on-error: {label}",
            )
            for line in str(step.get("run", "") or "").splitlines():
                if any(s in line for s in REQUIRED_GATE_SCRIPTS):
                    self.assertIsNone(
                        SUPPRESSION_RE.search(line),
                        f"meta-gate command must not be suppressed: {line.strip()}",
                    )

    def test_expensive_jobs_depend_on_meta_gates(self):
        for job_name in EXPENSIVE_JOBS:
            self.assertIn(job_name, self.jobs, f"expected job {job_name}")
            self.assertIn(
                META_GATE_JOB,
                _needs(self.jobs[job_name]),
                f"{job_name} must not start before the meta-gates pass",
            )

    def test_meta_gate_job_does_not_depend_on_expensive_jobs(self):
        # A cycle or reversed edge would defeat the fast-fail intent.
        needs = _needs(self.jobs[META_GATE_JOB])
        for job_name in EXPENSIVE_JOBS:
            self.assertNotIn(job_name, needs)

    def test_meta_gate_job_avoids_android_sdk_setup(self):
        # The whole point is that this job is cheap.
        blob = yaml.safe_dump(self.jobs[META_GATE_JOB])
        for expensive in ("sdkmanager", "gradle/actions/setup-gradle", "emulator"):
            self.assertNotIn(
                expensive,
                blob,
                f"meta-gate job must stay cheap; found {expensive}",
            )

    def test_meta_gate_job_runs_the_verifier_unit_tests(self):
        steps = self.jobs[META_GATE_JOB]["steps"]
        commands = "\n".join(str(s.get("run", "") or "") for s in steps)
        self.assertIn("scripts.tests.test_mobsf_truthfulness", commands)
        self.assertIn("scripts.tests.test_release_workflow_integrity", commands)


if __name__ == "__main__":
    unittest.main()
