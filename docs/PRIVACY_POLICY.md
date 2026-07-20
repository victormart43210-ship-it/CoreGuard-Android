# Privacy Policy — CoreGuard

**Last updated:** July 2026  
**Package:** `com.coldboar.coreguard`

This is a plain-language privacy policy for Play Console listing. Host this file
(or an equivalent page) at a public HTTPS URL and paste that URL into Play Console.

## Summary

CoreGuard is a device security / monitoring prototype. We aim to collect as little
as possible.

## Data the app processes on-device

- **Device security signals** (debugger, emulator/root heuristics, signing certificate
  fingerprint, build type) — evaluated and displayed locally.
- **Memory (RAM) statistics** — read via Android system APIs and shown in the UI.
- **CPU** — currently **simulated** in the UI (not measured).

These checks are not uploaded by the app itself in the current open-source build
except as described under Billing below.

## Billing & purchase verification

- **Debug builds** may use a local demo unlock. That is not a payment.
- **Release builds** use Google Play Billing. When you subscribe, Google processes
  payment under Google’s terms.
- The app may send your **purchase token**, **product id**, and **package name** to
  our billing verification server so we can confirm the subscription with the
  Google Play Developer API. We do not use purchase tokens for advertising.

## Permissions

- `INTERNET` — used to contact the billing verification server on the Play/release path.

No SMS, contacts, precise location, or microphone access is requested by the current build.

## Data sharing

- **Google Play** — for purchases and distribution.
- **Our billing verification host** — purchase token validation only (when configured).
- We do not sell personal data.

## Retention

Purchase verification logs on a self-hosted server should be retained only as long
as needed for fraud prevention and support, then deleted. Configure retention on
your deployment.

## Contact

Replace this section with your support email before publishing:

`support@example.com`

## Changes

We may update this policy when the app’s data practices change. The “Last updated”
date will change accordingly.
