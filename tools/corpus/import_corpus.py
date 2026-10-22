#!/usr/bin/env python3
"""
Konkani Bible XML -> per-book SQLite converter.

This is a hybrid importer:
- Uses the newer importer/database contract: introductions, headings, verse
  ranges, source positions, preserved HTML, text_plain, FTS5, and per-book DBs.
- Uses the older OpenCode importer’s important structural insight: a chapter
  is sometimes a text stream rather than a 1:1 mapping of XML child -> verse.
  When XML child boundaries are unreliable, verse markers are discovered from
  the complete source-order chapter stream.

IMPORTANT:
This script intentionally keeps the existing Android database contract.
Do not change table/column names without changing the Android data layer.

Input:
    *.xml in the current directory

Output:
    bible_db/<book-id>.sqlite

The generated database schema is the one used by the current Android app:
    meta
    books
    chapters
    introduction_blocks
    headings
    verses
    verses_fts

HTML is preserved in verses.text / headings.html /
introduction_blocks.html. text_plain is generated separately for FTS.
"""

from __future__ import annotations

import argparse
import glob
import html
import os
import re
import shutil
import sqlite3
import tempfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from typing import Iterable


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

OUT_DIR = "biblios_db"

NUM_TAG = re.compile(r"^_(\d+)$")
INTRO_TAG = re.compile(r"^_intro$", re.I)
TITLE_TAG = re.compile(r"^_title$", re.I)

# A verse locator:
#   1
#   18A
#   18b
#   18acb
#
# The alphabetic suffix is intentionally ignored for the numeric model.
#
# The surrounding rules avoid matching:
#   15:13
#   5/21-22
#   HTML attributes / ordinary alphanumeric words
VERSE_MARKER = re.compile(
    r"(?<![0-9A-Za-z.:/])"
    r"(?P<number>[0-9]{1,3})"
    r"(?P<suffix>[A-Za-z]+)?"
    r"(?=\s)"
)

# Used only for extracting the first/last verse locator from a source block.
# We deliberately do not globally remove every number-looking token.
VERSE_LOCATOR_RE = VERSE_MARKER

HTML_TAG_RE = re.compile(r"<[^>]*>", re.S)
HTML_COMMENT_RE = re.compile(r"<!--.*?-->", re.S)
WHITESPACE_RE = re.compile(r"\s+")

# Known Konkani -> English mapping from the existing corpus/importer contract.
BOOK_NAMES = {
    "Utpoti": "Genesis",
    "Suttka": "Exodus",
    "Levi_Xastr": "Leviticus",
    "Lokgonnti": "Numbers",
    "Dusri_Somurt": "Deuteronomy",
    "Joshuachem_Pustok": "Joshua",
    "Fuddareanchem_Pustok": "Judges",
    "Ruthachem_Pustok": "Ruth",
    "Samuelachem_Poilem_Pustok": "1 Samuel",
    "Samuelachem_Dusrem_Pustok": "2 Samuel",
    "Razanchem_Poilem_Pustok": "1 Kings",
    "Razanchem_Dusrem_Pustok": "2 Kings",
    "Itihasachem_Poilem_Pustok": "1 Chronicles",
    "Itihasachem_Dusrem_Pustok": "2 Chronicles",
    "Ezrachem_Pustok": "Ezra",
    "Nehemiasachem_Pustok": "Nehemiah",
    "Tobit": "Tobit",
    "Judit": "Judith",
    "Esther": "Esther",
    "Makabeusachem_Poilem_Pustok": "1 Maccabees",
    "Makabeusachem_Dusrem_Pustok": "2 Maccabees",
    "Jobachem_Pustok": "Job",
    "Stotranchem_Pustok": "Psalms",
    "Sirakachem_Pustok": "Sirach",
    "Zannvaiechem_Pustok": "Wisdom",
    "Gitantlem_Git": "Song of Songs",
    "Izaias": "Isaiah",
    "Jeremias": "Jeremiah",
    "Vilap": "Lamentations",
    "Barukachem_Pustak": "Baruch",
    "Ezekiel": "Ezekiel",
    "Daniel": "Daniel",
    "Hosea": "Hosea",
    "Joel": "Joel",
    "Amos": "Amos",
    "Obadia": "Obadiah",
    "Jonas": "Jonah",
    "Mika": "Micah",
    "Nahum": "Nahum",
    "Habakuk": "Habakkuk",
    "Zefania": "Zephaniah",
    "Haggai": "Haggai",
    "Zekaria": "Zechariah",
    "Malaki": "Malachi",
    "Upodexok": "Qoheleth",
    "Mhonnneo": "Proverbs",
    "matev": "Matthew",
    "mark": "Mark",
    "lukas": "Luke",
    "juanv": "John",
    "dhormdutancho_itihas": "Acts",
    "romkarank": "Romans",
    "korintkarant1": "1 Corinthians",
    "korintkarant2": "2 Corinthians",
    "galasiekarank": "Galatians",
    "efezkarank": "Ephesians",
    "filipkarank": "Philippians",
    "koloskarank": "Colossians",
    "tesalonikarank1": "1 Thessalonians",
    "tesalonikarank2": "2 Thessalonians",
    "timotak1": "1 Timothy",
    "timotak2": "2 Timothy",
    "tilak": "Titus",
    "filemonak": "Philemon",
    "hebrevank": "Hebrews",
    "jakob": "James",
    "pedru1": "1 Peter",
    "pedru2": "2 Peter",
    "juanv1": "1 John",
    "juanv2": "2 John",
    "juanv3": "3 John",
    "judas": "Jude",
    "juanvak_prokasovnni": "Revelation",
}

