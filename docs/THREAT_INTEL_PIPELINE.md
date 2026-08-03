# Ethical threat-intel and learning pipeline

This pipeline is intentionally limited to legal/open sources and deterministic processing paths for closed-testing readiness.

## 1) Approved sources only

Source allowlist: `/home/runner/work/CoreGuard-Android/CoreGuard-Android/security/threat-intel/v1/sources.allowlist.v1.json`

Allowed source categories:
- NVD CVE API
- Android/Google security advisories
- Public vendor advisories (for example Qualcomm)
- Public disclosure blogs tied to advisories (Project Zero)

Prohibited collection behavior:
- No private/leaked feeds
- No scraping private chats/forums
- No personal data, credentials, or device identifiers
- No malware binaries in this ingestion path

## 2) Detection rules and tuning

Rules config: `/home/runner/work/CoreGuard-Android/CoreGuard-Android/security/threat-intel/v1/detection.rules.v1.json`

Rule groups:
- Suspicious Android permission combinations
- Unusual network behavior thresholds
- Known exploit indicators tied to public advisories

Tuning knobs for false-positive reduction:
- `tuning.model_confidence_threshold`
- `tuning.rule_vote_threshold`
- Per-rule threshold fields in network and permission rules

When confidence is lower than threshold, fallback is always `rule-based-detection`.

## 3) Safe training scaffold

Dataset and model scaffold paths:
- Dataset interface (CSV): `/home/runner/work/CoreGuard-Android/CoreGuard-Android/security/threat-intel/v1/training.dataset.sample.csv`
- Trainer: `/home/runner/work/CoreGuard-Android/CoreGuard-Android/scripts/threat_intel/train_baseline_model.py`
- Model artifact: `/home/runner/work/CoreGuard-Android/CoreGuard-Android/security/threat-intel/models/baseline-model.v1.json`

Safety constraints:
- Uses only historical/public indicator-style features
- No private telemetry required
- Includes explainability output (`top_feature_contributions`)
- Includes confidence thresholding and low-confidence fallback policy

## 4) Integrity and attribution

Ingestion script:
`/home/runner/work/CoreGuard-Android/CoreGuard-Android/scripts/threat_intel/ingest_open_sources.py`

Generated artifact sidecars:
- `.sha256`: deterministic hash for integrity checks
- `.sig`: detached HMAC signature when a key is provided (otherwise marked `UNSIGNED`)

Each record is source-attributed and timestamped in the bundle.

## 5) Continuous-learning workflow and rollback safeguards

Workflow script:
`/home/runner/work/CoreGuard-Android/CoreGuard-Android/scripts/threat_intel/continuous_learning.py`

Safeguards:
- Schema validation gate before update
- Drift check (centroid-distance threshold)
- Quality regression check (accuracy tolerance)
- Rollback snapshot written to `baseline-model.rollback.json` on regression

## 6) Safe source update process

1. Edit allowlist JSON and detection rules JSON in the same PR.
2. Run:
   - `python3 scripts/threat_intel/validate_configs.py`
   - `python3 -m unittest scripts.tests.test_threat_intel_pipeline`
3. Regenerate deterministic artifacts:
   - `python3 scripts/threat_intel/ingest_open_sources.py --deterministic`
   - `python3 scripts/threat_intel/train_baseline_model.py --deterministic`
4. Update this document if allowlisted sources or prohibited policies changed.
5. Require security reviewer approval before merge.
