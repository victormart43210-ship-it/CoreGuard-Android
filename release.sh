#!/usr/bin/env bash
set -euo pipefail

REPO="Victormart43210-ship-it/CoreGuard-Android"
RELEASE_BRANCH="release/v1.0.0"
DRY_RUN="${DRY_RUN:-true}"

run_cmd() {
  if [[ "$DRY_RUN" == "true" ]]; then
    echo "[DRY-RUN] $*"
  else
    "$@"
  fi
}

echo "==> Mode: $([[ "$DRY_RUN" == "true" ]] && echo "DRY-RUN" || echo "LIVE")"

echo "==> 1/5: Running Local Gradle Release Verification..."
run_cmd ./gradlew clean cyclonedxBom assembleRelease --no-daemon

echo "==> 2/5: Configuring GitHub Repository Secret (NVD_API_KEY)..."
if [[ "$DRY_RUN" == "true" ]]; then
  echo "[DRY-RUN] Skipping prompt + gh secret set NVD_API_KEY"
else
  read -r -s -p "Enter your NIST NVD API Key: " NVD_KEY
  echo ""
  if [[ -z "${NVD_KEY}" ]]; then
    echo "ERROR: NVD_API_KEY cannot be empty."
    exit 1
  fi
  gh secret set NVD_API_KEY --repo "$REPO" --body "$NVD_KEY"
fi

echo "==> 3/5: Resolving default branch..."
DEFAULT_BRANCH="$(gh repo view "$REPO" --json defaultBranchRef -q '.defaultBranchRef.name')"
echo "Default branch: $DEFAULT_BRANCH"

echo "==> 4/5: Applying Branch Protection to '$DEFAULT_BRANCH'..."
if [[ "$DRY_RUN" == "true" ]]; then
  cat <<EOF
[DRY-RUN] gh api --method PUT \\
  -H "Accept: application/vnd.github+json" \\
  -H "X-GitHub-Api-Version: 2022-11-28" \\
  "/repos/$REPO/branches/$DEFAULT_BRANCH/protection" \\
  --input <json>
EOF
  cat <<'JSON'
[DRY-RUN] Payload:
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
else
  gh api \
    --method PUT \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "/repos/$REPO/branches/$DEFAULT_BRANCH/protection" \
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
fi

echo "==> 5/5: Creating release branch and opening production PR..."
if [[ "$DRY_RUN" == "true" ]]; then
  echo "[DRY-RUN] git checkout -b '$RELEASE_BRANCH' || git checkout '$RELEASE_BRANCH'"
else
  git checkout -b "$RELEASE_BRANCH" 2>/dev/null || git checkout "$RELEASE_BRANCH"
fi

run_cmd git add .

if [[ "$DRY_RUN" == "true" ]]; then
  echo "[DRY-RUN] Would commit only if staged diff exists"
else
  if ! git diff --cached --quiet; then
    git commit -m "chore(release): finalize OWASP MASVS v2.1 compliance matrix and PR templates"
  else
    echo "No staged changes to commit."
  fi
fi

run_cmd git push -u origin "$RELEASE_BRANCH"

if [[ "$DRY_RUN" == "true" ]]; then
  echo "[DRY-RUN] gh pr list --repo '$REPO' --head '$RELEASE_BRANCH' --base '$DEFAULT_BRANCH' --json number -q 'length > 0'"
  echo "[DRY-RUN] gh pr create --repo '$REPO' --title 'release: CoreGuard Elite v1.0.0 Production Release' --body 'Final production release build for CoreGuard Elite v1.0.0. All OWASP MASVS v2.1.0 controls verified.' --base '$DEFAULT_BRANCH' --head '$RELEASE_BRANCH'"
else
  if gh pr list --repo "$REPO" --head "$RELEASE_BRANCH" --base "$DEFAULT_BRANCH" --json number -q 'length > 0' | grep -q true; then
    echo "PR already exists for $RELEASE_BRANCH -> $DEFAULT_BRANCH"
  else
    gh pr create \
      --repo "$REPO" \
      --title "release: CoreGuard Elite v1.0.0 Production Release" \
      --body "Final production release build for CoreGuard Elite v1.0.0. All OWASP MASVS v2.1.0 controls verified." \
      --base "$DEFAULT_BRANCH" \
      --head "$RELEASE_BRANCH"
  fi
fi

echo "==> Done."
echo "Tip: run live with: DRY_RUN=false ./release.sh"
