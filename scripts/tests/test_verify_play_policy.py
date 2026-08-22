"""TDD tests for scripts/policy/verify_play_policy.py (stdlib + pytest)."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

from scripts.policy.verify_play_policy import CheckResult, run_checks

REPO_ROOT = Path(__file__).resolve().parents[2]
VALIDATOR = REPO_ROOT / "scripts" / "policy" / "verify_play_policy.py"

MANIFEST_HEAD = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
"""

PERMISSIONS_BASE = [
    '<uses-permission android:name="android.permission.INTERNET" />',
    '<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />',
    '<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />',
]

VPN_SERVICE_OK = """    <service
        android:name=".mvt.GuardVpnService"
        android:exported="false"
        android:permission="android.permission.BIND_VPN_SERVICE"
        android:foregroundServiceType="specialUse">
        <property
            android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
            android:value="Runs an on-device privacy VPN that blocks connections to servers known to track or surveil users" />
        <intent-filter>
            <action android:name="android.net.VpnService" />
        </intent-filter>
    </service>
"""

APPLICATION_OPEN = """    <application
        android:name=".CoreGuardApplication"
        android:allowBackup="false"
        android:usesCleartextTraffic="false">
"""

VPN_POLICY_DOC = """# VPN Service Policy

## Prominent in-app disclosure

The app shows a prominent disclosure before establishing the VPN and obtains
affirmative consent. This is the prominent disclosure section.
"""


def _write_manifest(repo_root: Path, body: str) -> None:
    manifest_path = repo_root / "app/src/main/AndroidManifest.xml"
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(MANIFEST_HEAD + body + "\n</manifest>\n", encoding="utf-8")


def _write_minimal_repo(
    repo_root: Path, *, manifest_body: str, include_docs: bool = True
) -> None:
    _write_manifest(repo_root, manifest_body)
    screen = (
        repo_root
        / "app/src/main/java/com/coldboar/coreguard/ui/screens/PrivacyPolicyScreen.kt"
    )
    screen.parent.mkdir(parents=True, exist_ok=True)
    screen.write_text("package com.coldboar.coreguard.ui.screens\n", encoding="utf-8")
    if include_docs:
        play = repo_root / "docs/play"
        play.mkdir(parents=True, exist_ok=True)
        (play / "VPN_SERVICE_POLICY.md").write_text(VPN_POLICY_DOC, encoding="utf-8")
        (play / "DATA_SAFETY_MAP.md").write_text(
            "# Data Safety Mapping\n", encoding="utf-8"
        )


def _manifest(
    permissions: list[str], service_block: str, allow_backup: str, cleartext: str
) -> str:
    parts = ["    " + p for p in permissions]
    app = APPLICATION_OPEN.replace(
        'android:allowBackup="false"', f'android:allowBackup="{allow_backup}"'
    ).replace(
        'android:usesCleartextTraffic="false"',
        f'android:usesCleartextTraffic="{cleartext}"',
    )
    parts.append(app)
    parts.append(service_block)
    parts.append("    </application>")
    return "\n".join(parts)


def _check(results: list[CheckResult], cid: str) -> CheckResult:
    for r in results:
        if r.id == cid:
            return r
    raise AssertionError(f"missing check result {cid}")


def test_all_correct_passes(tmp_path: Path) -> None:
    _write_minimal_repo(
        tmp_path,
        manifest_body=_manifest(PERMISSIONS_BASE, VPN_SERVICE_OK, "false", "false"),
    )
    results = run_checks(tmp_path)
    failed = [r for r in results if r.required and not r.passed]
    assert not failed, [r.id for r in failed]


def test_bind_vpn_service_missing_fails(tmp_path: Path) -> None:
    service = VPN_SERVICE_OK.replace(
        'android:permission="android.permission.BIND_VPN_SERVICE"', ""
    )
    _write_minimal_repo(
        tmp_path, manifest_body=_manifest(PERMISSIONS_BASE, service, "false", "false")
    )
    results = run_checks(tmp_path)
    assert _check(results, "vpn_service_usage").passed  # usage still detected
    assert not _check(results, "bind_vpn_service_permission").passed


def test_service_exported_true_fails(tmp_path: Path) -> None:
    service = VPN_SERVICE_OK.replace(
        'android:exported="false"', 'android:exported="true"'
    )
    _write_minimal_repo(
        tmp_path, manifest_body=_manifest(PERMISSIONS_BASE, service, "false", "false")
    )
    results = run_checks(tmp_path)
    assert not _check(results, "vpn_service_not_exported").passed


def test_request_install_packages_present_fails(tmp_path: Path) -> None:
    perms = PERMISSIONS_BASE + [
        '<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />'
    ]
    _write_minimal_repo(
        tmp_path, manifest_body=_manifest(perms, VPN_SERVICE_OK, "false", "false")
    )
    results = run_checks(tmp_path)
    assert not _check(results, "no_request_install_packages").passed


def test_allow_backup_true_fails(tmp_path: Path) -> None:
    _write_minimal_repo(
        tmp_path,
        manifest_body=_manifest(PERMISSIONS_BASE, VPN_SERVICE_OK, "true", "false"),
    )
    results = run_checks(tmp_path)
    assert not _check(results, "backup_and_cleartext_disabled").passed


def test_uses_cleartext_traffic_true_fails(tmp_path: Path) -> None:
    _write_minimal_repo(
        tmp_path,
        manifest_body=_manifest(PERMISSIONS_BASE, VPN_SERVICE_OK, "false", "true"),
    )
    results = run_checks(tmp_path)
    assert not _check(results, "backup_and_cleartext_disabled").passed


def test_missing_vpn_policy_doc_fails(tmp_path: Path) -> None:
    _write_minimal_repo(
        tmp_path,
        manifest_body=_manifest(PERMISSIONS_BASE, VPN_SERVICE_OK, "false", "false"),
        include_docs=False,
    )
    results = run_checks(tmp_path)
    assert not _check(results, "vpn_service_policy_doc").passed
    assert not _check(results, "prominent_disclosure_doc").passed
    assert not _check(results, "data_safety_map_doc").passed


def test_vpn_service_usage_missing_fails(tmp_path: Path) -> None:
    # No service block at all.
    _write_minimal_repo(
        tmp_path, manifest_body=_manifest(PERMISSIONS_BASE, "", "false", "false")
    )
    results = run_checks(tmp_path)
    assert not _check(results, "vpn_service_usage").passed


def test_integration_real_repo_passes() -> None:
    proc = subprocess.run(
        [sys.executable, str(VALIDATOR), "--repo-root", str(REPO_ROOT)],
        capture_output=True,
        text=True,
    )
    assert proc.returncode == 0, proc.stderr + proc.stdout


def test_json_output(tmp_path: Path) -> None:
    _write_minimal_repo(
        tmp_path,
        manifest_body=_manifest(PERMISSIONS_BASE, VPN_SERVICE_OK, "false", "false"),
    )
    proc = subprocess.run(
        [sys.executable, str(VALIDATOR), "--repo-root", str(tmp_path), "--json"],
        capture_output=True,
        text=True,
    )
    assert proc.returncode == 0, proc.stderr
    import json

    payload = json.loads(proc.stdout)
    assert payload["passed"] is True
    assert len(payload["results"]) >= 10
