#!/usr/bin/env python3
"""Ingest legal/open-source threat intelligence fixtures with provenance and integrity."""

from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from scripts.threat_intel.validate_configs import validate_all

DEFAULT_OUTPUT_DIR = REPO_ROOT / "security" / "threat-intel" / "artifacts"
ALLOWLIST_PATH = REPO_ROOT / "security" / "threat-intel" / "v1" / "sources.allowlist.v1.json"
RULES_PATH = REPO_ROOT / "security" / "threat-intel" / "v1" / "detection.rules.v1.json"
FIXTURES_DIR = REPO_ROOT / "security" / "threat-intel" / "fixtures"


@dataclass(frozen=True)
class IngestedRecord:
    source_id: str
    source_url: str
    advisory_id: str
    title: str
    published_at: str
    collected_at: str


def _read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _sha256_bytes(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _timestamp(deterministic: bool) -> str:
    if deterministic:
        return "2026-01-01T00:00:00Z"
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def build_bundle(deterministic: bool = False) -> dict[str, Any]:
    validate_all()
    allowlist = _read_json(ALLOWLIST_PATH)

    allowed_sources = {src["id"]: src for src in allowlist["allowed_sources"]}
    collected_at = _timestamp(deterministic)

    source_manifests: list[dict[str, Any]] = []
    records: list[dict[str, Any]] = []

    for source_id, source in sorted(allowed_sources.items()):
        fixture_path = FIXTURES_DIR / f"{source_id}.json"
        fixture_raw = fixture_path.read_bytes()
        fixture = json.loads(fixture_raw.decode("utf-8"))

        advisories = fixture.get("records", [])
        for advisory in advisories:
            record = IngestedRecord(
                source_id=source_id,
                source_url=source["url"],
                advisory_id=str(advisory.get("advisory_id", "")).strip(),
                title=str(advisory.get("title", "")).strip(),
                published_at=str(advisory.get("published_at", "")).strip(),
                collected_at=collected_at,
            )
            if not record.advisory_id or not record.title:
                continue
            records.append(record.__dict__)

        source_manifests.append(
            {
                "source_id": source_id,
                "source_name": source["name"],
                "source_url": source["url"],
                "fetched_at": fixture.get("fetched_at", collected_at),
                "fixture_sha256": _sha256_bytes(fixture_raw),
                "record_count": len(advisories),
            }
        )

    rules_hash = _sha256_bytes(RULES_PATH.read_bytes())
    records.sort(key=lambda r: (r["source_id"], r["advisory_id"]))

    return {
        "bundle_version": "1.0.0",
        "bundle_id": "coreguard-threat-intel-open-sources",
        "created_at": collected_at,
        "allowlist_policy": ALLOWLIST_PATH.name,
        "ruleset": RULES_PATH.name,
        "ruleset_sha256": rules_hash,
        "sources": source_manifests,
        "records": records,
    }


def _write_integrity_files(bundle_bytes: bytes, output_path: Path, hmac_key: str | None) -> None:
    sha256_path = output_path.with_suffix(output_path.suffix + ".sha256")
    sha256_path.write_text(_sha256_bytes(bundle_bytes) + "\n", encoding="utf-8")

    sig_path = output_path.with_suffix(output_path.suffix + ".sig")
    if hmac_key:
        signature = hmac.new(hmac_key.encode("utf-8"), bundle_bytes, hashlib.sha256).hexdigest()
        sig_path.write_text(signature + "\n", encoding="utf-8")
    else:
        sig_path.write_text("UNSIGNED\n", encoding="utf-8")


def write_bundle(output_dir: Path, deterministic: bool = False, hmac_key: str | None = None) -> Path:
    bundle = build_bundle(deterministic=deterministic)
    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / "open-threat-intel.bundle.v1.json"
    bundle_bytes = json.dumps(bundle, indent=2, sort_keys=True).encode("utf-8") + b"\n"
    output_path.write_bytes(bundle_bytes)
    _write_integrity_files(bundle_bytes, output_path, hmac_key)
    return output_path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--deterministic", action="store_true", help="Use a fixed timestamp for reproducible output")
    parser.add_argument("--hmac-key", default="", help="Optional HMAC key for detached .sig file")
    args = parser.parse_args()

    output_path = write_bundle(
        output_dir=args.output_dir,
        deterministic=args.deterministic,
        hmac_key=args.hmac_key.strip() or None,
    )
    print(f"[ingest_open_sources] wrote {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
