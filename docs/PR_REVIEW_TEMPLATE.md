# PR Review Template — CoreGuard Android Stabilization

## Review decision
- [ ] Approve
- [ ] Comment only
- [ ] Request changes

## Reviewer summary
<!-- 2-5 sentences: what changed, overall risk, and whether this is mergeable now -->

---

## 1) Scope and commit hygiene
- [ ] PR matches the stabilization scope and does not introduce new major features
- [ ] Commits are logically grouped and reviewable
- [ ] Changes are consistent with the documented commit plan
- [ ] Branch still reads as a consolidation/honesty pass, not a feature expansion

**Notes:**
<!-- Mention if any commit mixes unrelated code/docs changes -->

---

## 2) Billing and premium entitlement review

Files to inspect:
- `app/src/main/java/com/coldboar/coreguard/BillingProvider.kt`
- `app/src/main/java/com/coldboar/coreguard/Entitlements.kt`
- `app/src/main/java/com/coldboar/coreguard/PlayBillingProvider.kt`
- `app/src/main/java/com/coldboar/coreguard/PaywallActivity.kt`

Checklist:
- [ ] `BillingProvider.PREMIUM_PRODUCT_ID` is the single authoritative subscription ID
- [ ] Production billing path is centered on `PlayBillingProvider`
- [ ] No production logic routes through `DemoBillingProvider`
- [ ] Pre-init fallback behavior remains safely free-tier
- [ ] Premium and free behavior are consistent across screens
- [ ] Purchase flow wording reflects real Google Play behavior
- [ ] No stale billing aliases/constants remain active

**Billing reviewer notes:**
<!-- Record any mismatch between paywall, settings, entitlement, and purchase restore logic -->

---

## 3) Quilla architecture and honesty review

Files to inspect:
- `app/src/main/java/com/coldboar/coreguard/quilla/QuillaAgentModels.kt`
- `app/src/main/java/com/coldboar/coreguard/quilla/UltimateQuillaAgent.kt`
- `app/src/main/java/com/coldboar/coreguard/ui/components/QuillaAgentPanel.kt`

Checklist:
- [ ] Quilla behaves as one assistant, not multiple conflicting paths
- [ ] Quilla actions point only to real app destinations
- [ ] Quilla does not claim a device is clean without scan evidence
- [ ] Quilla offensive-use refusals remain intact
- [ ] Quilla premium prompts refer only to real premium-gated features
- [ ] Lore/codex language is not presented as detection evidence

**Quilla reviewer notes:**
<!-- Call out any fabricated certainty, dead action chips, or route mismatches -->

---

## 4) Navigation and route review

Files to inspect:
- `app/src/main/java/com/coldboar/coreguard/ui/CoreGuardApp.kt`
- `app/src/main/java/com/coldboar/coreguard/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/coldboar/coreguard/ui/screens/ScannerScreen.kt`
- `app/src/main/java/com/coldboar/coreguard/ui/screens/ShieldScreen.kt`
- `app/src/main/java/com/coldboar/coreguard/ui/screens/SettingsScreen.kt`
- `app/src/main/java/com/coldboar/coreguard/ui/screens/ToolsScreen.kt`

Checklist:
- [ ] Bottom-nav routes remain correct and non-duplicated
- [ ] Home / Scanner / Shield / Compliance / Settings remain primary surfaces
- [ ] Timeline / Tools / Supply Chain / Privacy Policy routes still work
- [ ] Quilla entry points from all touched screens are wired correctly
- [ ] No dead buttons or no-op callbacks remain
- [ ] Back-stack behavior remains sensible

**Navigation reviewer notes:**
<!-- Mention circular routes, wrong targets, unexpected bottom-bar behavior, etc. -->

---

## 5) Startup and application wiring review

Files to inspect:
- `app/src/main/java/com/coldboar/coreguard/CoreGuardApplication.kt`

Checklist:
- [ ] Removed startup wiring does not break runtime assumptions
- [ ] Billing warm-up still makes sense
- [ ] App initialization remains safe and minimal
- [ ] No removed dependency is still implicitly required later

**Startup reviewer notes:**
<!-- Mention any initialization or lifecycle concern -->

---

## 6) Resource and string cleanup review

Files to inspect:
- `app/src/main/res/values/strings.xml`

Checklist:
- [ ] Stale demo-named paywall strings were removed safely
- [ ] No deleted string IDs are still referenced
- [ ] Paywall and premium wording remain honest and consistent
- [ ] No store-facing copy overstates security capabilities

**Resource reviewer notes:**
<!-- Mention any broken resource reference or misleading string -->

---

## 7) Tests added/updated in this PR

