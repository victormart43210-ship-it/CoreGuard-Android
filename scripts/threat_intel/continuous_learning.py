#!/usr/bin/env python3
"""Deterministic continuous-learning scaffold with drift and rollback safeguards."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from scripts.threat_intel.ingest_open_sources import write_bundle
from scripts.threat_intel.train_baseline_model import train
from scripts.threat_intel.validate_configs import validate_all

MODEL_PATH = REPO_ROOT / "security" / "threat-intel" / "models" / "baseline-model.v1.json"
ROLLBACK_PATH = REPO_ROOT / "security" / "threat-intel" / "models" / "baseline-model.rollback.json"
DATASET_PATH = REPO_ROOT / "security" / "threat-intel" / "v1" / "training.dataset.sample.csv"


def _load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def _quality_regressed(previous: dict, current: dict, tolerance: float) -> bool:
    prev_acc = float(previous.get("evaluation", {}).get("accuracy", 0.0))
    curr_acc = float(current.get("evaluation", {}).get("accuracy", 0.0))
    return curr_acc + tolerance < prev_acc


def _drift_detected(previous: dict, current: dict, threshold: float) -> bool:
    prev_benign = previous.get("centroids", {}).get("benign", {})
    curr_benign = current.get("centroids", {}).get("benign", {})
    distance = 0.0
    for feature, prev_value in prev_benign.items():
        curr_value = float(curr_benign.get(feature, prev_value))
        distance += abs(float(prev_value) - curr_value)
    return distance > threshold


def run(deterministic: bool, quality_tolerance: float, drift_threshold: float, hmac_key: str) -> int:
    validate_all()
    write_bundle(
        output_dir=REPO_ROOT / "security" / "threat-intel" / "artifacts",
        deterministic=deterministic,
        hmac_key=hmac_key or None,
    )

    current_model = train(DATASET_PATH, deterministic=deterministic)

    if MODEL_PATH.exists():
        previous_model = _load_json(MODEL_PATH)
        if _quality_regressed(previous_model, current_model, quality_tolerance) or _drift_detected(
            previous_model, current_model, drift_threshold
        ):
            ROLLBACK_PATH.write_text(json.dumps(previous_model, indent=2, sort_keys=True) + "\n", encoding="utf-8")
            print("[continuous_learning] quality/drift safeguard triggered; rollback snapshot written")
            return 1

    MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
    MODEL_PATH.write_text(json.dumps(current_model, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"[continuous_learning] updated {MODEL_PATH}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--deterministic", action="store_true")
    parser.add_argument("--quality-tolerance", type=float, default=0.02)
    parser.add_argument("--drift-threshold", type=float, default=0.25)
    parser.add_argument("--hmac-key", default="")
    args = parser.parse_args()
    return run(args.deterministic, args.quality_tolerance, args.drift_threshold, args.hmac_key.strip())


if __name__ == "__main__":
    raise SystemExit(main())
