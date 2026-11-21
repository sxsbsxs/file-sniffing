# File Sniffing

An Android-first webpage resource sniffer and local media download manager.

**Current app version:** 0.8.1

## Technology architecture

| Layer | Technology | Responsibility |
|---|---|---|
| UI | Kotlin, Jetpack Compose, Material 3 | Native home, resource list, download center |
| Sniffing | WebView, shouldInterceptRequest, injected JavaScript | Observe page requests, DOM media elements and dynamic resource changes |
| HTTP | OkHttp 5 + OkDownload 1.0.7 | Shared HTTP stack, multi-connection file transfer and breakpoint persistence |
| Adaptive media | Media3 ExoPlayer, DownloadHelper, Transformer | Inspect HLS/DASH tracks, select supported high-bitrate tracks and export MP4 |
| Architecture | MVVM, Kotlin Flow, Hilt | ViewModels, reactive state, dependency injection |
| Local data | Room, DataStore | Sniff history, download history and selected output folder |
| Background | WorkManager, foreground worker notification | Persistent download jobs, progress, cancel and retry |
| Filename policy | Java | Shared safe filename normalization for URL-derived and downloaded names |
| Native inspection | C++17 + JNI | Identify downloaded byte signatures and reject HTML error pages saved as media |
| Developer validation | Python 3 | Validate local HLS/DASH fixtures and regression-test the native signature detector |

OkDownload 1.0.7 is intentionally hidden behind `OkDownloadTransferEngine`: it is an older upstream library, so the rest of the app depends on the adapter contract rather than on its API directly.

## Current implementation

- [x] Compose + Material 3 app shell, minimal link-entry home, share-sheet URL receiving and external HTTP/HTTPS link opening.
- [x] WebView inspection using `shouldInterceptRequest`, DOM scanning, Performance API and a MutationObserver.
- [x] Resource types and filtering for video, audio, image, HLS and DASH manifest URLs.
- [x] Multi-select resources and batch enqueue.
- [x] OkHttp shared client, OkDownload adapter and SAF `content://` output.
- [x] Preserve the same SAF destination URI for interrupted direct transfers so OkDownload can resume saved byte ranges on retry.
- [x] Media3 `DownloadHelper` prepares adaptive streams and derives the selected stream keys, preferring the highest supported bitrate.
- [x] Media3 `Transformer` exports selected HLS/DASH streams to an MP4 file, then copies it into the chosen SAF folder.
- [x] Hilt application and Hilt-aware WorkManager worker.
- [x] MVVM + StateFlow for sniffing state and download-center preferences/history.
- [x] Room database for captured resources and persistent download records.
- [x] DataStore for the selected output folder.
- [x] WorkManager foreground download jobs with notifications, progress updates, cancellation and retry.
- [x] Restore task list from WorkManager and Room after reopening the app.
- [x] Persist the original URL, Referer and SAF destination tree in Room so failed/cancelled jobs can be retried without relying on WorkInfo input data.
- [x] Restore task filenames from durable WorkManager tags instead of trying to read unavailable inputData from WorkInfo.
- [x] Verify direct-download payload signatures through a C++17/JNI probe before reporting completion; HTML error pages are reported as failures.
- [x] Validate HLS/DASH fixtures and native signature cases with the Python CI tool.

## Known limitations

- This is a generic webpage sniffer, not a universal extractor for every site. Results are best-effort and depend on URLs exposed to WebView request callbacks, the DOM or the Performance API.
- Blob-only URLs, service-worker-private responses, app-private media and sites that conceal media URLs may not be extractable as ordinary files.
- Media3 handles clear HLS/DASH streams that its supported extractors and muxer can decode. DRM/protected streams are not decrypted or bypassed. Some live, unusual, encrypted or malformed manifests may fail with a descriptive export error.
- Adaptive exports use a temporary file in the app cache before copying to the SAF destination. Large exports therefore need sufficient temporary storage.
- WebView login cookies are not yet bridged into OkHttp/OkDownload or Media3 requests. Authenticated resources may return HTTP 401/403.
- OkDownload upstream 1.0.7 is old. It is isolated behind an adapter and should be reconsidered if a maintained alternative or a safe local fork is adopted.
- The current Media3 path selects the highest supported bitrate by default. A user-facing per-resolution chooser is a follow-up task.
- A dedicated pause button is not yet exposed. Cancelling and retrying a direct HTTP/HTTPS task reuses its SAF URI and OkDownload breakpoint store where the server supports range requests. Adaptive Media3 exports currently restart on retry.

## Privacy and scope

- No account or backend server is required for the basic workflow.
- Page inspection and downloads run on the device.
- Credentials are not extracted or uploaded. WebView cookies are not shared with the download clients.
- DRM circumvention, authentication bypass and protected-stream decryption are out of scope.
- Cleartext HTTP remains permitted for user-entered pages for compatibility; HTTPS is preferred. Mixed content inside HTTPS pages remains blocked by WebView.

## Build

Open the project in Android Studio with JDK 17 and Android SDK 36, or run:

```bash
python3 tools/validate_media_fixtures.py
./gradlew :app:assembleDebug
```

GitHub Actions builds a debug APK on pushes and pull requests to `main`, publishing the APK as the `file-sniffing-debug` artifact.
