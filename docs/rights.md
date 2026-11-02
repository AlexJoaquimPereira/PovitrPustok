# Rights register

Status: F1-02 register. Last reviewed: 2026-11-02. Register owner: Alex Pereira.

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
fails after it passes. Cell text must not contain a literal `|`; the build-time parser splits on it.

<!-- rights-register:start -->
| id | content | status | owner | licence | attribution | covers | territory | expiry | evidence |
|---|---|---|---|---|---|---|---|---|---|
| app-code | Povitr Pustok application code | shipped | Alex Pereira | Apache-2.0 | Copyright 2026 Alex Pereira | - | worldwide | none | `LICENSE`; `NOTICE`; `assets/licenses/Apache-2.0.txt`; `assets/licenses/NOTICE.txt` |
| romi-ui-translation | Romi UI translation (1.3.1) | shipped | Alex Pereira | Apache-2.0 | Romi UI translation by Alex Pereira, done manually by hand | - | worldwide | none | `docs/translations/1.3.1-romi.csv`; `app/src/main/res/values-b+kok+Latn/strings.xml` |
| konkani-corpus | Konkani (Romi) Bible corpus, 73 books | shipped | Archdiocese of Goa and Daman | © Archdiocese of Goa and Daman; used with permission; not Apache-2.0 | Konkani Bible text © Archdiocese of Goa and Daman | konkani-romi | worldwide (permission on file; scope to confirm) | none | Permission on file; `NOTICE`; `tools/corpus/README.md`; source XML and SQLite are not in git |
| catholic-readings-api | Catholic Readings API (Daily Reading) | network | cpbjr / Catholic Readings API | MIT | Catholic Readings API (cpbjr.github.io) | - | worldwide | none | `https://github.com/cpbjr/catholic-readings-api/blob/main/LICENSE`; `AboutApiScreen.kt` |
| software-libraries | Third-party libraries packaged in the APK | shipped | Various component authors | Apache-2.0 (runtime components) | See About > Software licences | - | worldwide | none | `LicenceCatalog.kt` (runtime list); `AboutLicensesScreen.kt` |
| atkinson-hyperlegible | Atkinson Hyperlegible typeface (regular and bold) | shipped | Braille Institute of America, Inc. | SIL OFL 1.1 | Copyright 2020 Braille Institute of America, Inc. | - | worldwide | none | `app/src/main/res/font/`; `assets/licenses/OFL-1.1.txt`; `https://github.com/googlefonts/atkinson-hyperlegible` |
| goa-calendar-overrides | Goan particular-calendar corrections in code | shipped | Alex Pereira | Apache-2.0 | Goan particular-calendar corrections, manually cross-checked; calendar facts are not copyrightable | - | IN | none | `GoaCalendarOverrides.kt`; `AboutApiScreen.kt`; `docs/rights.md` |
| webc | World English Bible, Catholic edition (WEBC), 73 books | shipped | Public domain (eBible.org) | Public domain | World English Bible, Catholic edition; eBible.org; the name is a trademark and unmodified redistribution is used | webc | worldwide | none | `https://ebible.org/Scriptures/details.php?id=eng-web-c`; `tools/editions/README.md`; `tools/editions/webc-manifest.json`; `AboutWebcScreen.kt` |
| douay-rheims | Douay-Rheims American Edition 1899, 73 books | shipped | Public domain (eBible.org) | Public domain | Douay-Rheims 1899; eBible.org; unmodified redistribution | dra | worldwide | none | `https://ebible.org/Scriptures/details.php?id=engDRA`; `https://bible.helloao.org/api/eng_dra/complete.json`; `tools/editions/README.md`; `tools/editions/dra-manifest.json`; `AboutDraScreen.kt` |
| openbible-cross-references | OpenBible.info cross references | planned | OpenBible.info | CC-BY-4.0 | OpenBible.info (CC BY 4.0) | - | worldwide | none | Planned 1.4 (roadmap S1-07); not shipped |
| douay-annotations | Douay-Rheims annotations | planned | Public domain | CC0-1.0 | Douay-Rheims annotations | - | worldwide | none | Planned 1.4; not shipped |
| commentary | Haydock and Catena Aurea commentary | planned | Public domain | Public domain | Haydock / Catena Aurea | - | worldwide | none | Planned 1.6 (roadmap A1-06); not shipped |
| calendar-lectionary-pack | Annual Goa calendar/lectionary pack | planned | To be confirmed | pending | Goa calendar/lectionary | - | IN | none | Planned 1.5 (roadmap C1-05); not shipped |
| llm-providers | User-selected LLM providers (BYOK) | planned | Provider terms | Provider terms (BYOK) | User-selected provider | - | user jurisdiction | none | Planned 1.6; no developer key or proxy |
<!-- rights-register:end -->

## Notes

- The corpus permission is on file. Its scope for English parallel text, AI quotation,
  calendar/lectionary use, and derivative/export use is still being confirmed; 1.3.1 uses only the
  existing Konkani reader scope.
- `webc` and `dra` are independent public-domain English translations bundled as separate 73-book
  editions. They are not parallel text of the Konkani corpus and no Konkani and English text is
  combined in one verse; the Archdiocese permission scope is therefore unchanged by adding them.
  The WEBC text is redistributed unmodified (HTML wrapping only) and keeps the edition name; the
  DRA is the American Edition 1899 from eBible.org `engDRA` via helloao.
- `romi-ui-translation` is the developer's own work, licensed with the app code. It ships with the 1.3.1 generated locale.
- `goa-calendar-overrides` is a small code table, not a content pack. The celebration names, ranks
  and dates are factual calendar data, manually cross-checked against the Goan particular calendar
  and Universalis Goa; nothing is copied or bundled from Universalis. It is licensed with the app
  code and limited to India. No calendar pack ships in 1.3.1.
- No content packs ship in 1.3.1. A future pack must add a `shipped` row whose `covers` includes
  the pack id; `RightsRegisterTest` fails otherwise.
- The register points at the evidence; it does not replace the licence texts or the permission
  letter. Test/build-only components (JUnit EPL-1.0, Robolectric MIT) appear in the in-app
  catalogue but are not packaged and are not shipped content.
- `atkinson-hyperlegible` covers the two bundled `.ttf` files and the OFL text in
  `assets/licenses/OFL-1.1.txt`. The files are unmodified upstream releases, so the OFL Reserved
  Font Name clause is not triggered; if they are ever modified, they must be renamed. The in-app
  **Typeface licence** page shows the complete licence text.
- The app code is Apache-2.0. The Konkani corpus is **not** Apache-2.0; the `NOTICE` and the
  Source edition page state the rights holder and the permission.
