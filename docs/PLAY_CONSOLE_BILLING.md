# Play Console billing & Internal Testing

Step-by-step setup for CoreGuard (`com.coldboar.coreguard`).
This does **not** create the Play product for you — you must use a Google Play Developer account.

## 1. Create the subscription product

1. Open [Google Play Console](https://play.google.com/console) → your app (package `com.coldboar.coreguard`).
2. **Monetize** → **Products** → **Subscriptions** → **Create subscription**.
3. Product ID (must match code): `coreguard_premium_monthly`
4. Add a base plan (e.g. monthly auto-renewing) and activate it.
5. Save / activate the subscription.

## 2. License testing

1. Play Console → **Settings** → **License testing**.
2. Add Gmail accounts that will run test purchases (license response: RESPOND_NORMALLY).

## 3. Internal Testing track

1. Build a **release** AAB (signed; see `docs/RELEASE_READINESS.md`).
2. Play Console → **Testing** → **Internal testing** → create a release → upload AAB.
3. Add testers (email list) and share the opt-in link.
4. Install **from the Play Store testing link** (not a random sideload QR) so Billing works.

## 4. Service account for billing-server

1. Google Cloud Console → create/select a project linked to Play Console.
2. Create a **service account** → download JSON key (**do not commit it**).
3. Play Console → **Users and permissions** → invite the service account email
   with permission to view financial data / manage orders (as required for purchase validation).
4. On the host running `billing-server`:
   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS=/secure/path/play-service-account.json
   export COREGUARD_VERIFY_MODE=google
   ./gradlew :billing-server:run
   ```

## 5. Point the Android release build at the server

```bash
export COREGUARD_VERIFY_URL=https://your-billing-server.example
./gradlew :app:bundleRelease
```

Or: `./gradlew :app:bundleRelease -Pcoreguard.verifyUrl=https://your-billing-server.example`

## 6. Local mock verification (no Play credentials)

```bash
export COREGUARD_VERIFY_MODE=mock
./gradlew :billing-server:run
# POST body purchaseToken: mock_active_coreguard_premium_monthly
```

Mock mode is for server/unit tests only — it does not replace real Play purchases.
