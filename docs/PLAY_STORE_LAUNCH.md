# CoreGuard Play Store Launch Guide

> Maps the operator launch checklist to this repository.  
> **Does not** claim Play approval, completed billing, or fake purchase verification.

Companion docs: [RELEASE_READINESS.md](RELEASE_READINESS.md) · [PRIVACY_POLICY.md](PRIVACY_POLICY.md) · [store-listing/](store-listing/)

---

## 1. Prerequisites

| Item | Repo status |
|------|-------------|
| Google Play Developer account ($25) | Operator action |
| Signed AAB | `./gradlew bundleRelease` with `KEYSTORE_*` env (see §2) |
| App icon 512×512 | [`store-listing/app_icon_512.png`](store-listing/app_icon_512.png) |
| Feature graphic 1024×500 | [`store-listing/feature_graphic_1024x500.png`](store-listing/feature_graphic_1024x500.png) |
| 2–5 screenshots | Placeholder PNGs in `store-listing/` — **replace with device shots** |
| Privacy Policy URL | Host [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md) on HTTPS |

---

## 2. Build & signing

```bash
# Generate once; store OUTSIDE git
keytool -genkeypair -v \
  -keystore "$HOME/coreguard-secrets/coreguard-release.jks" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias coreguard

export KEYSTORE_PATH="$HOME/coreguard-secrets/coreguard-release.jks"
export KEYSTORE_PASSWORD='…'
export KEY_ALIAS=coreguard
export KEY_PASSWORD='…'

./gradlew bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

- Signing is wired in `app/build.gradle.kts` **only when** `KEYSTORE_PATH` is set.
- Never commit `*.jks` / passwords. CI blocks common secret paths when the security gate is enabled.

Without a keystore, `bundleRelease` still builds an **unsigned** bundle (or fails signing attach) — use env for Play uploads.

---

## 3. Play Console setup

1. Create app → package **`com.coldboar.coreguard`**
2. App type: **App** · Category: **Tools** (suggested)
3. Complete content rating + target audience
4. Complete **Data Safety** (see §5)
5. Upload AAB to **Internal testing** first

---

## 4. Store listing assets

Copy from [`store-listing/README.md`](store-listing/README.md):

- Short description (≤80 chars)
- Full description (honest prototype language)
- Icon + feature graphic + screenshots

---

## 5. Policies & compliance

| Requirement | This build |
|-------------|------------|
| Privacy policy link | Required — host `PRIVACY_POLICY.md` |
| Background services | None declared |
| Network access | No `INTERNET` permission on this branch’s core app |
| VPN / device monitoring | Local heuristics + teaching lab only — disclose; **no** spying/hacking claims |
| Misleading security claims | Forbidden — keep “heuristic / simulated / demo” labels |

Removed unused `READ_PHONE_STATE` (not needed for RAM via `ActivityManager`).

---

## 6. Testing & release

1. Internal testing → real devices  
2. Fix crashes/ANRs  
3. Closed testing  
4. Gradual production rollout  

```bash
./gradlew test assembleDebug lintDebug
cd cli && go test -race ./...   # if shipping the companion CLI docs
```

---

## 7. Backend readiness

| Guide item | Honest status on this branch |
|------------|------------------------------|
| Deployed API | **Not required** for core offline prototype |
| HTTPS endpoints | N/A until verify/billing backend ships |
| Auth (Firebase/JWT) | **No login** in this build |
| Rate limiting / logging | N/A without backend |

When Play Billing + verification are added, deploy HTTPS verify, update Data Safety, and **do not** grant premium from client alone.

---

## 8. Final checklist

- [x] No hardcoded keystore secrets in repo
- [x] Privacy policy draft committed
- [x] Store listing copy + graphic placeholders
- [x] Unused phone-state permission removed
- [ ] Operator: Play Developer account
- [ ] Operator: Host privacy policy URL
- [ ] Operator: Real device screenshots
- [ ] Operator: Signed AAB upload to Internal testing
- [ ] Operator: Replace demo billing before charging users
- [ ] Operator: Content rating + Data Safety submitted
- [ ] **Not claimed:** Play approval, login E2E, or verified billing

---

*Google Play review is independent. Uploading an AAB does not guarantee approval.*
