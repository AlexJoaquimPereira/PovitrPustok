# Release checklist

Povitr Pustok ships through **GitHub Releases + Obtainium**. There is no Play Store,
F-Droid, or CI-published APK.

> **Private repository.** Releases are distributed from a private repository by owner
> decision. Anonymous downloads are not available; Obtainium must be configured with a
> GitHub token that can read the repository (fine-grained token: Contents: Read). The
> repository history contains no corpus, keystore, or secret blobs, so this is an
> access-control choice, not a corpus-protection requirement.

## 1. One-time setup

- [ ] The release key exists outside the repository; `keystore.properties` points to it.
- [ ] Two offline encrypted backups of the JKS and its passwords exist, and a restore has
      been tested in a throwaway directory.
      *Current key: owner confirmed both backups and a tested restore on 2026-10-28.*
- [ ] The signing certificate SHA-256 is recorded and matches every release:
      `AD:D0:11:B3:87:7E:31:63:BE:D1:4E:0A:A8:A4:FB:AD:B6:F8:3E:A1:23:5B:38:42:AC:ED:C7:99:35:26:A2:61`.
- [ ] `ANDROID_HOME` points at an SDK with build-tools 36.0.0; the Gradle daemon uses JDK 25.
- [ ] The private corpus is present and `python3 tools/corpus/generate_manifest.py --check` passes.
- [ ] The working tree is clean, on `master`, and up to date with `origin`.

## 2. Prepare

- [ ] Bump `versionCode` and `versionName` in `app/build.gradle.kts`; commit the bump.
- [ ] Update README test counts, routes, or architecture notes if the release changes them.
- [ ] Draft the release notes from [`release-notes-template.md`](release-notes-template.md).
- [ ] Confirm no `.sqlite`, corpus XML, `keystore.properties`, or JKS file is staged.
- [ ] Every shipped text/data/pack has a current row in `docs/rights.md`, and
      `RightsRegisterTest` passes. Review every `pending` row and record owner acceptance for
      pending content shipping in this release (currently the Goan calendar overrides).

## 3. Verify and package

- [ ] `./gradlew :app:testDebugUnitTest`
- [ ] `ANDROID_SERIAL=<device> ./gradlew :app:connectedDebugAndroidTest`
- [ ] `./gradlew :app:lintDebug`
- [ ] `python3 tools/corpus/generate_manifest.py --check` (the JVM suite also
      fails on manifest drift, but check before packaging a new corpus).
- [ ] `tools/release/package_release.sh` (add `--dry-run` for a rehearsal).
      This builds the signed APK/AAB, checks 73 corpus assets, rejects debug schemas,
      and writes `SHA256SUMS` plus `BUILD-INFO.txt` under `app/build/outputs/release/`.
- [ ] `apksigner verify --print-certs` shows APK Signature Scheme v3 and the expected
      certificate.
- [ ] `jarsigner -verify` accepts the AAB.
- [ ] Record the APK and AAB SHA-256 values from `SHA256SUMS`.

## 4. Upgrade test

- [ ] Install the previous published release on a clean API 30+ device/emulator.
- [ ] Create a bookmark and read a chapter, so both user-data tables are populated.
- [ ] Install the new APK over it with `adb install -r <new>.apk`.
- [ ] Verify the home screen shows the saved chapter under **Continue reading**, the bookmark
      is listed, and opening it returns to the saved chapter.
- [ ] Record the result in `docs/releases/<version>-dry-run.md` or the release issue.

## 5. Publish

- [ ] Commit the version bump (release notes stay in the GitHub release body, not a
      committed file); create an annotated tag `v<version>`; push `master` and the tag.
- [ ] GitHub release: title, notes, and the APK, AAB, and `SHA256SUMS` assets.
- [ ] Never attach corpus files, source XML, debug/test APKs, R8 mapping files, or keystores.
- [ ] Confirm the release is visible to the Obtainium token; the repository is private, so
      anonymous downloads are not expected.
- [ ] Obtainium: configure a GitHub token with read access, refresh, and confirm it sees the
      new version and installs it.

## 6. Post-release

- [ ] Download the published APK and run `sha256sum -c SHA256SUMS`.
- [ ] Keep `app/build/outputs/mapping/release/mapping.txt` and the artifacts locally for 90 days.
- [ ] If the release is broken: mark it pre-release/delete its assets and republish the previous
      version. Never re-sign a fix with a different key; always increase `versionCode`.

## CI guarantee

CI builds a release only when `CORPUS_ARCHIVE_URL` is configured. Without it, CI runs
`-PskipCorpusTests`, skips `assembleRelease`, and asserts that no release APK exists. CI uploads
only test reports; it never publishes an APK or AAB.

## Release record

| Field | Value |
|---|---|
| Version / versionCode | |
| Commit / tag | |
| APK SHA-256 | |
| AAB SHA-256 | |
| Signer SHA-256 | |
| JVM / instrumented / lint | |
| Upgrade from | |
| Date / approver | |
