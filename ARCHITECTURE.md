# Architecture

## Direct link workflow

    Paste or share URL
      -> SharedLinkNormalizer
      -> Direct file URL? -- yes --> ResourceSniffer / Java ResourceClassifier
      -> no --> SniffingViewModel -> YtDlpRuntime -> Site extractor and metadata
      -> Resolver result -> Download action -> WorkManager
      -> Direct HTTP file -> OkDownload + OkHttp
      -> Supported site page -> yt-dlp + FFmpeg
      -> Direct HLS/DASH manifest -> Media3
      -> MediaStore Download / SAF -> Room download history / Local files

There is no WebView screen, webpage navigation flow, DOM injection, browser request interception or general webpage sniffing.

## Package responsibilities

- MainActivity.kt: URL-first home, share-sheet URL intake, resolver results, download center and local files.
- presentation.sniffing: direct URL resolution state; calls yt-dlp and does not observe webpage or WebView state.
- presentation.downloads: DataStore output-folder/performance preferences and Room-backed download history.
- data.extractor.YtDlpRuntime: initializes the Android yt-dlp/FFmpeg runtime, refreshes the separate yt-dlp executable from the Nightly channel at startup and weekly thereafter, inspects metadata and downloads/merges media. Failed/offline update attempts leave the bundled executable intact and are retried after a six-hour cooldown.
- data.extractor.YtDlpUpdatePolicy: pure scheduling rules for first-run, weekly refresh and failed-update retry.
- data.extractor.YouTubeUrlParser and NiconicoUrlParser: validate known URL shapes before selecting site-specific handling.
- data.extractor.YtDlpInfoJsonParser: parses safe metadata and actual format availability without retaining signed media URLs or authorization headers.
- data.network: OkDownload adapter, size-aware byte-range strategy and shared speed limiter.
- data.media: Media3 DownloadHelper track selection, adaptive cache and Transformer export for direct HLS/DASH manifests.
- data.storage.DownloadOutput: MediaStore Download publication on Android 10+ and optional SAF output.
- data.local: Room entities, DAOs and database.
- data.preferences: DataStore-backed preferences.
- data.repository: repository interfaces and implementations.
- di: Hilt bindings for network, storage and repositories.
- ResourceClassifier.java: platform-independent direct URL/MIME/extension classification.
- FileNameSanitizer.java: shared safe filename normalization.
- NativeMediaProbe.kt and app/src/main/cpp: C++17/JNI payload signature inspection; it is not a decoder.
- tools/validate_media_fixtures.py: offline HLS/DASH fixture and native signature validation.

## Design constraints

1. UI calls ViewModels; it does not own network or database work.
2. Supported site video pages are passed to yt-dlp directly; the app does not open those pages.
3. WorkManager persists download scheduling and runs long transfers in a foreground worker.
4. Room stores app-facing download history. OkDownload's breakpoint store retains direct-file resume metadata.
5. Android 10+ downloads are published to the public MediaStore Download collection by default; user-granted SAF is an optional override.
6. Adaptive HLS/DASH manifests are exported through Media3 where supported; app code does not manually concatenate media segments.
7. The app does not implement DRM decryption, login bypass, region bypass or account-entitlement bypass.
8. The Android yt-dlp wrapper dependency is pinned, but the executable is refreshed independently from the yt-dlp Nightly channel at startup and weekly thereafter, so extractor changes are not frozen at the dependency release date.
9. Public Niconico links are attempted through yt-dlp's guest route without a sign-in prompt; the service still enforces genuine visibility, membership, payment, region and DRM restrictions.
10. Network access remains enabled because yt-dlp, OkHttp and Media3 request site APIs and media streams. This is separate from opening pages in an in-app browser.
