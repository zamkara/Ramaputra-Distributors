# Distributor Ramaputra Android

Minimal Android WebView dengan ikon vector, splash screen, dan penyesuaian warna status/navigation bar.

Perlu JDK 17+, Android SDK platform 37, dan build tools.
Salin `.env.example` menjadi `.env`, lalu isi `RPD_UPSTREAM_URL` dan variabel signing (`RPD_RELEASE_KEYSTORE`, `RPD_RELEASE_STORE_PASSWORD`, `RPD_RELEASE_KEY_ALIAS`, `RPD_RELEASE_KEY_PASSWORD`).

Jalankan `./build.sh`. APK berada di `/tmp/rpd-dms/app-release.apk`.
Tidak ada hasil build atau cache yang ditulis ke repo.
