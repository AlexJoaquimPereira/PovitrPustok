# Performance baseline

Status: F1-21 baseline, 2026-10-28. Re-run this procedure after a change that
could affect startup, the Books title scan, search, or reader parsing.

## Environment

| Field | Value |
|---|---|
| Device | Pixel 8 Pro API 35 emulator (`Pixel_8_Pro_API_35`, x86_64, KVM) |
| GPU | `-gpu angle_indirect`, Vulkan disabled |
| Android | API 35; the image's SQLite has no FTS5 |
| Builds | Signed R8 release APK for cold start; debug instrumentation for first-use flows |
| App | 1.2.4 (`versionCode` 10), pre-1.3.0 tree |
| Runs | 5 per measurement, app data cleared before each first-use run |

The emulator is a development baseline, not a user-device benchmark. Debug
first-use numbers are conservative because the debug build is not R8-shrunk.
Cold start uses the signed release APK. Medians are reported; the first
install run is noted separately because it includes one-off dexopt/corpus
publication work.

## Method

### Cold start (release)

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
for i in 1 2 3 4 5; do
  adb shell am force-stop com.biblia.povitrpustok
  adb shell am start -W -n com.biblia.povitrpustok/.MainActivity
done
```

`TotalTime` from `am start -W` is the metric. The app is force-stopped between
runs; the process is cold, the device is warm.

### First Books, first search, first chapter (debug instrumentation)

```bash
adb shell pm clear com.biblia.povitrpustok
adb logcat -c
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.biblia.povitrpustok.perf.FirstUsePerformanceTest
adb logcat -d -s PerfBaseline:I
```

`FirstUsePerformanceTest` performs the real first-use sequence and logs
`booksFirstFrameMs`, `booksTitlesMs`, `searchMs`, and `chapterMs`. Clear app
data before each run; running it inside the full suite reuses warm
corpus/title caches and is only a smoke check.

## Baseline (before the parser cache)

Code: `0421b67` (`test(perf): add the first-use performance baseline`).

| Measurement | Runs (ms) | Median |
|---|---|---|
| Cold start (release) | 1046, 1339, 1196, 1284, 1196 | **1196 ms** |
| Books first catalog frame | 980, 1018, 1012, 880, 897 | **980 ms** |
| Books title scan complete | 1464, 1729, 1610, 1509, 1590 | **1590 ms** |
| First search (scan fallback) | 2850, 3051, 3250, 3233, 3102 | **3102 ms** |
| First chapter open | 934, 882, 1016, 964, 998 | **964 ms** |

The first install run measured 1416 / 2184 / 3283 / 849 ms for the four
first-use segments; it is excluded from the medians.

## After the parser cache

Code: `636b4d7` (`perf(reader): cache parsed HTML documents`).

| Measurement | Runs (ms) | Median | Budget |
|---|---|---|---|
| Cold start (release) | 1199, 1374, 1253, 1398, 1709 | **1374 ms** | ≤ 1800 ms |
| Books first catalog frame | 918, 829, 866, 1095, 982 | **918 ms** | ≤ 1500 ms |
| Books title scan complete | 1748, 1473, 1516, 1706, 1774 | **1706 ms** | ≤ 2500 ms |
| First search (scan fallback) | 3733, 3216, 3333, 3548, 3466 | **3466 ms** | ≤ 4700 ms |
| First chapter open | 965, 950, 1047, 934, 1035 | **965 ms** | ≤ 1500 ms |

**No budget was exceeded.** Every individual run is also under its budget
(worst: cold start 1709 ms, titles 1774 ms, search 3733 ms, chapter 1047 ms,
frame 1095 ms). The cold-start and search medians move by roughly
150–370 ms between runs, which is emulator noise/thermal variance; the
parser cache is not on the cold-start path.

## Parser cache

`BibleHtmlDocumentCache` is a process-wide, bounded LRU of parsed
`BibleHtmlDocument`s (256 entries), keyed by the exact source HTML.
`ReaderComponents.rememberDocument` and the book-introduction renderer read
through the same shared cache instead of calling `BibleHtmlParser.parse` per
composition. The cache keeps scroll-back — and re-creation after a LazyColumn
item is disposed — from reparsing a verse/heading/block; ordinary
recomposition is already covered by `remember`. It does not make the first
parse of a chapter faster, so the first chapter-open number is intentionally
unchanged. `BibleHtmlDocumentCacheTest` covers reuse, distinct documents, LRU
eviction, capacity validation, and a full second pass over the longest chapter.

The bound is 256 because Psalm 119 has 176 verses: at the original 128 entries
the working set was larger than the cache, so a full scroll-back thrashed and
reparsed all 176 verses (0% second-pass hits). 256 holds that chapter plus its
headings and a second ordinary chapter; the retained structure for 128 verses
is only 467 nodes and ~16 KB of text, so the extra entries are cheap.

### Controlled cache A/B (2026-10-31)

The before/after tables above compare different builds and are **not** a cache
experiment: cold start, Books and search never touch the cache. To isolate it,
the parser was benchmarked with real corpus HTML on the JVM and on the API 35
emulator (debug build), comparing a parse per access with a warm cache:

| Workload | Parse | Warm cache | Saving |
|---|---|---|---|
| JVM, Genesis 1 (31 verses) | 320 µs | 6 µs | ~314 µs |
| JVM, Psalm 119 (176 verses) | 1.68 ms | 9 µs | ~1.67 ms |
| JVM, 1000 verses | 2.95 ms | 47 µs | ~2.9 ms |
| Emulator, Genesis 1 | 3.25 ms | 19 µs | ~3.2 ms |
| Emulator, Psalm 119 | 19.9 ms | 40 µs | ~19.9 ms |

The cache is a scroll-back/revisit optimisation: on the emulator it saves
roughly 0.1 ms per verse, so re-rendering a 31-verse chapter saves ~3 ms —
spread across frames as verses re-enter, and not a first-render win. It cannot
explain the cold-start, Books or search deltas above; those flows never call
it, and the first chapter open (the only cache-touching baseline flow) moved
by 1 ms.

## Findings

- **First Books open** includes the one-time title scan across all 73 books,
  which publishes the private corpus on first use. It is the dominant first-use
  cost after search; 1.6–1.7 s on this emulator is acceptable for a one-time
  scan, and later Books opens are cache hits.
- **First search** is the slowest flow (~3.1–3.5 s) because the emulator has no
  FTS5 and uses the normalized scan fallback across the corpus. A device with
  FTS5 should be faster; this baseline does not measure that path. Keep the
  4.7 s budget for the fallback.
- **First chapter open** is ~1 s including the database open and the first
  parse/render. The content cache targets later scroll/recomposition, not this
  first parse.
- **Cold start** is ~1.2–1.4 s on the release build. No startup corpus copy is
  performed; database publication remains lazy.

## Reproduction notes

- Use `ANDROID_SERIAL=emulator-5554` when a phone is also attached.
- The emulator image lacks FTS5; do not treat these search numbers as the FTS5
  path. The three `BibleFtsInstrumentedTest` cases skip there.
- If the emulator has been up for a long time or is thermally throttled,
  repeat the five runs and use the median; do not compare a single run.
- Record new numbers here when a release-affecting change lands. A budget
  breach should be explained in the release checklist before shipping.
