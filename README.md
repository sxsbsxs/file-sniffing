# File Sniffing

An Android-first webpage resource sniffer and general file download manager.

**Current app version:** 0.9.10

## Technology architecture

| Layer | Technology | Responsibility |
|---|---|---|
| UI | Kotlin, Jetpack Compose, Material 3 | Native home, resource list, download center |
| Sniffing | WebView, shouldInterceptRequest, injected JavaScript, yt-dlp Android runtime | Observe generic page media and use yt-dlp's dedicated extractors for Niconico and YouTube |
| HTTP | OkHttp 5 + OkDownload 1.0.7 | Shared HTTP stack, URL-scoped WebView session cookies, configurable multi-connection transfer, shared speed cap and breakpoint persistence |
| Adaptive media | Media3 ExoPlayer, DownloadHelper, Transformer | Inspect HLS/DASH tracks, select supported high-bitrate tracks and export MP4 |
| Architecture | MVVM, Kotlin Flow, Hilt | ViewModels, reactive state, dependency injection |
| Local data | Room, DataStore | Sniff history, download history and selected output folder |
| Background | WorkManager, foreground worker notification | Persistent download jobs, progress, cancel and retry |
| Resource classification | Java | URL, extension, MIME and sanitized download-filename inference for media, documents, archives, apps, fonts, code and data files |
| Filename policy | Java | Shared safe filename normalization for URL-derived and downloaded names |
| YouTube extraction | yt-dlp Android 0.17.3 + FFmpeg | Inspect actual format metadata and download/merge YouTube media in a persistent worker; current default download selects best available video/audio |
| Native inspection | C++17 + JNI | Identify downloaded media signatures, fragmented MP4 segments and HTML error pages |
| Developer validation | Python 3 | Run offline HLS/DASH fixture checks, compile and test Java resource classification and validate C++ media signatures |

OkDownload 1.0.7 is intentionally hidden behind `OkDownloadTransferEngine`: it is an older upstream library, so the rest of the app depends on the adapter contract rather than on its API directly.

## Current implementation

