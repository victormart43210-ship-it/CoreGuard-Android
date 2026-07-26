# Enable GitHub Dependency Graph (owner action)

Dependency Review CI requires the repository Dependency Graph. Until it is
enabled, the workflow soft-fails with a warning (see
`.github/workflows/dependency-review.yml`).

## Enable (repository admin)

1. Open **Settings → Security → Code security and analysis**
2. Enable **Dependency graph**
3. (Recommended) Enable **Dependabot alerts**
4. Re-run the **Dependency review** workflow on any open PR, or open a no-op PR

Direct link pattern:

`https://github.com/victormart43210-ship-it/CoreGuard-Android/settings/security_analysis`

This cannot be toggled by the Cursor GitHub integration (HTTP 403 on
vulnerability-alerts / repo admin APIs).
