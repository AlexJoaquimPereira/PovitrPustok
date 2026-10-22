# Povitr Pustok — Konkani Catholic Bible for Android

An offline-first Android reader for a **73-book Konkani Catholic Bible** corpus, built with
Kotlin, Jetpack Compose and Material 3.

The app renders a corpus of per-book SQLite databases that were generated outside this
repository from the original XML sources. The corpus is treated as **immutable content**:
the app never re-imports, rewrites or "fixes" it.

| | |
|---|---|
| **Application ID / namespace** | `com.biblia.povitrpustok` |
| **Version** | 1.1.0 (versionCode 2) |
| **Min SDK** | 30 (Android 11) — FTS5 is used when the device SQLite provides it; a scan fallback covers builds that omit it |
| **Compile SDK / Target SDK** | 36 / 36 |
| **Language / UI** | Kotlin 2.2.10 · Jetpack Compose · Material 3 |
| **Architecture** | Layered: UI → ViewModel → Repository → datasource |
| **Tests** | 431 JVM unit tests (Robolectric 4.16.1; 2 FTS5 Assumes) · 30 instrumented tests |

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
15. [License](#15-license)

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
* One chapter at a time, composed from headings, verses and introduction blocks in source order.
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
* Bookmarks identify an exact location (`bookId` + `chapterId` + `verseId`), unique per location.
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

---

## 3. Screens and navigation

Navigation uses a single `NavController`/back stack (Navigation Compose). Routes are declared in
`core/navigation/AppDestination.kt` and wired in `PovitrPustokNavHost.kt`. Navigation arguments are
always compact identifiers (book id, chapter position, optional verse target) — never HTML or
content objects.

| Route | Screen | Arguments |
|---|---|---|
| `home` | Home launcher | — |
| `books` | Books catalogue | — |
| `books/{bookId}` | Chapter selection | `bookId` |
| `books/{bookId}/introduction` | Book introduction | `bookId` |
| `reader/{bookId}/{chapterPosition}?verseId=&verseStart=&verseEnd=` | Reader | `bookId`, `chapterPosition`, optional verse target / passage |
| `search` | Search | — |
| `reference` | Scripture reference entry | — |
| `daily-reading` | Daily Reading | — |
| `bookmarks` | Bookmarks | — |
| `settings` | *(declared, not implemented)* | — |

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

### Package responsibilities

| Package | Responsibility |
|---|---|
| `core/database` | Read-only per-book SQLite handle + asset-backed provider |
| `core/html` | Corpus HTML parser, node model, diagnostics, renderer, Compose surface |
| `core/navigation` | Route contract and navigation graph |
| `core/result` | `BibleDataException` hierarchy |
| `domain/model` | Pure domain models (books, chapters, verses, headings, liturgy, bookmarks) |
| `domain/reference` | Book-name matcher and Scripture reference parser (pure) |
| `domain/repository` | Repository interfaces (the domain boundary) |
| `data/app` | Room database, DAOs, entities, bookmark repository |
| `data/bible` | Bible/search/title repositories, reference resolver, snippet builder |
| `data/liturgy` | Catholic Readings API client, DTOs, mappers, cache-first repository |
| `data/di` | Hilt bindings |
| `feature/*` | One package per screen: `Screen`, `UiState`, `ViewModel` |
| `ui` | Home screen/ViewModel and the Material 3 theme |

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
| Testing | AndroidX Test Core, Coroutines Test | 1.7.0, 1.7.3 |
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

1. The book id is resolved against the static `BibleCatalog` (73 entries) — rendering the catalog
   never opens a database.
2. On first use the APK asset is copied lazily to `filesDir/bible/<BookId>.sqlite` through a
   `.sqlite.tmp` file and an atomic rename, so a partial copy is never exposed.
3. A `.version` marker holding the app's `versionCode` is written alongside; a missing, stale or
   partial copy is re-copied.
4. The database is opened with `OPEN_READONLY` and cached for the process lifetime.
5. `BibleBookNotFoundException` / `BibleDatabaseNotFoundException` / `CorruptBibleDatabaseException`
   are raised on the corresponding failures.

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

`AppDatabase` (`povitr_pustok_app.db`, **version 2**) holds only application state and is entirely
separate from the Bible assets.

| Table | Contents |
|---|---|
| `bookmarks` | Exact location (`bookId`, `chapterId`, `verseId`, verse range), unique per location |
| `reading_state` | Last-read location for *Continue reading* |
| `liturgical_day_cache` | Cached liturgical day, **UNIQUE** on `date` |
| `liturgical_reading` | Normalised reading rows (type, reference, label, position), FK → day with cascade |

`MIGRATION_1_2` adds the two liturgical cache tables without touching bookmarks or reading state,
and there is **no destructive fallback**.

### 6.3 External liturgical data (Daily Reading)

* Endpoint base: `https://cpbjr.github.io/catholic-readings-api/`
* `readings/{year}/{monthDay}.json` and `liturgical-calendar/{year}/{monthDay}.json`
* DTOs are mapped to domain models; **DTOs are never persisted** — only the normalised cache rows are.
* The API supplies *references*; the local corpus supplies the Bible text.

---

## 7. Corpus characteristics and known data quirks

These are **corpus facts**, verified against the real databases and intentionally *not* corrected
in the app:

| Observation | Handling |
|---|---|
| **John 1 starts at verse 2** (50 records; no verse 1 record). | Rendered exactly as the database contains; documented by `JohnChapterOneDataTest`. |
| **147 of 150 Psalms have no `verse_start = 1` record** (their first record is verse 2). | The app displays `verse_start` verbatim — there is no application-side offset. |
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
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/bible/            # 73 corpus databases (git-ignored)
│       │   ├── java/com/biblia/povitrpustok/
│       │   │   ├── BibleApplication.kt  # @HiltAndroidApp
│       │   │   ├── MainActivity.kt
│       │   │   ├── core/{database,html,navigation,result}
│       │   │   ├── data/{app,bible,di,liturgy}
│       │   │   ├── domain/{model,reference,repository}
│       │   │   ├── feature/{books,reader,search,reference,bookmarks,dailyreading}
│       │   │   └── ui/{theme, HomeScreen, HomeViewModel}
│       │   └── res/                     # strings, themes, adaptive launcher icons
│       ├── test/                        # 431 JVM unit tests + HTML fixtures
│       └── androidTest/                 # 30 instrumented tests
├── gradle/libs.versions.toml            # version catalog
├── build.gradle.kts, settings.gradle.kts, gradle.properties
├── gradlew, gradlew.bat, gradle/wrapper/
└── README.md
```

Reserved (currently empty) package directories exist in the working tree for planned work:
`core/util`, `domain/usecase`, `feature/{history,notes,settings}`, `ui/{components,typography}`.
Because git does not track empty directories, a fresh clone will not contain them.

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

Otherwise place a verified copy at `app/src/main/assets/bible/<BookId>.sqlite` and run the manifest
check. Without the corpus, unit tests can be run with `-PskipCorpusTests`; the 137 real-corpus
tests are then reported as skipped. CI uses the same switch when the private-artifact secrets are
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

### Building

```bash
# Debug build
./gradlew :app:assembleDebug

# Release build (unsigned unless keystore.properties is present)
./gradlew :app:assembleRelease

# Unit tests
./gradlew :app:testDebugUnitTest

# Compile instrumented tests (running them needs a device/emulator)
./gradlew :app:assembleDebugAndroidTest

# Lint
./gradlew :app:lintDebug

# Install on a connected device
./gradlew :app:installDebug
```

### Release signing

Release signing is configured from `keystore.properties` at the repository root. **This file and
all keystores are git-ignored and must never be committed.**

```properties
storeFile=/absolute/or/relative/path/to/keystore.jks
storePassword=…
keyAlias=…
keyPassword=…
```

If the file is absent, the release signing config is simply left unset and the project still builds
(producing an unsigned release artifact).

---

## 10. Testing

| Suite | Count | Runner | Notes |
|---|---|---|---|
| JVM unit tests | **431** | JUnit4 + Robolectric | Run with `:app:testDebugUnitTest` |
| Instrumented tests | **30** | AndroidJUnitRunner | Compile with `:app:assembleDebugAndroidTest`; require an API 30+ device. On the API 35 emulator: 27 executed / 0 failed / 3 FTS5-only skips (30 total) |

29 unit-test classes use `RobolectricTestRunner` because they exercise real Android resources,
assets or Room.

**Robolectric configuration**

* `app/src/test/resources/robolectric.properties` pins `sdk=35` (the provisioned
  `android-all-instrumented` jar; targetSdk 36 has no matching local jar).
* `app/build.gradle.kts` maps the `ROBOLECTRIC_USER_HOME` environment variable onto the test
  `user.home`, for hosts where `$HOME` is read-only. Robolectric resolves `android-all` jars from
  `$HOME/.m2` and writes a download lock there, so point it at a writable directory:

```bash
export ROBOLECTRIC_USER_HOME=/path/to/writable/dir   # containing .m2/repository android-all jars
./gradlew :app:testDebugUnitTest
```

**Real-content tests.** Most data-layer and reader tests run against the **real corpus assets**,
not synthetic fixtures, and assert semantic preservation rather than exact serialisation.

**Documented skips.** Two tests call `Assume.assumeTrue(...)`: `BibleDatabaseProviderTest` and
`CorpusGateTest`. Both skip the FTS5-`MATCH` parity check under Robolectric (desktop SQLite does
not provide the same FTS5 build). On devices without FTS5 the three `BibleFtsInstrumentedTest`
cases skip as well; the search path is still covered by the scan fallback tests.

**Instrumented coverage.** `BibleFtsInstrumentedTest` verifies FTS5 on devices whose SQLite
provides it, including a full-path `MATCH` test. On builds without FTS5 these three tests skip;
the repository/search instrumented tests still run through the scan fallback.

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

Release builds are R8-optimized (`optimization.enable = true`): the signed APK is ≈**12.2 MB**
(down from ≈38.0 MB unminified). `app/build/outputs/mapping/release/mapping.txt` is kept locally
for deobfuscation; it is not committed.

The old `dist/` folder was removed in REPO-01: it held stale, divergent APKs that did not match the
current corpus. Build the artifact you need from source instead.

---

## 12. Known limitations and roadmap

**Known limitations**

* The on-device FTS5 `MATCH` path is verified only on hardware whose SQLite includes FTS5.
  The test POCO M2 Pro (API 31) does not, so its three FTS5-only tests skip; search there uses the
  scan fallback (SEARCH-04). This does not affect the shipped fallback behavior.
* Release-variant instrumentation is not runnable because R8 shrinks test-runner dependencies; the
  R8 release is covered by app-level smoke tests instead. Debug instrumentation remains the UI gate.
* The corpus is copyrighted and not committed. CI runs without it unless the private
  `CORPUS_ARCHIVE_*` secrets are configured; a locally built APK requires the private corpus.
* Lint reports style warnings ("Modifier parameter should be the first optional parameter"); there
  are no lint errors.
* John 1 starts at verse 2, and 147 Psalms lack a verse-1 record. This is source data,
  deliberately not "fixed" — see [§7](#7-corpus-characteristics-and-known-data-quirks).

**Planned (reserved packages, not yet implemented)**

* Reading history (`feature/history`)
* Notes (`feature/notes`)
* Settings — typography, font size, theme override (`feature/settings`)
* Shared UI components and a standalone typography module (`ui/components`, `ui/typography`)
* Use-case layer (`domain/usecase`) and core utilities (`core/util`)

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
* Earlier releases are tagged in git (`v1.0.0`, `v1.1.0`). The 1.1.0 notes are published in the
  corresponding GitHub release.
* UI/UX research and prioritized, non-breaking improvement proposals:
  [`UI_UX_RESEARCH.md`](UI_UX_RESEARCH.md).
* Figma plan (20-day roadmap, guardrails, structural wireframes):
  [`PovitrPustok_UIUX_Plan_1.2.0.fig`](PovitrPustok_UIUX_Plan_1.2.0.fig).

---

## 15. License and corpus attribution

No license file is included in this repository. All rights are reserved by the repository owner
unless a license is added.

**Bible corpus:** the Konkani Bible text, the source XML, and the derived SQLite databases are
© **Archdiocese of Goa and Daman**. All rights reserved by the rights holder. They are not
distributed with this repository; see [Obtaining the corpus](#obtaining-the-corpus). Any use or
distribution of the text — including inside a built APK — requires the Archdiocese's permission.
