# 1.3.1 accessibility script (F2-08)

Status: executed 2026-10-30 on the API 35 emulator. This is a release check,
not a certification, and not a full accessibility audit. One phone plus the
API 35 emulator, per the project's one-device rule.

## Environment

- Device/emulator: `Pixel_8_Pro_API_35` (`emulator-5554`), 1344×2992, 480 dpi,
  Android 15 (API 35), gesture navigation.
- Build: `d0c6390` (`versionName` 1.3.0, debug), plus this lane's working-tree
  changes: the ATF dependency/test, `minimumInteractiveComponentSize()` on the
  Books testament segments (the P1 fix below), and this document.
- Font scale: 1.0 for the baseline pass, then 2.0 for the large-font pass.
- Theme: light and dark (`cmd uimode night`).
- Orientation: portrait and landscape (rotation locked for each pass).
- Width: default 448 dp and 320 dp (`wm size 960x2992` at 480 dpi).
- TalkBack: installed and enabled (`com.google.android.marvin.talkback`
  15.0.0.639625893 / 60138361). The emulator was launched with `-no-audio`, so
  announcements were read from TalkBack's on-screen "Display speech output"
  overlay and cross-checked against the accessibility node tree; they were not
  verified by listening.
- Animations: default, then all three scales at 0 ("Remove animations").
- Bold text: `font_weight_adjustment=200` for the bold pass.

## Manual script

| # | Flow | Check | Result |
|---|---|---|---|
| 1 | Home | TalkBack reads the masthead, one node per destination, and the Settings footer. No duplicate labels. | Pass. Order: overline → title → `01. Bible. 73 books` → `02. Search. word or verse` → `03. Bookmarks. saved verses` → `04. Daily Reading. today's Mass` → `05. Go to reference. Juanv 3:16` → `Settings & about` → `Home`/`Bible`/`More` tabs. One node per destination; no focus trap (Tab continues into the nav suite and back to the content). |
| 2 | Home → Bible | The adaptive suite is reachable; the Bible tab is selected; the Books list is announced. | Pass. `Bible. Tab 2 of 3`; the list announces `Utpoti. Genesis. Button. In list. 75 items`. |
| 3 | Books → Utpoti → chapter 1 | Chapter cells are 48 dp; the chapter opens at the top; the reader bar is announced. | Pass. Chapter cells measure 48 dp (rows 56 dp); chapter 1 opens at the top; the bar announces `Chapter 1. Button`. Cell label is `1. In list` (P3: no "Chapter" prefix). |
| 4 | Reader, John 1 | TalkBack reads verse text in source order; verse selection is announced; bookmark custom action works; copy/share actions are labelled. | Pass with P2. Verses announce number + text in source order (`1. … Button. In list. 25 items`); selection announces `Selected.`; the action sheet exposes `Read this verse`, `Copy verse`, `Share verse`, `Add bookmark`; the reader bar also has a labelled `Add bookmark` button. P2: the MoreVert overflow announces `Settings & about. Button`, not "More options". |
| 5 | Reader, passage banner | The banner and "Show full chapter" are both announced; at 2x the label stacks above the action. | Pass. `Verse 16` and `Show full chapter` are separate labels; at 2x the banner stacks and neither clips. |
| 6 | Reader, TTS | "Read this verse" is labelled; play/pause/stop states are announced; audio focus releases on stop/leave. No Konkani text is sent to the English voice. | Pass with limitation. `Read this verse` is labelled. On the Romi Konkani corpus no Konkani voice is installed, so the honest notice replaces the bar: `No Konkani voice is installed… the Romi Konkani text is not spoken`, with `Dismiss`. Play/pause/stop and audio-focus release were therefore not exercised here; they are covered by `ReaderTtsUiTest` and the speech-controller tests. |
| 7 | Search | Field label, clear action, result count and result rows are announced; long names wrap at 2x. | Pass. Field label/hint `Search the Bible`; clear action `Clear`; count `Showing first 50 results`; rows expose book, chapter:verse and snippet. Long result text wraps (no horizontal clipping) at 2x. |
| 8 | Bookmarks | Empty state and rows are announced; remove action is labelled; long names wrap at 2x. | Pass (empty state exercised). `No bookmarks yet.`, guidance and `Browse books` are announced. The delete control is labelled `Remove bookmark`; the row name allows two lines at 2x (`localMaxLines = 2`), though a saved row was not created in this pass. Row/undo behaviour is covered by the existing instrumented test. |
| 9 | Daily Reading | Date header, previous/next/today controls, and the unavailable-readings notice are announced. | Pass. `Fri, Oct 30, 2026`, `Previous day`, `Next day`; readings loaded for the date. The `Today` control is only present off today. The unavailable-readings notice path is covered by `DailyReadingUiTest`; it did not appear on this run. |
| 10 | Settings | Every control has a label and state; 200% does not clip; theme changes apply. | Pass. Scripture size (`Small`/`Medium`/`Large`/`Extra large`), line spacing (`Compact`/`Normal`/`Relaxed`), red letter and theme (`System`/`Light`/`Dark`) expose checked states; no clipping at 2x; theme applies without restart. |
| 11 | About | Landing directory, each disclosure page, and the full licence text are reachable; headings are announced. | Pass. Landing directory, Source edition, API & data, Software licences and Credits are reachable; section titles carry heading semantics. The App licence page contains the complete Apache-2.0 text in one text node. |
| 12 | More | All five rows are reachable and labelled; the suite hides on detail screens. | Pass. Search, Bookmarks, Daily Reading, Go to reference, Settings & about; the Home/Bible/More suite is visible on More and absent on Reader/Settings/About. |
| 13 | Predictive back | Back from Reader/Books/Daily returns to the expected destination; the system back animation is not suppressed. | Pass. Reader → Home (continue card), Chapters → Books, Daily → Home, Settings → More. `enableOnBackInvokedCallback=true` is set; no suppressed-animation warning for this app. |
| 14 | Landscape/320 dp | No clipping or overlap; every destination remains scrollable/reachable. | Pass. Landscape uses the navigation rail; Home/Reader/Settings/About/More are scrollable; 320 dp stacks the Home grid and wraps long labels without overlap. |
| 15 | Dark theme | Red-letter, verse numbers and meta text meet 4.5:1; colour is never the only signal. | Pass (spot check). Red-letter headings/body, verse numbers and meta text remain legible in dark; headings and verse numbers are also distinguished by position/weight, not colour alone. Token pairs are covered by `ThemeColorsTest`. |
| 16 | Remove animations | Navigation and state changes remain usable with animations disabled. | Pass. Tab switching, Books → chapters → Reader, Daily, Settings and Back all work with window/transition/animator scales at 0. |

