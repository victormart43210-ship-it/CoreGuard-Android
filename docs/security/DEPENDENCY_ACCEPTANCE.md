# Dependency Acceptance Policy — CoreGuard

> Scope: newly introduced **direct production dependencies** only. This is a
> lightweight, human-readable acceptance gate that complements (does not
> replace) automated dependency scanning.

## 1. Purpose

Every new direct production dependency added to CoreGuard must be explicitly
accepted in a PR before merge. This document defines what "accepted" means and
is paired with a machine-readable inventory of currently-accepted direct
dependencies at `docs/security/direct-dependency-allowlist.json`.

This policy is intentionally lightweight (YAGNI): it does not attempt to
re-derive the full transitive dependency graph, which is already covered by
automated tooling (see §4).

## 2. What counts as a "direct production dependency"

A direct production dependency is a dependency declared directly (not
transitively) in a production scope of one of:

- `app/build.gradle` + `gradle/android-app.gradle` — Android `implementation`
  (and `kapt`) declarations shipped in the release APK.
- `tools/quilla-defensive-crawler/requirements.txt` — runtime Python
  dependencies of the Quilla defensive crawler tooling.
- `cli/go.mod` — direct `require` declarations of the CoreGuard CLI.

**Excluded** from manual acceptance (handled automatically or out of scope):

- **Transitive dependencies.** Pulled in automatically by the build system;
  not manually inventoried here (YAGNI). Their vulnerabilities are caught by
  the automated dependency scan (§4) and Dependabot.
- **Test-only / debug-only** dependencies (`testImplementation`,
  `androidTestImplementation`, `debugImplementation`) — never shipped.
- **Dev extras** in `requirements.txt` (`pytest`, `mypy`, `ruff`, etc.) —
  developer tooling, not shipped.
- **Internal modules** (`project(":core:model")`) — first-party source, not a
  third-party package.

## 3. Acceptance record (per new direct dependency)

Each new direct production dependency must add an entry to
`docs/security/direct-dependency-allowlist.json` recording:

| Field | Description |
| --- | --- |
| `name` | Package / artifact name (e.g. `androidx.core:core-ktx`). |
| `version` | Pinned version (or BOM-managed). |
| `purpose` | What CoreGuard uses it for and why it is needed. |
| `license` | Declared SPDX license identifier(s). |
| `source` | Canonical source repository URL. |
| `maintainer` | Maintainer / project provenance (e.g. Google, Kotlin, pyca). |
| `security_relevance` | Whether the dependency touches sensitive flows (networking, crypto, billing, identity, native code) and any security note. |
| `why_needed` | Why this dependency is required vs. an alternative or stdlib. |

The PR adding the dependency must add the entry **before merge**. Bumping a
version of an already-accepted dependency does not require a new acceptance
record, but should be reflected by updating the `version` field in the same PR.

## 4. Relationship to automated scanning

Automated dependency scanning remains in place and is **not** replaced by this
acceptance step:

- `.github/workflows/dependency-review.yml` runs the GitHub
  `dependency-review-action` on every PR to `main`, failing closed at
  `fail-on-severity: high`.
- `.github/dependabot.yml` keeps dependency version bump PRs flowing.
- `.github/workflows/black-duck-security-scan-ci.yml` runs Black Duck SCA.

This acceptance policy is a human gate on top of those: it ensures every new
direct dependency has a recorded purpose, provenance, and security-relevance
note at the time it is introduced — context the automated scanners do not
produce.

## 5. When to update this policy and the allowlist

- **Adding** a new direct production dependency → add an entry to
  `direct-dependency-allowlist.json` in the same PR.
- **Removing** a dependency → remove its entry in the same PR.
- **Bumping** a version → update the `version` field in the same PR.

The allowlist is a snapshot of currently-accepted direct dependencies. It is
not expected to be exhaustive for transitive deps (YAGNI).
