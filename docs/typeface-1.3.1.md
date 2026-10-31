# 1.3.1 optional typeface (F2-09)

Status: evaluated and shipped 2026-10-31 as an opt-in Scripture typeface.
This is a preference record, not a dyslexia claim or a clinical evaluation.

## What ships

- **Atkinson Hyperlegible 1.006**, designed by the Braille Institute of
  America, Inc., under the **SIL Open Font License 1.1**.
- Two unmodified upstream files: regular (54,348 B) and bold (55,256 B),
  bundled under `app/src/main/res/font/`. The upstream italic face is not
  bundled.
- The complete licence ships in
  `app/src/main/assets/licenses/OFL-1.1.txt` and is shown on the in-app
  **Typeface licence** page (Settings > About > Software licences >
  TYPEFACES).
- `docs/rights.md` records the face as shipped content; the runtime-library
  list is unchanged because a typeface is not a library.

## Glyph coverage

The corpus (73 SQLite files, every text column) plus all `res/values*/strings.xml`
use **119 distinct characters**. The font covers every one except **U+1EBD
`ẽ`**, which appears exactly once, in Ezekiel 16:35
(`bhettoun dilẽi:`). `ĩ` (U+0129) — named in the original roadmap wording — is
not used anywhere in the corpus or the UI.

Android falls back per glyph, so the verse still renders: on the API 35
emulator the tilde is visible over the `e`, with no tofu and no clipping. The
fallback face makes that single character subtly different from the
surrounding Atkinson letters. The font is left unpatched; a patched or
renamed derivative is not worth one character.

## Preference

- **Off by default**: an existing install sees no change on upgrade.
- Applies to Scripture body text only — reader verses, poetic verses and the
  Settings preview. Titles, chrome and display type keep the app's serif.
- Settings uses one switch row (the `RedLetterControl` pattern): the whole row
  is a 48 dp target, the caption is inside the tap area, and TalkBack reads the
  name, the low-vision purpose, the "not a dyslexia treatment" caveat and the
  state as one node. A two-segment control was rejected because the name cannot
  hold one line at 320 dp / 200% font scale.
- Checked at 320 dp and 200%: the title wraps, the caption wraps, the switch
  stays aligned, nothing clips or ellipsizes.

## Verification

- JVM: **633 tests / 0 failed / 2 skipped** (the two documented FTS5
  Robolectric `Assume` skips).
- Instrumented: **99 tests / 0 failed / 4 skipped (95 executed)**,
  XML-authoritative; the skips are 3 x FTS5-only and 1 network-dependent Daily
  Reading case.
- `lintDebug` clean; debug and androidTest APKs assemble.

## Limitations

- Italic spans in the corpus render as Compose's synthesised oblique because
  only regular and bold ship.
- Settings is taller than one 448 dp screen, so the About entry needs a scroll
  there; the shell state-restoration test now scrolls to it.
- One corpus character (`ẽ`) uses the system fallback face, as recorded above.
- This is a one-emulator visual check, not a low-vision user study, and the
  preference makes no claim beyond offering a clearer typeface.
