# COREGUARD_SECURITY_CLAIMS_MATRIX.md

Initial audit matrix of user-facing security claims found in app strings/UI/docs.

| User-facing claim | Producing feature | Evidence source | Android/platform limitation | Test proving behavior | Allowed store wording | Prohibited wording |
|---|---|---|---|---|---|---|
| “Know your device's risk in minutes” | Guardian Score + local checks | `app/src/main/res/values/strings.xml` (`store_short_description`, `store_full_description`) | Heuristic checks cannot prove total compromise absence | `app/src/test/.../GuardianScoreTest.kt` | “Helps assess device risk posture” | “Proves device is safe” |
| “Guardian Score … root/debugger/emulator/build/signature/spyware indicators” | `SecurityCheckRunner` + `GuardianScore` | strings + `SecurityCheckRunner.kt` | Signals are mixed heuristic/verified; not comprehensive malware proof | `SecurityCheckRunnerConcurrentTest.kt`, `GuardianScoreExplainTest.kt` | “Summarizes multiple local checks” | “Detects every attack vector” |
| “Check against known spyware indicators” | Nemesis scanner IOC matching | strings + `NemesisScanner.kt`, `IocMatcher.kt` | Visibility limited on non-rooted Android; app sandbox limits file/process access | `mvt/NemesisScannerTest.kt`, `mvt/IocMatcherTest.kt` | “Checks for known indicators in observable artifacts” | “Finds all spyware” |
| “A clean result is reassuring — not a guarantee” | Nemesis result copy honesty | strings + `ScannerScreen.kt` | True limitation statement | N/A (copy behavior) | Keep as-is honesty wording | “No threats found = fully safe” |
| “On-device DNS-filtering VPN can block indicator domains” | Privacy Shield (`GuardVpnService`) | strings + `ShieldScreen.kt`, `GuardVpnService.kt` | DNS-only path; cannot block hardcoded-IP traffic and non-DNS vectors | `mvt/DnsFilterTest.kt`, `mvt/IpV4UdpTest.kt` | “Can block matching DNS indicator domains” | “Blocks all spyware traffic” |
| “Requires VPN consent” | Shield start flow | `ShieldScreen.kt`, `GuardVpnService.kt` | User/system consent required by Android; app cannot bypass | Manual/device required (NONE in this session) | “Requires explicit VPN permission” | “Auto-enables silently” |
| “Scans and reports stay on your device” | local storage and local processing | strings + `ScanHistoryStore.kt`, `LocalSecurityData.kt`, `docs/privacy-policy.html` | Optional network features exist (billing/feed refresh/allowed DNS forwarding) | Unit tests for local stores; no network isolation test in session | “Core scans are local; optional network features exist” | “100% offline always” |
| “CoreGuard does not route all traffic through our servers” | Shield architecture | `strings.xml`, `GuardVpnService.kt` | Allowed DNS queries forwarded to system/upstream resolver, not CoreGuard backend | `DnsFilterTest.kt` (DNS parsing/blocking only) | “No CoreGuard traffic relay backend in this repo” | “Complete network anonymity/VPN provider” |
| “Quilla is on-device and evidence-led” | Quilla agent modules | `QuillaAgentPanel.kt`, `UltimateQuillaAgent.kt` | No external LLM backend here, but responses are heuristic synthesis | multiple `quilla/*Test.kt` suites | “On-device assistant grounded in local context” | “Forensic oracle with guaranteed correctness” |
| “Quilla will not bypass consent or run shield silently” | Quilla action routing copy | `UltimateQuillaAgent.kt`, `QuillaActionRouter.kt` | Correct Android limitation statement | `QuillaActionRouterTest.kt` | Keep explicit consent language | “Quilla auto-secures device without prompts” |
| “Live signature refresh (Premium)” | `IocFeedFetcher` + entitlement gate | `ScannerScreen.kt`, `IocFeedFetcher.kt`, `EntitlementPolicy.kt` | HTTPS transport only; no feed signature verification | `EntitlementPolicyTest.kt` | “Refreshes remote indicator feed over HTTPS” | “Cryptographically trusted zero-risk feed” |
| “Compliance JSON export” | premium compliance/report feature | `EntitlementPolicy.kt`, `compliance/*` | Export correctness depends on local check quality | `ComplianceReportExporterTest.kt` | “Exports local report data” | “Certified compliance guarantee” |
| “Timeline shows past checks” | Scan history timeline | `TimelineScreen.kt`, `ScanHistoryStore.kt` | Historical local snapshots only; not continuous monitoring | `ThreatTimelineVizTest.kt` | “Shows recorded past scans” | “Real-time always-on forensic monitoring” |
| “Scam Guard optional notification access, on-device heuristics” | Notification listener + scam engine | `ScamGuardNotificationListener.kt`, `ScamGuardEngine.kt`, privacy policy docs | Depends on user granting notification listener permission | `elite/ScamGuardEngineTest.kt` | “Optional on-device heuristic analysis” | “Guaranteed phishing prevention” |
| “Play Billing secure checkout; app never sees card details” | Play Billing flow | strings + `PlayBillingProvider.kt` | Entitlement trust still client-side without backend verification | `PlayBillingContractTest.kt` (contract-level) | “Checkout handled by Google Play” | “Fraud-proof entitlement security” |
| “Runtime integrity checks (debugger/hooking/root etc.)” | Tamper + security evaluators | `tamperguard.cpp`, `NativeTamperGuard.kt`, `SecurityCheckEvaluators.kt` | Anti-tamper is best-effort; advanced bypasses possible | `TamperEvaluatorTest.kt` + related tests | “Best-effort runtime tamper signals” | “Unbypassable anti-hacking” |
| “Do not claim guaranteed Play approval/release readiness” | Release docs honesty policy | `docs/RELEASE_READINESS.md`, `docs/SECURITY_CLAIMS.md` | Approval controlled by Google review process | NONE | “Readiness steps and known gaps” | “Guaranteed approval/certification” |
| “No backend account upload for core scan results in v1” | privacy model | `docs/RELEASE_READINESS.md`, `docs/privacy-policy.html` | Optional network operations still happen for selected features | NONE in this session | “No account backend for core local scans in current repo” | “No network usage at all” |