NT = {
    "matev", "mark", "lukas", "juanv", "dhormdutancho_itihas", "romkarank",
    "korintkarant1", "korintkarant2", "galasiekarank", "efezkarank",
    "filipkarank", "koloskarank", "tesalonikarank1", "tesalonikarank2",
    "timotak1", "timotak2", "tilak", "filemonak", "hebrevank", "jakob",
    "pedru1", "pedru2", "juanv1", "juanv2", "juanv3", "judas",
    "juanvak_prokasovnni",
}

# The importer must not rely on this for canonical ordering inside Android.
# This only provides deterministic corpus metadata. The Android application
# already owns the final canonical display order.
CANONICAL_IDS = [
    "Utpoti", "Suttka", "Levi_Xastr", "Lokgonnti", "Dusri_Somurt",
    "Joshuachem_Pustok", "Fuddareanchem_Pustok", "Ruthachem_Pustok",
    "Samuelachem_Poilem_Pustok", "Samuelachem_Dusrem_Pustok",
    "Razanchem_Poilem_Pustok", "Razanchem_Dusrem_Pustok",
    "Itihasachem_Poilem_Pustok", "Itihasachem_Dusrem_Pustok",
    "Ezrachem_Pustok", "Nehemiasachem_Pustok", "Tobit", "Judit", "Esther",
    "Makabeusachem_Poilem_Pustok", "Makabeusachem_Dusrem_Pustok",
    "Jobachem_Pustok", "Stotranchem_Pustok", "Mhonnneo", "Upodexok",
    "Gitantlem_Git", "Zannvaiechem_Pustok", "Sirakachem_Pustok",
    "Izaias", "Jeremias", "Vilap", "Barukachem_Pustak", "Ezekiel", "Daniel",
    "Hosea", "Joel", "Amos", "Obadia", "Jonas", "Mika", "Nahum", "Habakuk",
    "Zefania", "Haggai", "Zekaria", "Malaki",
    "matev", "mark", "lukas", "juanv", "dhormdutancho_itihas", "romkarank",
    "korintkarant1", "korintkarant2", "galasiekarank", "efezkarank",
    "filipkarank", "koloskarank", "tesalonikarank1", "tesalonikarank2",
    "timotak1", "timotak2", "tilak", "filemonak", "hebrevank", "jakob",
    "pedru1", "pedru2", "juanv1", "juanv2", "juanv3", "judas",
    "juanvak_prokasovnni",
]


# ---------------------------------------------------------------------------
# Existing database contract
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


# ---------------------------------------------------------------------------
# Data structures
# ---------------------------------------------------------------------------

@dataclass
class SourceFragment:
    """One source-order XML content block."""
    tag: str
    html: str
    child_index: int


@dataclass
class VerseRecord:
    verse_start: int
    verse_end: int | None
    text: str
    source_position: int
    raw_locator: str | None = None


# ---------------------------------------------------------------------------
# XML / HTML preservation
# ---------------------------------------------------------------------------

