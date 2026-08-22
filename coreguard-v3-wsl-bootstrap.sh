#!/usr/bin/env bash
# CoreGuard V3/V3.x - WSL Ubuntu companion bootstrap
# Run inside Ubuntu/WSL after Windows bootstrap and reboot.
set -euo pipefail

WORKSPACE="${HOME}/coreguard-v3"
REPO_URL="https://github.com/victormart43210-ship-it/CoreGuard-Android.git"

say() { printf '\n==> %s\n' "$*"; }
ok()  { printf '[OK] %s\n' "$*"; }
warn(){ printf '[WARN] %s\n' "$*"; }

say "Installing bootstrap prerequisites"
sudo apt-get update
sudo apt-get install -y curl ca-certificates gnupg

say "Adding GitHub CLI repository"
curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg \
  | sudo dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg >/dev/null 2>&1
sudo chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
  | sudo tee /etc/apt/sources.list.d/github-cli.list >/dev/null

say "Adding Node.js 24 LTS repository"
curl -fsSL https://deb.nodesource.com/setup_24.x | sudo -E bash -

say "Updating and installing Ubuntu packages"
sudo apt-get update
sudo apt-get install -y \
  git curl unzip zip jq build-essential ca-certificates gnupg \
  python3 python3-pip python3-venv pipx openjdk-17-jdk gh nodejs

python3 -m pipx ensurepath >/dev/null 2>&1 || true
export PATH="$HOME/.local/bin:$HOME/.cargo/bin:$PATH"

say "Installing uv (isolated Python tool manager)"
if ! command -v uv >/dev/null 2>&1; then
  curl -LsSf https://astral.sh/uv/install.sh | sh
  export PATH="$HOME/.local/bin:$HOME/.cargo/bin:$PATH"
fi

say "Installing OpenHands CLI with Python 3.12"
if ! command -v openhands >/dev/null 2>&1; then
  uv tool install openhands --python 3.12
fi
ok "OpenHands installed"

say "Installing Aider (installed for advisory/scratch use only; OpenHands remains sole repo writer)"
if ! command -v aider >/dev/null 2>&1; then
  python3 -m pip install --user aider-install
  export PATH="$HOME/.local/bin:$PATH"
  aider-install
fi
ok "Aider installed"

say "Installing Promptfoo"
if command -v npm >/dev/null 2>&1; then
  npm install -g promptfoo
else
  warn "npm not found inside WSL. Promptfoo can be installed later after Node.js is available."
fi

say "Creating Linux workspace"
mkdir -p "$WORKSPACE" "$WORKSPACE/logs" "$WORKSPACE/bot-output"
if [[ ! -d "$WORKSPACE/CoreGuard-Android/.git" ]]; then
  git clone "$REPO_URL" "$WORKSPACE/CoreGuard-Android"
fi
git -C "$WORKSPACE/CoreGuard-Android" config pull.ff only
git -C "$WORKSPACE/CoreGuard-Android" config fetch.prune true
git -C "$WORKSPACE/CoreGuard-Android" config rerere.enabled true

say "Checking Docker from WSL"
if command -v docker >/dev/null 2>&1 && docker ps >/dev/null 2>&1; then
  ok "Docker is available from WSL"
else
  warn "Docker is not available from WSL. Enable Docker Desktop -> Resources -> WSL Integration -> Ubuntu."
fi

say "Version snapshot"
printf 'git: '; git --version
printf 'java: '; java -version 2>&1 | head -n 1
printf 'node: '; node --version
printf 'npm: '; npm --version
printf 'gh: '; gh --version | head -n 1
printf 'uv: '; uv --version
printf 'openhands: '; openhands --version || true

cat <<'NEXT'

NEXT MANUAL STEPS
-----------------
1. Run: gh auth login
2. Launch OpenHands from the repo when needed:
     cd ~/coreguard-v3/CoreGuard-Android
     openhands serve --mount-cwd
3. Ollama runs on Windows by default. Verify from Windows with: ollama list
4. Under the V3 One-Writer rule, OpenHands is the only agent allowed to modify the CoreGuard repository.
   Aider/specialist agents may review or work only on disposable scratch copies, never commit/write the canonical repo.
5. Never place production API keys in .aider.conf.yml, OpenHands prompts, Slack, GitHub issues, or the APK.
6. Use Google Secret Manager + Workload Identity/OIDC for cloud production secrets.
NEXT
