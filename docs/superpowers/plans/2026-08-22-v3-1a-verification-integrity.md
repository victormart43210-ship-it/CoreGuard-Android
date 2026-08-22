# Plan — V3.1A Verification Integrity & Adversarial CI Gates

- **Branch:** `cursor/v3-1a-verification-integrity`
- **Base SHA:** `1cf1518203d16d360b91d89da39dc06a9fb503ad`
- **Date:** 2026-08-22
- **Role:** sole implementation writer (Superpowers + TDD + one-writer rule + fail-closed security + small independently testable commits)
- **PR:** exactly one DRAFT PR, never marked ready, never merged.

## Goal

Make it substantially harder for AI-generated code, dependency changes, CI
mistakes, Kotlin analysis gaps, malformed security inputs, policy drift, and
false-green tooling to enter `main` undetected. This PR strengthens the system
that verifies CoreGuard. It does **not** add product features.

## Environment limitation (read first)

The implementation sandbox has **no Android SDK** (`ANDROID_HOME` unset).
Gradle / Android instrumentation / CodeQL / API-36 evidence therefore cannot
be produced locally. Per the task's own CI-evidence rules, those results are
sourced from GitHub Actions on the exact pushed head SHA and reported as
`PASS` / `FAIL` / `UNAVAILABLE` / `NOT RUN` — never `UNAVAILABLE -> PASS`.

Locally runnable evidence (this PR's own Python validators and the Quilla
crawler tests) is produced with `python3 -m pytest`, `ruff`, and `mypy`.

## Files to create

| Path | Task |
|---|---|
| `docs/superpowers/plans/2026-08-22-v3-1a-verification-integrity.md` | 0 (this file) |
| `.github/workflows/codeql.yml` | 1 |
| `scripts/ci/verify_codeql_coverage.py` | 1 |
| `scripts/tests/test_verify_codeql_coverage.py` | 1 |
| `.github/security-gates.yml` | 4 |
| `scripts/ci/verify_security_workflows.py` | 4 |
| `scripts/tests/test_verify_security_workflows.py` | 4 |
| `scripts/ci/mobsf_truthfulness.py` | 5 |
| `scripts/tests/test_mobsf_truthfulness.py` | 5 |
| `tools/quilla-defensive-crawler/tests/test_adversarial_properties.py` | 2 |
| `tools/quilla-defensive-crawler/tests/fixtures/adversarial/*.json` | 3 |
| `scripts/policy/verify_play_policy.py` | 8 |
| `scripts/tests/test_verify_play_policy.py` | 8 |
| `docs/play/VPN_SERVICE_POLICY.md` | 8 |
| `docs/play/DATA_SAFETY_MAP.md` | 8/9 |
| `scripts/policy/data_safety_inventory.yml` | 9 |
| `scripts/policy/verify_data_safety_drift.py` | 9 |
| `scripts/tests/test_verify_data_safety_drift.py` | 9 |
| `docs/security/DEPENDENCY_ACCEPTANCE.md` | 10 |
| `docs/security/direct-dependency-allowlist.yml` | 10 |
| `scripts/ci/generate_release_manifest.py` | 7 |
| `scripts/tests/test_generate_release_manifest.py` | 7 |
| `testdata/security/adversarial/README.md` | 3 |

## Files to modify

| Path | Task | Change |
|---|---|---|
| `.github/workflows/release.yml` | 6, 7 | add `gh attestation verify` step + manifest generation + publish manifest |
| `.github/workflows/mobsf.yml` | 5 | call `mobsf_truthfulness.py` for truthful status (still advisory) |
| `.github/workflows/android.yml` | 11 | wire meta-gates early, fast-fail before instrumentation |
| `.github/workflows/quilla-crawler-validate.yml` | 11 | run new adversarial test file |

## Test-first expectation (Task 12)

Every repository-owned validator follows: write failing test -> run -> confirm
failure -> implement minimum code -> run targeted test -> run full validator
suite -> commit. No backfilled tests.

## Task verification matrix

| Task | Verification command (local) | Expected |
|---|---|---|
| 1 | `python3 -m pytest scripts/tests/test_verify_codeql_coverage.py -v` | PASS |
| 2-3 | `cd tools/quilla-defensive-crawler && python -m pytest tests/test_adversarial_properties.py -v` | PASS |
| 4 | `python3 -m pytest scripts/tests/test_verify_security_workflows.py -v` | PASS |
| 5 | `python3 -m pytest scripts/tests/test_mobsf_truthfulness.py -v` | PASS |
| 7 | `python3 -m pytest scripts/tests/test_generate_release_manifest.py -v` | PASS |
| 8 | `python3 -m pytest scripts/tests/test_verify_play_policy.py -v` | PASS |
| 9 | `python3 -m pytest scripts/tests/test_verify_data_safety_drift.py -v` | PASS |
| all python | `python3 scripts/ci/verify_codeql_coverage.py && python3 scripts/ci/verify_security_workflows.py && python3 scripts/policy/verify_play_policy.py && python3 scripts/policy/verify_data_safety_drift.py` | PASS (exit 0) |
| quilla lint | `cd tools/quilla-defensive-crawler && python -m ruff check quilla_crawler tests && python -m ruff format --check quilla_crawler tests && python -m mypy quilla_crawler --ignore-missing-imports` | PASS |
| gradle | `./gradlew clean` … `:app:verifyNoPlaceholderApk` | NOT RUN locally (no Android SDK); CI on head SHA |

## Commit boundaries

1. `docs(plan): add V3.1A verification integrity plan`
2. `test(ci): define CodeQL Kotlin coverage invariants`
3. `ci(codeql): enforce Kotlin-aware CodeQL analysis`
4. `test(ci): define security workflow invariants`
5. `ci(security): add machine-readable gate classifications`
6. `security(policy): separate MobSF execution from findings status`
7. `test(security): add adversarial parser property tests`
8. `security(test): add deterministic adversarial corpus`
9. `test(policy): define Play/VPN policy invariants`
10. `security(policy): add Play and Data Safety drift checks`
11. `security(policy): document dependency acceptance policy`
12. `test(release): define release manifest invariants`
13. `security(release): verify artifact attestations and emit manifest`
14. `ci(android): wire meta-gates early for fast-fail ordering`

## Stop conditions

Stop and report `BLOCKED` if: main does not build (CI), a validator reveals an
existing serious security regression, CodeQL config conflicts with the repo
build, attestation verification requires changing trust boundaries, the VPN
checker reveals behavior contradicting documentation, or any change would
require weakening a security gate or changing product behavior.

## Deferred to V3.1B

native/JNI fuzzing, dynamic MobSF/DAST, physical-device smoke matrix, VPN
behavioral adversarial testing, RASP physical-device testing, Play Integrity
physical-device testing. Physical-device trigger policy: VPN changed, native/RASP
changed, Play Integrity changed, device-trust changed, hardware-backed Keystore
changed, OEM-sensitive API changed.
