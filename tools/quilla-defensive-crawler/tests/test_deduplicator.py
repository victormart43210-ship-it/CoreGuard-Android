"""Tests for deduplicator.py — stable ID generation and provenance merging."""

from __future__ import annotations

from quilla_crawler.deduplicator import Deduplicator, _stable_id_for
from quilla_crawler.models import CrawlerEntry, VerificationStatus


def _make_entry(
    entry_id: str = "test-entry",
    title: str = "Test Entry",
    cves: list[str] | None = None,
    source_id: str = "source-a",
    status: VerificationStatus = VerificationStatus.TRUSTED_SOURCE,
    confidence: float = 0.90,
    refs: list[str] | None = None,
) -> CrawlerEntry:
    return CrawlerEntry(
        id=entry_id,
        title=title,
        category="crawler-vulnerability",
        tags=["test"],
        summary="A test entry.",
        body="Body text.",
        defense="Apply patches.",
        references=refs or ["https://www.cisa.gov/kev"],
        source_id=source_id,
        source_name=source_id,
        related_cves=cves or [],
        confidence=confidence,
        verification_status=status,
    )


class TestStableIds:
    def test_cve_based_id(self) -> None:
        entry = _make_entry(cves=["CVE-2023-20963"])
        sid = _stable_id_for(entry)
        assert sid.startswith("cve:")
        assert "CVE-2023-20963" in sid

    def test_source_id_based_id(self) -> None:
        entry = _make_entry(entry_id="kev-cve-2023-20963", cves=[])
        sid = _stable_id_for(entry)
        assert sid.startswith("id:")

    def test_url_based_id_for_html(self) -> None:
        entry = _make_entry(entry_id="advisory-html-abcdef012345", cves=[])
        entry.canonical_url = "https://example.com/advisory/1"
        sid = _stable_id_for(entry)
        assert sid.startswith("url:")


class TestDeduplication:
    def test_single_entry_accepted(self) -> None:
        d = Deduplicator()
        d.add(_make_entry("entry-1"))
        result = d.accepted_entries()
        assert len(result) == 1

    def test_same_cve_different_sources_merged(self) -> None:
        d = Deduplicator()
        d.add(
            _make_entry(
                "kev-cve-2023-20963",
                cves=["CVE-2023-20963"],
                source_id="source-a",
                refs=["https://www.cisa.gov/kev"],
            )
        )
        d.add(
            _make_entry(
                "nvd-cve-2023-20963",
                cves=["CVE-2023-20963"],
                source_id="source-b",
                refs=["https://nvd.nist.gov/vuln/detail/CVE-2023-20963"],
            )
        )
        result = d.accepted_entries()
        assert len(result) == 1
        merged = result[0]
        # Merged entry should be CORROBORATED.
        assert merged.verification_status == VerificationStatus.CORROBORATED

    def test_provenance_preserved_on_merge(self) -> None:
        d = Deduplicator()
        d.add(
            _make_entry(
                "kev-cve-2023-20963",
                cves=["CVE-2023-20963"],
                source_id="source-a",
                refs=["https://www.cisa.gov/kev"],
            )
        )
        d.add(
            _make_entry(
                "nvd-cve-2023-20963",
                cves=["CVE-2023-20963"],
                source_id="source-b",
                refs=["https://nvd.nist.gov/vuln/detail/CVE-2023-20963"],
            )
        )
        result = d.accepted_entries()
        merged = result[0]
        # Both source refs should be present.
        assert any(
            ref == "https://www.cisa.gov/kev" or ref.startswith("https://www.cisa.gov/")
            for ref in merged.references
        )
        assert any(ref.startswith("https://nvd.nist.gov/") for ref in merged.references)

    def test_unrelated_entries_not_merged(self) -> None:
        d = Deduplicator()
        d.add(
            _make_entry("entry-alpha", title="CVE-2023-11111 Android vuln", cves=["CVE-2023-11111"])
        )
        d.add(
            _make_entry("entry-beta", title="CVE-2023-22222 Samsung vuln", cves=["CVE-2023-22222"])
        )
        result = d.accepted_entries()
        assert len(result) == 2

    def test_rejected_entries_excluded(self) -> None:
        d = Deduplicator()
        d.add(_make_entry("entry-1", status=VerificationStatus.REJECTED))
        result = d.accepted_entries()
        assert len(result) == 0

    def test_unverified_entries_excluded(self) -> None:
        d = Deduplicator()
        d.add(_make_entry("entry-1", status=VerificationStatus.UNVERIFIED))
        result = d.accepted_entries()
        assert len(result) == 0

    def test_corroborated_entries_included(self) -> None:
        d = Deduplicator()
        d.add(_make_entry("entry-1", status=VerificationStatus.CORROBORATED))
        result = d.accepted_entries()
        assert len(result) == 1

    def test_output_sorted_by_id(self) -> None:
        d = Deduplicator()
        d.add(_make_entry("zzz-entry", cves=[]))
        d.add(_make_entry("aaa-entry", cves=[]))
        d.add(_make_entry("mmm-entry", cves=[]))
        result = d.accepted_entries()
        ids = [e.id for e in result]
        assert ids == sorted(ids)

    def test_max_5000_entries_respected(self) -> None:
        d = Deduplicator()
        for i in range(5100):
            d.add(_make_entry(f"entry-{i:05d}", cves=[]))
        result = d.accepted_entries()
        assert len(result) <= 5000
