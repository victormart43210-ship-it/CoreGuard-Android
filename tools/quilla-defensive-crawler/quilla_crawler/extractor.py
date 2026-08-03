"""Extraction / parser adapters for each approved source type.

Implemented parsers:
1. cisa_kev       — structured CISA KEV JSON
2. generic_advisory_html — generic advisory HTML page
3. misp_android   — MISP Android/Malpedia galaxy JSON
"""

from __future__ import annotations

import hashlib
import json
import re
from datetime import datetime, timezone
from typing import Dict, List, Optional
from urllib.parse import urlparse

from bs4 import BeautifulSoup, Tag

from quilla_crawler.classifier import classify_entry
from quilla_crawler.config import SourceConfig, TrustLevel
from quilla_crawler.models import CrawlerEntry, FetchResult, VerificationStatus
from quilla_crawler.sanitizer import sanitize_html, sanitize_text, sanitize_entry_fields

PARSER_VERSION: str = "1.0"

# Mobile/Android relevance pattern (whole-word match).
_MOBILE_RE: re.Pattern[str] = re.compile(
    r"(?<![a-z0-9])"
    r"(android|chromium|chrome|webkit|samsung|qualcomm|mediatek|pixel|mobile|"
    r"iphone|ipad|ios|webview|bluetooth|wifi|modem|baseband|aosp)"
    r"(?![a-z0-9])",
    re.IGNORECASE,
)

# CVE identifier pattern.
_CVE_RE: re.Pattern[str] = re.compile(r"\bCVE-\d{4}-\d{4,}\b", re.IGNORECASE)

# MITRE ATT&CK Mobile technique pattern.
_MITRE_RE: re.Pattern[str] = re.compile(r"\bT\d{4}(?:\.\d{3})?\b")

# Offensive content that must not appear in the Quilla body.
_OFFENSIVE_RE: re.Pattern[str] = re.compile(
    r"(?i)(reverse[\s\-_]shell|bind[\s\-_]shell|meterpreter|"
    r"metasploit|payload[\s\-_]build|exploit[\s\-_]code|"
    r"proof[\s\-_]of[\s\-_]concept|weaponi[sz]|persistence[\s\-_]command|"
    r"credential[\s\-_]theft|evasion[\s\-_]technique|"
    r"exfiltrat[ei]|lateral[\s\-_]movement[\s\-_]command)"
)


def extract_entries(result: FetchResult, source: SourceConfig) -> List[CrawlerEntry]:
    """Dispatch to the correct parser based on source.parser."""
    parser = source.parser
    if parser == "cisa_kev":
        return _parse_cisa_kev(result, source)
    elif parser == "misp_android":
        return _parse_misp_android(result, source)
    elif parser == "generic_advisory_html":
        return _parse_generic_advisory_html(result, source)
    else:
        return []


# ── CISA KEV parser ───────────────────────────────────────────────────────────

