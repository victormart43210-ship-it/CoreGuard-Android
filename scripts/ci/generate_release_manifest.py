#!/usr/bin/env python3
"""Deterministic release evidence manifest (CoreGuard V3.1A Task 7).

Binds a release artifact to the exact commit that produced it and to the exact
evidence gathered about it. The manifest is emitted from authoritative workflow
evidence only: this module never infers, defaults, or upgrades a status.

Design rules:
  * Absent evidence stays ``UNKNOWN`` / ``UNAVAILABLE`` / ``NOT_RUN``.
  * ``provenance_verified`` is a tri-state, not a bool that defaults to false in
    a way that reads like "checked and fine".
  * The artifact digest is recomputed from the file on disk; a caller-supplied
    expected digest that disagrees is a hard failure, never a silent overwrite.
  * Serialisation is deterministic (sorted keys, fixed separators) so the
    manifest can be diffed and re-verified.

Exit codes:
  0  manifest written and internally consistent
  1  evidence is missing or contradictory (digest mismatch, absent artifact)
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

SCHEMA_VERSION = 1

# Status vocabulary shared with the CI gates. UNKNOWN is a real value here.
STATUS_UNKNOWN = "UNKNOWN"
STATUS_UNAVAILABLE = "UNAVAILABLE"
STATUS_NOT_RUN = "NOT_RUN"
STATUS_PASS = "PASS"
STATUS_FAIL = "FAIL"

VALID_GATE_STATUSES = frozenset(
    {STATUS_PASS, STATUS_FAIL, STATUS_UNKNOWN, STATUS_UNAVAILABLE, STATUS_NOT_RUN}
)

# Provenance verification is tri-state: verified, failed, or never established.
PROVENANCE_VERIFIED = "VERIFIED"
PROVENANCE_FAILED = "FAILED"
PROVENANCE_NOT_VERIFIED = "NOT_VERIFIED"

VALID_PROVENANCE = frozenset(
    {PROVENANCE_VERIFIED, PROVENANCE_FAILED, PROVENANCE_NOT_VERIFIED}
)

_SHA_RE = re.compile(r"^[0-9a-f]{40}$|^[0-9a-f]{64}$")
_DIGEST_RE = re.compile(r"^[0-9a-f]{64}$")


class ManifestError(RuntimeError):
    """Raised when release evidence is missing or self-contradictory."""


def sha256_file(path: Path, chunk_size: int = 1024 * 1024) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        while chunk := fh.read(chunk_size):
            digest.update(chunk)
    return digest.hexdigest()


def _normalise_status(value: str | None, field: str) -> str:
    """Unset evidence becomes UNKNOWN; an unrecognised value is a hard error."""
    if value is None or not str(value).strip():
        return STATUS_UNKNOWN
    candidate = str(value).strip().upper()
    if candidate not in VALID_GATE_STATUSES:
        raise ManifestError(
            f"{field} has unrecognised value {value!r}; refusing to guess. "
            f"Allowed: {sorted(VALID_GATE_STATUSES)}"
        )
    return candidate


def _normalise_provenance(value: str | None) -> str:
    if value is None or not str(value).strip():
        return PROVENANCE_NOT_VERIFIED
    candidate = str(value).strip().upper()
    # Accept the shell-friendly spellings a workflow step is likely to emit.
    aliases = {
        "TRUE": PROVENANCE_VERIFIED,
        "VERIFIED": PROVENANCE_VERIFIED,
        "SUCCESS": PROVENANCE_VERIFIED,
        "FALSE": PROVENANCE_FAILED,
        "FAILED": PROVENANCE_FAILED,
        "FAILURE": PROVENANCE_FAILED,
        "NOT_VERIFIED": PROVENANCE_NOT_VERIFIED,
        "SKIPPED": PROVENANCE_NOT_VERIFIED,
        "UNKNOWN": PROVENANCE_NOT_VERIFIED,
    }
    if candidate not in aliases:
        raise ManifestError(
            f"provenance_verified has unrecognised value {value!r}; refusing to guess."
        )
    return aliases[candidate]


def build_manifest(
    repository: str,
    commit_sha: str,
    artifact_path: Path,
    generated_at: str,
    provenance_verified: str | None = None,
    build_status: str | None = None,
    security_gate_status: str | None = None,
    expected_sha256: str | None = None,
    extra_gates: dict[str, str] | None = None,
) -> dict[str, Any]:
    """Builds the manifest dict, refusing to invent any PASS value."""
    if not repository or not repository.strip():
        raise ManifestError("repository is required; manifest cannot be unbound.")

    if not commit_sha or not commit_sha.strip():
        raise ManifestError(
            "commit_sha is required; an unbound manifest cannot prove what was built."
        )
    commit = commit_sha.strip().lower()
    if not _SHA_RE.match(commit):
        raise ManifestError(f"commit_sha {commit_sha!r} is not a valid git SHA.")

    if not artifact_path.exists() or not artifact_path.is_file():
        raise ManifestError(
            f"artifact {artifact_path} does not exist; there is nothing to attest."
        )

    actual_digest = sha256_file(artifact_path)

    if expected_sha256:
        expected = expected_sha256.strip().lower()
        if not _DIGEST_RE.match(expected):
            raise ManifestError(
                f"expected_sha256 {expected_sha256!r} is not a SHA-256 hex digest."
            )
        if expected != actual_digest:
            # Never rewrite the digest to match the artifact: the disagreement is
            # the finding.
            raise ManifestError(
                "artifact digest mismatch: expected "
                f"{expected} but computed {actual_digest}."
            )

    gates = {
        "build": _normalise_status(build_status, "build_status"),
        "security": _normalise_status(security_gate_status, "security_gate_status"),
    }
    for name, value in sorted((extra_gates or {}).items()):
        gates[name] = _normalise_status(value, f"gate:{name}")

    provenance = _normalise_provenance(provenance_verified)

    manifest: dict[str, Any] = {
        "schema_version": SCHEMA_VERSION,
        "repository": repository.strip(),
        "commit_sha": commit,
        "artifact": artifact_path.name,
        "artifact_sha256": actual_digest,
        "artifact_size_bytes": artifact_path.stat().st_size,
        "provenance_verified": provenance,
        "gates": gates,
        "generated_at": generated_at,
        # An explicit, conservative roll-up so a consumer never has to infer it.
        "release_evidence_complete": (
            provenance == PROVENANCE_VERIFIED
            and gates["build"] == STATUS_PASS
            and gates["security"] == STATUS_PASS
        ),
    }
    return manifest


def serialize(manifest: dict[str, Any]) -> str:
    """Deterministic serialisation: sorted keys, fixed separators, trailing NL."""
    return json.dumps(manifest, indent=2, sort_keys=True, separators=(",", ": ")) + "\n"


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Generate a release evidence manifest.")
    ap.add_argument("--repository", required=True)
    ap.add_argument("--commit-sha", required=True)
    ap.add_argument("--artifact", required=True)
    ap.add_argument("--generated-at", required=True, help="ISO-8601 UTC timestamp.")
    ap.add_argument("--provenance-verified", default=None)
    ap.add_argument("--build-status", default=None)
    ap.add_argument("--security-gate-status", default=None)
    ap.add_argument("--expected-sha256", default=None)
    ap.add_argument("--output", default=None, help="Write manifest here.")
    ap.add_argument(
        "--require-complete",
        action="store_true",
        help="Exit non-zero when required release evidence is incomplete.",
    )
    args = ap.parse_args(argv)

    try:
        manifest = build_manifest(
            repository=args.repository,
            commit_sha=args.commit_sha,
            artifact_path=Path(args.artifact),
            generated_at=args.generated_at,
            provenance_verified=args.provenance_verified,
            build_status=args.build_status,
            security_gate_status=args.security_gate_status,
            expected_sha256=args.expected_sha256,
        )
    except ManifestError as exc:
        print(f"RELEASE_MANIFEST_ERROR: {exc}", file=sys.stderr)
        return 1

    payload = serialize(manifest)
    if args.output:
        Path(args.output).write_text(payload, encoding="utf-8")
    print(payload, end="")

    if args.require_complete and not manifest["release_evidence_complete"]:
        print(
            "RELEASE_EVIDENCE_INCOMPLETE: refusing to claim release readiness.",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