def serialize_xml_node(node: ET.Element) -> str:
    """
    Serialize an XML node's contents without using ElementTree's HTML
    serialization. The source corpus contains literal HTML inside XML text,
    so we preserve text/tails exactly and only reconstruct actual XML nodes.
    """
    parts: list[str] = []

    if node.text:
        parts.append(node.text)

    for child in list(node):
        # If the child is an actual XML node, preserve its tag/attributes.
        attrs = "".join(
            f' {k}="{html.escape(v, quote=True)}"'
            for k, v in child.attrib.items()
        )

        if len(child) == 0 and not child.text:
            parts.append(f"<{child.tag}{attrs}/>")
        else:
            parts.append(f"<{child.tag}{attrs}>")
            parts.append(serialize_xml_node(child))
            parts.append(f"</{child.tag}>")

        if child.tail:
            parts.append(child.tail)

    return "".join(parts)


def element_content(element: ET.Element) -> str:
    """Return the complete source content of an XML element."""
    return serialize_xml_node(element)


def normalize_html_for_plain_text(source: str) -> str:
    """
    Generate search text without modifying the canonical HTML column.

    This is intentionally conservative. Formatting tags disappear; <br> and
    paragraph/block tags become whitespace. Entities are decoded after tag
    removal.
    """
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

    # Keep meaningful Unicode characters and collapse only search whitespace.
    text = WHITESPACE_RE.sub(" ", text).strip()
    return text


# ---------------------------------------------------------------------------
# Verse detection
# ---------------------------------------------------------------------------

def marker_matches(text: str) -> list[re.Match[str]]:
    return list(VERSE_LOCATOR_RE.finditer(text))


def marker_number(match: re.Match[str]) -> int:
    return int(match.group("number"))


def remove_first_marker(text: str, match: re.Match[str]) -> str:
    """Remove only the locator itself; preserve all following HTML."""
    return text[:match.start()] + text[match.end():]


def remove_internal_markers(text: str) -> str:
    """
    Remove verse locators embedded in one source range block.

    This is used only when the XML structure itself says that several verse
    locators belong to one source record (e.g. 11-12). It is NOT used for the
    general chapter-stream parser.
    """
    return VERSE_LOCATOR_RE.sub("", text)


def clean_verse_html(text: str) -> str:
    """
    Minimal cleanup of the locator boundary.

    Do not normalize HTML whitespace or formatting. Only remove whitespace that
    was directly attached to the verse-number marker.
    """
    return text.lstrip()


def plausible_range(markers: list[re.Match[str]]) -> bool:
    """
    A numeric XML source block containing multiple verse locators is treated as
    one verse-range record only when the locators are a short consecutive run.

    This preserves source ranges such as 11-12 / 13-15 / 17-18 without grouping
    an entire chapter into one record.
    """
    if len(markers) < 2 or len(markers) > 4:
        return False

    nums = [marker_number(m) for m in markers]

    return all(b == a + 1 for a, b in zip(nums, nums[1:]))



# HTML context tracking used when a verse boundary occurs inside a formatting
# element. The database stores each verse as an independently renderable HTML
# fragment; therefore an element opened before verse N and closed after verse N
# must be closed in N and reopened in N+1.
HTML_TOKEN_RE = re.compile(r"(?P<tag><!--.*?-->|<[^>]*>)", re.S)
HTML_VOID_TAGS = {
    "br", "hr", "img", "meta", "link", "input", "area", "base",
    "col", "embed", "param", "source", "track", "wbr",
}
HTML_CONTAINER_TAGS = {
    "p", "div", "span", "font", "i", "em", "b", "strong", "u",
    "h1", "h2", "h3", "h4", "h5", "h6", "center", "sup", "sub",
}


def _html_tag_name(raw: str):
    m = re.match(r"<\s*(/?)\s*([A-Za-z][A-Za-z0-9:_-]*)", raw)
    return (m.group(1), m.group(2).lower()) if m else None


def _html_is_self_closing(raw: str, name: str) -> bool:
    return name in HTML_VOID_TAGS or bool(re.search(r"/\s*>\s*$", raw))


def html_context_before(text: str, offset: int):
    """Return the active HTML opening tags immediately before offset."""
    stack = []
    for m in HTML_TOKEN_RE.finditer(text, 0, offset):
        raw = m.group("tag")
        if raw.startswith("<!--"):
            continue
        parsed = _html_tag_name(raw)
        if not parsed:
            continue
        closing, name = parsed
        if name not in HTML_CONTAINER_TAGS:
            continue
        if closing:
            # Tolerate malformed source: close nearest matching opener.
            for i in range(len(stack)-1, -1, -1):
                if stack[i][0] == name:
                    del stack[i:]
                    break
        elif not _html_is_self_closing(raw, name):
            stack.append((name, raw))
    return stack


