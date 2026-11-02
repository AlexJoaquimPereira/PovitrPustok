#!/usr/bin/env python3
"""Generate or verify a committed metadata-only manifest for an English edition.

The manifest holds hashes, counts and a schema fingerprint - never Bible text.
It lets a checkout detect a missing, stale, or tampered edition without
committing the (gitignored) SQLite assets.

This is a generic sibling of ``tools/corpus/generate_manifest.py``; the Konkani
script is unchanged and keeps its own version string.

Usage:
    python3 tools/editions/generate_manifest.py --edition all
    python3 tools/editions/generate_manifest.py --edition webc --check
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

from edition_common import (
    DEFAULT_ASSETS_ROOT,
    GENERATOR,
    book_metadata,
    load_catalog,
    schema_fingerprint,
)

EDITIONS = {
    "webc": {
        "source_url": "https://bible.helloao.org/api/eng_webc/complete.json",
        "source_sha256": "3eea854d339cd3809a9efd03621ff88c7dc0f3e82a44ace0b2b54b2b824a922b",
        "book_count": 73,
        "total_chapters": 1328,
        "total_verses": 35408,
    },
    "dra": {
        "source_url": "https://bible.helloao.org/api/eng_dra/complete.json",
        "source_sha256": "041f23de2fde2e6c75383b4fe15f53867c676db5fb6b4c93bd55138ffb93f0a6",
        "book_count": 73,
        "total_chapters": 1334,
        "total_verses": 35811,
    },
}


def build_manifest(edition: str, assets: Path) -> dict:
    spec = EDITIONS[edition]
    files = sorted(assets.glob("*.sqlite"))
    if not files:
        raise SystemExit(f"No *.sqlite assets found in {assets}")

    books = [book_metadata(path) for path in files]
    catalog_ids = {book.book_id for book in load_catalog()}
    manifest_ids = {book["id"] for book in books}

    if len(files) != spec["book_count"]:
        raise SystemExit(f"{edition}: {len(files)} files, expected {spec['book_count']}")
    if manifest_ids != catalog_ids:
        missing = sorted(catalog_ids - manifest_ids)
        extra = sorted(manifest_ids - catalog_ids)
        raise SystemExit(f"{edition}: book id mismatch; missing={missing} extra={extra}")

    total_chapters = sum(book["chapters"] for book in books)
    total_verses = sum(book["verses"] for book in books)
    if total_chapters != spec["total_chapters"]:
        raise SystemExit(
            f"{edition}: {total_chapters} chapters, expected {spec['total_chapters']}"
        )
    if total_verses != spec["total_verses"]:
        raise SystemExit(
            f"{edition}: {total_verses} verses, expected {spec['total_verses']}"
        )

    aggregate = hashlib.sha256(
        "".join(f"{book['file']}:{book['sha256']}\n" for book in books).encode()
    ).hexdigest()

    return {
        "format": 1,
        "edition": edition,
        "source_url": spec["source_url"],
        "source_sha256": spec["source_sha256"],
        "generator": GENERATOR,
        "book_count": len(books),
        "total_chapters": total_chapters,
        "total_verses": total_verses,
        "aggregate_sha256": aggregate,
        "schema_fingerprint": schema_fingerprint(files[0]),
        "books": books,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--edition", choices=sorted(EDITIONS) + ["all"], default="all")
    parser.add_argument(
        "--assets",
        type=Path,
        default=None,
        help="Assets directory for one edition (only valid with --edition <id>).",
    )
    parser.add_argument(
        "--manifest",
        type=Path,
        default=None,
        help="Manifest path (only valid with --edition <id>).",
    )
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    if args.edition == "all" and (args.assets or args.manifest):
        raise SystemExit("--assets/--manifest require a single --edition")

    editions = sorted(EDITIONS) if args.edition == "all" else [args.edition]

    for edition in editions:
        assets = args.assets or (DEFAULT_ASSETS_ROOT / edition)
        manifest_path = args.manifest or (
            Path(__file__).resolve().parent / f"{edition}-manifest.json"
        )
        manifest = build_manifest(edition, assets)
        rendered = json.dumps(manifest, indent=2, sort_keys=False) + "\n"

        if args.check:
            if not manifest_path.is_file():
                print(f"Missing manifest: {manifest_path}", file=sys.stderr)
                return 1
            if manifest_path.read_text() != rendered:
                print(
                    f"{edition}: asset manifest drift against {manifest_path}. "
                    "Regenerate only after reviewing the source change.",
                    file=sys.stderr,
                )
                return 1
            print(
                f"{edition}: manifest OK ({manifest['book_count']} books, "
                f"{manifest['total_chapters']} chapters, "
                f"{manifest['total_verses']} verses)."
            )
            continue

        manifest_path.write_text(rendered)
        print(
            f"Wrote {manifest_path} ({manifest['book_count']} books, "
            f"{manifest['total_chapters']} chapters, "
            f"{manifest['total_verses']} verses)."
        )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
