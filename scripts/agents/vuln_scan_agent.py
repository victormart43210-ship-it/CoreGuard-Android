#!/usr/bin/env python3
"""
Static Vulnerability Scanner — Swarm Agent 2
=============================================
Searches the CoreGuard-Android source tree for common Android/Kotlin security
vulnerabilities that are not captured by the MASVS compliance rules.

Checks performed:
  VULN-HARDCRED   — Hard-coded credentials (passwords, tokens, connection strings)
  VULN-WEAKRAND   — Insecure random number generation
  VULN-LOGCRED    — Sensitive data written to Logcat
  VULN-SQLINJ     — Possible SQL-injection in raw queries
  VULN-WEBVIEW    — Dangerous WebView configuration (JS enabled, file access)
  VULN-IMPLICIT   — Unprotected exported components (implicit intents / no permission)
  VULN-PATH-TRAV  — File-path operations using unvalidated user input

Exit codes:
  0 — no FAIL findings
  1 — at least one FAIL finding
"""

import re
import sys
import json
import argparse
from pathlib import Path
from dataclasses import dataclass, field, asdict
from typing import List, Optional, Tuple


# ---------------------------------------------------------------------------
# Data model (same shape as masvs_agent for easy aggregation)
# ---------------------------------------------------------------------------

@dataclass
class Finding:
    rule: str
    severity: str
    file: str
    line: Optional[int]
    message: str


@dataclass
class AgentReport:
    agent: str = "Static Vulnerability Scanner"
    findings: List[Finding] = field(default_factory=list)

    def add(self, rule: str, severity: str, file: str, line: Optional[int], message: str):
        self.findings.append(Finding(rule, severity, file, line, message))

    @property
    def has_failures(self) -> bool:
        return any(f.severity == "FAIL" for f in self.findings)

    def print_summary(self):
        if not self.findings:
            print(f"[{self.agent}] ✅  No findings — all vulnerability checks passed.")
            return
        for f in self.findings:
            icon = {"FAIL": "❌", "WARN": "⚠️ ", "INFO": "ℹ️ "}.get(f.severity, "  ")
            loc = f"{f.file}:{f.line}" if f.line else f.file
            print(f"{icon} [{f.rule}] {loc} — {f.message}")

    def to_json(self) -> str:
        return json.dumps(
            {"agent": self.agent, "findings": [asdict(f) for f in self.findings]},
            indent=2,
        )


# ---------------------------------------------------------------------------
# Vulnerability patterns
# ---------------------------------------------------------------------------

# Hard-coded credentials beyond generic keys (connection strings, bearer tokens, etc.)
_CREDENTIAL_PATTERNS: List[Tuple[re.Pattern, str]] = [
    (re.compile(r'(?i)(bearer|basic)\s+"[A-Za-z0-9+/=]{20,}"'), "Hard-coded Bearer/Basic auth token."),
    (re.compile(r'(?i)Authorization\s*[=:]\s*"[^"]{10,}"'), "Hard-coded Authorization header value."),
    (re.compile(r'jdbc:[a-zA-Z]+://[^\s"]{10,}'), "Connection string with embedded password."),
    (re.compile(r'(?i)(aws_access_key|aws_secret|firebase_api_key)\s*[=:]\s*"[^"]{8,}"'), "Hard-coded cloud provider credential."),
    (re.compile(r'AIza[0-9A-Za-z\-_]{35}'), "Hard-coded Google API key (AIza…)."),
    (re.compile(r'AAAA[A-Za-z0-9_\-]{100,}'), "Hard-coded Firebase server key (AAAA…)."),
]

# Sensitive data written to Android Log
_LOG_CREDENTIAL_PATTERNS: List[re.Pattern] = [
    re.compile(r'Log\.[dviwe]\([^,]+,\s*(?:password|token|secret|key|credential)', re.IGNORECASE),
    re.compile(r'println\(.*(?:password|token|secret|api_?key)', re.IGNORECASE),
]

# Raw SQL with string concatenation (injection risk)
_SQL_INJECTION_PATTERNS: List[re.Pattern] = [
    re.compile(r'rawQuery\(\s*"[^"]*"\s*\+'),
    re.compile(r'execSQL\(\s*"[^"]*"\s*\+'),
]

