# COREGUARD_BLOCKERS.md

## Audit context
- Branch: `copilot/audit-coreguard-phase0`
- Commit: `8d65355b8606a619ca1f1bf890d402802ca5bf98`

## Owner/infra blockers (cannot be solved by code-only edits here)

| Blocker | Why blocked | Needed to unblock | Owner action |
|---|---|---|---|
| Android dependency resolution in this sandbox | `dl.google.com` unreachable; AGP/SDK artifacts cannot be fetched | Network egress to Google Maven + SDK repos | Provide build environment with required network access and/or mirrored artifacts |
| Play Console product setup | Locked model requires monthly + yearly trial products; code/docs currently only monthly SKU flow | Create/confirm Play products with authoritative IDs and trial terms | Configure products in Play Console and share canonical product metadata |
| Signed release verification | No release keystore secrets available in this audit environment | Signing credentials + expected cert fingerprint | Provide secrets (`SIGNING_*`, `EXPECTED_CERT_SHA256`) in CI/release context |
| Physical device validation | VPN/billing/store behavior needs real-device checks | Device test matrix and tester accounts | Execute and record physical-device runs |
| Backend billing verification + RTDN | Not present in repository | Deployed backend endpoint(s), secure credentialing, RTDN plumbing | Stand up backend services and service accounts; define API contracts |
| Final Play policy approval | External to repo | Play Console review completion | Complete data safety/content/policy questionnaires and resolve review feedback |

## Code/work blockers discovered (implementation gaps)

| Area | Current state | Block type | Needed work |
|---|---|---|---|
| Locked SDK levels | compile/target are 35 | Code/config | Upgrade build config + CI/scripts/docs to 36 |
| Billing version + lifecycle | Billing 7.1.1, boolean entitlement model | Code+product | Upgrade to Billing 9.1.0 and implement full lifecycle state handling |
| Yearly SKU with trial | Not implemented | Code+product | Add yearly SKU constants/offer handling and paywall/entitlement wiring |
| Threat feed authenticity | HTTPS fetch only; no signature/version/rollback checks | Security architecture | Add signed feed metadata verification and anti-rollback logic |
| Truth model consistency | Severity/evidence modeled differently per subsystem | Architecture | Introduce shared finding/evidence model used across Scanner/Shield/Guardian/Quilla |
| Persistent controls | Several security-control toggles are in-memory UI state only | UX integrity | Move controls to DataStore-backed state and enforce in feature logic |

## Verification blockers from this session
- Could not complete successful `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `bundleRelease`, or `build` due AGP dependency fetch failure from `dl.google.com`.
- Could not validate signed AAB behavior, release minification output, or runtime behavior on device in this environment.

