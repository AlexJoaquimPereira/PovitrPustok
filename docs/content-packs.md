# Content packs

Status: the format and licence gate are implemented in 1.3.0 (F1-18/F1-19).
**No packs ship in 1.3.0.** The first expected pack is the annual Goa calendar
(C1-05); the same format is intended for commentary and other data later.

## Lifecycle

A pack is ordinary data inside the APK:

1. It is authored and reviewed outside the app, then committed under
   `app/src/main/assets/packs/<pack-id>/`.
2. It ships in the APK and is read from the read-only assets.
3. It is replaced **only by an app update**. There is no remote pack store, no
   download manager, no resumable download, and no runtime replacement.
4. A calendar pack is regenerated yearly, before its `expiry` date. The new
   pack gets a new `version`, new checksums, and ships in the next app update.
5. Removing a pack is also an app update: delete its directory and any code
   that reads it.

This keeps the offline, no-backend contract: the APK is the complete content
bundle, and a pack cannot change after installation.

## Directory layout

```text
app/src/main/assets/packs/
└── <pack-id>/
    ├── manifest.json
    ├── data.json
    └── audio/track.mp3
```

The directory name must equal the manifest `id`. `manifest.json` is metadata
only; it is never listed as a payload file.

## Manifest

```json
{
  "id": "example-calendar",
  "type": "calendar",
  "version": "2027.1",
  "locale": "en-IN",
  "licence": "CC0-1.0",
  "attribution": "Example calendar data",
  "territory": ["IN"],
  "expiry": "2027-12-31",
  "checksum": {
    "calendar.json": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  },
  "files": ["calendar.json"]
}
```

| Field | Required | Meaning |
| --- | --- | --- |
| `id` | yes | Lowercase hyphenated pack id; must match the directory. |
| `type` | yes | `json`, `text`, `audio`, or `calendar`. |
| `version` | yes | Pack version, e.g. `2027.1`; bump on every content change. |
| `locale` | yes | BCP-47 tag of the content, e.g. `en-IN`, `kok-Latn`. |
| `licence` | yes | Licence identifier (SPDX where one applies). |
| `attribution` | yes | Human-readable credit shown with the content. |
| `territory` | calendar | ISO 3166-1 alpha-2 codes; absent/empty means worldwide. |
| `expiry` | calendar | Last valid day, inclusive (`yyyy-MM-dd`). |
| `checksum` | yes | Map of every payload path to its lowercase SHA-256 digest. |
| `files` | yes | Payload paths; must match the `checksum` keys exactly. |

`calendar` is a JSON payload (for example `calendar.json`) whose annual
regeneration makes `territory` and `expiry` mandatory. The other types do not require them, but
may declare them when the licence is time- or place-limited.

## Validation rules

`ContentPackManifestParser` and `ContentPackLoader` reject:

* malformed JSON, a non-object root, or trailing content;
* unknown top-level fields (a misspelled `expiryy` cannot disable expiry);
* a manifest `id` that differs from its directory;
* missing/blank `licence` or `attribution`;
* unsupported `type` values;
* payload paths that are absolute, contain `..`/`.`/empty segments, contain
  backslashes or control characters, or name `manifest.json`;
* payload extensions outside the type's allow-list — `.json` for `json` and
  `calendar`, `.txt`/`.md` for `text`, and `.mp3`/`.ogg`/`.opus`/`.wav`/`.m4a`
  for `audio`. Executable or archive content (`.apk`, `.dex`, `.so`, `.jar`,
  `.sh`, …) is never accepted;
* a `files`/`checksum` set mismatch, duplicate paths, or a digest that is not
  a lowercase 64-character SHA-256;
* a calendar pack without a territory or expiry;
* an expired pack.

Expiry is inclusive: a pack expiring `2027-12-31` is valid on that date and
rejected from `2028-01-01`. Loading is fail-closed; a bad pack aborts the load
with a `ContentPackException` subtype rather than serving partial content.

## Adding a pack

1. Create `app/src/main/assets/packs/<pack-id>/`.
2. Add the payload files.
3. Compute the digest of each payload (paths in the manifest are relative to
   the pack directory):

   ```bash
   find app/src/main/assets/packs/<pack-id> -type f ! -name manifest.json \
     -exec sha256sum {} +
   ```

4. Write `manifest.json` with the exact relative paths and digests.
5. Run the licence gate:

   ```bash
   ./gradlew :app:testDebugUnitTest \
     --tests 'com.biblia.povitrpustok.core.pack.LicenceManifestTest'
   ```

6. Run the full JVM suite and lint before committing.

The gate (`LicenceManifestTest`) loads every real pack with the production
parser and loader. It fails on a missing licence/attribution row, a calendar
without territory or expiry, an expired term, or a checksum mismatch. Fixtures
in the same test prove each failure mode. The test does not need the private
Bible corpus, so it also runs with `-PskipCorpusTests`.

## Current packs

None in 1.3.0. `app/src/main/assets/packs/.gitkeep` only keeps the directory in
git; the loader reports directories containing entries, so `.gitkeep` is not a
pack.

## Sign-off

F1-20 requires owner sign-off on this lifecycle. Owner: Alex Pereira.
Sign-off: pending.
