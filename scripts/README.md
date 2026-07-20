# Play release automation

## One command (when credentials exist)

```bash
# Required for real Play steps:
export GOOGLE_APPLICATION_CREDENTIALS=/secure/path/play-service-account.json
# Required for devices on Internal Testing (public HTTPS):
export COREGUARD_VERIFY_URL=https://your-billing-server.example
export COREGUARD_VERIFY_MODE=google

./scripts/play_pipeline.sh
```

Place the JSON at `~/coreguard-secrets/play-service-account.json` if you prefer not to export the path.

## What the pipeline does

1. **ensurePlayProduct** — creates/confirms `coreguard_premium_monthly` via Play API  
2. **billing-server** — starts verifier (`google` mode with credentials, else `mock`)  
3. **bundleRelease** — signed AAB with `COREGUARD_VERIFY_URL` baked in  
4. **uploadInternalTesting** — uploads AAB to the Internal Testing track  

## What this Cloud Agent cannot do without your secrets

- Log into your Google Play Developer account in a browser
- Invent a service-account JSON
- Host a public HTTPS URL on your cloud provider without provider credentials
- Install the app on your phone from Internal Testing for you

After a successful upload, open the Internal Testing opt-in link on the device (not a sideload QR).
