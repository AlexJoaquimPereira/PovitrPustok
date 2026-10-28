# AGENTS.md

Compact, verified guidance for working in this repository. Read `README.md` for product context.

## Project shape

- Single-module Android app (`:app`), Kotlin + Jetpack Compose + Material 3, Hilt, Room, and raw
  read-only SQLite for the Bible corpus.
- Entry point: `app/src/main/java/com/biblia/povitrpustok/MainActivity.kt`.
- Boundaries: `core/database` (corpus access), `core/html` (parser/renderer), `data/*`
  (repositories), `domain/*` (models/repositories), `feature/*` (screens/ViewModels),
  `ui/theme`.
- `app/build/generated/` and KSP output are generated; never edit them.
- Branch is `master`.
- **Make atomic commits**: one logical change per commit. Do not bundle unrelated features, tests,
  or documentation. Stage by path or with `git add -p`; each commit should build and test on its
  own.
- **Do not push without the user's explicit approval.**

## Review after every change

- After implementing a change, run a review of the actual diff with the review specialist
  (`oracle`) before asking to push. Keep the same review session across changes so feedback
  stays continuous and prior findings can be re-checked.
- Verify the review against the commits/files yourself — a review summary is a claim, not
  evidence. Fix blockers and should-fix findings, or state explicitly what is deferred.
- A review complements, never replaces, the test/build gates below.

## Toolchain and commands

- Gradle wrapper + **JDK 25** daemon (`gradle/gradle-daemon-jvm.properties`). `compileSdk`/`targetSdk`
  are **36**; `minSdk` is 30.
