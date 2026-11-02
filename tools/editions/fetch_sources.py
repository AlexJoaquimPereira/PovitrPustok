#!/usr/bin/env python3
"""Download and verify the public-domain English edition sources.

Both sources are official JSON endpoints, not scraped pages:

  WEBC  https://bible.helloao.org/api/eng_webc/complete.json
        World English Bible (Catholic), public domain (ebible.org eng-web-c).
  DRA   https://bible.helloao.org/api/eng_dra/complete.json
        Douay-Rheims American Edition 1899, public domain (ebible.org engDRA).

Downloads are pinned by SHA-256; a mismatch aborts without touching the
existing file. The JSON lands in ``tools/editions/sources/<edition>/complete.json``
which is gitignored: it is reproducible from the pinned URL + hash.

Usage:
    python3 tools/editions/fetch_sources.py            # both editions
    python3 tools/editions/fetch_sources.py --edition webc
    python3 tools/editions/fetch_sources.py --force    # re-download valid files
"""

from __future__ import annotations

import argparse
import hashlib
import os
import sys
import tempfile
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SOURCES_DIR = Path(__file__).resolve().parent / "sources"

SOURCES = {
    "webc": {
        "url": "https://bible.helloao.org/api/eng_webc/complete.json",
        "sha256": "3eea854d339cd3809a9efd03621ff88c7dc0f3e82a44ace0b2b54b2b824a922b",
        "bytes": 7_555_951,
        "licence": "Public domain (World English Bible, Catholic edition; ebible.org eng-web-c)",
    },
    "dra": {
        "url": "https://bible.helloao.org/api/eng_dra/complete.json",
        "sha256": "041f23de2fde2e6c75383b4fe15f53867c676db5fb6b4c93bd55138ffb93f0a6",
        "bytes": 6_564_355,
        "licence": "Public domain (Douay-Rheims American Edition 1899; ebible.org engDRA)",
    },
}

CHUNK = 1024 * 1024

# A descriptive UA is sent to both endpoints; helloao does not require one, but
# some public endpoints reject urllib's default User-Agent.
USER_AGENT = "PovitrPustok-edition-fetcher/1.0 (+https://github.com/povitrpustok)"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(CHUNK), b""):
            digest.update(block)
    return digest.hexdigest()


def download(url: str, destination: Path) -> None:
    """Download to a temp file next to the target, then rename."""
    destination.parent.mkdir(parents=True, exist_ok=True)
    fd, temp_name = tempfile.mkstemp(
        dir=str(destination.parent), prefix=destination.name + ".", suffix=".part"
    )
    try:
        with os.fdopen(fd, "wb") as output, urllib.request.urlopen(
            urllib.request.Request(url, headers={"User-Agent": USER_AGENT}),
            timeout=120,
        ) as response:
            for block in iter(lambda: response.read(CHUNK), b""):
                output.write(block)
        os.replace(temp_name, destination)
    except BaseException:
        Path(temp_name).unlink(missing_ok=True)
        raise


def ensure_source(edition: str, force: bool) -> Path:
    spec = SOURCES[edition]
    destination = SOURCES_DIR / edition / "complete.json"

    if destination.is_file() and not force:
        digest = sha256_file(destination)
        if digest == spec["sha256"]:
            print(f"  ok      {edition}: already present and verified ({destination})")
            return destination
        print(f"  stale   {edition}: existing file hash {digest} != pinned; re-downloading")

    print(f"  fetch   {edition}: {spec['url']}")
    try:
        download(spec["url"], destination)
    except OSError as exc:
        raise SystemExit(f"Download failed for {edition} ({spec['url']}): {exc}")

    digest = sha256_file(destination)
    size = destination.stat().st_size
    if digest != spec["sha256"]:
        destination.unlink(missing_ok=True)
        raise SystemExit(
            f"SHA-256 mismatch for {edition}:\n"
            f"  expected {spec['sha256']}\n"
            f"  actual   {digest}\n"
            f"The upstream file changed; do not import it until the new hash is reviewed."
        )
    if size != spec["bytes"]:
        destination.unlink(missing_ok=True)
        raise SystemExit(
            f"Size mismatch for {edition}: expected {spec['bytes']} bytes, got {size}"
        )

    print(f"  verified {edition}: {size:,} bytes, sha256 {digest}")
    return destination


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--edition",
        choices=sorted(SOURCES) + ["all"],
        default="all",
        help="Which edition to download (default: all).",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Re-download even when the existing file already matches the pinned hash.",
    )
    args = parser.parse_args()

    editions = sorted(SOURCES) if args.edition == "all" else [args.edition]

    print("=" * 78)
    print("PUBLIC-DOMAIN ENGLISH EDITION SOURCE FETCH")
    print("=" * 78)

    for edition in editions:
        spec = SOURCES[edition]
        print(f"\n{edition.upper()} - {spec['licence']}")
        ensure_source(edition, args.force)

    print("\nDone. Sources live under tools/editions/sources/ (gitignored).")
    print("Next: python3 tools/editions/import_edition.py --edition all")
    return 0


if __name__ == "__main__":
    sys.exit(main())