def _parse_cisa_kev(result: FetchResult, source: SourceConfig) -> List[CrawlerEntry]:
    try:
        data = json.loads(result.body)
    except (json.JSONDecodeError, ValueError):
        return []

    vulns = data.get("vulnerabilities", [])
    if not isinstance(vulns, list):
        return []

    entries: List[CrawlerEntry] = []
    doc_sha = hashlib.sha256(result.body.encode("utf-8")).hexdigest()

    for vuln in vulns:
        if not isinstance(vuln, dict):
            continue
        if len(vuln) > 32:
            continue  # Field limit guard

        cve = str(vuln.get("cveID", "")).strip().upper()
        vendor = str(vuln.get("vendorProject", "")).strip()
        product = str(vuln.get("product", "")).strip()
        vuln_name = str(vuln.get("vulnerabilityName", "")).strip()
        description = str(vuln.get("shortDescription", "")).strip()
        date_added = str(vuln.get("dateAdded", "")).strip()
        required_action = str(vuln.get("requiredAction", "")).strip()
        due_date = str(vuln.get("dueDate", "")).strip()
        ransomware = str(vuln.get("knownRansomwareCampaignUse", "Unknown")).strip()
        notes = str(vuln.get("notes", "")).strip()

        # Filter: Android / mobile relevance.
        blob = f"{vuln_name} {vendor} {product} {description}".lower()
        if not _MOBILE_RE.search(blob):
            continue

        # Sanitize fields.
        title_raw = f"{cve} — {vuln_name}" if cve else vuln_name or "Known Exploited Vulnerability"
        body_raw = (
            f"{description}\n\nVendor/product: {vendor} / {product}.\n"
            f"Date added: {date_added}. Required action: {required_action}.\n"
            f"Due date: {due_date}. Ransomware use: {ransomware}.\n"
            f"Notes: {notes}" if notes else (
                f"{description}\n\nVendor/product: {vendor} / {product}.\n"
                f"Date added: {date_added}. Required action: {required_action}.\n"
                f"Due date: {due_date}. Ransomware use: {ransomware}."
            )
        )
        defense_raw = (
            "Apply vendor patches promptly. On Android: install OS/security updates, "
            "remove abandoned apps, re-run Nemesis after updates. "
            "Treat CISA KEV entries as requiring immediate remediation."
        )

        title, summary, body, defense, warnings = sanitize_entry_fields(
            title=title_raw,
            summary="CISA Known Exploited Vulnerability — actively abused in the wild.",
            body=body_raw,
            defense=defense_raw,
        )
        if not title:
            continue

        entry_id = f"kev-{cve.lower()}" if cve else f"kev-row-{hashlib.sha256(vuln_name.encode()).hexdigest()[:8]}"

        # Remove offensive content patterns from body.
        body = _redact_offensive(body, warnings)

        refs: List[str] = ["https://www.cisa.gov/known-exploited-vulnerabilities-catalog"]
        if cve:
            refs.append(f"https://nvd.nist.gov/vuln/detail/{cve}")

        confidence = _base_confidence(source.trust_level)
        if date_added:
            pass  # has date, no penalty
        else:
            confidence -= 0.05
            warnings.append("missing publication date")

        confidence = max(0.0, min(1.0, confidence - 0.10 * warnings.count("offensive content removed")))

        published = _parse_date(date_added)
        entry = CrawlerEntry(
            id=entry_id,
            title=title,
            category="crawler-vulnerability",
            tags=_build_tags(["cisa", "kev", "vulnerability", "patch", "android", "mobile",
                               cve.lower(), vendor.lower(), product.lower()]),
            summary=summary,
            body=body,
            defense=defense,
            references=refs,
            source_id=source.id,
            source_name=source.name,
            source_url=result.url,
            canonical_url=result.final_url or result.url,
            published_at=published,
            confidence=max(0.0, min(1.0, confidence)),
            verification_status=_trust_to_status(source.trust_level),
            source_document_sha256=doc_sha,
            parser_version=PARSER_VERSION,
            warnings=warnings,
            related_cves=[cve] if cve else [],
        )
        classify_entry(entry)
        entries.append(entry)

    return entries


# ── MISP Android / Malpedia parser ─────────────────────────────────────────────

