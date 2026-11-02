# Public-domain English editions (WEBC, DRA)

Two English editions are generated from official **JSON** endpoints and stored in the
same per-book SQLite contract as the Konkani corpus. No HTML scraping is used: both
sources publish a single `complete.json` document, pinned by SHA-256.

| Edition | Source | Licence |
| --- | --- | --- |
| `webc` | <https://bible.helloao.org/api/eng_webc/complete.json> — World English Bible (Catholic), ebible.org `eng-web-c` | Public domain. The source's own metadata says `publicDomain: true`, attribution `not-required`. |
| `dra` | <https://bible.helloao.org/api/eng_dra/complete.json> — Douay-Rheims American Edition 1899, ebible.org `engDRA` | Public domain. The source's own metadata says `publicDomain: true`, attribution `not-required`. |

Pinned source hashes (checked by `fetch_sources.py`; the importer never downloads):

| Edition | SHA-256 | Bytes | Books / chapters / verses |
| --- | --- | --- | --- |
| `webc` | `3eea854d339cd3809a9efd03621ff88c7dc0f3e82a44ace0b2b54b2b824a922b` | 7,555,951 | 73 / 1,328 / 35,408 |
| `dra` | `041f23de2fde2e6c75383b4fe15f53867c676db5fb6b4c93bd55138ffb93f0a6` | 6,564,355 | 73 / 1,334 / 35,811 |

## Layout

```text
tools/editions/
  fetch_sources.py       # download + verify sources (gitignored output)
  edition_common.py      # catalog parsing, book mapping, schema, HTML/plain text, writer
  import_edition.py      # JSON -> per-book SQLite
  generate_manifest.py   # write/verify <edition>-manifest.json (metadata only)
  validate_editions.py   # read-only asset validation + spot checks
  webc-manifest.json     # committed provenance/checksums, no scripture
  dra-manifest.json
  sources/               # gitignored: sources/<edition>/complete.json
```

Generated assets (also gitignored through `*.sqlite`):

```text
app/src/main/assets/bible/webc/<CanonicalBookId>.sqlite   # 73 files
app/src/main/assets/bible/dra/<CanonicalBookId>.sqlite    # 73 files
```

Book ids, English names, testaments and positions are **read from**
`app/src/main/java/com/biblia/povitrpustok/domain/model/BibleCatalog.kt`; the tools
abort if that file does not yield exactly 73 unique books. Catalog `position`
(alphabetical, 1–73) is used verbatim — note that the Konkani corpus stores a
different, biblical reading order in `books.position`; the app never reads that
column for display order.

## Regeneration

```bash
python3 tools/editions/fetch_sources.py                 # download + verify both
python3 tools/editions/import_edition.py --edition all  # writes the 146 assets
python3 tools/editions/generate_manifest.py --edition all
python3 tools/editions/validate_editions.py             # read-only verification
```

`fetch_sources.py --force` re-downloads even when the cached file already matches.
A hash mismatch aborts and deletes the new download; review upstream changes before
re-pinning.

Regeneration is deterministic for a given source hash + SQLite version, so
`generate_manifest.py --check` fails if an asset or the schema changes. The manifest
holds only hashes, counts and a schema fingerprint; it never contains Bible text.

The schema, indexes, FTS5 table, triggers and `text_plain` normalization are copied
from `tools/corpus/import_corpus.py`. All 73 books share the same
`schema_fingerprint` as the Konkani corpus. FTS5 is required; the importer exits with
a clear message if the host SQLite lacks it.

## Source mapping and rendering decisions

* **Mapping by name, never by index.** Both helloao sources append the deuterocanon
  after Revelation rather than interleaving it. Aliases: `Esther (Greek)`→Esther,
  `Daniel (Greek)`→Daniel, `Wisdom of Solomon`→Wisdom, `Song of Solomon`→Song of
  Songs, `Ecclesiastes`→Qoheleth, `Revelation of John`→Revelation, and
  `Ecclesiasticus`/`Canticle of Canticles`/`Apocalypse`/`Sirach` as alternates.
  Unmapped or duplicate source names abort the import.
* **WEBC inline rendering:** strings and `{text}` escape to text; `poem` appends
  `<br/>`; `wordsOfJesus` wraps in `<font color="#FF0000">`; `lineBreak` becomes
  `<br/>`; `{descriptive,text}` becomes `<i>text</i>`; `noteId` is dropped (footnote
  text lives in `chapter.footnotes` and is editorial, not Scripture). Chunk
  boundaries that lost their separating space (a dropped note marker, a
  Song-of-Songs speaker label) get one space back; no other text is altered.
* **WEBC paragraphs:** a `line_break` with a `paragraph` value opens `<p>` for the
  next verse and closes at that verse's end, so every verse fragment stays
  self-contained. Line breaks without a paragraph become `<br/>` on the next verse.
* **WEBC headings:** the 5 explicit `heading` items go into `headings` with
  `before_verse` = the next verse's number (Baruch 6 → 1; Daniel 3 → 24 and 91;
  Daniel 13 → 1; Daniel 14 → 1), positions 0-based per chapter.
* **WEBC `hebrew_subtitle` (117):** rendered as `<i>…</i>` at the start of the
  following verse. A subtitle is skipped only if its normalized text already appears
  in that verse; none of the 117 did, so all 117 are rendered.
* **Empty verses (29, WEBC):** verses that exist only as a `noteId` (for example
  Sirach 1:5, Luke 17:36, Acts 8:37) keep their row with `text = ''` so source verse
  numbering and the 35,408 total stay intact.
* **DRA (helloao):** same `chapter.content[]` shape as WEBC but only `verse` items
  (strings, `{poem,text}`, `{lineBreak}`) and `line_break` items exist. It reuses
  the WEBC inline renderer and paragraph rule unchanged: `poem` → `<br/>`,
  `lineBreak` → `<br/>`, a `line_break` with `paragraph` → `<p>` around the next
  verse. There are no notes, red letters, headings or hebrew subtitles, and no
  markup is invented beyond those rules.
* **Introductions** are empty in both sources.

## Known source quirks (not fixed by this pipeline)

* DRA Esther uses Vulgate chapter numbering (16 chapters) while WEBC `Esther (Greek)`
  has 10; that is the entire 1,334 vs 1,328 chapter difference.
* DRA Psalms follow the Vulgate: Psalm 22 = Hebrew Psalm 23.
* Non-sequential verse numbers are preserved as published in WEBC: Esther (Greek) 4
  and 9, Sirach 11/16/19/22/26. The 1899 DRA source is continuous (1..N per chapter).
* The WEBC Daniel 3 heading embeds its own footnote body in the heading string
  ("THE SONG OF THE THREE HOLY CHILDREN3:24 …"); it is preserved verbatim.
* WEBC `chapter.footnotes` is not imported; no note text is stored.

## Deferred

* `BibleEditions`/`BibleCatalog` do not list `webc`/`dra` yet, so the app cannot open
  these assets. Wiring the editions (ids, canon, `assetPattern`) is a separate
  Kotlin change.
* No Kotlin/Robolectric test asserts these manifests; `validate_editions.py` is the
  current gate. A `CorpusManifestTest`-style test should be added once the editions
  are wired into the app.
