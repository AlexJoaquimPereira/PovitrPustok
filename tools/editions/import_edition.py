#!/usr/bin/env python3
"""Public-domain English JSON -> per-book SQLite corpus converter.

Two editions share one schema (the Konkani corpus contract) and one driver:

  webc  https://bible.helloao.org/api/eng_webc/complete.json
  dra   https://bible.helloao.org/api/eng_dra/complete.json

Book ids are mapped **by name** through
``app/src/main/java/com/biblia/povitrpustok/domain/model/BibleCatalog.kt``
(never by source index: the sources append the deuterocanon after Revelation).
Unmapped or duplicate names abort the run.

Output:
    app/src/main/assets/bible/<edition>/<CanonicalId>.sqlite   (gitignored)

Usage:
    python3 tools/editions/import_edition.py --edition all
    python3 tools/editions/import_edition.py --edition webc --verbose
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from edition_common import (
    DEFAULT_ASSETS_ROOT,
    BookData,
    Chapter,
    Heading,
    Verse,
    build_source_map,
    ensure_fts5,
    escape_text,
    load_catalog,
    load_json,
    map_source_books,
    normalize_book_name,
    normalize_html_for_plain_text,
    write_book_database,
)

SOURCES_DIR = Path(__file__).resolve().parent / "sources"

EDITIONS = {
    "webc": {
        "name": "World English Bible (Catholic)",
        "source": SOURCES_DIR / "webc" / "complete.json",
        "id_field": "id",  # book id used in meta.source_xml
        "prefix": "eng_webc",
    },
    "dra": {
        "name": "Douay-Rheims American Edition 1899",
        "source": SOURCES_DIR / "dra" / "complete.json",
        "id_field": "id",
        "prefix": "eng_dra",
    },
}

# ---------------------------------------------------------------------------
# WEBC rendering
# ---------------------------------------------------------------------------


# Characters that must not be preceded by the space re-inserted where a
# dropped footnote marker sat (WEBC stores the marker *instead of* a space).
NO_SPACE_BEFORE = set(".,;:!?)]}%…/-–—")


def render_webc_inline(items: list) -> str:
    """Render one WEBC verse ``content`` list to a self-contained HTML fragment.

    - strings / ``{text}``      -> escaped text
    - ``{text, poem}``          -> escaped text + ``<br/>``
    - ``{text, wordsOfJesus}``  -> ``<font color="#FF0000">text</font>``
    - ``{lineBreak}``           -> ``<br/>``
    - ``{noteId}``              -> dropped
    - ``{descriptive, text}``   -> ``<i>text</i>``

    Adjacent chunks are frequently split without surrounding whitespace ("God"
    + dropped note + "created", or a Song-of-Songs speaker label followed by
    its line). One space is restored at such a boundary; it never adds note
    text and never inserts markup.
    """
    parts: list[str] = []
    last_char = ""

    def take(text: str) -> str:
        nonlocal last_char
        if not text:
            return text
        if (
            last_char
            and not last_char.isspace()
            and not text[0].isspace()
            and text[0] not in NO_SPACE_BEFORE
        ):
            text = " " + text
        last_char = text[-1]
        return text

    for item in items:
        if isinstance(item, str):
            parts.append(escape_text(take(item)))
            continue

        if "noteId" in item:
            # Footnote text lives in chapter.footnotes but is not Scripture
            # text; dropping keeps verses free of editorial annotations.
            continue

        if "lineBreak" in item:
            parts.append("<br/>")
            last_char = ""
            continue

        text = take(item.get("text", ""))

        if "descriptive" in item:
            parts.append(f"<i>{escape_text(text)}</i>")
            continue

        if "wordsOfJesus" in item:
            text = f'<font color="#FF0000">{escape_text(text)}</font>'

        if "poem" in item:
            text += "<br/>"
            last_char = ""

        parts.append(text)

    return "".join(parts)


def subtitle_plain_text(items: list) -> str:
    """Plain text of a WEBC ``hebrew_subtitle`` block."""
    return " ".join(
        item if isinstance(item, str) else item.get("text", "")
        for item in items
    ).strip()


def parse_webc_book(source_book: dict, source_ref: str, catalog_book, stats: dict) -> BookData:
    chapters: list[Chapter] = []

    for chapter_json in source_book["chapters"]:
        chapter_node = chapter_json["chapter"]
        number = int(chapter_node["number"])

        verses: list[Verse] = []
        headings: list[Heading] = []

        pending_paragraph = False
        pending_breaks = 0
        pending_subtitles: list[str] = []
        seen_verse = False

        def flush_subtitles(target: Verse) -> None:
            """Attach pending hebrew subtitles to ``target`` unless duplicated."""
            nonlocal pending_subtitles
            for subtitle in pending_subtitles:
                target_plain = normalize_text_for_compare(target.text_html)
                marker = normalize_text_for_compare(escape_text(subtitle))
                if marker and marker in target_plain:
                    stats["subtitles_skipped_duplicate"] += 1
                    continue
                target.text_html = f"<i>{escape_text(subtitle)}</i> " + target.text_html
                stats["subtitles_rendered"] += 1
            pending_subtitles = []

        content = chapter_node["content"]

        for index, item in enumerate(content):
            item_type = item["type"]

            if item_type == "line_break":
                if item.get("paragraph"):
                    pending_paragraph = True
                    pending_breaks = 0
                else:
                    pending_breaks += 1
                continue

            if item_type == "heading":
                heading_html = escape_text(
                    subtitle_plain_text(item.get("content", []))
                )
                next_verse = next(
                    (
                        later["number"]
                        for later in content[index + 1:]
                        if later["type"] == "verse"
                    ),
                    None,
                )
                stats["headings"] += 1
                headings.append(
                    Heading(
                        position=len(headings),
                        before_verse=int(next_verse) if next_verse else 0,
                        text_html=heading_html,
                    )
                )
                continue

            if item_type == "hebrew_subtitle":
                subtitle = subtitle_plain_text(item.get("content", []))
                if subtitle:
                    pending_subtitles.append(subtitle)
                continue

            if item_type != "verse":
                raise SystemExit(f"Unhandled WEBC item type {item_type!r}")

            body = render_webc_inline(item["content"])
            prefix = ""
            if pending_paragraph:
                prefix = "<p>"
            elif pending_breaks and seen_verse:
                prefix = "<br/>" * pending_breaks
            suffix = "</p>" if pending_paragraph else ""

            text_html = prefix + body + suffix
            if not normalize_html_for_plain_text(body):
                # WEBC keeps the verse number with no text (the noteId-only
                # versification rows); store an empty row rather than markup.
                text_html = ""

            verse = Verse(
                number=int(item["number"]),
                text_html=text_html,
            )
            flush_subtitles(verse)
            verses.append(verse)

            if not normalize_html_for_plain_text(body):
                stats["empty_verses"] += 1
            pending_paragraph = False
            pending_breaks = 0
            seen_verse = True

        # A trailing subtitle with no following verse belongs to the last verse.
        if pending_subtitles:
            if verses:
                trailer = Verse(number=verses[-1].number, text_html="")
                flush_subtitles(trailer)
                if trailer.text_html:
                    verses[-1].text_html += " " + trailer.text_html
            else:
                pending_subtitles = []

        # Resolve heading anchors now that the verse list is known.
        resolved: list[Heading] = []
        for heading in headings:
            before = heading.before_verse
            if not before:
                later = next((v.number for v in verses), 1)
                before = later
            elif not any(v.number == before for v in verses):
                later = next((v.number for v in verses if v.number > before), None)
                before = later if later is not None else (verses[-1].number if verses else 1)
            resolved.append(
                Heading(
                    position=heading.position,
                    before_verse=before,
                    text_html=heading.text_html,
                )
            )

        chapters.append(Chapter(number=number, verses=verses, headings=resolved))
        stats["chapters"] += 1
        stats["verses"] += len(verses)

    return BookData(catalog=catalog_book, source_ref=source_ref, chapters=chapters)


def normalize_text_for_compare(fragment: str) -> str:
    return normalize_html_for_plain_text(fragment).lower()


# ---------------------------------------------------------------------------
# DRA rendering
# ---------------------------------------------------------------------------


def parse_dra_book(source_book: dict, source_ref: str, catalog_book, stats: dict) -> BookData:
    """Parse the helloao Douay-Rheims 1899 book.

    Same ``chapter.content[]`` shape as WEBC, but only ``verse`` items
    (strings, ``{poem,text}``, ``{lineBreak}``) and ``line_break`` items exist:
    no notes, red letters, headings or hebrew subtitles. The WEBC inline
    renderer and paragraph rules are reused unchanged; nothing is invented.
    """
    chapters: list[Chapter] = []

    for chapter_json in source_book["chapters"]:
        chapter_node = chapter_json["chapter"]
        number = int(chapter_node["number"])
        verses: list[Verse] = []
        pending_paragraph = False
        pending_breaks = 0

        for item in chapter_node["content"]:
            item_type = item["type"]

            if item_type == "line_break":
                if item.get("paragraph"):
                    pending_paragraph = True
                    pending_breaks = 0
                else:
                    pending_breaks += 1
                continue

            if item_type != "verse":
                raise SystemExit(f"Unhandled DRA item type {item_type!r}")

            body = render_webc_inline(item["content"])
            if pending_paragraph:
                prefix, suffix = "<p>", "</p>"
            elif pending_breaks and verses:
                prefix, suffix = "<br/>" * pending_breaks, ""
            else:
                prefix, suffix = "", ""

            text_html = prefix + body + suffix
            if not normalize_html_for_plain_text(body):
                text_html = ""

            verses.append(Verse(number=int(item["number"]), text_html=text_html))
            pending_paragraph = False
            pending_breaks = 0

        chapters.append(Chapter(number=number, verses=verses, headings=[]))
        stats["chapters"] += 1
        stats["verses"] += len(verses)

    return BookData(catalog=catalog_book, source_ref=source_ref, chapters=chapters)


# ---------------------------------------------------------------------------
# Driver
# ---------------------------------------------------------------------------


def import_edition(edition: str, out_root: Path, verbose: bool) -> dict:
    spec = EDITIONS[edition]
    payload = load_json(spec["source"])
    catalog = load_catalog()
    mapping = build_source_map(catalog)

    source_books = payload.get("books")
    if not isinstance(source_books, list) or not source_books:
        raise SystemExit(f"{spec['source']}: no 'books' array")

    mapped = map_source_books(source_books, mapping)
    if len(mapped) != 73:
        raise SystemExit(f"{edition}: mapped {len(mapped)} books, expected 73")

    out_dir = out_root / edition
    out_dir.mkdir(parents=True, exist_ok=True)

    totals = {
        "books": 0,
        "chapters": 0,
        "verses": 0,
        "headings": 0,
        "empty_verses": 0,
        "subtitles_rendered": 0,
        "subtitles_skipped_duplicate": 0,
    }

    print(f"\n{edition.upper()} - {spec['name']}")
    print(f"source: {spec['source']}")
    print(f"output: {out_dir}")

    for entry in sorted(mapped, key=lambda e: e["catalog"].position):
        source_book = entry["source"]
        catalog_book = entry["catalog"]
        source_ref = f"{spec['prefix']}:{source_book[spec['id_field']]}"

        stats = {
            "chapters": 0,
            "verses": 0,
            "headings": 0,
            "empty_verses": 0,
            "subtitles_rendered": 0,
            "subtitles_skipped_duplicate": 0,
        }

        if edition == "webc":
            book = parse_webc_book(source_book, source_ref, catalog_book, stats)
        else:
            book = parse_dra_book(source_book, source_ref, catalog_book, stats)

        if not book.chapters:
            raise SystemExit(f"{edition}/{catalog_book.book_id}: no chapters")

        db_path = out_dir / f"{catalog_book.book_id}.sqlite"
        written = write_book_database(db_path, edition, book)
        if written["chapters"] != stats["chapters"] or written["verses"] != stats["verses"]:
            raise SystemExit(
                f"{edition}/{catalog_book.book_id}: write mismatch {written} vs {stats}"
            )

        totals["books"] += 1
        for key in ("chapters", "verses", "headings", "empty_verses",
                    "subtitles_rendered", "subtitles_skipped_duplicate"):
            totals[key] += stats.get(key, 0)

        if verbose:
            print(
                f"  {catalog_book.book_id:30s} src={normalize_book_name(source_book['name']):24s} "
                f"chapters={stats['chapters']:3d} verses={stats['verses']:5d} "
                f"headings={stats['headings']:2d}"
            )

    print(
        f"  books={totals['books']} chapters={totals['chapters']} verses={totals['verses']} "
        f"headings={totals['headings']}"
    )
    if edition == "webc":
        print(
            f"  hebrew_subtitle: rendered={totals['subtitles_rendered']} "
            f"skipped_duplicate={totals['subtitles_skipped_duplicate']} "
            f"empty_verses={totals['empty_verses']}"
        )

    return totals


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--edition",
        choices=sorted(EDITIONS) + ["all"],
        default="all",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=DEFAULT_ASSETS_ROOT,
        help="Assets root (default: app/src/main/assets/bible).",
    )
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()

    ensure_fts5()

    editions = sorted(EDITIONS) if args.edition == "all" else [args.edition]

    print("=" * 78)
    print("PUBLIC-DOMAIN ENGLISH EDITION -> PER-BOOK SQLITE")
    print("=" * 78)

    for edition in editions:
        import_edition(edition, args.out, args.verbose)

    print("\nNext: python3 tools/editions/generate_manifest.py --edition all")
    return 0


if __name__ == "__main__":
    sys.exit(main())
