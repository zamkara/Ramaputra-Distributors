# Distributor Ramaputra Android

Minimal Android WebView untuk `https://dmsx.ramaputra.co.id`.
Source hanya memuat WebView, ikon vector, splash, dan penyesuaian warna status/navigation bar.

Perlu JDK 17+, Android SDK platform 37, build tools, serta variabel signing:
`RPD_RELEASE_KEYSTORE`, `RPD_RELEASE_STORE_PASSWORD`, `RPD_RELEASE_KEY_ALIAS`,
`RPD_RELEASE_KEY_PASSWORD`.

Jalankan `./build.sh`. APK berada di `/tmp/rpd-dms/app-release.apk`.
Tidak ada hasil build atau cache yang ditulis ke repo.