Files to inspect:
- `app/src/test/java/com/coldboar/coreguard/PlayBillingContractTest.kt`
- `app/src/test/java/com/coldboar/coreguard/ReleaseReadinessContractTest.kt`
- `app/src/test/java/com/coldboar/coreguard/quilla/UltimateQuillaAgentTest.kt`

Checklist:
- [ ] Tests cover the changed behavior
- [ ] Billing fallback behavior is protected by tests
- [ ] Quilla honesty behavior is protected by tests
- [ ] New tests reflect real documented behavior
- [ ] No test encodes misleading product assumptions

**Test reviewer notes:**
<!-- Mention missing edge cases or overly weak assertions -->

---

## 8) Documentation truthfulness review

Files to inspect:
- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/QUILLA_ARCHITECTURE.md`
- `docs/SECURITY_CLAIMS.md`
- `docs/RELEASE_READINESS.md`
- `docs/PLAY_CONSOLE_CHECKLIST.md`
- `docs/MANUAL_RELEASE_TEST.md`
- `docs/THREAT_MODEL.md`
- `store/README.md`
- `docs/PR_66_RELEASE_SUMMARY.md`

Checklist:
- [ ] README matches the app that actually exists
- [ ] Release docs do not claim successful Android validation that was not run
- [ ] Privacy/network wording is internally consistent
- [ ] Docs no longer imply “fully offline” if billing / IOC refresh / DNS forwarding use network
- [ ] Store guidance clearly requires real screenshots
- [ ] Security limitations are explicit and defensible

**Docs reviewer notes:**
<!-- Note contradictions between README, release docs, store docs, and code -->

---

## 9) Release-readiness gate

Checklist:
- [ ] PR does not falsely imply the app is release-ready today
- [ ] Real Android build/test/lint status is clearly marked as pending unless evidence is attached
- [ ] Physical-device testing is clearly marked as pending unless evidence is attached
- [ ] Play Console tasks are clearly marked as pending unless evidence is attached
- [ ] Signing / `EXPECTED_CERT_SHA256` work is clearly marked as pending unless evidence is attached

**Required evidence before calling this “release-ready”:**
- [ ] `:app:compileDebugKotlin`
- [ ] `:app:testDebugUnitTest`
- [ ] `:app:lintDebug`
- [ ] `:app:assembleDebug`
- [ ] `:app:assembleRelease`
- [ ] `:app:bundleRelease`
- [ ] Physical-device validation
- [ ] Play Billing license-tester validation
- [ ] Real store screenshots
- [ ] Release signing configured

**Release gate notes:**
<!-- If evidence is missing, say exactly what is still blocked -->

---

## 10) Final reviewer recommendation

### Merge status
- [ ] Safe to merge as consolidation branch
- [ ] Mergeable, but only as “not yet release-validated”
- [ ] Needs follow-up PR(s)
- [ ] Should not merge yet

### Blocking issues
1.
2.
3.

### Non-blocking follow-ups
1.
2.
3.

---

# Ready-to-paste review comments

## Comment — billing path
> Please show the full production entitlement path from app initialization through purchase restore and premium gate checks. I want proof that no production logic can still route through `DemoBillingProvider`.

## Comment — Quilla honesty
> I need confirmation that Quilla never claims the device is clean without scan-backed evidence. Please point me to the exact logic and tests that enforce that behavior.

## Comment — route wiring
> Several screens gained Quilla/navigation changes. Please confirm each new action path lands on a real destination and is not just a rendered chip/button with no effective route.

## Comment — release wording
> This PR should not imply release readiness unless real Android build/lint/test output and physical-device validation are attached. Please keep the wording at “stabilized but not fully release-validated” until that evidence exists.

## Comment — network/privacy claims
> Please verify that docs and store-facing copy consistently acknowledge optional network behavior for Google Play Billing, IOC refresh, and allowed DNS forwarding in Privacy Shield.

## Comment — stale resources
> Please confirm removed demo-named paywall strings are no longer referenced anywhere in code or layouts.

---

# Suggested final approval text
> Reviewed the stabilization diff as a consolidation/honesty pass. I verified the main risk areas: billing source-of-truth, Quilla action routing, navigation wiring, and documentation consistency. I am approving this as mergeable only as a stabilized integration branch, not as proof of final release readiness pending real Android toolchain validation, physical-device testing, signing, and Play Console completion.

# Suggested request-changes text
> I’m requesting changes before merge. The branch is directionally correct, but I still need stronger proof on one or more of the following: production billing path isolation, Quilla evidence honesty, route wiring correctness, or documentation consistency around release/network claims.
