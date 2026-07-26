# Security & product claims (allowed vs forbidden)

This matrix keeps CoreGuard copy honest. Prefer this over marketing impulse.

## Allowed claims

| Claim | Why it is allowed |
|-------|-------------------|
| On-device privacy-integrity / spyware **indicator** checks | Nemesis Scanner matches local artifacts against IOC lists |
| A clean scan is **reassuring, not a guarantee** | Explicitly disclosed in Scanner UI and store copy |
| Privacy Shield can block domains matching known indicators | DNS filter VPN; requires user VPN consent |
| Guardian Score summarizes local heuristic checks | Root/debugger/emulator/signature/build heuristics |
| Quilla is an **on-device** agent (no cloud LLM) | Local knowledge + evidence; no ChatGPT/Claude keys |
| Signed telemetry deltas stay on-device unless user opt-in export exists | `TelemetryBridge` ring buffer; Keystore ECDSA when available |
| Optional server-side Quilla hypothesis evaluator may use an LLM | `scripts/agents/quilla_hypothesis_evaluator.py` only; not shipped as on-device Quilla |
| Quilla Intel Network can pull public Amnesty/MVT STIX, CISA KEV, and MISP Android briefs | `QuillaIntelNetwork` + `PublicMultiSourceStixFetcher`; optional HTTPS; defensive only |
| Premium unlocks signature refresh, JSON export, longer timeline, coaching tips | Matches `EntitlementPolicy` |
| Optional HTTPS for IOC/STIX refresh and billing | Documented in Privacy Policy |

## Forbidden / overclaim language

| Do not say | Why |
|------------|-----|
| Guaranteed spyware detection or removal | App cannot prove absence of spyware or uninstall foreign implants |
| “100% offline” / “fully offline” as absolute | Billing, optional IOC refresh, Quilla Research sync, and Shield DNS forwarding use network |
| Quilla “automates defenses” or silently runs scans/VPN | Actions **navigate** / suggest; VPN still needs Android consent |
| “Live continuous threat intel” for Quilla Research | Optional pull of public Amnesty/MVT STIX archives, not a live feed |
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
