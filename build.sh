#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")" && pwd)
if [[ -f "$root/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "$root/.env"
    set +a
fi
sdk=${ANDROID_HOME:-/home/zam/Android/Sdk}
platform=${ANDROID_PLATFORM:-android-37.0}
version_code=${RPD_VERSION_CODE:-7}
version_name=${RPD_VERSION_NAME:-1.0.3}
jar="$sdk/platforms/$platform/android.jar"
[[ -f "$jar" ]] || { echo "Android SDK platform $platform diperlukan." >&2; exit 1; }
: "${RPD_RELEASE_KEYSTORE:?Set RPD_RELEASE_KEYSTORE}"
: "${RPD_RELEASE_STORE_PASSWORD:?Set RPD_RELEASE_STORE_PASSWORD}"
: "${RPD_RELEASE_KEY_ALIAS:?Set RPD_RELEASE_KEY_ALIAS}"
: "${RPD_RELEASE_KEY_PASSWORD:?Set RPD_RELEASE_KEY_PASSWORD}"

sdk_tools=$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)
tmp=$(mktemp -d /tmp/rpd-build.XXXXXX)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/classes" "$tmp/dex" "$tmp/package/assets" /tmp/rpd-dms

"$sdk_tools/aapt2" compile --dir "$root/res" -o "$tmp/res.zip"
"$sdk_tools/aapt2" link -o "$tmp/base.apk" --manifest "$root/AndroidManifest.xml" \
    -I "$jar" --min-sdk-version 26 --target-sdk-version 37 \
    --version-code "$version_code" --version-name "$version_name" "$tmp/res.zip"
javac --release 17 -cp "$jar" -d "$tmp/classes" "$root/src/MainActivity.java"
"$sdk_tools/d8" --release --min-api 26 --lib "$jar" --output "$tmp/dex" \
    "$tmp/classes/id/co/ramaputra/dms/"*.class
cp "$tmp/dex/classes.dex" "$tmp/package/"
cp "$root/src/system-bars.js" "$tmp/package/assets/"
cp "$tmp/base.apk" "$tmp/unaligned.apk"
(cd "$tmp/package" && zip -q -u "$tmp/unaligned.apk" classes.dex assets/system-bars.js)
"$sdk_tools/zipalign" -f 4 "$tmp/unaligned.apk" "$tmp/aligned.apk"
output=/tmp/rpd-dms/app-release.apk
"$sdk_tools/apksigner" sign --ks "$RPD_RELEASE_KEYSTORE" \
    --ks-key-alias "$RPD_RELEASE_KEY_ALIAS" --v4-signing-enabled false \
    --ks-pass env:RPD_RELEASE_STORE_PASSWORD --key-pass env:RPD_RELEASE_KEY_PASSWORD \
    --out "$output" "$tmp/aligned.apk"
"$sdk_tools/apksigner" verify "$output"
ls -lh "$output"
if command -v filekiwi >/dev/null 2>&1; then
    filekiwi --title "Distributor Ramaputra $version_name" "$output"
else
    npx -y @file-kiwi/node --title "Distributor Ramaputra $version_name" "$output"
fi
