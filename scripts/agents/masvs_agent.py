#!/usr/bin/env python3
"""
MASVS Compliance Agent — Swarm Agent 1
=======================================
Audits the CoreGuard-Android source tree against OWASP Mobile Application
Security Verification Standard (MASVS) rules.

Checks performed:
  MASVS-STORAGE-1  — No sensitive data written to plain-text / world-readable files
  MASVS-CRYPTO-1   — No hard-coded cryptographic keys or secrets in source
  MASVS-CRYPTO-2   — No use of weak / deprecated cryptographic primitives
  MASVS-NETWORK-1  — No hard-coded cleartext HTTP endpoints
  MASVS-CODE-1     — Debug build flags do not reach release configuration
  MASVS-RESILIENCE — Anti-tamper / anti-debug checks are present

Exit codes:
  0  — all checks pass
  1  — one or more FAIL findings; CI job fails (used by PR Gatekeeper)
"""

import re
import sys
import json
import argparse
from pathlib import Path
from dataclasses import dataclass, field, asdict
from typing import List, Optional


# ---------------------------------------------------------------------------
# Data model
# ---------------------------------------------------------------------------

@dataclass
class Finding:
    rule: str
    severity: str          # FAIL | WARN | INFO
    file: str
    line: Optional[int]
    message: str


@dataclass
class AgentReport:
    agent: str = "MASVS Compliance Agent"
    findings: List[Finding] = field(default_factory=list)

    def add(self, rule: str, severity: str, file: str, line: Optional[int], message: str):
        self.findings.append(Finding(rule, severity, file, line, message))

    @property
    def has_failures(self) -> bool:
        return any(f.severity == "FAIL" for f in self.findings)

    def print_summary(self):
        if not self.findings:
            print(f"[{self.agent}] ✅  No findings — all MASVS checks passed.")
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
# Check implementations
# ---------------------------------------------------------------------------

# Patterns that suggest a hard-coded cryptographic key or password
_HARDCODED_KEY_PATTERNS = [
    re.compile(r'(?i)(secret|password|passwd|private_?key|api_?key)\s*[=:]\s*"[^"]{8,}"'),
    re.compile(r'(?i)val\s+\w*(key|secret|token|password)\w*\s*=\s*"[^"]{8,}"'),
    re.compile(r'(?i)const\s+val\s+\w*\s*=\s*"[A-Za-z0-9+/=]{32,}"'),  # base64-ish long string
]

# Weak cryptographic algorithm identifiers
_WEAK_CRYPTO_PATTERNS = [
    (re.compile(r'"MD5"'), "MD5 is cryptographically broken; use SHA-256 or SHA-3."),
    (re.compile(r'"SHA-?1"'), "SHA-1 is deprecated; use SHA-256 or stronger."),
    (re.compile(r'"DES[^A-Za-z]|/DES/'), "DES provides only 56-bit security; use AES-256."),
    (re.compile(r'(?i)AES/ECB'), "ECB mode is deterministic and reveals patterns; use AES/GCM."),
    (re.compile(r'(?i)getInstance\("RC4"\)'), "RC4 is broken; use ChaCha20 or AES/GCM."),
    (re.compile(r'java\.util\.Random\(\)'), "java.util.Random is not cryptographically secure; use SecureRandom."),
]

# Cleartext HTTP endpoint literals
_CLEARTEXT_HTTP_PATTERN = re.compile(r'"http://[a-zA-Z0-9]')

# Debug build leakage into production config
_DEBUG_FLAG_PATTERNS = [
    re.compile(r'isDebuggable\s*=\s*true'),
    re.compile(r'debuggable\s*true'),
]

# Anti-tamper / RASP signal: expected evaluator class names that MUST be present
_REQUIRED_RESILIENCE_CLASSES = {
    "FridaDetectionEvaluator",
    "NativeDebuggerEvaluator",
    "HookDetectionEvaluator",
    "MemoryIntegrityEvaluator",
    "RootCheckEvaluator",
}

# Plain-text storage API calls
_INSECURE_STORAGE_PATTERNS = [
    (re.compile(r'openFileOutput\([^,]+,\s*MODE_WORLD_READABLE'), "World-readable file storage violates MASVS-STORAGE-2."),
    (re.compile(r'getSharedPreferences[^;]*\bMODE_WORLD_READABLE\b'), "World-readable SharedPreferences violates MASVS-STORAGE-2."),
]

# Allowlist: lines matching these patterns are Android Keystore boilerplate, not secrets.
_KEY_SCAN_ALLOWLIST = re.compile(
    r'(?i)(keystore|key_?alias|ANDROID_KEYSTORE|KeyStore\.getInstance|keyStore\.getEntry)'
)


# ---------------------------------------------------------------------------
# Scanner helpers
# ---------------------------------------------------------------------------

def _kotlin_java_files(root: Path, exclude_test: bool = False) -> List[Path]:
    files = list(root.rglob("*.kt")) + list(root.rglob("*.java"))
    if exclude_test:
        files = [f for f in files if "/test/" not in f.as_posix() and "/androidTest/" not in f.as_posix()]
    return files


def _gradle_files(root: Path) -> List[Path]:
    return [p for p in list(root.rglob("*.gradle")) + list(root.rglob("*.gradle.kts")) if p.is_file()]


