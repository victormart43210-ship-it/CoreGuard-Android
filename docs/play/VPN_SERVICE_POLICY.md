# VPN Service Policy — CoreGuard (CoreGuard V3.1A)

> Status: current-state documentation for Google Play VpnService policy readiness.
> This document is referenced by `scripts/policy/verify_play_policy.py` (check
> `vpn_service_policy_doc` and `prominent_disclosure_doc`). It describes what the
> app declares today; it is **not** a claim of Play approval.

## 1. VpnService usage

CoreGuard declares an on-device privacy VPN implemented by
`com.coldboar.coreguard.mvt.GuardVpnService`. The VPN is **core functionality**,
not an optional feature: it blocks outbound connections to domains known to track
or surveil users (spyware/tracker blocking at the network layer).

In `app/src/main/AndroidManifest.xml` the service is declared as:

```xml
<service
    android:name=".mvt.GuardVpnService"
    android:exported="false"
    android:permission="android.permission.BIND_VPN_SERVICE"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Runs an on-device privacy VPN that blocks connections to servers known to track or surveil users" />
    <intent-filter>
        <action android:name="android.net.VpnService" />
    </intent-filter>
</service>
```

## 2. Policy-relevant declarations (current state)

| Requirement | Value | Source |
| --- | --- | --- |
| VpnService usage detected | Yes — `GuardVpnService` declares `android.net.VpnService` | `AndroidManifest.xml` |
| `BIND_VPN_SERVICE` permission on the service | Yes (`android:permission`) | `AndroidManifest.xml` |
| Service exported | `false` (not exported) | `AndroidManifest.xml` |
| `FOREGROUND_SERVICE` permission | Declared | `AndroidManifest.xml` |
| `FOREGROUND_SERVICE_SPECIAL_USE` permission | Declared | `AndroidManifest.xml` |
| `foregroundServiceType` | `specialUse` | `AndroidManifest.xml` |
| `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` | Set to the privacy-VPN description above | `AndroidManifest.xml` |
| `REQUEST_INSTALL_PACKAGES` | **Not declared** (must stay absent) | `AndroidManifest.xml` |
| `allowBackup` | `false` | `AndroidManifest.xml` |
| `usesCleartextTraffic` | `false` | `AndroidManifest.xml` |
| Privacy policy reference | `app/src/main/java/com/coldboar/coreguard/ui/screens/PrivacyPolicyScreen.kt` + `docs/privacy-policy.html` | source tree |

These conditions are enforced as a static preflight gate by
`scripts/policy/verify_play_policy.py`, which exits nonzero if any required
condition is violated.

## 3. Why a VPN (foreground service of type `specialUse`)

The VPN intercepts outbound DNS/connection attempts and refuses those targeting
domains on the signed threat-intelligence bundle. Because it must run while the
app is in the background to be effective, it is hosted in a foreground service.
`specialUse` is the correct foreground-service type for a VPN whose purpose is
not captured by the dedicated location/media/health types; the
`PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property states the concrete purpose.

## 4. Prominent in-app disclosure

Because the VPN intercepts network traffic, Google Play requires a **prominent
in-app disclosure** presented during normal use of the feature — not buried in a
menu or settings list, not combined with other unrelated disclosures, and
requiring the user's affirmative consent before the VPN is established.

CoreGuard's disclosure flow:

1. The disclosure is shown on the VPN/Shield screen the first time the user
   attempts to enable the privacy VPN, as part of the normal enablement flow —
   not behind a nested settings menu.
2. The disclosure explains, in plain language: the VPN intercepts network
   connections on the device, the purpose (blocking known tracker/surveillance
   domains), and that no browsing history leaves the device.
3. The user must take an affirmative action (an explicit "Enable" control) to
   consent. The VPN is **not** started until affirmative consent is given.
4. The disclosure is presented on its own — it is not bundled with the billing
   paywall, the notification-listener consent, or any other permission request.
5. The user can disable the VPN at any time from the same screen; disabling
   tears down the `VpnService` connection.

This prominent-disclosure requirement is documented here so that the static gate
(`verify_play_policy.py`) can confirm the documentation exists; the runtime
consent flow itself is implemented in the UI layer.

## 5. Play Console VPN declaration form (required, not yet proven)

Submitting an app that declares a VpnService to Google Play requires completing
the **VPN declaration form** in the Play Console and is **subject to Google
Play review and approval**. CoreGuard's current source state is structured to
satisfy the documented policy requirements, but Play approval is a separate,
Google-controlled process and is **not yet proven**. This document makes no claim
that Google Play has approved the VPN usage.

Reference: [Google Play VpnService policy](https://developer.android.com/guide/playcore/permissions/vpn)
and the [Google Play policy center](https://support.google.com/googleplay/android-developer/topic/9858016).

## 6. Data safety

The machine-readable data-safety inventory lives at
`scripts/policy/data_safety_inventory.json`, validated by
`scripts/policy/verify_data_safety_drift.py`. The human-readable mapping is at
`docs/play/DATA_SAFETY_MAP.md`.
