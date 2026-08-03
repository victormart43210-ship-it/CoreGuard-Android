"""Bundle builder — produces a deterministic signed JSON bundle.

Output files:
    output/quilla-intel.json
    output/quilla-intel-manifest.json

The .sig file is written by signer.py after signing.

Bundle determinism:
- UTF-8
- sorted JSON keys
- compact separators (, and :)
- no NaN / Infinity
- entries ordered by id
"""

from __future__ import annotations

import hashlib
import json
import os
import uuid
from datetime import datetime, timezone
from typing import List

from quilla_crawler import __version__
from quilla_crawler.models import CrawlerEntry, MAX_ENTRIES_PER_BUNDLE

BUNDLE_SCHEMA_VERSION: int = 1
GENERATOR_NAME: str = "CoreGuard Quilla Defensive Crawler"


def build_bundle(
    entries: List[CrawlerEntry],
    output_dir: str = "output/",
) -> bytes:
    """Build the quilla-intel.json bundle and write it to disk.

    Returns the exact UTF-8 bytes of quilla-intel.json (used for signing).
    """
    if len(entries) > MAX_ENTRIES_PER_BUNDLE:
        entries = entries[:MAX_ENTRIES_PER_BUNDLE]

    # Only publishable entries.
    publishable = [e for e in entries if e.is_publishable()]
    publishable.sort(key=lambda e: e.id)

    entry_dicts = [e.to_bundle_dict() for e in publishable]

    # Deterministic entries hash.
    entries_json = _to_deterministic_bytes(entry_dicts)
    entries_sha256 = hashlib.sha256(entries_json).hexdigest()

    bundle = {
        "schema_version": BUNDLE_SCHEMA_VERSION,
        "bundle_id": str(uuid.uuid4()),
        "generated_at": datetime.now(tz=timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "generator": GENERATOR_NAME,
        "generator_version": __version__,
        "entry_count": len(publishable),
        "entries_sha256": entries_sha256,
        "entries": entry_dicts,
    }

    bundle_bytes = _to_deterministic_bytes(bundle)

    # Write bundle.
    os.makedirs(output_dir, exist_ok=True)
    bundle_path = os.path.join(output_dir, "quilla-intel.json")
    with open(bundle_path, "wb") as fh:
        fh.write(bundle_bytes)

    # Write manifest (summary without entries for quick inspection).
    manifest = {
        "schema_version": BUNDLE_SCHEMA_VERSION,
        "bundle_id": bundle["bundle_id"],
        "generated_at": bundle["generated_at"],
        "generator": GENERATOR_NAME,
        "generator_version": __version__,
        "entry_count": len(publishable),
        "entries_sha256": entries_sha256,
        "bundle_sha256": hashlib.sha256(bundle_bytes).hexdigest(),
    }
    manifest_path = os.path.join(output_dir, "quilla-intel-manifest.json")
    with open(manifest_path, "wb") as fh:
        fh.write(_to_deterministic_bytes(manifest))

    return bundle_bytes


def _to_deterministic_bytes(obj: object) -> bytes:
    """Serialize to deterministic UTF-8 JSON bytes."""
    return json.dumps(
        obj,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    ).encode("utf-8")


def load_bundle_bytes(bundle_path: str) -> bytes:
    """Load bundle bytes for signature verification."""
    with open(bundle_path, "rb") as fh:
        return fh.read()