- [x] Compose + Material 3 app shell, minimal link-entry home, share-sheet URL receiving and external HTTP/HTTPS link opening.
- [x] WebView inspection using `shouldInterceptRequest`, DOM scanning, Performance API and a MutationObserver, with main-frame errors separated from empty results and YouTube subresource request diagnostics shown without retaining URLs or credential values.
- [x] Niconico video extraction for `nicovideo.jp/watch` and `nico.ms` links through yt-dlp's dedicated Niconico extractor, including its Watch API, DMS access-right request and actual HLS format enumeration.
- [x] Use the same yt-dlp + FFmpeg worker path for Niconico and YouTube, instead of maintaining a separate hand-written Niconico API implementation in the active extraction flow.
- [x] Expand/collapse the embedded page preview so users can interact with a site and sign in before downloading resources.
- [x] Java resource classifier for media, documents, archives, apps/installers, fonts, code/data and generic files, including MIME-based extensionless URLs and sanitized HTML download-name hints.
- [x] Page scanning for media DOM elements, recognized file URLs and explicit `<a download>` links, including dynamically added links; standalone HLS/DASH segments are hidden from the normal results list.
- [x] Recognize the Japanese mobile-browser region notice for diagnostics. A page-text warning is not treated as proof that the separate yt-dlp media API is denied; when yt-dlp returns a real media format, the extracted video is shown and the stale page warning is suppressed.
- [x] Result filters appear only for categories actually discovered on the current page.
- [x] Multi-select resources and batch enqueue.
- [x] OkHttp shared client, OkDownload adapter and public MediaStore `Download/` output by default, with optional user-selected SAF folders.
- [x] Install the WebView cookie interceptor in the shared OkHttp network client used by OkDownload and Media3; CookieManager scopes cookies to each exact request URL and redirect exchange, and cookie values are never stored in Room or logged.
- [x] Preserve the same output URI for interrupted direct transfers so OkDownload can resume saved byte ranges on retry in either MediaStore Download or a SAF folder.
- [x] Media3 `DownloadHelper` prepares adaptive streams and derives selected stream keys, preferring the highest supported bitrate by default.
- [x] Read actual HLS/DASH video heights from Media3 track mapping, offer those options in the resource chooser, and carry the selected height through Worker, Room and retry.
- [x] Media3 `Transformer` exports selected HLS/DASH streams to MP4 and publishes them into the public Download folder by default (or the chosen SAF folder).
- [x] Hilt application and Hilt-aware WorkManager worker.
- [x] MVVM + StateFlow for sniffing state and download-center preferences/history.
- [x] Room database for captured resources and persistent download records.
- [x] DataStore for the optional output folder and configurable download performance.
- [x] WorkManager foreground download jobs with notifications, progress updates, cancellation and retry.
- [x] Pause direct HTTP/HTTPS tasks and continue from the saved OkDownload breakpoint when the server supports byte ranges; persist and distinguish paused tasks from cancelled tasks after app restart.
- [x] Download-center filters for task status and discovered resource kind.
- [x] Live direct-transfer byte counts, total size and speed; remaining time is shown only when the server provides a usable total length and a speed estimate exists.
- [x] Persist direct-transfer byte count, confirmed total size and speed in Room so transfer metrics survive app restarts.
- [x] MIME-aware SAF file creation and output-name inference for common document, archive, application, font, image, audio and video formats.
- [x] Restore task list from WorkManager and Room after reopening the app.
- [x] Browse the public Download folder in-app by default on Android 10+ or the selected SAF folder, including subfolders, filename search, MIME filters, file sizes, modification dates and opening files with Android.
- [x] Let users configure direct-file byte-range connections (1–512), concurrent download tasks (1–4) and a shared speed cap (unlimited, 1/2/5/10/20 MB/s). Large files scale up to the configured ceiling; HLS/DASH segment workers are separately capped at 32.
- [x] Persist the original URL, Referer and SAF destination tree in Room so failed/cancelled jobs can be retried without relying on WorkInfo input data.
- [x] Restore task filenames from durable WorkManager tags instead of trying to read unavailable inputData from WorkInfo.
- [x] Verify direct-download payload signatures through a C++17/JNI probe before reporting completion; HTML error pages are reported as failures.
- [x] Validate HLS/DASH fixtures, Java URL/MIME classification, YouTube diagnostic redaction and native ISO BMFF/file signatures with the Python CI tool.

## Site-specific extraction

Niconico and YouTube use the upstream Seal-family `youtubedl-android` runtime (yt-dlp 0.17.3 wrapper), not WebView request scraping, to inspect metadata and download media. YouTube playlists use yt-dlp flat-playlist output. Niconico uses yt-dlp's own Watch API + DMS/HLS extractor, so the browser's mobile-page region banner does not by itself decide whether API media extraction is available. The app flushes and reads the Android WebView Chromium cookie database, exports only Google/YouTube and Niconico-related cookie domains to a private, short-lived Netscape file, and deletes the file after each yt-dlp operation. Downloads run through yt-dlp and FFmpeg. Dynamic per-format UI selection, EJS/JavaScript challenge runtime and a PO Token provider are not integrated yet; videos requiring those components may still fail. See [the extraction research report](docs/YOUTUBE_EXTRACTION_RESEARCH.md) for remaining runtime and compatibility work.

## Known limitations

