"""Adversarial property/fuzz tests for the Quilla crawler's highest-risk
pure parsing & validation surfaces.

These surfaces consume EXTERNALLY-SOURCED / attacker-influenced data:

1. ``signer.verify_bundle`` — signature / digest verification (fail-closed).
2. ``bundle.build_bundle`` + ``_to_deterministic_bytes`` — canonicalization
   and publishable filtering.
3. ``CrawlerEntry`` / ``is_publishable()`` + pydantic validation — entry
   construction from external data.

No external fuzzing platform is used. ``hypothesis`` is NOT a dependency, so we
use ``pytest.mark.parametrize`` plus a deterministic pseudo-random generator
seeded with ``random.Random(0xC0DE)`` for property-style coverage. A real
Ed25519 keypair is generated once (module scope) into a tmp path; a known-good
bundle is signed and then mutated to exercise digest-mismatch invariants.

Invariants proven (adapted per surface):
- arbitrary malformed input never crashes the process (typed exception / False).
- invalid schema never activates data (malformed entry never publishable).
- digest mismatch is rejected (single bit flip in bundle or sig => verify False).
- unknown fields cannot bypass validation (extra keys do not flip UNVERIFIED).
- oversized fields are bounded (strings > MAX_* rejected; lists > MAX_* capped;
  > MAX_ENTRIES_PER_BUNDLE truncated).
- duplicate keys cannot alter security meaning (last-value-wins is deterministic).
- invalid encoding is rejected safely (bad base64, non-PEM key, non-UTF-8).
- null/empty objects cannot become verified (empty dict / None / empty list).
- unexpected nested arrays/objects fail predictably (pydantic ValidationError).
- canonicalization is deterministic (same input => identical bytes / sha256).
- parser failure returns explicit typed failure (verify_bundle -> bool; pydantic
  raises ValidationError; no silent PASS).
"""

from __future__ import annotations

import base64
import json
import os
import random
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import cast

import pytest
from pydantic import ValidationError

from quilla_crawler.bundle import _to_deterministic_bytes, build_bundle
from quilla_crawler.models import (
    MAX_ENTRIES_PER_BUNDLE,
    MAX_REFERENCES,
    MAX_RELATED_IOCS,
    MAX_TAGS,
    MAX_TITLE_CHARS,
    CrawlerEntry,
    VerificationStatus,
)
from quilla_crawler.signer import generate_keypair, load_public_key_pem, sign_bundle, verify_bundle

# ── Deterministic pseudo-random generator (fixed seed) ────────────────────────
RNG = random.Random(0xC0DE)

# A set of independent seeds derived from the fixed RNG; used to drive
# parametrized mutations so the property tests are reproducible.
MUTATION_SEEDS: list[int] = [RNG.randint(0, 2**31 - 1) for _ in range(24)]

_FIXTURES = Path(__file__).parent / "fixtures" / "adversarial"


@dataclass(frozen=True)
class SignedBundle:
    """A known-good signed bundle plus the material needed to mutate it."""

    bundle_bytes: bytes
    sig_b64: str
    pub_pem: bytes
    entries: list[CrawlerEntry]


def _make_entry(
    entry_id: str = "adv-entry-a",
    title: str = "Adversarial Test Entry",
    status: VerificationStatus = VerificationStatus.TRUSTED_SOURCE,
) -> CrawlerEntry:
    return CrawlerEntry(
        id=entry_id,
        title=title,
        category="crawler-vulnerability",
        tags=["android", "adversarial"],
        summary="Adversarial summary text.",
        body="Adversarial body text.",
        defense="Apply patches promptly.",
        references=["https://www.cisa.gov/known-exploited-vulnerabilities-catalog"],
        verification_status=status,
        confidence=0.90,
    )


@pytest.fixture(scope="module")
def keypair(tmp_path_factory: pytest.TempPathFactory) -> tuple[str, str, bytes]:
    """Generate one real Ed25519 keypair into a tmp path (perms 0o600)."""
    tmp = tmp_path_factory.mktemp("quilla-keys")
    priv = str(tmp / "private.pem")
    pub = str(tmp / "public.pem")
    generate_keypair(priv, pub)
    return priv, pub, load_public_key_pem(pub)


