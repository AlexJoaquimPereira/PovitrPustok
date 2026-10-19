#!/usr/bin/env bash
# Fetch the private Bible corpus artifact into the app's assets directory.
#
# Required environment:
#   CORPUS_ARCHIVE_URL      HTTPS URL of a ZIP containing bible/*.sqlite (or the
#                           SQLite files at the archive root)
#   CORPUS_ARCHIVE_SHA256   SHA-256 of the ZIP
# Optional:
#   CORPUS_ARCHIVE_TOKEN    Bearer token for a private bucket / repo
#   CORPUS_ARCHIVE_FILE     Local ZIP path, for offline testing (skips download)
#
# The corpus is copyrighted and is never committed. CI supplies these values
# from repository secrets; without them the workflow runs -PskipCorpusTests.
set -euo pipefail

if [[ -n "${CORPUS_ARCHIVE_FILE:-}" ]]; then
    archive="$CORPUS_ARCHIVE_FILE"
    cleanup_archive=""
else
    : "${CORPUS_ARCHIVE_URL:?CORPUS_ARCHIVE_URL is required}"
    archive="$(mktemp -t corpus-XXXXXX.zip)"
    cleanup_archive="$archive"
    auth=()
    if [[ -n "${CORPUS_ARCHIVE_TOKEN:-}" ]]; then
        auth=(-H "Authorization: Bearer ${CORPUS_ARCHIVE_TOKEN}")
    fi
    curl --fail --silent --show-error --location "${auth[@]}" \
        "$CORPUS_ARCHIVE_URL" --output "$archive"
fi

: "${CORPUS_ARCHIVE_SHA256:?CORPUS_ARCHIVE_SHA256 is required}"
echo "${CORPUS_ARCHIVE_SHA256}  ${archive}" | sha256sum --check --status || {
    echo "Corpus archive checksum mismatch: ${archive}" >&2
    exit 1
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"; [[ -z "${cleanup_archive:-}" ]] || rm -f "$cleanup_archive"' EXIT
unzip -q "$archive" -d "$tmp/extract"

source_dir="$tmp/extract/bible"
if [[ ! -d "$source_dir" ]]; then
    source_dir="$tmp/extract"
fi

# Validate the staged corpus before touching the developer's existing tree.
count="$(find "$source_dir" -maxdepth 1 -name '*.sqlite' | wc -l | tr -d ' ')"
if [[ "$count" != "73" ]]; then
    echo "Expected 73 corpus databases in the archive, found ${count}" >&2
    exit 1
fi
python3 tools/corpus/generate_manifest.py --check --assets "$source_dir"

dest="app/src/main/assets/bible"
rm -rf "$dest"
mkdir -p "$dest"
find "$source_dir" -maxdepth 1 -name '*.sqlite' -exec cp -a {} "$dest"/ \;

python3 tools/corpus/generate_manifest.py --check
echo "Fetched and verified ${count} corpus databases."
