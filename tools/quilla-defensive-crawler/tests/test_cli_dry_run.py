"""CLI dry-run behavior — offline-safe empty bundle and key cleanup."""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path


def test_dry_run_builds_empty_bundle_when_sources_unavailable() -> None:
    """Dry-run must exit 0 offline and must not leave a private key behind."""
    repo = Path(__file__).resolve().parents[1]
    config = repo / "source_allowlist.json"
    assert config.is_file()

    with tempfile.TemporaryDirectory() as tmp:
        private = os.path.join(tmp, "private.pem")
        public = os.path.join(tmp, "public.pem")
        out = os.path.join(tmp, "output")
        os.makedirs(out, exist_ok=True)

        gen = subprocess.run(
            [
                sys.executable,
                "-m",
                "quilla_crawler",
                "generate-key",
                "--private",
                private,
                "--public",
                public,
            ],
            cwd=str(repo),
            capture_output=True,
            text=True,
            check=False,
        )
        assert gen.returncode == 0, gen.stderr
        assert os.path.isfile(private)

        env = os.environ.copy()
        env["QUILLA_SIGNING_KEY_PATH"] = private
        crawl = subprocess.run(
            [
                sys.executable,
                "-m",
                "quilla_crawler",
                "crawl",
                "--config",
                str(config),
                "--output",
                out,
                "--dry-run",
                "--max-pages",
                "1",
            ],
            cwd=str(repo),
            capture_output=True,
            text=True,
            check=False,
            env=env,
        )
        assert crawl.returncode == 0, crawl.stderr + crawl.stdout
        assert "signing skipped" in crawl.stdout

        bundle_path = os.path.join(out, "quilla-intel.json")
        assert os.path.isfile(bundle_path)
        data = json.loads(Path(bundle_path).read_text(encoding="utf-8"))
        assert data["schema_version"] == 1
        assert "entries" in data
        assert data["entry_count"] == len(data["entries"])
        # Dry-run must not write a signature beside the bundle.
        assert not os.path.exists(os.path.join(out, "quilla-intel.json.sig"))

        os.remove(private)
        assert not os.path.exists(private)
