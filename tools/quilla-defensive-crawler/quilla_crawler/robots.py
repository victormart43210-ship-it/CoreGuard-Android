"""Minimal robots.txt parser and checker.

Fetches and caches robots.txt for each allowed host and checks whether a
given path is permitted for our User-Agent.

Rule 20: Respect robots.txt for HTML sources only. JSON/feed sources skip
robots checking (they are direct API endpoints, not crawled pages).
"""

from __future__ import annotations

import time
from typing import Dict, List, Optional
from urllib.parse import urlparse
from urllib.robotparser import RobotFileParser

import httpx

from quilla_crawler.network_guard import (
    CONNECT_TIMEOUT,
    READ_TIMEOUT,
    USER_AGENT,
    MAX_RESPONSE_BYTES,
)

_CACHE_TTL_SECONDS: int = 3600  # Re-fetch robots.txt at most once per hour


class RobotsCache:
    """Per-host robots.txt cache."""

    def __init__(self) -> None:
        self._cache: Dict[str, tuple[RobotFileParser, float]] = {}

    def is_allowed(self, url: str, *, user_agent: str = USER_AGENT) -> bool:
        """Return True if the URL may be fetched; False if robots.txt denies it."""
        parsed = urlparse(url)
        host = parsed.hostname or ""
        scheme = parsed.scheme or "https"
        robots_url = f"{scheme}://{host}/robots.txt"

        parser = self._get_parser(host, robots_url)
        if parser is None:
            # Could not fetch robots.txt — be conservative and allow.
            return True
        return parser.can_fetch(user_agent, url)

    def _get_parser(self, host: str, robots_url: str) -> Optional[RobotFileParser]:
        cached = self._cache.get(host)
        now = time.monotonic()
        if cached is not None:
            parser, fetched_at = cached
            if now - fetched_at < _CACHE_TTL_SECONDS:
                return parser

        # Fetch with a safe httpx call (no cookies, no auth, HTTPS assumed).
        parser = RobotFileParser()
        parser.set_url(robots_url)
        try:
            resp = httpx.get(
                robots_url,
                headers={"User-Agent": USER_AGENT},
                follow_redirects=False,
                timeout=httpx.Timeout(connect=CONNECT_TIMEOUT, read=READ_TIMEOUT, write=5.0, pool=5.0),
            )
            if resp.status_code == 200:
                body = resp.text
                if len(body) > MAX_RESPONSE_BYTES:
                    body = body[:MAX_RESPONSE_BYTES]
                parser.parse(body.splitlines())
            elif resp.status_code == 404:
                # No robots.txt — everything allowed.
                parser.parse([])
            else:
                # Non-200, non-404 — be conservative: allow.
                parser.parse([])
        except Exception:  # noqa: BLE001
            parser.parse([])

        self._cache[host] = (parser, now)
        return parser


# Module-level singleton for reuse across the crawl run.
_robots_cache = RobotsCache()


def is_allowed(url: str) -> bool:
    """Return True if robots.txt permits fetching the URL."""
    return _robots_cache.is_allowed(url)
