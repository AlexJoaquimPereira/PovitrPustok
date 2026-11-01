# 1.3.1 Romi UI translation worksheet

Status: translation complete 2026-11-01; integration lands in F2-14.

## Provenance

The 1.3.1 Romi UI translation was produced **manually, by hand**, by the app's
developer, Alex Pereira. No machine translation was used. The worksheet is the
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
