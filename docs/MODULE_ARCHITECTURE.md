# Module architecture

CoreGuard is moving from a single `:app` blob toward a **module pattern**: each
feature exposes a small public façade and hides internals. Gradle modules enforce
the hard boundaries; Kotlin `object` façades enforce the soft ones inside `:app`.

## Current Gradle modules

| Module | Kind | Responsibility |
|--------|------|----------------|
| `:app` | Android application | UI, Play wiring, JNI host, feature façades, Activities/Services |
| `:core:model` | Kotlin JVM library | Pure domain types: security check results, Guardian Score / rank |

## Feature façades (module pattern inside `:app`)

| Façade | Hides | UI should call |
|--------|-------|----------------|
| `ScannerModule` | `DeviceScanner`, IOC loaders, `/proc` walkers, `LastScan` writes | Scan / history / latest report |
| `ShieldModule` | `NemesisShield`, `GuardVpnService` intent wiring | Arm / disarm / observe state |
| `BillingModule` | `CoreGuardApplication.billingProvider` lookup | Premium checks / `EntitlementPolicy` |
| `BillingProvider` | Play Billing client details | Injected into Compose screens |
| `SwarmModule` | `SwarmCoordinator`, agent ctors, Redux alert Counter store | Alerts / agent count / increment / reset Counter |
| `EliteModule` | DTS engine, Scam Guard, Forensic Journal, Redux threat Counter | Evaluate DTS / inspect scam / journal export / Counter |
| `GuardianModule` | Oracle, Pulse, Book of Changes, Constellation, Ward Circle, Baseline, Verify, Reports | `refreshIntelligence` / explain / report |
| `QuillaMemoryModule` | Memory snapshot, choir seal, Nemesis→choir bridge, correlator, research/Infinity | Memory / `onScanCompleted` / sync / train |

### Swarm alert Counter (Redux-style, not React-Redux)

Android has no React-Redux runtime. CoreGuard uses a tiny unidirectional store
(`SwarmAlertCounterStore`) behind `SwarmModule.alertCounter`:

- **State** — immutable `SwarmAlertCounterState`
- **Actions** — `AlertObserved` / `Increment` / `Reset`
- **Reducer** — pure `SwarmAlertCounterStore.reduce`
- **Subscribe bridge** — `ui.redux.rememberSwarmAlertCounterState`
- **UI** — `SwarmAlertCounter` Compose only **paints** and calls
  `SwarmModule.incrementAlertCounter()` / `resetAlertCounter()` (no Action imports)

Do not put agent registration or native RASP inside the Counter composable.

### Elite threat Counter (same Redux contract)

`EliteThreatCounterStore` behind `EliteModule.threatCounter` holds Dynamic Threat
Score + Scam amber count. Subscribe via `ui.redux.rememberEliteThreatCounterState`.
Presentation: `EliteThreatCounter`. Engines feed the store only through
`EliteModule.evaluateThreatScore` / `inspectScamText`. Home must not open a
raw `DisposableEffect` on the store — use the redux helpers.

## Target modules (post–Internal Testing)

Extract in this order once Play smoke is green:

1. `:core:security` — evaluators + native RASP
2. `:feature:scanner` — MVT / IOC (non-VPN)
3. `:feature:shield` — VPN service (keep class names stable for Play)
4. `:feature:billing` — Play Billing impls
5. `:feature:quilla` + `:data:quilla` — unify the three Quilla package roots
6. `:feature:compliance`, `:feature:lab`, `:feature:supply`, `:feature:swarm`

Dependency rule: `:app` → feature modules → `:core:*`. No feature→feature cycles;
share contracts through `:core:model` (and later `:core:security` APIs).

## Why not a full split before Wednesday

Play surface (VPN service name, billing, ProGuard, NDK) and entitlement wiring are
still concentrated in `:app`. A big-bang multi-module move risks CI/Play breakages
without changing user-visible readiness. This slice lands the pattern and the
first hard module boundary without blocking Internal Testing.
