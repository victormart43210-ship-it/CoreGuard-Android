"""Quilla Defensive Crawler — defensive threat-intelligence collector.

This package runs on a Raspberry Pi or controlled server, NOT inside the
Android application. It collects public cybersecurity advisories, CVEs,
IOCs and mitigation guidance, sanitizes and cryptographically signs the
result, and publishes a JSON bundle that CoreGuard Android can verify.
"""

__version__ = "1.0.0"
__all__ = [
    "config",
    "models",
    "network_guard",
    "robots",
    "fetcher",
    "sanitizer",
    "extractor",
    "classifier",
    "deduplicator",
    "bundle",
    "signer",
    "audit",
]
