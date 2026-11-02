#!/usr/bin/env python3
"""Shared plumbing for the public-domain English edition importers.

Everything here mirrors the Konkani corpus contract in
``tools/corpus/import_corpus.py``: same schema, same ``text_plain``
normalization, same per-book database layout. Only the source format differs
(JSON instead of XML), so the parsing/rendering code lives here and
``import_edition.py`` stays a thin driver.

The canonical book list is *read* from ``BibleCatalog.kt`` instead of being
duplicated here, so ids, English names, testaments and positions cannot drift
from the app. Nothing in this module writes to the repository.
"""

from __future__ import annotations

import hashlib
import html
import json
import re
import sqlite3
from dataclasses import dataclass
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
CATALOG_KT = (
    REPO_ROOT
    / "app/src/main/java/com/biblia/povitrpustok/domain/model/BibleCatalog.kt"
)
DEFAULT_ASSETS_ROOT = REPO_ROOT / "app/src/main/assets/bible"

GENERATOR = "tools/editions/import_edition.py"
LANGUAGE = "English"

# ---------------------------------------------------------------------------
# Schema: copied verbatim from tools/corpus/import_corpus.py.
# Do not edit without changing the Android data layer.
# ---------------------------------------------------------------------------

SCHEMA = """
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS meta (
    key TEXT PRIMARY KEY,
    value TEXT
);

CREATE TABLE IF NOT EXISTS books (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    english_name TEXT,
    testament TEXT,
    position INTEGER,
    filename TEXT
);

CREATE TABLE IF NOT EXISTS chapters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    book_id TEXT NOT NULL,
    number INTEGER NOT NULL,
    position INTEGER NOT NULL,
    UNIQUE(book_id, position),
    FOREIGN KEY(book_id) REFERENCES books(id)
);

CREATE TABLE IF NOT EXISTS introduction_blocks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    book_id TEXT NOT NULL,
    position INTEGER NOT NULL,
    type TEXT NOT NULL,
    html TEXT NOT NULL,
    FOREIGN KEY(book_id) REFERENCES books(id)
);

CREATE TABLE IF NOT EXISTS headings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    chapter_id INTEGER NOT NULL,
    position INTEGER NOT NULL,
    before_verse INTEGER NOT NULL,
    html TEXT NOT NULL,
    FOREIGN KEY(chapter_id) REFERENCES chapters(id)
);

CREATE TABLE IF NOT EXISTS verses (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    chapter_id INTEGER NOT NULL,
    book_id TEXT NOT NULL,
    chapter_number INTEGER NOT NULL,
    verse_start INTEGER NOT NULL,
    verse_end INTEGER,
    position INTEGER NOT NULL,
    text TEXT NOT NULL,
    text_plain TEXT,
    FOREIGN KEY(chapter_id) REFERENCES chapters(id),
    FOREIGN KEY(book_id) REFERENCES books(id)
);

CREATE INDEX IF NOT EXISTS idx_chapters_book_position
    ON chapters(book_id, position);

CREATE INDEX IF NOT EXISTS idx_headings_chapter_position
    ON headings(chapter_id, position);

CREATE INDEX IF NOT EXISTS idx_headings_chapter_before_verse
    ON headings(chapter_id, before_verse);

CREATE INDEX IF NOT EXISTS idx_verses_chapter_position
    ON verses(chapter_id, position);

CREATE INDEX IF NOT EXISTS idx_verses_chapter_verse
    ON verses(chapter_id, verse_start, verse_end);

CREATE INDEX IF NOT EXISTS idx_verses_book_chapter_verse
    ON verses(book_id, chapter_number, verse_start);

CREATE VIRTUAL TABLE IF NOT EXISTS verses_fts USING fts5(
    text_plain,
    content='verses',
    content_rowid='id'
);

CREATE TRIGGER IF NOT EXISTS verses_ai AFTER INSERT ON verses BEGIN
    INSERT INTO verses_fts(rowid, text_plain)
    VALUES (new.id, new.text_plain);
END;

CREATE TRIGGER IF NOT EXISTS verses_ad AFTER DELETE ON verses BEGIN
    INSERT INTO verses_fts(verses_fts, rowid, text_plain)
    VALUES ('delete', old.id, old.text_plain);
END;

CREATE TRIGGER IF NOT EXISTS verses_au AFTER UPDATE ON verses BEGIN
    INSERT INTO verses_fts(verses_fts, rowid, text_plain)
    VALUES ('delete', old.id, old.text_plain);
    INSERT INTO verses_fts(rowid, text_plain)
    VALUES (new.id, new.text_plain);
END;
"""

REQUIRED_TABLES = (
    "meta",
    "books",
    "chapters",
    "introduction_blocks",
    "headings",
    "verses",
    "verses_fts",
)
REQUIRED_INDEXES = (
    "idx_verses_chapter_position",
    "idx_verses_chapter_verse",
    "idx_verses_book_chapter_verse",
    "idx_chapters_book_position",
    "idx_headings_chapter_position",
    "idx_headings_chapter_before_verse",
)
REQUIRED_TRIGGERS = ("verses_ai", "verses_ad", "verses_au")

