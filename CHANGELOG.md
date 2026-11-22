# Changelog

## 0.9.8

- Increased the configurable direct HTTP byte-range connection ceiling from 16 to 512, with presets for 1/2/4/8/16/32/64/128/256/512 and a 64-connection default for very large files.
- Scaled block counts by file size: small files keep a small number of ranges, while large files target roughly one range per 4 MiB and can use the configured 256/512 ceiling when the server supports byte ranges.
- Kept HLS/DASH segment concurrency separate and capped its worker pool at 32 so the direct-file range setting cannot create hundreds of Media3 worker threads.
- Added regression tests for 128/256/512 connection selection and the supported setting ceiling.
- Increased app version to 0.9.8 (versionCode 26).

## 0.9.7

- Made the public `Download/` folder the default destination on Android 10+, using MediaStore pending-file publication; a user-selected SAF folder remains available as an override. Android 9 and earlier continue to require a SAF folder.
- Removed the mandatory “choose a folder before downloading” gate and updated direct downloads, adaptive HLS/DASH exports, retries and the in-app file browser to use the default Download destination.
- Added user controls for per-task connection ceiling (1–16), concurrent download tasks (1–4) and a shared download speed cap (unlimited, 1/2/5/10/20 MB/s); adaptive segment concurrency and shared OkHttp response throttling use the same settings.
- Added connection-policy tests for the configurable ceiling.
- Increased app version to 0.9.7 (versionCode 25).

## 0.9.6

- Added a dedicated Niconico extractor for `nicovideo.jp/watch` and `nico.ms` video links, using the current `v3` / `v3_guest` watch API and the DMS HLS access-right endpoint.
- Reuses only the user's existing WebView `user_session` when present; guest requests stay guest requests. Premium, PPV, member-only and regional restrictions are reported instead of bypassed.
- Sends the returned HLS master manifest through the existing Media3 track inspection and download/export path, so the quality picker is based on tracks exposed by the manifest.
- Refreshes short-lived Niconico manifest URLs in the WorkManager worker before every download/retry and stores the stable watch-page URL in history instead of the expiring manifest URL.
- Added unit tests for Niconico URL validation, DMS track parsing, output combinations, HLS URL validation and restricted-access responses.
- Increased app version to 0.9.6 (versionCode 24).

## 0.9.5

- Added privacy-safe WebView page diagnostics for human-verification screens, region restrictions and access-denied responses. The native app receives only boolean signals, never raw page text or credential values.
- Added a targeted notice recognizer for nicovideodl.jp so its announced 720p limit and service-ending notice are not mistaken for a valid media stream.
- Added unit tests for verification, region restriction, access-denied and nicovideodl page-state classification. The app reports the page state; it does not bypass verification or access restrictions.
- Increased app version to 0.9.5 (versionCode 23).

## YouTube extraction research groundwork (post-0.9.4, not a released runtime)

- Added a pure parser for actual yt-dlp `-J` JSON metadata. It exposes only formats reported by the extractor, separates muxed/video-only/audio-only tracks, filters DRM-marked formats, and can compose an explicit video+audio format selector.
- The parser never retains signed media URLs or request headers, preventing short-lived URLs and authorization headers from leaking into UI state, Room history or logs.
- Added JVM tests for actual format selection, video/audio pairing, DRM filtering, malformed/playlist payloads and URL/header redaction.
- Added a research report comparing yt-dlp + EJS + JS runtimes + PO Token providers, Android packaging approaches, ABI/runtime constraints and third-party license risks. This is parser groundwork only: it does not bundle or execute yt-dlp yet.

## 0.9.4

- Added persistent per-request Media3 segment caching for HLS/DASH exports. Selected segments are downloaded into a private app cache before remuxing; a paused or interrupted job can reuse already-completed segments when retried.
- Changed Media3 export to read from the same cache used by the segment downloader, avoiding a second full network transfer during remux; successful exports clear their temporary adaptive cache.
- Enabled pause controls for HLS/DASH tasks and discard their partial segment cache only on an explicit cancellation; ordinary pause preserves it for continuation.
- Increased app version to 0.9.4 (versionCode 22).

## 0.9.3

- Fixed a wiring bug: the existing URL-scoped WebView cookie interceptor is now installed on the shared OkHttp client used by OkDownload and Media3, including redirect exchanges.
- Added strict YouTube video URL parsing for watch, short-link, Shorts, live and embed links, with spoofed-host and malformed-ID rejection tests.
- The YouTube empty state now says when a video page is recognized but actual signed media formats have not been extracted; it no longer implies that a page URL is a downloadable video.
- Increased app version to 0.9.3 (versionCode 21).


## 0.9.2

- Added request-scoped WebView cookie reuse in OkHttp's network interceptor so authenticated resource URLs can use the existing WebView session where CookieManager permits it; cookies are not stored or logged.
- Added an expand/collapse control for the embedded browser preview to make in-page sign-in and resource inspection usable.
- Added unit tests for exact-URL cookie lookup and preservation of explicit Cookie headers.
- Increased app version to 0.9.2 (versionCode 20).

## 0.9.1

- Added a real HLS/DASH resolution chooser sourced from the current manifest's Media3 video track mapping; the selected height is carried into background export and persisted for retry/history.
- Added pause/continue for direct HTTP/HTTPS downloads using the existing OkDownload breakpoint and SAF document; paused state is distinguishable from cancellation after app restart.
- Persisted transferred bytes, confirmed total size and speed in Room so download history retains real transfer metrics.
- Completed the file-browser and tool-first UI work from 0.9.0, and clarified remaining Media3 and site-authentication limitations.
- Increased app version to 0.9.1 (versionCode 19).

