from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from scripts.threat_intel.ingest_open_sources import build_bundle
from scripts.threat_intel.train_baseline_model import train
from scripts.threat_intel.validate_configs import validate_all


class ThreatIntelPipelineTests(unittest.TestCase):
    def test_config_validation_passes(self) -> None:
        validate_all()

    def test_ingestion_bundle_has_provenance(self) -> None:
        bundle = build_bundle(deterministic=True)
        self.assertEqual(bundle["bundle_version"], "1.0.0")
        self.assertTrue(bundle["sources"])
        self.assertTrue(bundle["records"])
        for source in bundle["sources"]:
            self.assertIn("source_id", source)
            self.assertIn("fixture_sha256", source)
            self.assertIn("fetched_at", source)
        for record in bundle["records"]:
            self.assertIn("source_id", record)
            self.assertIn("collected_at", record)

    def test_training_emits_guardrails(self) -> None:
        repo_root = Path(__file__).resolve().parents[2]
        dataset = repo_root / "security" / "threat-intel" / "v1" / "training.dataset.sample.csv"
        model = train(dataset, deterministic=True)
        self.assertEqual(model["model_version"], "baseline-anomaly-v1")
        self.assertGreaterEqual(model["evaluation"]["accuracy"], 0.5)
        self.assertEqual(model["guardrails"]["low_confidence_behavior"], "fallback_to_rule_based_detection")
        self.assertTrue(model["explainability_preview"])

    def test_model_serialization_stable(self) -> None:
        repo_root = Path(__file__).resolve().parents[2]
        dataset = repo_root / "security" / "threat-intel" / "v1" / "training.dataset.sample.csv"
        model = train(dataset, deterministic=True)
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "model.json"
            out.write_text(json.dumps(model, sort_keys=True), encoding="utf-8")
            saved = json.loads(out.read_text(encoding="utf-8"))
            self.assertEqual(saved["created_at"], "2026-01-01T00:00:00Z")


if __name__ == "__main__":
    unittest.main()
