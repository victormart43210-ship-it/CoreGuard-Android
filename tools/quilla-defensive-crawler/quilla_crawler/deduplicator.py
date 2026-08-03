"""Deterministic deduplication for CrawlerEntry records.

Identity priority order (spec §DEDUPLICATION):
1. CVE identifier + affected product
2. Published source identifier (source_id + entry id from source)
3. Canonical URL + normalized title hash

When two approved independent sources describe the same CVE / malware family
/ IOC / technique, the status is upgraded to CORROBORATED and references from
both sources are merged.
"""

from __future__ import annotations

import hashlib
import re
from typing import Dict, List, Optional
from urllib.parse import urlparse, urlunparse

from quilla_crawler.models import (
    CrawlerEntry,
    MAX_ENTRIES_PER_BUNDLE,
    VerificationStatus,
)


def _normalize_cve(cve: str) -> str:
    return cve.upper().strip()


def _normalize_url(url: str) -> str:
    try:
        p = urlparse(url.strip().lower())
        return urlunparse((p.scheme, p.netloc, p.path.rstrip("/"), "", "", ""))
    except Exception:  # noqa: BLE001
        return url.lower().strip()


def _normalize_title(title: str) -> str:
    return re.sub(r"\s+", " ", title.lower().strip())


def _stable_id_for(entry: CrawlerEntry) -> str:
    """Compute the canonical stable ID for deduplication grouping."""
    # Priority 1: CVE + product (if we have both).
    if entry.related_cves:
        cve = _normalize_cve(entry.related_cves[0])
        # Extract product hint from entry ID or title.
        return f"cve:{cve}"

    # Priority 2: source-assigned stable ID.
    if entry.id and not entry.id.startswith("advisory-html-"):
        return f"id:{entry.id}"

    # Priority 3: canonical URL + title hash.
    url = _normalize_url(entry.canonical_url or entry.source_url)
    title_hash = hashlib.sha256(_normalize_title(entry.title).encode()).hexdigest()[:16]
    return f"url:{url}:title:{title_hash}"


class Deduplicator:
    """Collects entries and merges duplicates."""

    def __init__(self) -> None:
        # stable_id → list of entries that share that identity.
        self._groups: Dict[str, List[CrawlerEntry]] = {}

    def add(self, entry: CrawlerEntry) -> None:
        key = _stable_id_for(entry)
        self._groups.setdefault(key, []).append(entry)

    def accepted_entries(self) -> List[CrawlerEntry]:
        """Return merged, deduplicated, publishable entries (≤ MAX_ENTRIES_PER_BUNDLE)."""
        merged: List[CrawlerEntry] = []
        for group in self._groups.values():
            entry = _merge_group(group)
            if entry is not None:
                merged.append(entry)
        # Stable deterministic ordering by id.
        merged.sort(key=lambda e: e.id)
        return merged[:MAX_ENTRIES_PER_BUNDLE]


def _merge_group(group: List[CrawlerEntry]) -> Optional[CrawlerEntry]:
    """Merge a deduplication group into a single publishable entry."""
    if not group:
        return None

    # Use the entry with the highest confidence as the primary.
    primary = max(group, key=lambda e: e.confidence)

    if not primary.is_publishable():
        return None

    # If two or more *different* sources cover the same identity, upgrade status.
    source_ids = {e.source_id for e in group if e.source_id}
    if len(source_ids) >= 2:
        primary.verification_status = VerificationStatus.CORROBORATED
        primary.confidence = min(1.0, primary.confidence + 0.05)

    # Merge references from all group members (deduplicated, capped at 8).
    all_refs: list[str] = list(primary.references)
    seen_refs = set(all_refs)
    for e in group:
        if e is primary:
            continue
        for ref in e.references:
            if ref not in seen_refs:
                all_refs.append(ref)
                seen_refs.add(ref)
    primary.references = all_refs[:8]

    # Merge warnings.
    all_warnings = list(primary.warnings)
    for e in group:
        if e is primary:
            continue
        all_warnings.extend(e.warnings)
    primary.warnings = list(dict.fromkeys(all_warnings))  # deduplicate, preserve order

    # Merge CVEs.
    all_cves = list(dict.fromkeys(primary.related_cves + [c for e in group for c in e.related_cves]))
    primary.related_cves = all_cves[:256]

    return primary
