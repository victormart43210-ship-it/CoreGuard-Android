"""Deterministic classifier for CrawlerEntry records.

Assigns category and defensive tags based on content signals.
No external LLM — fully deterministic and testable.

Categories:
    crawler-vulnerability
    crawler-malware
    crawler-spyware
    crawler-stalkerware
    crawler-android-hardening
    crawler-network-defense
    crawler-incident-response
    crawler-mitre-mobile
    crawler-ioc-context
"""

from __future__ import annotations

import re
from typing import List

from quilla_crawler.models import CrawlerEntry

# Keyword → category mappings. First match wins.
_CATEGORY_RULES: list[tuple[re.Pattern[str], str]] = [
    # Stalkerware first (more specific than spyware)
    (re.compile(r"\b(stalkerware|stalkware|monitoring\s+app|spouseware)\b", re.I), "crawler-stalkerware"),
    # Spyware
    (re.compile(r"\b(spyware|pegasus|nso\s+group|paragon|cytrox|predator|hermit|triton)\b", re.I), "crawler-spyware"),
    # MITRE Mobile techniques
    (re.compile(r"\b(T\d{4}(\.\d{3})?|mitre|att&ck|mobile\s+technique)\b", re.I), "crawler-mitre-mobile"),
    # Android hardening
    (re.compile(r"\b(hardening|security\s+baseline|scap|benchmark|cis\s+android|masvs|mastg)\b", re.I), "crawler-android-hardening"),
    # Incident response
    (re.compile(r"\b(incident\s+response|forensic|mvt|ioc\s+collection|artifact)\b", re.I), "crawler-incident-response"),
    # Network defense
    (re.compile(r"\b(network\s+defense|firewall|ids|ips|dns\s+security|tls\s+inspection)\b", re.I), "crawler-network-defense"),
    # IOC context
    (re.compile(r"\b(ioc|indicator\s+of\s+compromise|hash|sha256|md5|ip\s+address|domain\s+ioc)\b", re.I), "crawler-ioc-context"),
    # Malware
    (re.compile(r"\b(malware|trojan|ransomware|rootkit|botnet|rat|banker|dropper)\b", re.I), "crawler-malware"),
    # Vulnerability (default for CVE/KEV)
    (re.compile(r"\b(cve|vulnerability|exploit|patch|zero-day|zero\s+day|rce|lpe)\b", re.I), "crawler-vulnerability"),
]

# Tag generation rules: pattern → list of tags to add.
_TAG_RULES: list[tuple[re.Pattern[str], List[str]]] = [
    (re.compile(r"\bCVE-\d{4}-\d+\b", re.I), ["cve"]),
    (re.compile(r"\b(android|aosp)\b", re.I), ["android"]),
    (re.compile(r"\b(ios|iphone|ipad)\b", re.I), ["ios"]),
    (re.compile(r"\b(samsung|galaxy)\b", re.I), ["samsung"]),
    (re.compile(r"\b(qualcomm|snapdragon)\b", re.I), ["qualcomm"]),
    (re.compile(r"\b(mediatek|mtk)\b", re.I), ["mediatek"]),
    (re.compile(r"\b(pixel|nexus)\b", re.I), ["pixel"]),
    (re.compile(r"\b(bluetooth|ble)\b", re.I), ["bluetooth"]),
    (re.compile(r"\b(wi-?fi|802\.11)\b", re.I), ["wifi"]),
    (re.compile(r"\b(baseband|modem|rcp|qmi)\b", re.I), ["baseband"]),
    (re.compile(r"\b(webview|chromium|chrome)\b", re.I), ["webview"]),
    (re.compile(r"\b(patch|update|fix|upgrade)\b", re.I), ["patch"]),
    (re.compile(r"\b(mitigation|remediation|defense|hardening)\b", re.I), ["mitigation"]),
    (re.compile(r"\b(ransomware)\b", re.I), ["ransomware"]),
    (re.compile(r"\b(spyware|stalkerware)\b", re.I), ["spyware"]),
    (re.compile(r"\b(exploit|rce|lpe|eop)\b", re.I), ["exploit"]),
    (re.compile(r"\b(zero.?day)\b", re.I), ["zero-day"]),
    (re.compile(r"\b(mitre|att&ck|t\d{4})\b", re.I), ["mitre-attack"]),
    (re.compile(r"\b(cisa|nist|ncsc|enisa)\b", re.I), ["government-source"]),
]


def classify_entry(entry: CrawlerEntry) -> None:
    """Mutate entry.category and extend entry.tags based on content signals."""
    blob = " ".join([entry.title, entry.summary, entry.body, entry.category])

    # Category — only override if current category is generic.
    if entry.category in ("crawler-vulnerability", "general", ""):
        for pattern, cat in _CATEGORY_RULES:
            if pattern.search(blob):
                entry.category = cat
                break

    # Generate additional defensive tags.
    new_tags: list[str] = list(entry.tags)
    existing = set(entry.tags)
    for pattern, tags in _TAG_RULES:
        if pattern.search(blob):
            for t in tags:
                if t not in existing:
                    new_tags.append(t)
                    existing.add(t)

    entry.tags = new_tags[:64]
