# Rights register

Status: F1-02 register. Last reviewed: 2026-10-28. Register owner: Alex Pereira.

This register records the owner, licence, attribution, territory, expiry, and evidence for every
text, data set, and content pack the app ships or fetches. `RightsRegisterTest` reads the table
below at build time.

Status meanings:

- `shipped` — bundled in the APK.
- `network` — fetched at runtime; not bundled.
- `pending` — shipped or planned, but the licence, attribution, territory, or evidence still needs
  confirmation. Resolve before the affected release.
- `planned` — not shipped; reserves the rights position for a future release.

Every bundled content pack must add a row whose `covers` contains the pack id. Every Bible edition
must be covered by a row. Expiry is `none` or an ISO-8601 date; the date is inclusive, and the test
fails after it passes.

<!-- rights-register:start -->
| id | content | status | owner | licence | attribution | covers | territory | expiry | evidence |
|---|---|---|---|---|---|---|---|---|---|
| app-code | Povitr Pustok application code | shipped | Alex Pereira | Apache-2.0 | Copyright 2026 Alex Pereira | - | worldwide | none | `LICENSE`; `NOTICE`; `assets/licenses/Apache-2.0.txt`; `assets/licenses/NOTICE.txt` |
| konkani-corpus | Konkani (Romi) Bible corpus, 73 books | shipped | Archdiocese of Goa and Daman | © Archdiocese of Goa and Daman; used with permission; not Apache-2.0 | Konkani Bible text © Archdiocese of Goa and Daman | konkani-romi | worldwide (permission on file; scope to confirm) | none | Permission on file; `NOTICE`; `tools/corpus/README.md`; source XML and SQLite are not in git |
| catholic-readings-api | Catholic Readings API (Daily Reading) | network | cpbjr / Catholic Readings API | MIT | Catholic Readings API (cpbjr.github.io) | - | worldwide | none | `https://github.com/cpbjr/catholic-readings-api/blob/main/LICENSE`; `AboutApiScreen.kt` |
| software-libraries | Third-party libraries packaged in the APK | shipped | Various component authors | Apache-2.0, MIT, EPL-1.0 (per component) | See About > Software licences | - | worldwide | none | `LicenceCatalog.kt`; `AboutLicensesScreen.kt` |
| goa-calendar-overrides | Goan particular-calendar corrections in code | pending | Goan particular-calendar sources (to confirm) | pending | Goan particular calendar corrections | - | IN | none | `GoaCalendarOverrides.kt`; `AboutApiScreen.kt`; roadmap C1-07 |
| webc | World English Bible, Catholic edition (WEBC) | planned | Public domain | Public domain | World English Bible, Catholic edition | - | worldwide | none | Planned 1.4 (roadmap S1-03); not shipped |
| douay-rheims | Douay-Rheims Bible | planned | Public domain | Public domain | Douay-Rheims Bible | - | worldwide | none | Planned 1.4 (roadmap S1-04); not shipped |
| openbible-cross-references | OpenBible.info cross references | planned | OpenBible.info | CC-BY-4.0 | OpenBible.info (CC BY 4.0) | - | worldwide | none | Planned 1.4 (roadmap S1-07); not shipped |
| douay-annotations | Douay-Rheims annotations | planned | Public domain | CC0-1.0 | Douay-Rheims annotations | - | worldwide | none | Planned 1.4; not shipped |
| commentary | Haydock and Catena Aurea commentary | planned | Public domain | Public domain | Haydock / Catena Aurea | - | worldwide | none | Planned 1.6 (roadmap A1-06); not shipped |
| calendar-lectionary-pack | Annual Goa calendar/lectionary pack | planned | To be confirmed | pending | Goa calendar/lectionary | - | IN | none | Planned 1.5 (roadmap C1-05); not shipped |
| llm-providers | User-selected LLM providers (BYOK) | planned | Provider terms | Provider terms (BYOK) | User-selected provider | - | user jurisdiction | none | Planned 1.6; no developer key or proxy |
<!-- rights-register:end -->

## Notes

- The corpus permission is on file. Its scope for English parallel text, AI quotation,
  calendar/lectionary use, and derivative/export use is still being confirmed; 1.3.0 uses only the
  existing Konkani reader scope.
- `goa-calendar-overrides` is a small code table, not a content pack. It is `pending` because the
  About API page describes Universalis Goa as a manual cross-check; confirm both the disclosure
  wording and the rights position before shipping a calendar pack (C1-07).
- No content packs ship in 1.3.0. A future pack must add a `shipped` row whose `covers` includes
  the pack id; `RightsRegisterTest` fails otherwise.
- The register points at the evidence; it does not replace the licence texts or the permission
  letter.
- The app code is Apache-2.0. The Konkani corpus is **not** Apache-2.0; the `NOTICE` and the
  Source edition page state the rights holder and the permission.
