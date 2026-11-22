# Distributor Ramaputra

Android WebView shell for the Distributor Ramaputra web application. The app includes a vector launcher icon, a splash screen, and status and navigation bar colors that follow the page.

## Requirements

- JDK 17 or newer
- Android SDK Platform 37
- Android SDK Build-Tools

## Local setup

Copy `.env.example` to `.env` and set:

| Variable | Purpose |
| --- | --- |
| `RPD_UPSTREAM_URL` | HTTPS origin loaded by the WebView |
| `RPD_RELEASE_KEYSTORE` | Path to the release keystore (`.jks`) |
| `RPD_RELEASE_KEY_ALIAS` | Key alias inside the keystore |
| `RPD_RELEASE_STORE_PASSWORD` | Keystore password |
| `RPD_RELEASE_KEY_PASSWORD` | Key password |

Build the signed release APK:

```sh
./build.sh
```

The APK is written to `/tmp/rpd-dms/app-release.apk`. Build output and caches stay outside this repository. On a local machine, the script also sends the APK to file.kiwi. GitHub Actions skips that upload.

## Versioning

The release version lives in `build.sh`:

- `RPD_VERSION_NAME` defaults to `1.0.5`
- `RPD_VERSION_CODE` defaults to `9`

Override either value in the environment when you need a one-off build. For a published release, change the defaults in `build.sh` and raise both numbers. Each GitHub Release uses `v` plus `RPD_VERSION_NAME` as its tag, so a push that does not bump the version cannot publish a second release for the same tag.

## GitHub release

A push of `main` to GitHub runs the Build & release workflow. The workflow compiles the signed APK, then publishes a GitHub Release. The release notes are the commit subjects included in that push, oldest first. The first release lists every commit on `main`.

Create these Actions secrets on the GitHub repository before that push. `GITHUB_TOKEN` is provided by Actions and is not one of them.

| Secret | Value |
| --- | --- |
| `RPD_UPSTREAM_URL` | Same origin as the local `RPD_UPSTREAM_URL` |
| `RPD_RELEASE_KEYSTORE_BASE64` | Base64 of the release keystore, one line |
| `RPD_RELEASE_KEY_ALIAS` | Same alias as the local build |
| `RPD_RELEASE_STORE_PASSWORD` | Keystore password |
| `RPD_RELEASE_KEY_PASSWORD` | Key password |

Encode the keystore without wrapping the line:

```sh
base64 -w0 /path/to/release.jks
```
