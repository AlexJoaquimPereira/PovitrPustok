# Povitr Pustok 1.1.0

**Release date:** 2026-10-20
**Tag:** `v1.1.0`
**Previous release:** `v1.0.0`
**Scope:** 48 commits — a full correctness, reliability, and release-hardening pass over the
already-implemented phases 1–10, plus corpus reproducibility and R8 optimization.

---

## Highlights

- **Fixed the P0 database bug that made every book-scoped read return nothing.** `books.id` is
  TEXT, but it was read through `getLong()`, producing `0`; every chapter list, chapter read, and
  introduction query then bound `"0"` and returned zero rows. Search appeared to work only because
  it did not filter on that id.
- **Search is functional on every device again.** Some OEM SQLite builds (including the API 31
  POCO M2 Pro used for testing) do not compile FTS5. The app now detects that at runtime and uses
  an accent/apostrophe-aware scan fallback instead of returning an empty result set.
- **Release APK is 67.8 % smaller** — R8/shrinking is enabled: **37.97 MB → 12.23 MB**.
- **The corpus is now reproducible** without committing copyrighted scripture: a deterministic
  importer, a metadata-only SHA-256 manifest, and a private-artifact CI path.
- **Copyright attribution added** in the Home UI and documentation: the Konkani Bible text is
  © Archdiocese of Goa and Daman.

---

## Breaking / behavior changes

| Area | Change |
|---|---|
| Search on non-FTS5 devices | Results are matched by normalized substring tokens and ordered by source position, not BM25. On FTS5-capable devices the ranked path is unchanged. |
| Search ordering across books | Results are rank-interleaved across books instead of being dominated by the first alphabetical book. |
| Range references | A requested range end must be covered by the corpus; impossible ends now return `NotFound` instead of silently narrowing. |
| Release artifacts | R8 is enabled. Keep `mapping.txt` if you need crash deobfuscation. |
| Distribution | The corpus is copyrighted and no longer assumed to be redistributable; see “Corpus and copyright” below. |
| Version | `versionCode 2`, `versionName 1.1.0`. |

No public API or database schema breaking change for users. Room migration `1→2` (liturgical cache)
runs automatically and has no destructive fallback.

---

## Database and corpus correctness

- **DB-01 (P0): textual `books.id`.** Added `storedBookId` via `getString(0)` and bound it in
  `getChapters`, `getChapter`, and `getIntroductionBlocks`. New all-73-book corpus read tests.
- **DB-02: NULL `verse_end`.** 34,251 of 34,272 verses store `NULL`; it is coalesced to
  `verse_start` so plain verses render `n`, not `n–0`. The 21 real source ranges are preserved.
- **DB-03: source verse order.** `getVerses` now orders by `position, id` instead of
  `verse_start`; 77 of 1,330 chapters previously displayed verses out of source order.
- **DB-04: prepared-database recovery.** The internal copy is validated (`quick_check` + required
  schema) before use; a corrupt copy is recopied atomically, and a corrupt packaged asset raises a
  typed error instead of being trusted on the marker alone.
- **DB-05: cancellation and handle policy.** Search checks cancellation between books; the
  73-handle process-lifetime policy is documented and tested.
- **REF-01/02: ranges and ambiguity.** Verses covered by a source range are found, duplicate
  verse starts surface an ambiguity picker, and range ends are validated.
- **REF-03/04: reference parsing.** Handles typographic dashes (U+2010/2011/2013/2014/2212),
  Ecclesiastes aliases, single-chapter books (Jude/Philemon/2 John), and preserves a verse target
  through chapter ambiguity.
- **CATALOG-01:** corrected 1/2 Thessalonians testament/English name and the Acts display name;
  the catalog is now cross-checked against all 73 corpus rows.
- Corpus fact corrections in tests: John 1 starts at verse 2 (50 records), 147 Psalms lack verse 1,
  John 12:24–26 resolves, James 1 lacks verses 1 and 7, Genesis 16 holds the 11–12 range.

## Search

- **SEARCH-01:** per-book rank query + fair round-robin merge; the default limit of 50 now spans
  many books instead of 50 hits from Amos.
- **SEARCH-02:** one corrupt/failing book no longer aborts the whole search; partial results plus a
  diagnostics warning are shown.
- **SEARCH-03 (partial):** on-device FTS tests strengthened and made device-ready. The FTS5
  `MATCH` path itself still needs FTS5-capable hardware; the test POCO lacks the module.
- **SEARCH-04:** runtime FTS5 probe + normalized scan fallback (case, diacritics, curly/ASCII
  apostrophes, edge punctuation). Verified on device: 13 search/repository tests pass without FTS5.

## Reader, HTML, and navigation

- **READER-01:** retry replays the last request, preserving verse target and passage.
- **READER-02:** duplicate chapter numbers (Job) are disambiguated consistently in the selector,
  menu, and reference labels.