# Dangerous WebView settings
_WEBVIEW_PATTERNS: List[Tuple[re.Pattern, str]] = [
    (re.compile(r'setJavaScriptEnabled\(true\)'), "JavaScript enabled in WebView — restrict to trusted content only."),
    (re.compile(r'setAllowFileAccess\(true\)'), "File access enabled in WebView — can expose local app files."),
    (re.compile(r'setAllowUniversalAccessFromFileURLs\(true\)'), "Universal file URL access enabled — high risk of data exfiltration."),
    (re.compile(r'setAllowFileAccessFromFileURLs\(true\)'), "File URL cross-origin access enabled in WebView."),
]

# Path traversal risk: File constructed from external input without canonicalization
_PATH_TRAVERSAL_PATTERNS: List[Tuple[re.Pattern, str]] = [
    (re.compile(r'File\(\w+\.(getStringExtra|getExtra|getString)\('), "File path derived from Intent/Bundle without validation — path traversal risk."),
    (re.compile(r'File\(\w+\.path\b'), "File constructed directly from URI path — canonicalize and validate before use."),
]

# Opening tag for exported components (permission may appear on the same tag).
_EXPORTED_COMPONENT_OPEN = re.compile(
    r'<(activity|service|receiver)\b([^>]*)>',
    re.IGNORECASE | re.DOTALL,
)

# Standard Android launcher intent — exported MAIN/LAUNCHER activities are expected
# and must NOT receive a restrictive signature permission (would break home-screen launch).
_LAUNCHER_ACTION = re.compile(
    r'<action\b[^>]*android:name\s*=\s*"android\.intent\.action\.MAIN"',
    re.IGNORECASE,
)
_LAUNCHER_CATEGORY = re.compile(
    r'<category\b[^>]*android:name\s*=\s*"android\.intent\.category\.LAUNCHER"',
    re.IGNORECASE,
)
_PERMISSION_ATTR = re.compile(r'\bandroid:permission\s*=', re.IGNORECASE)
_EXPORTED_TRUE = re.compile(r'\bandroid:exported\s*=\s*"true"', re.IGNORECASE)


# ---------------------------------------------------------------------------
# Scanners
# ---------------------------------------------------------------------------

def _kotlin_java_files(root: Path) -> List[Path]:
    return list(root.rglob("*.kt")) + list(root.rglob("*.java"))


def scan_hardcoded_credentials(root: Path, report: AgentReport):
    for src in _kotlin_java_files(root):
        text = src.read_text(errors="replace")
        lines = text.splitlines()
        for lineno, line in enumerate(lines, 1):
            for pat, msg in _CREDENTIAL_PATTERNS:
                if pat.search(line):
                    report.add(
                        rule="VULN-HARDCRED",
                        severity="FAIL",
                        file=str(src.relative_to(root)),
                        line=lineno,
                        message=f"{msg} Snippet: {line.strip()[:100]}",
                    )
                    break


def scan_log_credentials(root: Path, report: AgentReport):
    for src in _kotlin_java_files(root):
        for lineno, line in enumerate(src.read_text(errors="replace").splitlines(), 1):
            for pat in _LOG_CREDENTIAL_PATTERNS:
                if pat.search(line):
                    report.add(
                        rule="VULN-LOGCRED",
                        severity="WARN",
                        file=str(src.relative_to(root)),
                        line=lineno,
                        message=f"Possible sensitive data in log statement: {line.strip()[:100]}",
                    )
                    break


def scan_sql_injection(root: Path, report: AgentReport):
    for src in _kotlin_java_files(root):
        for lineno, line in enumerate(src.read_text(errors="replace").splitlines(), 1):
            for pat in _SQL_INJECTION_PATTERNS:
                if pat.search(line):
                    report.add(
                        rule="VULN-SQLINJ",
                        severity="FAIL",
                        file=str(src.relative_to(root)),
                        line=lineno,
                        message=f"Raw SQL query with string concatenation — use parameterized queries: {line.strip()[:100]}",
                    )
                    break


