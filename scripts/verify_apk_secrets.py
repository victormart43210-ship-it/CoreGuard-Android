#!/usr/bin/env python3
"""Deterministic Zero-APK-Secrets gate with redacted findings."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import sys
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Rule:
    name: str
    pattern: re.Pattern[bytes]
    note: str


RULES = (
    Rule(
        name="google-api-key",
        pattern=re.compile(rb"AIza[0-9A-Za-z\-_]{35}"),
        note="Google API key pattern",
    ),
    Rule(
        name="private-key-material",
        pattern=re.compile(rb"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
        note="Private key block header",
    ),
    Rule(
        name="oauth-access-token",
        pattern=re.compile(rb"ya29\.[0-9A-Za-z\-_]+"),
        note="Google OAuth access token prefix",
    ),
    Rule(
        name="client-secret-assignment",
        pattern=re.compile(rb"(?i)client_secret\s*[:=]\s*['\"]?[0-9A-Za-z_\-\.]{8,}"),
        note="Client secret assignment",
    ),
    Rule(
        name="local-password-assignment",
        pattern=re.compile(
            rb"(?i)(?:local|debug|dev|test)_password\s*[:=]\s*['\"]?[^\s'\"\\]{6,}"
        ),
        note="Local password assignment",
    ),
)

SERVICE_ACCOUNT_TYPE = re.compile(rb'(?i)"type"\s*:\s*"service_account"')
SERVICE_ACCOUNT_PRIVATE_KEY = re.compile(rb'(?i)"private_key"\s*:\s*"-----BEGIN')

# Narrow allowlist for known non-secret identifiers that can appear in docs/config blobs.
ALLOWLIST = (
    re.compile(rb"(?i)\bclient_secret_version\b"),
    re.compile(rb"(?i)\bno_client_secret_configured\b"),
)


def _is_allowlisted(blob: bytes, start: int, end: int) -> bool:
    window_start = max(0, start - 64)
    window_end = min(len(blob), end + 64)
    context = blob[window_start:window_end]
    return any(pattern.search(context) for pattern in ALLOWLIST)


def _scan_blob(blob: bytes, path: str, findings: list[str]) -> None:
    for rule in RULES:
        for match in rule.pattern.finditer(blob):
            if _is_allowlisted(blob, match.start(), match.end()):
                continue
            findings.append(f"{rule.name} in {path}")
            break

    type_match = SERVICE_ACCOUNT_TYPE.search(blob)
    key_match = SERVICE_ACCOUNT_PRIVATE_KEY.search(blob)
    if type_match and key_match and not _is_allowlisted(blob, type_match.start(), key_match.end()):
        findings.append(f"service-account-private-key in {path}")


def scan_apk(apk_path: Path) -> tuple[str, list[str]]:
    if not apk_path.is_file():
        raise FileNotFoundError(f"APK not found: {apk_path}")

    apk_bytes = apk_path.read_bytes()
    apk_sha256 = hashlib.sha256(apk_bytes).hexdigest()
    findings: list[str] = []

    try:
        with zipfile.ZipFile(io.BytesIO(apk_bytes)) as archive:
            for info in archive.infolist():
                if info.is_dir():
                    continue
                data = archive.read(info.filename)
                _scan_blob(data, info.filename, findings)
    except zipfile.BadZipFile as exc:
        raise RuntimeError(f"Unreadable APK zip: {apk_path}") from exc

    return apk_sha256, sorted(set(findings))


def run_self_test() -> int:
    with tempfile.TemporaryDirectory(prefix="apk-secret-gate-") as tmp_dir:
        tmp = Path(tmp_dir)
        clean_apk = tmp / "clean.apk"
        bad_apk = tmp / "bad.apk"

        with zipfile.ZipFile(clean_apk, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("assets/readme.txt", "client_secret_version=1")
            archive.writestr("res/raw/policy.txt", "no credentials here")

        with zipfile.ZipFile(bad_apk, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr(
                "assets/fake-key.txt",
                "AIzaAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            )
            archive.writestr(
                "assets/service-account.json",
                json.dumps(
                    {
                        "type": "service_account",
                        "private_key": "-----BEGIN PRIVATE KEY-----FAKE-----END PRIVATE KEY-----",
                    }
                ),
            )

        _, clean_findings = scan_apk(clean_apk)
        _, bad_findings = scan_apk(bad_apk)

        if clean_findings:
            print("[SELF-TEST] Clean fixture unexpectedly failed.", file=sys.stderr)
            for finding in clean_findings:
                print(f"[SELF-TEST] {finding}", file=sys.stderr)
            return 1
        if not bad_findings:
            print("[SELF-TEST] Secret fixture unexpectedly passed.", file=sys.stderr)
            return 1

    print("[SELF-TEST] PASS — clean fixture passed and fake-secret fixture failed.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Fail-closed scanner for forbidden secrets in APK binary content."
    )
    parser.add_argument("--apk", type=Path, help="Path to APK to scan.")
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="Run deterministic fixtures (clean pass + fake-secret fail).",
    )
    args = parser.parse_args()

    if args.self_test:
        return run_self_test()

    if args.apk is None:
        parser.error("--apk is required unless --self-test is used")

    try:
        apk_sha256, findings = scan_apk(args.apk)
    except Exception as exc:  # fail closed
        print(f"[ZERO-APK-SECRETS] ERROR: {exc}", file=sys.stderr)
        return 2

    print(f"[ZERO-APK-SECRETS] APK SHA-256: {apk_sha256}")
    if findings:
        print("[ZERO-APK-SECRETS] FAIL: forbidden secret indicators found.")
        for finding in findings:
            print(f"[ZERO-APK-SECRETS] - {finding}")
        print("[ZERO-APK-SECRETS] Secret values are intentionally redacted.")
        return 1

    print("[ZERO-APK-SECRETS] PASS: no forbidden secret indicators found.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