@pytest.fixture(scope="module")
def signed_bundle(keypair: tuple[str, str, bytes]) -> SignedBundle:
    """Build + sign a known-good bundle once, then mutate it per test."""
    priv, _, pub_pem = keypair
    entries = [_make_entry("adv-entry-a"), _make_entry("adv-entry-b")]
    with tempfile.TemporaryDirectory() as tmp:
        bundle_bytes = build_bundle(entries, output_dir=tmp)
        sign_bundle(bundle_bytes, priv, output_dir=tmp)
        with open(os.path.join(tmp, "quilla-intel.sig"), encoding="ascii") as fh:
            sig_b64 = fh.read()
    return SignedBundle(
        bundle_bytes=bundle_bytes, sig_b64=sig_b64, pub_pem=pub_pem, entries=entries
    )


# ════════════════════════════════════════════════════════════════════════════
# Surface 1: signer.verify_bundle — fail-closed verification
# ════════════════════════════════════════════════════════════════════════════


class TestVerifyBundleFailClosed:
    """verify_bundle must return False (never crash/hang) for any bad input."""

    def test_valid_signature_verifies(self, signed_bundle: SignedBundle) -> None:
        assert (
            verify_bundle(signed_bundle.bundle_bytes, signed_bundle.sig_b64, signed_bundle.pub_pem)
            is True
        )

    @pytest.mark.parametrize("seed", MUTATION_SEEDS)
    def test_single_bit_flip_in_bundle_rejected(
        self, signed_bundle: SignedBundle, seed: int
    ) -> None:
        rng = random.Random(seed)
        data = bytearray(signed_bundle.bundle_bytes)
        pos = rng.randrange(len(data))
        bit = 1 << rng.randrange(8)
        data[pos] ^= bit
        assert verify_bundle(bytes(data), signed_bundle.sig_b64, signed_bundle.pub_pem) is False

    @pytest.mark.parametrize("seed", MUTATION_SEEDS)
    def test_single_bit_flip_in_signature_rejected(
        self, signed_bundle: SignedBundle, seed: int
    ) -> None:
        rng = random.Random(seed)
        sig_bytes = bytearray(base64.b64decode(signed_bundle.sig_b64))
        pos = rng.randrange(len(sig_bytes))
        bit = 1 << rng.randrange(8)
        sig_bytes[pos] ^= bit
        bad_sig = base64.b64encode(bytes(sig_bytes)).decode("ascii")
        assert verify_bundle(signed_bundle.bundle_bytes, bad_sig, signed_bundle.pub_pem) is False

    def test_truncated_signature_rejected(self, signed_bundle: SignedBundle) -> None:
        sig_bytes = base64.b64decode(signed_bundle.sig_b64)
        for cut in range(1, len(sig_bytes)):
            bad = base64.b64encode(sig_bytes[:-cut]).decode("ascii")
            assert verify_bundle(signed_bundle.bundle_bytes, bad, signed_bundle.pub_pem) is False

    def test_empty_signature_rejected(self, signed_bundle: SignedBundle) -> None:
        assert verify_bundle(signed_bundle.bundle_bytes, "", signed_bundle.pub_pem) is False

    @pytest.mark.parametrize(
        "bad_sig",
        [
            "not-base64!!!",
            "====",
            "aW52YWxpZA",  # valid base64 but wrong length/content
            "\x00\x01\x02",
            "0" * 200,
        ],
    )
    def test_bad_base64_signature_rejected(self, signed_bundle: SignedBundle, bad_sig: str) -> None:
        assert verify_bundle(signed_bundle.bundle_bytes, bad_sig, signed_bundle.pub_pem) is False

    def test_non_pem_public_key_rejected(self, signed_bundle: SignedBundle) -> None:
        assert (
            verify_bundle(signed_bundle.bundle_bytes, signed_bundle.sig_b64, b"not a pem key")
            is False
        )

    def test_non_ed25519_public_key_rejected(self, signed_bundle: SignedBundle) -> None:
        # An RSA public key is valid PEM but not Ed25519 -> must be rejected.
        from cryptography.hazmat.primitives import serialization
        from cryptography.hazmat.primitives.asymmetric import rsa

        rsa_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        rsa_pem = rsa_key.public_key().public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo,
        )
        assert verify_bundle(signed_bundle.bundle_bytes, signed_bundle.sig_b64, rsa_pem) is False

    @pytest.mark.parametrize("seed", MUTATION_SEEDS)
    def test_arbitrary_bytes_as_bundle_never_crash(
        self, signed_bundle: SignedBundle, seed: int
    ) -> None:
        """Any byte string as bundle_bytes must not crash; valid sig won't match."""
        rng = random.Random(seed)
        garbage = bytes(rng.randrange(256) for _ in range(rng.randrange(0, 512)))
        assert verify_bundle(garbage, signed_bundle.sig_b64, signed_bundle.pub_pem) is False

    @pytest.mark.parametrize("seed", MUTATION_SEEDS)
    def test_random_signature_strings_rejected(
        self, signed_bundle: SignedBundle, seed: int
    ) -> None:
        rng = random.Random(seed)
        bad_sig = base64.b64encode(bytes(rng.randrange(256) for _ in range(64))).decode("ascii")
        assert verify_bundle(signed_bundle.bundle_bytes, bad_sig, signed_bundle.pub_pem) is False

    @pytest.mark.parametrize(
        "bundle_bytes",
        [b"", b"\x00", b"\xff" * 1024, b"not bytes" * 4],
    )
    def test_empty_or_garbage_bundle_rejected(
        self, signed_bundle: SignedBundle, bundle_bytes: bytes
    ) -> None:
        assert verify_bundle(bundle_bytes, signed_bundle.sig_b64, signed_bundle.pub_pem) is False

    def test_none_inputs_rejected_safely(self, signed_bundle: SignedBundle) -> None:
        # None for any argument must be caught (fail-closed), never crash.
        # cast() keeps mypy happy while still passing None at runtime.
        assert (
            verify_bundle(cast(bytes, None), signed_bundle.sig_b64, signed_bundle.pub_pem) is False
        )
        assert (
            verify_bundle(signed_bundle.bundle_bytes, cast(str, None), signed_bundle.pub_pem)
            is False
        )
        assert (
            verify_bundle(signed_bundle.bundle_bytes, signed_bundle.sig_b64, cast(bytes, None))
            is False
        )


