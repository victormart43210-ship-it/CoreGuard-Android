# Pegasus Scanner & Domain Blocker

CoreGuard ships an on-device **Pegasus scanner** and a **spyware-domain
blocker**, inspired by Amnesty International's
[Mobile Verification Toolkit (MVT)](https://github.com/mvt-project/mvt).

> **Attribution & honesty.** CoreGuard is **not affiliated with or endorsed by
> Amnesty International**. It reuses the *format and public indicators* from the
> MVT project. An in-app scanner on a non-rooted device has far less visibility
> than MVT run against a full forensic acquisition, so a `CLEAN` result is **not
> a guarantee** the device is uncompromised.

## What it does

### 1. Forensic scanner (`mvt` package)
Enumerates on-device artifacts and matches them against a set of
mercenary-spyware Indicators of Compromise (IOCs):

| Artifact | Source | Indicator types |
|----------|--------|-----------------|
| Installed apps | `PackageManager` | `package` |
| Processes / threads | `/proc/*/cmdline`, `/proc/*/comm` (best-effort) | `process` |
| Accessible files | app storage + Downloads | `filename`, `filepath` |

Each match becomes a `Detection` with a severity, and the scan produces a
verdict: `CLEAN`, `SUSPICIOUS`, or `INFECTED`. The latest verdict is also
surfaced on the Security Dashboard (`Spyware Scan (MVT)`).

### 2. Domain blocker (`GuardVpnService`)
A userspace, **non-root** `VpnService` that acts as a DNS sinkhole. It captures
DNS queries, and:
- answers **NXDOMAIN** for any domain on the IOC list (so a compromised app
  cannot resolve the spyware C2 address), and
- forwards every other query to the real upstream resolver through a protected
  socket.

**Limitation:** it filters by domain, so traffic to a **hardcoded IP address**
(no DNS lookup) is not blocked. It also requires the user to grant the system
VPN permission.

## Indicators of Compromise

Indicators are loaded, merged, and de-duplicated from:
1. Bundled JSON under `app/src/main/assets/ioc/` (curated public Pegasus
   indicators from Amnesty / `mvt-project`, plus clearly-marked `EXAMPLE`
   entries used only for tests/demos).
2. User-imported JSON feeds dropped into the app's `filesDir/ioc/` directory.
3. A hardcoded fallback (`DefaultIndicators`).

### Accepted IOC formats
- **CoreGuard**: `{ "indicators": [ { "type", "value", "malware", "reference" } ] }`
- **STIX2**: a bundle whose `objects` contain `indicator` entries with a
  `pattern` and `name` (a subset of the STIX2 pattern grammar is parsed).

### Updating indicators
Download a fresh feed from the
[`mvt-project/mvt-indicators`](https://github.com/mvt-project/mvt-indicators)
repository (STIX2), convert or copy it into the `ioc` folder, and call
`IocRepository.invalidate()` (or restart the app) to reload.

## Testing

Pure logic is covered by unit tests (`app/src/test/.../mvt/`): the IOC matcher,
scanner classification, the DNS query parser / NXDOMAIN builder, and the
IPv4/UDP packet reader-writer. Run them with:

```bash
./gradlew testDebugUnitTest
```
