#!/usr/bin/env python3
"""Read-only validation of the generated English edition assets.

Checks, per edition:
  * exactly 73 databases, ids matching BibleCatalog (no missing/duplicate);
  * pinned chapter/verse totals;
  * PRAGMA quick_check, user_version, required tables/indexes/triggers/FTS;
  * FTS row count equals verses count, no verse ranges, no NULL text_plain;
  * spot-checked verses print their text;
  * raw asset size plus a deflate (APK) estimate.

Usage:
    python3 tools/editions/validate_editions.py
    python3 tools/editions/validate_editions.py --edition webc
"""

from __future__ import annotations

import argparse
import sqlite3
import sys
import zlib
from pathlib import Path

from edition_common import (
    DEFAULT_ASSETS_ROOT,
    REQUIRED_INDEXES,
    REQUIRED_TABLES,
    REQUIRED_TRIGGERS,
    load_catalog,
)

EXPECTED = {
    "webc": {"books": 73, "chapters": 1328, "verses": 35408},
    "dra": {"books": 73, "chapters": 1334, "verses": 35811},
}

# (book id, chapter, verse or None for "last verse of the book", label)
SPOT_CHECKS = {
    "webc": [
        ("Utpoti", 1, 1, "Genesis 1:1"),
        ("Stotranchem_Pustok", 23, 1, "Psalm 23:1"),
        ("juanv", 3, 16, "John 3:16"),
        ("Tobit", 1, 1, "Tobit 1:1"),
        ("Makabeusachem_Poilem_Pustok", 1, 1, "1 Maccabees 1:1"),
        ("juanvak_prokasovnni", 22, None, "Revelation 22 (last verse)"),
    ],
    "dra": [
        ("Utpoti", 1, 1, "Genesis 1:1"),
        ("Stotranchem_Pustok", 22, 1, "Psalm 22:1 (Vulgate = Hebrew 23)"),
        ("juanv", 3, 16, "John 3:16"),
        ("Tobit", 1, 1, "Tobit 1:1"),
        ("Makabeusachem_Poilem_Pustok", 1, 1, "1 Maccabees 1:1"),
        ("juanvak_prokasovnni", 22, None, "Revelation 22 (last verse)"),
    ],
}


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def check_schema(conn: sqlite3.Connection, path: Path) -> None:
    names = {
        row[0]: row[1]
        for row in conn.execute("SELECT name, type FROM sqlite_master")
    }
    for table in REQUIRED_TABLES:
        if names.get(table) != "table":
            fail(f"{path.name}: missing table {table}")
    for index in REQUIRED_INDEXES:
        if names.get(index) != "index":
            fail(f"{path.name}: missing index {index}")
    for trigger in REQUIRED_TRIGGERS:
        if names.get(trigger) != "trigger":
            fail(f"{path.name}: missing trigger {trigger}")

    user_version = conn.execute("PRAGMA user_version").fetchone()[0]
    if user_version != 0:
        fail(f"{path.name}: user_version={user_version}, expected 0")

    quick = conn.execute("PRAGMA quick_check").fetchone()[0]
    if quick != "ok":
        fail(f"{path.name}: quick_check={quick}")

    foreign = conn.execute("PRAGMA foreign_key_check").fetchall()
    if foreign:
        fail(f"{path.name}: foreign_key_check returned {len(foreign)} rows")


def check_book(path: Path) -> dict:
    with sqlite3.connect(f"file:{path}?mode=ro", uri=True) as conn:
        check_schema(conn, path)

        book_id, name, english_name, testament, position = conn.execute(
            "SELECT id, name, english_name, testament, position FROM books"
        ).fetchone()
        chapters = conn.execute("SELECT COUNT(*) FROM chapters").fetchone()[0]
        verses = conn.execute("SELECT COUNT(*) FROM verses").fetchone()[0]
        fts = conn.execute("SELECT COUNT(*) FROM verses_fts").fetchone()[0]
        headings = conn.execute("SELECT COUNT(*) FROM headings").fetchone()[0]

        if verses != fts:
            fail(f"{path.name}: verses={verses} but fts={fts}")
        if book_id != path.stem:
            fail(f"{path.name}: books.id={book_id} does not match filename")

        ranged = conn.execute(
            "SELECT COUNT(*) FROM verses WHERE verse_end IS NOT NULL"
        ).fetchone()[0]
        if ranged:
            fail(f"{path.name}: {ranged} verses carry verse_end, expected NULL")

        null_plain = conn.execute(
            "SELECT COUNT(*) FROM verses WHERE text_plain IS NULL"
        ).fetchone()[0]
        if null_plain:
            fail(f"{path.name}: {null_plain} verses have NULL text_plain")

        blank = conn.execute(
            "SELECT COUNT(*) FROM verses WHERE TRIM(text) <> '' "
            "AND TRIM(COALESCE(text_plain, '')) = ''"
        ).fetchone()[0]
        if blank:
            fail(f"{path.name}: {blank} non-empty verses have empty text_plain")

        # Chapters must be positioned 1..N and unique; verses 0..M-1 per chapter.
        chapter_positions = [
            row[0]
            for row in conn.execute(
                "SELECT position FROM chapters ORDER BY position"
            )
        ]
        if chapter_positions != list(range(1, chapters + 1)):
            fail(f"{path.name}: chapter positions are not 1..{chapters}")

        bad_verse_positions = conn.execute(
            """
            SELECT COUNT(*) FROM (
                SELECT chapter_id, COUNT(*) AS n,
                       MIN(position) AS lo, MAX(position) AS hi,
                       COUNT(DISTINCT position) AS distinct_positions
                FROM verses GROUP BY chapter_id
                HAVING lo <> 0 OR hi <> n - 1 OR distinct_positions <> n
            )
            """
        ).fetchone()[0]
        if bad_verse_positions:
            fail(
                f"{path.name}: {bad_verse_positions} chapters have "
                "non-sequential verse positions"
            )

        meta = dict(conn.execute("SELECT key, value FROM meta"))

    return {
        "id": book_id,
        "name": name,
        "english_name": english_name,
        "testament": testament,
        "position": position,
        "chapters": chapters,
        "verses": verses,
        "headings": headings,
        "meta": meta,
    }


