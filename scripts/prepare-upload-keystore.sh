#!/usr/bin/env bash
# Generate a Play upload keystore + local keystore.properties (gitignored).
# Run ONCE on a machine you control. Back up the .jks offline immediately.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="${1:-$HOME/coreguard-secrets}"
ALIAS="${KEY_ALIAS:-coreguard}"
STORE_PASS="${STORE_PASSWORD:-}"
KEY_PASS="${KEY_PASSWORD:-}"

mkdir -p "$OUT_DIR"
JKS="$OUT_DIR/coreguard-upload.jks"

if [[ -f "$JKS" ]]; then
  echo "[keystore] Already exists: $JKS"
else
  if [[ -z "$STORE_PASS" ]]; then
    STORE_PASS="$(openssl rand -base64 24 | tr -d '/+=' | head -c 24)"
  fi
  if [[ -z "$KEY_PASS" ]]; then
    KEY_PASS="$STORE_PASS"
  fi
  echo "[keystore] Generating $JKS …"
  keytool -genkeypair -v \
    -keystore "$JKS" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -storepass "$STORE_PASS" \
    -keypass "$KEY_PASS" \
    -dname "CN=CoreGuard Upload, OU=Mobile, O=ColdBoar, L=Unknown, ST=Unknown, C=US"
  umask 077
  cat > "$OUT_DIR/passwords.env" <<EOF
export SIGNING_STORE_FILE=$JKS
export SIGNING_STORE_PASSWORD=$STORE_PASS
export SIGNING_KEY_ALIAS=$ALIAS
export SIGNING_KEY_PASSWORD=$KEY_PASS
EOF
  chmod 600 "$OUT_DIR/passwords.env"
  echo "[keystore] Passwords written to $OUT_DIR/passwords.env (keep offline backup)."
fi

# shellcheck disable=SC1090
source "$OUT_DIR/passwords.env"

# Relative path from repo root when possible
rel_store="$SIGNING_STORE_FILE"
case "$rel_store" in
  "$ROOT"/*) rel_store="${rel_store#$ROOT/}" ;;
esac

cat > "$ROOT/keystore.properties" <<EOF
storeFile=$SIGNING_STORE_FILE
storePassword=$SIGNING_STORE_PASSWORD
keyAlias=$SIGNING_KEY_ALIAS
keyPassword=$SIGNING_KEY_PASSWORD
EOF
chmod 600 "$ROOT/keystore.properties"

KEYTOOL_BIN="keytool"
if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/keytool" ]]; then
  KEYTOOL_BIN="${JAVA_HOME}/bin/keytool"
fi
CERT_SHA="$("$KEYTOOL_BIN" -list -v -keystore "$SIGNING_STORE_FILE" \
  -alias "$SIGNING_KEY_ALIAS" -storepass "$SIGNING_STORE_PASSWORD" 2>/dev/null \
  | awk -F': ' '/SHA256:/{print $2; exit}')"

echo
echo "[keystore] Wrote gitignored $ROOT/keystore.properties"
echo "[keystore] Upload-cert SHA-256 (set EXPECTED_CERT_SHA256 for release builds):"
echo "  $CERT_SHA"
echo
echo "Build signed AAB:"
echo "  source $OUT_DIR/passwords.env"
echo "  export EXPECTED_CERT_SHA256='$CERT_SHA'"
echo "  ./gradlew -Pcoreguard.androidBuild=true :app:bundleRelease"