def _parse_misp_android(result: FetchResult, source: SourceConfig) -> List[CrawlerEntry]:
    """Parse MISP Android or Malpedia galaxy JSON."""
    # Handle possible content-type negotiation — raw.githubusercontent serves text/plain.
    body = result.body.strip()
    try:
        data = json.loads(body)
    except (json.JSONDecodeError, ValueError):
        return []

    values = data.get("values", [])
    if not isinstance(values, list):
        return []

    entries: List[CrawlerEntry] = []
    doc_sha = hashlib.sha256(result.body.encode("utf-8")).hexdigest()

    for obj in values:
        if not isinstance(obj, dict):
            continue

        name = str(obj.get("value", "")).strip()
        if not name:
            continue
        description = str(obj.get("description", "")).strip()
        meta = obj.get("meta") or {}
        if not isinstance(meta, dict):
            meta = {}

        synonyms: List[str] = []
        raw_syns = meta.get("synonyms", [])
        if isinstance(raw_syns, list):
            synonyms = [str(s).strip() for s in raw_syns if str(s).strip()]

        refs_raw: List[str] = []
        raw_refs = meta.get("refs", [])
        if isinstance(raw_refs, list):
            for r in raw_refs:
                ref = str(r).strip()
                if ref.startswith("https://"):
                    refs_raw.append(ref)

        # Mobile relevance check for Malpedia (not needed for Android galaxy).
        if "malpedia" in source.id:
            blob = f"{name} {description} {' '.join(synonyms)}".lower()
            if not (_MOBILE_RE.search(blob)):
                continue

        slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
        prefix = "malpedia" if "malpedia" in source.id else "misp-android"
        entry_id = f"{prefix}-{slug}"

        title_raw = (
            f"Malware family (Malpedia): {name}"
            if "malpedia" in source.id
            else f"Android malware family: {name}"
        )
        body_raw = description or f"Defensive family brief: {name}."
        defense_raw = (
            "Treat family IOCs as detection context, not proof of infection. "
            "Run Nemesis scanner, review suspicious apps/permissions, update the OS."
        )

        title, summary, body, defense, warnings = sanitize_entry_fields(
            title=title_raw,
            summary="Open-source MISP defensive family brief for defenders.",
            body=body_raw,
            defense=defense_raw,
        )
        if not title:
            continue

        body = _redact_offensive(body, warnings)

        syn_tags = [s.lower() for s in synonyms]
        tags = _build_tags(
            ["android", "malware", "misp", "family", name.lower()] + syn_tags
        )

        refs: List[str] = [
            "https://www.misp-galaxy.org/android"
            if "android" in source.id
            else "https://malpedia.caad.fkie.fraunhofer.de/"
        ]
        refs.extend(refs_raw[:7])

        confidence = _base_confidence(source.trust_level)
        if not description:
            confidence -= 0.05

        entry = CrawlerEntry(
            id=entry_id,
            title=title,
            category="crawler-malware",
            tags=tags,
            summary=summary,
            body=body,
            defense=defense,
            references=refs[:8],
            source_id=source.id,
            source_name=source.name,
            source_url=result.url,
            canonical_url=result.final_url or result.url,
            confidence=max(0.0, min(1.0, confidence)),
            verification_status=_trust_to_status(source.trust_level),
            source_document_sha256=doc_sha,
            parser_version=PARSER_VERSION,
            warnings=warnings,
        )
        classify_entry(entry)
        entries.append(entry)

    return entries


# ── Generic advisory HTML parser ───────────────────────────────────────────────

