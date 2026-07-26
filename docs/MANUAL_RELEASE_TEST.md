# Manual release test (device / emulator)

Run this smoke path before promoting a build. Check boxes only with **real** evidence from a device or emulator session — do not mark complete from code review alone.

## Preconditions

- [ ] Debug or Internal-testing build installed on a physical device or emulator
- [ ] Google account added as a Play license tester (for billing checks)
- [ ] Play Console product `coreguard_premium_monthly` exists (for purchase flow)

## Smoke path

1. **Home**
   - [ ] Guardian Score / security checks render without crash
   - [ ] Navigation to Scanner and Timeline works
2. **Scanner**
   - [ ] Privacy check completes and shows a verdict
   - [ ] Disclaimer that a clean result is not a guarantee is visible
   - [ ] Free user: signature refresh shows Premium upsell (does not silently refresh)
   - [ ] Premium user: signature refresh can run (network allowed)
3. **Shield**
   - [ ] VPN consent dialog appears on first enable
   - [ ] Active state updates; spyware-removal disclaimer remains visible
4. **Compliance**
   - [ ] Scores visible for free users
   - [ ] Export gated; upsell card visible; Settings navigation works
5. **Timeline**
   - [ ] Free tier truncates to 3 entries; Premium shows deeper history
6. **Settings / Tools → Quilla**
   - [ ] “what can you do” answers without cloud-LLM claims
   - [ ] “Open Scanner” navigates (does not silently scan)
   - [ ] Research sync failure does **not** claim “loaded 0 indicators” success
   - [ ] Offensive prompt (“hack … without permission”) is refused
7. **Billing**
   - [ ] Purchase uses SKU `coreguard_premium_monthly`
   - [ ] Success unlocks export / refresh / timeline depth
   - [ ] Cancel / error paths show non-crashing status text
8. **Privacy Policy**
   - [ ] Screen opens; network disclosures match actual behavior

## Evidence to capture

- Build id / commit SHA tested
- Device model + Android version
- Screenshots or short notes per failing step
- Whether `./gradlew -Pcoreguard.androidBuild=true :app:testDebugUnitTest` passed in CI or locally

## Honest status for this agent environment

This Cloud Agent environment may lack a full Android SDK / emulator. If unit tests or device runs were not executed here, leave the checkboxes above unchecked and record that limitation in the PR summary.
