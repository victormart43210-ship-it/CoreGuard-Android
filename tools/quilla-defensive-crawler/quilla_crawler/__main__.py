"""Command-line entry point for the Quilla Defensive Crawler.

Usage:
    python -m quilla_crawler crawl --config source_allowlist.json --output output/
    python -m quilla_crawler generate-key --private output/private/quilla-ed25519-private.pem \\
                                          --public  output/public/quilla-ed25519-public.pem

Safety invariant: A CLI argument can reduce configured limits but never increase them.
                  A CLI argument cannot add arbitrary URLs or hosts.
"""

from __future__ import annotations

import argparse
import json
import os
import sys

from quilla_crawler import __version__
from quilla_crawler.audit import AuditLogger
from quilla_crawler.bundle import build_bundle
from quilla_crawler.config import AllowList
from quilla_crawler.deduplicator import Deduplicator
from quilla_crawler.fetcher import Fetcher
from quilla_crawler.signer import generate_keypair, sign_bundle


def _cmd_generate_key(args: argparse.Namespace) -> int:
    """Generate an Ed25519 key-pair and write PEM files."""
    priv_path = args.private
    pub_path = args.public

    # Refuse to overwrite an existing private key.
    if os.path.exists(priv_path):
        print(
            f"ERROR: private key already exists at {priv_path}. "
            "Delete it manually if you intend to rotate.",
            file=sys.stderr,
        )
        return 1

    os.makedirs(os.path.dirname(priv_path) or ".", exist_ok=True)
    os.makedirs(os.path.dirname(pub_path) or ".", exist_ok=True)
    generate_keypair(priv_path, pub_path)

    print("=" * 72)
    print("WARNING: PRIVATE KEY SECURITY")
    print("=" * 72)
    print(f"  Private key written to: {priv_path}")
    print("  *** NEVER commit this file to version control. ***")
    print("  *** Store it in a secure location with restricted permissions. ***")
    print("  Set QUILLA_SIGNING_KEY_PATH to this path before running 'crawl'.")
    print("=" * 72)
    print(f"  Public key written to:  {pub_path}")
    print("  Embed the public key bytes in the CoreGuard Android app.")
    print("=" * 72)
    return 0


def _cmd_crawl(args: argparse.Namespace) -> int:
    """Run the defensive crawler and optionally sign the output bundle."""
    # Load and validate the allowlist config.
    try:
        with open(args.config, encoding="utf-8") as fh:
            raw = json.load(fh)
        allowlist = AllowList.model_validate(raw)
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: failed to load config {args.config!r}: {exc}", file=sys.stderr)
        return 1

    output_dir = args.output
    os.makedirs(output_dir, exist_ok=True)
    audit_path = os.path.join(output_dir, "audit.jsonl")
    audit = AuditLogger(audit_path)

    verbose = args.verbose
    dry_run = args.dry_run

    # Resolve which source IDs to crawl.
    requested_ids: set[str] | None = None
    if args.source:
        requested_ids = {s.strip() for s in args.source.split(",")}
        unknown = requested_ids - {s.id for s in allowlist.sources}
        if unknown:
            print(f"ERROR: unknown source IDs: {unknown}", file=sys.stderr)
            return 1

    # Page-limit CLI override: may only reduce, never increase.
    cli_max_pages: int | None = args.max_pages
    if cli_max_pages is not None and cli_max_pages <= 0:
        print("ERROR: --max-pages must be a positive integer.", file=sys.stderr)
        return 1

    fetcher = Fetcher(audit=audit, verbose=verbose)
    dedup = Deduplicator()
    all_failed = True

    for source_cfg in allowlist.sources:
        if requested_ids is not None and source_cfg.id not in requested_ids:
            continue

        # Enforce CLI page-limit cap.
        effective_max_pages = source_cfg.max_pages
        if cli_max_pages is not None:
            effective_max_pages = min(effective_max_pages, cli_max_pages)

        if verbose:
            print(f"[crawl] source={source_cfg.id!r} pages_limit={effective_max_pages}")

        entries = fetcher.fetch_source(source_cfg, max_pages_override=effective_max_pages)
        if entries:
            all_failed = False
        for entry in entries:
            dedup.add(entry)

    accepted = dedup.accepted_entries()

    if verbose:
        print(f"[crawl] accepted entries after deduplication: {len(accepted)}")

    if all_failed and not accepted:
        print("ERROR: all sources failed and no entries were accepted.", file=sys.stderr)
        return 1

    bundle_bytes = build_bundle(accepted, output_dir=output_dir)

    if dry_run:
        print(f"[dry-run] bundle built ({len(bundle_bytes):,} bytes), signing skipped.")
        return 0

    # Sign — fails closed if key is missing.
    key_path = os.environ.get("QUILLA_SIGNING_KEY_PATH", "")
    if not key_path:
        print(
            "ERROR: QUILLA_SIGNING_KEY_PATH is not set. "
            "Cannot sign the bundle. Aborting publish.",
            file=sys.stderr,
        )
        return 1

    try:
        sign_bundle(bundle_bytes, key_path, output_dir=output_dir)
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: signing failed: {exc}", file=sys.stderr)
        return 1

    print(
        f"[crawl] bundle signed and written to {output_dir!r} "
        f"({len(accepted)} entries)."
    )
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        prog="quilla_crawler",
        description="CoreGuard Quilla Defensive Crawler v" + __version__,
    )
    sub = parser.add_subparsers(dest="command", required=True)

    # --- crawl sub-command ---
    crawl_p = sub.add_parser("crawl", help="Run defensive crawler and produce bundle.")
    crawl_p.add_argument(
        "--config",
        default="source_allowlist.json",
        help="Path to source_allowlist.json (default: source_allowlist.json)",
    )
    crawl_p.add_argument(
        "--output",
        default="output/",
        help="Output directory (default: output/)",
    )
    crawl_p.add_argument(
        "--source",
        default="",
        help="Comma-separated source IDs to crawl (default: all).",
    )
    crawl_p.add_argument(
        "--dry-run",
        action="store_true",
        help="Validate and crawl but do not sign or publish.",
    )
    crawl_p.add_argument(
        "--max-pages",
        type=int,
        default=None,
        help="Override max pages per source (may only reduce configured limit).",
    )
    crawl_p.add_argument("--verbose", "-v", action="store_true")

    # --- generate-key sub-command ---
    key_p = sub.add_parser("generate-key", help="Generate Ed25519 signing key-pair.")
    key_p.add_argument(
        "--private",
        default="output/private/quilla-ed25519-private.pem",
        help="Path for the private key PEM file.",
    )
    key_p.add_argument(
        "--public",
        default="output/public/quilla-ed25519-public.pem",
        help="Path for the public key PEM file.",
    )

    args = parser.parse_args()

    if args.command == "crawl":
        return _cmd_crawl(args)
    elif args.command == "generate-key":
        return _cmd_generate_key(args)
    return 0


if __name__ == "__main__":
    sys.exit(main())