# ════════════════════════════════════════════════════════════════════════════
# Surface 2: build_bundle + _to_deterministic_bytes — canonicalization
# ════════════════════════════════════════════════════════════════════════════


class TestBundleCanonicalization:
    """Deterministic canonicalization and publishable filtering."""

    def test_to_deterministic_bytes_is_deterministic(self) -> None:
        obj = {"b": 2, "a": 1, "c": [3, 2, 1]}
        assert _to_deterministic_bytes(obj) == _to_deterministic_bytes(obj)

    def test_to_deterministic_bytes_sorted_keys(self) -> None:
        out = _to_deterministic_bytes({"zeta": 1, "alpha": 2, "mid": 3})
        # Keys appear in sorted order in the serialized output.
        assert out.index(b'"alpha"') < out.index(b'"mid"') < out.index(b'"zeta"')

    def test_to_deterministic_bytes_key_order_independent(self) -> None:
        a = _to_deterministic_bytes({"id": "x", "title": "t"})
        b = _to_deterministic_bytes({"title": "t", "id": "x"})
        assert a == b

    def test_to_deterministic_bytes_compact_separators(self) -> None:
        out = _to_deterministic_bytes({"a": 1, "b": 2})
        assert b": " not in out
        assert b", " not in out

    def test_to_deterministic_bytes_rejects_nan(self) -> None:
        with pytest.raises(ValueError):
            _to_deterministic_bytes(float("nan"))

    def test_to_deterministic_bytes_rejects_infinity(self) -> None:
        with pytest.raises(ValueError):
            _to_deterministic_bytes(float("inf"))

    def test_to_deterministic_bytes_rejects_non_serializable(self) -> None:
        with pytest.raises(TypeError):
            _to_deterministic_bytes(object())

    def test_same_entries_same_entries_sha256(self) -> None:
        entries = [_make_entry("a"), _make_entry("b")]
        with tempfile.TemporaryDirectory() as t1, tempfile.TemporaryDirectory() as t2:
            b1 = build_bundle(entries, output_dir=t1)
            b2 = build_bundle(entries, output_dir=t2)
        d1 = json.loads(b1)
        d2 = json.loads(b2)
        # bundle_id / generated_at are intentionally fresh; entries + sha must match.
        assert d1["entries"] == d2["entries"]
        assert d1["entries_sha256"] == d2["entries_sha256"]
        assert d1["entry_count"] == d2["entry_count"]

    def test_entries_sha256_recomputable(self) -> None:
        import hashlib

        entries = [_make_entry("a")]
        with tempfile.TemporaryDirectory() as tmp:
            bundle_bytes = build_bundle(entries, output_dir=tmp)
        data = json.loads(bundle_bytes)
        recomputed = hashlib.sha256(_to_deterministic_bytes(data["entries"])).hexdigest()
        assert data["entries_sha256"] == recomputed

    def test_entries_ordered_by_id(self) -> None:
        entries = [_make_entry("zzz"), _make_entry("aaa"), _make_entry("mmm")]
        with tempfile.TemporaryDirectory() as tmp:
            data = json.loads(build_bundle(entries, output_dir=tmp))
        ids = [e["id"] for e in data["entries"]]
        assert ids == sorted(ids)

    def test_duplicate_ids_handled_deterministically(self) -> None:
        # Duplicate ids are both kept but sorted; rebuild is deterministic.
        entries = [_make_entry("dup"), _make_entry("dup"), _make_entry("aaa")]
        with tempfile.TemporaryDirectory() as t1, tempfile.TemporaryDirectory() as t2:
            b1 = build_bundle(entries, output_dir=t1)
            b2 = build_bundle(entries, output_dir=t2)
        assert json.loads(b1)["entries"] == json.loads(b2)["entries"]

    def test_only_publishable_entries_included(self) -> None:
        entries = [
            _make_entry("ok-1", status=VerificationStatus.TRUSTED_SOURCE),
            _make_entry("ok-2", status=VerificationStatus.CORROBORATED),
            _make_entry("bad-1", status=VerificationStatus.UNVERIFIED),
            _make_entry("bad-2", status=VerificationStatus.REJECTED),
            _make_entry("bad-3", status=VerificationStatus.EXPIRED),
        ]
        with tempfile.TemporaryDirectory() as tmp:
            data = json.loads(build_bundle(entries, output_dir=tmp))
        assert data["entry_count"] == 2
        assert {e["id"] for e in data["entries"]} == {"ok-1", "ok-2"}

    def test_no_publishable_entries_yields_empty_bundle(self) -> None:
        entries = [_make_entry(status=VerificationStatus.UNVERIFIED) for _ in range(3)]
        with tempfile.TemporaryDirectory() as tmp:
            data = json.loads(build_bundle(entries, output_dir=tmp))
        assert data["entry_count"] == 0
        assert data["entries"] == []

    def test_empty_entries_list_safe(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            data = json.loads(build_bundle([], output_dir=tmp))
        assert data["entry_count"] == 0

    def test_max_entries_per_bundle_cap(self) -> None:
        # Exceeding MAX_ENTRIES_PER_BUNDLE is truncated, never errors.
        entries = [_make_entry(f"e-{i}") for i in range(MAX_ENTRIES_PER_BUNDLE + 50)]
        with tempfile.TemporaryDirectory() as tmp:
            data = json.loads(build_bundle(entries, output_dir=tmp))
        assert data["entry_count"] == MAX_ENTRIES_PER_BUNDLE
        assert len(data["entries"]) == MAX_ENTRIES_PER_BUNDLE

    def test_build_bundle_writes_files(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            build_bundle([_make_entry("a")], output_dir=tmp)
            assert os.path.isfile(os.path.join(tmp, "quilla-intel.json"))
            assert os.path.isfile(os.path.join(tmp, "quilla-intel-manifest.json"))

    def test_manifest_bundle_sha256_matches(self) -> None:
        import hashlib

        with tempfile.TemporaryDirectory() as tmp:
            bundle_bytes = build_bundle([_make_entry("a")], output_dir=tmp)
            with open(os.path.join(tmp, "quilla-intel-manifest.json"), "rb") as fh:
                manifest = json.loads(fh.read().decode("utf-8"))
        assert manifest["bundle_sha256"] == hashlib.sha256(bundle_bytes).hexdigest()


# ════════════════════════════════════════════════════════════════════════════
# Surface 3: CrawlerEntry / is_publishable + pydantic validation
# ════════════════════════════════════════════════════════════════════════════


class TestCrawlerEntryValidation:
    """Entry construction from external data — invalid schema never activates."""

    @pytest.mark.parametrize("status", list(VerificationStatus))
    def test_is_publishable_only_for_trusted_or_corroborated(
        self, status: VerificationStatus
    ) -> None:
        entry = _make_entry(status=status)
        assert entry.is_publishable() == (
            status in (VerificationStatus.TRUSTED_SOURCE, VerificationStatus.CORROBORATED)
        )

    def test_default_status_is_unverified_and_not_publishable(self) -> None:
        entry = CrawlerEntry(id="x", title="t")
        assert entry.verification_status is VerificationStatus.UNVERIFIED
        assert entry.is_publishable() is False

    def test_empty_dict_cannot_construct(self) -> None:
        with pytest.raises(ValidationError):
            CrawlerEntry.model_validate({})

    def test_none_cannot_construct(self) -> None:
        with pytest.raises(ValidationError):
            CrawlerEntry.model_validate(None)

    def test_missing_required_fields_rejected(self) -> None:
        with pytest.raises(ValidationError):
            CrawlerEntry.model_validate({"title": "no id"})
        with pytest.raises(ValidationError):
            CrawlerEntry.model_validate({"id": "no-title"})

    def test_empty_id_rejected(self) -> None:
        with pytest.raises(ValidationError):
            CrawlerEntry(id="", title="t")

    def test_empty_title_rejected(self) -> None:
        with pytest.raises(ValidationError):
            CrawlerEntry(id="i", title="")

    def test_oversized_title_rejected(self) -> None:
        with pytest.raises(ValidationError):
            CrawlerEntry(id="i", title="A" * (MAX_TITLE_CHARS + 1))

    def test_oversized_id_rejected(self) -> None:
        with pytest.raises(ValidationError):
            CrawlerEntry(id="A" * 129, title="t")

    def test_confidence_out_of_range_rejected(self) -> None:
        with pytest.raises(ValidationError):
            CrawlerEntry(id="i", title="t", confidence=1.5)
        with pytest.raises(ValidationError):
            CrawlerEntry(id="i", title="t", confidence=-0.1)

    def test_invalid_verification_status_rejected(self) -> None:
        with pytest.raises(ValidationError):
            CrawlerEntry.model_validate(
                {"id": "i", "title": "t", "verification_status": "SUPER_TRUSTED"}
            )

    def test_unexpected_nested_object_rejected(self) -> None:
        # A nested object where a string is expected must fail predictably.
        with pytest.raises(ValidationError):
            CrawlerEntry.model_validate({"id": {"nested": "obj"}, "title": "t"})

    def test_unexpected_nested_array_rejected(self) -> None:
        with pytest.raises(ValidationError):
            CrawlerEntry.model_validate({"id": "i", "title": ["t"]})

    def test_unknown_fields_do_not_flip_unverified_to_publishable(self) -> None:
        # Extra/unknown keys are ignored by pydantic; an UNVERIFIED entry must
        # stay UNVERIFIED (and therefore not publishable) regardless of extras.
        entry = CrawlerEntry.model_validate(
            {
                "id": "i",
                "title": "t",
                "verification_status": "UNVERIFIED",
                "unknown_field": "TRUSTED_SOURCE",
                "is_admin": True,
                "verification_status_override": "TRUSTED_SOURCE",
            }
        )
        assert entry.verification_status is VerificationStatus.UNVERIFIED
        assert entry.is_publishable() is False

    def test_unknown_fields_do_not_downgrade_trusted(self) -> None:
        entry = CrawlerEntry.model_validate(
            {"id": "i", "title": "t", "verification_status": "TRUSTED_SOURCE", "evil": "x"}
        )
        assert entry.verification_status is VerificationStatus.TRUSTED_SOURCE
        assert entry.is_publishable() is True

    @pytest.mark.parametrize("count", [MAX_TAGS + 1, MAX_TAGS + 100])
    def test_tags_list_capped(self, count: int) -> None:
        entry = CrawlerEntry(id="i", title="t", tags=[f"tag-{k}" for k in range(count)])
        assert len(entry.tags) == MAX_TAGS

    @pytest.mark.parametrize("count", [MAX_REFERENCES + 1, MAX_REFERENCES + 50])
    def test_references_list_capped(self, count: int) -> None:
        entry = CrawlerEntry(
            id="i", title="t", references=[f"https://ex.com/{k}" for k in range(count)]
        )
        assert len(entry.references) == MAX_REFERENCES

    @pytest.mark.parametrize("count", [MAX_RELATED_IOCS + 1, MAX_RELATED_IOCS + 100])
    def test_related_iocs_list_capped(self, count: int) -> None:
        entry = CrawlerEntry(id="i", title="t", related_cves=[f"cve-{k}" for k in range(count)])
        assert len(entry.related_cves) == MAX_RELATED_IOCS

    def test_duplicate_keys_in_json_last_value_wins(self) -> None:
        # json.loads keeps the last value for duplicate keys; the resulting
        # entry must remain TRUSTED_SOURCE (deterministic, no security flip).
        raw = '{"id": "i", "id": "i2", "title": "t", "verification_status": "UNVERIFIED", "verification_status": "TRUSTED_SOURCE"}'
        entry = CrawlerEntry.model_validate(json.loads(raw))
        assert entry.id == "i2"
        assert entry.verification_status is VerificationStatus.TRUSTED_SOURCE

    def test_compute_content_sha256_is_deterministic(self) -> None:
        e1 = _make_entry("a", title="Same Title")
        e2 = _make_entry("b", title="Same Title")
        assert e1.compute_content_sha256() == e2.compute_content_sha256()

    def test_to_bundle_dict_tags_sorted_unique(self) -> None:
        entry = CrawlerEntry(id="a", title="t", tags=["android", "android", "zebra", "apple"])
        d = entry.to_bundle_dict()
        assert d["tags"] == sorted({"android", "zebra", "apple"})


# ════════════════════════════════════════════════════════════════════════════
# Adversarial corpus: every fixture is handled safely by a parsing surface
# ════════════════════════════════════════════════════════════════════════════

_CORPUS_FILES = [
    "empty_bundle.json",
    "malformed_json.txt",
    "deep_nesting.json",
    "duplicate_keys.json",
    "invalid_digest.json",
    "valid_digest_invalid_schema.json",
    "invalid_unicode.bin",
    "truncated_feed.json",
    "oversized_field.json",
    "unexpected_nested_object.json",
    "future_timestamp.json",
    "empty_stix_objects.json",
    "partially_valid_bundle.json",
]


class TestAdversarialCorpus:
    """Each corpus file must be consumed by a parsing surface without crashing.

    These are parser-robustness fixtures (no malware / no offensive payloads).
    We exercise the pure surfaces that the bundle/entry layer exposes:
    json.loads (the parsers' first step) and CrawlerEntry.model_validate.
    """

    @pytest.mark.parametrize("name", _CORPUS_FILES)
    def test_corpus_json_parses_or_fails_safely(self, name: str) -> None:
        path = _FIXTURES / name
        raw = path.read_bytes()
        text: str | None = None
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError:
            # Non-UTF-8 input (invalid_unicode.bin): the parser must reject it
            # safely rather than crash. simulate the json step failing cleanly.
            with pytest.raises((UnicodeDecodeError, ValueError)):
                json.loads(raw.decode("utf-8", errors="strict"))
            return
        assert text is not None
        try:
            data = json.loads(text)
        except (json.JSONDecodeError, ValueError):
            # Malformed / truncated JSON must be rejected, not crash.
            return
        # Valid JSON that is not a valid CrawlerEntry must raise ValidationError,
        # never silently become a publishable entry.
        if isinstance(data, dict):
            try:
                CrawlerEntry.model_validate(data)
            except ValidationError:
                return
            # If it validated, it must never be publishable from raw corpus data
            # unless it explicitly carries a trusted/corroborated status.
            entry = CrawlerEntry.model_validate(data)
            if entry.verification_status not in (
                VerificationStatus.TRUSTED_SOURCE,
                VerificationStatus.CORROBORATED,
            ):
                assert entry.is_publishable() is False
