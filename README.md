# File Sniffing

File Sniffing is an Android link resolver and download manager. It no longer contains an in-app browser or webpage-sniffing screen.

Current app version: 0.9.18

## How it works

1. Paste or share a video or file URL.
2. Supported site URLs are sent directly to the bundled yt-dlp runtime for metadata and format inspection.
3. Direct media/file URLs go to the HTTP download engine.
4. Choose Download. WorkManager runs the task and shows progress in Download Center.

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
- YouTube watch URLs and playlists, plus common non-YouTube playlist URL shapes when yt-dlp exposes real HTTP(S) item page URLs. Playlist item pages are routed through yt-dlp; direct MP4/MP3 file links remain on the HTTP engine.
- Niconico watch URLs through yt-dlp's Niconico extractor.
- Bilibili and other URLs supported by the installed yt-dlp extractor set, subject to extractor version and site access rules.
- Direct HTTP(S) file and media URLs recognized by the resource classifier.
- A single media preview per resolved URL, with thumbnail, uploader, duration and actual format counts.
- A download configuration sheet for video quality, audio-only mode and compatible audio selection; formats and track languages are limited to actual yt-dlp metadata returned for the URL.
- Direct media/file URLs are identified separately and go straight to download rather than showing a fake quality picker.
- Audio-only output through yt-dlp and FFmpeg; chosen format IDs are preserved when retrying a failed task.
- Public Download folder by default on Android 10+; optional SAF destination override. Android 9 and earlier require a chosen SAF directory.
- Persistent download queue, Worker-owned task history, Room state reconciliation after app restart, direct-file pause/resume and retry with source metadata saved before enqueue. yt-dlp pause/retry reuses a stable private session and waits for the previous process to exit before starting the next attempt.
- Active URL + format deduplication prevents accidental double-tap enqueue of the same selection while allowing different qualities of the same video. Niconico tasks use a canonical watch URL rather than a changing signed CDN URL.
- Download Center supports search and sorting by recent, filename and progress.

## Reliability checks\n\n- Direct HTTP output is checked for empty responses, known byte-count truncation and common HTML/JSON/XML error payloads before it is marked complete. The native signature probe also detects JSON bodies served with generic binary MIME, while a verified media signature takes precedence over a misleading plain-text header.\n- yt-dlp output is checked before it is published; incomplete temporary work is retained only for resumable sessions.\n- MediaStore publication errors are surfaced instead of silently treating an unpublished pending file as complete.\n- Pause/cancel states are confirmed against WorkManager, and durable task history is reconciled when the app starts again.\n\n## Important limits

- The app no longer provides a browser. Users paste or share a link; the app does not navigate to a login page or scrape visible page content.
- The yt-dlp executable is checked against the Nightly channel at app startup and then weekly, matching Seal's updater strategy. When offline or an update fails, the bundled executable remains available and the updater retries after a six-hour cooldown.
- Site extraction still depends on each site's current APIs and access rules. Extractor errors are reported rather than replaced with guessed formats.
- Some YouTube videos require newer JavaScript challenge support, EJS components or PO Tokens; those runtime integrations are not currently bundled.
- Private, Premium-only, PPV, member-only, region-restricted, DRM-protected or otherwise unauthorized media is not unlocked by the app.
- Public Niconico watch links are attempted through yt-dlp's guest route; the app does not request an account or password. The service's genuine video-visibility, Premium, channel membership, PPV, region and DRM rules still apply.
- Direct-file multi-connection downloads depend on server Range support. A higher connection ceiling cannot force a server to accept parallel requests.
- HLS/DASH export depends on Media3 support for the manifest and codecs. Encrypted/DRM streams are not decrypted.
- Temporary yt-dlp output uses app-private storage and is removed after the operation; signed media URLs are not written to Room or app logs.

## Reference review

A repository-specific review of 26 high-star open-source downloaders, yt-dlp frontends and media-stream tools is maintained in [docs/OPEN_SOURCE_BENCHMARK.md](docs/OPEN_SOURCE_BENCHMARK.md). It explains which patterns were adopted and what remains on the roadmap.

## Build

Open the project in Android Studio with JDK 17 and Android SDK 36, or run:

    python3 tools/validate_media_fixtures.py
    ./gradlew :app:assembleDebug

GitHub Actions builds separate debug APKs on pushes and pull requests to main. Download only the artifact matching the phone CPU architecture: `file-sniffing-arm64-v8a` for most modern 64-bit Android phones, `file-sniffing-armeabi-v7a` for 32-bit ARM devices, or `file-sniffing-x86_64` for x86_64 emulators. There is no universal APK containing all three copies of Python and FFmpeg.
