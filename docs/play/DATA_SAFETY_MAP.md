# Data Safety Mapping — CoreGuard

> Human-readable mapping of declared sensitive data flows. The
> machine-readable source of truth is
> `scripts/policy/data_safety_inventory.json`, validated by
> `scripts/policy/verify_data_safety_drift.py`. This document exists so the Play
> policy gate (`scripts/policy/verify_play_policy.py`, check `data_safety_map_doc`)
> can confirm a Data Safety mapping is present.

## How to read this mapping

Each category below records four flags — **collected**, **transmitted**,
**stored_locally**, **stored_remotely** — plus a purpose, retention period, and
user control. "not_applicable" is used where a field genuinely does not apply
rather than guessing.

## Categories

### device_security_findings
On-device malware/spyware scan results. Collected on device, **not transmitted**,
stored locally, presented to the user. Cleared on next scan or via settings.

### app_package_metadata
Installed-app enumeration (`QUERY_ALL_PACKAGES`) to match against spyware package
indicators. Collected locally, **not transmitted**, session/scoped retention.
Declared to Play Console as a core device-security feature.

### network_security_observations
The privacy VPN observes connection attempts to known tracker/surveillance
domains. Collected on device, **not transmitted**, per-session retention.

### crash_performance_telemetry
**Not collected.** No crash or performance SDK is integrated. All flags false.

### authentication_identity
Play Billing obfuscated account ID used for entitlement verification. No raw
account IDs are collected or stored remotely by CoreGuard.

### billing_entitlement
Premium subscription entitlement via Google Play Billing. Transmitted to the
Play Billing service; entitlement cached locally.

### threat_intelligence_requests
Signed threat-intelligence bundles downloaded from the Cloud Run backend.
Collected, transmitted (download), stored both locally and remotely (backend
serves signed bundles).

### quilla_inputs_outputs
Quilla defensive crawler (operator tooling, not app runtime) gathers
open-source intel into signed bundles. Stored locally as artifacts.

## Drift gate

`verify_data_safety_drift.py` fails if any required category is missing or if a
category has contradictory flags (e.g. data stored remotely but never
transmitted without a purpose explaining local-only remote storage). Run it from
the repo root: `python3 scripts/policy/verify_data_safety_drift.py`.
