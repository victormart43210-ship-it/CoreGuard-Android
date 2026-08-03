"""HTTP fetcher with all network safety rules enforced.

Wraps httpx to:
- Enforce HTTPS, allowlist, path, port, and credential rules.
- Resolve IPs and block SSRF ranges before connecting.
- Stream responses and abort if > MAX_RESPONSE_BYTES.
- Apply rate-limiting per host.
- Exponential back-off on 429/5xx only.
- Respect robots.txt for HTML sources.
- Strip cookies, auth headers, proxy settings.
- Select the correct parser and extract CrawlerEntry records.
"""

from __future__ import annotations

import time
from collections import defaultdict
from urllib.parse import urlparse

import httpx

from quilla_crawler.audit import AuditLogger
from quilla_crawler.config import SourceConfig
from quilla_crawler.extractor import extract_entries
from quilla_crawler.models import CrawlerEntry, FetchResult
from quilla_crawler.network_guard import (
    CONNECT_TIMEOUT,
    MAX_REDIRECTS,
    MAX_RESPONSE_BYTES,
    READ_TIMEOUT,
    USER_AGENT,
    NetworkGuardError,
    assert_allowed_content_type,
    validate_url,
)
from quilla_crawler.robots import is_allowed

MAX_ATTEMPTS: int = 3
_RETRY_STATUSES: frozenset[int] = frozenset([429, 500, 502, 503, 504])
_NO_RETRY_STATUSES: frozenset[int] = frozenset([401, 403])