def html_context_after(text: str, offset: int, initial_stack):
    """Track HTML context through text[offset:] starting from initial_stack."""
    stack = list(initial_stack)
    for m in HTML_TOKEN_RE.finditer(text, offset):
        raw = m.group("tag")
        if raw.startswith("<!--"):
            continue
        parsed = _html_tag_name(raw)
        if not parsed:
            continue
        closing, name = parsed
        if name not in HTML_CONTAINER_TAGS:
            continue
        if closing:
            for i in range(len(stack)-1, -1, -1):
                if stack[i][0] == name:
                    del stack[i:]
                    break
        elif not _html_is_self_closing(raw, name):
            stack.append((name, raw))
    return stack


def independently_renderable_fragment(text: str, content_start: int, content_end: int):
    """
    Extract one verse interval and make HTML formatting self-contained.

    Tags opened before the verse locator are reopened at the beginning of the
    fragment. Tags left open at the verse boundary are closed. The source HTML
    itself is otherwise not normalized.
    """
    before = html_context_before(text, content_start)
    prefix = "".join(raw for _, raw in before)
    body = text[content_start:content_end]
    active = html_context_after(text, content_start, before)
    # html_context_after() above walks to the end of the entire source; for a
    # boundary we need context only through content_end.
    active = list(before)
    for m in HTML_TOKEN_RE.finditer(text, content_start, content_end):
        raw = m.group("tag")
        if raw.startswith("<!--"):
            continue
        parsed = _html_tag_name(raw)
        if not parsed:
            continue
        closing, name = parsed
        if name not in HTML_CONTAINER_TAGS:
            continue
        if closing:
            for i in range(len(active)-1, -1, -1):
                if active[i][0] == name:
                    del active[i:]
                    break
        elif not _html_is_self_closing(raw, name):
            active.append((name, raw))
    closing = "".join(f"</{name}>" for name, _ in reversed(active))
    return prefix + body + closing


def split_stream_into_verses(stream: str, start_position: int) -> list[VerseRecord]:
    """
    Split a chapter stream on explicit verse locators without inventing
    missing verse numbers.

    HTML is segmented independently: if <p>, <font>, <i>, etc. is open at a
    verse boundary, the current verse receives a closing tag and the next
    verse receives the corresponding opening tag. This prevents fragments such
    as "<p>verse text" in one record and "</p>next verse" in another.

    A prefix before the first explicit locator is kept as its own verse-1
    record when the first marker is exactly 2 (the John 1 repair): dropping
    it would silently lose Scripture. When the first marker is already verse
    1, the prefix is formatting only; a stream whose first marker is 3 or
    later is left unchanged and needs a source-specific audit.
    """
    matches = marker_matches(stream)
    if not matches:
        return []

    records = []

    # Some chapters (notably John 1) open with verse-1 content before the
    # first explicit marker, which is "2". The source has no literal "1"
    # marker because the chapter starts inside <_1>. Preserve that prefix as
    # verse 1 instead of dropping it; only do this when the first marker is
    # 2, so a genuinely verse-2-starting stream is not rewritten.
    if marker_number(matches[0]) == 2 and stream[:matches[0].start()].strip():
        prefix = independently_renderable_fragment(stream, 0, matches[0].start())
        if prefix.strip():
            records.append(
                VerseRecord(
                    verse_start=1,
                    verse_end=None,
                    text=prefix.lstrip(),
                    source_position=start_position,
                )
            )

    for i, match in enumerate(matches):
        content_start = match.end()
        content_end = matches[i + 1].start() if i + 1 < len(matches) else len(stream)
        fragment = independently_renderable_fragment(stream, content_start, content_end)
        if not fragment.strip():
            continue
        records.append(
            VerseRecord(
                verse_start=marker_number(match),
                verse_end=None,
                text=fragment.lstrip(),
                source_position=start_position + len(records),
            )
        )
    return records


