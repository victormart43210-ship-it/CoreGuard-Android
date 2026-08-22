#!/usr/bin/env python3
"""Detect drift in the CoreGuard Data Safety inventory.

Loads scripts/policy/data_safety_inventory.json and fails if any required
category is missing or if a category has contradictory flags. Stdlib only
(json, pathlib, argparse). No third-party imports.

Run from the repo root::

    python3 scripts/policy/verify_data_safety_drift.py
    python3 scripts/policy/verify_data_safety_drift.py --json
    python3 scripts/policy/verify_data_safety_drift.py --inventory /path/to.json
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

DEFAULT_INVENTORY_REL = Path("scripts/policy/data_safety_inventory.json")

REQUIRED_CATEGORIES = [
    "device_security_findings",
    "app_package_metadata",
    "network_security_observations",
    "crash_performance_telemetry",
    "authentication_identity",
    "billing_entitlement",
    "threat_intelligence_requests",
    "quilla_inputs_outputs",
]

REQUIRED_FIELDS = [
    "collected",
    "transmitted",
    "stored_locally",
    "stored_remotely",
    "purpose",
    "retention",
    "user_control",
]

BOOL_FIELDS = {"collected", "transmitted", "stored_locally", "stored_remotely"}


@dataclass
class CheckResult:
    id: str
    name: str
    passed: bool
    message: str


def _default_inventory_path() -> Path:
    repo_root = Path(__file__).resolve().parents[2]
    return repo_root / DEFAULT_INVENTORY_REL


def _load_inventory(path: Path) -> dict[str, Any]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("inventory must be a JSON object")
    return data


def _check_category(name: str, entry: Any) -> list[CheckResult]:
    results: list[CheckResult] = []
    base = f"category '{name}'"

    if not isinstance(entry, dict):
        results.append(
            CheckResult(
                id=f"{name}.shape",
                name=f"{base} is an object",
                passed=False,
                message=f"{base} must be a JSON object, got {type(entry).__name__}.",
            )
        )
        return results

    # Required fields present.
    missing = [f for f in REQUIRED_FIELDS if f not in entry]
    results.append(
        CheckResult(
            id=f"{name}.fields_present",
            name=f"{base} required fields present",
            passed=not missing,
            message=(
                "all required fields present"
                if not missing
                else f"missing fields: {', '.join(missing)}"
            ),
        )
    )
    if missing:
        return results

    # Boolean fields are booleans.
    bad_bools = [f for f in BOOL_FIELDS if not isinstance(entry[f], bool)]
    results.append(
        CheckResult(
            id=f"{name}.bool_types",
            name=f"{base} boolean fields are booleans",
            passed=not bad_bools,
            message=(
                "all boolean fields are booleans"
                if not bad_bools
                else f"non-boolean fields: {', '.join(bad_bools)}"
            ),
        )
    )

    collected = entry["collected"]
    transmitted = entry["transmitted"]
    stored_remotely = entry["stored_remotely"]
    purpose = str(entry["purpose"])

    # Contradiction 1: transmitting data that is not collected.
    results.append(
        CheckResult(
            id=f"{name}.transmitted_implies_collected",
            name=f"{base}: transmitted implies collected",
            passed=not (transmitted and not collected),
            message=(
                "ok"
                if not (transmitted and not collected)
                else "transmitted=true but collected=false — cannot transmit uncollected data."
            ),
        )
    )

    # Contradiction 2: storing remotely data that is not collected.
    results.append(
        CheckResult(
            id=f"{name}.remote_implies_collected",
            name=f"{base}: stored_remotely implies collected",
            passed=not (stored_remotely and not collected),
            message=(
                "ok"
                if not (stored_remotely and not collected)
                else "stored_remotely=true but collected=false — cannot store uncollected data remotely."
            ),
        )
    )

    # Contradiction 3: stored remotely but never transmitted. Allowed only when
    # the purpose field explains local-only remote storage.
    contradicts = stored_remotely and not transmitted
    explains = "local" in purpose.lower()
    results.append(
        CheckResult(
            id=f"{name}.remote_implies_transmitted",
            name=f"{base}: stored_remotely implies transmitted (or explained)",
            passed=not contradicts or explains,
            message=(
                "ok"
                if not contradicts or explains
                else "stored_remotely=true but transmitted=false — purpose must explain local-only remote storage."
            ),
        )
    )

    return results


def verify(inventory_path: Path) -> list[CheckResult]:
    data = _load_inventory(inventory_path)
    categories = data.get("categories")
    results: list[CheckResult] = []

    results.append(
        CheckResult(
            id="categories_object",
            name="inventory has a 'categories' object",
            passed=isinstance(categories, dict),
            message=(
                "ok"
                if isinstance(categories, dict)
                else "top-level 'categories' must be an object"
            ),
        )
    )
    if not isinstance(categories, dict):
        return results

    # Required categories present.
    missing = [c for c in REQUIRED_CATEGORIES if c not in categories]
    results.append(
        CheckResult(
            id="required_categories_present",
            name="all required categories present",
            passed=not missing,
            message=(
                "all 8 required categories present"
                if not missing
                else f"missing categories: {', '.join(missing)}"
            ),
        )
    )

    # Per-category checks.
    for name in REQUIRED_CATEGORIES:
        if name in categories:
            results.extend(_check_category(name, categories[name]))

    return results


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Data Safety inventory drift gate.")
    parser.add_argument(
        "--inventory",
        type=Path,
        default=_default_inventory_path(),
        help="Path to data_safety_inventory.json (default: %(default)s).",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        dest="as_json",
        help="Emit machine-readable JSON result list.",
    )
    args = parser.parse_args(argv)

    try:
        results = verify(Path(args.inventory))
    except Exception as exc:  # noqa: BLE001
        print(f"[verify_data_safety_drift] ERROR: {exc}", file=sys.stderr)
        return 1

    failures = [r for r in results if not r.passed]

    if args.as_json:
        payload = {
            "passed": not failures,
            "inventory": str(args.inventory),
            "results": [asdict(r) for r in results],
        }
        print(json.dumps(payload, indent=2))
    else:
        for r in results:
            status = "PASS" if r.passed else "FAIL"
            print(f"[{status}] {r.id}: {r.name} — {r.message}")
        print("-" * 60)
        if failures:
            print(f"Data Safety drift gate: {len(failures)} check(s) FAILED.")
        else:
            print("Data Safety drift gate: PASS (inventory consistent and complete).")

    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
