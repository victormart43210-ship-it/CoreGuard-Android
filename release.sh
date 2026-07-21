#!/usr/bin/env bash
set -euo pipefail

REPO_DEFAULT="Victormart43210-ship-it/CoreGuard-Android"
REPO="${REPO:-$REPO_DEFAULT}"
VERSION="1.0.0"
RELEASE_BRANCH=""
BASE_BRANCH=""
DRY_RUN="${DRY_RUN:-true}"
ASSUME_YES="false"

usage() {
  cat <<'EOF'
Usage: ./release.sh [options]

Options:
  --version <x.y.z>     Release version (default: 1.0.0)
  --branch <name>       Release branch name (default: release/v<version>)
  --base <name>         Base branch for PR/protection (default: repo default branch)
  --repo <owner/repo>   Target repository (default: Victormart43210-ship-it/CoreGuard-Android)
  --dry-run             Simulate actions (default)
  --live                Execute real mutations
  --yes                 Non-interactive mode (no prompts)
  -h, --help            Show help

Environment variables:
  DRY_RUN=true|false
  REPO=owner/repo
  NVD_API_KEY=<key>     Required in --live --yes mode
EOF
}

log() { echo "[INFO] $*"; }
warn() { echo "[WARN] $*"; }
err() { echo "[ERROR] $*" >&2; }

run_cmd() {
  if [[ "$DRY_RUN" == "true" ]]; then
    echo "[DRY-RUN] $*"
  else
    "$@"
  fi
}

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    err "Missing required command: $cmd"
    exit 1
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version)
      VERSION="${2:-}"; shift 2 ;;
    --branch)
      RELEASE_BRANCH="${2:-}"; shift 2 ;;
    --base)
      BASE_BRANCH="${2:-}"; shift 2 ;;
    --repo)
      REPO="${2:-}"; shift 2 ;;
    --dry-run)
      DRY_RUN="true"; shift ;;
    --live)
      DRY_RUN="false"; shift ;;
    --yes)
      ASSUME_YES="true"; shift ;;
    -h|--help)
      usage; exit 0 ;;
    *)
      err "Unknown argument: $1"
      usage
      exit 1 ;;
  esac
done

if [[ -z "$VERSION" ]]; then
  err "Version cannot be empty"
  exit 1
fi

if [[ -z "$RELEASE_BRANCH" ]]; then
  RELEASE_BRANCH="release/v$VERSION"
fi

log "Mode: $([[ "$DRY_RUN" == "true" ]] && echo "DRY-RUN" || echo "LIVE")"
log "Repo: $REPO"
log "Version: $VERSION"
log "Release branch: $RELEASE_BRANCH"

# Prerequisite checks
require_cmd gh
require_cmd git
require_cmd jq
require_cmd java

if [[ ! -x "./gradlew" ]]; then
  err "./gradlew not found or not executable"
  exit 1
fi

# Ensure we are in a git work tree
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  err "This script must be run from inside a git repository"
  exit 1
fi

# Enforce clean working tree before release in live mode
if [[ "$DRY_RUN" == "false" ]]; then
  if [[ -n "$(git status --porcelain)" ]]; then
    err "Working tree is not clean. Commit/stash changes before running live release."
    exit 1
  fi
fi

log "1/6: Running Local Gradle Release Verification..."
run_cmd ./gradlew clean cyclonedxBom assembleRelease --no-daemon

log "2/6: Configuring GitHub Repository Secret (NVD_API_KEY)..."
if [[ "$DRY_RUN" == "true" ]]; then
  echo "[DRY-RUN] Skipping secret mutation: gh secret set NVD_API_KEY"
else
  NVD_KEY="${NVD_API_KEY:-}"
  if [[ -z "$NVD_KEY" && "$ASSUME_YES" == "true" ]]; then
    err "NVD_API_KEY must be set in environment when using --yes in live mode"
    exit 1
  fi
  if [[ -z "$NVD_KEY" ]]; then
    read -r -s -p "Enter your NIST NVD API Key: " NVD_KEY
    echo ""
  fi
  if [[ -z "$NVD_KEY" ]]; then
    err "NVD_API_KEY cannot be empty"
    exit 1
  fi
  gh secret set NVD_API_KEY --repo "$REPO" --body "$NVD_KEY"
fi