def parse_source_block(
    fragment: SourceFragment,
    next_explicit_number: int | None,
    source_position: int,
) -> list[VerseRecord]:
    """Parse a numeric XML child while preserving HTML context."""
    text = fragment.html
    explicit_match = NUM_TAG.match(fragment.tag)
    explicit_number = int(explicit_match.group(1)) if explicit_match else None
    if explicit_number is None:
        return split_stream_into_verses(text, source_position)

    matches = marker_matches(text)
    if matches and marker_number(matches[0]) != explicit_number:
        return split_stream_into_verses(text, source_position)

    if not matches:
        # The XML tag itself supplies the verse number. Preserve all HTML.
        return [
            VerseRecord(
                verse_start=explicit_number,
                verse_end=None,
                text=text,
                source_position=source_position,
                raw_locator=str(explicit_number),
            )
        ]

    first = matches[0]

    # Explicit source range inside one XML record. Keep it as one DB record,
    # but make its HTML independently renderable.
    if len(matches) > 1 and plausible_range(matches):
        body = independently_renderable_fragment(
            text, first.end(), len(text)
        )
        return [
            VerseRecord(
                verse_start=marker_number(first),
                verse_end=marker_number(matches[-1]),
                text=body.lstrip(),
                source_position=source_position,
                raw_locator=first.group(0),
            )
        ]

    body = independently_renderable_fragment(
        text, first.end(), len(text)
    )
    return [
        VerseRecord(
            verse_start=explicit_number,
            verse_end=None,
            text=body.lstrip(),
            source_position=source_position,
            raw_locator=first.group(0),
        )
    ]


# ---------------------------------------------------------------------------
# Chapter parsing
# ---------------------------------------------------------------------------

def collect_chapter_fragments(chapter: ET.Element) -> tuple[
    list[SourceFragment],
    list[tuple[int, str, int]],
]:
    """
    Collect source-order content and title blocks.

    Returns:
      content fragments
      title blocks as (source_index, html, child_index)
    """
    fragments: list[SourceFragment] = []
    titles: list[tuple[int, str, int]] = []

    if chapter.text:
        fragments.append(
            SourceFragment("_chapter_text", chapter.text, -1)
        )

    for index, child in enumerate(list(chapter)):
        content = element_content(child)

        if TITLE_TAG.match(child.tag):
            titles.append((len(fragments), content, index))
        else:
            fragments.append(SourceFragment(child.tag, content, index))

        if child.tail:
            fragments.append(
                SourceFragment("_tail", child.tail, index)
            )

    return fragments, titles


def parse_chapter(
    chapter: ET.Element,
    chapter_number: int,
) -> tuple[list[VerseRecord], list[tuple[int, int, str]]]:
    """
    Hybrid chapter parser.

    Returns:
      verses
      headings as (source_position, before_verse, html)

    Decision:
      - If numeric XML children cleanly represent source records/ranges, retain
        their boundaries.
      - If a child contains a non-conforming multi-verse stream, or the chapter
        has a sparse child structure with hidden verse markers, parse the entire
        chapter stream like the older converter.

    This is the key fix for chapters such as John 1.
    """
    fragments, title_blocks = collect_chapter_fragments(chapter)

    numeric_fragments = [
        f for f in fragments if NUM_TAG.match(f.tag)
    ]

    # Full source stream is always available as a fallback.
    full_stream = "".join(f.html for f in fragments)

    all_matches = marker_matches(full_stream)

    # If the chapter contains many verse markers but the XML numeric children
    # do not account for them, the child hierarchy is not a reliable verse
    # boundary. Use stream parsing.
    child_numbers = [
        int(NUM_TAG.match(f.tag).group(1))
        for f in numeric_fragments
    ]

    stream_numbers = [marker_number(m) for m in all_matches]

    hidden_markers = len(stream_numbers) > len(numeric_fragments)

    # A chapter with one/few XML blocks containing many verses is exactly the
    # case the older importer handled better.
    stream_mode = (
        hidden_markers
        or (
            len(numeric_fragments) <= 2
            and len(all_matches) >= 4
        )
    )

    if stream_mode:
        verses = split_stream_into_verses(full_stream, 0)
    else:
        verses = []
        position = 0

        for fragment in fragments:
            if not NUM_TAG.match(fragment.tag):
                # Ignore title handled above and non-numeric tail/text fragments
                # for explicit mode. If they contain verse markers, switch to
                # stream mode to avoid losing them.
                if marker_matches(fragment.html):
                    verses = split_stream_into_verses(full_stream, 0)
                    stream_mode = True
                    break
                continue

            parsed = parse_source_block(
                fragment,
                next_explicit_number=None,
                source_position=position,
            )

            verses.extend(parsed)
            position += max(1, len(parsed))

    # If explicit parsing produced no verses but the source contains verse
    # markers, always fall back.
    if not verses and all_matches:
        verses = split_stream_into_verses(full_stream, 0)
        stream_mode = True

    # Heading association:
    # A title belongs immediately before the first verse whose source position
    # follows the title. If no later verse exists, use the first verse.
    #
    # Source order, not the title's displayed chapter number, is authoritative.
    headings: list[tuple[int, int, str]] = []

    for source_index, title_html, child_index in title_blocks:
        before_verse = verses[0].verse_start if verses else 1

        # Determine the first verse whose source content occurs after this
        # title. This applies in stream mode too: title blocks are
        # interspersed with numeric children (e.g. John 1's section titles),
        # and treating every title as chapter-start put all headings at the
        # top of the chapter.
        later_numbers = []
        for f in fragments:
            if f.child_index > child_index and NUM_TAG.match(f.tag):
                later_numbers.append(int(NUM_TAG.match(f.tag).group(1)))
        if later_numbers:
            before_verse = later_numbers[0]

        headings.append(
            (
                child_index if child_index >= 0 else source_index,
                before_verse,
                title_html,
            )
        )

    return verses, headings


