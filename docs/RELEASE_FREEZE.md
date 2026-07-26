# Release freeze (Internal Testing)

**Status:** Active until Internal Testing is stable on Play Console.

## Allowed changes

- Release blockers and crash fixes
- Security, privacy, and policy compliance
- Accessibility, performance, and documentation accuracy
- CI / signing / R8 / test reliability

## Not allowed

- New product features
- Speculative UI redesigns
- Mystical/lore expansions that affect evidence UX
- Drive-by dependency upgrades unrelated to build breaks

## Version source of truth

| Field | Location |
|-------|----------|
| `versionCode` / `versionName` | `gradle/android-app.gradle` only |
| Release package | `com.coldboar.coreguard` |
| Debug package | `com.coldboar.coreguard.debug` |

Do not invent a second version string in docs without updating Gradle first.