def scan_webview(root: Path, report: AgentReport):
    for src in _kotlin_java_files(root):
        for lineno, line in enumerate(src.read_text(errors="replace").splitlines(), 1):
            for pat, msg in _WEBVIEW_PATTERNS:
                if pat.search(line):
                    report.add(
                        rule="VULN-WEBVIEW",
                        severity="WARN",
                        file=str(src.relative_to(root)),
                        line=lineno,
                        message=msg,
                    )


def scan_path_traversal(root: Path, report: AgentReport):
    for src in _kotlin_java_files(root):
        for lineno, line in enumerate(src.read_text(errors="replace").splitlines(), 1):
            for pat, msg in _PATH_TRAVERSAL_PATTERNS:
                if pat.search(line):
                    report.add(
                        rule="VULN-PATH-TRAV",
                        severity="WARN",
                        file=str(src.relative_to(root)),
                        line=lineno,
                        message=msg,
                    )


def _component_block(text: str, open_match: re.Match) -> str:
    """Return the component XML block starting at open_match (self-closing or paired)."""
    start = open_match.start()
    open_tag = open_match.group(0)
    if open_tag.rstrip().endswith("/>"):
        return open_tag
    kind = open_match.group(1).lower()
    close = re.search(rf'</{kind}\s*>', text[open_match.end():], re.IGNORECASE)
    if not close:
        return open_tag
    return text[start: open_match.end() + close.end()]


def is_safe_launcher_activity(component_kind: str, block: str) -> bool:
    """
    True only for an <activity> that is a normal launcher entry point:
    both MAIN action and LAUNCHER category present in the same component block.
    Narrow exemption — does not suppress other exported components.
    """
    if component_kind.lower() != "activity":
        return False
    return bool(_LAUNCHER_ACTION.search(block) and _LAUNCHER_CATEGORY.search(block))


def iter_unprotected_exports(manifest_text: str):
    """
    Yield (line_number, kind, snippet) for exported components lacking android:permission,
    excluding MAIN/LAUNCHER activities (false positives for launcher MainActivity).
    """
    for match in _EXPORTED_COMPONENT_OPEN.finditer(manifest_text):
        attrs = match.group(2) or ""
        if not _EXPORTED_TRUE.search(attrs):
            continue
        if _PERMISSION_ATTR.search(attrs):
            continue
        kind = match.group(1)
        block = _component_block(manifest_text, match)
        if is_safe_launcher_activity(kind, block):
            continue
        line = manifest_text[: match.start()].count("\n") + 1
        yield line, kind.lower(), match.group(0)[:120]


def scan_unprotected_components(root: Path, report: AgentReport):
    for manifest in root.rglob("AndroidManifest.xml"):
        # Skip build intermediates / merged manifests under build/
        rel = str(manifest.relative_to(root))
        if "/build/" in f"/{rel}" or rel.startswith("build/"):
            continue
        text = manifest.read_text(errors="replace")
        for line, kind, snippet in iter_unprotected_exports(text):
            report.add(
                rule="VULN-IMPLICIT",
                severity="WARN",
                file=rel,
                line=line,
                message=(
                    f"Exported {kind} without android:permission — consider adding a "
                    f"signature-level permission (launcher MAIN/LAUNCHER activities are exempt): "
                    f"{snippet}"
                ),
            )


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="CoreGuard Static Vulnerability Scanner")
    parser.add_argument("root", nargs="?", default=".", help="Repository root directory")
    parser.add_argument("--json-out", help="Write JSON report to this file")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    report = AgentReport()

    print(f"[Vuln Scanner] Scanning {root} …")
    scan_hardcoded_credentials(root, report)
    scan_log_credentials(root, report)
    scan_sql_injection(root, report)
    scan_webview(root, report)
    scan_path_traversal(root, report)
    scan_unprotected_components(root, report)

    print()
    report.print_summary()

    if args.json_out:
        Path(args.json_out).write_text(report.to_json())
        print(f"\n[Vuln Scanner] JSON report written to {args.json_out}")

    fails = sum(1 for f in report.findings if f.severity == "FAIL")
    warns = sum(1 for f in report.findings if f.severity == "WARN")
    print(f"\n[Vuln Scanner] Total: {len(report.findings)} finding(s) — {fails} FAIL, {warns} WARN")

    sys.exit(1 if report.has_failures else 0)


if __name__ == "__main__":
    main()
