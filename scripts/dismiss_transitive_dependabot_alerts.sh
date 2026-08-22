#!/usr/bin/env bash
# Bulk-dismiss Dependabot alerts on packages that are transitive build tooling
# only — i.e. NOT present in :app's releaseRuntimeClasspath (what ships in the
# Play Store APK). See PR #161 for the full triage analysis.
#
# SAFETY MODEL
# ============
# 1. Dry-run by default. Nothing is dismissed unless --apply is passed.
# 2. Only alerts whose package name matches the allowlist below are considered.
#    Anything outside the allowlist is left untouched, no matter its severity.
# 3. Before dismissing anything with --apply, the script downloads the current
#    :app release SBOM (via GitHub's dependency-graph API) and re-verifies that
#    each candidate package is absent from that SBOM. If a candidate is found
#    in the release runtime, it is skipped and reported.
# 4. Dismissal reason is "not_used" ("vulnerable code is not used") with an
#    audit comment pointing back to PR #161.
# 5. All actions are logged. Nothing is deleted; dismissals can be reopened
#    from the GitHub UI or via `gh api ... state=open`.
#
# PREREQUISITES
# =============
# - `gh` authenticated with a token that has repo security_events write scope.
# - `jq` installed.
# - PR #161 (scoped dependency-submission) must be merged AND
#   "Automatic dependency submission" must be OFF in
#     Settings → Code security → Dependency graph
#   AND the scoped workflow must have run at least once on main.
#   The script checks for this via the SBOM date; if the SBOM is still the
#   full 452-package graph, it aborts.
#
# USAGE
# =====
#   ./scripts/dismiss_transitive_dependabot_alerts.sh           # dry-run
#   ./scripts/dismiss_transitive_dependabot_alerts.sh --apply   # actually dismiss

set -euo pipefail

REPO="victormart43210-ship-it/CoreGuard-Android"
TRIAGE_PR=161

# Allowlist — exact ecosystem/name pairs proven absent from
# :app releaseRuntimeClasspath (see PR #161 triage). Anything not in this list
# is skipped unconditionally, even if it looks similar.
#
# One entry per line: <ecosystem>/<name>
ALLOWLIST=$(cat <<'EOF'
maven/io.netty:netty-codec
maven/io.netty:netty-codec-http
maven/io.netty:netty-codec-http2
maven/io.netty:netty-common
maven/io.netty:netty-handler
maven/io.netty:netty-handler-proxy
maven/org.bouncycastle:bcprov-jdk18on
maven/org.bouncycastle:bcpkix-jdk18on
maven/org.jetbrains.kotlin:kotlin-gradle-plugin
maven/org.bitbucket.b_c:jose4j
maven/org.jdom:jdom2
maven/commons-io:commons-io
maven/org.apache.commons:commons-compress
maven/com.google.protobuf:protobuf-java
maven/com.google.guava:guava
EOF
)

APPLY=0
if [[ "${1:-}" == "--apply" ]]; then
  APPLY=1
fi

log() { printf '%s\n' "$*" >&2; }

# --------------------------------------------------------------------------
# 1. Preflight — confirm the SBOM has been rescoped.
# --------------------------------------------------------------------------
log "== Preflight: check current SBOM scope =="
SBOM_JSON=$(mktemp)
trap 'rm -f "$SBOM_JSON"' EXIT

if ! gh api "repos/${REPO}/dependency-graph/sbom" > "$SBOM_JSON" 2>/dev/null; then
  log "ERROR: could not fetch SBOM. Check gh auth and repo access."
  exit 2
fi

SBOM_PKG_COUNT=$(jq -r '.sbom.packages | length' "$SBOM_JSON")
log "SBOM currently reports ${SBOM_PKG_COUNT} packages."

if [[ "$SBOM_PKG_COUNT" -gt 200 ]]; then
  log ""
  log "ABORT: SBOM still contains ${SBOM_PKG_COUNT} packages — the Automatic"
  log "Dependency Submission likely has not been disabled yet."
  log ""
  log "Before running --apply:"
  log "  1. Confirm PR #${TRIAGE_PR} is merged (adds the scoped workflow)."
  log "  2. In repo Settings → Code security → Dependency graph, toggle OFF"
  log "     'Automatic dependency submission'."
  log "  3. Push any commit to main (or workflow_dispatch the new workflow)"
  log "     so 'Gradle dependency submission (scoped)' runs once."
  log "  4. Wait for the new SBOM to publish (usually < 5 min after the run)."
  log "  5. Re-run this script."
  exit 3
fi

log "SBOM appears rescoped (${SBOM_PKG_COUNT} packages). Proceeding."

# Extract the set of package purls / names present in the current SBOM for
# runtime-presence verification.
SBOM_PACKAGES=$(mktemp)
trap 'rm -f "$SBOM_JSON" "$SBOM_PACKAGES"' EXIT
jq -r '.sbom.packages[] | .name // empty' "$SBOM_JSON" | sort -u > "$SBOM_PACKAGES"

