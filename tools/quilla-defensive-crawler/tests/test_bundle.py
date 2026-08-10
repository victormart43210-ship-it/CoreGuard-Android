"""Tests for bundle.py and signer.py — determinism, signing, and verification."""

from __future__ import annotations

import json
import os
import tempfile

import pytest

from quilla_crawler.bundle import _to_deterministic_bytes, build_bundle
from quilla_crawler.models import CrawlerEntry, VerificationStatus
from quilla_crawler.signer import generate_keypair, load_public_key_pem, sign_bundle, verify_bundle


def _make_entry(entry_id: str, title: str = "Test Entry") -> CrawlerEntry:
    return CrawlerEntry(
        id=entry_id,
        title=title,
        category="crawler-vulnerability",
        tags=["test", "android"],
        summary="Test advisory summary.",
        body="Test body text.",
        defense="Apply patches promptly.",
        references=["https://www.cisa.gov/kev"],
        verification_status=VerificationStatus.TRUSTED_SOURCE,
        confidence=0.90,
    )


class TestBundleDeterminism:
    def test_same_entries_produce_same_bytes(self) -> None:
        entries = [_make_entry("entry-a"), _make_entry("entry-b")]
        with tempfile.TemporaryDirectory() as tmp:
            b1 = build_bundle(entries, output_dir=tmp)
            b2 = build_bundle(entries, output_dir=tmp)
        # bundle_id and generated_at are intentionally fresh on each call.
        # The entries content and sha256 must be identical.
        import json as _json

        d1 = _json.loads(b1)
        d2 = _json.loads(b2)
        assert d1["entries"] == d2["entries"]
        assert d1["entries_sha256"] == d2["entries_sha256"]
        assert d1["entry_count"] == d2["entry_count"]

    def test_bundle_json_keys_are_sorted(self) -> None:
        entries = [_make_entry("entry-a")]
        with tempfile.TemporaryDirectory() as tmp:
            bundle_bytes = build_bundle(entries, output_dir=tmp)
        data = json.loads(bundle_bytes.decode("utf-8"))
        keys = list(data.keys())
        assert keys == sorted(keys)

    def test_entries_ordered_by_id(self) -> None:
        entries = [_make_entry("zzz"), _make_entry("aaa"), _make_entry("mmm")]
        with tempfile.TemporaryDirectory() as tmp:
            bundle_bytes = build_bundle(entries, output_dir=tmp)
        data = json.loads(bundle_bytes.decode("utf-8"))
        ids = [e["id"] for e in data["entries"]]
        assert ids == sorted(ids)

    def test_bundle_is_utf8(self) -> None:
        entries = [_make_entry("entry-a", title="Advisory with émojis 🛡")]
        with tempfile.TemporaryDirectory() as tmp:
            bundle_bytes = build_bundle(entries, output_dir=tmp)
        # Should not raise.
        bundle_bytes.decode("utf-8")

    def test_entry_count_matches(self) -> None:
        entries = [_make_entry(f"entry-{i}") for i in range(5)]
        with tempfile.TemporaryDirectory() as tmp:
            bundle_bytes = build_bundle(entries, output_dir=tmp)
        data = json.loads(bundle_bytes.decode("utf-8"))
        assert data["entry_count"] == 5
        assert len(data["entries"]) == 5

    def test_entries_sha256_correct(self) -> None:
        import hashlib

        entries = [_make_entry("entry-a")]
        with tempfile.TemporaryDirectory() as tmp:
            bundle_bytes = build_bundle(entries, output_dir=tmp)
        data = json.loads(bundle_bytes.decode("utf-8"))
        # Recompute entries hash.
        entries_bytes = _to_deterministic_bytes(data["entries"])
        expected = hashlib.sha256(entries_bytes).hexdigest()
        assert data["entries_sha256"] == expected

    def test_rejected_entries_excluded(self) -> None:
        entries = [
            _make_entry("entry-ok"),
            CrawlerEntry(
                id="entry-rejected",
                title="Rejected",
                verification_status=VerificationStatus.REJECTED,
                confidence=0.0,
            ),
        ]
        with tempfile.TemporaryDirectory() as tmp:
            bundle_bytes = build_bundle(entries, output_dir=tmp)
        data = json.loads(bundle_bytes.decode("utf-8"))
        assert data["entry_count"] == 1
        assert data["entries"][0]["id"] == "entry-ok"

    def test_manifest_file_written(self) -> None:
        entries = [_make_entry("entry-a")]
        with tempfile.TemporaryDirectory() as tmp:
            build_bundle(entries, output_dir=tmp)
            assert os.path.isfile(os.path.join(tmp, "quilla-intel-manifest.json"))

    def test_bundle_file_written(self) -> None:
        entries = [_make_entry("entry-a")]
        with tempfile.TemporaryDirectory() as tmp:
            build_bundle(entries, output_dir=tmp)
            assert os.path.isfile(os.path.join(tmp, "quilla-intel.json"))


class TestSigningAndVerification:
    def test_valid_signature_verifies(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            priv = os.path.join(tmp, "private.pem")
            pub = os.path.join(tmp, "public.pem")
            generate_keypair(priv, pub)

            entries = [_make_entry("entry-a")]
            bundle_bytes = build_bundle(entries, output_dir=tmp)
            sign_bundle(bundle_bytes, priv, output_dir=tmp)

            sig_path = os.path.join(tmp, "quilla-intel.sig")
            with open(sig_path) as f:
                sig_b64 = f.read()

            pub_pem = load_public_key_pem(pub)
            assert verify_bundle(bundle_bytes, sig_b64, pub_pem) is True

    def test_modified_bundle_fails_verification(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            priv = os.path.join(tmp, "private.pem")
            pub = os.path.join(tmp, "public.pem")
            generate_keypair(priv, pub)

            entries = [_make_entry("entry-a")]
            bundle_bytes = build_bundle(entries, output_dir=tmp)
            sign_bundle(bundle_bytes, priv, output_dir=tmp)

            sig_path = os.path.join(tmp, "quilla-intel.sig")
            with open(sig_path) as f:
                sig_b64 = f.read()

            # Tamper with the bundle.
            tampered = bundle_bytes.replace(b'"entry-a"', b'"entry-EVIL"')
            pub_pem = load_public_key_pem(pub)
            assert verify_bundle(tampered, sig_b64, pub_pem) is False

    def test_wrong_signature_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            priv = os.path.join(tmp, "private.pem")
            pub = os.path.join(tmp, "public.pem")
            generate_keypair(priv, pub)

            entries = [_make_entry("entry-a")]
            bundle_bytes = build_bundle(entries, output_dir=tmp)

            pub_pem = load_public_key_pem(pub)
            assert verify_bundle(bundle_bytes, "aW52YWxpZHNpZ25hdHVyZQ==", pub_pem) is False

    def test_absent_private_key_prevents_signing(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            entries = [_make_entry("entry-a")]
            bundle_bytes = build_bundle(entries, output_dir=tmp)
            with pytest.raises(RuntimeError, match="not found|not set"):
                sign_bundle(bundle_bytes, os.path.join(tmp, "nonexistent.pem"), output_dir=tmp)

    def test_private_key_permissions_enforced(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            priv = os.path.join(tmp, "private.pem")
            pub = os.path.join(tmp, "public.pem")
            generate_keypair(priv, pub)
            # Make private key world-readable.
            os.chmod(priv, 0o644)
            entries = [_make_entry("entry-a")]
            bundle_bytes = build_bundle(entries, output_dir=tmp)
            with pytest.raises(RuntimeError, match="permissive|permission"):
                sign_bundle(bundle_bytes, priv, output_dir=tmp)
