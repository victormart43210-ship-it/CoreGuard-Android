# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 1.x (latest) | ✅ |
| < 1.0 | ❌ |

## Official Distribution

CoreGuard-Android is distributed exclusively through this repository's [GitHub Releases](https://github.com/victormart43210-ship-it/CoreGuard-Android/releases) page.

**Official application identity:**
- Package name: `com.coldboar.coreguard`
- Release artifacts are signed under the project's controlled signing key
- SHA-256 checksums are published alongside each release for verification

Warn against installing APKs from unofficial sources, repackaged builds, or versions not matching the published checksum. The project license (Apache 2.0) permits forks and redistribution, so always verify origin before installation on sensitive devices.

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

To report a vulnerability, open a [GitHub Security Advisory](https://github.com/victormart43210-ship-it/CoreGuard-Android/security/advisories/new) in this repository. This uses GitHub's private disclosure channel, which keeps the details confidential until a fix is available.

Include in your report:
- A description of the vulnerability and its potential impact
- Steps to reproduce or a proof-of-concept (no exploit code that could cause harm)
- Affected version(s) and any relevant device or OS configuration
- Whether you believe a CVE should be assigned

### What to expect

| Stage | Timeframe |
|-------|-----------|
| Acknowledgement of receipt | Within 5 business days |
| Initial triage and severity assessment | Within 10 business days |
| Status update or remediation plan | Within 30 days |
| Public disclosure (coordinated) | After a fix is available and the reporter is notified |

We follow responsible disclosure. We will coordinate a disclosure timeline with you and credit you in release notes unless you prefer to remain anonymous.

## Signing Key and Update Continuity

Release APKs are signed with a project-controlled key. The signing-certificate SHA-256 fingerprint is published in each GitHub Release description and in the repository's release notes. Verify the fingerprint before installing an update.

If a signing key must be rotated (e.g., due to compromise), an advisory will be published, existing releases will be marked deprecated, and a migration path will be documented.

## Security Controls in This Repository

- **Backup disabled**: `android:allowBackup="false"` with explicit data-extraction rules that exclude all data from cloud and device-transfer backup.
- **Cleartext blocked**: Network security configuration disables all cleartext HTTP traffic.
- **Release shrinking**: Release builds use ProGuard/R8 with resource shrinking enabled.
- **Least-privilege permissions**: Only permissions required for shipped features are declared. All dangerous and special permissions are requested at runtime with contextual explanations.
- **Automated dependency updates**: Dependabot is enabled for Gradle and GitHub Actions dependencies.
- **CI/CD provenance**: Release builds are produced by GitHub Actions from a protected tag and include a GitHub artifact attestation linking the binary to the source commit and workflow.

## Scope

In scope for vulnerability reports:
- The Android application source (`app/`) and its declared permissions and behaviors
- The CI/CD release pipeline and signing process
- Any network endpoints or backend services reachable from the app

Out of scope:
- Attacks requiring physical access to an already-compromised device with screen unlocked
- Social engineering or phishing that is not a direct consequence of an application bug
- Vulnerabilities in third-party dependencies (report those upstream; Dependabot alerts will be monitored)
- Denial-of-service attacks with no privacy or data-integrity impact

## Incident Response

In the event of a confirmed significant vulnerability:
1. A private patch branch is created from the affected release tag.
2. A fix is developed, reviewed, and tested.
3. A new signed release is produced via the CI pipeline with an updated attestation.
4. A GitHub Security Advisory is published with the CVE (if applicable), affected versions, fixed version, and mitigation steps.
5. Affected users are notified via the release notes and repository advisory feed.

If credentials or backend keys are compromised, they are rotated before any public disclosure.
