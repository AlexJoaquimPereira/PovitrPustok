# Architecture and 1.3.0 data foundations

Status: 1.3.0 design record (F1-22), 2026-10-29. [`README.md`](../README.md) is
the product and architecture reference; this page records the cross-cutting
contracts that landed in 1.3.0 so later releases can build on them without
re-deriving the rules.

## Layering

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

The corpus is immutable content: the app never re-imports, rewrites, or
migrates the Bible databases. Only application state lives in Room. Compose and
ViewModels never touch SQLite or Room directly.

## Room schema v3

`AppDatabase` (`povitr_pustok_app.db`) is version **3** with `exportSchema =
true`. The checked-in schemas are
`app/schemas/com.biblia.povitrpustok.data.app.AppDatabase/{1,2,3}.json`; the
debug variant carries them as test assets and `MigrationTestHelper` validates
the real migration path against them.

| Table | Contents |
|---|---|
| `bookmarks` | App-owned UUID `id`; stable anchor (`bookId`, `chapterPosition`, `verseStart`, `verseEnd`, `startOffset`, `endOffset`); display `chapterNumber`; `createdAt`, `updatedAt`, `deletedAt`; unique index on the anchor |
| `reading_state` | Single row (id = 1) with `bookId`, `chapterPosition`, `updatedAt` |
| `liturgical_day_cache` | Cached liturgical day, **UNIQUE** on `date` |
| `liturgical_reading` | Normalised reading rows (type, reference, label, position), FK → day with cascade |

Migrations:

* `MIGRATION_1_2` adds the two liturgical cache tables and leaves bookmarks and
  reading state untouched.
* `MIGRATION_2_3` rebuilds `bookmarks` with the UUID and anchor columns. It
  creates the unique anchor index before copying, inserts with `INSERT OR
  IGNORE` ordered oldest-first, and therefore collapses duplicate anchors
  instead of aborting the upgrade. `createdAt` is preserved, `updatedAt` starts
  at `createdAt`, `deletedAt` is `NULL`, and offsets start at 0.
* There is **no destructive fallback on upgrade**. The only destructive path is
  `fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)`: installing
  an older build over a newer database must not crash, and only app state
  (bookmarks, reading position, liturgical cache) lives there. The corpus is
  separate and immutable.

`AppDatabaseMigrationTest` covers 1→2, 2→3, duplicate-anchor deduplication, and
the production builder wiring to v3.

## Stable verse anchors

Corpus row ids (`chapters.id`, `verses.id`) change when the assets are
regenerated, so user-owned records never key by them.

`VerseAnchor` (`domain/model/VerseAnchor.kt`) is a pure value type:

```text
VerseAnchor(
    bookId, chapterPosition,
    verseStart, verseEnd = verseStart,
    startOffset = 0, endOffset = 0
)
```

It validates a non-blank book id, positive chapter position and verse start, a
non-decreasing range, and non-negative/non-decreasing offsets. Offsets are
reserved for future rendered-text annotations; bookmark creation uses 0/0.

`Bookmark` / `BookmarkEntity` add an app-owned UUID `id`, the anchor columns,
a display-only `chapterNumber`, `createdAt`, `updatedAt`, and a nullable
`deletedAt` tombstone reserved for the future export/merge format. The unique
index is the anchor, not a corpus row id. `BookmarkDao` inserts with
`OnConflictStrategy.IGNORE`, deletes by id or anchor, observes newest-first,
and answers `isBookmarked` by anchor.

Reading state already used `bookId` + `chapterPosition` + `updatedAt`; v3
leaves it unchanged.

Reader navigation uses the anchor's book id, chapter position, and
`verseStart` as `targetVerseNumber`; the bookmark destination carries no corpus
row ids. `BookmarkDestinationTest` proves this. `BookmarksViewModelTest`,
`ReaderBookmarkIntegrationTest`, and `ReaderViewModelTest` cover add/remove,
target coexistence, and reading-state persistence against the anchor.

