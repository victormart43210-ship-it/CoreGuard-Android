# Adversarial Input Corpus (Security)

This directory documents the canonical adversarial / malformed-input corpus used
by the Quilla defensive crawler's parser-robustness and verification-integrity
property tests.

## Canonical corpus location

The canonical, machine-consumed fixture files live alongside the crawler's test
suite (kept under version control with the tests that consume them):

```
tools/quilla-defensive-crawler/tests/fixtures/adversarial/
```

Consumed by: `tools/quilla-defensive-crawler/tests/test_adversarial_properties.py`.

These are **parser-robustness** fixtures only. They contain no malware, no
exploit code, and no offensive payloads — just malformed, oversized, or
edge-case structured data used to prove the crawler's parsing and validation
surfaces fail closed and never crash.

## Fixture categories

| File | Category |
| --- | --- |
| `empty_bundle.json` | Null/empty object — empty top-level object. |
| `malformed_json.txt` | Invalid JSON — syntactically broken JSON text. |
| `deep_nesting.json` | Bounded deep nesting — 50 nested array levels (no stack overflow). |
| `duplicate_keys.json` | Duplicate keys — JSON last-value-wins semantics. |
| `invalid_digest.json` | Digest mismatch — `entries_sha256` does not match the entries. |
| `valid_digest_invalid_schema.json` | Valid digest, invalid schema — correct digest but unsupported/missing fields. |
| `invalid_unicode.bin` | Invalid encoding — non-UTF-8 byte sequence. |
| `truncated_feed.json` | Truncated input — JSON cut off mid-record. |
| `oversized_field.json` | Oversized field — string far exceeding `MAX_TITLE_CHARS`. |
| `unexpected_nested_object.json` | Unexpected nesting — object/array where a scalar string is expected. |
| `future_timestamp.json` | Temporal edge — far-future ISO-8601 timestamp. |
| `empty_stix_objects.json` | Empty collection — STIX-style bundle with no objects. |
| `partially_valid_bundle.json` | Mixed validity — one valid entry plus malformed entries. |