## Espresso ATF advisory

- Dependency: `androidx.test.espresso:espresso-accessibility:3.5.1` (same
  version as Espresso core; ATF 3.1.2 transitively). The Compose BOM does not
  manage Espresso, so the existing `espressoCore` version is reused.
- Test: `app/src/androidTest/java/com/biblia/povitrpustok/ui/AccessibilityAtfTest.kt`.
  Two focused tests exercise Home, Books, Chapters, Reader, the Reader menu,
  Search (empty and results), Bookmarks, Daily, More, Settings and About.
- Blocker/adaptation: Espresso's `AccessibilityValidator` walks the `View`
  tree. Compose exposes the UI as virtual accessibility nodes under a single
  `AndroidComposeView`, so the validator only sees that host and reports it as
  unlabeled. The test therefore runs ATF's hierarchy checks on the active
  window's accessibility tree (`AccessibilityHierarchyAndroid.newBuilder` plus
  the preset's checks, looked up by class), which does include the Compose
  nodes.
- Result: **2 tests, 2 passed, 0 failed** (XML-authoritative), with four
  consecutive passing runs after the P1 fix below. No ATF P1 remains.
- **P1 found and fixed:** the first run's `TouchTargetSizeCheck` reported a
  46 dp target on Books: `Rect(673, 370 - 1296, 508)`, the New Testament
  segment of the Old/New Testament jump control. The `heightIn(min = 48.dp)`
  on `SegmentedButton` did not expand the inner clickable node to 48 dp. Added
  `Modifier.minimumInteractiveComponentSize()` to `TestamentSegment`
  (`BooksScreen.kt`); the visual height is unchanged and the ATF check passes.
- ATF contrast checks reported `NOT_RUN` (no screenshot data / colour not
  determinable for Compose nodes), so ATF did **not** verify contrast. Contrast
  remains covered by `ThemeColorsTest` and the manual dark spot check.
- The new test raises the instrumented count from 95 to 97. Full suite after
  the fix: **97 tests / 0 failed / 4 skipped (93 executed)**, XML-authoritative.
  Focused regression: `BooksUiTest` + `TouchTargetsUiTest` = **7/7 passed**,
  including `books_testament_jump_segments_are_48dp_targets`.

## Accepted findings (P2/P3)

| Severity | Finding | Disposition |
|---|---|---|
| P2 | Reader overflow (MoreVert) announces `Settings & about`, the menu's only item. It conveys the destination but not that it is a menu. | Accepted for 1.3.1; a clearer label ("More options") is a small follow-up. No P1. |
| P3 | Chapter cells announce `1. In list`; the word "Chapter" is not included. | Accepted; position and the reader bar provide context. |
| P3 | Home cards read their decorative number first (`01. Bible. 73 books`). | Accepted; the number matches the visible order. |
| P3 | Verse nodes append `Button. In list` because the chapter is exposed as a list. | Accepted; harmless platform wording. |

## Limitations

- No audio output on the headless emulator; TalkBack wording was verified from
  the speech-output overlay and the node tree, not by ear.
- The long-press verse action sheet is exposed as a long-click action; TalkBack's
  local "Actions" menu was not driven end-to-end. The sheet itself and the
  dedicated `Add bookmark` button were verified.
- TTS play/pause/stop and audio-focus release could not be exercised on the Romi
  corpus because no Konkani voice is installed; those paths are covered by the
  TTS unit/instrumented tests.
- Daily Reading depends on the network; the unavailable-readings notice was not
  shown on this run and is covered by `DailyReadingUiTest`.
- This is an advisory pass on one emulator, not a certification or a full audit.
