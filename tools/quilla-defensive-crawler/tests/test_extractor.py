"""Tests for extractor.py — CISA KEV, MISP, and generic HTML parsers."""

from __future__ import annotations

import os

from quilla_crawler.config import SourceConfig, TrustLevel
from quilla_crawler.extractor import extract_entries
from quilla_crawler.models import FetchResult, VerificationStatus

_FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures")


def _kev_source() -> SourceConfig:
    return SourceConfig(
        id="cisa-kev",
        name="CISA KEV",
        seed_urls=[
            "https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json"
        ],
        allowed_hosts=["www.cisa.gov"],
        allowed_path_prefixes=["/sites/default/files/feeds/"],
        content_types=["application/json"],
        max_depth=0,
        max_pages=1,
        requests_per_minute=6,
        trust_level=TrustLevel.TRUSTED_SOURCE,
        parser="cisa_kev",
    )


def _misp_source() -> SourceConfig:
    return SourceConfig(
        id="misp-android-galaxy",
        name="MISP Android Galaxy",
        seed_urls=["https://raw.githubusercontent.com/MISP/misp-galaxy/main/clusters/android.json"],
        allowed_hosts=["raw.githubusercontent.com"],
        allowed_path_prefixes=["/MISP/misp-galaxy/main/clusters/"],
        content_types=["application/json", "text/plain"],
        max_depth=0,
        max_pages=1,
        requests_per_minute=10,
        trust_level=TrustLevel.TRUSTED_SOURCE,
        parser="misp_android",
    )


def _html_source() -> SourceConfig:
    return SourceConfig(
        id="android-security-bulletin",
        name="Android Security Bulletin",
        seed_urls=["https://source.android.com/docs/security/bulletin"],
        allowed_hosts=["source.android.com"],
        allowed_path_prefixes=["/docs/security/bulletin"],
        content_types=["text/html"],
        max_depth=1,
        max_pages=10,
        requests_per_minute=6,
        trust_level=TrustLevel.TRUSTED_SOURCE,
        parser="generic_advisory_html",
    )


class TestCisaKevParser:
    def test_android_entries_are_extracted(self) -> None:
        with open(os.path.join(_FIXTURES, "cisa_kev_sample.json")) as f:
            body = f.read()
        result = FetchResult(
            url="https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json",
            final_url="https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json",
            http_status=200,
            content_type="application/json",
            bytes_received=len(body),
            body=body,
        )
        entries = extract_entries(result, _kev_source())
        # Should have 2 Android/mobile entries (Android Framework and Samsung Galaxy)
        assert len(entries) == 2
        ids = {e.id for e in entries}
        assert "kev-cve-2023-20963" in ids
        assert "kev-cve-2023-21492" in ids

    def test_desktop_only_entries_filtered(self) -> None:
        with open(os.path.join(_FIXTURES, "cisa_kev_sample.json")) as f:
            body = f.read()
        result = FetchResult(
            url="https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json",
            final_url="https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json",
            http_status=200,
            content_type="application/json",
            bytes_received=len(body),
            body=body,
        )
        entries = extract_entries(result, _kev_source())
        # Toaster CVE must not be included
        assert not any("1999-0001" in e.id for e in entries)

    def test_entries_are_trusted_source_status(self) -> None:
        with open(os.path.join(_FIXTURES, "cisa_kev_sample.json")) as f:
            body = f.read()
        result = FetchResult(
            url="https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json",
            final_url="https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json",
            http_status=200,
            content_type="application/json",
            bytes_received=len(body),
            body=body,
        )
        entries = extract_entries(result, _kev_source())
        for e in entries:
            assert e.verification_status == VerificationStatus.TRUSTED_SOURCE

    def test_references_include_cisa_and_nvd(self) -> None:
        with open(os.path.join(_FIXTURES, "cisa_kev_sample.json")) as f:
            body = f.read()
        result = FetchResult(
            url="https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json",
            final_url="https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json",
            http_status=200,
            content_type="application/json",
            bytes_received=len(body),
            body=body,
        )
        entries = extract_entries(result, _kev_source())
        kev = next(e for e in entries if "cve-2023-20963" in e.id)
        assert any(ref.startswith("https://www.cisa.gov/") for ref in kev.references)
        assert any(ref.startswith("https://nvd.nist.gov/") for ref in kev.references)

    def test_empty_json_returns_empty_list(self) -> None:
        result = FetchResult(
            url="https://www.cisa.gov/sites/default/files/feeds/kev.json",
            final_url="https://www.cisa.gov/sites/default/files/feeds/kev.json",
            http_status=200,
            content_type="application/json",
            body='{"vulnerabilities":[]}',
        )
        assert extract_entries(result, _kev_source()) == []

    def test_invalid_json_returns_empty_list(self) -> None:
        result = FetchResult(
            url="https://www.cisa.gov/sites/default/files/feeds/kev.json",
            final_url="https://www.cisa.gov/sites/default/files/feeds/kev.json",
            http_status=200,
            content_type="application/json",
            body="not json {{",
        )
        assert extract_entries(result, _kev_source()) == []