## Edition seam

`Edition` (`domain/model/Edition.kt`) declares `id`, `languageTag`, `label`,
`assetPattern` (`bible/{bookId}.sqlite`), `canon`, and `licence`.

`BibleEditions` ships exactly one edition, **Konkani (Romi)** (`konkani-romi`,
`kok-Latn`, 73-book canon). `BibleEditions.DEFAULT` is Konkani. `byId` returns
`null` for an unknown id; the provider throws `BibleEditionNotFoundException`
rather than silently falling back to another edition.

`BibleCatalog` is edition-scoped: `byId(bookId, editionId)` returns a book only
when the id is in that edition's canon. `AssetBibleDatabaseProvider` keys open
handles by `(editionId, bookId)` and includes the edition in the copy path
(`filesDir/bible/<editionId>/` for non-default editions), while keeping the
pre-seam `filesDir/bible/` path for Konkani so existing users do not recopy the
corpus.

The Reader and Search routes declare an optional `editionId` query parameter
defaulting to `konkani-romi`; existing callers that omit it are unchanged.
Edition tests: `BibleEditionTest`, `AssetBibleDatabaseProviderEditionTest`,
`BibleRepositoryEditionTest`.

## Content packs

Packs are bundled data, never code. The directory layout is
`app/src/main/assets/packs/<pack-id>/manifest.json` plus declared payloads.

`ContentPack` / `ContentPackType` (`domain/model/ContentPack.kt`) support
`json`, `text`, `audio`, and `calendar` payloads. A manifest declares `id`,
`type`, `version`, `locale`, `licence`, `attribution`, optional `territory` and
`expiry`, a `checksum` map, and `files`.

Validation is strict and fail-closed (`ContentPackManifestParser`,
`ContentPackLoader`):

* unknown top-level fields are rejected;
* the manifest id must match its directory;
* payload paths are relative, stay inside the pack, and use an extension
  allowed by the type — executable/archive content is never accepted;
* `files` and `checksum` must describe exactly the same paths with lowercase
  SHA-256 digests;
* calendar packs require a territory and expiry, and an expired pack is
  rejected at parse time;
* a missing or mismatched checksum aborts the load with a typed
  `ContentPackException`.

`LicenceManifestTest` is the build-time gate: it loads every real pack with the
production parser and loader and fails on a missing licence/attribution, missing
calendar territory/expiry, an expired term, or a checksum mismatch.
`RightsRegisterTest` additionally requires a shipped row in
[`docs/rights.md`](rights.md) whose `covers` includes every bundled pack id.

Packs ship inside the APK and are replaced only by an app update. There is no
remote store, download manager, resumable download, or runtime replacement; a
calendar pack is regenerated yearly before its expiry. **No packs ship in
1.3.0.** The format and lifecycle are documented in
[`docs/content-packs.md`](content-packs.md).

## Module boundary

1.3.0 stays a single `:app` module (F1-23). Do not modularize for its own sake.
Revisit only when incremental build exceeds roughly 90 seconds, a subsystem
needs its own runtime/test (calendar generator, packs), or two developers work
in parallel. The likely first extractions are `:core:content` (packs +
licences) and `:feature:liturgy` (calendar); keep the reader in `:app` until
annotations force a split.

## Verification map

| Contract | Tests |
|---|---|
| Room v3 schema and migrations | `AppDatabaseMigrationTest`, `AppDatabaseTest` |
| Stable anchors and bookmark identity | `BookmarkDestinationTest`, `BookmarksViewModelTest`, `ReaderBookmarkIntegrationTest`, `ReaderViewModelTest` |
| Edition seam | `BibleEditionTest`, `AssetBibleDatabaseProviderEditionTest`, `BibleRepositoryEditionTest` |
| Content-pack format and licence gate | `ContentPackTest`, `ContentPackManifestParserTest`, `ContentPackLoaderTest`, `LicenceManifestTest` |
| Rights register | `RightsRegisterTest` |
