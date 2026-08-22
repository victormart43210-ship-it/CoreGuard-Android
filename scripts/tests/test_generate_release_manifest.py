#!/usr/bin/env python3
"""Tests for the release evidence manifest generator (Task 7).

The invariant under test: a manifest may never claim more than the evidence
supports. Missing evidence stays UNKNOWN/NOT_VERIFIED, and a digest that
disagrees with the artifact is a failure rather than a silent correction.
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

from scripts.ci.generate_release_manifest import (  # noqa: E402
    PROVENANCE_FAILED,
    PROVENANCE_NOT_VERIFIED,
    PROVENANCE_VERIFIED,
    SCHEMA_VERSION,
    STATUS_UNKNOWN,
    ManifestError,
    build_manifest,
    main,
    serialize,
    sha256_file,
)

COMMIT = "a" * 40
REPO = "victormart43210-ship-it/CoreGuard-Android"
WHEN = "2026-08-22T21:00:00Z"


class ReleaseManifestTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = TemporaryDirectory()
        self.tmp = Path(self._tmp.name)
        self.artifact = self.tmp / "app-release.aab"
        self.artifact.write_bytes(b"pretend-bundle")

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def build(self, **kwargs):
        params = dict(
            repository=REPO,
            commit_sha=COMMIT,
            artifact_path=self.artifact,
            generated_at=WHEN,
        )
        params.update(kwargs)
        return build_manifest(**params)

    # ------------------------------------------------------------ determinism

    def test_generation_is_deterministic(self):
        a = serialize(self.build())
        b = serialize(self.build())
        self.assertEqual(a, b)
        # Sorted keys make the output diff-stable.
        self.assertEqual(
            list(json.loads(a).keys()), sorted(json.loads(a).keys())
        )

    def test_binds_commit_and_digest(self):
        manifest = self.build()
        self.assertEqual(SCHEMA_VERSION, manifest["schema_version"])
        self.assertEqual(COMMIT, manifest["commit_sha"])
        self.assertEqual(sha256_file(self.artifact), manifest["artifact_sha256"])
        self.assertEqual("app-release.aab", manifest["artifact"])
        self.assertEqual(len(b"pretend-bundle"), manifest["artifact_size_bytes"])

    # ----------------------------------------------------------- missing input

    def test_missing_commit_sha_is_rejected(self):
        for bad in ("", "   ", None):
            with self.subTest(bad=bad):
                with self.assertRaises(ManifestError):
                    self.build(commit_sha=bad)

    def test_non_sha_commit_is_rejected(self):
        with self.assertRaises(ManifestError):
            self.build(commit_sha="not-a-sha")

    def test_missing_artifact_is_rejected(self):
        with self.assertRaises(ManifestError):
            self.build(artifact_path=self.tmp / "absent.aab")

    def test_directory_as_artifact_is_rejected(self):
        with self.assertRaises(ManifestError):
            self.build(artifact_path=self.tmp)

    def test_missing_repository_is_rejected(self):
        with self.assertRaises(ManifestError):
            self.build(repository="")

    # ---------------------------------------------------------- digest mismatch

    def test_digest_mismatch_is_a_hard_failure(self):
        with self.assertRaises(ManifestError) as ctx:
            self.build(expected_sha256="b" * 64)
        self.assertIn("digest mismatch", str(ctx.exception))

    def test_matching_expected_digest_is_accepted(self):
        manifest = self.build(expected_sha256=sha256_file(self.artifact).upper())
        self.assertEqual(sha256_file(self.artifact), manifest["artifact_sha256"])

    def test_malformed_expected_digest_is_rejected(self):
        with self.assertRaises(ManifestError):
            self.build(expected_sha256="zzzz")

    # -------------------------------------------------------------- provenance

    def test_absent_provenance_is_not_verified_not_false_pass(self):
        manifest = self.build()
        self.assertEqual(PROVENANCE_NOT_VERIFIED, manifest["provenance_verified"])
        self.assertFalse(manifest["release_evidence_complete"])

    def test_provenance_failure_is_recorded(self):
        manifest = self.build(provenance_verified="failure")
        self.assertEqual(PROVENANCE_FAILED, manifest["provenance_verified"])
        self.assertFalse(manifest["release_evidence_complete"])

    def test_unrecognised_provenance_value_is_rejected(self):
        with self.assertRaises(ManifestError):
            self.build(provenance_verified="probably-fine")

    # ------------------------------------------------------------ gate statuses

    def test_unset_gate_status_is_unknown(self):
        manifest = self.build()
        self.assertEqual(STATUS_UNKNOWN, manifest["gates"]["build"])
        self.assertEqual(STATUS_UNKNOWN, manifest["gates"]["security"])

    def test_unknown_gate_status_blocks_completeness(self):
        manifest = self.build(
            provenance_verified="true",
            build_status="PASS",
            security_gate_status="UNKNOWN",
        )
        self.assertFalse(
            manifest["release_evidence_complete"],
            "UNKNOWN security gate must not read as release-ready",
        )

    def test_malformed_gate_status_is_rejected(self):
        with self.assertRaises(ManifestError):
            self.build(build_status="LOOKS_GOOD")

    def test_fully_verified_evidence_is_complete(self):
        manifest = self.build(
            provenance_verified="true",
            build_status="PASS",
            security_gate_status="PASS",
        )
        self.assertEqual(PROVENANCE_VERIFIED, manifest["provenance_verified"])
        self.assertTrue(manifest["release_evidence_complete"])

    def test_extra_gates_are_normalised_and_sorted(self):
        manifest = self.build(extra_gates={"instrumentation": "NOT_RUN", "lint": "PASS"})
        self.assertEqual("NOT_RUN", manifest["gates"]["instrumentation"])
        self.assertEqual("PASS", manifest["gates"]["lint"])

    # ------------------------------------------------------------------- CLI

    def test_cli_writes_manifest_and_succeeds(self):
        out = self.tmp / "manifest.json"
        code = main(
            [
                "--repository", REPO,
                "--commit-sha", COMMIT,
                "--artifact", str(self.artifact),
                "--generated-at", WHEN,
                "--provenance-verified", "true",
                "--build-status", "PASS",
                "--security-gate-status", "PASS",
                "--output", str(out),
                "--require-complete",
            ]
        )
        self.assertEqual(0, code)
        doc = json.loads(out.read_text(encoding="utf-8"))
        self.assertTrue(doc["release_evidence_complete"])

    def test_cli_require_complete_fails_on_incomplete_evidence(self):
        code = main(
            [
                "--repository", REPO,
                "--commit-sha", COMMIT,
                "--artifact", str(self.artifact),
                "--generated-at", WHEN,
                "--require-complete",
            ]
        )
        self.assertEqual(1, code)

    def test_cli_fails_on_digest_mismatch(self):
        code = main(
            [
                "--repository", REPO,
                "--commit-sha", COMMIT,
                "--artifact", str(self.artifact),
                "--generated-at", WHEN,
                "--expected-sha256", "c" * 64,
            ]
        )
        self.assertEqual(1, code)

    def test_cli_fails_on_missing_artifact(self):
        code = main(
            [
                "--repository", REPO,
                "--commit-sha", COMMIT,
                "--artifact", str(self.tmp / "nope.aab"),
                "--generated-at", WHEN,
            ]
        )
        self.assertEqual(1, code)

    # ------------------------------------------------------ tamper resistance

    def test_tampered_artifact_changes_the_digest(self):
        before = self.build()["artifact_sha256"]
        self.artifact.write_bytes(b"pretend-bundle-modified")
        after = self.build()["artifact_sha256"]
        self.assertNotEqual(before, after)

    def test_manifest_contains_no_secret_looking_fields(self):
        manifest = self.build(provenance_verified="true")
        blob = serialize(manifest).lower()
        for token in ("password", "secret", "token", "keystore", "private"):
            self.assertNotIn(token, blob)


if __name__ == "__main__":
    unittest.main()