- **READER-03:** removed the dead introduction path from the Reader; the separate introduction
  screen remains the sole entry point.
- **READER-04:** cancellation is rethrown, data failures are mapped, unexpected failures become a
  controlled error state instead of a crash/stuck spinner.
- **READER-05:** bookmark toggles are serialized; the DB unique index remains the backstop.
- **READER-06:** failed navigation clears stale target/selection/highlight/passage while retaining
  the last good chapter.
- **HTML-01:** fixed an infinite loop on a stray `/` inside a tag; added adversarial parser tests.
- **HTML-02:** rendered `AnnotatedString`s are remembered per document instead of rebuilt on every
  recomposition.
- **NAV-01:** wired the dead back arrows on Daily Reading, Bookmarks, and Reference.

## Daily Reading and liturgy

- **LITURGY-01:** day cache replacement is one Room transaction (verified in generated code).
- **LITURGY-02:** date changes are revalidated on `ON_RESUME`; same-day resumes do not refetch;
  superseded loads are cancelled.
- **LITURGY-03:** API date paths use a root-locale formatter (`MM-dd`), fixing non-Latin-digit
  locales.
- **LITURGY-04:** nullable API bodies are guarded (`MALFORMED_RESPONSE`), concurrent loads share a
  single fetch via a mutex + cache re-check.

## Data, bookmarks, backup

- **BOOKMARK-01:** opening a bookmark navigates by stable book/chapter/verse location, not a corpus
  row id. Badges/toggle remain row-id based within one corpus version (recorded follow-up).
- **PROVIDER-01:** close is synchronized, idempotent, and terminal; use-after-close fails fast.
- **BACKUP-01:** Android backup includes only the Room database, not the ~25 MB derived corpus.
- **DIST-01:** Reader scroll position and Search/Reference queries survive process death via
  `rememberSaveable`/`SavedStateHandle`; one-shot navigation is deliberately not replayed.

## Build, CI, and repository

- **RELEASE-01:** release signing config is actually applied when `keystore.properties` exists;
  credential-less builds remain unsigned. Signed APK verified with the existing certificate.
- **RELEASE-02:** R8/resource shrinking enabled; signed release **12,225,449 B** (was
  **37,972,811 B**).
- **RELEASE-03:** `compileSdk`/`targetSdk` aligned to **36/36**.
- **CI-01:** GitHub Actions runs unit tests, debug/release builds, lint, and androidTest
  compilation; reports are uploaded; no `continue-on-error`.
- **REPO-01/02:** removed stale divergent `dist/` APKs and the byte-identical inert
  `app/src/assets` copy; documented Gradle outputs.
- **Tests:** 381 JVM tests (0 failed, 2 documented FTS5 Assumes) and 19 instrumented tests
  (device run: 19 / 0 failed / 3 FTS5-only skips). Added corpus gate, manifest, migration,
  adversarial HTML, and Compose navigation/search tests.

## Corpus reproducibility and copyright

- `tools/corpus/import_corpus.py` regenerates all 73 SQLite books deterministically from private
  source XML; a full regeneration was verified byte-identical to the shipped assets.
- `tools/corpus/corpus-manifest.json` contains only hashes/counts/schema fingerprint
  (73 books, 1,330 chapters, 34,272 verses, version `konkani-catholic-2026.08.17`).
- `tools/corpus/fetch_corpus.sh` validates a private ZIP before replacing the local corpus.
- CI uses `CORPUS_ARCHIVE_URL`, `CORPUS_ARCHIVE_SHA256`, and optional `CORPUS_ARCHIVE_TOKEN`.
  Without them, corpus tests are reported as skipped via `-PskipCorpusTests` and no release APK is
  produced.
- **The Konkani Bible text, source XML, and derived databases are © Archdiocese of Goa and Daman.**
  They are not committed. A built APK still embeds the text; distribution requires permission.

---

## Upgrade notes

1. **Room migration 1→2 runs automatically.** It adds the liturgical cache tables; bookmarks and
   reading state are preserved, and there is no destructive fallback.
2. **The corpus is not in the repository.** Existing installs keep their internal copy; the
   `versionCode` marker (now 2) causes a fresh validated copy on first use.
3. **Search behavior depends on the device SQLite build.** FTS5 devices use ranked search; others
   use the scan fallback. Both are deterministic for identical queries.
4. **Release artifacts:** use `assembleRelease`/`bundleRelease`; signed only when
   `keystore.properties` exists. Keep `app/build/outputs/mapping/release/mapping.txt` for
   deobfuscation.
5. **CI:** configure the private corpus secrets to run the full suite/release job; otherwise the
   workflow intentionally runs the non-corpus suite.

## Known issues

- **SEARCH-03:** the FTS5 `MATCH`/BM25 path is still unverified on FTS5-capable hardware. The test
  device lacks the module; the fallback is fully exercised.