class Fetcher:
    """Orchestrates safe HTTP fetching and parsing for all approved sources."""

    def __init__(self, audit: AuditLogger | None = None, verbose: bool = False) -> None:
        self._audit = audit
        self._verbose = verbose
        # Per-host last-request timestamp for rate limiting
        self._last_request_ts: dict[str, float] = defaultdict(float)

    def fetch_source(
        self,
        source: SourceConfig,
        max_pages_override: int | None = None,
    ) -> list[CrawlerEntry]:
        """Fetch all seed URLs for a source and return extracted entries."""
        max_pages = max_pages_override if max_pages_override is not None else source.max_pages
        entries: list[CrawlerEntry] = []
        pages_fetched = 0

        for seed_url in source.seed_urls:
            if pages_fetched >= max_pages:
                break
            try:
                validate_url(seed_url, source)
            except NetworkGuardError as exc:
                if self._audit:
                    self._audit.record_rejection(seed_url, str(exc), source.id)
                if self._verbose:
                    print(f"[fetcher] BLOCKED seed {seed_url!r}: {exc}")
                continue

            result = self._fetch_with_retry(seed_url, source)
            pages_fetched += 1
            if result.ok:
                new_entries = extract_entries(result, source)
                entries.extend(new_entries)
                if self._verbose:
                    print(f"[fetcher] {seed_url!r} → {len(new_entries)} entries")
            else:
                if self._verbose:
                    print(f"[fetcher] FAILED {seed_url!r}: {result.error}")

        return entries

    def _fetch_with_retry(self, url: str, source: SourceConfig) -> FetchResult:
        """Fetch with exponential back-off for 429/5xx (rule 22-24)."""
        last_result = FetchResult(url=url)
        for attempt in range(1, MAX_ATTEMPTS + 1):
            result = self._fetch_once(url, source)
            last_result = result
            if result.ok:
                return result
            if result.http_status in _NO_RETRY_STATUSES:
                # Rule 24: never retry auth failures.
                break
            if result.http_status in _RETRY_STATUSES and attempt < MAX_ATTEMPTS:
                backoff = 2**attempt
                if self._verbose:
                    print(f"[fetcher] back-off {backoff}s after HTTP {result.http_status}")
                time.sleep(backoff)
                continue
            break
        return last_result

    def _fetch_once(self, url: str, source: SourceConfig) -> FetchResult:
        """Single HTTP GET with all safety rules applied."""
        start = time.monotonic()

        # Robots.txt check for HTML sources (rule 20).
        parsed_ct = source.content_types
        is_html_source = "text/html" in parsed_ct
        if is_html_source and not is_allowed(url):
            return FetchResult(
                url=url,
                error="robots.txt disallows this URL",
            )

        # Rate limiting (rule 21).
        host = urlparse(url).hostname or ""
        min_gap = 60.0 / source.requests_per_minute
        elapsed_since_last = time.monotonic() - self._last_request_ts[host]
        if elapsed_since_last < min_gap:
            time.sleep(min_gap - elapsed_since_last)

        # Build a transport that validates IPs pre-connection.
        transport = _SafeHTTPTransport(source)

        try:
            with httpx.Client(
                transport=transport,
                headers={
                    "User-Agent": USER_AGENT,
                    "Accept": "application/json, text/html, text/plain, */*;q=0.8",
                },
                cookies=None,  # Rule 12: no cookies
                follow_redirects=False,  # We handle redirects manually for rule 8+9
                timeout=httpx.Timeout(
                    connect=CONNECT_TIMEOUT,
                    read=READ_TIMEOUT,
                    write=5.0,
                    pool=5.0,
                ),
            ) as client:
                final_url, resp = self._follow_redirects(client, url, source)

            self._last_request_ts[host] = time.monotonic()

            ct_header = resp.headers.get("content-type", "")
            try:
                assert_allowed_content_type(ct_header, source.content_types or None)
            except NetworkGuardError as exc:
                return FetchResult(
                    url=url,
                    final_url=final_url,
                    http_status=resp.status_code,
                    content_type=ct_header,
                    error=str(exc),
                    duration_ms=(time.monotonic() - start) * 1000,
                )

            # Stream and size-limit (rules 17-18).
            chunks: list[bytes] = []
            total = 0
            for chunk in resp.iter_bytes(chunk_size=65_536):
                total += len(chunk)
                if total > MAX_RESPONSE_BYTES:
                    return FetchResult(
                        url=url,
                        final_url=final_url,
                        http_status=resp.status_code,
                        content_type=ct_header,
                        error=f"response exceeds {MAX_RESPONSE_BYTES} bytes",
                        bytes_received=total,
                        duration_ms=(time.monotonic() - start) * 1000,
                    )
                chunks.append(chunk)

            body_bytes = b"".join(chunks)
            body_text = body_bytes.decode("utf-8", errors="replace")
            duration = (time.monotonic() - start) * 1000

            if self._audit:
                self._audit.record_fetch(
                    source_id=source.id,
                    url=url,
                    final_url=final_url,
                    http_status=resp.status_code,
                    bytes_received=len(body_bytes),
                    content_type=ct_header,
                    duration_ms=duration,
                )

            return FetchResult(
                url=url,
                final_url=final_url,
                http_status=resp.status_code,
                content_type=ct_header,
                bytes_received=len(body_bytes),
                body=body_text,
                duration_ms=duration,
            )

        except NetworkGuardError as exc:
            return FetchResult(
                url=url, error=str(exc), duration_ms=(time.monotonic() - start) * 1000
            )
        except Exception as exc:  # noqa: BLE001
            return FetchResult(
                url=url,
                error=f"{type(exc).__name__}: {exc}",
                duration_ms=(time.monotonic() - start) * 1000,
            )

    def _follow_redirects(
        self,
        client: httpx.Client,
        url: str,
        source: SourceConfig,
    ) -> tuple[str, httpx.Response]:
        """Manually follow redirects up to MAX_REDIRECTS, validating each hop."""
        current_url = url
        for hop in range(MAX_REDIRECTS + 1):
            resp = client.get(current_url)
            if resp.status_code not in (301, 302, 303, 307, 308):
                return current_url, resp
            if hop >= MAX_REDIRECTS:
                raise NetworkGuardError(
                    f"Rule 10 – exceeded {MAX_REDIRECTS} redirects from {url!r}"
                )
            location = resp.headers.get("location", "")
            if not location:
                return current_url, resp
            # Resolve relative redirects.
            if location.startswith("/"):
                parsed = urlparse(current_url)
                location = f"{parsed.scheme}://{parsed.netloc}{location}"
            # Rule 8+9: validate redirect.
            from quilla_crawler.network_guard import validate_redirect

            validate_redirect(current_url, location, source)
            current_url = location
        return current_url, resp


class _SafeHTTPTransport(httpx.HTTPTransport):
    """HTTP transport that resolves IPs pre-connection to block SSRF (rule 25)."""

    def __init__(self, source: SourceConfig) -> None:
        super().__init__()
        self._source = source

    def handle_request(self, request: httpx.Request) -> httpx.Response:
        from quilla_crawler.network_guard import _resolve_and_assert_safe_ip

        hostname = request.url.host
        if hostname:
            _resolve_and_assert_safe_ip(hostname)
        return super().handle_request(request)