class TestMispAndroidParser:
    def test_family_briefs_extracted(self) -> None:
        with open(os.path.join(_FIXTURES, "misp_android_sample.json")) as f:
            body = f.read()
        result = FetchResult(
            url="https://raw.githubusercontent.com/MISP/misp-galaxy/main/clusters/android.json",
            final_url="https://raw.githubusercontent.com/MISP/misp-galaxy/main/clusters/android.json",
            http_status=200,
            content_type="application/json",
            bytes_received=len(body),
            body=body,
        )
        entries = extract_entries(result, _misp_source())
        assert len(entries) == 2
        ids = {e.id for e in entries}
        assert "misp-android-copycat" in ids
        assert "misp-android-flubot" in ids

    def test_body_contains_description(self) -> None:
        with open(os.path.join(_FIXTURES, "misp_android_sample.json")) as f:
            body = f.read()
        result = FetchResult(
            url="https://raw.githubusercontent.com/MISP/misp-galaxy/main/clusters/android.json",
            final_url="https://raw.githubusercontent.com/MISP/misp-galaxy/main/clusters/android.json",
            http_status=200,
            content_type="application/json",
            bytes_received=len(body),
            body=body,
        )
        entries = extract_entries(result, _misp_source())
        copycat = next(e for e in entries if "copycat" in e.id)
        assert "roots" in copycat.body.lower() or "persistency" in copycat.body.lower()

    def test_synonyms_in_tags(self) -> None:
        with open(os.path.join(_FIXTURES, "misp_android_sample.json")) as f:
            body = f.read()
        result = FetchResult(
            url="https://raw.githubusercontent.com/MISP/misp-galaxy/main/clusters/android.json",
            final_url="https://raw.githubusercontent.com/MISP/misp-galaxy/main/clusters/android.json",
            http_status=200,
            content_type="application/json",
            bytes_received=len(body),
            body=body,
        )
        entries = extract_entries(result, _misp_source())
        copycat = next(e for e in entries if "copycat" in e.id)
        assert "copy-cat" in copycat.tags


class TestGenericAdvisoryHtmlParser:
    def test_extracts_title_from_h1(self) -> None:
        with open(os.path.join(_FIXTURES, "advisory_html_sample.html")) as f:
            body = f.read()
        result = FetchResult(
            url="https://source.android.com/docs/security/bulletin/2023-06-01",
            final_url="https://source.android.com/docs/security/bulletin/2023-06-01",
            http_status=200,
            content_type="text/html",
            bytes_received=len(body),
            body=body,
        )
        entries = extract_entries(result, _html_source())
        assert len(entries) == 1
        assert "CVE-2023-99999" in entries[0].title or "WebView" in entries[0].title

    def test_script_content_removed_from_body(self) -> None:
        with open(os.path.join(_FIXTURES, "advisory_html_sample.html")) as f:
            body = f.read()
        result = FetchResult(
            url="https://source.android.com/docs/security/bulletin/2023-06-01",
            final_url="https://source.android.com/docs/security/bulletin/2023-06-01",
            http_status=200,
            content_type="text/html",
            bytes_received=len(body),
            body=body,
        )
        entries = extract_entries(result, _html_source())
        assert len(entries) == 1
        body_text = entries[0].body
        assert "alert(" not in body_text
        assert "<script>" not in body_text

    def test_cves_extracted_to_related(self) -> None:
        with open(os.path.join(_FIXTURES, "advisory_html_sample.html")) as f:
            body = f.read()
        result = FetchResult(
            url="https://source.android.com/docs/security/bulletin/2023-06-01",
            final_url="https://source.android.com/docs/security/bulletin/2023-06-01",
            http_status=200,
            content_type="text/html",
            bytes_received=len(body),
            body=body,
        )
        entries = extract_entries(result, _html_source())
        assert len(entries) == 1
        assert "CVE-2023-99999" in entries[0].related_cves
