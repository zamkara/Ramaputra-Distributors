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
version_code=${RPD_VERSION_CODE:-9}
version_name=${RPD_VERSION_NAME:-1.0.5}
jar="$sdk/platforms/$platform/android.jar"
[[ -f "$jar" ]] || { echo "Android SDK platform $platform diperlukan." >&2; exit 1; }
: "${RPD_UPSTREAM_URL:?Set RPD_UPSTREAM_URL}"
: "${RPD_RELEASE_KEYSTORE:?Set RPD_RELEASE_KEYSTORE}"
: "${RPD_RELEASE_STORE_PASSWORD:?Set RPD_RELEASE_STORE_PASSWORD}"
: "${RPD_RELEASE_KEY_ALIAS:?Set RPD_RELEASE_KEY_ALIAS}"
: "${RPD_RELEASE_KEY_PASSWORD:?Set RPD_RELEASE_KEY_PASSWORD}"

sdk_tools=$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)
tmp=$(mktemp -d /tmp/rpd-build.XXXXXX)
trap 'rm -rf "$tmp"' EXIT
upstream_home=${RPD_UPSTREAM_URL%/}
upstream_host=${upstream_home#https://}
upstream_host=${upstream_host#http://}
upstream_host=${upstream_host%%/*}
upstream_host=${upstream_host%%:*}
mkdir -p "$tmp/src" "$tmp/classes" "$tmp/dex" "$tmp/package/assets" /tmp/rpd-dms
cat > "$tmp/src/BuildConfig.java" <<EOF
package id.co.ramaputra.dms;

final class BuildConfig {
    static final String HOME = "$upstream_home";
    static final String HOST = "$upstream_host";
}
EOF

"$sdk_tools/aapt2" compile --dir "$root/res" -o "$tmp/res.zip"
"$sdk_tools/aapt2" link -o "$tmp/base.apk" --manifest "$root/AndroidManifest.xml" \
    -I "$jar" --min-sdk-version 26 --target-sdk-version 37 \
    --version-code "$version_code" --version-name "$version_name" "$tmp/res.zip"
javac -g:none --release 17 -cp "$jar" -d "$tmp/classes" \
    "$tmp/src/BuildConfig.java" "$root/src/MainActivity.java"
"$sdk_tools/d8" --release --min-api 26 --lib "$jar" --output "$tmp/dex" \
    "$tmp/classes/id/co/ramaputra/dms/"*.class
cp "$tmp/dex/classes.dex" "$tmp/package/"
cp "$root/src/system-bars.js" "$tmp/package/assets/"
cp "$tmp/base.apk" "$tmp/unaligned.apk"
(cd "$tmp/package" && zip -q -9 -u "$tmp/unaligned.apk" classes.dex assets/system-bars.js)
"$sdk_tools/zipalign" -f 4 "$tmp/unaligned.apk" "$tmp/aligned.apk"
output=/tmp/rpd-dms/app-release.apk
apksigner=(java --enable-native-access=ALL-UNNAMED -jar "$sdk_tools/lib/apksigner.jar")
"${apksigner[@]}" sign --ks "$RPD_RELEASE_KEYSTORE" \
    --ks-key-alias "$RPD_RELEASE_KEY_ALIAS" \
    --v1-signing-enabled false --v4-signing-enabled false \
    --ks-pass env:RPD_RELEASE_STORE_PASSWORD --key-pass env:RPD_RELEASE_KEY_PASSWORD \
    --out "$output" "$tmp/aligned.apk"
"${apksigner[@]}" verify "$output"
ls -lh "$output"
if command -v filekiwi >/dev/null 2>&1; then
    (cd "$tmp" && filekiwi --title "Distributor Ramaputra $version_name" "$output")
else
    (cd "$tmp" && npx -y @file-kiwi/node --title "Distributor Ramaputra $version_name" "$output")
fi
