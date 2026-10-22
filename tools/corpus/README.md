# Private Bible corpus (copyrighted)

The Konkani Bible text is copyrighted by the **Archdiocese of Goa and Daman** and is **not**
committed to this repository. This directory contains only tooling and metadata (SHA-256 hashes,
counts, schema fingerprint); it contains no scripture.

## Layout

```text
tools/corpus/
  import_corpus.py       # deterministic XML -> per-book SQLite generator
  generate_manifest.py   # writes/verifies corpus-manifest.json (hashes only)
  audit_corpus.py        # read-only importer-defect audit (anchors, verse 1)
  fetch_corpus.sh        # CI: download private ZIP, verify, extract
  corpus-manifest.json   # committed provenance/checksum manifest
```

The app expects 73 files in `app/src/main/assets/bible/*.sqlite`. That path is
gitignored (`*.sqlite`) and must be populated locally or fetched in CI.

## Local regeneration

You need lawful access to the source XML (for example a private copy of the
original application's `assets/*.xml`). Keep it outside the repository.

```bash
python3 tools/corpus/import_corpus.py \
  --source /path/to/private/xml \
  --out app/src/main/assets/bible

python3 tools/corpus/generate_manifest.py
python3 tools/corpus/generate_manifest.py --check
```

The importer is deterministic: the same XML and Python/SQLite versions produce
byte-identical databases. The current corpus reproduces
`konkani-catholic-2026.10.21`.

## Corpus audit (required before shipping a regenerated corpus)

```bash
python3 tools/corpus/audit_corpus.py
```

Read-only scan of every asset. It reports two importer defects: chapters whose
headings are all pinned to the first verse (the John-1 "all titles at the top"
artifact) and chapters whose first verse record starts after verse 1. It exits
non-zero while issues remain, so a clean run is the release gate. The current
corpus is **not** clean: only John was regenerated after the anchor/prefix
repair, so 72 books still need regeneration from the private source (the audit
lists every affected chapter). Never commit the regenerated `.sqlite` files;
regenerate `corpus-manifest.json` and ship them through the private artifact
store.

## CI (option C: private artifact store)

Do **not** put the corpus in the public repository or in public GitHub release
assets. Store a ZIP of the corpus in a private location, for example:

- a private S3/R2 bucket with server-side encryption and a scoped token, or
- a private repository/release asset accessed with a fine-grained token.

The ZIP must contain either `bible/*.sqlite` or the SQLite files at its root.

Configure repository secrets:

| Secret | Required | Meaning |
| --- | --- | --- |
| `CORPUS_ARCHIVE_URL` | yes | HTTPS URL of the private ZIP |
| `CORPUS_ARCHIVE_SHA256` | yes | SHA-256 of the ZIP |
| `CORPUS_ARCHIVE_TOKEN` | no | Bearer token for a private bucket/repo |

With `CORPUS_ARCHIVE_URL` set, CI downloads, checksums, extracts, verifies the
manifest, runs the full real-corpus suite, and assembles the release APK.
Without it, CI runs `-PskipCorpusTests`: corpus-dependent tests are reported as
**skipped** and no release APK is produced. A local checkout without assets
still fails unless you pass that property explicitly.

Encryption at rest is provided by the private store (SSE, encrypted volume, or
an encrypted archive you decrypt before calling `fetch_corpus.sh`). Note that
this protects the stored artifact, not the installed app: an APK embeds the
corpus in readable form.

## Changing the corpus

1. Replace/regenerate the SQLite files.
2. Update `CORPUS_VERSION` in `generate_manifest.py` to a new identifiable
   version.
3. Regenerate `corpus-manifest.json` and review the diff (hashes/counts only).
4. Run `./gradlew :app:testDebugUnitTest` and the device suite.
5. Upload a new private ZIP and update `CORPUS_ARCHIVE_SHA256`.
