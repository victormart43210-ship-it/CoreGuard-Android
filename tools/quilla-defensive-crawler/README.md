# Quilla Defensive Crawler

A **defensive-only** threat-intelligence crawler for the CoreGuard Quilla AI system.

Runs on a Raspberry Pi or controlled server. Collects public cybersecurity advisories,
CVEs, IOCs, and mitigation guidance from approved sources. Sanitizes the results,
signs the bundle with Ed25519, and publishes `quilla-intel.json` + `quilla-intel.sig`
for the CoreGuard Android app to download and verify.

> **Important:** This crawler is strictly defensive. It does not exploit
> vulnerabilities, scan ports, brute-force credentials, bypass authentication,
> execute JavaScript, or download executable malware. See `source_allowlist.json`
> for the complete list of approved sources.

---

## Architecture

```
Approved public sources (CISA, Android Security Bulletins, MISP)
        │
Raspberry Pi defensive crawler  ←  this tool
        │
Sanitize, validate, classify, deduplicate
        │
Sign with Ed25519 private key
        │
quilla-intel.json + quilla-intel.sig + quilla-intel-manifest.json
        │  (upload to HTTPS static host — never expose the Pi directly)
CoreGuard Android verifies signature + schema
        │
SharedThreatKnowledgeRepository (ThreatKnowledgeSource.CRAWLER)
        │
CyberKnowledgeBase → QuillaIntelNetwork → QuillaInfinityTrainer
```

---

## Installation on Raspberry Pi OS

```bash
# 1. Update system packages
sudo apt update && sudo apt upgrade -y

# 2. Install Python 3.11+ if not already present
sudo apt install -y python3.11 python3.11-venv python3-pip git

# 3. Clone CoreGuard repository
git clone https://github.com/victormart43210-ship-it/CoreGuard-Android.git
cd CoreGuard-Android/tools/quilla-defensive-crawler

# 4. Create and activate a virtual environment
python3.11 -m venv .venv
source .venv/bin/activate

# 5. Install dependencies
pip install -r requirements.txt
```

---

## Generating signing keys

```bash
python -m quilla_crawler generate-key \
  --private output/private/quilla-ed25519-private.pem \
  --public  output/public/quilla-ed25519-public.pem
```

**Where to store the private key:**

- Store `output/private/quilla-ed25519-private.pem` on the Raspberry Pi only.
- Set file permissions to `0600` (`chmod 600 output/private/quilla-ed25519-private.pem`).
- **Never commit the private key to version control.**
- Back it up to an encrypted offline location.
- Set the environment variable before running crawls:

```bash
export QUILLA_SIGNING_KEY_PATH="$(pwd)/output/private/quilla-ed25519-private.pem"
```

**Public key:**

Embed `output/public/quilla-ed25519-public.pem` in the CoreGuard Android app as a
raw resource at `app/src/main/res/raw/quilla_ed25519_public.pem`. This file is safe
to commit and publish; it contains no secrets.

---

## Configuring approved sources

All crawlable sources must be explicitly listed in `source_allowlist.json`.

The crawler **fails closed** — any URL not present in the allowlist is rejected
before a network connection is attempted.

To add a new source, extend the `sources` array in `source_allowlist.json` with:

- A unique `id` and descriptive `name`.
- `seed_urls` (must use HTTPS).
- `allowed_hosts` and `allowed_path_prefixes` for safe URL scoping.
- `content_types` accepted from this source.
- `max_pages`, `max_depth`, `requests_per_minute` limits.
- `trust_level`: `TRUSTED_SOURCE` or `CORROBORATED`.
- `parser`: `cisa_kev`, `misp_android`, or `generic_advisory_html`.

Do **not** add: exploit databases, paste sites, credential collections, underground
forums, social-media scrapers, or arbitrary GitHub repositories.

You must review the website's terms of service, license, `robots.txt`, and rate-limit
policies before adding any source.

---

## Running a dry run

A dry run validates the configuration and fetches sources, but does not sign or
publish any output:

```bash
python -m quilla_crawler crawl \
  --config source_allowlist.json \
  --output output/ \
  --dry-run \
  --verbose
```

---

## Running a full crawl and signing

```bash
export QUILLA_SIGNING_KEY_PATH="output/private/quilla-ed25519-private.pem"

python -m quilla_crawler crawl \
  --config source_allowlist.json \
  --output output/
```

This produces:

