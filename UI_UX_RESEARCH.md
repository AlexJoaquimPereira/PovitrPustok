# Povitr Pustok — UI/UX Research Audit

**Type:** read-only design and usability research
**Repository:** `/home/alex/AndroidStudioProjects/PovitrPustok`
**Commit inspected:** `3cdd5a4` (`release: 1.1.0`)
**Target device context:** POCO M2 Pro, Android 11 / API 31, MIUI 14, 1080 × 2400 px (≈393 × 873 dp at ~440 dpi), gesture or 3-button navigation
**App context:** 73-book Konkani Catholic Bible, English UI, Material 3 + Jetpack Compose, offline corpus, AMOLED dark theme, system font scaling enabled
**Deliverable:** this file only. No app code, resources, tests, or Gradle files were changed.

---

## 0. Executive summary

The implementation is disciplined and the product is functional, but the UI is still at the "utility screen" stage. The two most valuable conclusions from this audit are:

1. **The corpus itself is a visual system input that the UI currently ignores.** 3,257 of 3,258 heading records and 1,620 of 34,272 verse records contain hard-coded `<font color>` spans. The renderer reproduces those colors literally (`BibleHtmlRenderer.kt:94–98`). In light mode, red (`#FF0000`, 3.82:1) and green (`#00B050`, 2.74:1) headings fail WCAG AA. In dark mode, navy (`#021A56`, 1.28:1), blue (`#0000FF`, 2.44:1), and black (`#000000`, 1.00:1) headings effectively disappear on the AMOLED background. This is a 1–2 day presentation-only fix with an outsized readability payoff.
2. **The Reader is the product, and its chrome is the weakest part.** There is no back arrow; the top-left arrow means "previous chapter" and is disabled at chapter 1. Chapter navigation, the chapter picker, and the bookmark action all sit in a fixed 64 dp top bar on a 6.67-inch phone. Selection/target/bookmark states composite to 1.05–1.18:1 and are nearly invisible in both themes. The reading column has no maximum measure, so it is ≈38 characters wide in portrait, ≈90 in landscape, and ≈19 at 200 % font scale.

The first week of work should be: fix corpus color mapping, make Home inset/scroll safe, unblock the Books list, give the Reader a back action and a thumb-zone chapter bar, and make its typography and state feedback legible. Settings/About can follow as a single well-scoped destination.

