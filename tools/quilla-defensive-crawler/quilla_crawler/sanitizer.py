"""HTML and text sanitizer for the Quilla Defensive Crawler.

Spec requirements:
- Parse without executing active content.
- Remove: script, style, iframe, object, embed, form, input, button, canvas,
  video, audio, SVG, template elements.
- Remove comments, hidden elements, event-handler attributes.
- Remove all attributes except safe link metadata for provenance.
- Convert to normalized plain text; collapse whitespace.
- Remove null bytes and control characters.
- Apply Unicode NFC normalization.
- Cap extracted fields.
- Remove code blocks and shell-command blocks from Quilla body.
- Detect prompt-injection phrases and remove the affected line, emit a warning.
"""

from __future__ import annotations

import re
import unicodedata
from typing import Tuple

from bs4 import BeautifulSoup, Comment, Tag

from quilla_crawler.models import (
    MAX_BODY_CHARS,
    MAX_DEFENSE_CHARS,
    MAX_FIELD_CHARS,
    MAX_SUMMARY_CHARS,
    MAX_TITLE_CHARS,
)

# Tags that execute code or present deceptive UX — always removed.
_DANGEROUS_TAGS: frozenset[str] = frozenset(
    [
        "script", "style", "iframe", "object", "embed", "form", "input",
        "button", "canvas", "video", "audio", "svg", "template", "noscript",
        "applet", "base", "link", "meta",
    ]
)

# Attributes allowed to remain (for provenance only — href on <a>).
_SAFE_ATTRIBUTES: frozenset[str] = frozenset(["href", "src"])

# Prompt-injection detection patterns (case-insensitive).
_INJECTION_PATTERNS: tuple[re.Pattern[str], ...] = (
    re.compile(r"ignore\s+(all\s+)?previous\s+instructions?", re.IGNORECASE),
    re.compile(r"reveal\s+(the\s+)?system\s+prompt", re.IGNORECASE),
    re.compile(r"execute\s+this\s+command", re.IGNORECASE),
    re.compile(r"act\s+as\s+(an?\s+)?administrator", re.IGNORECASE),
    re.compile(r"disable\s+your\s+safeguards?", re.IGNORECASE),
    re.compile(r"you\s+are\s+now\s+(in\s+)?developer\s+mode", re.IGNORECASE),
    re.compile(r"forget\s+(all\s+)?previous\s+(instructions?|context)", re.IGNORECASE),
    re.compile(r"new\s+instructions?:\s+", re.IGNORECASE),
    re.compile(r"system\s*:\s*you\s+are", re.IGNORECASE),
)

# Code block patterns to strip from the body (markdown fences, inline code).
_CODE_BLOCK_RE: re.Pattern[str] = re.compile(r"```.*?```", re.DOTALL)
_INLINE_CODE_RE: re.Pattern[str] = re.compile(r"`[^`\n]{1,200}`")

# Control characters (keep \t, \n, \r).
_CONTROL_CHAR_RE: re.Pattern[str] = re.compile(
    r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f-\x9f]"
)


def sanitize_html(html: str) -> Tuple[str, list[str]]:
    """Parse HTML, remove dangerous elements, return plain text + warnings.

    Returns:
        (plain_text, warnings)
    """
    warnings: list[str] = []

    soup = BeautifulSoup(html, "lxml")

    # Remove comments.
    for comment in soup.find_all(string=lambda text: isinstance(text, Comment)):
        comment.extract()

    # Remove dangerous tags and their subtrees.
    for tag_name in _DANGEROUS_TAGS:
        for tag in soup.find_all(tag_name):
            tag.decompose()

    # Remove hidden elements.
    for tag in soup.find_all(True):
        style = tag.get("style", "") if isinstance(tag, Tag) else ""
        if "display:none" in style.replace(" ", "") or "visibility:hidden" in style.replace(" ", ""):
            tag.decompose()

    # Strip all attributes except safe provenance ones.
    for tag in soup.find_all(True):
        if not isinstance(tag, Tag):
            continue
        attrs_to_remove = [
            k for k in list(tag.attrs.keys())
            if k not in _SAFE_ATTRIBUTES or k.startswith("on")
        ]
        # Also remove any event handlers that slipped through.
        attrs_to_remove += [k for k in tag.attrs if k.lower().startswith("on")]
        for attr in set(attrs_to_remove):
            del tag[attr]

    text = soup.get_text(separator="\n")
    text, new_warnings = sanitize_text(text)
    warnings.extend(new_warnings)

    return text, warnings


def sanitize_text(text: str) -> Tuple[str, list[str]]:
    """Sanitize plain text: normalize unicode, strip control chars, detect injection.

    Returns:
        (sanitized_text, warnings)
    """
    warnings: list[str] = []

    # Unicode NFC normalization.
    text = unicodedata.normalize("NFC", text)

    # Remove null bytes and control characters (keep \t \n \r).
    text = _CONTROL_CHAR_RE.sub("", text)

    # Remove code blocks before injection scan.
    text = _CODE_BLOCK_RE.sub("[code block removed]", text)
    text = _INLINE_CODE_RE.sub("[code]", text)

    # Per-line prompt-injection scan.
    lines = text.splitlines()
    clean_lines: list[str] = []
    for line in lines:
        injected = False
        for pattern in _INJECTION_PATTERNS:
            if pattern.search(line):
                warnings.append(
                    f"prompt-injection-like phrase removed: "
                    f"{line[:120]!r}"
                )
                injected = True
                break
        if not injected:
            clean_lines.append(line)
    text = "\n".join(clean_lines)

    # Collapse repeated blank lines.
    text = re.sub(r"\n{3,}", "\n\n", text)

    # Collapse within-line whitespace.
    text = re.sub(r"[ \t]{2,}", " ", text)

    return text.strip(), warnings


def cap_field(text: str, max_chars: int, field_name: str) -> Tuple[str, list[str]]:
    """Truncate a field to max_chars; return (value, warnings)."""
    warnings: list[str] = []
    if len(text) > max_chars:
        warnings.append(f"field {field_name!r} truncated from {len(text)} to {max_chars} chars")
        text = text[:max_chars]
    return text, warnings


def sanitize_entry_fields(
    title: str,
    summary: str,
    body: str,
    defense: str,
) -> Tuple[str, str, str, str, list[str]]:
    """Apply per-field limits and sanitization.

    Returns:
        (title, summary, body, defense, all_warnings)
    """
    all_warnings: list[str] = []

    title, tw = sanitize_text(title)
    title, tw2 = cap_field(title, MAX_TITLE_CHARS, "title")
    all_warnings.extend(tw + tw2)

    summary, sw = sanitize_text(summary)
    summary, sw2 = cap_field(summary, MAX_SUMMARY_CHARS, "summary")
    all_warnings.extend(sw + sw2)

    body, bw = sanitize_text(body)
    body, bw2 = cap_field(body, MAX_BODY_CHARS, "body")
    all_warnings.extend(bw + bw2)

    defense, dw = sanitize_text(defense)
    defense, dw2 = cap_field(defense, MAX_DEFENSE_CHARS, "defense")
    all_warnings.extend(dw + dw2)

    return title, summary, body, defense, all_warnings
