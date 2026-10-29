# 1.3.1 Romi UI translation worksheet

Status: worksheet only; no translated resource is shipped yet.

Scope for 1.3.1: **Settings, About, reader chrome, bookmarks, and the new shell labels**.
Home, Books, Search, Daily Reading, and reference-entry content stay English for this release.
The `scope` column marks:

- `translate` — UI chrome; fill the `romi` column.
- `review` — proper names/link labels; owner decides.
- `keep English` — legal text, copyright/attribution, URLs, licence names. Do not translate
  without a deliberate review; an inaccurate legal translation is worse than English.

Rules for the `romi` column:

- Keep every placeholder (`%1$s`, `%1$d`, `%2$d`, `%3$d`) exactly once and in the same order.
- Keep punctuation that carries meaning (`·`, `–`, `—`, `…`) and the `%` placeholders.
- Do not translate `Povitr Pustok`, `GitHub`, `Apache`, `MIT`, `Universalis Goa`, or URLs.
- Romi Konkani uses Latin script; do not switch to Devanagari.
- When a line is intentionally left English, copy the English text so the fallback is explicit.

After the worksheet is filled, the `values-b+kok+Latn/strings.xml` scaffold is generated from
the `translate` rows, and the app's locale config is wired. Untranslated keys fall back to
English, so a partially filled worksheet is shippable; empty `romi` cells are not generated.

The CSV is the editable source of truth. Do not hand-edit a generated `strings.xml` later;
regenerate it from the CSV so keys cannot drift.
