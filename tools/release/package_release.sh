#!/usr/bin/env bash
#
# Builds and packages a signed Povitr Pustok release for GitHub Releases.
# Never uploads anything and never touches the network.
#
# Usage:
#   tools/release/package_release.sh [--dry-run] [--skip-build]
#
#   --dry-run     Name the output as a dry run (includes the short commit).
#   --skip-build  Reuse the existing release outputs. Rehearsal only: the
#                 outputs must have been built from the current HEAD.
#
# Requires: a gitignored keystore.properties, the private corpus in
# app/src/main/assets/bible, JDK 25, Android SDK build-tools, python3, unzip,
# sha256sum and jarsigner.

set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$repo_root"

dry_run=false
skip_build=false
for arg in "$@"; do
    case "$arg" in
        --dry-run) dry_run=true ;;
        --skip-build) skip_build=true ;;
        *)
            echo "unknown argument: $arg" >&2
            exit 2
            ;;
    esac
done

if [[ ! -f keystore.properties ]]; then
    echo "keystore.properties is missing; a signed release cannot be produced." >&2
    exit 1
fi

if [[ "$skip_build" == false ]]; then
    ./gradlew :app:assembleRelease :app:bundleRelease
else
    echo "warning: --skip-build reuses existing outputs; confirm they match HEAD." >&2
fi

apk=app/build/outputs/apk/release/app-release.apk
aab=app/build/outputs/bundle/release/app-release.aab
metadata=app/build/outputs/apk/release/output-metadata.json
for file in "$apk" "$aab" "$metadata"; do
    if [[ ! -f "$file" ]]; then
        echo "missing $file; run without --skip-build" >&2
        exit 1
    fi
done

read -r version_name version_code < <(python3 - "$metadata" <<'PY'
import json
import sys

data = json.load(open(sys.argv[1], encoding="utf-8"))
element = data["elements"][0]
print(element["versionName"], element["versionCode"])
PY
)

apksigner=${APKSIGNER:-}
if [[ -z "$apksigner" && -n "${ANDROID_HOME:-}" ]]; then
    apksigner="$ANDROID_HOME/build-tools/36.0.0/apksigner"
fi
if [[ ! -x "$apksigner" ]]; then
    apksigner=$(command -v apksigner || true)
fi
if [[ -z "$apksigner" ]]; then
    echo "apksigner not found; set APKSIGNER or ANDROID_HOME" >&2
    exit 1
fi

verify_output=$("$apksigner" verify --verbose --print-certs "$apk")
if ! grep -qE 'Verified using v[23] scheme .*: true' <<<"$verify_output"; then
    echo "release APK is not signed with APK Signature Scheme v2 or v3" >&2
    exit 1
fi
signer_sha256=$(awk -F': ' '/Signer #1 certificate SHA-256 digest/ {print $2}' <<<"$verify_output")
if [[ -z "$signer_sha256" ]]; then
    echo "could not read the signer certificate digest" >&2
    exit 1
fi

aab_verify_output=$(jarsigner -verify "$aab" 2>&1 || true)
if ! grep -q 'jar verified\.' <<<"$aab_verify_output"; then
    echo "release AAB is not signed" >&2
    exit 1
fi

corpus_count=$(unzip -l "$apk" | grep -cE 'assets/bible/[^/]+\.sqlite$' || true)
if [[ "$corpus_count" -ne 73 ]]; then
    echo "release APK has $corpus_count corpus assets; expected 73" >&2
    exit 1
fi
aab_corpus_count=$(unzip -l "$aab" | grep -cE 'assets/bible/[^/]+\.sqlite$' || true)
if [[ "$aab_corpus_count" -ne 73 ]]; then
    echo "release AAB has $aab_corpus_count corpus assets; expected 73" >&2
    exit 1
fi
if unzip -l "$apk" | grep -qE '(^| )assets/schemas/|(^| )schemas/'; then
    echo "debug Room schemas found in the release APK" >&2
    exit 1
fi
if unzip -l "$aab" | grep -qE '(^| )assets/schemas/|(^| )schemas/'; then
    echo "debug Room schemas found in the release AAB" >&2
    exit 1
fi

version_slug=$(printf '%s' "$version_name" | tr -c 'A-Za-z0-9._-' '-')
commit=$(git rev-parse --short HEAD)
if [[ "$dry_run" == true ]]; then
    base="povitr-pustok-${version_slug}-dry-run-${commit}"
else
    base="povitr-pustok-${version_slug}"
fi
out_dir="app/build/outputs/release/$base"
mkdir -p "$out_dir"
cp "$apk" "$out_dir/$base.apk"
cp "$aab" "$out_dir/$base.aab"

cat > "$out_dir/BUILD-INFO.txt" <<EOF
versionName=$version_name
versionCode=$version_code
commit=$(git rev-parse HEAD)
builtAt=$(date -u +%Y-%m-%dT%H:%M:%SZ)
signerSHA256=$signer_sha256
corpusAssets=$corpus_count
dryRun=$dry_run
EOF

(
    cd "$out_dir"
    sha256sum "$base.apk" "$base.aab" > SHA256SUMS
)

echo "Packaged $base in $out_dir"
cat "$out_dir/SHA256SUMS"