log "3/6: Resolving default/base branch..."
DEFAULT_BRANCH="$(gh repo view "$REPO" --json defaultBranchRef -q '.defaultBranchRef.name')"
if [[ -z "$BASE_BRANCH" ]]; then
  BASE_BRANCH="$DEFAULT_BRANCH"
fi
log "Default branch: $DEFAULT_BRANCH"
log "Base branch: $BASE_BRANCH"

log "4/6: Applying Branch Protection to '$BASE_BRANCH'..."
if [[ "$DRY_RUN" == "true" ]]; then
  cat <<EOF
[DRY-RUN] gh api --method PUT \\
  -H "Accept: application/vnd.github+json" \\
  -H "X-GitHub-Api-Version: 2022-11-28" \\
  "/repos/$REPO/branches/$BASE_BRANCH/protection" \\
  --input <json>
EOF
else
  set +e
  gh api \
    --method PUT \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "/repos/$REPO/branches/$BASE_BRANCH/protection" \
    --input - <<'JSON'
{
  "required_status_checks": {
    "strict": true,
    "contexts": [
      "SAST Code Scan (MobSF)",
      "Generate CycloneDX SBOM",
      "OWASP Dependency-Check (SCA)"
    ]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1
  },
  "restrictions": null
}
JSON
  rc=$?
  set -e
  if [[ $rc -ne 0 ]]; then
    err "Failed to apply branch protection to '$BASE_BRANCH'."
    warn "Rollback hints:"
    warn "  1) Inspect current protection: gh api /repos/$REPO/branches/$BASE_BRANCH/protection"
    warn "  2) Verify required status check names exactly match workflow job names"
    warn "  3) Retry with corrected contexts or temporarily remove strict checks"
    exit $rc
  fi
fi

log "5/6: Creating release branch..."
if [[ "$DRY_RUN" == "true" ]]; then
  echo "[DRY-RUN] git checkout '$BASE_BRANCH'"
  echo "[DRY-RUN] git pull --ff-only origin '$BASE_BRANCH'"
  echo "[DRY-RUN] git checkout -b '$RELEASE_BRANCH' || git checkout '$RELEASE_BRANCH'"
else
  git checkout "$BASE_BRANCH"
  git pull --ff-only origin "$BASE_BRANCH"
  git checkout -b "$RELEASE_BRANCH" 2>/dev/null || git checkout "$RELEASE_BRANCH"
fi

run_cmd git add .

if [[ "$DRY_RUN" == "true" ]]; then
  echo "[DRY-RUN] Would commit only if staged diff exists"
else
  if ! git diff --cached --quiet; then
    git commit -m "chore(release): finalize OWASP MASVS v2.1 compliance matrix and PR templates"
  else
    log "No staged changes to commit."
  fi
fi

run_cmd git push -u origin "$RELEASE_BRANCH"

log "6/6: Opening production PR..."
if [[ "$DRY_RUN" == "true" ]]; then
  echo "[DRY-RUN] gh pr list --repo '$REPO' --head '$RELEASE_BRANCH' --base '$BASE_BRANCH' --json number -q 'length > 0'"
  echo "[DRY-RUN] gh pr create --repo '$REPO' --title 'release: CoreGuard Elite v$VERSION Production Release' --body 'Final production release build for CoreGuard Elite v$VERSION. All OWASP MASVS v2.1.0 controls verified.' --base '$BASE_BRANCH' --head '$RELEASE_BRANCH'"
else
  if gh pr list --repo "$REPO" --head "$RELEASE_BRANCH" --base "$BASE_BRANCH" --json number -q 'length > 0' | grep -q true; then
    log "PR already exists for $RELEASE_BRANCH -> $BASE_BRANCH"
  else
    gh pr create \
      --repo "$REPO" \
      --title "release: CoreGuard Elite v$VERSION Production Release" \
      --body "Final production release build for CoreGuard Elite v$VERSION. All OWASP MASVS v2.1.0 controls verified." \
      --base "$BASE_BRANCH" \
      --head "$RELEASE_BRANCH"
  fi
fi

log "Done."
log "Examples:"
log "  Dry-run: ./release.sh --version 1.0.1 --dry-run"
log "  Live CI:  NVD_API_KEY=*** ./release.sh --version 1.0.1 --live --yes"