## 0.9.0

- Added a local file browser for the selected SAF download directory with folder navigation, search, MIME-category filters, size/date metadata and Android file opening.
- Kept bottom navigation available on the sniffing result screen and added a dedicated Files tab.
- Replaced the promotional-style home header with a compact URL-first workflow and reduced preview height to give discovered resources more room.
- Increased app version to 0.9.0 (versionCode 18).

## 0.8.6

- Expanded Java resource classification to documents, archives, application packages/installers, fonts, code/data files and generic binary downloads while preserving existing media and playlist types.
- Captured explicit HTML download links and recognized file links, respected sanitized download-attribute filenames and hid standalone HLS/DASH media segments from the normal resource list.
- Added dynamic resource-category filters to the inspection results and status/type filters to the download center.
- Passed resource kinds through WorkManager task state and displayed live transferred bytes, server-reported total size, transfer speed and ETA when those values are available.
- Added extension-aware MIME selection for SAF output documents and avoided inventing a `.bin` suffix for unknown files.
- Increased app version to 0.8.6 (versionCode 14).

## 0.8.3

- Upgraded the Kotlin Android and Compose Gradle plugins to stable 2.4.21; kept KSP 2.3.12, which supports Kotlin 2.4.x.
- Moved URL, extension, MIME and output-name resource classification into a platform-independent Java component called by the Kotlin capture pipeline.
- Extended the native C++17/JNI probe to recognize fragmented MP4 `styp` and `moof` boxes separately from complete MP4 files.
- Added Python-driven JDK/C++ regression cases for extensionless resources, HLS/DASH MIME types, path traversal, plus signs and fragmented MP4 signatures.
- Increased app version to 0.8.3.


## 0.8.2

- Added redacted YouTube WebView diagnostics for Player API, Google Video, Performance API entries, subresource HTTP errors and network errors.
- Added Java request classification that inspects URL host/path categories and sensitive header names only; it does not retain URL parameters or credential values.
- Added offline Java regression tests for YouTube request classification and credential redaction.
- Kept YouTube media requests out of the generic download list until a dedicated extractor can resolve real formats and authenticated media access.


## 0.8.1

- Fixed download task filename restoration by storing the encoded filename in a durable WorkManager tag rather than reading a nonexistent `WorkInfo.inputData` property.
- Added HTTP and HTTPS `ACTION_VIEW` filters so users can open a webpage link in File Sniffing from other apps.
- Kept external URL handling separate from page cookies; this does not grant authenticated download access.
- Added an explicit result-page error state for main-frame WebView network and HTTP failures.
- Added Python CI checks for HTTP/HTTPS VIEW routing while retaining text/plain share intents.

## 0.8.0

- Reused the original SAF document URI for direct-download retries so OkDownload can continue saved breakpoints.
- Preserved partial document URIs in WorkManager progress and Room history.
- Added legacy SharedPreferences-to-DataStore migration for the selected output folder.
- Added explicit HLS/DASH MIME hints for extensionless adaptive manifest URLs.
- Updated the task action label to distinguish continuing a breakpoint download from starting a fresh retry.

## 0.7.0

- Aligned the toolchain to Kotlin 2.3.20, KSP 2.3.12, AGP 8.13.2 and Gradle 8.14.8.
- Updated AndroidX Hilt Work integration/compiler to 1.4.0 for KSP2 processing.
- Updated Compose BOM to 2026.09.00 and Lifecycle to 2.11.0.
- Switched CI to install Android SDK 36 and build with the checked-in Gradle Wrapper.
- Added the WorkManager data-sync foreground service type to the merged manifest.
- Improved OkDownload progress for multi-block and resumed transfers.
- Made Media3-to-SAF export stop promptly on worker cancellation.
- Preserved partial SAF documents and OkDownload breakpoint offsets across cancelled/failed direct-download retries.

## 0.6.0

- Refactored the download architecture around Hilt, MVVM and Kotlin Flow.
- Added Room entities, DAOs and repositories for sniffed resources and download history.
- Added DataStore-backed selected output folder preferences.
- Added shared OkHttp configuration and an OkDownload SAF transfer adapter.
- Migrated HLS/DASH adaptive exports to Media3 DownloadHelper + Transformer.
- Added stream-key selection, highest-supported-bitrate preference and MP4 export to the selected SAF folder.
- Kept WorkManager foreground jobs for persistent background execution and Room task history.
- Removed the previous handwritten HLS/DASH segment-merging implementations.

## 0.5.0

- Added Hilt application/worker injection.
- Added Room and DataStore foundations.
- Added OkHttp and OkDownload dependencies and the direct-file transfer adapter.
- Added Flow-based sniffing and download ViewModels.

## 0.4.0

- Added persistent WorkManager-based download jobs with foreground notifications.
- Added cancel and retry actions for download tasks.
- Added handwritten HLS and static DASH segment processing (superseded by Media3 in 0.6.0).
- Added Android 13+ notification permission request for download progress.

## 0.3.0

- Added persistent-download worker foundation and background notification plumbing.

## 0.2.0

- Added in-app webpage inspection and media resource classification.
- Added multi-select and batch download UI.
- Added direct file downloads to the selected SAF folder.
