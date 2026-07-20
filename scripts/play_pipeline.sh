#!/usr/bin/env bash
# CoreGuard Play release pipeline.
# Automates what this environment can; fails clearly when Play credentials are missing.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SECRETS_DIR="${COREGUARD_SECRETS_DIR:-$HOME/coreguard-secrets}"
VERIFY_URL="${COREGUARD_VERIFY_URL:-}"
VERIFY_MODE="${COREGUARD_VERIFY_MODE:-google}"
PORT="${PORT:-8080}"

mkdir -p "$SECRETS_DIR"
chmod 700 "$SECRETS_DIR" || true

echo "== CoreGuard Play pipeline =="
echo "secrets dir: $SECRETS_DIR"

need_creds() {
  if [[ -z "${GOOGLE_APPLICATION_CREDENTIALS:-}" ]]; then
    if [[ -f "$SECRETS_DIR/play-service-account.json" ]]; then
      export GOOGLE_APPLICATION_CREDENTIALS="$SECRETS_DIR/play-service-account.json"
    fi
  fi
  if [[ -z "${GOOGLE_APPLICATION_CREDENTIALS:-}" || ! -f "${GOOGLE_APPLICATION_CREDENTIALS}" ]]; then
    cat <<EOF

BLOCKED: Play service-account JSON not found.
1) Create a service account in Google Cloud linked to Play Console
2) Download the JSON key to:
     $SECRETS_DIR/play-service-account.json
3) Invite that service account in Play Console → Users and permissions
4) Re-run: ./scripts/play_pipeline.sh

EOF
    return 1
  fi
  export GOOGLE_APPLICATION_CREDENTIALS
  echo "Using credentials: $GOOGLE_APPLICATION_CREDENTIALS"
  return 0
}

ensure_keystore() {
  local ks="$SECRETS_DIR/coreguard-upload.jks"
  local props="$SECRETS_DIR/keystore.env"
  if [[ ! -f "$ks" ]]; then
    echo "Generating local upload keystore at $ks (not committed)…"
    local pass
    pass="$(openssl rand -base64 24 | tr -d '/+=' | head -c 24)"
    keytool -genkeypair -v \
      -keystore "$ks" \
      -alias coreguard \
      -keyalg RSA -keysize 2048 -validity 10000 \
      -storepass "$pass" -keypass "$pass" \
      -dname "CN=CoreGuard Upload, OU=Mobile, O=ColdBoar, L=Unknown, ST=Unknown, C=US"
    cat >"$props" <<EOF
export KEYSTORE_PATH=$ks
export KEYSTORE_PASSWORD=$pass
export KEY_ALIAS=coreguard
export KEY_PASSWORD=$pass
EOF
    chmod 600 "$props" "$ks"
  fi
  # shellcheck disable=SC1090
  source "$props"
}

start_billing_server() {
  echo "Starting billing-server on :$PORT (mode=$VERIFY_MODE)…"
  export PORT
  export COREGUARD_VERIFY_MODE="$VERIFY_MODE"
  # Run in background via gradle; log to secrets dir
  ./gradlew --no-daemon :billing-server:run >"$SECRETS_DIR/billing-server.log" 2>&1 &
  echo $! >"$SECRETS_DIR/billing-server.pid"
  for i in $(seq 1 40); do
    if curl -fsS "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; then
      echo "billing-server healthy at http://127.0.0.1:$PORT"
      return 0
    fi
    sleep 1
  done
  echo "billing-server failed to become healthy. See $SECRETS_DIR/billing-server.log"
  tail -50 "$SECRETS_DIR/billing-server.log" || true
  return 1
}

step_create_product() {
  echo "-- Step 1: ensure Play product coreguard_premium_monthly --"
  if ! need_creds; then return 1; fi
  ./gradlew --no-daemon :billing-server:ensurePlayProduct
}

step_deploy_server() {
  echo "-- Step 2: deploy/start billing-server --"
  if [[ "${VERIFY_MODE}" == "google" ]]; then
    if ! need_creds; then
      echo "Falling back to COREGUARD_VERIFY_MODE=mock for local bring-up."
      VERIFY_MODE=mock
    fi
  fi
  start_billing_server
  if [[ -z "$VERIFY_URL" ]]; then
    # Local default; phones on Internal Testing need a public HTTPS URL you set.
    VERIFY_URL="http://127.0.0.1:$PORT"
    echo "NOTE: COREGUARD_VERIFY_URL defaulting to $VERIFY_URL (device builds need a public HTTPS URL)."
  fi
  export COREGUARD_VERIFY_URL="$VERIFY_URL"
}

step_build_release() {
  echo "-- Step 3: build signed release AAB --"
  ensure_keystore
  if [[ -z "${COREGUARD_VERIFY_URL:-}" ]]; then
    echo "ERROR: COREGUARD_VERIFY_URL is empty"
    return 1
  fi
  export KEYSTORE_PATH KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD COREGUARD_VERIFY_URL
  ./gradlew --no-daemon :app:bundleRelease
  local aab="$ROOT/app/build/outputs/bundle/release/app-release.aab"
  if [[ ! -f "$aab" ]]; then
    echo "AAB missing after build"
    return 1
  fi
  cp -f "$aab" "$SECRETS_DIR/app-release.aab"
  echo "AAB ready: $SECRETS_DIR/app-release.aab"
  echo "$aab" >"$SECRETS_DIR/aab.path"
}

step_internal_testing() {
  echo "-- Step 4: upload Internal Testing --"
  if ! need_creds; then return 1; fi
  local aab
  aab="$(cat "$SECRETS_DIR/aab.path" 2>/dev/null || true)"
  if [[ -z "$aab" || ! -f "$aab" ]]; then
    aab="$SECRETS_DIR/app-release.aab"
  fi
  ./gradlew --no-daemon :billing-server:uploadInternalTesting -Paab="$aab"
}

FAIL=0
step_create_product || FAIL=1
step_deploy_server || FAIL=1
step_build_release || FAIL=1
step_internal_testing || FAIL=1

echo
echo "== Pipeline finished (fail_bits=$FAIL) =="
echo "Artifacts / logs under: $SECRETS_DIR"
if [[ "$FAIL" -ne 0 ]]; then
  echo "Some steps were blocked without Play credentials or public VERIFY_URL."
  echo "Drop play-service-account.json into $SECRETS_DIR and set COREGUARD_VERIFY_URL to your HTTPS server, then re-run."
  exit 1
fi