- Run from the repository root:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests 'com.biblia.povitrpustok.data.bible.JohnChapterOneDataTest'
./gradlew :app:assembleDebug :app:lintDebug
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:assembleRelease
```

- Configuration cache is enabled. If a flag changes, Gradle handles invalidation; do not disable it.
- `assembleRelease` is signed only when a gitignored `keystore.properties` exists; otherwise it
  produces an unsigned artifact. Never commit keystore material.
- R8 is enabled for release (`optimization.enable = true`); release build failures are usually
  missing keep rules, not debug-code bugs.

## Corpus is private and required for most tests

- The 73 `app/src/main/assets/bible/*.sqlite` files are **gitignored and copyrighted**
  (© Archdiocese of Goa and Daman). Never commit them or source XML.
- A fresh clone has no corpus. Most data/reader tests need it; without assets, use:

```bash
./gradlew :app:testDebugUnitTest -PskipCorpusTests
```

  Corpus-dependent tests then skip explicitly. Never treat those skips as passing coverage.
- Corpus tooling: `tools/corpus/README.md`.
  - Regenerate: `python3 tools/corpus/import_corpus.py --source <private-xml> --out app/src/main/assets/bible`
  - Verify/update metadata: `python3 tools/corpus/generate_manifest.py [--check]`
- Any corpus change requires regenerating `tools/corpus/corpus-manifest.json`; `CorpusManifestTest`
  will fail on hash/count drift. Do not hand-edit `.sqlite` files.
- CI uses private `CORPUS_ARCHIVE_*` secrets. Without them it runs the skip path and does not build
  a release APK.

## Data invariants (easy to break)

- `books.id` is **TEXT**. Never read it with `getLong`/`getInt`; bind `storedBookId` as a string.
- Navigate chapters by **`position`**, never `number`: source numbers repeat (Job 26).
- `verse_end` is NULL for ordinary single verses; coalesce to `verse_start`. Keep real source
  ranges intact.
- Do not reorder verses by `verse_start`; source order is `position, id`.
- Imported corpus rows are immutable. Never write to a corpus database.
- Search uses FTS5 only when the device SQLite provides it; otherwise it uses the normalized scan
  fallback. Do not assume FTS5 exists. Android phones and the bundled emulator both lack it.
- Non-continuous references are ordered segments, not one span. Preserve `segments`;
  cross-chapter passages carry one chapter per segment.

## Tests and device/emulator quirks

- JVM baseline is JUnit4 + Robolectric; `app/src/test/resources/robolectric.properties` pins
  `sdk=35` because that android-all jar is provisioned. Do not bump it without checking `~/.m2`.
- Two tests skip FTS5 parity under Robolectric via `Assume`; three `BibleFtsInstrumentedTest` cases
  skip on devices/emulators without FTS5. This is expected, not a failure.
- Use a focused filter while iterating; run the full JVM suite before committing.
- Compose UI tests live under `app/src/androidTest`; they require a device/emulator. Existing tests
  use both visible text and test tags, so preserve labels unless deliberately migrating tests.

### Working emulator

The AVD is `Pixel_8_Pro_API_35` (x86_64, KVM). Default/SwiftShader GPU backends SIGSEGV in
`libGLESv2.so`; **`-gpu angle_indirect` works**:

```bash
"$ANDROID_HOME/emulator/emulator" -avd Pixel_8_Pro_API_35 \
  -no-window -no-audio -no-boot-anim -no-snapshot \
  -gpu angle_indirect -feature -Vulkan \
  -camera-back none -camera-front none
```

Wait for `emulator-5554`, then target it explicitly if a phone is also attached:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

The emulator boots in ~16 s. Its system image also lacks FTS5, so it closes UI/device regression
testing but **not** the FTS5 verification item.

- If an install fails with `INSTALL_FAILED_INSUFFICIENT_STORAGE`, the AVD's 5.8 GB data
  partition has filled up from repeated test installs; relaunch the AVD with `-wipe-data`.
- On some OEM phones, Gradle's default install fails with a signature/user-profile conflict; use
  `adb install --user 0 -r -t app/build/outputs/apk/debug/app-debug.apk` first.
- `adb devices` may list multiple transports; always set `ANDROID_SERIAL` for deterministic runs.

## Releases

- Follow `docs/release-checklist.md`; use `docs/release-notes-template.md` for GitHub release notes.
- Build signed artifacts locally with `tools/release/package_release.sh`. CI never publishes an
  APK/AAB and must not produce a release artifact without the private corpus.
- Attach the versioned APK, AAB, and `SHA256SUMS`; never attach corpus files, source XML, debug/test
  APKs, mapping files, or keystore material.
- Verify the signer certificate and the 73 corpus assets before publishing. Push only after the
  owner's explicit approval.
- The repository is private; Obtainium needs a GitHub token with read access. The corpus is not in
  git history, so visibility is an access-control choice, not a corpus-protection requirement.

## Rights and licensing

- `docs/rights.md` is the rights register. Every shipped text, data set, and content pack needs a
  row; `RightsRegisterTest` fails on a missing row, uncovered edition/pack, or expired term.
- Never add a content pack without a `shipped` rights row whose `covers` includes the pack id.
- The corpus is used with permission and is not Apache-2.0. Do not imply diocesan production or
  relicense the corpus.

## Documentation and local-only files

- `README.md` is the product/architecture reference.
- `TODO.md` and `AUDIT_REPORT.md` are **gitignored local working documents**; do not assume a fresh
  clone contains them. Do not rewrite `AUDIT_REPORT.md` as fixes land.
- Release notes are published in GitHub releases, not committed as a file.
- UI/UX research and Figma files were removed in 1.2.3 and must not be reintroduced.

## Disclosure pages

- Settings > About is a **landing page**; every disclosure type has its own page under
  `feature/about` (Source edition, API & data, Software licences, App licence). Credits is a
  project/maintainer page, not a disclosure index.
- Every new API, data source, licence or acknowledgement gets its **own page** with a source link,
  repository link and licence/attribution — do not append it to an existing page.
- The app's own licence page must show the **complete** licence text (Apache-2.0), not a link.
- When adding or changing a disclosure, update `README.md` and this file, and add tests.
- Do not repeat source-edition, API or licence text on Credits; the About landing is the directory.
