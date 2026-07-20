# billing-server

Kotlin (Ktor) service that verifies Google Play subscription purchase tokens.

## Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/health` | Liveness |
| POST | `/v1/subscriptions/verify` | Verify purchase token |

### Verify request

```json
{
  "packageName": "com.coldboar.coreguard",
  "productId": "coreguard_premium_monthly",
  "purchaseToken": "..."
}
```

### Verify response

```json
{
  "active": true,
  "reason": null,
  "expiryTimeMillis": 1735689600000
}
```

## Run

```bash
# Mock mode (CI / local, no Play credentials)
export COREGUARD_VERIFY_MODE=mock
./gradlew :billing-server:run

# Production mode (Play Developer API)
export COREGUARD_VERIFY_MODE=google
export GOOGLE_APPLICATION_CREDENTIALS=/secure/path/service-account.json
export PORT=8080
./gradlew :billing-server:run
```

Never commit service-account JSON keys.

## Tests

```bash
./gradlew :billing-server:test
```
