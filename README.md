# File Sniffing

File Sniffing is an Android link resolver and download manager. It no longer contains an in-app browser or webpage-sniffing screen.

Current app version: 0.9.13

## How it works

1. Paste or share a video or file URL.
2. Supported site URLs are sent directly to the bundled yt-dlp runtime for metadata and format inspection.
3. Direct media/file URLs go to the HTTP download engine.
4. Choose Download. WorkManager runs the task and shows progress in Download Center.
5. For Niconico videos that require registration, optionally save a Niconico account from the account icon on the resolver screen.

The app does not open supplied URLs in a WebView, inject scripts into pages, scan DOM content or intercept browser page requests.

## Technology

| Layer | Technology | Responsibility |
|---|---|---|
| UI | Kotlin, Jetpack Compose, Material 3 | URL input, resolver results, download center and local files |
| Site extraction | yt-dlp Android 0.17.3 runtime + yt-dlp Nightly executable | Resolve supported site URLs, metadata, playlists and media formats; refresh extractor code independently of the Android wrapper |
| Media merging | FFmpeg | Merge separate audio/video streams and convert requested audio output |
| Direct downloads | OkHttp 5 + OkDownload 1.0.7 | HTTP(S) transfers, range connections, speed cap and breakpoint resume |
| Adaptive media | Media3 ExoPlayer, DownloadHelper, Transformer | Handle direct HLS/DASH manifests where supported and export MP4 |
| Architecture | MVVM, Kotlin Flow, Hilt | ViewModels, reactive UI state and dependency injection |
| Local data | Room, DataStore | Download history, destination and performance preferences |
| Background | WorkManager | Persistent downloads, progress, pause/cancel and retry |
| Resource classification | Java | Detect direct file URLs by extension and MIME type |
| Native inspection | C++17 + JNI | Verify downloaded payload signatures and detect HTML error responses |
| Developer validation | Python 3 | Validate local HLS/DASH fixtures and native media signatures |

## Supported workflows

- Single video or audio URLs supported by yt-dlp.
- YouTube watch URLs and playlist URLs.
- Niconico watch URLs through yt-dlp's Niconico extractor.
- Bilibili and other URLs supported by the installed yt-dlp extractor set, subject to extractor version and site access rules.
- Direct HTTP(S) file and media URLs recognized by the resource classifier.
- Audio-only output through yt-dlp and FFmpeg.
- Public Download folder by default on Android 10+; optional SAF destination override. Android 9 and earlier require a chosen SAF directory.
- Persistent download queue, task history, direct-file pause/resume, retry and local file browsing.

## Important limits

- The app no longer provides a browser. Users paste or share a link; the app does not navigate to a login page or scrape visible page content.
- The yt-dlp executable is checked against the Nightly channel at app startup and then weekly, matching Seal's updater strategy. When offline or an update fails, the bundled executable remains available and the updater retries after a six-hour cooldown.\n- Site extraction still depends on each site's current APIs and access rules. Extractor errors are reported rather than replaced with guessed formats.
- Some YouTube videos require newer JavaScript challenge support, EJS components or PO Tokens; those runtime integrations are not currently bundled.
- Private, Premium-only, PPV, member-only, region-restricted, DRM-protected or otherwise unauthorized media is not unlocked by the app.
- Niconico supports optional account/password sign-in through yt-dlp. Credentials are encrypted with Android Keystore; each Niconico operation creates a temporary owner-only netrc file and deletes it afterward. No WebView or in-app page navigation is involved.
- Niconico authentication only supplies the user's own account session. Premium-only, channel-member-only, PPV, DRM or other entitlement restrictions still apply. The current UI does not collect a one-time 2FA code.
- Direct-file multi-connection downloads depend on server Range support. A higher connection ceiling cannot force a server to accept parallel requests.
- HLS/DASH export depends on Media3 support for the manifest and codecs. Encrypted/DRM streams are not decrypted.
- Temporary yt-dlp output uses app-private storage and is removed after the operation; signed media URLs are not written to Room or app logs.

## Build

Open the project in Android Studio with JDK 17 and Android SDK 36, or run:

    python3 tools/validate_media_fixtures.py
    ./gradlew :app:assembleDebug

GitHub Actions builds a debug APK on pushes and pull requests to main, publishing the APK as the file-sniffing-debug artifact.
