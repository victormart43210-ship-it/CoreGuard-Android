#!/usr/bin/env python3
"""Static preflight gate for Google Play VpnService policy readiness.

Inspects the CoreGuard source tree (AndroidManifest + docs) and verifies the
conditions Google Play requires before an app may declare a VpnService. Exits 0
on PASS and nonzero on any REQUIRED check failure.

Run from the repo root::

    python3 scripts/policy/verify_play_policy.py
    python3 scripts/policy/verify_play_policy.py --json
    python3 scripts/policy/verify_play_policy.py --repo-root /path/to/repo

Stdlib only (xml.etree.ElementTree, json, argparse, pathlib). No third-party
imports, matching scripts/threat_intel/validate_configs.py.
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from xml.etree import ElementTree as ET  # noqa: N817

ANDROID_NS = "http://schemas.android.com/apk/res/android"
MANIFEST_REL = Path("app/src/main/AndroidManifest.xml")
VPN_POLICY_DOC_REL = Path("docs/play/VPN_SERVICE_POLICY.md")
DATA_SAFETY_MAP_REL = Path("docs/play/DATA_SAFETY_MAP.md")
PRIVACY_POLICY_SCREEN_GLOB = "PrivacyPolicyScreen.kt"
PRIVACY_POLICY_HTML_REL = Path("docs/privacy-policy.html")

BIND_VPN_SERVICE = "android.permission.BIND_VPN_SERVICE"
FOREGROUND_SERVICE = "android.permission.FOREGROUND_SERVICE"
FOREGROUND_SERVICE_SPECIAL_USE = "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
REQUEST_INSTALL_PACKAGES = "android.permission.REQUEST_INSTALL_PACKAGES"
VPN_ACTION = "android.net.VpnService"
SPECIAL_USE_SUBTYPE_PROPERTY = "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"


@dataclass
class CheckResult:
    """Outcome of a single policy check."""

    id: str
    name: str
    passed: bool
    required: bool
    message: str


def _a(attr: str) -> str:
    """Qualify an android: attribute name with its namespace URI."""
    return f"{{{ANDROID_NS}}}{attr}"


def _parse_manifest(repo_root: Path) -> ET.Element:
    manifest_path = repo_root / MANIFEST_REL
    if not manifest_path.exists():
        raise FileNotFoundError(f"AndroidManifest not found at {manifest_path}")
    return ET.parse(manifest_path).getroot()


def _permission_names(root: ET.Element) -> set[str]:
    names: set[str] = set()
    for perm in root.findall("uses-permission"):
        name = perm.get(_a("name"))
        if name:
            names.add(name)
    return names


def _vpn_service(root: ET.Element) -> ET.Element | None:
    """Return the service element declaring the VpnService intent-filter."""
    for service in root.iter("service"):
        for intent_filter in service.findall("intent-filter"):
            for action in intent_filter.findall("action"):
                if action.get(_a("name")) == VPN_ACTION:
                    return service
    return None


def _privacy_policy_reference(repo_root: Path) -> Path | None:
    html = repo_root / PRIVACY_POLICY_HTML_REL
    if html.exists():
        return html
    for match in repo_root.rglob(PRIVACY_POLICY_SCREEN_GLOB):
        return match
    return None


def run_checks(repo_root: Path) -> list[CheckResult]:
    """Run all Play/VPN policy checks against the given repo root."""
    results: list[CheckResult] = []
    root = _parse_manifest(repo_root)
    permissions = _permission_names(root)
    application = root.find("application")

    vpn_service = _vpn_service(root)

    # 1. VpnService usage detected in manifest (the service with BIND_VPN_SERVICE).
    results.append(
        CheckResult(
            id="vpn_service_usage",
            name="VpnService usage detected in manifest",
            passed=vpn_service is not None,
            required=True,
            message=(
                "A <service> declaring the android.net.VpnService intent-filter was found."
                if vpn_service is not None
                else "No <service> declaring the android.net.VpnService intent-filter was found."
            ),
        )
    )

    # 2. BIND_VPN_SERVICE permission declared on that service.
    bind_ok = (
        vpn_service is not None
        and vpn_service.get(_a("permission")) == BIND_VPN_SERVICE
    )
    results.append(
        CheckResult(
            id="bind_vpn_service_permission",
            name="BIND_VPN_SERVICE permission declared on the VPN service",
            passed=bind_ok,
            required=True,
            message=(
                "Service declares android:permission=android.permission.BIND_VPN_SERVICE."
                if bind_ok
                else "The VPN service must declare android:permission=android.permission.BIND_VPN_SERVICE."
            ),
        )
    )

    # 3. The VPN service is not exported (exported="false").
    exported = vpn_service.get(_a("exported")) if vpn_service is not None else None
    exported_ok = exported == "false"
    results.append(
        CheckResult(
            id="vpn_service_not_exported",
            name="VPN service exported=false",
            passed=exported_ok,
            required=True,
            message=(
                f"Service android:exported={exported!r} (expected 'false')."
                if not exported_ok
                else "Service android:exported=false."
            ),
        )
    )

    # 4. Foreground-service requirements.
    fg_perms_ok = (
        FOREGROUND_SERVICE in permissions
        and FOREGROUND_SERVICE_SPECIAL_USE in permissions
    )
    fg_type = (
        vpn_service.get(_a("foregroundServiceType"))
        if vpn_service is not None
        else None
    )
    subtype_value: str | None = None
    if vpn_service is not None:
        for prop in vpn_service.findall("property"):
            if prop.get(_a("name")) == SPECIAL_USE_SUBTYPE_PROPERTY:
                subtype_value = prop.get(_a("value"))
    fg_type_ok = fg_type == "specialUse" and bool(subtype_value)
    fg_ok = fg_perms_ok and fg_type_ok
    results.append(
        CheckResult(
            id="foreground_service_special_use",
            name="Foreground service specialUse + subtype property",
            passed=fg_ok,
            required=True,
            message=(
                "FOREGROUND_SERVICE + FOREGROUND_SERVICE_SPECIAL_USE permissions present, "
                "foregroundServiceType=specialUse and subtype property set."
                if fg_ok
                else (
                    "Missing foreground-service requirements: "
                    f"permissions_ok={fg_perms_ok}, foregroundServiceType={fg_type!r}, "
                    f"subtype_property={'set' if subtype_value else 'missing'}."
                )
            ),
        )
    )

    # 5. REQUEST_INSTALL_PACKAGES must be absent.
    rip_absent = REQUEST_INSTALL_PACKAGES not in permissions
    results.append(
        CheckResult(
            id="no_request_install_packages",
            name="REQUEST_INSTALL_PACKAGES permission absent",
            passed=rip_absent,
            required=True,
            message=(
                "android.permission.REQUEST_INSTALL_PACKAGES is not declared."
                if rip_absent
                else "android.permission.REQUEST_INSTALL_PACKAGES must NOT be declared."
            ),
        )
    )

    # 6. allowBackup=false and usesCleartextTraffic=false.
    allow_backup = (
        application.get(_a("allowBackup")) if application is not None else None
    )
    cleartext = (
        application.get(_a("usesCleartextTraffic")) if application is not None else None
    )
    backup_ok = allow_backup == "false" and cleartext == "false"
    results.append(
        CheckResult(
            id="backup_and_cleartext_disabled",
            name="allowBackup=false and usesCleartextTraffic=false",
            passed=backup_ok,
            required=True,
            message=(
                "allowBackup=false and usesCleartextTraffic=false."
                if backup_ok
                else f"allowBackup={allow_backup!r}, usesCleartextTraffic={cleartext!r} (both must be 'false')."
            ),
        )
    )

    # 7. Privacy policy reference exists.
    pp_path = _privacy_policy_reference(repo_root)
    results.append(
        CheckResult(
            id="privacy_policy_reference",
            name="Privacy policy reference exists",
            passed=pp_path is not None,
            required=True,
            message=(
                f"Privacy policy reference found at {pp_path.relative_to(repo_root)}."
                if pp_path is not None
                else "No PrivacyPolicyScreen.kt or docs/privacy-policy.html found."
            ),
        )
    )

    # 8. VpnService policy documentation exists.
    vpn_doc = repo_root / VPN_POLICY_DOC_REL
    results.append(
        CheckResult(
            id="vpn_service_policy_doc",
            name="docs/play/VPN_SERVICE_POLICY.md exists",
            passed=vpn_doc.exists(),
            required=True,
            message=(
                f"{VPN_POLICY_DOC_REL} exists."
                if vpn_doc.exists()
                else f"{VPN_POLICY_DOC_REL} is missing."
            ),
        )
    )

    # 9. Prominent-disclosure documentation section exists in that doc.
    disclosure_ok = False
    disclosure_msg = f"{VPN_POLICY_DOC_REL} is missing."
    if vpn_doc.exists():
        text = vpn_doc.read_text(encoding="utf-8").lower()
        if "prominent" in text and "disclosure" in text:
            disclosure_ok = True
            disclosure_msg = (
                f"{VPN_POLICY_DOC_REL} documents the in-app prominent disclosure."
            )
        else:
            disclosure_msg = (
                f"{VPN_POLICY_DOC_REL} must document the in-app prominent disclosure "
                "(text must mention 'prominent' and 'disclosure')."
            )
    results.append(
        CheckResult(
            id="prominent_disclosure_doc",
            name="Prominent in-app disclosure documented",
            passed=disclosure_ok,
            required=True,
            message=disclosure_msg,
        )
    )

    # 10. Data Safety mapping document exists.
    data_safety_map = repo_root / DATA_SAFETY_MAP_REL
    results.append(
        CheckResult(
            id="data_safety_map_doc",
            name="docs/play/DATA_SAFETY_MAP.md exists",
            passed=data_safety_map.exists(),
            required=True,
            message=(
                f"{DATA_SAFETY_MAP_REL} exists."
                if data_safety_map.exists()
                else f"{DATA_SAFETY_MAP_REL} is missing."
            ),
        )
    )

    return results


def main(argv: list[str] | None = None) -> int:
    default_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(
        description="Play/VPN policy static preflight gate."
    )
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=default_root,
        help="Repository root (default: %(default)s).",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        dest="as_json",
        help="Emit machine-readable JSON result list.",
    )
    args = parser.parse_args(argv)

    try:
        results = run_checks(Path(args.repo_root))
    except Exception as exc:  # noqa: BLE001
        print(f"[verify_play_policy] ERROR: {exc}", file=sys.stderr)
        return 1

    failures = [r for r in results if r.required and not r.passed]

    if args.as_json:
        payload = {
            "passed": not failures,
            "repo_root": str(args.repo_root),
            "results": [asdict(r) for r in results],
        }
        print(json.dumps(payload, indent=2))
    else:
        for r in results:
            status = "PASS" if r.passed else "FAIL"
            print(f"[{status}] {r.id}: {r.name} — {r.message}")
        print("-" * 60)
        if failures:
            print(f"Play policy gate: {len(failures)} required check(s) FAILED.")
        else:
            print("Play policy gate: PASS (all required checks satisfied).")

    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
