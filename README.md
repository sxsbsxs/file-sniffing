# File Sniffing

An Android-first webpage resource sniffer and general file download manager.

**Current app version:** 0.9.9

## Technology architecture

| Layer | Technology | Responsibility |
|---|---|---|
| UI | Kotlin, Jetpack Compose, Material 3 | Native home, resource list, download center |
| Sniffing | WebView, shouldInterceptRequest, injected JavaScript, Niconico watch API, yt-dlp Android runtime | Observe generic page media, resolve Niconico DMS HLS manifests and inspect YouTube videos through yt-dlp |
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
- [x] Niconico video extraction for `nicovideo.jp/watch` and `nico.ms` links: request the current `v3` or `v3_guest` watch API, explicitly forward the WebView session cookie to both API calls, request the DMS HLS manifest with its short-lived access key, and expose the actual manifest to the existing Media3 quality/download pipeline. Regional, member-only, PPV and Premium restrictions are reported, not bypassed.
- [x] Refresh Niconico's short-lived HLS manifest inside the WorkManager worker on every start/retry; keep the stable watch URL in download history instead of persisting the expiring manifest URL.
- [x] Expand/collapse the embedded page preview so users can interact with a site and sign in before downloading resources.
- [x] Java resource classifier for media, documents, archives, apps/installers, fonts, code/data and generic files, including MIME-based extensionless URLs and sanitized HTML download-name hints.
- [x] Page scanning for media DOM elements, recognized file URLs and explicit `<a download>` links, including dynamically added links; standalone HLS/DASH segments are hidden from the normal results list.
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

## YouTube extraction research

See [the YouTube extraction research report](docs/YOUTUBE_EXTRACTION_RESEARCH.md) for the current yt-dlp/EJS/QuickJS/PO Token runtime comparison, licensing findings and staged integration plan. The current JSON parser is groundwork only: this change does not bundle or execute yt-dlp yet.

## Known limitations

- This is a generic webpage sniffer with a dedicated Niconico DMS adapter, not a universal extractor for every site. Generic-site results remain best-effort and depend on URLs exposed to WebView request callbacks, the DOM or the Performance API.
- Blob-only URLs, service-worker-private responses, app-private media and sites that conceal media URLs may not be extractable as ordinary files.
- Generic downloadable assets are best-effort: known file extensions and explicit HTML `download` links are captured, but WebView's request interception callback does not expose response headers such as `Content-Disposition`; opaque extensionless endpoints without a DOM filename or MIME hint may remain unidentified.
- The local file browser reads the public Download collection by default on Android 10+ or the user-selected SAF directory; it is not a full-device filesystem explorer and does not bypass Android storage permissions.
- Media3 handles clear HLS/DASH streams that its supported extractors and muxer can decode. DRM/protected streams are not decrypted or bypassed. Some live, unusual, encrypted or malformed manifests may fail with a descriptive export error.
- Adaptive exports use a temporary file in the app cache before publishing to Download or the selected SAF destination. Large exports therefore need sufficient temporary storage.
- Android 10+ downloads use the public MediaStore Download collection; Android 9 and earlier require the user to grant a SAF folder. Transfer speed depends on network quality and server support for ranged requests; a higher connection limit cannot force a server to provide parallel ranges.
- Niconico extraction uses the service's current watch API and HLS access-right endpoint. Guest access is used only when the WebView has no `user_session`; otherwise the current WebView session is reused. Premium-only, pay-per-view, channel-member and region-restricted responses remain unavailable unless the signed-in account already has the required access. Short-lived HLS manifest URLs are refreshed by the worker before download. The app does not create account credentials, bypass access restrictions, or add a custom DRM/key-decryption implementation.
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
