"""Ed25519 bundle signing and key-generation utilities.

Safety rules:
- Private key loaded from QUILLA_SIGNING_KEY_PATH env variable only.
- Never printed, logged, or written to audit records.
- Filesystem permissions of private key file are checked (mode 0o600 or 0o400).
- Publishing fails closed when the key is missing or unreadable.
"""

from __future__ import annotations

import base64
import os
import stat

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import (
    Ed25519PrivateKey,
    Ed25519PublicKey,
)


def generate_keypair(private_path: str, public_path: str) -> None:
    """Generate an Ed25519 key-pair and write PEM files."""
    private_key = Ed25519PrivateKey.generate()
    public_key = private_key.public_key()

    priv_pem = private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )
    pub_pem = public_key.public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )

    # Write private key with restrictive permissions.
    _write_restricted(private_path, priv_pem, mode=0o600)
    # Public key may be world-readable.
    with open(public_path, "wb") as fh:
        fh.write(pub_pem)


def sign_bundle(
    bundle_bytes: bytes,
    key_path: str,
    output_dir: str = "output/",
) -> None:
    """Sign bundle_bytes and write the Base64 signature to quilla-intel.sig."""
    private_key = _load_private_key(key_path)
    sig_bytes = private_key.sign(bundle_bytes)
    sig_b64 = base64.b64encode(sig_bytes).decode("ascii")

    sig_path = os.path.join(output_dir, "quilla-intel.sig")
    with open(sig_path, "w", encoding="ascii") as fh:
        fh.write(sig_b64)


def verify_bundle(bundle_bytes: bytes, sig_b64: str, public_key_pem: bytes) -> bool:
    """Return True if the signature is valid for the given bundle bytes."""
    try:
        public_key = serialization.load_pem_public_key(public_key_pem)
        if not isinstance(public_key, Ed25519PublicKey):
            return False
        sig_bytes = base64.b64decode(sig_b64)
        public_key.verify(sig_bytes, bundle_bytes)
        return True
    except Exception:  # noqa: BLE001
        return False


def load_public_key_pem(public_path: str) -> bytes:
    """Load a public key PEM file."""
    with open(public_path, "rb") as fh:
        return fh.read()


# ── Private helpers ───────────────────────────────────────────────────────────


def _load_private_key(key_path: str) -> Ed25519PrivateKey:
    """Load the private key from disk with safety checks."""
    if not key_path:
        raise RuntimeError("QUILLA_SIGNING_KEY_PATH is not set. Refusing to sign bundle.")
    if not os.path.isfile(key_path):
        raise RuntimeError(f"Private key file not found: {key_path!r}")

    # Check filesystem permissions — warn if group/world readable.
    file_stat = os.stat(key_path)
    mode = stat.S_IMODE(file_stat.st_mode)
    if mode & (stat.S_IRGRP | stat.S_IROTH):
        raise RuntimeError(
            f"Private key {key_path!r} has overly permissive mode "
            f"0o{mode:o}. Set permissions to 0o600 before signing."
        )

    with open(key_path, "rb") as fh:
        pem_data = fh.read()

    private_key = serialization.load_pem_private_key(pem_data, password=None)
    if not isinstance(private_key, Ed25519PrivateKey):
        raise RuntimeError(f"Key at {key_path!r} is not an Ed25519 private key.")
    return private_key


def _write_restricted(path: str, data: bytes, mode: int) -> None:
    """Write bytes to a file and immediately set restrictive permissions."""
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, mode)
    with os.fdopen(fd, "wb") as fh:
        fh.write(data)
    os.chmod(path, mode)
