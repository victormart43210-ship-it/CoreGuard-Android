#!/usr/bin/env python3
"""
RASP / ASM Auditor — Swarm Agent 3
====================================
Audits the CoreGuard-Android project for Runtime Application Self-Protection
(RASP) coverage and binary hardening of native libraries.

Checks performed:
  RASP-PROGUARD   — Release build must enable minification and obfuscation
  RASP-RULES      — ProGuard rules must not globally suppress all obfuscation
  RASP-NATIVE     — CMakeLists or ndk-build must strip debug symbols in release
  RASP-HOOKS      — Native hook-detection surface (NativeTamperGuard JNI bridge) present
  RASP-STACK      — Stack protector / SafeStack flags present in native build config
  RASP-BACKUP     — AndroidManifest must disable Auto Backup to prevent data exfil
  RASP-NETCFG     — Network security config must block cleartext traffic

Exit codes:
  0 — all RASP/hardening checks pass (or only WARN)
  1 — at least one FAIL finding
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
    severity: str
    file: str
    line: Optional[int]
    message: str


@dataclass
class AgentReport:
    agent: str = "RASP / ASM Auditor"
    findings: List[Finding] = field(default_factory=list)

    def add(self, rule: str, severity: str, file: str, line: Optional[int], message: str):
        self.findings.append(Finding(rule, severity, file, line, message))

    @property
    def has_failures(self) -> bool:
        return any(f.severity == "FAIL" for f in self.findings)

    def print_summary(self):
        if not self.findings:
            print(f"[{self.agent}] ✅  No findings — all RASP/hardening checks passed.")
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
# Gradle / build config helpers
# ---------------------------------------------------------------------------

def _read(path: Path) -> str:
    try:
        return path.read_text(errors="replace")
    except OSError:
        return ""


def _find_first(root: Path, *globs: str) -> Optional[Path]:
    for g in globs:
        matches = list(root.rglob(g))
        if matches:
            return matches[0]
    return None


# ---------------------------------------------------------------------------
# Check implementations
# ---------------------------------------------------------------------------

def check_proguard_enabled(root: Path, report: AgentReport):
    """RASP-PROGUARD: Release build must enable minification (isMinifyEnabled = true)."""
    gradle_files = list(root.rglob("*.gradle.kts")) + list(root.rglob("*.gradle"))
    found_minify = False
    for gf in gradle_files:
        text = _read(gf)
        # Kotlin DSL (isMinifyEnabled) or Groovy minifyEnabled with '=' or space assignment.
        if (
            re.search(r'isMinifyEnabled\s*=\s*true', text)
            or re.search(r'minifyEnabled\s*=\s*true', text)
            or re.search(r'minifyEnabled\s+true', text)
        ):
            found_minify = True
            report.add(
                "RASP-PROGUARD",
                "INFO",
                str(gf.relative_to(root)),
                None,
                "Minify/R8 enabled in release build configuration. ✓",
            )
            break
    if not found_minify:
        report.add(
            "RASP-PROGUARD",
            "FAIL",
            "app/build.gradle.kts",
            None,
            "No minifyEnabled/isMinifyEnabled = true found in any Gradle file. "
            "Release code will not be obfuscated — reverse-engineering risk.",
        )


def check_proguard_rules(root: Path, report: AgentReport):
    """RASP-RULES: ProGuard rules must not globally keep everything (-keep class * { *; })."""
    _DANGEROUS_KEEP = re.compile(r'-keep\s+class\s+\*\s*\{[^}]*\*\s*;')
    for rules_file in list(root.rglob("proguard-rules.pro")) + list(root.rglob("consumer-rules.pro")):
        text = _read(rules_file)
        for lineno, line in enumerate(text.splitlines(), 1):
            if _DANGEROUS_KEEP.search(line):
                report.add("RASP-RULES", "FAIL", str(rules_file.relative_to(root)), lineno,
                           "Overly broad ProGuard keep rule defeats obfuscation: " + line.strip()[:100])
        report.add("RASP-RULES", "INFO", str(rules_file.relative_to(root)), None,
                   f"ProGuard rules file present and reviewed. ✓")


def check_native_strip(root: Path, report: AgentReport):
    """RASP-NATIVE: CMake/ndk-build must strip debug symbols in release builds."""
    cmake = _find_first(root, "CMakeLists.txt")
    if cmake is None:
        report.add("RASP-NATIVE", "WARN", ".", None,
                   "No CMakeLists.txt found — if the project uses native code, add symbol stripping.")
        return

    text = _read(cmake)
    rel_path = str(cmake.relative_to(root))

    # NDEBUG macro and visibility flag are positive signals
    has_ndebug = bool(re.search(r'NDEBUG', text))
    has_visibility_hidden = bool(re.search(r'-fvisibility=hidden', text))

    if has_ndebug:
        report.add("RASP-NATIVE", "INFO", rel_path, None, "NDEBUG macro present in CMake config. ✓")
    else:
        report.add("RASP-NATIVE", "WARN", rel_path, None,
                   "NDEBUG not explicitly referenced — confirm debug symbols are stripped in release.")

    if has_visibility_hidden:
        report.add("RASP-NATIVE", "INFO", rel_path, None,
                   "-fvisibility=hidden flag detected — unexported symbols are hidden from the dynamic linker. ✓")
    else:
        report.add("RASP-NATIVE", "WARN", rel_path, None,
                   "-fvisibility=hidden not found — exported symbols inflate the attack surface.")


def check_native_tamper_guard(root: Path, report: AgentReport):
    """RASP-HOOKS: NativeTamperGuard JNI bridge must be present and register key methods."""
    _REQUIRED_JNI_METHODS = [
        "fridaPortOpen",
        "tracerPid",
        "hookedLibraryPath",
        "codeIntegrityIntact",
    ]
    kt_files = list((root / "app").rglob("NativeTamperGuard.kt")) if (root / "app").exists() else []
    cpp_files = (
        list((root / "app").rglob("*.cpp")) + list((root / "app").rglob("*.c"))
    ) if (root / "app").exists() else []

    if not kt_files and not cpp_files:
        report.add("RASP-HOOKS", "FAIL", "app/", None,
                   "NativeTamperGuard Kotlin/C++ files not found. RASP hook detection is missing.")
        return

    all_native_text = " ".join(_read(f) for f in kt_files + cpp_files)
    for method in _REQUIRED_JNI_METHODS:
        if method in all_native_text:
            report.add("RASP-HOOKS", "INFO", "app/", None,
                       f"JNI method '{method}' is present in native bridge. ✓")
        else:
            report.add("RASP-HOOKS", "WARN", "app/", None,
                       f"JNI method '{method}' not found in native source — RASP coverage may be incomplete.")


def check_stack_protector(root: Path, report: AgentReport):
    """RASP-STACK: Stack protector flag should appear in CMake configuration."""
    cmake = _find_first(root, "CMakeLists.txt")
    if cmake is None:
        return  # already warned in check_native_strip

    text = _read(cmake)
    rel_path = str(cmake.relative_to(root))

    if re.search(r'-fstack-protector-strong', text):
        report.add("RASP-STACK", "INFO", rel_path, None,
                   "-fstack-protector-strong present — stack smashing protection is active. ✓")
    elif re.search(r'-fstack-protector\b', text):
        report.add("RASP-STACK", "WARN", rel_path, None,
                   "-fstack-protector (basic) found; upgrade to -fstack-protector-strong.")
    else:
        report.add("RASP-STACK", "WARN", rel_path, None,
                   "No -fstack-protector flag found in CMake — add for memory corruption protection.")


def check_backup_disabled(root: Path, report: AgentReport):
    """RASP-BACKUP: AndroidManifest must disable Auto Backup."""
    manifests = list(root.rglob("AndroidManifest.xml"))
    if not manifests:
        report.add("RASP-BACKUP", "WARN", ".", None, "AndroidManifest.xml not found.")
        return

    # Instrumentation / androidTest package manifests are not the production app.
    _SKIP_MARKERS = ("androidTest", "AndroidTest", "debugAndroidTest")

    for manifest in manifests:
        text = _read(manifest)
        rel_path = str(manifest.relative_to(root))
        if any(marker in rel_path for marker in _SKIP_MARKERS):
            continue
        if 'android:allowBackup="false"' in text or "android:allowBackup=\"false\"" in text:
            report.add("RASP-BACKUP", "INFO", rel_path, None,
                       "android:allowBackup=\"false\" is set. ✓")
        else:
            report.add("RASP-BACKUP", "FAIL", rel_path, None,
                       "android:allowBackup is not explicitly set to false — "
                       "app data may be backed up to Google Drive by default.")


def check_network_security_config(root: Path, report: AgentReport):
    """RASP-NETCFG: Network security config must block cleartext traffic."""
    nsc_files = list(root.rglob("network_security_config.xml"))
    if not nsc_files:
        report.add("RASP-NETCFG", "WARN", ".", None,
                   "network_security_config.xml not found — cleartext traffic may be permitted.")
        return

    for nsc in nsc_files:
        text = _read(nsc)
        rel_path = str(nsc.relative_to(root))
        if 'cleartextTrafficPermitted="false"' in text:
            report.add("RASP-NETCFG", "INFO", rel_path, None,
                       "cleartextTrafficPermitted=\"false\" confirmed. ✓")
        elif 'cleartextTrafficPermitted="true"' in text:
            report.add("RASP-NETCFG", "FAIL", rel_path, None,
                       "cleartext traffic is explicitly permitted — all traffic should use TLS.")
        else:
            report.add("RASP-NETCFG", "WARN", rel_path, None,
                       "cleartextTrafficPermitted not explicitly set to false in network security config.")


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="CoreGuard RASP/ASM Auditor")
    parser.add_argument("root", nargs="?", default=".", help="Repository root directory")
    parser.add_argument("--json-out", help="Write JSON report to this file")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    report = AgentReport()

    print(f"[RASP Auditor] Scanning {root} …")
    check_proguard_enabled(root, report)
    check_proguard_rules(root, report)
    check_native_strip(root, report)
    check_native_tamper_guard(root, report)
    check_stack_protector(root, report)
    check_backup_disabled(root, report)
    check_network_security_config(root, report)

    print()
    report.print_summary()

    if args.json_out:
        Path(args.json_out).write_text(report.to_json())
        print(f"\n[RASP Auditor] JSON report written to {args.json_out}")

    fails = sum(1 for f in report.findings if f.severity == "FAIL")
    warns = sum(1 for f in report.findings if f.severity == "WARN")
    print(f"\n[RASP Auditor] Total: {len(report.findings)} finding(s) — {fails} FAIL, {warns} WARN")

    sys.exit(1 if report.has_failures else 0)


if __name__ == "__main__":
    main()
