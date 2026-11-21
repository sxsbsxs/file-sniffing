# File Sniffing

An Android-first webpage media resource discovery and download manager.

## Product direction

Paste a page URL, let the in-app browser inspect the page, review the media resource URLs it exposes, and save directly downloadable files into a folder selected through Android's Storage Access Framework.

The visual direction follows the clarity and polish of leading consumer apps (Douyin, Kuaishou, YouTube and Bilibili) without copying their branding or screen layouts.

## Implemented

- [x] Android app foundation with Kotlin, Jetpack Compose and Gradle Wrapper.
- [x] Minimal URL-entry home screen with paste, clear, URL validation and Android share-sheet link receiving.
- [x] Embedded WebView with Safe Browsing and page request inspection.
- [x] DOM, dynamic attribute and Performance API scanning for exposed video, audio, image and source resources.
- [x] Resource classification using common file extensions and MIME hints.
- [x] Separate labels for HLS/DASH playlists and individual media segments.
- [x] Resource filters for all, video, audio and image categories.
- [x] Multi-select resources and enqueue batch downloads.
- [x] Native folder selection via Android Storage Access Framework and persisted folder permission.
- [x] Direct HTTP/HTTPS file transfer with response MIME and Content-Disposition filename handling.
- [x] Byte progress for known-length files and indeterminate progress for chunked/unknown-length transfers.
- [x] Persistent WorkManager download jobs with foreground data-transfer notifications.
- [x] Task status restoration after reopening the app, cancellation, retry for failed/cancelled work, and open-file action.
- [x] Clear HLS VOD/live-snapshot playlist download: master-playlist bandwidth selection, relative segment resolution, TS concatenation, and fMP4 init-segment plus fragment concatenation.
- [x] Static DASH MPD download for common SegmentList and SegmentTemplate + SegmentTimeline layouts, selecting the highest-bandwidth video representation.
- [x] Adaptive launcher icon and GitHub Actions debug-build workflow.

## Current limitations

- This is a generic webpage resource sniffer, not a universal extractor for every video platform. Results are best-effort and limited to resources exposed to the WebView request layer, DOM, or Performance API.
- HLS support currently covers clear playlists without byte-range segments. AES-128 encrypted playlists, SAMPLE-AES, DRM, encrypted keys, alternate audio rendition selection, and BYTERANGE are not handled.
- HLS MPEG-TS segments are saved as a .ts file. Fragmented MP4 playlists are assembled from the initialization segment and media fragments into an .mp4 container; unusual discontinuities, codec switches or malformed manifests may still require a dedicated remuxer.
- A live HLS playlist is downloaded as the currently available segment snapshot; the worker does not follow the live stream indefinitely.
- Dynamic DASH live MPDs, SegmentBase, negative SegmentTimeline repeats, byte-range segments, and MPDs without SegmentTimeline for SegmentTemplate are not yet supported.
- Downloads do not yet reuse WebView login cookies. A resource that requires an authenticated browser session may return HTTP 401/403.
- WorkManager jobs survive ordinary app process recreation and expose task notifications. Pause/resume and byte-level HTTP range continuation are not implemented yet.
- Cross-origin restrictions, service workers, blob-only URLs, encrypted media, app-private resources and sites that conceal media URLs can limit results.

## Privacy and scope

- No account or backend server is required for the basic workflow.
- Page inspection and direct downloads happen on the device.
- Credentials are not extracted or uploaded.
- DRM circumvention, authentication bypass, and protected-stream decryption are out of scope.
- Cleartext HTTP is permitted for user-entered pages for compatibility; HTTPS should be preferred. Mixed content inside HTTPS pages remains blocked by WebView.

## Build

Open the project in Android Studio with JDK 17 and Android SDK 35. Run the app configuration, or use:

```bash
./gradlew :app:assembleDebug
```

The GitHub Actions workflow builds the debug APK on pushes and pull requests to `main` and uploads it as the `file-sniffing-debug` artifact.