# --------------------------------------------------------------------------
# 2. Fetch all open Dependabot alerts.
# --------------------------------------------------------------------------
log ""
log "== Fetching open Dependabot alerts =="
ALERTS_JSON=$(mktemp)
trap 'rm -f "$SBOM_JSON" "$SBOM_PACKAGES" "$ALERTS_JSON"' EXIT
gh api "repos/${REPO}/dependabot/alerts?state=open&per_page=100" > "$ALERTS_JSON"

TOTAL=$(jq -r 'length' "$ALERTS_JSON")
log "Open alerts: ${TOTAL}"

# --------------------------------------------------------------------------
# 3. For each alert, decide: dismiss, keep, or skip.
# --------------------------------------------------------------------------
log ""
log "== Triage =="
if [[ "$APPLY" -eq 1 ]]; then
  log "MODE: APPLY (will dismiss matching alerts)"
else
  log "MODE: DRY-RUN (no changes will be made — pass --apply to dismiss)"
fi
log ""

WOULD_DISMISS=0
KEPT_OUT_OF_ALLOWLIST=0
KEPT_PRESENT_IN_SBOM=0
DISMISSED=0
DISMISS_FAILED=0

# Iterate alerts one by one so we can log per-alert reasoning.
COUNT=$(jq -r 'length' "$ALERTS_JSON")
for i in $(seq 0 $((COUNT - 1))); do
  NUMBER=$(jq -r ".[$i].number" "$ALERTS_JSON")
  SEVERITY=$(jq -r ".[$i].security_advisory.severity" "$ALERTS_JSON")
  ECOSYSTEM=$(jq -r ".[$i].dependency.package.ecosystem" "$ALERTS_JSON")
  NAME=$(jq -r ".[$i].dependency.package.name" "$ALERTS_JSON")
  CVE=$(jq -r ".[$i].security_advisory.cve_id // .[$i].security_advisory.ghsa_id" "$ALERTS_JSON")
  KEY="${ECOSYSTEM}/${NAME}"

  # Rule 1: only proceed for allowlisted packages.
  if ! grep -qxF "$KEY" <<<"$ALLOWLIST"; then
    log "  KEEP  #${NUMBER}  ${SEVERITY}  ${KEY}  ${CVE}  (not in allowlist)"
    KEPT_OUT_OF_ALLOWLIST=$((KEPT_OUT_OF_ALLOWLIST + 1))
    continue
  fi

  # Rule 2: even if allowlisted, if the package is present in the current
  # scoped SBOM, it IS shipped — do NOT dismiss.
  if grep -qxF "$NAME" "$SBOM_PACKAGES"; then
    log "  KEEP  #${NUMBER}  ${SEVERITY}  ${KEY}  ${CVE}  (present in release SBOM)"
    KEPT_PRESENT_IN_SBOM=$((KEPT_PRESENT_IN_SBOM + 1))
    continue
  fi

  # This alert is safe to dismiss.
  WOULD_DISMISS=$((WOULD_DISMISS + 1))

  if [[ "$APPLY" -eq 0 ]]; then
    log "  WOULD-DISMISS  #${NUMBER}  ${SEVERITY}  ${KEY}  ${CVE}"
    continue
  fi

  # Actually dismiss. Reason "not_used" = "Vulnerable code is not used".
  COMMENT="Transitive build-tooling dependency, not present in :app releaseRuntimeClasspath (Play Store APK). See triage in PR #${TRIAGE_PR}."
  if gh api -X PATCH "repos/${REPO}/dependabot/alerts/${NUMBER}" \
      -f state=dismissed \
      -f dismissed_reason=not_used \
      -f dismissed_comment="$COMMENT" >/dev/null 2>&1; then
    log "  DISMISSED  #${NUMBER}  ${SEVERITY}  ${KEY}  ${CVE}"
    DISMISSED=$((DISMISSED + 1))
  else
    log "  FAILED     #${NUMBER}  ${SEVERITY}  ${KEY}  ${CVE}  (API error)"
    DISMISS_FAILED=$((DISMISS_FAILED + 1))
  fi
done

# --------------------------------------------------------------------------
# 4. Summary.
# --------------------------------------------------------------------------
log ""
log "== Summary =="
log "  Total open alerts:              ${TOTAL}"
log "  Kept — not in allowlist:        ${KEPT_OUT_OF_ALLOWLIST}"
log "  Kept — present in release SBOM: ${KEPT_PRESENT_IN_SBOM}"
if [[ "$APPLY" -eq 1 ]]; then
  log "  Dismissed:                      ${DISMISSED}"
  log "  Failed:                         ${DISMISS_FAILED}"
  if [[ "$DISMISS_FAILED" -gt 0 ]]; then
    exit 1
  fi
else
  log "  Would dismiss (pass --apply):   ${WOULD_DISMISS}"
fi