def scan_hardcoded_keys(root: Path, report: AgentReport):
    """MASVS-CRYPTO-1: No hard-coded cryptographic keys or secrets."""
    # Exclude test sources (test files intentionally contain known-bad patterns as fixtures).
    for src in _kotlin_java_files(root, exclude_test=True):
        for lineno, line in enumerate(src.read_text(errors="replace").splitlines(), 1):
            stripped = line.strip()
            if stripped.startswith("//") or stripped.startswith("*"):
                continue
            if _KEY_SCAN_ALLOWLIST.search(line):
                continue
            for pat in _HARDCODED_KEY_PATTERNS:
                if pat.search(line):
                    report.add(
                        rule="MASVS-CRYPTO-1",
                        severity="FAIL",
                        file=str(src.relative_to(root)),
                        line=lineno,
                        message=f"Potential hard-coded key/secret: {line.strip()[:120]}",
                    )
                    break  # report once per line


def scan_weak_crypto(root: Path, report: AgentReport):
    """MASVS-CRYPTO-2: No weak or deprecated cryptographic primitives."""
    for src in _kotlin_java_files(root):
        text_lines = src.read_text(errors="replace").splitlines()
        for lineno, line in enumerate(text_lines, 1):
            for pat, msg in _WEAK_CRYPTO_PATTERNS:
                if pat.search(line):
                    report.add(
                        rule="MASVS-CRYPTO-2",
                        severity="FAIL",
                        file=str(src.relative_to(root)),
                        line=lineno,
                        message=msg,
                    )


def scan_cleartext_endpoints(root: Path, report: AgentReport):
    """MASVS-NETWORK-1: No hard-coded cleartext HTTP endpoints in production sources."""
    # Exclude test sources: unit tests legitimately use mock/evil HTTP URLs as IOC fixtures.
    for src in _kotlin_java_files(root, exclude_test=True):
        for lineno, line in enumerate(src.read_text(errors="replace").splitlines(), 1):
            if _CLEARTEXT_HTTP_PATTERN.search(line):
                report.add(
                    rule="MASVS-NETWORK-1",
                    severity="FAIL",
                    file=str(src.relative_to(root)),
                    line=lineno,
                    message=f"Cleartext HTTP URL literal: {line.strip()[:120]}",
                )


def scan_debug_flags(root: Path, report: AgentReport):
    """MASVS-CODE-1: Debug flags must not appear in release build configuration."""
    for gf in _gradle_files(root):
        for lineno, line in enumerate(gf.read_text(errors="replace").splitlines(), 1):
            for pat in _DEBUG_FLAG_PATTERNS:
                if pat.search(line):
                    report.add(
                        rule="MASVS-CODE-1",
                        severity="WARN",
                        file=str(gf.relative_to(root)),
                        line=lineno,
                        message=f"Possible debug flag in build config: {line.strip()[:120]}",
                    )


def scan_insecure_storage(root: Path, report: AgentReport):
    """MASVS-STORAGE-1/2: No world-readable file or SharedPreference storage."""
    for src in _kotlin_java_files(root):
        for lineno, line in enumerate(src.read_text(errors="replace").splitlines(), 1):
            for pat, msg in _INSECURE_STORAGE_PATTERNS:
                if pat.search(line):
                    report.add(
                        rule="MASVS-STORAGE-2",
                        severity="FAIL",
                        file=str(src.relative_to(root)),
                        line=lineno,
                        message=msg,
                    )


def scan_resilience_classes(root: Path, report: AgentReport):
    """MASVS-RESILIENCE: Required anti-tamper evaluator classes must be present."""
    src_dir = root / "app" / "src" / "main"
    if not src_dir.exists():
        report.add("MASVS-RESILIENCE", "WARN", "app/src/main", None,
                   "Source directory not found; resilience check skipped.")
        return

    all_kt_text = " ".join(
        f.read_text(errors="replace") for f in _kotlin_java_files(src_dir)
    )
    for cls in _REQUIRED_RESILIENCE_CLASSES:
        if cls not in all_kt_text:
            report.add(
                rule="MASVS-RESILIENCE",
                severity="FAIL",
                file="app/src/main",
                line=None,
                message=f"Required anti-tamper class '{cls}' not found in source tree.",
            )
        else:
            report.add(
                rule="MASVS-RESILIENCE",
                severity="INFO",
                file="app/src/main",
                line=None,
                message=f"Anti-tamper class '{cls}' is present. ✓",
            )


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="CoreGuard MASVS Compliance Agent")
    parser.add_argument("root", nargs="?", default=".", help="Repository root directory")
    parser.add_argument("--json-out", help="Write JSON report to this file")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    report = AgentReport()

    print(f"[MASVS Agent] Scanning {root} …")
    scan_hardcoded_keys(root, report)
    scan_weak_crypto(root, report)
    scan_cleartext_endpoints(root, report)
    scan_debug_flags(root, report)
    scan_insecure_storage(root, report)
    scan_resilience_classes(root, report)

    print()
    report.print_summary()

    if args.json_out:
        Path(args.json_out).write_text(report.to_json())
        print(f"\n[MASVS Agent] JSON report written to {args.json_out}")

    total = len(report.findings)
    fails = sum(1 for f in report.findings if f.severity == "FAIL")
    warns = sum(1 for f in report.findings if f.severity == "WARN")
    print(f"\n[MASVS Agent] Total: {total} finding(s) — {fails} FAIL, {warns} WARN")

    sys.exit(1 if report.has_failures else 0)


if __name__ == "__main__":
    main()