def spot_check(path: Path, chapter: int, verse: int | None) -> tuple[str, str]:
    with sqlite3.connect(f"file:{path}?mode=ro", uri=True) as conn:
        if verse is None:
            row = conn.execute(
                "SELECT verse_start, text_plain FROM verses WHERE chapter_number = ? "
                "ORDER BY position DESC LIMIT 1",
                (chapter,),
            ).fetchone()
        else:
            row = conn.execute(
                "SELECT verse_start, text_plain FROM verses "
                "WHERE chapter_number = ? AND verse_start = ? "
                "ORDER BY position LIMIT 1",
                (chapter, verse),
            ).fetchone()
    if row is None:
        return ("?", "<MISSING>")
    return (str(row[0]), row[1] or "")


def deflate_size(path: Path) -> int:
    """Approximate the APK's compressed contribution for one database."""
    digest = zlib.compressobj(9)
    total = 0
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            total += len(digest.compress(block))
    total += len(digest.flush())
    return total


def validate_edition(edition: str, assets_root: Path, verbose: bool) -> dict:
    expected = EXPECTED[edition]
    assets = assets_root / edition
    if not assets.is_dir():
        fail(f"missing asset directory {assets}")

    files = sorted(assets.glob("*.sqlite"))
    catalog = load_catalog()
    catalog_ids = {book.book_id for book in catalog}

    print(f"\n{edition.upper()} - {assets}")
    print(f"  files: {len(files)}")
    if len(files) != expected["books"]:
        fail(f"{edition}: {len(files)} files, expected {expected['books']}")

    ids = [path.stem for path in files]
    if len(set(ids)) != len(ids):
        fail(f"{edition}: duplicate book ids: {sorted(ids)}")
    missing = sorted(catalog_ids - set(ids))
    extra = sorted(set(ids) - catalog_ids)
    if missing or extra:
        fail(f"{edition}: missing={missing} extra={extra}")

    total_chapters = 0
    total_verses = 0
    total_headings = 0
    raw_bytes = 0
    compressed_bytes = 0
    books: list[dict] = []

    for path in files:
        info = check_book(path)
        books.append(info)
        total_chapters += info["chapters"]
        total_verses += info["verses"]
        total_headings += info["headings"]
        raw_bytes += path.stat().st_size
        compressed_bytes += deflate_size(path)
        if verbose:
            print(
                f"    {info['id']:30s} chapters={info['chapters']:3d} "
                f"verses={info['verses']:5d} headings={info['headings']:2d}"
            )

    if total_chapters != expected["chapters"]:
        fail(f"{edition}: {total_chapters} chapters, expected {expected['chapters']}")
    if total_verses != expected["verses"]:
        fail(f"{edition}: {total_verses} verses, expected {expected['verses']}")

    print(f"  distinct ids: {len(set(ids))}")
    print(f"  chapters: {total_chapters}")
    print(f"  verses:   {total_verses}")
    print(f"  headings: {total_headings}")
    print(f"  raw size: {raw_bytes:,} bytes ({raw_bytes / 1024 / 1024:.1f} MiB)")
    print(
        f"  deflate estimate: {compressed_bytes:,} bytes "
        f"({compressed_bytes / 1024 / 1024:.1f} MiB), "
        f"{compressed_bytes / raw_bytes:.0%} of raw"
    )

    print("  spot checks:")
    by_id = {info["id"]: info for info in books}
    for book_id, chapter, verse, label in SPOT_CHECKS[edition]:
        if book_id not in by_id:
            fail(f"{edition}: spot-check book {book_id} missing")
        number, text = spot_check(assets / f"{book_id}.sqlite", chapter, verse)
        shown = text if len(text) <= 200 else text[:200] + "..."
        print(f"    {label}: {number} | {shown}")

    return {
        "edition": edition,
        "books": len(files),
        "chapters": total_chapters,
        "verses": total_verses,
        "headings": total_headings,
        "raw_bytes": raw_bytes,
        "compressed_bytes": compressed_bytes,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--edition", choices=sorted(EXPECTED) + ["all"], default="all")
    parser.add_argument(
        "--assets",
        type=Path,
        default=DEFAULT_ASSETS_ROOT,
        help="Assets root (default: app/src/main/assets/bible).",
    )
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()

    print("=" * 78)
    print("ENGLISH EDITION ASSET VALIDATION")
    print("=" * 78)

    editions = sorted(EXPECTED) if args.edition == "all" else [args.edition]
    for edition in editions:
        validate_edition(edition, args.assets, args.verbose)

    print("\nAll checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
