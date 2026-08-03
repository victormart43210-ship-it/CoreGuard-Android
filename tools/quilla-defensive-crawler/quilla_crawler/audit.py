"""Structured JSON-Lines audit logger.

Writes one record per crawl event to output/audit.jsonl.
Never logs: private key material, auth headers, cookies, personal info,
or full untrusted page bodies.
"""

from __future__ import annotations

import json
import os
import threading
import uuid
from datetime import UTC, datetime


class AuditLogger:
    """Thread-safe append-only audit log writer."""

    def __init__(self, log_path: str) -> None:
        self._log_path = log_path
        self._run_id = str(uuid.uuid4())
        self._lock = threading.Lock()
        os.makedirs(os.path.dirname(log_path) or ".", exist_ok=True)

    @property
    def run_id(self) -> str:
        return self._run_id

    def record_fetch(
        self,
        source_id: str,
        url: str,
        final_url: str,
        http_status: int,
        bytes_received: int,
        content_type: str,
        duration_ms: float,
        parser: str = "",
        entries_accepted: int = 0,
        entries_rejected: int = 0,
        rejection_reasons: list[str] | None = None,
        sanitization_warnings: list[str] | None = None,
        error_class: str = "",
    ) -> None:
        self._write(
            {
                "event": "fetch",
                "run_id": self._run_id,
                "timestamp": _now_iso(),
                "source_id": source_id,
                "url": url,
                "final_url": final_url,
                "http_status": http_status,
                "bytes_received": bytes_received,
                "content_type": content_type,
                "parser": parser,
                "entries_accepted": entries_accepted,
                "entries_rejected": entries_rejected,
                "rejection_reasons": rejection_reasons or [],
                "sanitization_warnings": sanitization_warnings or [],
                "duration_ms": round(duration_ms, 2),
                "error_class": error_class,
            }
        )

    def record_rejection(
        self,
        url: str,
        reason: str,
        source_id: str = "",
    ) -> None:
        self._write(
            {
                "event": "url_rejected",
                "run_id": self._run_id,
                "timestamp": _now_iso(),
                "source_id": source_id,
                "url": url,
                "reason": reason[:512],
            }
        )

    def record_signing(
        self,
        bundle_path: str,
        entry_count: int,
        success: bool,
        error: str = "",
    ) -> None:
        self._write(
            {
                "event": "signing",
                "run_id": self._run_id,
                "timestamp": _now_iso(),
                "bundle_path": bundle_path,
                "entry_count": entry_count,
                "success": success,
                # No key material, no signature bytes, just status.
                "error": error[:256] if error else "",
            }
        )

    def record_warning(self, message: str, source_id: str = "") -> None:
        self._write(
            {
                "event": "warning",
                "run_id": self._run_id,
                "timestamp": _now_iso(),
                "source_id": source_id,
                "message": message[:512],
            }
        )

    def _write(self, record: dict) -> None:  # type: ignore[type-arg]
        line = json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n"
        with self._lock:
            with open(self._log_path, "a", encoding="utf-8") as fh:
                fh.write(line)


def _now_iso() -> str:
    return datetime.now(tz=UTC).strftime("%Y-%m-%dT%H:%M:%SZ")