## Cross-cutting truth risks identified from claim mapping
1. Threat-feed cryptographic authenticity is not implemented; keep wording limited to HTTPS refresh.
2. Scanner visibility limitations on non-rooted Android must remain explicit.
3. Billing entitlement should not be marketed as strongly verified until backend token verification + RTDN are implemented.
4. “Control” toggles that are in-memory only should not be marketed as persistent protection policy.

---

## Phase 1 updates (2026-08-01)

### Claims affected by truth model / persistent controls / scanner cancellation

| User-facing claim | Change in Phase 1 | New state |
|---|---|---|
| "Control toggles for real-time monitoring, deep scan, Quilla, intel" | Previously in-memory only; now persisted via DataStore | `realTimeMonitoringEnabled` wired; deep/quilla/intel disabled with "NOT YET AVAILABLE" label until backend honors the value |
| "Scanner shows scan progress / stages" | Previously: fake time-animated stage loop | Now: indeterminate progress with label "Estimated progress — scan in progress"; no fake stage numbers |
| "Scan result / verdict" | Previously: any scan produced a verdict regardless of completion | Now: `ScannerUiState.Cancelled` explicitly holds no verdict/score; incomplete scan results are not displayed as conclusive |
| "Evidence confidence labels" | Previously: plain text color-only label ("Verified signal", "Heuristic", "Guidance") | Now: `TruthSeal` composable with icon + text label mapped from `EvidenceClass` enum; accessible to users with color-vision deficiency |
| "Evidence class" | No shared model | Now: `EvidenceClass { OBSERVED, INFERRED, SIMULATED, UNAVAILABLE, USER_REPORTED }` — UNAVAILABLE is never shown as "safe"; absence of evidence is not "Protected" |
| "Guardian Score backing data" | `EvidenceKind` text only | Now: `GuardianScoreEvidence.toFinding()` maps to normalized `Finding` with explicit `evidenceClass`, `confidence`, `androidVisibilityLimits` |

### Still prohibited (unchanged)
- "Protected" state must never be inferred from absent data — UNAVAILABLE is UNAVAILABLE.
- Cancelled or incomplete scans must not produce a verdict or score.
- In-memory toggle state must never be marketed as a persistent system security control.

---

## Phase 3 updates (2026-08-02)

| User-facing claim | Change in Phase 3 | New state |
|---|---|---|
| "Scanner stages represent real engine work" | UI-owned stage messaging replaced by engine-emitted stage events | Scanner emits required stage IDs, labels, optional units, timestamps, and visibility limitations |
| "Cancelled scan handling" | Cancellation only affected UI job state previously | Cooperative cancellation now checked through expensive enumeration/matching path and persisted as `CANCELLED` session |
| "Failed scan handling" | Failure state was UI-only | Failed sessions now persist `FAILED` terminal state with reason and stage timeline |
| "Deep file inspection" | Toggle persisted but behavior unchanged | Toggle now controls scan behavior; disabled mode records an explicit skipped stage |
| "Quilla correlation control" | Quilla correlation always executed | Quilla correlation now gated by persisted setting |
| "Threat feed trust level" | Premium refresh copy implied freshness but not trust class | Session save records feed source and labels current authenticity as transport-protected, not cryptographically signed |
| "No definitive safety language" | Prior copy referenced reassuring clean result | Scanner summary now states: "No known indicators were observed in the data Android allowed CoreGuard to inspect." |

### Claims narrowed in this phase
- IOC string matches are no longer treated as automatically critical by default scanner severity mapping.
- Scanner copy now avoids definitive "safe/clean/protected" conclusions and reiterates Android visibility limits.