# ---------------------------------------------------------------------------
# Catalog (read from Kotlin, never duplicated)
# ---------------------------------------------------------------------------

CATALOG_RE = re.compile(
    r"BibleBook\(\s*"
    r'id\s*=\s*"([^"]+)",\s*'
    r'displayName\s*=\s*"[^"]*",\s*'
    r'englishName\s*=\s*"([^"]+)",\s*'
    r"testament\s*=\s*Testament\.(OLD|NEW)_TESTAMENT,\s*"
    r"position\s*=\s*(\d+)"
)


@dataclass(frozen=True)
class CatalogBook:
    book_id: str
    english_name: str
    testament: str  # "OT" | "NT"
    position: int


def load_catalog(path: Path = CATALOG_KT) -> list[CatalogBook]:
    """Parse ``BibleCatalog.books`` out of the Kotlin source.

    Fails loudly if the catalog is not exactly 73 unique books, so a Kotlin
    refactor cannot silently produce a short edition.
    """
    if not path.is_file():
        raise SystemExit(f"BibleCatalog.kt not found at {path}")

    source = path.read_text(encoding="utf-8")
    books = [
        CatalogBook(
            book_id=match.group(1),
            english_name=match.group(2),
            testament="OT" if match.group(3) == "OLD" else "NT",
            position=int(match.group(4)),
        )
        for match in CATALOG_RE.finditer(source)
    ]

    if len(books) != 73:
        raise SystemExit(
            f"Parsed {len(books)} books from {path.name}, expected 73. "
            "Update the catalog regex if the BibleBook constructor changed."
        )
    ids = [book.book_id for book in books]
    if len(set(ids)) != 73:
        raise SystemExit(f"Duplicate book ids in {path.name}: {ids}")
    positions = [book.position for book in books]
    if len(set(positions)) != 73:
        raise SystemExit(f"Duplicate positions in {path.name}: {positions}")

    return books


# ---------------------------------------------------------------------------
# Source book-name mapping
# ---------------------------------------------------------------------------

# Source names that do not normalize to a catalog englishName. Keys and values
# are English names (normalized on lookup), matching the catalog.
SOURCE_NAME_ALIASES = {
    "esther greek": "Esther",
    "daniel greek": "Daniel",
    "wisdom of solomon": "Wisdom",
    "song of solomon": "Song of Songs",
    "ecclesiastes": "Qoheleth",
    "revelation": "Revelation",
    "revelation of john": "Revelation",
    "sirach": "Sirach",
    "ecclesiasticus": "Sirach",
    "canticle of canticles": "Song of Songs",
    "apocalypse": "Revelation",
}


