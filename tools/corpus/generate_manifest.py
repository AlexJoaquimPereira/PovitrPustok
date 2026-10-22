#!/usr/bin/env python3
"""Generate or verify the committed corpus manifest.

The manifest contains only metadata (hashes, counts, schema fingerprint); it
never contains Bible text. It lets a checkout detect a missing, stale, or
tampered corpus without committing the copyrighted SQLite assets.

Usage:
    python3 tools/corpus/generate_manifest.py            # write manifest
    python3 tools/corpus/generate_manifest.py --check    # verify, non-zero on drift
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sqlite3
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ASSETS = REPO_ROOT / "app/src/main/assets/bible"
DEFAULT_MANIFEST = Path(__file__).resolve().parent / "corpus-manifest.json"
CORPUS_VERSION = "konkani-catholic-2026.10.22"
GENERATOR = "tools/corpus/import_corpus.py"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def schema_fingerprint(database: Path) -> str:
    parts: list[str] = []
    with sqlite3.connect(f"file:{database}?mode=ro", uri=True) as conn:
        tables = [
            row[0]
            for row in conn.execute(
                "SELECT name FROM sqlite_master "
                "WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
            )
        ]
        for table in tables:
            columns = conn.execute(f'PRAGMA table_info("{table}")').fetchall()
            for cid, name, col_type, notnull, default, pk in columns:
                parts.append(f"{table}:{cid}:{name}:{col_type}:{notnull}:{default}:{pk}")
    return hashlib.sha256("\n".join(parts).encode()).hexdigest()


def book_metadata(path: Path) -> dict:
    with sqlite3.connect(f"file:{path}?mode=ro", uri=True) as conn:
        book_id = conn.execute("SELECT id FROM books LIMIT 1").fetchone()[0]
        chapters = conn.execute("SELECT COUNT(*) FROM chapters").fetchone()[0]
        verses = conn.execute("SELECT COUNT(*) FROM verses").fetchone()[0]
        user_version = conn.execute("PRAGMA user_version").fetchone()[0]
    return {
        "file": path.name,
        "id": book_id,
        "sha256": sha256_file(path),
        "bytes": path.stat().st_size,
        "chapters": chapters,
        "verses": verses,
        "user_version": user_version,
    }


def build_manifest(assets: Path) -> dict:
    files = sorted(assets.glob("*.sqlite"))
    if not files:
        raise SystemExit(f"No *.sqlite assets found in {assets}")

    books = [book_metadata(path) for path in files]
    aggregate = hashlib.sha256(
        "".join(f"{book['file']}:{book['sha256']}\n" for book in books).encode()
    ).hexdigest()

    return {
        "format": 1,
        "corpus_version": CORPUS_VERSION,
        "generator": GENERATOR,
        "book_count": len(books),
        "total_chapters": sum(book["chapters"] for book in books),
        "total_verses": sum(book["verses"] for book in books),
        "aggregate_sha256": aggregate,
        "schema_fingerprint": schema_fingerprint(files[0]),
        "books": books,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--assets", type=Path, default=DEFAULT_ASSETS)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument(
        "--check",
        action="store_true",
        help="Compare against the existing manifest instead of writing it.",
    )
    args = parser.parse_args()

    manifest = build_manifest(args.assets)
    rendered = json.dumps(manifest, indent=2, sort_keys=False) + "\n"

    if args.check:
        if not args.manifest.exists():
            print(f"Missing manifest: {args.manifest}", file=sys.stderr)
            return 1
        if args.manifest.read_text() != rendered:
            print(
                "Corpus manifest drift: assets do not match "
                f"{args.manifest}. Regenerate only after verifying the source change.",
                file=sys.stderr,
            )
            return 1
        print(f"Corpus manifest OK ({manifest['book_count']} books).")
        return 0

    args.manifest.write_text(rendered)
    print(
        f"Wrote {args.manifest} "
        f"({manifest['book_count']} books, {manifest['total_verses']} verses)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
