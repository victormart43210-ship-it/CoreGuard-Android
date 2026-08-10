#!/usr/bin/env python3
"""Validate versioned threat-intel configuration files."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[2]
THREAT_INTEL_DIR = REPO_ROOT / "security" / "threat-intel" / "v1"


def _load_json(path: Path) -> dict[str, Any]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError(f"{path} must contain a JSON object")
    return data


def _require_keys(obj: dict[str, Any], keys: list[str], path: str) -> None:
    missing = [k for k in keys if k not in obj]
    if missing:
        raise ValueError(f"{path} missing required keys: {', '.join(missing)}")


def validate_sources_allowlist(path: Path) -> None:
    doc = _load_json(path)
    _require_keys(doc, ["schema_version", "allowed_sources", "prohibited_collection"], str(path))

    if doc["schema_version"] != "1.0.0":
        raise ValueError("sources allowlist must use schema_version=1.0.0")

    allowed = doc["allowed_sources"]
    if not isinstance(allowed, list) or not allowed:
        raise ValueError("allowed_sources must be a non-empty array")

    seen_ids: set[str] = set()
    for source in allowed:
        if not isinstance(source, dict):
            raise ValueError("Each allowed source must be an object")
        _require_keys(source, ["id", "name", "type", "url", "acceptable_data", "disallowed_data"], "allowed_sources[]")

        source_id = str(source["id"]).strip()
        if not re.fullmatch(r"[a-z0-9-]+", source_id):
            raise ValueError(f"Invalid source id format: {source_id}")
        if source_id in seen_ids:
            raise ValueError(f"Duplicate source id: {source_id}")
        seen_ids.add(source_id)

        if not str(source["url"]).startswith("https://"):
            raise ValueError(f"Source URL must be HTTPS: {source_id}")

        text_blob = json.dumps(source).lower()
        prohibited_terms = ["telegram", "dark web", "malware sample", "leak", "stolen"]
        hit = next((term for term in prohibited_terms if term in text_blob), None)
        if hit:
            raise ValueError(f"Source {source_id} includes prohibited term: {hit}")


def validate_detection_rules(path: Path, allowed_source_ids: set[str]) -> None:
    doc = _load_json(path)
    _require_keys(
        doc,
        [
            "schema_version",
            "ruleset_version",
            "permission_combination_rules",
            "network_behavior_rules",
            "exploit_indicator_rules",
            "tuning",
        ],
        str(path),
    )

    if doc["schema_version"] != "1.0.0":
        raise ValueError("detection rules must use schema_version=1.0.0")

    for rule in doc["permission_combination_rules"]:
        _require_keys(rule, ["id", "permissions", "trigger_threshold", "severity"], "permission_combination_rules[]")
        permissions = rule["permissions"]
        if not isinstance(permissions, list) or len(permissions) < 2:
            raise ValueError(f"Permission rule {rule.get('id')} requires at least 2 permissions")

    for rule in doc["network_behavior_rules"]:
        _require_keys(rule, ["id", "metric", "operator", "threshold", "severity"], "network_behavior_rules[]")
        if rule["operator"] not in {">", ">=", "<", "<=", "=="}:
            raise ValueError(f"Network rule {rule.get('id')} has invalid operator")
        if float(rule["threshold"]) < 0:
            raise ValueError(f"Network rule {rule.get('id')} threshold must be non-negative")

    for rule in doc["exploit_indicator_rules"]:
        _require_keys(rule, ["id", "source", "advisory", "indicator_type", "pattern", "confidence"], "exploit_indicator_rules[]")
        source = str(rule["source"])
        if source not in allowed_source_ids:
            raise ValueError(f"Exploit rule {rule.get('id')} uses non-allowlisted source: {source}")
        advisory = str(rule["advisory"])
        if source == "nvd-cve" and not advisory.startswith("CVE-"):
            raise ValueError(f"NVD advisory must use CVE id: {advisory}")
        confidence = float(rule["confidence"])
        if not (0.0 <= confidence <= 1.0):
            raise ValueError(f"Exploit rule {rule.get('id')} confidence must be in [0,1]")

    tuning = doc["tuning"]
    _require_keys(tuning, ["model_confidence_threshold", "rule_vote_threshold", "low_confidence_fallback"], "tuning")
    threshold = float(tuning["model_confidence_threshold"])
    if not (0.0 <= threshold <= 1.0):
        raise ValueError("model_confidence_threshold must be in [0,1]")
    if int(tuning["rule_vote_threshold"]) < 1:
        raise ValueError("rule_vote_threshold must be >= 1")


def validate_all() -> None:
    sources_path = THREAT_INTEL_DIR / "sources.allowlist.v1.json"
    rules_path = THREAT_INTEL_DIR / "detection.rules.v1.json"
    validate_sources_allowlist(sources_path)
    sources = _load_json(sources_path)
    source_ids = {src["id"] for src in sources["allowed_sources"]}
    validate_detection_rules(rules_path, source_ids)


def main() -> int:
    try:
        validate_all()
    except Exception as exc:  # noqa: BLE001
        print(f"[validate_configs] ERROR: {exc}", file=sys.stderr)
        return 1
    print("[validate_configs] OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