- This is a generic webpage sniffer with yt-dlp site-specific extractors for supported providers, not a universal extractor for every site. Generic-site results remain best-effort and depend on URLs exposed to WebView request callbacks, the DOM or the Performance API.
- Blob-only URLs, service-worker-private responses, app-private media and sites that conceal media URLs may not be extractable as ordinary files.
- Generic downloadable assets are best-effort: known file extensions and explicit HTML `download` links are captured, but WebView's request interception callback does not expose response headers such as `Content-Disposition`; opaque extensionless endpoints without a DOM filename or MIME hint may remain unidentified.
- The local file browser reads the public Download collection by default on Android 10+ or the user-selected SAF directory; it is not a full-device filesystem explorer and does not bypass Android storage permissions.
- Media3 handles clear HLS/DASH streams that its supported extractors and muxer can decode. DRM/protected streams are not decrypted or bypassed. Some live, unusual, encrypted or malformed manifests may fail with a descriptive export error.
- Adaptive exports use a temporary file in the app cache before publishing to Download or the selected SAF destination. Large exports therefore need sufficient temporary storage.
- Android 10+ downloads use the public MediaStore Download collection; Android 9 and earlier require the user to grant a SAF folder. Transfer speed depends on network quality and server support for ranged requests; a higher connection limit cannot force a server to provide parallel ranges.
- Niconico extraction delegates the Watch API, DMS access-right request and HLS format enumeration to yt-dlp's maintained Niconico extractor. Cookies are exported from the current WebView cookie database for the supported site domains and passed to yt-dlp as a private temporary Netscape file. The embedded mobile browser's regional warning is not treated as a definitive API denial: only the extractor's actual response determines whether media formats are available. Premium-only, pay-per-view, channel-member and genuinely region-restricted responses remain subject to the account and service's access rules. The app does not bypass DRM or account entitlements.
- YouTube URL recognition validates watch, youtu.be, Shorts, live and embed URLs. The app now calls yt-dlp for actual metadata and routes downloads through yt-dlp + FFmpeg. Signed media URLs are not stored in UI state or Room. Videos requiring an external JS runtime, EJS challenge components or PO Tokens may still fail; those integrations and dynamic quality selection remain outstanding. WebView request diagnostics are supplementary and are not used as the YouTube extractor.
- WebView cookies are read from Chromium's cookie database after flushing CookieManager, filtered to the relevant YouTube/Google and Niconico domains, and exported to an app-private temporary Netscape file for yt-dlp. The file is deleted after each operation. Cookie values are not uploaded, stored in Room or written to logs. Cookies for unrelated domains, app-only tokens and non-cookie authorization schemes are not synthesized.
- OkDownload upstream 1.0.7 is old. It is isolated behind an adapter and should be reconsidered if a maintained alternative or a safe local fork is adopted.
- The adaptive chooser lists resolutions exposed by the current HLS/DASH manifest through Media3 track mapping. Live, encrypted, DRM-protected, malformed or otherwise unsupported manifests may not expose selectable tracks.
- Pause/continue is available for ordinary HTTP/HTTPS downloads when the server supports byte ranges. HLS/DASH exports cache selected segments in a task-specific private Media3 cache; pausing and retrying can reuse completed segments, while final remux/export is repeated. A stream whose manifest or signed segment URLs expire may still need a fresh parse.
- HLS/DASH pause and continue controls are exposed in the download center. Explicit cancellation discards the adaptive partial cache; a pause retains it. Real-device validation across multiple live/VOD HLS and DASH providers is still outstanding.

## Privacy and scope

- No account or backend server is required for the basic workflow.
- Page inspection and downloads run on the device.
- Credentials are not uploaded or stored by the downloader. CookieManager is queried for the exact request URL and its domain/path policy decides whether cookies apply; cookie values are never logged or copied into Room. Diagnostic metadata records only request categories, counters, status codes and whether header names were present; it never stores URL query strings, Cookie values, Authorization values or PO Tokens.
- DRM circumvention, authentication bypass and protected-stream decryption are out of scope.
- Cleartext HTTP remains permitted for user-entered pages for compatibility; HTTPS is preferred. Mixed content inside HTTPS pages remains blocked by WebView.

## Build

Open the project in Android Studio with JDK 17 and Android SDK 36, or run:

```bash
python3 tools/validate_media_fixtures.py
./gradlew :app:assembleDebug
```

GitHub Actions builds a debug APK on pushes and pull requests to `main`, publishing the APK as the `file-sniffing-debug` artifact.
