# File Sniffing

An Android-first webpage resource sniffer and general file download manager.

**Current app version:** 0.9.5

## Technology architecture

| Layer | Technology | Responsibility |
|---|---|---|
| UI | Kotlin, Jetpack Compose, Material 3 | Native home, resource list, download center |
| Sniffing | WebView, shouldInterceptRequest, injected JavaScript | Observe page requests, DOM media elements and dynamic resource changes |
| HTTP | OkHttp 5 + OkDownload 1.0.7 | Shared HTTP stack, URL-scoped WebView session cookies, multi-connection transfer and breakpoint persistence |
| Adaptive media | Media3 ExoPlayer, DownloadHelper, Transformer | Inspect HLS/DASH tracks, select supported high-bitrate tracks and export MP4 |
| Architecture | MVVM, Kotlin Flow, Hilt | ViewModels, reactive state, dependency injection |
| Local data | Room, DataStore | Sniff history, download history and selected output folder |
| Background | WorkManager, foreground worker notification | Persistent download jobs, progress, cancel and retry |
| Resource classification | Java | URL, extension, MIME and sanitized download-filename inference for media, documents, archives, apps, fonts, code and data files |
| Filename policy | Java | Shared safe filename normalization for URL-derived and downloaded names |
| YouTube request diagnostics | Java + Kotlin | Classify Player API and Google Video requests; show redacted request/error counters and distinguish a recognized video page from an actual media stream |
| Native inspection | C++17 + JNI | Identify downloaded media signatures, fragmented MP4 segments and HTML error pages |
| Developer validation | Python 3 | Run offline HLS/DASH fixture checks, compile and test Java resource classification and validate C++ media signatures |

OkDownload 1.0.7 is intentionally hidden behind `OkDownloadTransferEngine`: it is an older upstream library, so the rest of the app depends on the adapter contract rather than on its API directly.

## Current implementation

- [x] Compose + Material 3 app shell, minimal link-entry home, share-sheet URL receiving and external HTTP/HTTPS link opening.
- [x] WebView inspection using `shouldInterceptRequest`, DOM scanning, Performance API and a MutationObserver, with main-frame errors separated from empty results and YouTube subresource request diagnostics shown without retaining URLs or credential values.
- [x] Expand/collapse the embedded page preview so users can interact with a site and sign in before downloading resources.
- [x] Java resource classifier for media, documents, archives, apps/installers, fonts, code/data and generic files, including MIME-based extensionless URLs and sanitized HTML download-name hints.
- [x] Page scanning for media DOM elements, recognized file URLs and explicit `<a download>` links, including dynamically added links; standalone HLS/DASH segments are hidden from the normal results list.
- [x] Result filters appear only for categories actually discovered on the current page.
- [x] Multi-select resources and batch enqueue.
- [x] OkHttp shared client, OkDownload adapter and SAF `content://` output.
- [x] Install the WebView cookie interceptor in the shared OkHttp network client used by OkDownload and Media3; CookieManager scopes cookies to each exact request URL and redirect exchange, and cookie values are never stored in Room or logged.
- [x] Preserve the same SAF destination URI for interrupted direct transfers so OkDownload can resume saved byte ranges on retry.
- [x] Media3 `DownloadHelper` prepares adaptive streams and derives selected stream keys, preferring the highest supported bitrate by default.
- [x] Read actual HLS/DASH video heights from Media3 track mapping, offer those options in the resource chooser, and carry the selected height through Worker, Room and retry.
- [x] Media3 `Transformer` exports selected HLS/DASH streams to an MP4 file, then copies it into the chosen SAF folder.
- [x] Hilt application and Hilt-aware WorkManager worker.
- [x] MVVM + StateFlow for sniffing state and download-center preferences/history.
- [x] Room database for captured resources and persistent download records.
- [x] DataStore for the selected output folder.
- [x] WorkManager foreground download jobs with notifications, progress updates, cancellation and retry.
- [x] Pause direct HTTP/HTTPS tasks and continue from the saved OkDownload breakpoint when the server supports byte ranges; persist and distinguish paused tasks from cancelled tasks after app restart.
- [x] Download-center filters for task status and discovered resource kind.
- [x] Live direct-transfer byte counts, total size and speed; remaining time is shown only when the server provides a usable total length and a speed estimate exists.
- [x] Persist direct-transfer byte count, confirmed total size and speed in Room so transfer metrics survive app restarts.
- [x] MIME-aware SAF file creation and output-name inference for common document, archive, application, font, image, audio and video formats.
- [x] Restore task list from WorkManager and Room after reopening the app.
- [x] Browse the selected SAF download directory in-app, including subfolders, filename search, MIME filters, file sizes, modification dates and opening files with Android.
- [x] Persist the original URL, Referer and SAF destination tree in Room so failed/cancelled jobs can be retried without relying on WorkInfo input data.
- [x] Restore task filenames from durable WorkManager tags instead of trying to read unavailable inputData from WorkInfo.
- [x] Verify direct-download payload signatures through a C++17/JNI probe before reporting completion; HTML error pages are reported as failures.
- [x] Validate HLS/DASH fixtures, Java URL/MIME classification, YouTube diagnostic redaction and native ISO BMFF/file signatures with the Python CI tool.

## YouTube extraction research

See [the YouTube extraction research report](docs/YOUTUBE_EXTRACTION_RESEARCH.md) for the current yt-dlp/EJS/QuickJS/PO Token runtime comparison, licensing findings and staged integration plan. The current JSON parser is groundwork only: this change does not bundle or execute yt-dlp yet.

## Known limitations

- This is a generic webpage sniffer, not a universal extractor for every site. Results are best-effort and depend on URLs exposed to WebView request callbacks, the DOM or the Performance API.
- Blob-only URLs, service-worker-private responses, app-private media and sites that conceal media URLs may not be extractable as ordinary files.
- Generic downloadable assets are best-effort: known file extensions and explicit HTML `download` links are captured, but WebView's request interception callback does not expose response headers such as `Content-Disposition`; opaque extensionless endpoints without a DOM filename or MIME hint may remain unidentified.
- The local file browser currently reads the selected SAF download directory only; it is not a full-device filesystem explorer and does not bypass Android storage permissions.
- Media3 handles clear HLS/DASH streams that its supported extractors and muxer can decode. DRM/protected streams are not decrypted or bypassed. Some live, unusual, encrypted or malformed manifests may fail with a descriptive export error.
- Adaptive exports use a temporary file in the app cache before copying to the SAF destination. Large exports therefore need sufficient temporary storage.
- YouTube URL recognition validates video IDs from watch, youtu.be, shorts, live and embed URLs. A pure parser can now consume a future yt-dlp `-J` result and expose only actual non-DRM format metadata; it deliberately drops signed media URLs and request headers. This parser is not yet connected to a yt-dlp runtime or the download UI. YouTube diagnostics still classify visible WebView request callbacks and Performance API entries; they do not decrypt signed media URLs. The app still does not resolve signature / n transforms, execute EJS as part of a downloader, or acquire PO Tokens, so recognizing a video page does not yet mean its stream formats are downloadable. WebView callback coverage is incomplete by platform design; a zero count is not proof that YouTube sent no such request.
- Only cookies already present in WebView CookieManager for the exact target URL are reused by OkHttp, OkDownload and Media3. Cookies scoped to another host, rejected third-party cookies, app-only tokens and non-cookie authorization schemes are not synthesized; authenticated resources may still return HTTP 401/403.
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
