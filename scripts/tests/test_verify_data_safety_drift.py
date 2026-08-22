"""TDD tests for scripts/policy/verify_data_safety_drift.py (stdlib + pytest)."""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

from scripts.policy.verify_data_safety_drift import REQUIRED_CATEGORIES, verify

REPO_ROOT = Path(__file__).resolve().parents[2]
VALIDATOR = REPO_ROOT / "scripts" / "policy" / "verify_data_safety_drift.py"
REAL_INVENTORY = REPO_ROOT / "scripts" / "policy" / "data_safety_inventory.json"


def _entry(**overrides) -> dict:
    base = {
        "collected": True,
        "transmitted": True,
        "stored_locally": True,
        "stored_remotely": True,
        "purpose": "deliver signed threat-intel bundles from Cloud Run backend",
        "retention": "per bundle version",
        "user_control": "enabled/disabled in settings",
    }
    base.update(overrides)
    return base


def _inventory(categories: dict | None = None) -> dict:
    if categories is None:
        categories = {name: _entry() for name in REQUIRED_CATEGORIES}
    return {"schema_version": "1.0.0", "categories": categories}


def _write_inventory(path: Path, data: dict) -> Path:
    path.write_text(json.dumps(data, indent=2), encoding="utf-8")
    return path


def _check(results, cid: str) -> bool:
    for r in results:
        if r.id == cid:
            return r.passed
    raise AssertionError(f"missing check result {cid}")


def test_complete_truthful_inventory_passes(tmp_path: Path) -> None:
    inv = _write_inventory(tmp_path / "inventory.json", _inventory())
    results = verify(inv)
    failures = [r for r in results if not r.passed]
    assert not failures, [r.id for r in failures]


def test_missing_category_fails(tmp_path: Path) -> None:
    cats = {name: _entry() for name in REQUIRED_CATEGORIES}
    del cats["crash_performance_telemetry"]
    inv = _write_inventory(tmp_path / "inventory.json", _inventory(cats))
    results = verify(inv)
    assert not _check(results, "required_categories_present")


def test_contradictory_flags_fails(tmp_path: Path) -> None:
    # stored_remotely=true but transmitted=false, purpose has no "local" -> contradiction.
    cats = {name: _entry() for name in REQUIRED_CATEGORIES}
    cats["threat_intelligence_requests"] = _entry(
        transmitted=False,
        stored_remotely=True,
        purpose="deliver signed threat-intel bundles from Cloud Run backend",
    )
    inv = _write_inventory(tmp_path / "inventory.json", _inventory(cats))
    results = verify(inv)
    assert not _check(
        results, "threat_intelligence_requests.remote_implies_transmitted"
    )


def test_remote_storage_explained_passes(tmp_path: Path) -> None:
    # stored_remotely=true, transmitted=false, but purpose explains local-only remote storage.
    cats = {name: _entry() for name in REQUIRED_CATEGORIES}
    cats["device_security_findings"] = _entry(
        transmitted=False,
        stored_remotely=True,
        purpose="synced to local-only remote backup cache for offline availability",
    )
    inv = _write_inventory(tmp_path / "inventory.json", _inventory(cats))
    results = verify(inv)
    assert _check(results, "device_security_findings.remote_implies_transmitted")


def test_transmitting_uncollected_fails(tmp_path: Path) -> None:
    cats = {name: _entry() for name in REQUIRED_CATEGORIES}
    cats["billing_entitlement"] = _entry(collected=False, transmitted=True)
    inv = _write_inventory(tmp_path / "inventory.json", _inventory(cats))
    results = verify(inv)
    assert not _check(results, "billing_entitlement.transmitted_implies_collected")


def test_missing_field_fails(tmp_path: Path) -> None:
    cats = {name: _entry() for name in REQUIRED_CATEGORIES}
    entry = _entry()
    del entry["retention"]
    cats["authentication_identity"] = entry
    inv = _write_inventory(tmp_path / "inventory.json", _inventory(cats))
    results = verify(inv)
    assert not _check(results, "authentication_identity.fields_present")


def test_integration_real_inventory_passes() -> None:
    proc = subprocess.run(
        [sys.executable, str(VALIDATOR), "--inventory", str(REAL_INVENTORY)],
        capture_output=True,
        text=True,
    )
    assert proc.returncode == 0, proc.stderr + proc.stdout
    results = verify(REAL_INVENTORY)
    failures = [r for r in results if not r.passed]
    assert not failures, [r.id for r in failures]


def test_json_output(tmp_path: Path) -> None:
    inv = _write_inventory(tmp_path / "inventory.json", _inventory())
    proc = subprocess.run(
        [sys.executable, str(VALIDATOR), "--inventory", str(inv), "--json"],
        capture_output=True,
        text=True,
    )
    assert proc.returncode == 0, proc.stderr
    payload = json.loads(proc.stdout)
    assert payload["passed"] is True
    assert len(payload["results"]) > 0
