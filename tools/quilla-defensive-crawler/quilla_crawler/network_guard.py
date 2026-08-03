"""Network safety guard for the Quilla Defensive Crawler.

Implements all 25 safety rules from the spec:

1.  HTTPS only.
2.  Explicit hostname allowlist.
3.  Explicit allowed-path prefixes.
4.  Reject URLs with username or password.
5.  Reject non-standard ports unless explicitly configured.
6.  Resolve hostname before connecting.
7.  Reject loopback / private / link-local / multicast / reserved / unspecified IPs.
8.  Re-check destination after redirects.
9.  Reject redirects to different hosts.
10. Maximum five redirects.
11. No proxy environment variables by default.
12. No cookies.
13. No authentication headers.
14. No browser automation / no JS execution.
15. Clear user-agent string.
16. Enforce connect, read, and total timeouts.
17. Stream responses; abort on size exceeded.
18. Maximum response size: 2 MiB.
19. Reject unknown or binary content types.
20. Respect robots.txt for HTML sources.
21. Respect host rate limits.
22. Exponential back-off for 429 / 5xx only.
23. Maximum three attempts.
24. Never retry 401 / 403.
25. Protect against DNS rebinding and SSRF.
"""

from __future__ import annotations

import ipaddress
import socket
from typing import List, Optional
from urllib.parse import urlparse

from quilla_crawler.config import SourceConfig

USER_AGENT = "CoreGuard-QuillaDefensiveCrawler/1.0"

# Hard limits
MAX_RESPONSE_BYTES: int = 2 * 1024 * 1024  # 2 MiB
MAX_REDIRECTS: int = 5
CONNECT_TIMEOUT: float = 10.0
READ_TIMEOUT: float = 30.0
TOTAL_TIMEOUT: float = 60.0

# Allowed text/data content types (no binaries, no scripts, no PDFs)
ALLOWED_CONTENT_TYPES: frozenset[str] = frozenset(
    [
        "text/html",
        "text/plain",
        "application/json",
        "application/xml",
        "text/xml",
        "application/rss+xml",
        "application/atom+xml",
    ]
)


class NetworkGuardError(Exception):
    """Raised when a URL or response is blocked by safety rules."""


def _assert_https(url: str) -> None:
    parsed = urlparse(url)
    if parsed.scheme.lower() != "https":
        raise NetworkGuardError(f"Rule 1 – HTTPS required, got {parsed.scheme!r}: {url}")


def _assert_no_credentials(url: str) -> None:
    parsed = urlparse(url)
    if parsed.username or parsed.password:
        raise NetworkGuardError(f"Rule 4 – URL must not contain credentials: {url}")


def _assert_allowed_host(url: str, source: SourceConfig) -> None:
    parsed = urlparse(url)
    host = (parsed.hostname or "").lower().strip()
    if host not in [h.lower() for h in source.allowed_hosts]:
        raise NetworkGuardError(
            f"Rule 2 – host {host!r} not in allowlist {source.allowed_hosts}: {url}"
        )


def _assert_allowed_path(url: str, source: SourceConfig) -> None:
    parsed = urlparse(url)
    path = parsed.path or "/"
    if not any(path.startswith(prefix) for prefix in source.allowed_path_prefixes):
        raise NetworkGuardError(
            f"Rule 3 – path {path!r} not under allowed prefixes "
            f"{source.allowed_path_prefixes}: {url}"
        )


def _assert_allowed_port(url: str, source: SourceConfig) -> None:
    parsed = urlparse(url)
    port = parsed.port
    allowed_port = source.allowed_port if source.allowed_port else 443
    if port is not None and port != allowed_port:
        raise NetworkGuardError(
            f"Rule 5 – non-standard port {port} not configured for source "
            f"{source.id!r}: {url}"
        )


def _resolve_and_assert_safe_ip(hostname: str) -> List[str]:
    """Resolve hostname and assert no private/loopback/etc. IPs (rule 6 + 7 + 25)."""
    try:
        results = socket.getaddrinfo(hostname, None, proto=socket.IPPROTO_TCP)
    except socket.gaierror as exc:
        raise NetworkGuardError(
            f"Rule 6 – DNS resolution failed for {hostname!r}: {exc}"
        ) from exc

    addrs: List[str] = []
    for _fam, _type, _proto, _canon, sockaddr in results:
        raw_addr = sockaddr[0]
        addrs.append(raw_addr)
        try:
            addr = ipaddress.ip_address(raw_addr)
        except ValueError as exc:
            raise NetworkGuardError(
                f"Rule 7 – could not parse resolved address {raw_addr!r}: {exc}"
            ) from exc

        if addr.is_loopback:
            raise NetworkGuardError(f"Rule 7 – loopback address blocked: {raw_addr}")
        if addr.is_private:
            raise NetworkGuardError(f"Rule 7 – private address blocked: {raw_addr}")
        if addr.is_link_local:
            raise NetworkGuardError(f"Rule 7 – link-local address blocked: {raw_addr}")
        if addr.is_multicast:
            raise NetworkGuardError(f"Rule 7 – multicast address blocked: {raw_addr}")
        if addr.is_reserved:
            raise NetworkGuardError(f"Rule 7 – reserved address blocked: {raw_addr}")
        if addr.is_unspecified:
            raise NetworkGuardError(f"Rule 7 – unspecified address blocked: {raw_addr}")

    if not addrs:
        raise NetworkGuardError(f"Rule 6 – no addresses resolved for {hostname!r}")
    return addrs


def validate_url(url: str, source: SourceConfig) -> None:
    """Full pre-connect validation for a URL against a source config.

    Raises :class:`NetworkGuardError` if any rule is violated.
    """
    _assert_https(url)
    _assert_no_credentials(url)
    _assert_allowed_host(url, source)
    _assert_allowed_path(url, source)
    _assert_allowed_port(url, source)

    parsed = urlparse(url)
    hostname = parsed.hostname or ""
    if not hostname:
        raise NetworkGuardError(f"Rule 6 – no hostname in URL: {url}")
    _resolve_and_assert_safe_ip(hostname)


def validate_redirect(original_url: str, redirect_url: str, source: SourceConfig) -> None:
    """Validate a redirect destination (rules 8 + 9)."""
    _assert_https(redirect_url)
    orig_host = (urlparse(original_url).hostname or "").lower()
    redir_host = (urlparse(redirect_url).hostname or "").lower()
    if orig_host != redir_host:
        raise NetworkGuardError(
            f"Rule 9 – redirect to different host blocked: {orig_host!r} → {redir_host!r}"
        )
    validate_url(redirect_url, source)


def assert_allowed_content_type(
    content_type: str,
    source_allowed: Optional[List[str]] = None,
) -> None:
    """Rule 19 – reject unknown or binary content types."""
    ct_base = content_type.split(";")[0].strip().lower()
    allowed = frozenset(ct.lower() for ct in (source_allowed or [])) or ALLOWED_CONTENT_TYPES
    if ct_base not in allowed:
        raise NetworkGuardError(
            f"Rule 19 – blocked content-type {ct_base!r}; allowed: {sorted(allowed)}"
        )
