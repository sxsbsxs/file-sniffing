# Changelog

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
