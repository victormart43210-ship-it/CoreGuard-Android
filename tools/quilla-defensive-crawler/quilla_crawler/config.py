"""Configuration models loaded from source_allowlist.json.

The crawler fails closed: any URL not explicitly present in the allowlist is
rejected before a network connection is attempted.
"""

from __future__ import annotations

from enum import Enum
from typing import List, Optional

from pydantic import BaseModel, Field, field_validator, model_validator


class TrustLevel(str, Enum):
    TRUSTED_SOURCE = "TRUSTED_SOURCE"
    CORROBORATED = "CORROBORATED"


class SourceConfig(BaseModel):
    """Configuration for one approved intelligence source."""

    id: str = Field(min_length=1, max_length=64)
    name: str = Field(min_length=1, max_length=128)
    seed_urls: List[str] = Field(min_length=1, max_length=8)
    allowed_hosts: List[str] = Field(min_length=1, max_length=8)
    allowed_path_prefixes: List[str] = Field(min_length=1, max_length=16)
    content_types: List[str] = Field(default_factory=list)
    max_depth: int = Field(default=0, ge=0, le=3)
    max_pages: int = Field(default=1, ge=1, le=500)
    requests_per_minute: int = Field(default=6, ge=1, le=60)
    trust_level: TrustLevel = TrustLevel.TRUSTED_SOURCE
    parser: str = Field(min_length=1, max_length=64)
    # Optional port override (e.g. 443); absent means 443 only.
    allowed_port: Optional[int] = Field(default=None)

    @field_validator("seed_urls", mode="before")
    @classmethod
    def _seed_urls_https(cls, v: list[str]) -> list[str]:
        for url in v:
            if not url.startswith("https://"):
                raise ValueError(f"seed URL must use HTTPS: {url!r}")
        return v

    @field_validator("allowed_hosts", mode="before")
    @classmethod
    def _no_empty_hosts(cls, v: list[str]) -> list[str]:
        for h in v:
            if not h.strip():
                raise ValueError("allowed_hosts must not contain empty entries")
        return v

    @model_validator(mode="after")
    def _seeds_within_allowed_hosts(self) -> "SourceConfig":
        for url in self.seed_urls:
            from urllib.parse import urlparse

            host = urlparse(url).hostname or ""
            if host not in self.allowed_hosts:
                raise ValueError(
                    f"seed URL host {host!r} not in allowed_hosts {self.allowed_hosts}"
                )
        return self


class AllowList(BaseModel):
    """Top-level allowlist configuration document."""

    schema_version: int = Field(default=1)
    sources: List[SourceConfig] = Field(min_length=1, max_length=32)

    @field_validator("schema_version")
    @classmethod
    def _supported_version(cls, v: int) -> int:
        if v != 1:
            raise ValueError(f"unsupported schema_version {v}; only 1 is supported")
        return v