- `output/quilla-intel.json` — the signed intelligence bundle.
- `output/quilla-intel.sig` — the Ed25519 signature (Base64).
- `output/quilla-intel-manifest.json` — a summary without entries (for quick inspection).
- `output/audit.jsonl` — structured audit log.

---

## Publishing output

**Do not expose the Raspberry Pi directly to the public internet.**

Instead, publish the three static output files through an established HTTPS hosting
provider, object-storage service (e.g. AWS S3, GCS, Cloudflare R2), or a controlled
HTTPS backend you already operate.

Example: upload to an S3 bucket with a public-read policy:

```bash
aws s3 cp output/quilla-intel.json      s3://your-bucket/intel/quilla-intel.json
aws s3 cp output/quilla-intel.sig       s3://your-bucket/intel/quilla-intel.sig
aws s3 cp output/quilla-intel-manifest.json s3://your-bucket/intel/quilla-intel-manifest.json
```

Configure the HTTPS CDN endpoint in `app/src/main/res/values/strings.xml`:

```xml
<string name="quilla_curated_bundle_url">https://your-cdn.example.com/intel/quilla-intel.json</string>
<string name="quilla_curated_sig_url">https://your-cdn.example.com/intel/quilla-intel.sig</string>
```

---

## How CoreGuard obtains the public key

1. Copy `output/public/quilla-ed25519-public.pem` to:
   `app/src/main/res/raw/quilla_ed25519_public.pem`
2. In `QuillaCuratedIntelFetcher`, load the key from:
   ```kotlin
   context.resources.openRawResource(R.raw.quilla_ed25519_public).use { it.readBytes() }
   ```
3. Pass the bytes to `QuillaCuratedIntelFetcher.fetchAndVerify(...)`.

The public key is safe to commit and is not a secret.

---

## Rotating signing keys

1. Generate a new key-pair:
   ```bash
   python -m quilla_crawler generate-key \
     --private output/private/quilla-ed25519-private-NEW.pem \
     --public  output/public/quilla-ed25519-public-NEW.pem
   ```
2. Update the public key resource in the Android app and release a new app version.
3. Only after the new app version is widely deployed, switch `QUILLA_SIGNING_KEY_PATH`
   to the new private key.
4. Securely delete the old private key.
5. Do not sign bundles with both old and new keys simultaneously.

---

## Inspecting audit logs

```bash
# Tail the latest audit records
tail -f output/audit.jsonl | python3 -c "import sys, json; [print(json.dumps(json.loads(l), indent=2)) for l in sys.stdin]"

# Count accepted entries by source
jq 'select(.event=="fetch") | {source_id, entries_accepted}' output/audit.jsonl
```

---

## Disabling a compromised source

1. Remove the source from `source_allowlist.json`.
2. Re-run the crawl to produce a new bundle without entries from that source.
3. Sign and publish the new bundle.
4. The Android app will pick up the updated bundle on next refresh.

---

## Rolling back to a previous verified bundle

CoreGuard caches the last verified-good bundle in app-private storage. If a refresh
fails (network error, bad signature, schema change), the app automatically falls back
to the previous verified cache.

To force a rollback server-side:

1. Re-publish a previous `quilla-intel.json` + `quilla-intel.sig` to your HTTPS host.
2. The app will download and verify it on next sync.

---

## Scheduling automated crawls

Use `cron` on the Raspberry Pi:

```cron
# Run the crawler daily at 03:00 UTC
0 3 * * * /home/pi/CoreGuard-Android/tools/quilla-defensive-crawler/.venv/bin/python \
  -m quilla_crawler crawl \
  --config /home/pi/CoreGuard-Android/tools/quilla-defensive-crawler/source_allowlist.json \
  --output /home/pi/CoreGuard-Android/tools/quilla-defensive-crawler/output/ \
  >> /home/pi/quilla-crawler.log 2>&1
```

---

## Important notices

- **Terms and licenses:** You must comply with the terms of service, data license,
  and robots.txt of every source in the allowlist before crawling.
- **Rate limits:** The crawler respects configured rate limits (`requests_per_minute`).
  Do not set values that violate a source's documented limits.
- **Context only:** Intelligence collected by this crawler is **context** for
  defensive correlation and education — it is not proof of compromise. A match
  between a database entry and a device artifact does not confirm infection.
- **No offensive use:** Do not use this tool to exploit vulnerabilities, scan
  networks, or collect personal information.
