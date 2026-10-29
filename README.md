# Povitr Pustok — Konkani Catholic Bible for Android

An offline-first Android reader for a **73-book Konkani Catholic Bible** corpus, built with
Kotlin, Jetpack Compose and Material 3.

The app renders a corpus of per-book SQLite databases that were generated outside this
repository from the original XML sources. The corpus is treated as **immutable content**:
the app never re-imports, rewrites or "fixes" it.

| | |
|---|---|
| **Application ID / namespace** | `com.biblia.povitrpustok` |
| **Version** | 1.3.0 (versionCode 11) |
| **Min SDK** | 30 (Android 11) — FTS5 is used when the device SQLite provides it; a scan fallback covers builds that omit it |
| **Compile SDK / Target SDK** | 36 / 36 |
| **Language / UI** | Kotlin 2.2.10 · Jetpack Compose · Material 3 |
| **Architecture** | Layered: UI → ViewModel → Repository → datasource |
| **Tests** | 542 JVM unit tests (Robolectric 4.16.1; 2 FTS5 Assumes) · 55 instrumented tests (3 FTS5-only + 1 network-dependent skip) |

---

> [!IMPORTANT]
> **The Bible corpus is not committed to this repository.**
> The Konkani text is © **Archdiocese of Goa and Daman**. The 73 `*.sqlite` files (≈25 MB) under
> `app/src/main/assets/bible/` are excluded by `.gitignore` (`*.sqlite`, `*.sql`). A fresh clone
> therefore **builds but cannot display Bible text** until the corpus is supplied — see
> [Obtaining the corpus](#obtaining-the-corpus).

---

## Contents

1. [Overview](#1-overview)
2. [Features](#2-features)
3. [Screens and navigation](#3-screens-and-navigation)
4. [Architecture](#4-architecture)
5. [Technology stack](#5-technology-stack)
6. [Data layer](#6-data-layer)
7. [Corpus characteristics and known data quirks](#7-corpus-characteristics-and-known-data-quirks)
8. [Project structure](#8-project-structure)
9. [Getting started](#9-getting-started)
10. [Testing](#10-testing)
11. [Build outputs](#11-build-outputs)
12. [Known limitations and roadmap](#12-known-limitations-and-roadmap)
13. [Repository conventions](#13-repository-conventions)
14. [Documentation](#14-documentation)
15. [License](#15-license-and-corpus-attribution)
16. [About and disclosure pages](#16-about-and-disclosure-pages)

---

## 1. Overview

*Povitr Pustok* ("Holy Book") is a Konkani-language Catholic Bible reader.

The engineering goals, in priority order:

1. Fast chapter opening and navigation.
2. Fast full-text Bible search across all 73 books.
3. Fast Scripture-reference lookup (`James 1:5`).
4. Faithful preservation of the source HTML formatting.
5. Correct rendering of poetic and specially formatted passages.
6. Support for introductions, multiple chapter titles, headings and irregular source structures.
7. Offline-first operation for the Bible corpus.
8. Clean separation between imported Bible content and application logic.

The Bible text is fully offline. Only the *Daily Reading* feature uses the network, and it
degrades to a local cache (see [§6.3](#63-external-liturgical-data-daily-reading)).

---

## 2. Features

### 2.1 Home
Launcher screen with entry points to **Books**, **Search**, **Go to reference**, **Bookmarks**
and **Daily Reading**, plus a **Continue reading** action that restores the last-read location
from the application database.

### 2.2 Books
* All 73 books grouped into **Old Testament** / **New Testament** in the corpus's canonical order.
* Each row shows the Konkani display name (deterministic Capitalize-Case) on the left and the
  English name on the right, ellipsised when long.
* Select a book → chapter grid, or open its **Introduction**.

### 2.3 Reader
* One chapter at a time, composed from headings and verses in source order. Book introductions
  are a separate screen (Books → Introduction), not part of the Reader.
* Source HTML is parsed into a structured representation and rendered to `AnnotatedString` —
  paragraphs, alignment, italics, bold, `<font color>`, line breaks and entities are preserved.
  Rendered text is always the canonical `verses.text`; `text_plain` is never used for display.
* **Verse ranges** (`verse_start`/`verse_end`) are preserved.
* **Poetic mode for Psalms** — a psalm verse without its own alignment renders centred, keeping
  `<br>` structure; explicit alignments are honoured.
* **Verse targeting** — the reader can open a chapter scrolled/highlighted to a specific verse or
  a requested passage (used by Search, Reference lookup, Bookmarks and Daily Reading).
* **Tap-to-select + bookmark** — tapping a verse selects it; the top-bar star toggles the
  bookmark for exactly that verse, and the subtitle shows `Chapter N · verse M`.
* **Swipe left/right** to move to the next/previous chapter (96 dp threshold, horizontal-dominance
  test, boundaries no-op); Previous/Next buttons and a chapter selector remain available.
* Malformed source HTML degrades deterministically and is reported through parser diagnostics.

### 2.4 Search
* FTS5 `MATCH` full-text search across all per-book indexes when the device SQLite provides
  FTS5, with an optional single-book filter.
* On devices whose SQLite build omits FTS5 (observed on some API 31 OEM builds), a scan fallback
  matches case- and accent-insensitively with the same per-book limit. Fallback results are
  source-position-ordered rather than BM25-ranked; search never silently returns zero because a
  module is missing.
* Input is sanitised into a safe FTS5 query (no raw injection into the MATCH string).
* Results show the book name, `chapter:verse` (or range) and a plain-text context snippet
  (up to 3 lines, ellipsised) — the snippet is the `text_plain` window, never raw HTML.
* Snippets are built in Kotlin (`SnippetBuilder`) — deliberately **not** with the SQL
  `snippet()` function, which is not portable across SQLite builds (see
  [§7](#7-corpus-characteristics-and-known-data-quirks)).
* Debounced, cancellation-safe query pipeline.

### 2.5 Reference lookup
* Parses common reference syntax (`James 1:5`, `1 John 3:16`, ranges) with case-insensitive
  Konkani/English book-name matching.
* Resolves to a stable internal location (`bookId` + `chapterPosition` + verse) rather than to
  chapter numbers, so duplicate chapter numbers in the corpus (e.g. Job) remain addressable.
* Ambiguous chapter matches are surfaced for the user to choose; invalid/not-found references
  produce explicit messages.

### 2.6 Bookmarks
* Bookmarks identify a **stable anchor** — book id, source chapter position, source verse range,
  and optional offsets — never corpus row ids, which change when the assets are regenerated.
* Each bookmark has an app-owned UUID and keeps the chapter number only as a display copy;
  the unique index is the anchor itself.
* List screen ordered newest-first, with removal and tap-to-open in the Reader.
* Persisted in the mutable application database; **never** written to the Bible databases.

### 2.7 Daily Reading
* Fetches the Catholic lectionary for a date from the
  [Catholic Readings API](https://cpbjr.github.io/catholic-readings-api/) and supplies the actual
  Bible text from the local corpus.
* **Cache-first, date-keyed** policy owned by the repository:
  * cached for that date → return cache;
  * otherwise fetch → save on success → return;
  * fetch failed + cache exists → return stale cache;
  * fetch failed + no cache → network error;
  * malformed response never overwrites a valid cache.
* No polling and no background scheduling; yesterday's cache never satisfies today.

### 2.8 Theming
Material 3 light and dark themes with a blue reading-focused accent, custom typography and a
low-noise shape set. The XML `Theme.PovitrPustok` covers only the pre-Compose window.

### 2.9 Settings
Reading controls: Scripture size, line spacing, red-letter toggle, and light/dark/system theme.
Each control writes through immediately to `SharedPreferences` and applies to the next
composition; the preview uses the real Scripture text style. Settings also shows the app version
and the entry to **About**.

---

## 3. Screens and navigation

Navigation uses a single `NavController`/back stack (Navigation Compose). Routes are declared in
`core/navigation/AppDestination.kt` and wired in `core/navigation/PovitrPustokNavHost.kt`.
Navigation arguments are compact identifiers (book id, chapter position, optional verse target,
optional edition id) — never HTML or content objects.

| Route | Screen | Arguments |
|---|---|---|
| `home` | Home launcher | — |
| `books` | Books catalogue | — |
| `books/{bookId}` | Chapter selection | `bookId` |
| `books/{bookId}/introduction` | Book introduction | `bookId` |
| `reader/{bookId}/{chapterPosition}?verseId=&verseStart=&verseEnd=&passages=&targetVerse=&editionId=` | Reader | `bookId`, `chapterPosition`, optional verse target / passage, optional edition |
| `search?editionId=` | Search | optional edition |
| `reference` | Scripture reference entry | — |
| `daily-reading` | Daily Reading | — |
| `bookmarks` | Bookmarks | — |
| `settings` | Settings (reading preferences) | — |
| `about` | About landing page | — |
| `about/source-edition` | Source edition disclosure | — |
| `about/api` | API and data disclosure | — |
| `about/licenses` | Software licences | — |
| `about/licenses/app` | App licence (full Apache-2.0 text) | — |
| `about/credits` | Credits | — |

The About disclosures are under `about/…`. `settings` is the reader-preferences screen, not an
About route. `editionId` is optional everywhere and defaults to `konkani-romi`.

Back-stack behaviour is the standard push/pop of Navigation Compose: Books → Chapters →
Introduction and Reader targets pop via `popBackStack()`.

---

## 4. Architecture

```text
Compose UI  (feature/*, ui/*)
     │  immutable UI state (StateFlow)
ViewModel   (feature/*/*ViewModel.kt)
     │  domain calls
Repository  (domain/repository ← data/*RepositoryImpl)
     │
DataSource  ├── Bible corpus: read-only SQLite assets (never written)
            ├── App state:    Room database (bookmarks, reading state, liturgy cache)
            └── Network:      Catholic Readings API (Daily Reading only)
```

Enforced rules (see implementation-plan §46 and the coding-harness rules):

* **No UI database access.** Compose and ViewModels never touch SQLite or Room directly.
* **No hard-coded Bible content.** All text comes from the corpus databases.
* **No destructive content transformations.** The corpus is never corrected or rewritten.
* **The repository is not a renderer** and the HTML parser is not a repository.
* **The HTML pipeline is Compose-free:** `BibleHtmlParser` → `BibleHtmlNode` →
  `BibleHtmlRenderer` → Compose (`BibleHtmlText`). The parser is pure Kotlin and JVM-testable.
* **No startup corpus copy or preload.** Each book is prepared lazily on first use, once per process.

### Bible editions

`Edition` / `BibleEditions` declare the edition id, language tag, label, asset pattern, canon and
licence. Only **Konkani (Romi)** (`konkani-romi`) ships today and is the default everywhere.
`BibleEditions.byId` returns `null` for an unknown id and the provider raises
`BibleEditionNotFoundException`; nothing silently falls back to another edition. The provider keys
handles by `(editionId, bookId)`; the default edition keeps the pre-seam `filesDir/bible/` path so
existing users do not recopy the corpus, while future editions get their own subtree. Reader and
search routes declare an optional `editionId` query parameter, defaulting to Konkani, so the UI is
unchanged.

### 1.3.0 data foundations

1.3.0 adds the contracts later releases depend on: **Room schema v3** with stable verse anchors,
the **edition seam**, and the **bundled content-pack format** with a build-time licence gate.
There is no visible feature change; the licences and reading-preference pages already shipped. The
design record — schema/migration rules, anchor identity, edition keying, pack validation, and the
single-module decision — is [`docs/architecture.md`](docs/architecture.md).

### Package responsibilities

| Package | Responsibility |
|---|---|
| `core/database` | Read-only per-book SQLite handle + edition-aware asset-backed provider |
| `core/html` | Corpus HTML parser, node model, diagnostics, renderer, Compose surface, bounded document cache |
| `core/navigation` | Route contract and navigation graph |
| `core/pack` | Bundled content-pack source, manifest parser, checksum-verifying loader |
| `core/result` | `BibleDataException` and `ContentPackException` hierarchies |
| `domain/model` | Pure domain models (books, chapters, verses, headings, liturgy, bookmarks, editions, packs) |
| `domain/reference` | Book-name matcher and Scripture reference parser (pure) |
| `domain/repository` | Repository interfaces (the domain boundary) |
| `data/app` | Room database, DAOs, entities, bookmark repository |
| `data/bible` | Bible/search/title repositories, reference resolver, snippet builder |
| `data/liturgy` | Catholic Readings API client, DTOs, mappers, cache-first repository |
| `data/preferences` | Persisted reading preferences (`SharedPreferences`) |
| `data/di` | Hilt bindings |
| `feature/*` | One package per screen: `Screen`, `UiState`, `ViewModel` |
| `ui` | Home screen/ViewModel, shared components and the Material 3 theme |

---

## 5. Technology stack

| Area | Choice | Version |
|---|---|---|
| Language | Kotlin (AGP 9 built-in) | 2.2.10 |
| Build | Android Gradle Plugin | 9.3.1 |
| Build | Gradle wrapper | 9.5.0 |
| Build | Gradle daemon JVM toolchain | 25 |
| Build | Java source/target compatibility | 11 |
| Build | KSP | 2.2.10-2.0.2 |
| UI | Jetpack Compose BOM | 2025.11.01 |
| UI | Compose compiler plugin | 2.2.10 |
| UI | Material 3, Activity Compose | BOM / 1.10.1 |
| UI | Lifecycle (runtime + viewmodel compose) | 2.9.4 |
| Navigation | Navigation Compose | 2.9.3 |
| DI | Hilt (+ hilt-navigation-compose) | 2.60.1 / 1.3.0 |
| Persistence | Room (runtime, ktx, compiler) | 2.7.2 |
| Async | Kotlin Coroutines | 1.9.0 |
| Network | Retrofit + Gson converter | 3.0.0 |
| Testing | JUnit4 | 4.13.2 |
| Testing | Robolectric | 4.16.1 |
| Testing | AndroidX Test Core, Coroutines Test | 1.7.0, 1.9.0 |
| Instrumented | AndroidX JUnit ext, Espresso | 1.1.5, 3.5.1 |

Versions are centralised in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

Two build notes worth knowing:

* `minSdk = 30` is deliberate: it is the baseline for the app's storage/lifecycle APIs. Search
  uses the framework SQLite FTS5 module when present; because some OEM builds omit it, a
  normalized scan fallback keeps search functional (SEARCH-04).
* `android.disallowKotlinSourceSets=false` in `gradle.properties` keeps KSP working with AGP 9
  built-in Kotlin (KSP still registers generated sources through the legacy `kotlin.sourceSets` DSL).

---

## 6. Data layer

### 6.1 Immutable Bible corpus

**Location:** `app/src/main/assets/bible/<BookId>.sqlite` — 73 files, one per book (~25 MB total).

**Preparation lifecycle** (`AssetBibleDatabaseProvider`):

1. The book id is resolved against the edition-scoped static `BibleCatalog` (73 entries for
   Konkani) — rendering the catalog never opens a database. An unknown edition or a book outside
   the edition's canon is rejected before any copy.
2. On first use the APK asset is copied lazily to `filesDir/bible/<BookId>.sqlite` through a
   `.sqlite.tmp` file and an atomic rename, so a partial copy is never exposed. The shipped
   Konkani edition keeps this pre-seam path; future editions get their own
   `filesDir/bible/<editionId>/` subtree.
3. A `.version` marker holding the app's `versionCode` is written alongside; a missing, stale or
   partial copy is re-copied.
4. The database is opened with `OPEN_READONLY` and cached for the process lifetime, keyed by
   `(editionId, bookId)`.
5. `BibleEditionNotFoundException` / `BibleBookNotFoundException` /
   `BibleDatabaseNotFoundException` / `CorruptBibleDatabaseException` are raised on the
   corresponding failures.

**Schema** (identical in every book database, generated by the external import pipeline):

| Table | Purpose |
|---|---|
| `meta` | `key`/`value` metadata: source filename, schema version, language, testament, English name, display name |
| `books` | One row per database: `name`, `english_name`, `testament`, `position`, `filename` |
| `chapters` | `id`, `book_id`, `number`, `position` — `position` controls source ordering; `number` may repeat |
| `headings` | `id`, `chapter_id`, `position`, `before_verse`, `html` — multiple headings per chapter, ordered |
| `verses` | `id`, `chapter_id`, `chapter_number`, `verse_start`, `verse_end`, `text`, `text_plain` |
| `introduction_blocks` | `id`, `book_id`, `position`, `type`, `html` — separate, ordered blocks |
| `verses_fts` | FTS5 external-content index over `verses.text_plain` |

**Full-text search** uses FTS5 `MATCH` joined back to `verses`/`chapters` when the device build
provides FTS5. If the module is absent, a per-book scan normalizes case/diacritics/apostrophes and
requires every query token; results stay deterministic but are position-ordered rather than ranked.
Search results are
snippeted in Kotlin because the SQL `snippet()` auxiliary function is not portable across SQLite
builds when the FTS table's columns do not match the content table (see §7).

**Source fidelity rules:** `verses.text` is canonical; HTML is never stripped during import;
`text_plain` is never used for display; multiple headings and introduction blocks stay separate;
poetic line breaks must remain representable.

### 6.2 Mutable application database (Room)

`AppDatabase` (`povitr_pustok_app.db`, **version 3**, `exportSchema = true`) holds only
application state and is entirely separate from the Bible assets. Schemas `1.json`–`3.json` are
checked in under `app/schemas/`.

| Table | Contents |
|---|---|
| `bookmarks` | App-owned UUID `id`; stable anchor (`bookId`, `chapterPosition`, `verseStart`, `verseEnd`, `startOffset`, `endOffset`); display `chapterNumber`; `createdAt`/`updatedAt`/`deletedAt`; unique index on the anchor |
| `reading_state` | Single row: last-read `bookId` + `chapterPosition`, with `updatedAt` |
| `liturgical_day_cache` | Cached liturgical day, **UNIQUE** on `date` |
| `liturgical_reading` | Normalised reading rows (type, reference, label, position), FK → day with cascade |

`MIGRATION_1_2` adds the two liturgical cache tables without touching bookmarks or reading state.
`MIGRATION_2_3` rebuilds `bookmarks` with the UUID and anchor columns, creates the unique anchor
index before copying, and inserts oldest-first with `INSERT OR IGNORE`, so duplicate anchors
collapse instead of aborting the upgrade. There is **no destructive fallback on upgrade**; the
only destructive path is the explicit downgrade fallback, because only app state lives here. See
[`docs/architecture.md`](docs/architecture.md).

### 6.3 External liturgical data (Daily Reading)

* Endpoint base: `https://cpbjr.github.io/catholic-readings-api/`
* `readings/{year}/{monthDay}.json` and `liturgical-calendar/{year}/{monthDay}.json`
* DTOs are mapped to domain models; **DTOs are never persisted** — only the normalised cache rows are.
* The API supplies *references*; the local corpus supplies the Bible text.

### 6.4 Bundled content packs

Content packs are data, not code. A pack lives under
`app/src/main/assets/packs/<pack-id>/` with a `manifest.json` declaring `id`, `type`
(`json`, `text`, `audio`, or `calendar`), `version`, `locale`, `licence`, `attribution`, optional
`territory`/`expiry`, a `checksum` map, and `files`.

`ContentPackManifestParser` rejects unknown fields, id/directory mismatches, path escapes,
non-allow-listed extensions, missing calendar territory/expiry, and expired packs.
`ContentPackLoader` verifies every declared SHA-256 and fails closed with a typed
`ContentPackException`. `LicenceManifestTest` loads every real pack with the production
parser/loader and fails on a missing licence/attribution, missing calendar territory/expiry, an
expired term, or a checksum mismatch; `RightsRegisterTest` requires a shipped rights row whose
`covers` includes every bundled pack id.

Packs ship inside the APK and are replaced only by an app update — no remote store, download
manager, or runtime replacement. **No packs ship in 1.3.0.** See
[`docs/content-packs.md`](docs/content-packs.md).

---

## 7. Corpus characteristics and known data quirks

These are **corpus facts**, verified against the real databases and intentionally *not* corrected
in the app:

| Observation | Handling |
|---|---|
| **John 1 starts at verse 1** after the prefix repair; **Psalm 92** is the only psalm with no verse-1 record. | Verified by `JohnChapterOneDataTest` and `PsalmRealDataTest`; the app applies no offset. |
| **23 chapters start after verse 1** in the source numbering (e.g. Daniel 6, Esther 5, Jeremias 3/4/5/17/50, Job 26/24, Psalm 92, Acts 2/4/7/8/15, Galatians 1, Revelation 13, 2 Corinthians 12, Luke 1/4, Matthew 27, Romans 1, Titus 1). | Rendered verbatim; `tools/corpus/audit_corpus.py` reports the full set. |
| **Two chapters have all headings anchored to verse 1** (John 5 and John 15). | Headings render at the source anchor; no guessed positions. Reported by `tools/corpus/audit_corpus.py`. |
| **`korintkarant1/2` have short display names** in the corpus. | Not silently corrected; display names are Capitalize-Cased only. |
| **`books.position` follows importer filename order**, not the Catholic canonical order. | Canonical ordering comes from the explicit `BibleCanonicalOrder`, not from `books.position`. |
| **Duplicate chapter numbers** (e.g. Job) and repeated/chapter-1 anomalies. | Chapters are addressed by `position`, never by `number`. |
| **SQL `snippet()` over `verses_fts`** is non-portable: the external-content FTS table is declared over a single `text_plain` column while the content table `verses` has 8 columns. | `snippet()` was removed; snippets are built in Kotlin. FTS5 `MATCH` is retained. |
| **Corpus HTML vocabulary** is small and irregular (spaced tags such as `<p align=center >`, malformed `font` colours such as `ff#0000`). | Covered by an explicit node model with a deterministic `Unknown` fallback that preserves content and emits diagnostics rather than discarding markup. |

---

## 8. Project structure

```text
PovitrPustok/
├── app/
│   ├── build.gradle.kts
│   ├── schemas/                         # exported Room schemas (v1–v3)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/{bible,licenses,packs}/  # corpus (git-ignored), licence texts, pack manifests
│       │   ├── java/com/biblia/povitrpustok/
│       │   │   ├── BibleApplication.kt  # @HiltAndroidApp
│       │   │   ├── MainActivity.kt
│       │   │   ├── core/{database,html,navigation,pack,result}
│       │   │   ├── data/{app,bible,di,liturgy,preferences}
│       │   │   ├── domain/{model,reference,repository}
│       │   │   ├── feature/{about,bookmarks,books,common,dailyreading,reader,reference,search,settings}
│       │   │   └── ui/{theme,components, AppVersion, HomeScreen, HomeViewModel}
│       │   └── res/                     # strings, themes, adaptive launcher icons
│       ├── test/                        # 542 JVM unit tests + HTML fixtures
│       └── androidTest/                 # 55 instrumented tests
├── docs/                                 # architecture, rights, content-pack, performance and release documentation
├── tools/{corpus,release}/               # corpus importer/audit, release packaging
├── gradle/libs.versions.toml            # version catalog
├── build.gradle.kts, settings.gradle.kts, gradle.properties
├── gradlew, gradlew.bat, gradle/wrapper/
├── LICENSE, NOTICE
└── README.md
```

---

## 9. Getting started

### Prerequisites

* **Android Studio** (recent stable) or a command-line Android SDK.
* **JDK 25** for the Gradle daemon (declared in `gradle/gradle-daemon-jvm.properties`; provisioned
  automatically by the Foojay toolchain resolver).
* **Android SDK Platform 36** (compileSdk/targetSdk) and platform tools.
* Network access on first build (Gradle downloads dependencies and the wrapper).

### Obtaining the corpus

The Konkani Bible text is **copyrighted** and is **not** distributed with this repository. Every
checkout must supply it locally, or CI must fetch it from a private artifact store. The tooling and
a checksum manifest live in [`tools/corpus/`](tools/corpus/README.md); the manifest contains only
hashes and counts, never scripture.

If you have lawful access to the source XML:

```bash
python3 tools/corpus/import_corpus.py --source /path/to/private/xml \
  --out app/src/main/assets/bible
python3 tools/corpus/generate_manifest.py
python3 tools/corpus/generate_manifest.py --check
```

For an authorised local build, place the 73 `.sqlite` files in
`app/src/main/assets/bible/` and run the manifest check. Never commit, redistribute, or publish the
source XML or the SQLite files. A built APK may be distributed only with the rights holder's
permission.

Without the corpus, unit tests can be run with `-PskipCorpusTests`; the 149 corpus-dependent tests
are then reported as skipped. CI uses the same switch when the private-artifact secrets are
absent. See `tools/corpus/README.md` for the `CORPUS_ARCHIVE_*` secrets.

The file names must match `databaseAsset` in
[`BibleCatalog.kt`](app/src/main/java/com/biblia/povitrpustok/domain/model/BibleCatalog.kt)
exactly. The catalog is the single source of truth for book id ⇄ asset mapping.

Note that a built APK embeds the corpus in readable form; the private artifact store protects the
source, not the installed app. Ensure you have distribution permission before publishing an APK.

<details>
<summary>Required asset names (73)</summary>

**Tagged Old Testament (48):**
`Amos`, `Barukachem_Pustak`, `Daniel`, `Dusri_Somurt`, `Esther`, `Ezekiel`, `Ezrachem_Pustok`,
`Fuddareanchem_Pustok`, `Gitantlem_Git`, `Habakuk`, `Haggai`, `Hosea`, `Itihasachem_Dusrem_Pustok`,
`Itihasachem_Poilem_Pustok`, `Izaias`, `Jeremias`, `Jobachem_Pustok`, `Joel`, `Jonas`,
`Joshuachem_Pustok`, `Judit`, `Levi_Xastr`, `Lokgonnti`, `Makabeusachem_Dusrem_Pustok`,
`Makabeusachem_Poilem_Pustok`, `Malaki`, `Mhonnneo`, `Mika`, `Nahum`, `Nehemiasachem_Pustok`,
`Obadia`, `Razanchem_Dusrem_Pustok`, `Razanchem_Poilem_Pustok`, `Ruthachem_Pustok`,
`Samuelachem_Dusrem_Pustok`, `Samuelachem_Poilem_Pustok`, `Sirakachem_Pustok`,
`Stotranchem_Pustok`, `Suttka`, `Tobit`, `Upodexok`, `Utpoti`, `Vilap`, `Zannvaiechem_Pustok`,
`Zefania`, `Zekaria`, `tesalonikarank1`, `tesalonikarank2`

**Tagged New Testament (25):**
`dhormdutancho_itihas`, `efezkarank`, `filemonak`, `filipkarank`, `galasiekarank`, `hebrevank`,
`jakob`, `juanv`, `juanv1`, `juanv2`, `juanv3`, `juanvak_prokasovnni`, `judas`, `koloskarank`,
`korintkarant1`, `korintkarant2`, `lukas`, `mark`, `matev`, `pedru1`, `pedru2`, `romkarank`,
`tilak`, `timotak1`, `timotak2`

</details>

### Installing and updating (Obtainium)

Povitr Pustok is distributed through **GitHub Releases + Obtainium**; there is no Play Store or
F-Droid build.

1. Install Obtainium.
2. Add `https://github.com/AlexJoaquimPereira/PovitrPustok`.
3. The repository is private. Configure a GitHub token with read access (fine-grained token:
   **Contents: Read**) in Obtainium before refreshing.
4. Obtainium checks releases and offers updates. You can also download the APK from a release and
   verify it against the attached `SHA256SUMS`.

Requires Android 11 (API 30) or newer.

### Building

```bash
# Debug build
./gradlew :app:assembleDebug

# Release build (unsigned unless keystore.properties is present)
./gradlew :app:assembleRelease

# Unit tests
./gradlew :app:testDebugUnitTest

# Unit tests without the private corpus
./gradlew :app:testDebugUnitTest -PskipCorpusTests

# Compile instrumented tests (running them needs a device/emulator)
./gradlew :app:assembleDebugAndroidTest

# Lint
./gradlew :app:lintDebug

# Install on a connected device
./gradlew :app:installDebug
```

### Release signing

Release signing is configured from `keystore.properties` at the repository root. **This file and
all keystores are git-ignored and must never be committed.** Release APKs are signed with APK
Signature Scheme v3 (`minSdk` is 30, so every supported device verifies it).

```properties
storeFile=/absolute/or/relative/path/to/keystore.jks
storePassword=…
keyAlias=…
keyPassword=…
```

If the file is absent, the release signing config is simply left unset and the project still builds
(producing an unsigned release artifact). Follow [`docs/release-checklist.md`](docs/release-checklist.md)
before publishing; [`tools/release/package_release.sh`](tools/release/package_release.sh) builds the
signed APK/AAB, verifies the corpus payload, and writes `SHA256SUMS`. CI never publishes release
artifacts. The repository is private; Obtainium requires a GitHub token with read access.

---

## 10. Testing

| Suite | Count | Runner | Notes |
|---|---|---|---|
| JVM unit tests | **542** | JUnit4 + Robolectric | Run with `:app:testDebugUnitTest` |
| Instrumented tests | **55** | AndroidJUnitRunner | Compile with `:app:assembleDebugAndroidTest`; require an API 30+ device. On the API 35 emulator: 51 executed / 0 failed / 4 skips (3 FTS5-only + 1 network-dependent Daily Reading) |

34 unit-test classes use `RobolectricTestRunner` because they exercise real Android resources,
assets or Room. Room migrations are validated by `MigrationTestHelper` against the checked-in
`app/schemas/` JSON; the debug variant carries those schemas as test assets, the release APK does
not. The migration suite runs under `testDebugUnitTest`; `testReleaseUnitTest` is not a supported
schema-test target.

**Robolectric configuration**

* `app/src/test/resources/robolectric.properties` pins `sdk=35` (the provisioned
  `android-all-instrumented` jar; targetSdk 36 has no matching local jar).
* `app/build.gradle.kts` maps the `ROBOLECTRIC_USER_HOME` environment variable onto the test
  `user.home`, for hosts where `$HOME` is read-only. Robolectric resolves `android-all` jars from
  `$HOME/.m2` and writes a download lock there, so point it at a writable directory:

```bash
export ROBOLECTRIC_USER_HOME=/path/to/robolectric-home   # containing .m2/repository android-all jars
./gradlew :app:testDebugUnitTest
```

**Real-content tests.** Most data-layer and reader tests run against the **real corpus assets**,
not synthetic fixtures, and assert semantic preservation rather than exact serialisation.

**Documented skips.** Corpus-dependent tests use `CorpusAvailabilityRule` and skip when
`-PskipCorpusTests` is set. `BibleDatabaseProviderTest` and `CorpusGateTest` additionally call
`Assume.assumeTrue(...)` to skip the FTS5-`MATCH` parity check under Robolectric (desktop SQLite
does not provide the same FTS5 build); `BibleSearchRepositoryTest` uses the same corpus guard for
its real-hit fallback test. On devices without FTS5 the three `BibleFtsInstrumentedTest` cases skip
as well; the search path is still covered by the scan fallback tests.

**Instrumented coverage.** `BibleFtsInstrumentedTest` verifies FTS5 on devices whose SQLite
provides it, including a full-path `MATCH` test. On builds without FTS5 these three tests skip;
the repository/search instrumented tests still run through the scan fallback.
`FirstUsePerformanceTest` logs the first-use baseline (Books, search, chapter); run it isolated
after clearing app data. See [`docs/performance.md`](docs/performance.md).

**Device gate (required).** The on-device FTS5 suite is not part of the JVM suite and cannot be
replaced by Robolectric:

```bash
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest   # API 30+; FTS5-only cases skip if the build lacks the module
```

CI must either provision such a device/emulator or require a documented pre-release device run.
Robolectric's `Assume` skip for FTS5 must never be counted as a passed `MATCH` test.

HTML regression fixtures under `app/src/test/resources/html/` are extracted verbatim from the real
corpus (James, Job, Esther, Leviticus, Psalms, Jeremias, 1 Kings, Genesis).

---

## 11. Build outputs

Artifacts are produced by Gradle under `app/build/outputs/` (never committed):

| Task | Output |
|---|---|
| `:app:assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` |
| `:app:assembleRelease` | `app/build/outputs/apk/release/app-release.apk` (signed only when `keystore.properties` exists; otherwise `app-release-unsigned.apk`) |
| `:app:bundleRelease` | `app/build/outputs/bundle/release/app-release.aab` |
| `tools/release/package_release.sh` | `app/build/outputs/release/<name>/` with the versioned APK/AAB, `SHA256SUMS`, and `BUILD-INFO.txt` |

Release builds are R8-optimized (`optimization.enable = true`): the signed APK is ≈**12.2 MB**
(down from ≈38.0 MB unminified). `app/build/outputs/mapping/release/mapping.txt` is kept locally
for deobfuscation; it is not committed.

The old `dist/` folder was removed in REPO-01: it held stale, divergent APKs that did not match the
current corpus. Build the artifact you need from source instead.

---

## 12. Known limitations and roadmap

**Release status.** 1.3.0 is the current development line: stable anchors and Room schema v3,
the edition seam, the bundled content-pack format, the rights register, and release hygiene. It
adds no new visible feature. Reading history and notes, English editions, the calendar, and BYOK
explanations remain later releases.

**Known limitations**

* The on-device FTS5 `MATCH` path is verified only on hardware whose SQLite includes FTS5.
  The API 35 emulator and many OEM builds do not, so their three FTS5-only tests skip; search there
  uses the scan fallback (SEARCH-04). This does not affect the shipped fallback behavior.
* Release-variant instrumentation is not runnable because R8 shrinks test-runner dependencies; the
  R8 release is covered by app-level smoke tests instead. Debug instrumentation remains the UI gate.
* The corpus is copyrighted and not committed. CI runs without it unless the private
  `CORPUS_ARCHIVE_*` secrets are configured; a locally built APK requires the private corpus.
* Lint reports 0 errors; warnings are dependency/target-SDK notices and Modifier-parameter style
  warnings.
* 23 chapters start after verse 1 in the source numbering, and John 5/15 headings are all anchored
  to verse 1. These are source quirks, deliberately not "fixed" — see
  [§7](#7-corpus-characteristics-and-known-data-quirks).

**Not implemented yet**

* Reading history and notes (planned 1.4). There is no use-case layer and there are no separate
  feature modules; 1.3.0 deliberately keeps the single `:app` module (see
  [`docs/architecture.md`](docs/architecture.md)).
* No content packs ship in 1.3.0; the annual calendar pack is planned for 1.5.

**Not implemented by design:** polling / background synchronisation for liturgical data
(no WorkManager scheduling), a general-purpose HTML engine, and any mutation or re-import of the
Bible corpus from inside the app.

---

## 13. Repository conventions

* **Layering is enforced** — UI → ViewModel → Repository → datasource. No UI database access, no
  Bible content in code.
* **The corpus is read-only** — never modified by the app or by migrations.
* **`.gitignore` excludes** build outputs, `.gradle`, `local.properties`, `keystore.properties`,
  `*.jks`, `*.keystore`, `google-services.json`, `.idea`, `.artifacts`, and the corpus
  (`*.sqlite`, `*.sql`).
* **Commit history** is granular: one logical change per commit, with a scoped subject
  (`feat(reader): …`, `test(data): …`, `chore(build): …`).

---

## 14. Documentation

* The authoritative engineering reference is
  **`Konkani_Bible_Android_Implementation_Plan.md`** (repository root). It defines the corpus
  schema, architecture rules, per-feature requirements, performance targets and the phase order.
  *Note: this working document is currently **not** tracked by git, so it will be absent from a
  fresh clone.*
* Section references of the form "implementation plan §N" in source KDoc point into that document.
* [`docs/architecture.md`](docs/architecture.md) records the 1.3.0 data foundations: Room
  schema v3 and migrations, stable verse anchors, the edition seam, the content-pack contract,
  and the single-module decision.
* [`docs/content-packs.md`](docs/content-packs.md) defines the bundled content-pack format, the
  licence/expiry gate, and the ship-in-APK lifecycle. No packs ship in 1.3.0.
* [`docs/rights.md`](docs/rights.md) is the rights register for every shipped or planned text,
  data set, and content pack; `RightsRegisterTest` enforces coverage and expiry.
* [`docs/performance.md`](docs/performance.md) records the F1-21 first-use baseline, budgets, and
  the parser-cache result.
* [`docs/release-checklist.md`](docs/release-checklist.md) and
  [`docs/release-notes-template.md`](docs/release-notes-template.md) define the GitHub
  Releases/Obtainium release procedure; [`docs/releases/`](docs/releases) holds dry-run records.
* Earlier releases are tagged in git (`v1.0.0` through `v1.2.4`); release notes are published in
  the corresponding GitHub releases.

---

## 15. License and corpus attribution

The application code is licensed under the **Apache License, Version 2.0**. See [`LICENSE`](LICENSE)
and [`NOTICE`](NOTICE); the license text is also bundled in the APK under `assets/licenses/` and is
shown in full on the in-app **Settings > About > Software licences > App licence** page.

**Bible corpus:** the Konkani Bible text, the source XML, and the derived SQLite databases are
© **Archdiocese of Goa and Daman**. All rights reserved by the rights holder. They are used with
the permission of the rights holder and are **not** covered by the Apache License. They are not
distributed with this repository; see [Obtaining the corpus](#obtaining-the-corpus). Any use or distribution of the
text — including inside a built APK — requires the Archdiocese's permission. The in-app
**Source edition** page carries this diocesan acknowledgement and copyright notice.

---

## 16. About and disclosure pages

Settings > About opens a landing page; every disclosure type has its own page under
`feature/about`, so the landing page never becomes a wall of mixed legal text.

**Required pages**

| Page | Content |
|---|---|
| Source edition | Diocesan acknowledgement and copyright for the Konkani corpus |
| API & data | Links to the source website, repository and licence for external data (Daily Reading) |
| Software licences | Third-party licence links, plus the entry to the App licence page with the complete Apache-2.0 text |
| Credits | Project page: maintainer GitHub link and API non-affiliation note. Specific source/API/licence acknowledgements live on their own pages. |

**Rules**

* Any new API, data source, licence or acknowledgement must get a **dedicated page** with a source
  link, its licence/attribution, and tests. Do not fold new disclosures into an existing page.
* Credits is a project page, not a second disclosure index. Do not repeat source-edition, API or
  licence text there; the About landing is the directory.
* The app's own licence page must **always show the complete licence text**, never a link or a
  summary.
* UI/UX research and Figma files were removed in 1.2.3 and must not be reintroduced.
