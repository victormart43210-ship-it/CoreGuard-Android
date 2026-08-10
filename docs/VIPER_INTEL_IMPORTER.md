# Viper threat-intelligence importer

## Overview

CoreGuard now supports a Viper importer that converts Viper records into the same `CyberKnowledgeBase.Entry` shape used by the existing Anki-backed codex flow.

- Importer: `app/src/main/java/com/coldboar/coreguard/knowledge/ViperThreatIntelImporter.kt`
- Shared query/repository layer: `app/src/main/java/com/coldboar/coreguard/knowledge/ThreatKnowledgeRepository.kt`

## Import workflow

1. Receive Viper JSON payload (`records[]`).
2. Sanitize every free-text field (strip control chars/markup, trim, bound length).
3. Validate required data (`title` + `summary`; id/indicator normalization).
4. Normalize severity/confidence for defensive-only use:
   - severity is capped to `LOW`/`MEDIUM`
   - confidence is capped to `<= 0.60`
5. Map each valid record to `CyberKnowledgeBase.Entry`.
6. Merge mapped entries through `SharedThreatKnowledgeRepository.mergeViperKnowledge(...)` (or `importViperPayload(...)`).

## Security constraints

Viper data is **knowledge context only**.

- It is never treated as automatic proof of infection or compromise.
- Repository query matches expose non-proof constraints (`provesCompromise=false`, confidence/severity caps).
- Device verdicts still require on-device evidence (Nemesis findings, timeline/correlation context).

## Shared query layer

`SharedThreatKnowledgeRepository` supports both sources:

- `ANKI`: bundled/curated codex-style entries
- `VIPER`: imported Viper records after sanitization/validation

`search(...)` returns unified ranked matches across both sources, with optional source filtering so CoreGuard and Quilla can query either source or both from one interface.