def _parse_generic_advisory_html(result: FetchResult, source: SourceConfig) -> List[CrawlerEntry]:
    """Extract a single advisory entry from an HTML page."""
    text, warnings = sanitize_html(result.body)
    if not text.strip():
        return []

    soup = BeautifulSoup(result.body, "lxml")

    # Title.
    h1 = soup.find("h1")
    title_raw = h1.get_text(strip=True) if h1 else ""
    if not title_raw:
        title_tag = soup.find("title")
        title_raw = title_tag.get_text(strip=True) if title_tag else ""
    if not title_raw:
        return []

    # CVEs.
    cves = sorted(set(_CVE_RE.findall(text)))

    # MITRE techniques.
    mitre = sorted(set(_MITRE_RE.findall(text)))

    # Summary — first non-trivial paragraph.
    summary_raw = ""
    for p in soup.find_all("p"):
        t = p.get_text(strip=True)
        if len(t) >= 80:
            summary_raw = t[:200]
            break

    # Body — sanitized full text (capped).
    body_raw = text[:8000]

    # Defense — look for mitigation/remediation sections.
    defense_raw = _extract_section(text, ["mitigation", "remediation", "solution", "recommendation"])

    title, summary, body, defense, field_warnings = sanitize_entry_fields(
        title=title_raw,
        summary=summary_raw,
        body=body_raw,
        defense=defense_raw,
    )
    warnings.extend(field_warnings)

    if not title:
        return []

    body = _redact_offensive(body, warnings)

    # Stable ID: CVE + hostname, or URL hash.
    if cves:
        entry_id = f"advisory-{cves[0].lower()}"
    else:
        url_hash = hashlib.sha256(result.final_url.encode()).hexdigest()[:12]
        entry_id = f"advisory-html-{url_hash}"

    doc_sha = hashlib.sha256(result.body.encode("utf-8")).hexdigest()

    # Confidence: generic HTML extraction gets -0.10.
    confidence = _base_confidence(source.trust_level) - 0.10
    if warnings:
        confidence -= 0.05 * min(len(warnings), 3)

    entry = CrawlerEntry(
        id=entry_id,
        title=title,
        category="crawler-vulnerability",
        tags=_build_tags(["advisory"] + [c.lower() for c in cves]),
        summary=summary,
        body=body,
        defense=defense,
        references=[result.final_url or result.url],
        source_id=source.id,
        source_name=source.name,
        source_url=result.url,
        canonical_url=result.final_url or result.url,
        confidence=max(0.0, min(1.0, confidence)),
        verification_status=_trust_to_status(source.trust_level),
        source_document_sha256=doc_sha,
        parser_version=PARSER_VERSION,
        warnings=warnings,
        related_cves=cves[:32],
        mitre_techniques=mitre[:16],
    )
    classify_entry(entry)
    return [entry]


# ── Helpers ───────────────────────────────────────────────────────────────────

def _extract_section(text: str, keywords: List[str]) -> str:
    """Extract a section from plain text following a keyword heading."""
    lines = text.splitlines()
    result_lines: List[str] = []
    capturing = False
    for line in lines:
        lower = line.lower().strip()
        if any(lower.startswith(kw) or lower == kw for kw in keywords):
            capturing = True
            continue
        if capturing:
            if lower and any(
                lower.startswith(h) for h in
                ["overview", "background", "description", "summary", "introduction",
                 "technical", "impact", "references", "ioc", "indicator"]
            ):
                break
            result_lines.append(line)
            if len("\n".join(result_lines)) >= 4000:
                break
    return "\n".join(result_lines).strip()


def _redact_offensive(body: str, warnings: list[str]) -> str:
    """Remove lines containing offensive procedure patterns."""
    lines = body.splitlines()
    clean: List[str] = []
    for line in lines:
        if _OFFENSIVE_RE.search(line):
            warnings.append(f"offensive content removed: {line[:80]!r}")
        else:
            clean.append(line)
    return "\n".join(clean)


def _base_confidence(trust_level: TrustLevel) -> float:
    return 0.90 if trust_level == TrustLevel.TRUSTED_SOURCE else 0.75


def _trust_to_status(trust_level: TrustLevel) -> VerificationStatus:
    return (
        VerificationStatus.TRUSTED_SOURCE
        if trust_level == TrustLevel.TRUSTED_SOURCE
        else VerificationStatus.CORROBORATED
    )


def _build_tags(items: List[str]) -> List[str]:
    seen: Dict[str, None] = {}
    for item in items:
        t = item.strip().lower()
        if t and len(t) <= 64:
            seen[t] = None
    return list(seen.keys())[:64]


def _parse_date(date_str: str) -> Optional[datetime]:
    if not date_str:
        return None
    for fmt in ("%Y-%m-%d", "%Y-%m-%dT%H:%M:%SZ", "%Y-%m-%dT%H:%M:%S"):
        try:
            return datetime.strptime(date_str, fmt).replace(tzinfo=timezone.utc)
        except ValueError:
            continue
    return None
