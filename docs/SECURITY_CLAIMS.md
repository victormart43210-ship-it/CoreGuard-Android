# Security & product claims (allowed vs forbidden)

This matrix keeps CoreGuard copy honest. Prefer this over marketing impulse.

## Allowed claims

| Claim | Why it is allowed |
|-------|-------------------|
| On-device privacy-integrity / spyware **indicator** checks | Nemesis Scanner matches local artifacts against IOC lists |
| Aggregate CPU usage is a **BASIC** `/proc/stat` sample | `CpuUsageCalculator`; not per-process and not a security verdict |
| Live Security Score refreshes on Home and via hourly WorkManager pulse | Weighted local `SecurityCheckRunner` summary; battery-not-low constraint; not cloud threat intel |
| Threat timeline chart shows past privacy-check verdicts / flag counts | `ScanHistoryStore` history visualization — not continuous network IPS |
| A clean scan is **reassuring, not a guarantee** | Explicitly disclosed in Scanner UI and store copy |
| Privacy Shield can block domains matching known indicators | DNS filter VPN; requires user VPN consent |
| Guardian Score summarizes local heuristic checks | Root/debugger/emulator/signature/build heuristics |
| Quilla is an **on-device** agent (no cloud LLM) | Local knowledge + evidence; no ChatGPT/Claude keys |
| Signed telemetry deltas stay on-device unless user opt-in export exists | `TelemetryBridge` ring buffer; Keystore ECDSA when available |
| Optional server-side Quilla hypothesis evaluator may use an LLM | `scripts/agents/quilla_hypothesis_evaluator.py` only; not shipped as on-device Quilla |
| Threat-intel hardening pipeline ingests legal/open advisories (NVD CVE + public vendor advisories) and keeps signed/hashed provenance artifacts | `security/threat-intel/v1/*`, `scripts/threat_intel/*`; optional HTTPS; no private/leaked feeds |
| Shared threat-knowledge query layer can merge Anki-backed codex entries with Viper threat-intel records | `SharedThreatKnowledgeRepository` + `ViperThreatIntelImporter`; Viper is sanitized/validated and mapped to `CyberKnowledgeBase.Entry` |
| Premium unlocks signature refresh, JSON export, longer timeline, coaching tips | Matches `EntitlementPolicy` |
| Optional HTTPS for IOC/STIX refresh and billing | Documented in Privacy Policy |
| Guardian Truth Seals label Observed / Inferred / Simulation / Unavailable / User reported | `EvidenceClass` + `TruthSeal`; never presents inferred/simulated as confirmed intrusion |
| Guardian Pulse summarizes posture from findings + data availability | Never maps "no data" to Protected |
| Signing mismatch: "does not match official signing identity" | Not automatic "malware" labeling for forks |
| Ward Circle is hardening progress, not immunity | Explicit copy: not unhackable |
| Evidence Constellation describes co-occurrence only | Conservative correlation; max confidence below VERIFIED unless verified signature inputs |

## Forbidden / overclaim language

| Do not say | Why |
|------------|-----|
| Guaranteed spyware detection or removal | App cannot prove absence of spyware or uninstall foreign implants |
| CPU usage is “Simulated” | Implementation samples `/proc/stat` on-device (coarse BASIC metric) |
| “Pegasus blocker” / “removes Pegasus” as a product claim | Shield is a DNS sinkhole for listed indicator domains only |
| “100% offline” / “fully offline” as absolute | Billing, optional IOC refresh, Quilla Research sync, and Shield DNS forwarding use network |
| Quilla “automates defenses” or silently runs scans/VPN | Actions **navigate** / suggest; VPN still needs Android consent |
| “Live continuous threat intel” for Quilla Research | Optional pull of public Amnesty/MVT STIX archives, not a live feed |
| “Viper alert = confirmed infection” | Viper entries are knowledge/correlation clues only and are capped as non-proof context |
| Quilla Research sync refreshes Nemesis Scanner signatures | Separate Premium Scanner path (`IocFeedFetcher`); Research is correlator-only |
| “Release-ready” / Play approval guaranteed | External Console, signing, device, and policy reviews remain |
| DemoBilling is the production path | Production uses `PlayBillingProvider`; unavailable billing fails closed (`FailClosedBillingProvider`) |
| MASVS “compliance certified” | Educational mapping / coverage scores only |

## Premium honesty

- Core scan, Privacy Shield, Guardian Score, and Quilla Q&A stay free.
- Premium is: live signature refresh, Compliance JSON export, longer timeline, Premium coaching tips.
- Do not sell Quilla itself as Premium-only while leaving the panel ungated.

## When copy changes

Update this file, `docs/RELEASE_READINESS.md`, store strings, and Quilla blurbs in the same change set when possible.
