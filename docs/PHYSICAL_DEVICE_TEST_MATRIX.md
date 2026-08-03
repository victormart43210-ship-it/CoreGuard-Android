# Physical-device matrix for closed testing

Use this matrix before promoting from Internal Testing to Closed Testing.

## Device/OS matrix

| Class | Example devices | Android versions |
|---|---|---|
| Low-end | Android Go / 3-4GB RAM class | 10, 11 |
| Mid-range | Pixel a-series / Galaxy A-series | 12, 13 |
| Flagship | Pixel / Galaxy S / OnePlus flagship | 14, 15 |
| Vendor skin variance | Samsung One UI / Xiaomi / Oppo | Latest supported |

## Network condition matrix

| Condition | Expected behavior |
|---|---|
| Stable Wi-Fi | All screens load; optional intel refresh succeeds |
| Metered LTE/5G | No crash; retries/backoff remain bounded |
| High-latency (>=300ms) | Refresh may slow but should not freeze UI |
| Intermittent offline | Graceful fallback; no false success claims |

## Core scenarios and pass/fail criteria

| Scenario | Pass criteria | Fail examples |
|---|---|---|
| Scanner baseline | Scan completes with clear verdict text | Crash/hang, empty verdict |
| Permission/network rules | Relevant alerts fire only when thresholds exceeded | Constant noisy false positives |
| Exploit indicator matching | Public advisory-linked indicators are attributable | Indicator with no source/advisory linkage |
| Low-confidence ML path | Falls back to rule-based detection | Model returns confident verdict below threshold |
| Signed release installability | Signed closed-test build installs and runs | Signature mismatch/install rejection |
| Billing smoke (`coreguard_premium_monthly`) | Purchase/restore/cancel path non-crashing | Stuck entitlement or uncaught errors |

## Defect logging format

Use this exact format for each failure:

```
[Device] <model>/<os_version>
[Build] <versionName/versionCode + git SHA>
[Network] <wifi|lte|offline|high-latency>
[Scenario] <name>
[Expected] <expected result>
[Actual] <observed result>
[Artifacts] <screenshot/video/logcat path>
[Severity] <blocker|high|medium|low>
```

## Exit criteria for closed-testing readiness

- All blocker/high defects resolved or explicitly risk-accepted
- Matrix covers at least 1 low-end, 1 mid-range, and 1 flagship physical device
- Offline/high-latency scenarios executed at least once
- Evidence attached to release decision notes