**What is genuinely good and should not be "redesigned away":** the single-authoritative reader content model (`ReaderContentItem`), position-based navigation (it survives Job's duplicate chapter numbers), the canonical 73-book order, the clean layered architecture, the restrained palette, and the existing route/bookmark contracts. The code gives a designer unusually safe ground to change presentation.

---

## 1. Scope, method, and hard constraints

### 1.1 What was inspected

| Area | Files |
|---|---|
| Home + theme | `ui/HomeScreen.kt`, `ui/HomeViewModel.kt`, `ui/theme/{Color,Theme,Type,Shapes}.kt` |
| Navigation | `core/navigation/AppDestination.kt`, `core/navigation/PovitrPustokNavHost.kt`, `MainActivity.kt` |
| Books | `feature/books/{BooksScreen,BooksViewModel,BooksUiState,BooksGrouping,BookDisplayName,BookChaptersScreen,BookChaptersViewModel,BookChaptersUiState,BookIntroductionScreen,BookIntroductionViewModel,BookIntroductionUiState}.kt` |
| Reader | `feature/reader/{ReaderScreen,ReaderComponents,ReaderContentBuilder,ReaderContentItem,ReaderUiState,ReaderViewModel,PsalmPoetry,SwipeNavigation}.kt` |
| Search | `feature/search/{SearchScreen,SearchResultItem,SearchUiState,SearchViewModel,BibleSearchQueryParser}.kt` |
| Reference | `feature/reference/{ReferenceEntryScreen,ReferenceEntryUiState,ReferenceEntryViewModel}.kt` |
| Bookmarks | `feature/bookmarks/{BookmarksScreen,BookmarksUiState,BookmarksViewModel}.kt` |
| Daily Reading | `feature/dailyreading/{DailyReadingScreen,DailyReadingUiState,DailyReadingViewModel}.kt` |
| Shared | `feature/common/ChapterLabels.kt`, `core/html/{BibleHtmlText,BibleHtmlRenderer,BibleHtmlParser}.kt` |
| Resources | `res/values/strings.xml`, `values/themes.xml`, `values-night/themes.xml`, `AndroidManifest.xml` |
| Tests | `app/src/androidTest/.../ui/HomeNavigationUiTest.kt`, `AppDestinationTest`, reader/search/bookmark unit tests, `ThemeColorsTest` |
| Corpus (read-only SQL) | `app/src/main/assets/bible/*.sqlite` — counts, color occurrences, heading/verse structure |

The UI + navigation + view-model layer is ≈3.6 k lines; every screen and shared component listed above was read in full, not sampled.

### 1.2 Method

1. **Static inspection** of 100 % of Compose screens, shared components, theme, resources, and navigation.
2. **Behavioural inspection** of the state that each screen can be in (loading / empty / error / content / selection / passage), including state fields that are computed but never rendered (e.g. `ReaderUiState.targetMissing`).
3. **Corpus inspection** with read-only `sqlite3` queries against the bundled assets: total headings/verses, `<font color>` occurrences, chapter heading structure, poetry alignment, duplicate chapter numbers.
4. **Contrast measurement** using the WCAG 2.1 relative-luminance formula, including alpha compositing for the reader's state tints. All ratios quoted below were computed, not estimated.
5. **Reading-measure measurement** using the actual Noto Serif Regular metrics at 19 sp against the real POCO width (393 dp) and the app's 20 dp reader padding.
6. **Test-contract inspection**: which strings, content descriptions, routes, and semantics the existing tests depend on.

### 1.3 What was not done (honest limitations)

- No APK was built or run, and no device screenshots were captured. Findings are code- and corpus-derived; device rendering (MIUI font substitution, AMOLED gamma, gesture behaviour) must be confirmed during the validation phase.
- No participant testing was conducted; the usability script in §8 is a plan, not results.
- Performance figures (73-database first scan) are inferred from the code path and corpus size, not measured on the POCO.

### 1.4 Hard constraints (non-negotiable for any redesign)

1. **No functional regressions.** All existing destinations, back-stack behaviour, retry paths, bookmark/reading-state persistence, and search fallback behaviour must keep working.
2. **Preserve routes and arguments exactly.** `home`, `books`, `books/{bookId}`, `books/{bookId}/introduction`, `reader/{bookId}/{chapterPosition}?verseId=&verseStart=&verseEnd=`, `search`, `reference`, `daily-reading`, `bookmarks`. `AppDestinationTest` asserts the exact strings and that there are **9 distinct routes** (`AppDestinationTest.kt:66–79`). Adding Settings/About requires a deliberate test update, not a silent one.
3. **Preserve database contracts.** No schema change, no new Room entity for visual work, no HTML rewriting, no changes to corpus assets, no change to bookmark identity (`bookId + chapterId + verseId`) or to position-based navigation.
4. **Preserve accessibility semantics already present.** Back arrows expose `contentDescription = "Back"`; bookmark/delete/clear icons have descriptions; book rows expose `Role.Button`; reader verses are clickable and therefore merge their descendants.
5. **Preserve test-visible strings unless deliberately migrated to test tags.** See §9 for the exact list. New tests should use test tags; existing text-based tests must not be broken by a visual change.
6. **No new dependencies.** No Material Icons Extended, no Accompanist, no DataStore, no adaptive-navigation library, no image-loading library, no new font dependency. Vector drawables, `SharedPreferences`, existing Compose/M3/Navigation/Hilt/Room/Retrofit APIs, and bundled font assets are allowed.
7. **Presentation only.** Do not alter `BibleHtmlRenderer` output (its unit tests assert exact text and span colors), do not renumber or "correct" the corpus, do not flatten poetry `<br/>` structure.
8. **The reader remains the only reader.** Do not introduce a second reading surface or duplicate content models.

### 1.5 Effort and risk definitions

| Label | Meaning |
|---|---|
| **S** | ≤ 1 developer-day, presentation-only |
| **M** | 2–5 developer-days, touches shared components or interaction |
| **L** | > 5 developer-days, new state/persistence or multiple screens |
| **Risk low** | Visual only; no route/DB/test-contract change |
| **Risk medium** | Changes shared components, test-visible semantics, or an interaction model |
| **Risk high** | Changes a state/data contract or navigation identity |

---

## 2. Current UI architecture and token inventory

### 2.1 Screen graph

```
Home (root)
├── Books ──► Book Chapters ──► Reader
│             └── Book Introduction
├── Search ──► Reader (verse target)
├── Reference ──► Reader (verse target or passage)
├── Bookmarks ──► Reader (passage)
└── Daily Reading ──► Reader (passage)
```

All screens are pushed onto one `NavHost` (`PovitrPustokNavHost.kt:30–33`). There is no bottom navigation, drawer, search action outside Home, or Settings/About. Chapter changes inside the Reader do **not** push routes; the back stack returns to the previous screen, not the previous chapter. That is a legitimate design choice, but it makes the Reader's own chrome more important, not less.

### 2.2 Theme tokens actually in use

`Theme.kt` sets 24 color roles per mode; everything else falls back to Material 3 baseline values. `Color.kt` defines a restrained blue/near-black palette. The AMOLED dark background is deliberate and guarded by `ThemeColorsTest` (`DarkBackground == #000000`, `LightPrimary == #0B57D0`). Do not change those existing values; add roles instead.

| Role | Light | Dark | Notes |
|---|---|---|---|
| background | `#FAF9FF` | `#000000` | Existing; keep |
| surface | `#FFFFFF` | `#000000` | Existing; keep |
| onBackground / onSurface | `#1A1B20` | `#E4E1E9` | Existing |
| onSurfaceVariant | `#44464F` | `#C4C6D0` | Existing |
| primary | `#0B57D0` | `#ADC6FF` | Existing |
| primaryContainer | `#D8E2FF` | `#004494` | Existing; too weak for state surfaces at low alpha |
| secondaryContainer | `#DEE1F9` | `#414559` | Existing; used for bookmark tint |
| tertiary | `#3F6550` | `#A5CFB4` | Existing; misused as search "warning" |
| error | `#BA1A1A` | `#FFB4AB` | Existing |
| surfaceContainer* | *baseline M3* | *baseline M3* | Not defined; purple-neutral baseline values |
| surfaceTint / inverseSurface / scrim | *baseline M3* | *baseline M3* | Not defined |

### 2.3 Typography actually in use

`Type.kt` overrides only four styles (`bodyLarge`, `bodyMedium`, `titleLarge`, `headlineMedium`). The app uses nine styles. The other five are Material defaults (Roboto), which produces a mixed typographic voice: scripture and page titles are Noto Serif; section headers, reader headings, verse numbers, metadata, and buttons are Roboto.

| Style | Current definition | Used by (examples) |
|---|---|---|
| `headlineMedium` | Serif 28/36 SemiBold | Home title; chapter numeral header |
| `titleLarge` | Serif 22/30 SemiBold | Daily celebration name |
| `titleMedium` | **Roboto 16/24 Medium (default)** | Testament header; reader section headings; Daily date |
| `titleSmall` | **Roboto 14/20 Medium (default)** | Search result book; bookmark book; Daily reading type |
| `bodyLarge` | Serif 17/28 | Book rows; introduction body; Daily reference |
| `bodyMedium` | Roboto 14/22 + 0.25 letterSpacing | Search snippets; empty states; metadata |
| `bodySmall` | **Roboto 12/16 (default)** | English book name; reference numbers; reader subtitle |
| `labelMedium` | **Roboto 12/16 Medium (default)** | Verse numbers |
| `labelSmall` | **Roboto 11/16 Medium (default)** | Copyright notice |
| `BibleTextStyle` | Serif 19/32 | Scripture in the reader |

### 2.4 Component inventory

There are effectively no shared UI components. Each screen re-implements its own top bar, loading spinner, empty text, error text, and list row:

- Top bars: `BooksScreen`, `SearchScreen`, `BookChaptersScreen`, `BookIntroductionScreen`, `ReaderScreen`, `ReferenceEntryScreen`, `BookmarksScreen`, `DailyReadingScreen` each construct `TopAppBar` independently. Back arrows are copied five times; Books and Search have none.
- Loading: six full-screen `CircularProgressIndicator` variants; no skeletons, no inline progress.
- Empty/error: nine ad-hoc `Text` blocks; only some have retry; only one (Book Introduction error) has no retry.
- Rows: `BookListItem`, `SearchResultItem`, `BookmarkRow`, `ReadingRow`, and ambiguous-reference rows all differ in padding, typography, and affordance.
- No `SnackbarHost`, bottom sheet, chip, segmented control, divider, or shared `EmptyState`/`ErrorState` exists anywhere in the app.

### 2.5 Corpus presentation facts (read-only SQL)

| Fact | Value | Consequence |
|---|---|---|
| Books | 73 (46 OT + 27 NT) | 73 rows in Books; 73-item dropdown in Search filter |
| Chapters | ≈1,300 | Chapter grid; 150 in Psalms |
| Verses | 34,272 | Reader virtualization is necessary |
| Heading records | 3,258 | Headings are the reader's navigational hierarchy |
| Headings with `<font color>` | **3,257** | Color is effectively the heading system |
| Heading color spans | `#FF0000` 3,811 · `#00B050` 1,332 · `#021A56` 367 · `#0000FF` 279 · `#0070C0` 108 · `#000000` 1 | Several fail contrast; see F-02 |
| Verses with `<font color>` | **1,620** (4.7 %) | Red-letter words of Jesus, plus 9 blue and 1 navy |
| Verse color spans | `#FF0000` 1,754 · `#0000FF` 9 · `#021A56` 1 | Light-mode red fails AA |
| Intro blocks with color | 104 red + 5 blue spans | Introductions inherit the same problem |
| Chapter label in corpus | `Ovesvor N` headings are green (`#00B050`) | The app also draws its own large chapter numeral → duplication |
| Poetry | Psalms verses use `<p align=center>` + `<br/>` | Centering is correct; stanza spacing and measure are not tuned |
| Duplicate chapter numbers | Job (and similar) | Position-based navigation and `(1)`/`(2)` presentation labels are correct |

Contrast table (computed, WCAG 2.1):

| Corpus color | On light paper `#FAF9FF` | On white | On AMOLED black | Verdict |
|---|---|---|---|---|
| `#FF0000` | 3.82:1 | 4.00:1 | 5.25:1 | **Fails AA in light** |
| `#00B050` | 2.74:1 | 2.87:1 | 7.33:1 | **Fails AA in light** |
| `#021A56` | 15.61:1 | 16.35:1 | **1.28:1** | **Invisible in dark** |
| `#0000FF` | 8.21:1 | 8.59:1 | **2.44:1** | **Fails dark** |
| `#0070C0` | 4.92:1 | 5.15:1 | **4.08:1** | Borderline light, fails dark |
| `#000000` | 20.06:1 | 21.00:1 | **1.00:1** | **Invisible in dark** |

---

## 3. Screen-by-screen inventory and critique

### 3.1 Home — `home`

**Implementation.** A `Surface` fills the screen; a centered `Column` with 32 dp side padding holds the app name (`headlineMedium`) and six full-width filled `Button`s: Bible, Search, Bookmarks, Continue reading (conditional), Go to reference, Daily Reading (`HomeScreen.kt:45–90`). The copyright notice is a `labelSmall` text pinned to the bottom center with 16 dp padding (`HomeScreen.kt:92–101`). `HomeViewModel` observes `ReadingState` and clears stale book ids (`HomeViewModel.kt:28–41`).

**States.** No loading state (good: Home never opens a database). "Continue reading" is hidden when there is no reading state. There is no error or empty state because there is no data load.

**Critique.**

- **No window insets.** `enableEdgeToEdge()` is enabled (`MainActivity.kt:16`) but Home uses a plain `Surface`/`Box`, not `Scaffold`. The bottom copyright sits 16 dp above the window bottom, i.e. underneath the gesture pill and, with 3-button navigation, underneath a 48 dp navigation bar. On MIUI with 3-button nav the notice is partially hidden.
- **No scrolling.** The column is vertically centered and fixed. At 393 × 873 dp portrait with 1× text it fits; at 200 % text it is tight; in landscape (≈393 dp of height) the content is taller than the viewport, so the first/last destinations are clipped and unreachable. The notice overlays the buttons because it is a sibling in the same `Box`.
- **No hierarchy.** Six identical filled buttons give every destination the same weight. Material guidance is one primary action per screen; here there are six. "Bible" is also semantically vague inside a Bible app, and "Go to reference" is library jargon.
- **No identity or context.** There is no subtitle explaining that this is the Konkani Catholic Bible, no book/chapter context for "Continue reading" (even though `ReadingState.updatedAt` exists), and no visual language beyond a serif title. The screen reads like a developer menu.
- **Attribution is visually buried.** The license notice is the smallest text in the app and is not tappable, while About/Settings do not exist.

**Keep.** The instant, offline, no-spinner Home; the graceful hiding of a stale "Continue reading"; the exact app name; the presence of the corpus notice.

---

### 3.2 Books — `books`

**Implementation.** `Scaffold` + `TopAppBar` titled "Books" with no navigation icon (`BooksScreen.kt:51–54`). While `isLoading`, a centered `CircularProgressIndicator` fills the screen (`:55–61`). Otherwise a `LazyColumn` groups the 73 books by testament using the explicit canonical order (`BooksGrouping.kt:20–27`) and renders a non-sticky `TestamentHeader` plus `BookListItem` rows (`BooksScreen.kt:62–77`). Each row shows the DB-sourced local name (Capitalize-Cased) on the left and the English name in parentheses on the right, both `maxLines = 1` (`:101–133`).

**States.** Loading (full-screen spinner) and content. There is **no error state and no retry**, and `BooksUiState` has no error field (`BooksUiState.kt:7–10`).

**Critique.**

- **Blocking first load.** `BooksViewModel` waits for `loadAllDisplayNames()` before setting `isLoading = false` (`BooksViewModel.kt:26–30`). That call opens every one of the 73 SQLite databases and reads `introduction_blocks` (`BookTitleRepositoryImpl.kt:58–69`). On a POCO M2 Pro this is a cold-start scan behind a blank screen. If one asset is missing or unreadable, the coroutine throws; nothing catches it, so the spinner never resolves. Builds without the private corpus (documented in the README) fail exactly this way.
- **No back arrow.** Books is a pushed destination but looks like a root. Chapters, Bookmarks, Reference, and Daily Reading all have back arrows; Books and Search are the exceptions.
- **Section headers look like links.** They use `titleMedium` in `primary` blue (`BooksScreen.kt:88–90`), which is the same color used for interactive text on the Reference screen. They are not sticky, so after scrolling into a testament the user loses the section label.
- **Row layout is fragile at scale.** The local name takes `weight(1f)` and the English name is intrinsic-width on the right (`:116–131`). At 200 % font scale the English name consumes roughly half the row and the primary name ellipsizes to a few characters. The right-aligned parenthetical also reads like an afterthought rather than a translation aid.
- **No fast navigation or search.** 73 rows ≈ five screens; there is no A–Z index, sticky header jump, or search action in the top bar.
- **No chapter/introduction affordance.** The row does not indicate whether a book has an introduction, how many chapters it has, or whether it was recently read.

**Keep.** Canonical ordering, DB-sourced titles with capitalization, single-line ellipsis (no overlap), the full-row click target, and the `Role.Button` semantics.

---

### 3.3 Book Chapters — `books/{bookId}`

**Implementation.** `TopAppBar` with the catalog display name and a back arrow (`BookChaptersScreen.kt:61–74`). `LaunchedEffect(bookId)` loads the chapter list (`:58`). States: loading spinner, error text + Retry, content (`:77–121`). Content is an "Introduction" `TextButton` followed by an adaptive grid of `OutlinedButton` chapter cells with `minSize = 72.dp` (`:100–119`). Duplicate chapter numbers get presentation-only `(1)`/`(2)` suffixes (`ChapterLabels.kt:14–24`).

**States.** Loading, error+retry, content. An empty chapter list renders only the Introduction button and a blank area — no empty message.

**Critique.**

- **Cell size math is wrong.** At 393 dp width with 16 dp content padding and 8 dp gaps, a cell is ≈84 dp wide. `OutlinedButton` uses M3's default 24 dp horizontal content padding, leaving ≈36 dp for text. "150" fits only barely; "150 (1)" (Job duplicate) wraps or clips; at 200 % font scale even "150" cannot fit. The M3 button minimum height is 40 dp, below the 48 dp touch-target requirement.
- **The Introduction entry is a bare text button** above the grid. It looks like a link, has no icon or chevron, and disappears visually against the grid. If a book has no introduction, tapping it leads to a dead-end "No introduction available." state.
- **No reading context.** The last-read chapter is not marked, there is no "Continue at chapter N", and for Psalms (150 chapters) there is no section jump. The grid is a long uniform scroll.
- **Title inconsistency.** The top bar uses `BibleCatalog.displayName` (e.g. "Fuddareanchem Pustok"), whereas the Books list shows the DB title ("Fuddareañachem Pustok"). The name visibly changes on tap.
- **Error handling is narrow.** Only `BibleDataException` is caught (`BookChaptersViewModel.kt:42–46`); an asset/IO failure leaves the screen in a spinner.

**Keep.** Position-based selection (`chapter.position`), `chapter.id` keys, duplicate-number suffixes, the grid metaphor (it is the right pattern for 150 chapters), and the back arrow.

---

### 3.4 Reader — `reader/{bookId}/{chapterPosition}`

**Implementation.** `Scaffold` with `ReaderTopBar` (`ReaderScreen.kt:49–58`). The top bar contains a two-line title (book name + "Chapter N" or "Chapter N · verse V"), a left `navigationIcon` that is **previous chapter**, a conditional bookmark star, a chapter-selector `TextButton`, and a right **next chapter** action (`ReaderScreen.kt:92–167`). Content is a `LazyColumn` with 20 dp horizontal / 16 dp vertical padding and 10 dp item spacing (`ReaderComponents.kt:227–251`). Items come from `ReaderContentBuilder` (header, headings interleaved by `before_verse`, verses). Verse rows are clickable and tinted for target/selected/bookmarked (`:99–141`). The chapter selector is a `TextButton` + `DropdownMenu` listing every chapter (`:315–344`). Scroll position is saved per chapter (`:182–187`); targets jump once via `scrollToItem` (`:203–210`). Horizontal swipes navigate chapters (`:219–248`, `SwipeNavigation.kt:25–30`).

**States.** Loading (spinner), error + Retry, content, target highlight (2 s), selected verse, bookmarked verse, passage mode, target-missing (computed but never displayed).

**Critique.**

- **No back affordance.** The top-left arrow is previous chapter. At chapter 1 it is disabled, so the natural "go back" position shows a disabled arrow. The only way back is the system back gesture/button. On a reading screen this is a core navigation failure.
- **Top bar overload and overflow.** A 64 dp `TopAppBar` holds a two-line title plus up to four actions. At 393 dp width with the bookmark visible, the title gets ≈153 dp (393 − 16 start − 48 back − 48 bookmark − ≈64 selector − 48 next − 16 end); "Fuddareañachem Pustok" alone is ≈168 dp at 16 sp, so it wraps; the subtitle makes it three lines inside 64 dp. Long book names clip even at 1× font scale. At 200 % font scale the two text lines need ≈80 dp and cannot fit at all. This is a layout bug, not just an aesthetic one.
- **"Chapter 0".** During first load/error, `state.chapter` is null and the subtitle falls back to `0`, rendering "Chapter 0" (`ReaderScreen.kt:107–114`).
- **State tints are imperceptible.** Target = `primaryContainer` at 35 % alpha, selected = 20 %, bookmarked = `secondaryContainer` at 25 % (`ReaderComponents.kt:110–115`). Composited over the backgrounds these are 1.07:1, 1.05:1, and 1.05:1 in light; 1.18:1, 1.08:1, and 1.11:1 in dark. State is communicated by color only, with no `stateDescription`.
- **The reading column has no measure cap.** With 20 dp padding: ≈38 characters per line in portrait (good), ≈90 in landscape (too long), ≈19 at 200 % font scale (very narrow). On any wider window the line length becomes unreadable.
- **Verse-per-item with 10 dp gaps reads like a list.** Every verse is a separate card-like row with 10 dp spacing; paragraph continuity is lost. The verse number has no fixed width, so the text's left edge jitters between 1-, 2-, and 3-digit verses, and wrapped lines do not hang under the first line.
- **Passage mode is contextless.** When opened from a bookmark or Daily Reading, only the requested verses render, but nothing says "Verses 5–8" or offers "Show full chapter". Tapping a bookmark can therefore open a single isolated verse with no surrounding chapter.
- **Target missing is silent.** `ReaderUiState.targetMissing` exists (`ReaderUiState.kt:42–43`) and is never rendered. A stale bookmark opens the chapter at the top with no explanation.
- **Bookmarking is hidden.** The star appears only after a verse is tapped, uses the "favorite" metaphor, and gives no confirmation. There is no hint that tapping selects a verse.
- **Chapter selection is a long dropdown.** Psalms has 150 entries in a `DropdownMenu`; scanning is slow and there is no grid, search, or sectioning.
- **Swipe has no feedback and can conflict with system back.** A 96 dp horizontal drag changes chapter instantly with no animation, no haptic, and no undo. Edge swipes from the right may be intercepted by MIUI's back gesture or by the reader depending on the gesture start point. TalkBack users cannot perform the swipe action; no custom accessibility actions expose next/previous.
- **No text selection, copy, or share.** The verse text is plain `Text`; there is no `SelectionContainer`. For a scripture app this is a significant missing affordance.
- **Poetry and chapter duplication.** Psalms are centered (good, `PsalmPoetry.kt:27–28`), but the app's large `ChapterHeader` numeral (`ReaderComponents.kt:70–78`) duplicates the corpus's own green "Ovesvor N" heading at the top of nearly every chapter.
- **Loading replaces content.** Loading the next chapter blanks the reading surface and shows a spinner; because the state retains the previous chapter, the top bar shows the old title during the load. There is no skeleton or crossfade.

**Keep.** The single authoritative `ReaderContentItem` sequence, position-based previous/next, scroll restoration per chapter, one-shot target scrolling, the 2-second highlight lifecycle, bookmark state observed as a set (no N+1), passage filtering as a concept, and the poetry `<br/>` fidelity.

---

### 3.5 Search — `search`

**Implementation.** `TopAppBar` titled "Search" with no back arrow (`SearchScreen.kt:59–62`). An `OutlinedTextField` with placeholder and a clear/search trailing icon (`:65–85`), a book-filter row ("In [All Bible]") implemented as a `TextButton` + 73-item `DropdownMenu` (`:87–91`, `163–203`), an optional warning line in `tertiary` (`:93–100`), then loading/error/blank-query/no-results/results (`:102–139`). Results are `SearchResultItem` rows: book name + `chapter:verse`, then a 3-line snippet (`SearchResultItem.kt:31–69`). Queries are debounced 300 ms and cancelled on change; blank queries never hit the database (`SearchViewModel.kt:55–64`, `BibleSearchQueryParser.kt:34–44`). The repository returns at most 50 results (`BibleSearchRepository.kt:33–37`).

**States.** Loading (full-screen spinner replacing results), error (no retry), blank-query hint, no-results hint, results, optional partial-results warning.

**Critique.**

- **Input ergonomics.** No `KeyboardOptions`/`KeyboardActions`, so the IME action does nothing useful and the keyboard stays open over the results; no `imePadding()` anywhere, so the keyboard covers the lower half of the list. There is no `label` (only a placeholder), so once text is entered TalkBack announces a value with no field purpose.
- **Results flicker.** `isSearching` swaps the whole list for a centered spinner on every debounce (`:103–108`). Typing a 6-letter word can blank the results several times.
- **No count, no cap disclosure.** `SearchUiState.resultCount` is set (`SearchViewModel.kt:97`) but never rendered. The list silently stops at 50; users cannot tell whether they are seeing everything.
- **No query highlighting.** Snippets are plain text; the matching word is not emphasized, which slows scanning in a dense Konkani result list. The book name is Konkani-only, so an English-speaking user searching "shepherd" sees a result labelled in Konkani.
- **Filter UX.** A 73-item dropdown is a poor selector; it has no search, no recent books, and no visible "clear filter" affordance beyond selecting "All Bible" again.
- **Warning semantics.** Partial-results warnings use the green `tertiary` role (`:97`), which reads as success rather than caution.
- **No back arrow.** Search is pushed from Home but looks like a root.
- **Hard-coded error copy.** "Search failed for N book(s)." / "Partial results: …" are English strings in the ViewModel (`SearchViewModel.kt:98–107`), not resources.

**Keep.** 300 ms debounce + `collectLatest` cancellation, the safe query parser, the no-query/no-database rule, per-book failure isolation, saved query/filter across process recreation, full-row tap targets, and the 3-line snippet cap.

---

### 3.6 Reference — `reference`

**Implementation.** Back arrow + "Go to reference" top bar (`ReferenceEntryScreen.kt:70–83`). A single `OutlinedTextField` with the hint "e.g. James 1:5", a full-width Go button, and a resolution area (`:91–157`). Four resolution states: Resolved (navigates once), Ambiguous (list of chapter candidates), AmbiguousVerse (list of source-verse candidates with occurrence suffix), NotFound/Invalid (plain error-colored text). Candidates are plain `Text` rows with `clickable` and 10 dp vertical padding (`:161–215`).

**States.** No resolution (blank area), resolving (spinner), resolved (navigates), ambiguous, ambiguous verse, not found, invalid.

**Critique.**

- **Dead blank state.** Below the Go button there is nothing until the user resolves something. No explanation of accepted forms, no examples of Konkani book names, no recent references.
- **English-only example.** The hint "e.g. James 1:5" is in `strings.xml:13`, while the book lists show Konkani names ("Jakobachem"). A Konkani-first reader may not connect "James" to the book they see.
- **No IME action.** Pressing the keyboard's action does nothing; the only way to submit is to reach the Go button. This is a concrete extra tap on every use.
- **Negative states are harsh.** NotFound and Invalid render in `error` red with no distinction between "you typed something unsupported" and "this verse does not exist"; there are no suggestions.
- **Candidate rows are 42 dp tall** (10 + 10 padding + 22 line height) and have no divider or chevron; `bodyMedium` in `primary` makes them look like links rather than list rows.
- **Occurrence suffix "(1)"** is unexplained (`verseLabel`, `:221–233`); a user choosing between duplicate source verses does not know what the number means.
- **No back-to-results hint.** After returning from the Reader, the previously resolved query remains and the screen is blank below the button; pressing Go navigates again. This is acceptable but slightly confusing.

**Keep.** The resolver-driven four-state model, "never pick silently" on ambiguity, one-shot navigation consumption, saved query across process recreation, and the back arrow.

---

### 3.7 Bookmarks — `bookmarks`

**Implementation.** Back arrow + "Bookmarks" (`BookmarksScreen.kt:56–69`). Loading spinner, empty text, or a `LazyColumn` of rows (`:72–94`). Each row is clickable and shows the Konkani book name + `chapter:verse` (or range), with a trailing trash `IconButton` (`:99–137`). Data is newest-first from Room (`BookmarkDao.kt:22`), and deletion is immediate (`BookmarksViewModel.kt:37–41`).

**States.** Loading, empty, content. No error state (Room flows are reliable, but a failure would leave `isLoading` true).

**Critique.**

- **Empty state is a dead end.** "No bookmarks yet." with no explanation of how to create one and no CTA to the Bible. A first-time user who taps Bookmarks from Home learns nothing.
- **Destructive delete has no undo or confirmation.** A misplaced tap on the always-visible trash icon deletes permanently. The repository can re-add the same bookmark (unique index, `addBookmark`), so undo is feasible without a schema change.
- **Rows lack recognition cues.** Only book + reference are shown; no verse snippet, no saved date (although `Bookmark.createdAt` exists), no grouping by book. With many bookmarks, every row looks the same.
- **Always-visible trash.** A destructive icon on every row adds visual noise and increases accidental taps; Material patterns would use swipe-to-dismiss or an overflow menu.
- **No count/context.** No "12 bookmarks" or grouping headers.
- **No back arrow problem** — this one has it (good).

**Keep.** Newest-first ordering, exact-location identity, tap-to-open via stable fields, observable Room state (no manual refresh), back arrow, and the 48 dp delete target.

---

### 3.8 Daily Reading — `daily-reading`

**Implementation.** Back arrow + "Daily Reading" (`DailyReadingScreen.kt:77–90`). States: loading spinner, error + Retry, success (`:91–124`). Success renders a `LazyColumn` with date (blue `titleMedium`), celebration (`titleLarge`), season, celebration type, optional navigation notice, an empty message, and reading rows (`:127–200`). Each reading row shows the reading type in blue `titleSmall` and the reference in `bodyLarge` (`:203–222`). The date is formatted with `DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")` (`:232–234`). The repository is cache-first per date, silently returning stale cache on network failure (`LiturgicalRepositoryImpl.kt:52–57`, `83–90`).

**States.** Loading, error, success, empty readings, partial resolution notice. No "offline/cached" state is surfaced.

**Critique.**

- **It is a reference list, not a reading experience.** The screen never shows scripture text; every row is a pointer into the Reader. For a feature called "Daily Reading" this is a product gap. The model even carries `celebrationDescription` and `celebrationQuote` (`LiturgicalDay.kt:13–21`) that are never shown.
- **No hierarchy between readings.** First Reading, Psalm, Second Reading, and Gospel all have the same visual weight, even though the Gospel is the natural focus.
- **No date navigation.** Only "today" exists. The repository already accepts any `LocalDate`, so previous/next-day browsing is a UI/ViewModel change, not a data-contract change.
- **No freshness signal.** Cached data is indistinguishable from fresh data; a stale cache returned after a network failure looks current.
- **Error copy comes from the repository** in English ("The liturgical service returned an error (…)", "Unable to reach …"), shown in `error` color. It is user-friendly enough, but untranslatable and mixed with UI strings.
- **Date locale mismatch.** The pattern uses the default locale, so an English UI can show localized month/day names (or non-Latin digits), producing a mixed-language screen.
- **The notice is error-colored** even when it is informational ("Could not find X in the Bible"), and it sits inline in the list with no dismiss affordance (it is consumed only on navigation).

**Keep.** Cache-first/date-keyed repository, explicit empty state, retry on error, the date/celebration/season header structure, and reuse of the single Reader.

---

### 3.9 Book Introduction — `books/{bookId}/introduction`

**Implementation.** Back arrow + generic "Introduction" title (`BookIntroductionScreen.kt:55–68`). Loading spinner, error text (no retry), empty message, or a `LazyColumn` of parsed blocks (`:70–97`). Each block is rendered with `BibleHtmlText` at the default `bodyLarge` style (`:100–107`).

**States.** Loading, error (no retry), empty, content.

**Critique.**

- **The book name is missing.** The top bar says only "Introduction"; after a process death or a deep link, the user loses which book they are in. `bookId` is available in state and `BibleCatalog.byId(bookId)` is a cheap fix.
- **Headings inside introductions render as body text.** `BibleHtmlRenderer` turns `Heading` nodes into plain text with alignment but no weight/size (`BibleHtmlRenderer.kt:76–86`), and the screen applies one body style to the whole block. A long introduction has no internal hierarchy.
- **Error has no retry**, unlike Chapters/Reader/Daily.
- **No measure cap**, so introductions are unreadably wide in landscape.
- **The empty state is a dead end** reached from a button that is always shown.

**Keep.** Per-block preservation, parser/renderer reuse, serif prose, no HTML leakage, and the separate screen (introductions do not belong in the Reader).

---

### 3.10 Missing: Settings and About

There is no Settings or About route. `AppDestination` has nine destinations (`AppDestination.kt:10–58`), and the README still claims `settings` is "declared, not implemented" (README:164) while listing it as future work (README:555). In practice:

- No in-app text size, line spacing, theme override, or red-letter toggle.
- No version, corpus source, permission, privacy, or license information beyond a tiny Home line.
- No way to reach attribution from the Reader, where the text is actually read.
- The Daily Reading feature calls an external API (only the date is sent), but there is no privacy note.

For a reading app, in-app typography controls are table stakes; for a licensed corpus, an accessible About screen is also a compliance concern.

---

## 4. Strengths worth keeping

1. **A single authoritative reader content sequence.** `ReaderContentBuilder` produces one ordered list used for rendering, target indexing, and passage filtering (`ReaderContentBuilder.kt:35–79`). Any visual change can be made without risking "where the app thinks the verse is".
2. **Position-based navigation.** Previous/next and selection use `Chapter.position`, never `Chapter.number` (`ReaderViewModel.kt:235–254`), which is the correct defence against Job's duplicate chapter numbers.
3. **Canonical order is explicit and tested.** `BibleCanonicalOrder` + `BooksGrouping` do not trust corpus metadata (`BooksGrouping.kt:20–27`), and tests cover it.
4. **Display names are corpus-sourced, with presentation-only capitalization.** `BookDisplayName` never mutates data (`BookDisplayName.kt:24–34`), and the unit tests pin the behaviour.
5. **Duplicate chapters are visually disambiguated without renumbering** (`ChapterLabels.kt:14–24`).
6. **The architecture keeps UI away from SQLite and HTML.** Screens and ViewModels only see domain objects; the risky parts are isolated and tested. This makes a visual overhaul low-risk.
7. **Restrained, intentional palette.** The AMOLED black + blue accent decision is consistent and guarded by `ThemeColorsTest`. It is a good base for an editorial look.
8. **Reader scroll and target lifecycle are carefully designed.** Per-chapter `rememberSaveable` scroll state, one-shot target application, a bounded highlight, and no re-scroll on recomposition (`ReaderComponents.kt:182–217`).
9. **Search is safe and cancellable.** Debounce, `collectLatest`, an FTS-safe query parser, per-book failure isolation, and no empty `MATCH *` (`SearchViewModel.kt:55–64`, `BibleSearchQueryParser.kt:34–44`).
10. **Bookmarks are identity-correct and observable.** Unique index, stable location, chapter-scoped set observation (no N+1), and re-add support that makes undo possible.
11. **Tests already cover the contracts that a redesign could break:** routes, reader content order, passage filtering, poetry decision, bookmarks, search, and theme values.
12. **Accessibility basics are present:** every icon-only action has a content description; directional icons are `AutoMirrored`; whole rows are clickable; book rows expose a button role; long names ellipsize rather than overlap.
13. **No runtime permissions, no onboarding friction, fully offline scripture.** The product respects the user's time and privacy.

---

## 5. Findings

Priorities are assigned by user impact on the actual device and supported configurations.

- **P0** — breaks a core task or makes content unreadable in a supported configuration; fix before the next release.
- **P1** — significant usability/readability/a11y problem; fix in the next design cycle.
- **P2** — meaningful polish or consistency gap; schedule.
- **P3** — nice-to-have / long-tail.

**Finding index**

| ID | Priority | Finding | Effort |
|---|---|---|---|
| F-01 | P0 | Home is not inset-safe or scroll-safe | S |
| F-02 | P0 | Corpus colors are rendered literally and fail contrast | M |
| F-03 | P0 | Books can hang on an indefinite spinner | S |
| F-04 | P1 | Reader chrome: no back, overloaded bar, out of thumb reach | M |
| F-05 | P1 | Passage mode is contextless; bookmarks open fragments | M |
| F-06 | P1 | Selection/target/bookmark states are invisible | S |
| F-07 | P1 | Typography has two voices and five undefined styles | S |
| F-08 | P1 | Reading column has no measure cap; verse flow is list-like | S |
| F-09 | P1 | Bookmarking is undiscoverable; deletion has no undo | M |
| F-10 | P1 | Navigation inconsistent; search only reachable from Home | S |
| F-11 | P1 | Home has no hierarchy, context, or identity | M |
| F-12 | P1 | Book name changes between screens | M |
| F-13 | P1 | Search/Reference input ergonomics | S–M |
| F-14 | P1 | Chapter cells cramped and too short for touch | S |
| F-15 | P1 | Settings and About are missing | M |
| F-16 | P2 | Empty/error/loading states inconsistent | S–M |
| F-17 | P2 | Dark theme incomplete; bars blend into content | S |
| F-18 | P2 | Daily Reading lacks hierarchy, date nav, freshness | M–L |
| F-19 | P2 | Motion absent or accidental | S–M |
| F-20 | P2 | Books list lacks scanning aids | S–M |
| F-21 | P2 | Book Introduction loses context and hierarchy | S |
| F-22 | P2 | Search results do not support scanning | S–M |
| F-23 | P2 | No text selection, copy, or share | S–M |
| F-24 | P2 | Locale, plurals, and copy are not resource-safe | S–M |
| F-25 | P2 | Poetry and chapter header duplication | S |
| F-26 | P2 | Accessibility semantics coverage is thin | S–M |
| F-27 | P3 | Copy and label polish | S |
| F-28 | P3 | Row affordances and dividers | S |
| F-29 | P3 | Reading progress and position | S |
| F-30 | P3 | RTL and bidi safety | S |
| F-31 | P3 | Book index / fast scroll | S–M |
| F-32 | P3 | Search history and recents | S |

### 5.1 P0

#### F-01 — Home is not inset-safe or scroll-safe

- **Evidence:** `HomeScreen.kt:45–102` (plain `Surface`/`Box`; `Column.fillMaxSize()` + `Arrangement.Center`; six buttons; notice `align(BottomCenter)` with 16 dp); `MainActivity.kt:16` (`enableEdgeToEdge()`); no `Scaffold`, `WindowInsets`, or scroll modifier.
- **Impact:** On the POCO in landscape (≈393 dp height) the centered stack is taller than the viewport; the first and last destinations are clipped and cannot be scrolled into view. With 3-button navigation the copyright notice sits behind the 48 dp navigation bar. At 200 % text the layout is tight in portrait and broken in landscape. A license notice is effectively hidden.
- **Recommendation:** Make Home a `Scaffold`/`safeDrawingPadding()` surface with a scrollable content area and a footer that is either pinned above the navigation inset or the last item with enough bottom padding. Use a compact 2×3 destination grid so everything fits in the first portrait viewport at 1× (see the wireframe in §10.7). Extract a stateless `HomeContent` for font-scale tests.
- **Effort:** S (0.5–1 day). **Risk:** low.
- **Non-breaking notes:** `HomeNavigationUiTest` asserts the copyright notice is displayed and clicks "Bookmarks" at default font. Keep the notice visible in the first portrait viewport (or pinned), and keep exactly one node with the text "Bible", "Search", and "Bookmarks". Adding a bottom navigation bar with those labels would create duplicate nodes and break the test.

#### F-02 — Corpus colors are rendered literally and fail contrast in both themes

- **Evidence:** `BibleHtmlRenderer.kt:94–98` emits absolute `Color(0xFF…)` spans; `BibleHtmlText.kt:29–35` renders them without adaptation. Corpus: 3,257 / 3,258 heading records and 1,620 / 34,272 verse records contain `<font color>`; span counts are `#FF0000` 3,811 headings + 1,754 verses, `#00B050` 1,332, `#021A56` 367, `#0000FF` 279 + 9, `#0070C0` 108, `#000000` 1. Measured contrast: `#FF0000` 3.82:1 and `#00B050` 2.74:1 on light paper; `#021A56` 1.28:1, `#0000FF` 2.44:1, `#000000` 1.00:1 on AMOLED black.
- **Impact:** In light mode, red-letter verses and green chapter headings fail WCAG AA for normal text and look harsh. In dark mode, navy and blue headings — including many section titles — effectively disappear on the black background; the green "Ovesvor N" chapter labels pass in dark (7.33:1) but fail in light (2.74:1). This affects the reading content itself across thousands of spans.
- **Recommendation:** Introduce a theme-aware `ScripturePalette` and map corpus colors at presentation time (exact-match table plus a contrast fallback). Proposed mappings: light red `#B3261E` (6.24:1), green `#0E6B36` (6.32:1), blue `#0B57D0` (6.10:1), navy `#22315C` (12.07:1), mid-blue `#005A8C` (7.06:1); dark red `#FFB4AB` (12.37:1), green `#7EDCA0` (12.63:1), blue `#ADC6FF` (12.30:1), navy `#A9B8E8` (10.71:1), mid-blue `#9CCBF0` (12.21:1). Add a "Words of Jesus in red" toggle (default on). Add a JVM contrast test for every mapped role in both themes.
- **Effort:** M (1–2 days). **Risk:** medium.
- **Non-breaking notes:** Do **not** change `BibleHtmlRenderer` — `BibleHtmlRendererTest` asserts exact span colors (including `#0000FF`). Perform the mapping on the resulting `AnnotatedString` inside `BibleHtmlText` (or a small wrapper), and include the palette/theme in the `remember` key (`remember(document, palette)`), otherwise the cached `AnnotatedString` will keep light-mode colors in dark mode. Also apply the mapping to `HeadingBlock` (it calls `BibleHtmlText`) and to introductions. No HTML/DB changes. Unknown colors: if contrast against the current background is below 4.5:1, fall back to `onBackground` rather than inventing a color. Consider a serif heading style so color is not the only hierarchy cue.

#### F-03 — Books can hang on an indefinite full-screen spinner

- **Evidence:** `BooksViewModel.kt:26–30` awaits `loadAllDisplayNames()` before `isLoading = false`; `BookTitleRepositoryImpl.kt:58–69` opens all 73 per-book databases; `BooksScreen.kt:55–61` shows a full-screen spinner; `BooksUiState.kt:7–10` has no error field; no `try/catch` around the scan.
- **Impact:** First open of Books blocks on a 73-database scan (cold start, slow storage, low-end device). If any asset is missing/corrupt (e.g. a build without the private corpus, documented in the README), the coroutine fails and the spinner never resolves, with no retry or explanation. The user cannot reach any Bible content.
- **Recommendation:** Set `isLoading = false` immediately and render all 73 books from `BibleCatalog`; update the rows progressively as DB titles arrive; catch per-book failures and fall back to the catalog name; add an error/retry path for a catastrophic failure; keep the cache. Optionally move the scan to a background single-flight and never block the list.
- **Effort:** S (0.5–1 day). **Risk:** low.
- **Non-breaking notes:** The UI already falls back per row (`state.displayNames[book.id] ?: book.displayName`, `BooksScreen.kt:69–71`), so rendering early only requires initializing `isLoading` to false and updating the map. Keep `BooksGrouping` and the existing keys. No database or repository contract change is needed; defensive catching is additive.

### 5.2 P1

#### F-04 — Reader chrome: no back action, overloaded 64 dp bar, everything out of thumb reach

- **Evidence:** `ReaderScreen.kt:92–167` (two-line title; `navigationIcon` = previous chapter; bookmark only when a verse is selected; chapter selector; next chapter); `:121–131` disables previous at the first chapter; Material 3 `TopAppBar` is 64 dp.
- **Impact:** There is no visible way back to Books. At chapter 1 the natural back position shows a disabled arrow. Chapter navigation — the most frequent reader action — is at the top of a 6.67-inch phone, outside comfortable one-handed reach. Long book names plus three actions make the title wrap and clip at 1×; at 200 % font the two-line title (≈80 dp) cannot fit at all. The "Chapter 0" fallback appears during load/error.
- **Recommendation:** Use a back arrow as `navigationIcon` (contentDescription "Back"), a single-line `titleMedium` book name, and move previous/chapter/next/bookmark into a bottom action bar in the thumb zone. Put "Chapter N" and the optional "verses x–y" subtitle in the scrolling content header, not the app bar. Open the chapter picker as a grid bottom sheet (3–5 columns, current chapter marked) rather than a 150-item dropdown. Remove the `?: 0` fallback.
- **Effort:** M (2–3 days). **Risk:** low–medium.
- **Non-breaking notes:** Keep `ReaderViewModel.previous()`, `next()`, `selectChapter(position)` and `toggleBookmark()` exactly as they are. Keep system back working; adding a back arrow must not intercept it. Add test tags for the new controls; no route changes. The top bar currently has no test coverage, so this is a safe visual change with manual validation.

#### F-05 — Passage mode is contextless; bookmarks open isolated fragments

- **Evidence:** `ReaderContentBuilder.kt:35–55` filters to the requested range; `ReaderComponents.kt:192–199` renders only those items; no banner anywhere in `ReaderScreen.kt`; `Bookmark.toReaderDestination()` sets `verseStart`/`verseEnd` (`Bookmark.kt:29–35`), so bookmark taps enter passage mode; `ReaderUiState.targetMissing` (`:42–43`) is never rendered.
- **Impact:** A user tapping a bookmark lands on a single verse with no chapter context and no way to expand it. A user tapping a Daily Reading sees the passage but cannot tell that the rest of the chapter is hidden. A stale target silently shows the chapter start (or a one-item passage) with no explanation. This makes bookmarks feel like isolated snippets rather than places in a book.
- **Recommendation:** In passage mode, show a sticky banner under the app bar: "Verses 5–8" + a "Show full chapter" text button. `showFullChapter()` can call `viewModel.load(bookId, chapterPosition)` (which already clears the passage) or navigate to the plain reader route. Surface `targetMissing` as a dismissible inline notice ("Verse not found — showing the chapter"). Decide bookmark behaviour explicitly: preferred is "open the full chapter at the bookmarked verse" (use the target/scroll mechanism plus a range highlight), with passage mode reserved for liturgy/daily readings; if the team prefers to keep the range, the banner is mandatory.
- **Effort:** M (2 days). **Risk:** medium.
- **Non-breaking notes:** Passage filtering is covered by `ReaderContentBuilderTest` and `ReaderTargeting*`; keep the filtering behaviour and make the banner additive. If bookmark navigation semantics change, update `BookmarkDestinationTest` and the reader targeting tests in the same commit, and explain the product decision. `ReaderViewModel.retry()` already replays the exact request; a `showFullChapter()` must not break that.

#### F-06 — Selection/target/bookmark states are effectively invisible

- **Evidence:** `ReaderComponents.kt:110–115` uses `primaryContainer.copy(alpha = 0.35f/0.20f)` and `secondaryContainer.copy(alpha = 0.25f)`. Composited results: light target `#EEF1FF` on `#FAF9FF` = 1.07:1; light selected = 1.05:1; light bookmarked = 1.05:1; dark target `#001834` on black = 1.18:1; dark selected = 1.08:1; dark bookmarked = 1.11:1. No `stateDescription` is set.
- **Impact:** Users cannot see which verse the top-bar bookmark applies to, which verse was targeted from search, or which verses are bookmarked. In dark mode the selected verse is essentially black-on-black. State is communicated by color alone, which also fails TalkBack and WCAG 1.4.1/1.4.11.
- **Recommendation:** Use solid, dedicated surfaces rather than alpha: light selection `#E7EEFB`, light target `#DCE9FF`, dark selection `#1B2738`, dark target `#22324C`; add a 2 dp accent bar in the verse gutter and a small bookmark marker for bookmarked verses. Add `stateDescription` ("Selected. Bookmarked.") and keep the highlight animation short (150 ms). All proposed surfaces keep the palette text colors above 5.4:1 (red) and 8:1 (ink).
- **Effort:** S (0.5–1 day). **Risk:** low.
- **Non-breaking notes:** Background modifiers are presentation-only; none of the item keys or scroll logic changes. Keep the existing 2-second highlight lifecycle (`ReaderComponents.kt:213–217`) and `clearHighlight()`. The bookmark set still comes from `bookmarkedVerseIds`.

#### F-07 — Typography has two voices and five undefined styles

- **Evidence:** `Type.kt:15–44` defines only `bodyLarge`, `bodyMedium`, `titleLarge`, `headlineMedium`. `titleMedium` (section headers, reader headings, Daily date), `titleSmall`, `bodySmall`, `labelMedium` (verse numbers), `labelSmall` (copyright), and `labelLarge` (buttons) are Material Roboto defaults. Usage counts: `bodyMedium` 11, `bodyLarge` 10, `bodySmall` 7, `titleSmall` 4, `titleMedium` 4.
- **Impact:** Scripture and page titles are Noto Serif, while section headings and reader headings are Roboto — the reader's hierarchy feels like UI chrome rather than a book. `bodyMedium` at 14 sp with 0.25 sp letter spacing is small for Konkani diacritics (ô, ñ, ê, ’). Metadata and content have similar weight, so scanning is harder.
- **Recommendation:** Define every style the app uses (see the proposed scale in §6.3). Serif for display/title/scripture; sans for UI/meta. Raise `bodyMedium` to 15/24, `titleMedium` to 17/24 serif, `titleSmall` to 15/22 sans, `bodySmall` to 13/20, `labelLarge` to 14/20, and add `Scripture`, `ScripturePoetry`, and `VerseNumber`. Keep the existing `bodyLarge`/`headlineMedium` values unless the scale is deliberately re-tuned.
- **Effort:** S (1 day). **Risk:** low.
- **Non-breaking notes:** This is visual-only; no layout logic depends on type metrics except the fixed-height app bars (fixed by F-04). `BibleTextStyle` remains the scripture style (or becomes an alias for the new `Scripture` style) so `PsalmPoetry`/`VerseBlock` references compile. `ThemeColorsTest` is unaffected.

#### F-08 — The reading column has no measure cap and the verse flow is list-like

- **Evidence:** `ReaderComponents.kt:227–251` (20 dp horizontal padding only; `spacedBy(10.dp)`); `VerseBlock` row (`:116–140`); `VerseNumber` has no fixed width (`:147–163`); `BibleTextStyle` 19/32 (`Type.kt:51–56`).
- **Impact:** Measured with Noto Serif at 19 sp: ≈38 characters per line at 393 dp portrait (good), ≈90 in landscape (too long to track), ≈19 at 200 % font scale (very narrow). The 10 dp gap between every verse breaks paragraph continuity. The verse text's left edge moves with the width of "1" vs "150", and wrapped lines start under the number rather than under the first line. The reader reads as a sequence of cards, not a page.
- **Recommendation:** Constrain the reading column with `widthIn(max = 620.dp)` and center it; keep 20 dp side padding, dropping to 16 dp at font scales ≥1.5. Reduce inter-verse spacing to 4 dp, add 20 dp before headings and 8 dp after. Use a fixed gutter (`30.dp × fontScale.coerceAtMost(2f)`) with the number right-aligned; the verse text column then has a stable left edge and wrapped lines align. Keep `Scripture` at 19/32 (1.68 line height).
- **Effort:** S (0.5–1 day). **Risk:** low.
- **Non-breaking notes:** Do not change `ReaderContentBuilder` order or item keys; `scrollToItem(index)` depends on the same list. Only modifiers/styles change. Verify that target scrolling still lands with the new padding (the index is unchanged; the offset may differ slightly). `PsalmPoetryTest` is unaffected.

#### F-09 — Bookmarking is undiscoverable, and deletion has no undo

- **Evidence:** `ReaderScreen.kt:132–149` shows a star only when `selectedVerseId != null`; `ReaderComponents.kt:99–141` selects a verse on tap; no hint or confirmation; `BookmarksScreen.kt:131–136` deletes immediately; `BookmarkRepositoryImpl.kt:45–78` can re-add from the stored fields; DAO unique index prevents duplicates.
- **Impact:** Users must guess that tapping a verse is a prerequisite for bookmarking, and the star reads as "favorite" rather than "bookmark". There is no confirmation that a bookmark was saved. On the Bookmarks screen, a misplaced tap permanently deletes a saved place. This undermines trust in the feature.
- **Recommendation:** Use a bookmark glyph (add two vector drawables; no dependency), support long-press on a verse to toggle a bookmark directly with a haptic and a snackbar ("Bookmarked Juanv 3:16 · Undo"), and show a persistent bookmark marker in the gutter. On the Bookmarks screen, use a snackbar with Undo (re-add using the removed `Bookmark` fields) and consider swipe-to-dismiss with the trash moved to an overflow. Keep the top-bar action for the selected verse.
- **Effort:** M (2 days). **Risk:** low–medium.
- **Non-breaking notes:** `toggleBookmark()` already serializes toggles and checks persisted state, so rapid taps remain safe. Undo does not need a schema change: the `Bookmark` object contains all fields required by `addBookmark`. TalkBack exposes long-click automatically; add an explicit custom action "Bookmark verse" for reliability. No route or DB changes.

#### F-10 — Navigation is inconsistent and search is only reachable from Home

- **Evidence:** `BooksScreen.kt:51–54` and `SearchScreen.kt:59–62` have no navigation icon; both are pushed from Home (`NavHost.kt:83–89`, `146–154`); search is reachable only via `HomeScreen.kt:68–70`; the Reader has no search/reference action.
- **Impact:** Books and Search look like roots while Chapters/Bookmarks/Reference/Daily all have back arrows. A user reading in the Reader who wants to search must back out to Home (or to Books), navigate to Search, and retype. In a scripture app, search should be one action away from anywhere.
- **Recommendation:** Add a back arrow ("Back") to Books and Search. Add a search action to the Books and Chapters top bars; add it to the Reader overflow/bottom bar when F-04 lands. Keep system back unchanged. Later, consider consolidating Search and Reference into a single "Find" screen with a Search/Reference segmented control, while keeping both routes alive.
- **Effort:** S (0.5 day). **Risk:** low.
- **Non-breaking notes:** The existing system-back test still passes. Do not add a second "Back" node on Bookmarks or any screen where one already exists. Search from Books should navigate to the existing `search` route (no new args). Adding an icon does not change route contracts.

#### F-11 — Home has no hierarchy, context, or identity

- **Evidence:** `HomeScreen.kt:64–89` (six identical filled buttons); "Bible" and "Go to reference" labels (`strings.xml:3–5`, `27–28`); "Continue reading" ignores `ReadingState.updatedAt` (`ReadingState.kt:7–11`); the notice is `labelSmall` (`HomeScreen.kt:92–101`).
- **Impact:** The first screen reads like a debug menu; every action competes equally. Users cannot tell where "Continue reading" will take them, what "Go to reference" means, or that the app is a Konkani Catholic Bible. The app's strongest content is invisible until a second tap.
- **Recommendation:** Adopt the editorial Home described in §10.7: an overline ("KONKANI CATHOLIC BIBLE"), a serif hero, one primary "Bible" action, a compact 2×3 destination grid with one-line subtitles, a "Continue reading" card with the book name + relative time, and a Daily Reading accent row. `ReadingState` stores `chapterPosition`, not a display chapter number; do not print a "Chapter N" derived from position for duplicate-number books (Job) unless you resolve the real number with a cheap read or cache it. Keep the exact labels "Bible", "Search", "Bookmarks" and keep the notice, now tappable to About.
- **Effort:** M (2–3 days). **Risk:** medium.
- **Non-breaking notes:** This is the highest test-risk change in the app. `HomeNavigationUiTest` clicks text nodes and asserts the notice is displayed. Keep one visible node per label; keep the notice in the initial portrait viewport (or pinned); if tiles are custom clickable surfaces, let semantics merge so `onNodeWithText(...).performClick()` still works. Do not add a bottom navigation bar with duplicate labels. No new dependencies; relative time can use `java.time`/`DateUtils`.

#### F-12 — The book name changes between screens

- **Evidence:** Books uses normalized DB titles (`BooksScreen.kt:69–71`; `BookTitleRepositoryImpl.kt:71–96`); Chapters (`BookChaptersScreen.kt:64`), Reader (`ReaderScreen.kt:96`), Search (`SearchResultItem.kt:37`), and Bookmarks (`BookmarksScreen.kt:106`) use `BibleCatalog.displayName` (`BibleCatalog.kt:13–85`). Example: "Fuddareañachem Pustok" vs "Fuddareanchem Pustok"; "Levi – Xastr" vs "Levi Xastr".
- **Impact:** The name visibly changes on tap; a user who selected by English name later sees only a Konkani name; TalkBack announces inconsistent labels for the same book; search results look like they come from a different library.
- **Recommendation:** Create one display-name source (a cached singleton exposing `BookTitleRepository` results, with the catalog as fallback) and use it on Chapters/Reader/Search/Bookmarks. Show the English name as a secondary line on Chapters and Reader (hide at large font scales), and as a secondary in search/bookmark rows. Enforce `maxLines = 1` + ellipsis.
- **Effort:** M (2–3 days). **Risk:** medium.
- **Non-breaking notes:** No route or DB changes; `getBook()` can keep returning the catalog object and the UI can overlay the resolved name. Reader/Search cannot synchronously query all titles today — use a shared cache with a catalog fallback so nothing blocks. Add a unit/instrumentation test comparing the catalog names to the corpus titles to prevent drift. Do not change bookmark identity or ids.

#### F-13 — Search and Reference input ergonomics

- **Evidence:** `SearchScreen.kt:65–85` (no label, no keyboard options/actions); `ReferenceEntryScreen.kt:91–116` (Go button, no IME action); no `imePadding()` in the app; `SearchScreen.kt:102–108` replaces the results with a spinner; `SearchUiState.resultCount` unused (`SearchUiState.kt:16`); hint "e.g. James 1:5" (`strings.xml:13`); 73-item filter dropdown (`SearchScreen.kt:183–200`); repository cap 50 (`BibleSearchRepository.kt:33–37`).
- **Impact:** Pressing the keyboard action does nothing on Reference and merely hides the keyboard on Search. The keyboard covers the lower half of the results. Results blank on every debounce, so the screen flickers while typing. Users cannot tell how many hits exist or that the list is capped. The English-only example conflicts with the Konkani book names shown elsewhere. Selecting a book from a 73-item menu is slow.
- **Recommendation:** Add `KeyboardOptions(imeAction = ImeAction.Search/Go, autoCorrectEnabled = false, capitalization = None/Words)` and `KeyboardActions`; add `Modifier.imePadding()` to the scrollable result area. Keep the previous results visible and show a 2 dp `LinearProgressIndicator` while searching. Show "N results · first 50 shown" (do not present `resultCount` as a total). Label the field. Replace the dropdown with a filter chip that opens a searchable bottom sheet. Change the hint to "Juanv 1:5 or James 1:5". Add a retry button to the search error state.
- **Effort:** S–M (1–2 days). **Risk:** low.
- **Non-breaking notes:** Keep the `OutlinedTextField` (or an equivalent with `hasSetTextAction`) so `HomeNavigationUiTest.search_returns_james_results` still finds the field. Keep the saved-state keys `search_query` and `search_book_filter`. Do not parse references inside Search; the Reference screen owns that.

#### F-14 — Chapter cells are cramped and too short for touch

- **Evidence:** `BookChaptersScreen.kt:106–119` (`GridCells.Adaptive(72.dp)`), `:126–131` (`OutlinedButton`); M3 default content padding 24 dp horizontal and 40 dp minimum height; cell ≈84 dp wide at 393 dp with 16 dp padding + 8 dp gaps; duplicate labels from `ChapterLabels.kt:14–24`.
- **Impact:** The text area is ≈36 dp wide: "150" barely fits, "150 (1)" wraps/clips, and at 200 % font scale even three digits cannot fit. 40 dp is below the 48 dp touch target. Psalms' 150 chapters become a long, uniform scroll with no position cues.
- **Recommendation:** Replace the button with a custom cell (`Surface`/`Box` with 48 dp min height, no horizontal content padding, serif numerals, centered). Compute columns from font scale (3 columns at ≥1.5×) or derive `minSize` from `LocalDensity.fontScale`. Add a compact "Chapter" section label and, for long books, a jump/section header. Mark the last-read chapter. Add an empty-state message when `chapters` is empty.
- **Effort:** S (0.5–1 day). **Risk:** low.
- **Non-breaking notes:** Keep `chapter.position` on click and `chapter.id` as the key; keep the Introduction entry and the back arrow. Duplicate labels must keep the exact `N (1)` format (`ChapterDisplayLabelTest`).

#### F-15 — Settings and About are missing

- **Evidence:** No route/constant in `AppDestination.kt:10–58`; no screen; README:164 still says `settings` is declared, README:555 lists it as future; the only attribution is `HomeScreen.kt:92–101`; no text-size/theme/red-letter controls.
- **Impact:** Users cannot adjust scripture size, line spacing, theme, or red-letter display in-app. The corpus attribution, permission, version, privacy note (Daily Reading uses an external API), and licenses are not accessible from the UI. For a licensed scripture app this is a compliance and trust gap.
- **Recommendation:** Add a single "About & settings" destination reachable from the Home footer and the Reader overflow. Sections: Reading (scripture size S/M/L/XL, line spacing, red-letter toggle, theme System/Light/Dark), About (corpus © Archdiocese of Goa and Daman, permission note, version, Daily Reading data source/privacy, open-source licenses). Persist with `SharedPreferences` and apply via a `CompositionLocal` consumed by `BibleTextStyle`; theme default System.
- **Effort:** M (3–5 days). **Risk:** medium.
- **Non-breaking notes:** Adding a route changes `route_constants_are_distinct` (`AppDestinationTest.kt:66–79`, expects 9) — update the test deliberately. Keep the Home notice text and make it the entry point (tap → About). Do not double-apply text scaling: the system font scale remains authoritative and the in-app control is a bounded multiplier (e.g. 0.9×–1.35×). Use `PackageManager` for the version (`buildConfig` is not enabled). No DB change; `SharedPreferences` is framework-only.

### 5.3 P2

#### F-16 — Empty, error, and loading states are inconsistent and sometimes dead ends

- **Evidence:** Home: no states. Books: spinner only, no error (`BooksScreen.kt:55–61`). Chapters: error + retry, but empty chapters render blank (`:77–121`). Reader: error + retry (`ReaderComponents.kt:286–309`). Search: error text with no retry (`SearchScreen.kt:110–122`). Reference: blank before resolving. Bookmarks: "No bookmarks yet." with no CTA (`BookmarksScreen.kt:75–80`). Daily: retry (`DailyReadingScreen.kt:97–114`). Introduction: error with no retry (`BookIntroductionScreen.kt:73–79`).
- **Impact:** Users cannot recover from some failures, and empty states do not teach the next step. The visual language changes from screen to screen.
- **Recommendation:** Add shared `EmptyState`, `ErrorState`, and `LoadingState` composables (icon/monogram, title, body, optional action). Use skeletons for Books and Reader content; use a linear indicator for search. Add retry to Search and Introduction; add "Browse books" to the Bookmarks empty state (requires an `onOpenBooks` callback); add an empty message to Chapters.
- **Effort:** S–M (1–2 days). **Risk:** low.
- **Non-breaking notes:** Keep existing test-visible strings ("No results found.", "No bookmarks yet.") or migrate them with the tests. Return types/state classes are unchanged; this is presentation composition.

#### F-17 — Dark theme is incomplete and bars blend into the content

- **Evidence:** `Theme.kt:36–61` sets 15 roles; `surfaceContainer*`, `surfaceBright`, `surfaceDim`, `inverseSurface`, `scrim`, and `surfaceTint` fall back to Material baseline values (tuned to the baseline purple palette). Top bars use `surface` = black with no divider.
- **Impact:** Any new Card, BottomSheet, NavigationBar, Snackbar, or scroll-behavior TopAppBar will pick up purple-neutral baseline surfaces that clash with the blue/black palette. Bars and content are visually indistinguishable on AMOLED, so scrolling content appears to run under an invisible bar.
- **Recommendation:** Define the full surface family deliberately (or map the surface containers to near-black and use hairlines for separation). Add a 1 dp `outlineVariant` divider under top bars and above bottom bars. Verify with a Card, bottom sheet, snackbar, and segmented control in both themes before shipping.
- **Effort:** S (1 day). **Risk:** low–medium.
- **Non-breaking notes:** `ThemeColorsTest` pins `DarkBackground`, `DarkSurface`, `LightPrimary`, etc. Do not change those; add new roles. Adding roles to `darkColorScheme`/`lightColorScheme` is additive.

#### F-18 — Daily Reading lacks hierarchy, date navigation, and freshness cues

- **Evidence:** `DailyReadingScreen.kt:127–200` (date/celebration/season/type/readings all flat); no date controls; `LiturgicalRepositoryImpl.kt:52–57`, `83–90` returns cached data silently; `LiturgicalDay.celebrationDescription`/`celebrationQuote` unused (`LiturgicalDay.kt:13–21`); date formatter uses the default locale (`DailyReadingScreen.kt:232–234`).
- **Impact:** The Gospel does not stand out; users cannot read yesterday/tomorrow; offline/stale data looks fresh; the screen is a link list rather than a devotional reading experience. A device in a non-English locale produces mixed-language dates.
- **Recommendation:** Make the Gospel the feature (larger reference, accent rule, "Read the Gospel" CTA), show the celebration quote/description, and group the other readings as compact rows. Add a previous/next-day control (repository already accepts any `LocalDate`; no data change). Show "Saved for offline · updated HH:mm" when data came from cache, and a retry when it is stale. Pin the date format to the UI locale (English for now) or localize deliberately.
- **Effort:** M–L (3–5 days). **Risk:** medium.
- **Non-breaking notes:** Keep the `DailyReadingUiState` sealed states and the one-shot navigation event. Date navigation adds a selected-date field to the ViewModel, not the route; `refreshIfDateChanged()` still applies to "today". Cache timestamps already exist in the entity (`LiturgicalDayCacheEntity`); exposing them may require a small domain/state addition, not a schema change.

#### F-19 — Motion is absent or accidental

- **Evidence:** `NavHost.kt:30–172` specifies no transitions; switching chapters inside the Reader swaps content instantly; state tints change instantly; there is no haptic or snackbar feedback for bookmarking; the highlight appears and disappears with a hard 2 s cut (`ReaderComponents.kt:213–217`).
- **Impact:** Chapter changes feel like a reload rather than a page turn; selection feels unresponsive; bookmarking feels like nothing happened. Conversely, relying on Navigation Compose defaults can produce a long cross-fade that feels sluggish on a low-end device.
- **Recommendation:** Specify explicit, short transitions: 180–220 ms fade + small slide for navigation; 160 ms horizontal slide for chapter changes (direction from next/previous); 150 ms color transitions for state; 120 ms icon scale on bookmark; snackbar/haptic confirmation. Skip animations when the system animation scale is 0. Never animate font size, line height, or list reordering.
- **Effort:** S–M (1–2 days). **Risk:** low.
- **Non-breaking notes:** Transitions are declarative on the NavHost/composables. Keep `waitForIdle`/test synchronization in mind: animations must be short and finite (no infinite pulses). The reader's target scroll stays an instant jump (animating through 100 items is slow and disorienting); animate the highlight, not the jump.

#### F-20 — Books list lacks scanning aids

- **Evidence:** `BooksScreen.kt:62–77` (non-sticky `TestamentHeader`), no search/index; 73 rows; blue section headers (`:88–90`).
- **Impact:** After scrolling, the testament context is lost; finding a book by English name requires reading every row; the blue headers look like links. No indication of reading progress or last-read book.
- **Recommendation:** Use sticky headers (`stickyHeader`), a quiet overline/rule style for sections (not primary blue), an A–Z/fast-scroll affordance, and a top-bar search action. Mark the last-read book. Consider a compact two-line row (local name + English subtitle) instead of the right-aligned parenthetical.
- **Effort:** S–M (1–2 days). **Risk:** low.
- **Non-breaking notes:** Keep canonical order, `book.id` keys, and the full-row click. Sticky headers are a LazyColumn API change; no data change.

#### F-21 — Book Introduction loses context and hierarchy

- **Evidence:** `BookIntroductionScreen.kt:55–68` (title "Introduction"), `:73–79` (error, no retry), `:100–107` (all blocks `bodyLarge`, renderer ignores heading styling).
- **Impact:** The reader cannot tell which book's introduction they are in; long introductions have no headings; a failure is unrecoverable; the line length is unconstrained.
- **Recommendation:** Title = book name + "Introduction"; cap the measure at 620 dp; render headings with a serif `titleMedium`/`titleLarge`; add Retry; add an end-of-introduction link to chapters. Keep the empty state, but only reachable when an introduction exists (or explain it).
- **Effort:** S (1 day). **Risk:** low.
- **Non-breaking notes:** Use `BibleCatalog.byId(state.bookId)` for the title; no ViewModel change. If heading styling needs renderer metadata, it can be done in `BibleHtmlText` by splitting documents, or by accepting a heading style map — do not change the `AnnotatedString` text content.

#### F-22 — Search results do not support scanning

- **Evidence:** `SearchResultItem.kt:31–69` (book + reference + 3-line snippet, no highlight, no divider); Konkani-only book name; no count; no English secondary.
- **Impact:** In a dense result list, users cannot quickly see why a row matched or which book it is in English. Rows blur together without dividers.
- **Recommendation:** Highlight the query terms in the snippet with a theme-aware span (case/diacritic-insensitive matching, careful with Konkani diacritics), add a hairline divider, show "N results · first 50 shown", and add the English book name as a secondary line. Keep snippets to 3 lines.
- **Effort:** S–M (1–2 days). **Risk:** low.
- **Non-breaking notes:** `SnippetBuilder` output is plain text and tests assert no HTML; highlighting is a UI `AnnotatedString` transform over `snippet`, not a data change. Keep the result key.

#### F-23 — No text selection, copy, or share

- **Evidence:** All scripture is rendered with `Text` (`BibleHtmlText.kt:30–35`, `VerseBlock`), with no `SelectionContainer` or custom actions.
- **Impact:** Users cannot copy a verse into a message, notes, or a homily, which is a common reason to use a Bible app on a phone. Long-press currently does nothing.
- **Recommendation:** Wrap the reading column (or individual verses) in `SelectionContainer` and add a verse action row/sheet with Copy, Share, and Bookmark. Use the existing `Verse.plainText` for clipboard text (no HTML). A share intent is framework-only.
- **Effort:** S–M (1–2 days). **Risk:** low–medium.
- **Non-breaking notes:** `SelectionContainer` can interfere with the tap-to-select bookmark gesture and the horizontal swipe. Prototype the gesture arbitration: tap = select verse, long-press = text selection or action menu, swipe = chapter. Keep the plain text from the domain object; never copy HTML.

#### F-24 — Locale, plurals, and copy are not resource-safe

- **Evidence:** Hard-coded English in ViewModels: `ReaderViewModel.kt:117`, `:121`; `SearchViewModel.kt:98–107`; `DailyReadingViewModel.kt:83–89`; `BookChaptersViewModel.kt:44`; `BookIntroductionViewModel.kt:42`; `ReferenceEntryViewModel.kt:62`. Strings in `strings.xml` have no plurals and only a default locale. The date formatter uses the default locale (`DailyReadingScreen.kt:232–234`). The reference hint is English-only.
- **Impact:** Localization is blocked; error text can expose technical wording; plural forms read as "book(s)"; dates can mix languages/digits; Konkani-first users meet English-only examples.
- **Recommendation:** Move every user-visible string to `strings.xml`, add `plurals` for result/book counts and "Chapter(s)", pin the date formatter to the UI locale (English for now) until full localization, and make the reference hint bilingual ("Juanv 1:5 or James 1:5"). Keep the UI English but structure resources for a future `values-kok`.
- **Effort:** S–M (1–2 days). **Risk:** low.
- **Non-breaking notes:** Do not change the existing test-visible strings; add new resources. ViewModel messages become resource ids or domain error kinds mapped in the UI; this is a slightly larger refactor than moving literals. Keep English text identical where tests match it.

#### F-25 — Poetry and chapter header duplication

- **Evidence:** `PsalmPoetry.kt:27–28` centers unaligned Psalm verses; `ChapterHeader` draws a 28 sp numeral (`ReaderComponents.kt:70–78`); corpus headings already contain "Ovesvor N" (green) at the start of each chapter.
- **Impact:** Every chapter shows the number twice, in two type styles and two colors. Centered long poetic lines at 200 % font scale wrap with ragged both edges and no stanza rhythm; the large numeral consumes vertical space without adding context.
- **Recommendation:** Keep centering for poetry but cap its measure at ≈480 dp, increase line height slightly (19/31), and preserve stanza blank lines. Restyle the app `ChapterHeader` as a small overline ("CHAPTER 3") or remove it in favour of the corpus heading (a content-model change; prefer restyling). Style the "Ovesvor N" heading as the chapter title after color mapping.
- **Effort:** S (0.5–1 day). **Risk:** low.
- **Non-breaking notes:** Do not remove the `Header` item without updating `ReaderContentBuilderTest` (it asserts `chapter-header` first). Restyling is presentation-only. Do not flatten `<br/>`.

#### F-26 — Accessibility semantics coverage is thin

- **Evidence:** Only `BooksScreen` sets a `role`; no `heading()` anywhere; no `stateDescription`; no custom actions; icon buttons have descriptions; candidate rows are 42 dp (`ReferenceEntryScreen.kt:175–183`); chapter cells are 40 dp; verse rows are ≈36 dp.
- **Impact:** TalkBack cannot navigate by heading in a 3,258-heading corpus; selection/bookmark state is silent; swipe navigation is unavailable to screen-reader users; some targets are below 48 dp; reading order is not explicitly controlled in ambiguous lists.
- **Recommendation:** Add `Modifier.semantics { heading() }` to chapter/section headings; add state descriptions and custom actions ("Next chapter", "Previous chapter", "Bookmark verse") to the reader; raise reference candidates and chapter cells to 48 dp; label the search field; keep decorative icons `contentDescription = null`; ensure the verse row merges its number and text (it does today because it is clickable — verify on device).
- **Effort:** S–M (1–2 days). **Risk:** low.
- **Non-breaking notes:** Semantics additions are additive; do not replace test-visible text labels. Custom actions do not change touch behavior. If the verse row stops being clickable, explicitly set `mergeDescendants = true` and a content description so the number and text are read together.

### 5.4 P3

Each entry states the evidence, impact, and recommendation; the table adds delivery constraints.

- **F-27 — Copy and label polish.** "Daily Reading" vs "Continue reading" vs "Go to reference" mixes title and sentence case; "Ch 3" is cryptic; "Chapter 0" appears during load; "Bible" is vague. Standardize sentence case ("Daily reading", "Go to reference", "Continue reading"), use "Chapter 3" with a chevron, and remove the zero fallback. Keep test-visible labels unchanged.
- **F-28 — Row affordances and dividers.** Search results, bookmark rows, Daily readings, and reference candidates have no hairline dividers and inconsistent press affordances; Daily rows look like static text but are clickable. Add 1 dp `outlineVariant` dividers inset to the text start and a consistent ripple.
- **F-29 — Reading progress and position.** No "Chapter 3 of 21", no progress bar within a chapter, no "x min read". A subtle chapter-position line in the reader content header would orient users without adding chrome.
- **F-30 — RTL and bidi safety.** `supportsRtl="true"` is declared, directional icons are auto-mirrored (good), but there is no RTL testing and references like `%1$d:%2$d` are bidi-ambiguous. Since the UI is English and Konkani is LTR, this is low priority; either set `supportsRtl="false"` until tested or add bidi isolation (`\u2068…\u2069`) to reference labels.
- **F-31 — Book index / fast scroll.** 73 books in a flat scroll; add sticky sections and an A–Z index or a jump-to-testament control. This pairs with F-20.
- **F-32 — Search history and recents.** No recent queries or recently opened books on Search/Home. A small "Recent" section (in-memory or `SharedPreferences`) would reduce repeated typing; no DB change needed.

| ID | Effort | Risk | Non-breaking notes |
|---|---|---|---|
| F-27 | S | Low | Keep `home_bible`, `home_search`, `home_bookmarks`, `search_no_results` and the notice text unchanged; change only the other labels. "Chapter 0" removal is inside `ReaderScreen` and has no test dependency. |
| F-28 | S | Low | Dividers/ripples are visual; keep row keys, click handlers, and content descriptions. Do not insert a divider node into the semantics tree that announces itself. |
| F-29 | S | Low | Use the chapter list already in `ReaderUiState.chapters` to compute "N of M"; do not add a data call per scroll. Keep it out of the app bar. |
| F-30 | S | Low | If `supportsRtl` is changed to false, note it in the manifest and README; if bidi isolation is added, keep the visible string identical for tests. |
| F-31 | S–M | Low | `stickyHeader` changes only the LazyColumn DSL; keep `book.id` keys and canonical order. An A–Z index is additive. |
| F-32 | S | Low | Store recents in memory or `SharedPreferences`; do not add a Room table. Do not show a recent query as a second node containing the current query text (it can confuse the existing "subhagi" count assertion). |

---

## 6. Proposed visual system

### 6.1 Direction: "editorial breviary"

Commit to a printed-book aesthetic rather than app-card aesthetics:

- **Paper and ink, not cards.** Near-white paper in light, true black in dark, hairlines instead of shadows, generous margins.
- **Serif for the Word, sans for the apparatus.** Scripture and headings are serif; references, metadata, buttons, and controls are sans. This is the single biggest perceptual upgrade and it uses only existing font families.
- **Numerals as navigation.** Chapter numbers, verse numbers, and destination markers are typographic (serif numerals, tabular figures), not a dependency on a large icon set. This is distinctive and fits the "no new dependencies" constraint.
- **Blue as a ribbon, red as the Word.** Primary blue marks place/navigation; the corpus red marks the words of Jesus, but in a readable, theme-aware crimson. Green/blue/navy corpus headings are remapped to quiet, accessible roles, not neon.
- **Quiet motion.** Page-turn-like horizontal transitions, short fades, no bouncing, no ornamental animation.
- **Generous negative space in the reader; controlled density in lists.** The reader is a page; Books/Search/Bookmarks are indexes.

This direction is compatible with the existing palette, AMOLED choice, and shape set, and it does not require a redesign of the architecture.

### 6.2 Color roles

Keep existing roles unless noted. Proposed additions:

| Role | Light | Dark | Usage | Contrast vs background |
|---|---|---|---|---|
| paper / background | `#FAF9FF` | `#000000` | Existing | — |
| surface | `#FFFFFF` | `#000000` | Existing | — |
| ink | `#1A1B20` | `#E4E1E9` | Body/UI text | 15.5 / 15.3 |
| muted | `#44464F` | `#C4C6D0` | Metadata | 8.98 / 12.34 |
| hairline | `#D9D7E0` | `#33343A` | Dividers, bars | decorative |
| accent (primary) | `#0B57D0` | `#ADC6FF` | Links, selected filter, focus | 6.10 / 12.30 |
| accent container | `#D8E2FF` | `#004494` | Filled chips/CTAs only with on-container | 1.23 / 2.25 (not for text surfaces) |
| selection surface | `#E7EEFB` | `#1B2738` | Tapped verse | ink 14.75 / 11.65 |
| target surface | `#DCE9FF` | `#22324C` | Search/bookmark target | ink 14.03 / 9.98 |
| bookmark marker | accent | accent | Gutter marker | 6.10 / 12.30 |
| scripture red | `#B3261E` | `#FFB4AB` | Words of Jesus | 6.24 / 12.37 |
| scripture green | `#0E6B36` | `#7EDCA0` | Corpus chapter labels | 6.32 / 12.63 |
| scripture blue | `#0B57D0` | `#ADC6FF` | Corpus blue | 6.10 / 12.30 |
| scripture navy | `#22315C` | `#A9B8E8` | Corpus subheadings | 12.07 / 10.71 |
| scripture mid-blue | `#005A8C` | `#9CCBF0` | Corpus mid-blue | 7.06 / 12.21 |
| warning | `#8A5300` | `#FFB86B` | Partial results, stale data | 6.05 / 12.32 |
| success | `#0E6B36` | `#7EDCA0` | Saved/offline confirmation | 6.32 / 12.63 |
| error | `#BA1A1A` | `#FFB4AB` | Existing | 6.0 / 12.37 |

Rules:

- Never use `primaryContainer` at low alpha for state. Use solid surfaces.
- Never use `tertiary` green for warnings; it reads as success. Add a warning role.
- Red-letter is a setting. Default on, but the app must look intentional with it off.
- Every color pair used for text must pass 4.5:1 against its actual surface, including selection/target surfaces.
- Do not change the existing pinned values in `ThemeColorsTest`; add roles around them.

### 6.3 Type scale

Define every style used. Keep `bodyLarge` (17/28 serif) and `headlineMedium` (28/36 serif) unless re-tuning the whole scale.

| Token | Family | Size / line | Weight | Usage |
|---|---|---|---|---|
| `displaySmall` | Serif | 34 / 42 | SemiBold | Home hero only |
| `headlineMedium` | Serif | 28 / 36 | SemiBold | Page titles / chapter numeral (existing) |
| `headlineSmall` | Serif | 24 / 32 | SemiBold | Book title, celebration name |
| `titleLarge` | Serif | 22 / 30 | SemiBold | Section title (existing) |
| `titleMedium` | Serif | 17 / 24 | SemiBold | Reader section headings, testament headers |
| `titleSmall` | Sans | 15 / 22 | Medium | Row titles, reading types |
| `bodyLarge` | Serif | 17 / 28 | Normal | Introductions, prose (existing) |
| `bodyMedium` | Sans | 15 / 24 | Normal | UI secondary text, snippets |
| `bodySmall` | Sans | 13 / 20 | Normal | Metadata, English book names |
| `labelLarge` | Sans | 14 / 20 | Medium | Buttons, chips |
| `labelMedium` | Sans | 12 / 16 | Medium | Verse numbers, overlines |
| `labelSmall` | Sans | 11 / 16 | Medium | Legal/attribution |
| `Scripture` | Serif | 19 / 32 | Normal | Scripture body (existing `BibleTextStyle`) |
| `ScripturePoetry` | Serif | 19 / 31 | Normal | Psalms, centered, max 480 dp |
| `VerseNumber` | Sans | 12 / 19 | Medium | Tabular figures, right-aligned |
| `UIOverline` | Sans | 11 / 16 | Medium, 1.2 sp tracking, uppercase | Home overline, section labels |

Notes:

- Serif = `FontFamily.Serif` (Noto Serif on Android) today. If the team wants a distinctive voice, bundle **Literata** or **Source Serif 4** (one regular + one semibold, Latin Extended for Konkani diacritics). This adds ~300–600 KB to a 12.2 MB APK and no dependency. Test `ô`, `ñ`, `ê`, `’`, and `–` before committing. This is optional; the system Noto Serif is perfectly serviceable.
- Use tabular figures for verse numbers (`fontFeatureSettings = "tnum"` where supported) so the gutter is stable.
- Do not use `TextAlign.Justify` for scripture; the corpus does not use it, and justification creates rivers in narrow Konkani lines.

### 6.4 Reading-optimized passage style

| Property | Value | Rationale |
|---|---|---|
| Measure | `widthIn(max = 620.dp)`, centered | ≈67 characters; 38 at 393 dp portrait is unchanged |
| Side padding | 20 dp; 16 dp at font scale ≥1.5 | More text at large fonts without abandoning margins |
| Scripture size | 19 sp / 32 sp (1.68) | Existing; comfortable for Noto Serif and Konkani diacritics |
| Verse spacing | 4 dp between verses; 24 dp before heading, 8 dp after | Paragraph flow instead of list fragments |
| Verse gutter | `30.dp × fontScale.coerceAtMost(2f)`, right-aligned, top-aligned | Stable left edge; 3 digits fit at 200 % |
| Verse number | 12 sp, muted, tabular | Present but subordinate |
| Heading style | Serif 17/24 SemiBold (`titleMedium`), mapped colors | Hierarchy without neon |
| Poetry | Centered, max 480 dp, 19/31, stanza blank lines preserved | Fidelity to `<br/>`; narrower measure |
| Red-letter | `ScriptureRed` role, setting-controlled | Readable in both themes |
| Selection | Solid surface + 2 dp accent bar in gutter | Visible (not 1.05:1) |
| Bookmark | Small marker in gutter + state description | Persistent, not color-only |
| Alignment | Start (except poetry) | No justification |
| Hyphenation | Optional `Hyphens.Auto`; validate on device | Long Konkani words can cause ragged lines |

Passage mode: render a sticky banner (`Verses 5–8 · Show full chapter`) and keep the chapter context in the app bar. If the passage is a bookmark, prefer opening the full chapter at the verse with a range highlight; if the team keeps filtered mode, the banner is the minimum.

### 6.5 Spacing, shape, elevation

- **Spacing scale:** 4, 8, 12, 16, 20, 24, 32, 40, 56 dp. Replace the current one-off 6/10/14/28 values with the nearest step.
- **Screen padding:** 20 dp reader, 16 dp lists, 24 dp settings/about. No edge-to-edge text.
- **Rows:** 56 dp min for list rows; 64 dp for two-line rows; 48 dp min for chapter cells and reference candidates; 12 dp vertical padding for rows (not 14/10/6 per screen).
- **Shapes:** add `extraSmall = 2.dp` (accent bars); keep `small = 4.dp` (verse highlight), `medium = 8.dp` (chips? use pill for filter chips), `large = 12.dp` (cards/sheets). Bottom sheets: 16 dp top corners.
- **Elevation:** 0 for content and reader; 0 with hairlines for top/bottom bars; tonal surface for bottom sheets and snackbars. No shadows on AMOLED.
- **Dividers:** 1 dp `outlineVariant`/hairline, inset 16 dp from the text start; use them in all list rows and under top bars.

### 6.6 Component patterns

**Top bars**

- One line only: back arrow (48 dp), title `titleMedium`/`titleLarge` `maxLines = 1`, `overflow = Ellipsis`, 1–2 actions.
- No two-line titles; no fixed multi-line content in a 64 dp bar. Use `heightIn(min = 64.dp)` only if the title is guaranteed one line.
- Back arrow always `contentDescription = "Back"`.
- Books/Search get a back arrow and a search action.

**Reader bottom bar (new)**

```
[ ‹ previous ]   [ Ch 3 ▾ ]   [ next › ]   [ bookmark ]
```
- 56 dp + navigation-bar inset; tonal surface + top hairline.
- Icons 24 dp, labels optional at 1×; content descriptions always.
- Chapter button opens a grid bottom sheet (4–5 columns, current chapter marked, "Chapter N of M").
- Bookmark applies to the selected verse; if none is selected, show a short helper ("Tap a verse to bookmark") or open a bookmark of the current chapter start? Prefer the helper, not a silent no-op.
- This is the primary one-handed win on the POCO.

**List row**

- 56/64 dp; leading slot (optional numeral/monogram), title `titleSmall`, subtitle `bodySmall`, trailing chevron/action; hairline divider; whole row clickable with a merged semantics label.
- Book row: Konkani name (serif 17) + English name (sans 13) stacked, not right-aligned parenthetical. This fixes large-font truncation and reads like a bilingual index.
- Search result: book + reference line; snippet with highlighted terms; divider.
- Bookmark: book + reference + saved date; trailing overflow or swipe; marker.
- Daily reading: type label (overline) + reference (titleMedium for the Gospel, bodyLarge for others).

**Chips / segmented controls**

- Filter chip for the search book filter ("All Bible ▾" / "Jakobachem ×"), 32 dp visual height with 48 dp touch target.
- Segmented control for Search vs Reference (later), Old/New Testament (Books), and text-size presets (Settings).
- Selected chip uses `secondaryContainer` with `onSecondaryContainer`; do not use primary blue text on a blue container.

**Cards**

- Only two cards: "Continue reading" on Home and the Gospel on Daily Reading. `Surface` with `surfaceContainerLow` (add a deliberate value), 12 dp radius, 16 dp padding, no shadow, one accent rule.
- Everything else is a row or a page, not a card.

**States**

- `LoadingState`: skeletons for lists (3–5 shimmerless placeholder rows with `surfaceVariant` at low emphasis), centered spinner only for cold start; `LinearProgressIndicator` for in-place search/chapter loads.
- `EmptyState`: 40–48 dp line icon or serif monogram, `titleMedium`, one-sentence body, optional text button.
- `ErrorState`: same layout, warning/error icon, message in `onSurface` (not all red), retry button; never raw exception text.
- `Snackbar`: confirmation + Undo for bookmark add/delete; 4 s; accessible announcement.

**Iconography**

- 24 dp, 2 dp stroke, rounded; directional icons `AutoMirrored`.
- Add two vector drawables: `ic_bookmark_border_24` and `ic_bookmark_24` (Material Symbols paths). Do not add Material Icons Extended for two icons.
- Star is reserved for "favorite" and should not be used for bookmarks.
- Typographic markers ("01"–"05") for Home tiles avoid an icon dependency and reinforce the editorial direction.
- Decorative icons get `contentDescription = null`; icon-only actions always have a description.

### 6.7 Motion guidelines

| Moment | Motion | Duration |
|---|---|---|
| Screen push/pop | Fade + 12 % horizontal slide | 180–220 ms |
| Reader chapter change | Horizontal slide + fade, direction-aware | 160–180 ms |
| Target verse reach | Instant jump, then highlight fade-in | 200 ms in, 1.6 s hold, 300 ms out |
| Verse selection | Background color transition | 150 ms |
| Bookmark toggle | Icon scale 0.9 → 1.0 + color | 120 ms |
| Home first paint | Optional 40 ms stagger, max 240 ms total | — |
| Snackbar | Standard M3 | — |

- Easing: `FastOutSlowInEasing` / `LinearOutSlowInEasing`; no spring bounce for reading chrome.
- Honor the system animation scale; skip non-essential animation at 0.
- Never animate text size, line height, or list reordering; never use infinite pulsing.

### 6.8 Dark mode and large fonts

- Keep AMOLED black; separate bars with hairline dividers, not gray surfaces.
- Map all corpus colors per theme (F-02).
- Test at 1.0, 1.3, and 2.0 font scale in portrait and landscape.
- No fixed heights around text: app bars one line; chapter cells grow; bottom bar uses icons with descriptions; list rows stack.
- At 200 %, hide non-essential secondary lines (English book names, subtitles) rather than truncating the primary text.
- Chapter grid columns: 4 at 1×, 3 at ≥1.5× (font-scale-aware `minSize`).
- Home becomes scrollable; the notice remains reachable (pinned or first-viewport).

---

## 7. Delivery plan

### 7.1 Quick wins (≤1 day each, sequenced)

| # | Change | Findings | Effort | Depends on |
|---|---|---|---|---|
| 1 | Home: `safeDrawingPadding` + scroll container + compact grid | F-01 | 0.5–1 d | — |
| 2 | Books: render immediately, progressive titles, error/retry | F-03 | 0.5–1 d | — |
| 3 | Reader: back arrow, single-line title, remove "Chapter 0" | F-04 (interim) | 0.5 d | — |
| 4 | Theme-aware scripture palette + red-letter toggle groundwork | F-02 | 1–2 d | Contrast test |
| 5 | Solid selection/target/bookmark surfaces + state descriptions | F-06 | 0.5–1 d | Palette |
| 6 | Reader spacing, measure cap, fixed verse gutter | F-08 | 0.5–1 d | Type scale |
| 7 | Chapter cells 48 dp + font-scale-aware columns + empty state | F-14 | 0.5–1 d | — |
| 8 | Search/Reference IME actions, `imePadding`, label | F-13 | 0.5 d | — |
| 9 | Search: keep previous results, count/cap line, retry | F-13/F-22 | 0.5–1 d | — |
| 10 | Bookmark snackbar + Undo (both screens) | F-09 | 1 d | Vector drawables |
| 11 | Full type scale | F-07 | 1 d | — |
| 12 | Back arrows on Books/Search + search action | F-10 | 0.5 d | — |
| 13 | Shared Empty/Error/Loading components; add retry to Introduction/Search | F-16 | 1 d | — |
| 14 | Bibliographic naming: one display-name cache | F-12 | 2–3 d | — |

**Sequencing principle:** palette and type scale first (they change every screen's appearance), then layout fixes, then interaction additions. Do not start the Home redesign before F-01's inset/scroll fix and a test-tag plan, or the existing UI test will be hard to keep green.

### 7.2 Medium changes (2–5 days)

1. **Reader bottom action bar + chapter grid sheet** (F-04, ~3 d). Depends on the interim back arrow and test tags. Biggest one-handed usability win.
2. **Passage banner + target-missing notice + bookmark navigation decision** (F-05, 2–3 d). Requires a product decision and test updates.
3. **Home editorial redesign** (F-11, 2–3 d). Depends on F-01, F-07, and F-12; keep labels/notice.
4. **Settings & About** (F-15, 3–5 d). Depends on the palette/type work; adds a route and persisted preferences.
5. **Daily Reading hierarchy + day navigation + offline badge** (F-18, 3–5 d). Repository already supports arbitrary dates.
6. **Books scanning aids** (F-20/F-31, 2 d): sticky headers, last-read marker, search action.
7. **Book Introduction restyle + retry** (F-21, 1 d).
8. **Text selection/copy/share with gesture arbitration** (F-23, 2–3 d).

### 7.3 Larger explorations

- **Reading experience package (1–2 weeks):** bundled Literata/Source Serif 4, in-reader text size/line spacing/margins, sepia/paper themes, red-letter toggle, reading progress, verse action sheet. This is the natural v1.2 flagship.
- **Unified "Find" screen (3–5 d):** Search + Reference as two modes of one screen with a segmented control, keeping both routes and the resolver. Improves discoverability and reduces duplicate input UX.
- **Tablet/foldable two-pane (1–2 weeks):** Books + Reader side by side using `BoxWithConstraints` (no new dependency). The current architecture supports it because content is route-driven.
- **Liturgical calendar (1–2 weeks):** date navigation, caching indicators, season colors, "today" jump. Builds on the existing cache-first repository.
- **Konkani UI localization (2–3 weeks):** `values-kok`, plurals, bilingual labels, localized dates. The corpus is Konkani; the UI should eventually meet it halfway.

### 7.4 Dependencies and risks

- Palette → all reader state and color work.
- Type scale → measure/flow tuning and bar heights.
- Test tags → any structural Home/Reader change.
- Product decision on bookmark navigation semantics → passage work.
- New route (Settings/About) → `AppDestinationTest` update.
- Do not parallelize the Home redesign and the Reader chrome on the same day without rebasing; both touch top-level layout and test-sensitive semantics.

---

## 8. Accessibility checklist and validation plan

### 8.1 Static checklist (per screen)

| Check | Target | How |
|---|---|---|
| Text contrast | ≥ 4.5:1 normal; ≥ 3:1 large/bold | Computed pairs in §6.2; add a JVM test |
| Non-text contrast | ≥ 3:1 for selection, focus, icons, borders | Measure composited state surfaces |
| Touch targets | ≥ 48 × 48 dp | Chapter cells, reference candidates, Home buttons, row actions |
| Font scale 200 % | No clipping, no lost actions, no horizontal scroll | Manual + Compose test with `Density(fontScale = 2f)` on stateless content |
| Landscape | Core tasks still completable; no clipped nav | `user_rotation 1` |
| TalkBack | Every action reachable; labels meaningful; headings navigable | Manual script §8.2 |
| State announcements | Selected/bookmarked/target announced | `stateDescription` |
| Reading order | Top bar → banner → content → bottom bar; lists in visual order | TalkBack sweep |
| Icon descriptions | Icon-only actions labeled; decorative icons null | Code review |
| Color-only meaning | No state conveyed only by color | Red-letter also has a setting; bookmark has a marker; selection has a bar |
| Keyboard/IME | Action submits; results not covered | Manual with Gboard |
| RTL | Layout does not break; bidi reference strings | Low priority; test or disable `supportsRtl` |
| Motion | No essential info only in animation; honors animation scale | Manual at 0× |

### 8.2 TalkBack script

For each screen, enable TalkBack and record the focus order and announcements:

1. **Home:** app name → each destination (label + subtitle) → notice → About link. Confirm the notice is announced as text, not swallowed by a clickable parent; confirm no duplicate "Bible" labels.
2. **Books:** top bar title → back → each testament header (should be a heading) → each book row ("Fuddareañachem Pustok, Judges, button") → end. Confirm the English name is included and the row has a button role.
3. **Chapters:** title → back → Introduction → chapter grid (each cell announces its label, including "(1)" duplicates) → end. Confirm 48 dp targets and row/column order.
4. **Reader:** back → title ("Juanv, chapter 3") → banner (if passage) → chapter heading (heading) → each verse ("Verse 3, <text>") → bottom bar actions. Tap a verse: confirm "Selected" is announced; bookmark: confirm "Bookmarked"/"Removed" is announced and focus does not jump. Confirm heading navigation works for section titles. Confirm swipe-only chapter navigation has button/custom-action equivalents.
5. **Search:** field label → typed value → clear → filter chip → result count → each result (book, reference, snippet) → retry (if error). Confirm the keyboard action announces "Search" and submits.
6. **Reference:** field label/hint → Go → resolution message/candidates (each 48 dp, announced as buttons) → back.
7. **Bookmarks:** title → back → count/group headers → each row (book, reference, saved date) → delete action (announced "Remove bookmark") → Undo snackbar.
8. **Daily Reading:** title → back → date (announced as a date, not a raw pattern) → celebration → season → Gospel CTA → readings → offline badge.
9. **Introduction:** book name + "Introduction" → back → headings (heading role) → body.

Record failures as: screen, focus index, announcement, expected, severity.

### 8.3 Font scale and orientation matrix

Run on the POCO M2 Pro (and one small-width emulator if available):

| Configuration | Screens | Pass criteria |
|---|---|---|
| 1.0× portrait, light | All | Baseline; existing UI tests green |
| 1.0× portrait, dark | All | No invisible text; bars separated; states visible |
| 1.3× portrait, light/dark | Home, Reader, Chapters, Search | No clipping; chapters 3–4 columns |
| 2.0× portrait, light/dark | Home, Reader, Chapters, Search, Bookmarks | All actions reachable; no text clipped; no horizontal overflow |
| 1.0× landscape | Home, Reader, Bookmarks | Home scrolls; reader measure capped; no clipped chrome |
| 2.0× landscape | Home, Reader | Core tasks completable; actions reachable |
| 3-button navigation | Home, Reader | Notice and bottom bar above the nav bar |
| Gesture navigation | Reader | Edge swipe does not accidentally change chapter |

Commands (device-specific; MIUI menus may differ):

```bash
adb shell settings put system font_scale 2.0
adb shell am force-stop com.biblia.povitrpustok
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1   # landscape
adb shell settings put system user_rotation 0
```

### 8.4 Automated tests

**Keep the existing tests green.** The current instrumentation suite is 19 tests (3 UI); the JVM suite is 381 tests. Add:

1. **Contrast contract (JVM, no Android UI):** extend the `ThemeColorsTest` pattern with a WCAG ratio helper; assert every text/surface pair in §6.2, including the scripture palette mapped over both backgrounds and the selection/target surfaces.
2. **Palette mapping (JVM):** assert every corpus color maps to the expected role in light/dark; unknown colors below 4.5:1 fall back to `onBackground`.
3. **Stateless layout tests (Compose):** extract `HomeContent`, `ReaderContent`, `ChapterGrid`, `SearchResults` as stateless composables and wrap them in `CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f))`. Assert every important node is displayed (`assertIsDisplayed`) and clickable.
4. **Test tags:** add stable tags alongside visible text:
   - `home.bible`, `home.search`, `home.bookmarks`, `home.continue`, `home.daily`, `home.reference`, `home.notice`
   - `books.row.<bookId>`, `books.header.<testament>`
   - `chapters.cell.<position>`, `chapters.introduction`
   - `reader.topbar`, `reader.back`, `reader.bookmark`, `reader.chapterPicker`, `reader.verse.<verseId>`, `reader.banner`
   - `search.field`, `search.filter`, `search.result.<bookId>.<chapter>.<verse>`, `search.count`
   - `reference.field`, `reference.go`, `reference.candidate.<id>`
   - `bookmarks.row.<id>`, `bookmarks.delete.<id>`
   - `daily.reading.<type>`, `daily.badge`
5. **Navigation tests:** keep the three existing tests; add one per new route/action (Settings/About, passage banner, bookmark undo). Use test tags in new tests; do not rewrite the existing text-based tests unless you also update the exact strings.
6. **Gesture tests:** preserve `SwipeNavigationTest`; if edge-swipe rejection is added, extend the pure function tests with start-x cases.
7. **Regression guard for naming:** a test that compares `BibleCatalog.displayName` to the corpus title (normalized) so F-12 cannot silently drift.

Example test (illustrative, not applied):

```kotlin
@Test
fun home_at_200_percent_keeps_notice_and_bookmarks_visible() {
    composeRule.setContent {
        CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 2f)) {
            BibleTheme { HomeContent(state = HomeUiState(), onOpenBooks = {}, /* … */) }
        }
    }
    composeRule.onNodeWithTag("home.bookmarks").assertIsDisplayed()
    composeRule.onNodeWithTag("home.notice").assertIsDisplayed()
}
```

### 8.5 Usability test script

**Participants:** 5–8 readers (mix of Konkani-first and English-first; at least two who use large system fonts; at least one TalkBack user if possible).

**Tasks (think-aloud, on the POCO):**

1. "Open the app and continue where you left off." → measures Continue discoverability/context.
2. "Find Juanv 3:16." → Reference or Books; measures naming consistency and input ergonomics.
3. "Search for a Konkani word and open one result." → measures search discoverability, keyboard behavior, result scanning.
4. "Bookmark a verse and then find it in Bookmarks." → measures tap-to-select discoverability, star icon, list recognition.
5. "Read today's Gospel." → measures Daily Reading hierarchy, offline/error comprehension.
6. "Make the scripture text bigger." → measures Settings/About discoverability (expected to fail today; confirms the gap).

**Metrics:** completion, time on task, taps, errors, confidence (1–5), verbatim quotes. **Success thresholds:** 5/5 for tasks 1–4 after the redesign; 4/5 for task 5; task 6 only after Settings ships. Include a post-task question about the red text and the chapter navigation model ("How would you go to the previous chapter? How would you leave the reader?").

---

## 9. Do-not-break invariants and migration notes

### 9.1 Test-visible strings and semantics (current)

| Test | Depends on |
|---|---|
| `HomeNavigationUiTest.home_shows_attribution_and_bookmarks_back_returns_home` | Exact notice text `Bible text © Archdiocese of Goa and Daman. All rights reserved.` displayed; text "Bookmarks" clickable; content description "Back" on the Bookmarks screen; notice displayed again after back |
| `HomeNavigationUiTest.search_returns_james_results` | Text "Search" clickable; a node with `hasSetTextAction()`; typing "subhagi"; ≥2 nodes containing "subhagi"; "No results found." absent |
| `HomeNavigationUiTest.system_back_returns_home_from_books` | Text "Bible" clickable; system back returns Home; notice displayed |
| `AppDestinationTest` | Exact route strings; 9 distinct route constants; no content in routes |
| Reader tests | `chapter-header` first; item keys; position-based previous/next; passage filtering; target one-shot; bookmark toggle; duplicate chapter labels |
| `BibleHtmlRendererTest` | Exact rendered text; exact `#0000FF` span color; center alignment; `<br/>` preservation |
| `PsalmPoetryTest` | Psalms-only centering; explicit alignment wins |
| `BookDisplayNameTest` | Capitalize-Case rules |
| `ChapterDisplayLabelTest` | `N (1)` suffix format |
| `ThemeColorsTest` | Exact `#000000` dark background/surface, `#0B57D0`/`#ADC6FF` primary, near-white light background, text luminance |
| `SearchViewModelTest` | Query/filter saved-state keys; debounce/cancellation; blank query behavior |
| `ReferenceEntryViewModelTest` | `reference_query` saved-state key; four resolution states |
| `BookmarkDestinationTest` | `ReaderDestination` mapping fields |

### 9.2 Invariants

- Routes and args exactly as in `AppDestination.kt`; adding a route requires updating `AppDestinationTest.route_constants_are_distinct` (currently expects 9).
- Reader navigation is by `chapter.position`; never by `chapter.number`.
- Bookmark identity is `bookId + chapterId + verseId`; no migration, no dedupe changes.
- `BibleHtmlRenderer` output is a tested contract; color adaptation happens after rendering.
- Passage filtering semantics remain test-covered; any change to bookmark destinations is a deliberate product decision with test updates.
- `BooksGrouping` uses `BibleCanonicalOrder`; do not use corpus testament metadata.
- `BookDisplayName.capitalizeDisplayName` is presentation-only; never write it back.
- Duplicate chapter labels keep the `N (1)` format.
- Reader scroll restoration is keyed by chapter id; target scrolling uses the same item list as rendering.
- The Home notice remains visible and exact; the app name remains "Povitr Pustok".
- No UI database access; no HTML in the UI layer; no corpus writes.

### 9.3 Safe migration pattern for test-visible strings

1. **Add** test tags and new semantics first. Run the existing tests unchanged.
2. **Refactor** layout/composition while keeping the same visible labels and content descriptions. Run again.
3. If a visible string must change, change it in `strings.xml` **and** in the test in the same commit; list the old/new strings in the commit message.
4. Never introduce a second node with exactly "Bible", "Search", or "Bookmarks" on Home (a bottom nav would do this). If a bottom nav is ever added, migrate the tests to `home.*` tags first.
5. Keep `hasSetTextAction()` available on Search. If you replace `OutlinedTextField`, verify the semantics.
6. Back arrows: keep `contentDescription = "Back"`; do not place two "Back" nodes on one screen.
7. If Home becomes scrollable, ensure the notice and "Bookmarks" are displayed in the initial portrait viewport at 1× (the test does not scroll).

### 9.4 Recommended non-breaking implementation order

1. Add test tags + stateless content extraction (no visual change).
2. Add the palette and type scale (visual only).
3. Fix insets/scroll/measure/spacing (visual only).
4. Add back arrows/search actions (additive).
5. Add state surfaces/descriptions (visual + semantics).
6. Add bookmark long-press/undo (additive interaction).
7. Add Reader bottom bar + chapter sheet (interaction; keep existing VM functions).
8. Add passage banner and bookmark destination decision (needs tests).
9. Redesign Home (highest test risk; last among the layout work).
10. Add Settings/About (new route; update route test).

---

## 10. Appendix — illustrative snippets and wireframes

> These are sketches to communicate intent, not applied code. Names are illustrative.

### 10.1 Reading container with a measure cap

```kotlin
@Composable
fun ReadingColumn(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit
) {
    val fontScale = LocalDensity.current.fontScale
    val side = if (fontScale >= 1.5f) 16.dp else 20.dp
    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxContent = 620.dp
        val horizontal = maxOf(side, (maxWidth - maxContent) / 2)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = horizontal, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}
```

At 393 dp this is unchanged (20 dp padding); in landscape/tablet it centers a 620 dp column.

### 10.2 Verse row with a stable gutter, state semantics, and long-press bookmark

```kotlin
@Composable
fun VerseRow(
    verse: Verse,
    isSelected: Boolean,
    isBookmarked: Boolean,
    isTarget: Boolean,
    onSelect: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fontScale = LocalDensity.current.fontScale
    val gutter = (30.dp * fontScale.coerceAtMost(2f))
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                when {
                    isTarget -> ReaderColors.target
                    isSelected -> ReaderColors.selected
                    isBookmarked -> ReaderColors.bookmarked
                    else -> Color.Transparent
                }
            )
            .then(
                if (isBookmarked) Modifier.drawBehind {
                    drawRoundRect(
                        color = ReaderColors.accent,
                        topLeft = Offset(0f, 4.dp.toPx()),
                        size = Size(2.dp.toPx(), size.height - 8.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx())
                    )
                } else Modifier
            )
            .combinedClickable(
                onClick = onSelect,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleBookmark()
                }
            )
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .semantics {
                stateDescription = buildString {
                    if (isSelected) append("Selected. ")
                    if (isBookmarked) append("Bookmarked. ")
                }.trim()
                customActions = listOf(
                    CustomAccessibilityAction("Bookmark verse") {
                        onToggleBookmark(); true
                    }
                )
            }
    ) {
        Text(
            text = verseLabel(verse),
            style = MaterialTheme.typography.labelMedium,
            color = ReaderColors.muted,
            textAlign = TextAlign.End,
            modifier = Modifier.width(gutter).padding(top = 5.dp)
        )
        Spacer(Modifier.width(10.dp))
        BibleHtmlText(
            document = rememberDocument(verse.html),
            style = MaterialTheme.typography.Scripture,
            modifier = Modifier.weight(1f)
        )
    }
}
```

Key details: fixed gutter (stable left edge), `combinedClickable` (keeps tap selection, adds long-press bookmark), solid state surfaces, a 2 dp accent bar for bookmarks, explicit state/action semantics. Verify gesture arbitration with `SelectionContainer` if F-23 ships.

### 10.3 Theme-aware scripture color mapping

```kotlin
data class ScripturePalette(
    val red: Color,
    val green: Color,
    val blue: Color,
    val navy: Color,
    val midBlue: Color,
    val fallback: Color
)

@Composable
fun rememberScripturePalette(): ScripturePalette {
    val dark = isSystemInDarkTheme()
    return if (dark) ScripturePalette(
        red = Color(0xFFFFB4AB), green = Color(0xFF7EDCA0),
        blue = Color(0xFFADC6FF), navy = Color(0xFFA9B8E8),
        midBlue = Color(0xFF9CCBF0), fallback = MaterialTheme.colorScheme.onBackground
    ) else ScripturePalette(
        red = Color(0xFFB3261E), green = Color(0xFF0E6B36),
        blue = Color(0xFF0B57D0), navy = Color(0xFF22315C),
        midBlue = Color(0xFF005A8C), fallback = MaterialTheme.colorScheme.onBackground
    )
}

fun AnnotatedString.mapCorpusColors(p: ScripturePalette, background: Color): AnnotatedString =
    AnnotatedString.Builder(this.text).apply {
        // Rebuild spanStyles, replacing exact corpus colors.
        val mapped = spanStyles.map { range ->
            val c = range.item.color
            val target = when (c.toArgb()) {
                0xFFFF0000.toInt() -> p.red
                0xFF00B050.toInt() -> p.green
                0xFF0000FF.toInt() -> p.blue
                0xFF021A56.toInt() -> p.navy
                0xFF0070C0.toInt() -> p.midBlue
                0xFF000000.toInt() -> p.fallback
                else -> c
            }.takeIf { contrast(it, background) >= 4.5f } ?: p.fallback
            range.copy(item = range.item.copy(color = target))
        }
        // …replace spanStyles with `mapped` (implementation detail)…
    }.toAnnotatedString()
```

Implementation notes:

- Do this in `BibleHtmlText` and include `palette/background` in the `remember` key.
- Do not modify `BibleHtmlRenderer`; its tests pin exact colors.
- Add a unit test for the mapping and the contrast fallback.
- If a bundled font or a theme change alters the background, the mapping must recompute.

### 10.4 Chapter cell (48 dp, no wasted content padding)

```kotlin
@Composable
fun ChapterCell(
    label: String,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
        }
    }
}
```

Use `GridCells.Adaptive(minSize = (72.dp * fontScale.coerceIn(1f, 1.6f)))` or a fixed 3/4-column choice based on `fontScale`.

### 10.5 Reader bottom bar (skeleton)

```kotlin
@Composable
fun ReaderBottomBar(
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    chapterLabel: String,
    isBookmarked: Boolean,
    hasSelection: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenChapters: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    Surface(tonalElevation = 0.dp) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious, enabled = canGoPrevious) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous chapter")
                }
                TextButton(onClick = onOpenChapters) {
                    Text(chapterLabel)          // "Ch 3"
                    Icon(Icons.Filled.ArrowDropDown, null)
                }
                IconButton(onClick = onNext, enabled = canGoNext) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next chapter")
                }
                IconButton(onClick = onToggleBookmark, enabled = hasSelection) {
                    Icon(
                        painter = painterResource(
                            if (isBookmarked) R.drawable.ic_bookmark_filled_24
                            else R.drawable.ic_bookmark_border_24
                        ),
                        contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark"
                    )
                }
            }
        }
    }
}
```

`Icons.Filled.Bookmark` is **not** in `material-icons-core`; use the two vector drawables instead, or reference your own `painterResource`.

### 10.6 Search result highlighting

```kotlin
fun highlight(snippet: String, query: String, color: Color): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(snippet)
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return buildAnnotatedString {
        append(snippet)
        terms.forEach { term ->
            var start = snippet.indexOf(term, ignoreCase = true)
            while (start >= 0) {
                addStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold), start, start + term.length)
                start = snippet.indexOf(term, start + term.length, ignoreCase = true)
            }
        }
    }
}
```

Konkani diacritics: case-insensitive matching is not diacritic-insensitive. If the query is unaccented and the snippet is accented (the corpus FTS removes diacritics), the client-side highlight may miss. Either precompute match ranges in the data layer or normalize both strings for matching while mapping ranges back to the original. Do not alter the snippet text.

### 10.7 Home wireframe (test-safe)

```
┌─────────────────────────────────────┐
│ KONKANI CATHOLIC BIBLE              │  overline, muted
│ Povitr Pustok                       │  displaySmall serif
│ ───                                 │  2 dp accent rule
│                                     │
│ Continue reading                    │  card (if state)
│ Jakobachem · 2 days ago             │
│                               ›     │
│                                     │
│ ┌───────────────┐ ┌───────────────┐ │
│ │ 01  Bible     │ │ 02  Search    │ │  primary / secondary
│ │ 73 books      │ │ word or verse │ │
│ └───────────────┘ └───────────────┘ │
│ ┌───────────────┐ ┌───────────────┐ │
│ │ 03 Bookmarks  │ │ 04 Daily      │ │
│ │ saved verses  │ │ Reading       │ │
│ │               │ │ today's Mass  │ │
│ └───────────────┘ └───────────────┘ │
│ ┌───────────────┐                   │
│ │ 05 Go to      │                   │
│ │    reference  │                   │
│ │ Juanv 3:16    │                   │
│ └───────────────┘                   │
│                                     │
│ Bible text © Archdiocese of Goa …   │  tappable → About
│ About & settings                    │
└─────────────────────────────────────┘
```

At 393 × 873 dp this fits in one viewport at 1×; at 2× it scrolls, but the notice and "Bookmarks" should still be reachable without ambiguity (the test runs at 1×). Keep exact labels; do not add a bottom nav with duplicate labels.

### 10.8 Corpus evidence queries (reproducible)

```bash
cd app/src/main/assets/bible
# Total headings/verses
for f in *.sqlite; do sqlite3 "$f" "select count(*) from headings;"; done | awk '{s+=$1} END {print s}'
for f in *.sqlite; do sqlite3 "$f" "select count(*) from verses;"; done | awk '{s+=$1} END {print s}'
# Heading colors (all spans)
for f in *.sqlite; do sqlite3 "$f" "select html from headings;"; done \
  | grep -o "#[0-9a-fA-F]\{6\}" | tr 'A-F' 'a-f' | sort | uniq -c | sort -rn
# Verse colors (all spans)
for f in *.sqlite; do sqlite3 "$f" "select text from verses;"; done \
  | grep -o "#[0-9a-fA-F]\{6\}" | tr 'A-F' 'a-f' | sort | uniq -c | sort -rn
# Records containing any color
for f in *.sqlite; do sqlite3 "$f" "select count(*) from headings where lower(html) like '%color=%';"; done | awk '{s+=$1} END {print s}'
for f in *.sqlite; do sqlite3 "$f" "select count(*) from verses where lower(text) like '%color=%';"; done | awk '{s+=$1} END {print s}'
```

Contrast formula: WCAG 2.1 relative luminance, `(L1 + 0.05) / (L2 + 0.05)`, with sRGB channel linearization. Alpha surfaces were composited channel-wise before measuring.

---

## 11. Ranked top-10 recommendations

1. **Map corpus colors to a theme-aware scripture palette** (F-02). Restores readability in both themes for thousands of headings/verses; highest impact per day of work. *M / medium risk / do first.*
2. **Make Home inset- and scroll-safe, then give it hierarchy** (F-01, F-11). Fixes unreachable actions in landscape/large fonts and turns a debug menu into a front door. *S–M / medium test risk.*
3. **Unblock the Books screen** (F-03). Render the catalog immediately, load titles progressively, and add an error path; removes a possible infinite spinner on first run. *S / low risk.*
4. **Give the Reader a back action and a thumb-zone chapter bar** (F-04). Fixes the "how do I leave?" problem, the 64 dp bar overflow, and one-handed use on a 6.67-inch phone. *M / low–medium.*
5. **Fix the reading surface: measure cap, verse flow, fixed gutter** (F-08). Turns a list of verse rows into a readable page at 1×, 2×, and landscape. *S / low.*
6. **Make selection/target/bookmark states visible and announced** (F-06). Replaces 1.05–1.18:1 tints with solid surfaces, a gutter marker, and `stateDescription`. *S / low.*
7. **Add passage context and fix bookmark destinations** (F-05). Users should never land in an unexplained one-verse fragment; surface `targetMissing` and offer "Show full chapter". *M / medium.*
8. **Complete the type scale** (F-07). One serif voice for the Word, one sans voice for the apparatus; fixes the Roboto/serif mismatch and small metadata. *S / low.*
9. **Polish search and reference input/result UX** (F-13, F-22). IME actions, keyboard insets, non-flickering results, count/cap, highlighting, bilingual filter and hint. *S–M / low.*
10. **Ship Settings & About with attribution, text size, theme, and red-letter controls** (F-15). Closes the biggest product/compliance gap and makes the reading experience adjustable. *M / medium.*

**Do first, in this order:** 1 → 2 → 3 → 5 → 6 → 4 → 8 → 9 → 7 → 10. Items 1, 2, 3, 5, and 6 are the "week one" set; they are mostly presentation-only and deliver most of the perceived quality lift. Items 4, 7, 9, and 10 need product decisions or test migrations and should follow with explicit acceptance criteria.

---

*End of document. All findings are derived from the inspected commit and the bundled corpus; no app code, resources, tests, or Gradle files were modified.*
