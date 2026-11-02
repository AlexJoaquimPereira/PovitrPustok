# 1.3.1 Romi UI translation worksheet

Status: translation complete 2026-11-02 (1.3.1 base plus the 1.4 English-edition
strings). 187 rows: 128 `translate` cells, all filled; 59 `keep English`.
The generated locale is 264 strings (128 translated) plus 2 plurals; the
remaining 136 strings are explicit English fallbacks.

## Choosing the language in the app

Settings > **APP LANGUAGE** offers English and Konkani (Romi) and applies the choice
immediately through the AppCompat delegate, so it works on every supported release. The
manifest's `autoStoreLocales` holder persists it, and Android's own per-app language screen
stays a second entry point — the two always agree because both read the same delegate.

The F2-14 language names (`settings_language_english`,
`settings_language_konkani`, `settings_language_konkani_spoken`) remain `keep
English`; the section label, the scope caption and the translation attribution
were translated in the 1.4 pass.

The 14 new English-edition UI strings (`settings_section_edition`,
`settings_edition_caption`, the two About leads and notes, the About
LICENCE/SOURCE TEXTS/link labels) are translated. The edition names, subtitles,
legal/attribution text, the two "Build source" link labels and `complete.json`
file names remain `keep English`. `python3 tools/translations/generate_romi_strings.py`
emits the translated cells into `values-b+kok+Latn`.

## Provenance

The Romi UI translation (1.3.1 base and the 1.4 English-edition additions) was
produced **manually, by hand**, by the app's developer, Alex Pereira. No machine translation was used. The worksheet is the
complete record of that work.

Scope for 1.3.1: **Settings, About, reader chrome, bookmarks, and the new shell labels**.
Home, Books, Search, Daily Reading, and reference-entry content stay English for this release.
The `scope` column marks:

- `translate` — UI chrome; fill the `romi` column.
- `review` — proper names/link labels; owner decides.
- `keep English` — legal text, copyright/attribution, URLs, licence names. Do not translate
  without a deliberate review; an inaccurate legal translation is worse than English.

Rules for the `romi` column:

- Keep every placeholder (`%1$s`, `%1$d`, `%2$d`, `%3$d`) exactly once. Reorder only by moving
  the `$n` position markers (e.g. `%2$d-acho %1$d-vo`), never by swapping bare `%s`/`%d`.
- Keep punctuation that carries meaning (`·`, `–`, `—`, `…`) and the `%` placeholders.
- Do not translate `Povitr Pustok`, `GitHub`, `Apache`, `MIT`, `Universalis Goa`, or URLs.
- Romi Konkani uses Latin script; do not switch to Devanagari.
- When a line is intentionally left English, leave the `romi` cell empty; the generator emits
  the English text as an explicit fallback.

The worksheet is complete. `values-b+kok+Latn/strings.xml` is generated from **every** default
`<string>`: a filled `romi` cell is used, and an empty cell emits the English text. Plurals are
copied as English until the worksheet supports them. The generated locale is therefore complete,
so a partially filled worksheet is shippable and lint-clean; a partial locale would otherwise fail
`MissingTranslation`.

The CSV is the editable source of truth. Do not hand-edit a generated `strings.xml` later;
regenerate it from the CSV so keys cannot drift.

## Keys added after 1.3.1

The Bible translation control (`settings_section_edition`, `settings_edition_konkani`,
`settings_edition_webc`, `settings_edition_dra`, `settings_edition_caption`) and the two
English-edition About pages (`about_webc_*`, `about_dra_*`) were added after 1.3.1 and are
`keep English` rows. Edition names, licence wording and attribution are deliberate English; the
generated locale emits the English text as its fallback until a reviewer fills a Romi cell.
