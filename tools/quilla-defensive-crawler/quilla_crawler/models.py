"""Domain models for crawler entries and the published bundle.

These are internal models. The published bundle uses a subset of these fields
compatible with the Android CyberKnowledgeBase.Entry schema.
"""

from __future__ import annotations

import hashlib
from datetime import datetime
from enum import StrEnum

from pydantic import BaseModel, Field, field_validator

# ── Hard limits ──────────────────────────────────────────────────────────────
MAX_ENTRIES_PER_BUNDLE: int = 5_000
MAX_FIELDS_PER_RECORD: int = 32
MAX_FIELD_CHARS: int = 16_384
MAX_TITLE_CHARS: int = 240
MAX_SUMMARY_CHARS: int = 1_000
MAX_BODY_CHARS: int = 8_000
MAX_DEFENSE_CHARS: int = 4_000
MAX_TAGS: int = 64
MAX_REFERENCES: int = 8
MAX_RELATED_IOCS: int = 256


class VerificationStatus(StrEnum):
    UNVERIFIED = "UNVERIFIED"
    CORROBORATED = "CORROBORATED"
    TRUSTED_SOURCE = "TRUSTED_SOURCE"
    REJECTED = "REJECTED"
    EXPIRED = "EXPIRED"


class CrawlerEntry(BaseModel):
    """Full internal crawler record. Only TRUSTED_SOURCE / CORROBORATED are published."""

    # Required CyberKnowledgeBase-compatible fields
    id: str = Field(min_length=1, max_length=128)
    title: str = Field(min_length=1, max_length=MAX_TITLE_CHARS)
    category: str = Field(default="crawler-vulnerability", max_length=64)
    tags: list[str] = Field(default_factory=list)
    summary: str = Field(default="", max_length=MAX_SUMMARY_CHARS)
    body: str = Field(default="", max_length=MAX_BODY_CHARS)
    defense: str = Field(default="", max_length=MAX_DEFENSE_CHARS)
    references: list[str] = Field(default_factory=list)

    # Extended provenance fields
    source_id: str = Field(default="")
    source_name: str = Field(default="")
    source_url: str = Field(default="")
    canonical_url: str = Field(default="")
    first_seen: datetime | None = None
    last_seen: datetime | None = None
    published_at: datetime | None = None
    modified_at: datetime | None = None
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)
    verification_status: VerificationStatus = VerificationStatus.UNVERIFIED
    content_sha256: str = Field(default="")
    source_document_sha256: str = Field(default="")
    parser_version: str = Field(default="1.0")
    warnings: list[str] = Field(default_factory=list)
    related_cves: list[str] = Field(default_factory=list)
    related_packages: list[str] = Field(default_factory=list)
    related_domains: list[str] = Field(default_factory=list)
    related_hashes: list[str] = Field(default_factory=list)
    mitre_techniques: list[str] = Field(default_factory=list)

    @field_validator("tags", mode="before")
    @classmethod
    def _cap_tags(cls, v: list[str]) -> list[str]:
        return v[:MAX_TAGS]

    @field_validator("references", mode="before")
    @classmethod
    def _cap_refs(cls, v: list[str]) -> list[str]:
        return v[:MAX_REFERENCES]

    @field_validator(
        "related_cves", "related_packages", "related_domains", "related_hashes", mode="before"
    )
    @classmethod
    def _cap_iocs(cls, v: list[str]) -> list[str]:
        return v[:MAX_RELATED_IOCS]

    def is_publishable(self) -> bool:
        """Return True if this entry may appear in the signed bundle."""
        return self.verification_status in (
            VerificationStatus.TRUSTED_SOURCE,
            VerificationStatus.CORROBORATED,
        )

    def compute_content_sha256(self) -> str:
        """SHA-256 of the concatenated sanitized content fields."""
        blob = "\n".join([self.title, self.summary, self.body, self.defense])
        return hashlib.sha256(blob.encode("utf-8")).hexdigest()

    def to_bundle_dict(self) -> dict[str, object]:
        """Minimal dict compatible with CyberKnowledgeBase.Entry."""
        return {
            "id": self.id,
            "title": self.title,
            "category": self.category,
            "tags": sorted(set(self.tags)),
            "summary": self.summary,
            "body": self.body,
            "defense": self.defense,
            "references": self.references,
        }


class FetchResult(BaseModel):
    """Outcome of fetching one URL."""

    url: str
    final_url: str = ""
    http_status: int = 0
    content_type: str = ""
    bytes_received: int = 0
    body: str = ""
    error: str = ""
    duration_ms: float = 0.0

    @property
    def ok(self) -> bool:
        return self.http_status in range(200, 300) and not self.error
