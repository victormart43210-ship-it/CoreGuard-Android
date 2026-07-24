# CoreGuard-Android — Merge & Launch Checklist

Tracking checklist for finishing the app, based on the 5 open draft PRs as of 2026-07-21.

## Open draft PRs (merge in this order)

- [ ] **#4** — feat: Network Defense Lab + Cobra CLI (delivery republish)
- [ ] **#3** — CoreGuard: Play Billing + server purchase verification
- [ ] **#5** — docs: Play Store launch prep (signing, privacy, listing assets)
- [ ] **#7** — feat: Ancient Guardian UI overhaul — protection sigils, Guardian Score, full dark theme
- [ ] **#8** — feat: enterprise anti-tamper defense — native root/Frida/debugger/hook detection + StrongBox crypto

Each PR is currently marked as **draft** — mark "Ready for review" before merging.

Note: PR #10 ("Continuing ongoing development efforts") was already merged into `main` separately from this stack.

## Outstanding tasks called out in the PRs themselves

### Play Store launch (PR #5)
- [ ] Create/confirm Play Developer account
- [ ] Host the privacy policy at a public URL
- [ ] Replace placeholder screenshots and store listing assets with real ones
- [ ] Generate and securely store a real release keystore (not committed to the repo)
- [ ] Upload a signed AAB to Internal testing track
- [ ] Wire up real Play Billing before charging users

### Billing hardening (PR #3)
- [ ] Verify CI security-gate + lint checks pass on the PR
- [ ] Manually verify RestrictedMode banner appears when a FAIL check is present
- [ ] Manually verify demo premium is labeled as demo, not Play-verified

### Anti-tamper (PR #8)
- [ ] Validate `ptrace` anti-debug guard on real hardware (emulator without KVM can't fully test this — shows WARN there)

## General
- [ ] Resolve merge conflicts across the 5 stacked PRs (in progress via coding agent)
- [ ] Final regression pass: `./gradlew test assembleDebug` after all PRs are merged
- [ ] Tag a release once merged and validated