- **TEST-03:** three Compose UI tests exist (attribution, back navigation, search results); target
  scroll, bookmark taps, Job selector labels, swipes, and retry/passage UI tests remain.
- **Release-variant instrumentation** cannot run because R8 strips test-runner dependencies
  (`androidx.tracing`, `kotlin.LazyKt`). The R8 app itself was smoke-tested on device.
- Bookmark badges and the toggle still compare corpus row ids; a corpus rebuild that renumbers
  rows could hide a badge until re-bookmarked. Navigation itself is stable (BOOKMARK-01).
- The corpus distribution permission from the Archdiocese is an operational/legal prerequisite,
  not a code issue.

## Verification

| Check | Result |
|---|---|
| JVM suite | 381 tests / 0 failed / 2 skipped (documented FTS5 Assumes) |
| Device suite (POCO M2 Pro, API 31, MIUI 14) | 19 / 0 failed / 3 FTS5-only skips |
| `assembleDebug`, `assembleDebugAndroidTest` | PASS |
| `assembleRelease`, `bundleRelease`, `lintRelease` | PASS |
| Signed release certificate | SHA-256 `add011b3877e3163bed14e0aa8a4fbadb6f83ea1235b3842acedc7993526a261` |
| R8 smoke test | launch, Books, Genesis 1 reader, Search, real Daily Reading data |

### Artifacts

| Artifact | Size (bytes) | SHA-256 |
|---|---:|---|
| `app-release.apk` (signed, R8) | 12,225,449 | `9b9e40f87014abd2424ab83b73d61638ce6768176fdf130e0860886aaffbbd8f` |
| `app-release.aab` (signed, R8) | 13,243,888 | `d836b1502a6b757294645926198548184f8d08489ab60f55675dcad163ab1974` |

`mapping.txt` is generated under `app/build/outputs/mapping/release/` and is not committed.

---

## Appendix — commits in this release

```text
b175041 chore: keep audit and remediation documents local
31ec651 fix(db): bind textual books.id in book-scoped queries
724558a fix(db): coalesce null verse_end to verse_start
ba4f8e2 fix(db): order chapter verses by source position
c181fbc fix(db): validate and recover prepared Bible databases
13fa4ec perf(search): cancel corpus-wide search between books
a41046a fix(reference): resolve verses inside source ranges and surface duplicates
d744ba6 fix(reference): reject range ends not covered by the corpus
1e80b3a fix(search): rank results across books instead of alphabetically truncating
61bc5a8 fix(search): isolate per-book index failures
18f67ff test(search): make the on-device FTS suite device-ready
38b0ae7 build(release): apply the configured signing config
15c0902 build: align compileSdk and targetSdk to 36
f4e2eae perf(books): cache absent titles and serialize corpus scans
c4a93c5 fix(liturgy): write the day cache in one transaction
528b54a fix(daily-reading): revalidate the date on resume
5d8f9ad fix(liturgy): format API date paths with root locale
bba7ede fix(liturgy): serialize concurrent loads and guard null bodies
569d588 fix(html): guarantee parser progress on stray slashes
99e9fe7 perf(reader): cache rendered AnnotatedString
0c3b6db fix(reader): map unexpected load failures to a controlled error
fd85627 fix(reader): serialize bookmark toggles
b1013b3 fix(catalog): correct Thessalonians and Acts metadata
ef9afa9 fix(reference): parse lectionary dashes, single-chapter books, Ecclesiastes
81c4291 fix(reference): preserve verse target through chapter ambiguity
8a5d363 fix(reader): preserve target and passage on retry
ad9c5fb fix(reader): clear stale target and selection on load failure
783d43f fix(reader): disambiguate duplicate chapter labels in the selector
4af7ae4 refactor(reader): remove dead introduction path
d7e4bda fix(navigation): wire secondary-screen back actions
c9bc3a0 chore(backup): back up only the app database
969ae79 fix(db): make provider close synchronized and terminal
889e008 fix(bookmarks): navigate by stable location, not corpus row ids
737c597 test(corpus): correct stale corpus-fact expectations
7c7d8ab test(corpus): add presence/schema gate
b1b1afd test: repair vacuous assertions and remove templates
6774579 test(html): cover adversarial parser inputs
2f88aa5 test(db): cover the 1-2 migration and real upgrade path
b65e1a2 ci: gate on tests, builds, and lint
697f0b6 fix: preserve reader scroll and restore search/reference queries
4f79b3b chore: guard against the duplicate inert asset tree
1d6bf23 chore: remove stale dist artifacts and document build outputs
d8bba6a feat(corpus): add reproducible importer, manifest, and private CI fetch
a44627c docs: attribute the Bible corpus to the Archdiocese of Goa and Daman
8990bce fix(search): add a scan fallback when device SQLite lacks FTS5
e6a671d test(ui): add Compose navigation and attribution tests
4c74e0d build(release): enable R8 shrinking
eb2df6e test(ui): cover device search results
```