def normalize_book_name(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", name.strip().lower()).strip()


def build_source_map(catalog: list[CatalogBook]) -> dict[str, CatalogBook]:
    """Normalized source book name -> catalog book."""
    mapping = {normalize_book_name(book.english_name): book for book in catalog}

    for alias, english_name in SOURCE_NAME_ALIASES.items():
        target = mapping.get(normalize_book_name(english_name))
        if target is None:
            raise SystemExit(
                f"Alias {alias!r} points at unknown English book name {english_name!r}"
            )
        existing = mapping.get(alias)
        if existing is not None and existing.book_id != target.book_id:
            raise SystemExit(
                f"Alias {alias!r} collides with catalog book {existing.book_id!r}"
            )
        mapping[alias] = target

    return mapping


def map_source_books(source_books: list[dict], mapping: dict[str, CatalogBook]) -> list[dict]:
    """Attach a catalog book to each source book; fail on unknown/duplicate."""
    mapped: list[dict] = []
    seen: dict[str, str] = {}
    unmapped: list[str] = []

    for source_book in source_books:
        name = source_book["name"]
        catalog_book = mapping.get(normalize_book_name(name))
        if catalog_book is None:
            unmapped.append(name)
            continue
        if catalog_book.book_id in seen:
            raise SystemExit(
                f"Duplicate source book for {catalog_book.book_id!r}: "
                f"{seen[catalog_book.book_id]!r} and {name!r}"
            )
        seen[catalog_book.book_id] = name
        mapped.append({"source": source_book, "catalog": catalog_book})

    if unmapped:
        raise SystemExit(f"Unmapped source book names: {sorted(unmapped)}")

    return mapped


# ---------------------------------------------------------------------------
# HTML helpers (identical normalization to the Konkani importer)
# ---------------------------------------------------------------------------

HTML_TAG_RE = re.compile(r"<[^>]*>", re.S)
HTML_COMMENT_RE = re.compile(r"<!--.*?-->", re.S)
WHITESPACE_RE = re.compile(r"\s+")


def normalize_html_for_plain_text(source: str) -> str:
    """Generate search text without modifying the canonical HTML column."""
    if not source:
        return ""

    text = HTML_COMMENT_RE.sub(" ", source)

    text = re.sub(
        r"<\s*(br|/p|p|/h[1-6]|h[1-6])\b[^>]*>",
        " ",
        text,
        flags=re.I,
    )
    text = HTML_TAG_RE.sub(" ", text)
    text = html.unescape(text)

    text = WHITESPACE_RE.sub(" ", text).strip()
    return text


def escape_text(text: str) -> str:
    """Escape source text for the HTML column (matches the corpus contract)."""
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def ensure_fts5() -> None:
    try:
        conn = sqlite3.connect(":memory:")
        conn.execute("CREATE VIRTUAL TABLE fts_probe USING fts5(a)")
        conn.close()
    except sqlite3.OperationalError as exc:  # pragma: no cover - environment gate
        raise SystemExit(
            "This Python/SQLite build has no FTS5 support. "
            "The corpus schema requires FTS5; install/enable it and retry. "
            f"Underlying error: {exc}"
        )


# ---------------------------------------------------------------------------
# Parsed edition model
# ---------------------------------------------------------------------------


@dataclass
class Verse:
    number: int
    text_html: str


@dataclass
class Heading:
    position: int
    before_verse: int
    text_html: str


@dataclass
class Chapter:
    number: int
    verses: list[Verse]
    headings: list[Heading]


@dataclass
class BookData:
    catalog: CatalogBook
    source_ref: str  # e.g. "eng_webc:GEN" / "douayrheims:1"
    chapters: list[Chapter]


# ---------------------------------------------------------------------------
# SQLite writer
# ---------------------------------------------------------------------------


def write_book_database(
    path: Path,
    edition: str,
    book: BookData,
) -> dict[str, int]:
    """Write one book database and return its row counts."""
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        path.unlink()

    conn = sqlite3.connect(path)
    try:
        conn.executescript(SCHEMA)
        cur = conn.cursor()

        book_id = book.catalog.book_id
        english_name = book.catalog.english_name

        cur.executemany(
            "INSERT INTO meta(key, value) VALUES (?, ?)",
            [
                ("source_xml", book.source_ref),
                ("language", LANGUAGE),
                ("testament", book.catalog.testament),
                ("english_name", english_name),
                ("display_name", english_name),
                ("generator", GENERATOR),
                ("edition", edition),
            ],
        )

        cur.execute(
            """
            INSERT INTO books(id, name, english_name, testament, position, filename)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                book_id,
                english_name,
                english_name,
                book.catalog.testament,
                book.catalog.position,
                f"bible/{edition}/{book_id}.sqlite",
            ),
        )

        chapter_count = 0
        heading_count = 0
        verse_count = 0

        for chapter_position, chapter in enumerate(book.chapters, start=1):
            cur.execute(
                """
                INSERT INTO chapters(book_id, number, position)
                VALUES (?, ?, ?)
                """,
                (book_id, chapter.number, chapter_position),
            )
            chapter_id = cur.lastrowid
            chapter_count += 1

            for heading in chapter.headings:
                cur.execute(
                    """
                    INSERT INTO headings(chapter_id, position, before_verse, html)
                    VALUES (?, ?, ?, ?)
                    """,
                    (
                        chapter_id,
                        heading.position,
                        heading.before_verse,
                        heading.text_html,
                    ),
                )
                heading_count += 1

            for verse_position, verse in enumerate(chapter.verses):
                cur.execute(
                    """
                    INSERT INTO verses(
                        chapter_id,
                        book_id,
                        chapter_number,
                        verse_start,
                        verse_end,
                        position,
                        text,
                        text_plain
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        chapter_id,
                        book_id,
                        chapter.number,
                        verse.number,
                        None,
                        verse_position,
                        verse.text_html,
                        normalize_html_for_plain_text(verse.text_html),
                    ),
                )
                verse_count += 1

        conn.commit()

        verse_total = cur.execute("SELECT COUNT(*) FROM verses").fetchone()[0]
        fts_total = cur.execute("SELECT COUNT(*) FROM verses_fts").fetchone()[0]
        if verse_total != fts_total:
            raise RuntimeError(
                f"FTS count mismatch for {path.name}: "
                f"verses={verse_total}, fts={fts_total}"
            )

        integrity = cur.execute("PRAGMA quick_check").fetchone()[0]
        if integrity != "ok":
            raise RuntimeError(f"SQLite quick_check failed for {path.name}: {integrity}")

        user_version = cur.execute("PRAGMA user_version").fetchone()[0]
        if user_version != 0:
            raise RuntimeError(f"{path.name}: user_version={user_version}, expected 0")
    finally:
        conn.close()

    return {
        "chapters": chapter_count,
        "verses": verse_count,
        "headings": heading_count,
    }


# ---------------------------------------------------------------------------
# Manifest helpers
# ---------------------------------------------------------------------------


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def schema_fingerprint(database: Path) -> str:
    """Same fingerprint algorithm as tools/corpus/generate_manifest.py."""
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


def load_json(path: Path) -> dict:
    if not path.is_file():
        raise SystemExit(
            f"Missing source JSON {path}. Run tools/editions/fetch_sources.py first."
        )
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)