# ---------------------------------------------------------------------------
# Introduction parsing
# ---------------------------------------------------------------------------

def parse_introduction(intro: ET.Element) -> list[tuple[str, str]]:
    """
    Preserve introduction children as ordered HTML blocks.

    _title is stored as type='title'. Other direct content is stored as
    type='body'. The source order is preserved.
    """
    blocks: list[tuple[str, str]] = []

    if intro.text and intro.text.strip():
        blocks.append(("body", intro.text))

    for child in list(intro):
        content = element_content(child)

        if TITLE_TAG.match(child.tag):
            blocks.append(("title", content))
        else:
            blocks.append(("body", content))

        if child.tail and child.tail.strip():
            blocks.append(("body", child.tail))

    return blocks


# ---------------------------------------------------------------------------
# Book parsing
# ---------------------------------------------------------------------------

def parse_book(root: ET.Element):
    intro_nodes: list[ET.Element] = []
    chapter_nodes: list[ET.Element] = []

    for child in list(root):
        if not isinstance(child.tag, str):
            continue

        if NUM_TAG.match(child.tag):
            chapter_nodes.append(child)
        elif INTRO_TAG.match(child.tag):
            intro_nodes.append(child)

    return intro_nodes, chapter_nodes


def derive_book_id(stem: str) -> str:
    return stem


def derive_display_name(stem: str, root: ET.Element) -> str:
    """
    Keep the database display-name fallback stable.

    The Android app separately obtains the authoritative visible local title
    from introduction_blocks.type='title'. Therefore this value is only a
    fallback/catalog value.
    """
    return root.tag.strip("_").replace("_", " ").strip() or stem


def canonical_position(stem: str, fallback: int) -> int:
    try:
        return CANONICAL_IDS.index(stem) + 1
    except ValueError:
        return fallback


# ---------------------------------------------------------------------------
# SQLite
# ---------------------------------------------------------------------------

def create_database(path: str):
    conn = sqlite3.connect(path)
    conn.executescript(SCHEMA)
    return conn


