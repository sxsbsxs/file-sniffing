# File Sniffing

An Android-first media resource discovery and local download manager.

## Product direction

Paste a web page URL, let the in-app browser inspect the page, review the media resource URLs it exposes, and save directly downloadable files into a folder selected through Android's Storage Access Framework.

The visual direction follows the clarity and polish of leading consumer apps (Douyin, Kuaishou, YouTube and Bilibili) without copying their branding or screen layouts.

## Implemented

- [x] Android app foundation with Kotlin, Jetpack Compose and Gradle Wrapper.
- [x] Minimal URL-entry home screen with paste, clear and URL validation.
- [x] Embedded WebView for opening the supplied page.
- [x] Request URL capture through WebViewClient resource callbacks.
- [x] DOM and Performance API scanning for video, audio, image and source resources.
- [x] Resource classification for common media extensions and MIME hints.
- [x] Separate labels for HLS/DASH playlists and individual media segments.
- [x] Resource filters for all, video, audio and image categories.
- [x] Native folder selection via Android Storage Access Framework.
- [x] Persisted destination-folder permission.
- [x] Direct HTTP/HTTPS file transfer into the chosen folder.
- [x] Content-Disposition filename and server MIME-type handling.
- [x] Byte-based progress when Content-Length is available; indeterminate progress for chunked/unknown-length transfers.
- [x] Download task list with success/failure state.
- [x] Adaptive launcher icon and GitHub Actions debug-build workflow.

## Current limitations

- This is a generic webpage resource sniffer, not a universal extractor for every video platform.
- A URL must be exposed to the WebView request layer, DOM, or Performance API. Some DRM, encrypted, blob-only, app-private, or otherwise protected media cannot be exported as ordinary files.
- The current downloader saves a directly accessible resource. An M3U8/MPD item is saved as a manifest; it is **not** yet merged into a complete MP4. A TS/M4S item is one segment, not the whole video.
- Downloads do not yet reuse WebView login cookies. A resource that requires an authenticated session may return HTTP 401/403.
- Download tasks are currently in-memory. Pause/resume, retry, background persistence, notifications, and process-death recovery are still to be implemented.
- Resource scanning is best-effort. Cross-origin restrictions, service workers, encrypted media, dynamic player internals, and sites that conceal media URLs can limit results.

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
