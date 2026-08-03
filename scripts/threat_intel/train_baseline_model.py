#!/usr/bin/env python3
"""Baseline anomaly-model training scaffold with explainability and fallback policy."""

from __future__ import annotations

import argparse
import csv
import json
import math
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[2]
DATASET_PATH = REPO_ROOT / "security" / "threat-intel" / "v1" / "training.dataset.sample.csv"
OUTPUT_PATH = REPO_ROOT / "security" / "threat-intel" / "models" / "baseline-model.v1.json"

FEATURES = [
    "suspicious_permission_score",
    "network_anomaly_score",
    "exploit_indicator_score",
    "failed_tls_rate",
    "domain_churn",
]


@dataclass
class Sample:
    label: int
    features: dict[str, float]


def _now(deterministic: bool) -> str:
    if deterministic:
        return "2026-01-01T00:00:00Z"
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def load_dataset(path: Path) -> list[Sample]:
    samples: list[Sample] = []
    with path.open("r", encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            label = int(row["label"])
            feature_values = {f: float(row[f]) for f in FEATURES}
            samples.append(Sample(label=label, features=feature_values))
    if not samples:
        raise ValueError("dataset is empty")
    return samples


def _class_centroid(samples: list[Sample], target_label: int) -> dict[str, float]:
    filtered = [s for s in samples if s.label == target_label]
    if not filtered:
        raise ValueError(f"No samples for label={target_label}")
    out: dict[str, float] = {}
    for feature in FEATURES:
        out[feature] = sum(s.features[feature] for s in filtered) / len(filtered)
    return out


def _distance(a: dict[str, float], b: dict[str, float]) -> float:
    return math.sqrt(sum((a[f] - b[f]) ** 2 for f in FEATURES))


def score_sample(sample: Sample, benign_centroid: dict[str, float], threat_centroid: dict[str, float]) -> tuple[int, float, list[dict[str, Any]]]:
    benign_distance = _distance(sample.features, benign_centroid)
    threat_distance = _distance(sample.features, threat_centroid)

    ratio = threat_distance / (benign_distance + threat_distance + 1e-9)
    threat_probability = max(0.0, min(1.0, 1.0 - ratio))
    prediction = 1 if threat_probability >= 0.5 else 0
    confidence = abs(threat_probability - 0.5) * 2.0

    contributions = []
    for feature in FEATURES:
        delta = sample.features[feature] - benign_centroid[feature]
        contributions.append({"feature": feature, "contribution": round(delta, 6)})
    contributions.sort(key=lambda item: abs(item["contribution"]), reverse=True)

    return prediction, confidence, contributions[:3]


def train(path: Path, deterministic: bool) -> dict[str, Any]:
    samples = load_dataset(path)
    benign_centroid = _class_centroid(samples, 0)
    threat_centroid = _class_centroid(samples, 1)

    confusion = defaultdict(int)
    confidences: list[float] = []

    for sample in samples:
        prediction, confidence, _ = score_sample(sample, benign_centroid, threat_centroid)
        confusion[(sample.label, prediction)] += 1
        confidences.append(confidence)

    total = len(samples)
    accuracy = (confusion[(0, 0)] + confusion[(1, 1)]) / total

    confidence_threshold = 0.7
    explainability_preview = []
    for sample in samples[:3]:
        prediction, confidence, top_features = score_sample(sample, benign_centroid, threat_centroid)
        explainability_preview.append(
            {
                "label": sample.label,
                "prediction": prediction,
                "confidence": round(confidence, 6),
                "top_feature_contributions": top_features,
            }
        )

    return {
        "model_version": "baseline-anomaly-v1",
        "created_at": _now(deterministic),
        "dataset": str(path.relative_to(REPO_ROOT)),
        "feature_order": FEATURES,
        "centroids": {
            "benign": benign_centroid,
            "threat": threat_centroid,
        },
        "evaluation": {
            "accuracy": round(accuracy, 6),
            "samples": total,
            "confusion_matrix": {
                "tn": confusion[(0, 0)],
                "fp": confusion[(0, 1)],
                "fn": confusion[(1, 0)],
                "tp": confusion[(1, 1)],
            },
            "mean_confidence": round(sum(confidences) / len(confidences), 6),
        },
        "guardrails": {
            "confidence_threshold": confidence_threshold,
            "low_confidence_behavior": "fallback_to_rule_based_detection",
            "explainability": "top_feature_contributions",
        },
        "explainability_preview": explainability_preview,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, default=DATASET_PATH)
    parser.add_argument("--output", type=Path, default=OUTPUT_PATH)
    parser.add_argument("--deterministic", action="store_true")
    args = parser.parse_args()

    model = train(args.dataset, deterministic=args.deterministic)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(model, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"[train_baseline_model] wrote {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