def write_database(
    path: str,
    stem: str,
    filename: str,
    root: ET.Element,
    intro_nodes: list[ET.Element],
    chapter_nodes: list[ET.Element],
    position: int,
):
    if os.path.exists(path):
        os.remove(path)

    conn = create_database(path)
    cur = conn.cursor()

    book_id = derive_book_id(stem)
    english_name = BOOK_NAMES.get(stem, stem)
    testament = "NT" if stem in NT else "OT"
    display_name = derive_display_name(stem, root)

    cur.executemany(
        "INSERT INTO meta(key, value) VALUES (?, ?)",
        [
            ("source_xml", filename),
            ("language", "Konkani"),
            ("testament", testament),
            ("english_name", english_name),
            ("display_name", display_name),
            ("generator", "hybrid-konkani-bible-converter"),
        ],
    )

    cur.execute(
        """
        INSERT INTO books(id, name, english_name, testament, position, filename)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        (
            book_id,
            display_name,
            english_name,
            testament,
            canonical_position(stem, position),
            filename,
        ),
    )

    intro_count = 0
    heading_count = 0
    verse_count = 0

    # Introductions
    intro_position = 0
    for intro in intro_nodes:
        for block_type, block_html in parse_introduction(intro):
            if not block_html.strip():
                continue

            intro_position += 1

            cur.execute(
                """
                INSERT INTO introduction_blocks(
                    book_id, position, type, html
                ) VALUES (?, ?, ?, ?)
                """,
                (
                    book_id,
                    intro_position,
                    block_type,
                    block_html,
                ),
            )
            intro_count += 1

    # Chapters
    chapter_position = 0

    for chapter_xml in chapter_nodes:
        chapter_position += 1

        match = NUM_TAG.match(chapter_xml.tag)
        if not match:
            continue

        chapter_number = int(match.group(1))

        cur.execute(
            """
            INSERT INTO chapters(book_id, number, position)
            VALUES (?, ?, ?)
            """,
            (
                book_id,
                chapter_number,
                chapter_position,
            ),
        )

        chapter_id = cur.lastrowid

        verses, headings = parse_chapter(
            chapter_xml,
            chapter_number,
        )

        # Headings are inserted in their source order. before_verse is derived
        # from the first verse that follows the heading in the source.
        for heading_position, before_verse, heading_html in headings:
            if not heading_html.strip():
                continue

            cur.execute(
                """
                INSERT INTO headings(
                    chapter_id, position, before_verse, html
                ) VALUES (?, ?, ?, ?)
                """,
                (
                    chapter_id,
                    heading_position,
                    before_verse,
                    heading_html,
                ),
            )
            heading_count += 1

        # Verses use source position, never verse number, for ordering.
        for source_position, verse in enumerate(verses):
            if not verse.text.strip():
                continue

            plain = normalize_html_for_plain_text(verse.text)

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
                    chapter_number,
                    verse.verse_start,
                    verse.verse_end,
                    source_position,
                    verse.text,
                    plain,
                ),
            )
            verse_count += 1

    # External-content FTS is maintained by the insert triggers.
    conn.commit()

    # Validate FTS cardinality before accepting the database.
    verse_total = cur.execute(
        "SELECT COUNT(*) FROM verses"
    ).fetchone()[0]

    fts_total = cur.execute(
        "SELECT COUNT(*) FROM verses_fts"
    ).fetchone()[0]

    if verse_total != fts_total:
        conn.rollback()
        conn.close()
        raise RuntimeError(
            f"FTS count mismatch for {filename}: "
            f"verses={verse_total}, fts={fts_total}"
        )

    # Basic integrity check.
    integrity = cur.execute("PRAGMA integrity_check").fetchone()[0]
    if integrity != "ok":
        conn.close()
        raise RuntimeError(
            f"SQLite integrity check failed for {filename}: {integrity}"
        )

    conn.close()

    return {
        "chapters": chapter_position,
        "verses": verse_count,
        "headings": heading_count,
        "introductions": intro_count,
    }


# ---------------------------------------------------------------------------
# Diagnostics
# ---------------------------------------------------------------------------

def chapter_diagnostic(
    db_path: str,
    book_name: str,
    chapter_number: int,
):
    """
    Print the imported verse sequence for selected diagnostic chapters.
    """
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()

    rows = cur.execute(
        """
        SELECT c.position,
               c.number,
               v.position,
               v.verse_start,
               v.verse_end,
               length(v.text)
        FROM chapters c
        JOIN verses v ON v.chapter_id = c.id
        WHERE c.number = ?
        ORDER BY c.position, v.position
        """,
        (chapter_number,),
    ).fetchall()

    print(f"\nDIAGNOSTIC {book_name} chapter {chapter_number}")
    print("-" * 72)

    for row in rows:
        print(
            f"chapter_position={row[0]:>3} "
            f"chapter={row[1]:>3} "
            f"verse_position={row[2]:>3} "
            f"verse={row[3]}"
            f"{('-' + str(row[4])) if row[4] else ''} "
            f"html_length={row[5]}"
        )

    conn.close()


def validate_database(db_path: str, stem: str):
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()

    checks = {}

    checks["chapters"] = cur.execute(
        "SELECT COUNT(*) FROM chapters"
    ).fetchone()[0]

    checks["verses"] = cur.execute(
        "SELECT COUNT(*) FROM verses"
    ).fetchone()[0]

    checks["headings"] = cur.execute(
        "SELECT COUNT(*) FROM headings"
    ).fetchone()[0]

    checks["introductions"] = cur.execute(
        "SELECT COUNT(*) FROM introduction_blocks"
    ).fetchone()[0]

    checks["fts"] = cur.execute(
        "SELECT COUNT(*) FROM verses_fts"
    ).fetchone()[0]

    if checks["verses"] != checks["fts"]:
        raise RuntimeError(
            f"{stem}: FTS mismatch "
            f"{checks['verses']} != {checks['fts']}"
        )

    duplicate_chapters = cur.execute(
        """
        SELECT number, COUNT(*)
        FROM chapters
        GROUP BY number
        HAVING COUNT(*) > 1
        """
    ).fetchall()

    if duplicate_chapters:
        checks["duplicate_chapters"] = duplicate_chapters

    conn.close()
    return checks


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    global OUT_DIR

    parser = argparse.ArgumentParser(
        description="Generate the per-book Konkani Bible SQLite corpus from source XML."
    )
    parser.add_argument(
        "--source",
        default=".",
        help="Directory containing the source *.xml files (default: current directory).",
    )
    parser.add_argument(
        "--out",
        default=OUT_DIR,
        help=f"Output directory for <book-id>.sqlite files (default: {OUT_DIR}).",
    )
    args = parser.parse_args()

    OUT_DIR = args.out
    os.makedirs(OUT_DIR, exist_ok=True)

    files = sorted(
        f for f in glob.glob(os.path.join(args.source, "*.xml"))
        if os.path.basename(f).lower() not in {"convert.xml"}
    )

    if not files:
        raise SystemExit("No XML files found in the current directory.")

    print("=" * 80)
    print("HYBRID KONKANI BIBLE XML -> SQLITE CONVERTER")
    print("=" * 80)
    print(f"Input files : {len(files)}")
    print(f"Output dir  : {OUT_DIR}")
    print()

    success = 0
    failed = []

    totals = {
        "chapters": 0,
        "verses": 0,
        "headings": 0,
        "introductions": 0,
    }

    for fallback_position, filename in enumerate(files, start=1):
        stem = os.path.splitext(os.path.basename(filename))[0]

        try:
            tree = ET.parse(filename)
            root = tree.getroot()

            intro_nodes, chapter_nodes = parse_book(root)

            if not chapter_nodes:
                raise RuntimeError("No numeric chapter elements found.")

            db_path = os.path.join(OUT_DIR, f"{stem}.sqlite")

            stats = write_database(
                db_path,
                stem,
                os.path.basename(filename),
                root,
                intro_nodes,
                chapter_nodes,
                fallback_position,
            )

            validation = validate_database(db_path, stem)

            for key in totals:
                totals[key] += stats[key]

            success += 1

            print(
                f"OK  {filename:40s} "
                f"chapters={stats['chapters']:4d} "
                f"verses={stats['verses']:5d} "
                f"headings={stats['headings']:4d} "
                f"intro={stats['introductions']:3d} "
                f"fts={validation['fts']:5d}"
            )

            if "duplicate_chapters" in validation:
                print(
                    f"    duplicate chapter numbers preserved: "
                    f"{validation['duplicate_chapters']}"
                )

        except Exception as exc:
            failed.append((filename, str(exc)))
            print(f"FAIL {filename}: {exc}")

    print()
    print("=" * 80)
    print("FINAL REPORT")
    print("=" * 80)
    print(f"Successfully processed: {success}")
    print(f"Failed:                 {len(failed)}")
    print(f"Total chapters:         {totals['chapters']}")
    print(f"Total verses:           {totals['verses']}")
    print(f"Total headings:         {totals['headings']}")
    print(f"Total intro blocks:     {totals['introductions']}")

    if failed:
        print()
        print("FAILED FILES:")
        for filename, error in failed:
            print(f"  {filename}: {error}")

    print()
    print("IMPORTANT POST-IMPORT CHECKS")
    print("-" * 80)
    print("Inspect at minimum:")
    print("  - John 1")
    print("  - Psalms 1")
    print("  - Psalms 23")
    print("  - Psalms 112")
    print("  - Psalms 150")
    print("  - James 1")
    print("  - Job 26")
    print("  - Genesis 17")
    print()
    print("Do not copy the databases into Android until these sequences")
    print("have been compared against the XML source.")

    if failed:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
