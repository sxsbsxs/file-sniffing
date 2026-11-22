# Changelog

## 0.9.18

- Make yt-dlp parsing and downloads use a cancellable process executor; replacing a URL, pausing or cancelling now terminates the associated process, and the worker waits for the process thread to exit before returning.
- Use a stable private SHA-256 session directory for each source/format/output selection so yt-dlp `.part` files survive WorkManager pause/retry attempts without storing signed media URLs in the path.
- Reuse a completed private yt-dlp output file when interruption happens during the final copy to Download/SAF; show 90–99% copy progress and remove temporary data only after publication succeeds.
- Validate yt-dlp format selector syntax before invoking the extractor; accept only safe single IDs, one real video+audio pair and the app's fixed fallback selectors.
- Validate direct HTTP and yt-dlp output before publishing: reject empty files, known-incomplete byte counts, HTML/JSON/XML error pages and plain-text payloads masquerading as media.
- Extend the native C++ probe to identify JSON error bodies even when they arrive as application/octet-stream; trust a recognized MP4/WebM/audio signature over an incorrect text/plain MIME header.
- Delete clearly invalid HTTP error payload files instead of offering them as resumable media.
- Verify MediaStore pending-to-visible publication; report failure and clean up the output if the file cannot be published. Apply the same cleanup rule to Media3 exports.
- Persist the latest transfer progress and byte counters in Room when a task pauses, rather than retaining only the last database snapshot.
- Persist retry attempts before WorkManager enqueue and replace the old paused/failed row atomically; do not leave an old paused row blocking all future downloads.
- Persist Worker input-validation failures instead of returning a failed WorkManager result while leaving the Room row queued. Observe the asynchronous WorkManager enqueue result; enqueue failures become failed task rows, and successful requests attach their LiveData observer on the main thread.
- Confirm pause/cancel state from WorkManager's terminal state; reconcile Room history from WorkManager on app restart to repair state transitions interrupted by process death.
- Use one canonical Niconico watch URL for task identity and deduplication even when CDN manifest URLs are refreshed.
- Guard against starting two retries of the same URL and format at the same time.
- Test stable yt-dlp session IDs, safe format selectors, HTTP payload integrity and Niconico queue identity.
- Bump app version to 0.9.18 (versionCode 36).

## 0.9.17

- Add a 26-project open-source benchmark and repository-specific product improvement plan in `docs/OPEN_SOURCE_BENCHMARK.md`.
- Replace the resolver's per-format card stack with one media preview card per source URL, showing a thumbnail, uploader, duration, available video qualities and audio track count.
- Add a download configuration bottom sheet with separate Video and Audio-only modes, actual yt-dlp format IDs, audio selection for video-only streams, and a preview of the output name and estimated size before enqueue.
- Preserve a muxed format's embedded audio; do not append an additional audio-only stream to formats that already contain audio.
- Distinguish direct MP4/audio file links from extractor format choices; direct files go straight to download without a fake quality picker.
- Add search by filename/error text and sorting by recent, filename and progress in Download Center.
- Add generic yt-dlp playlist detection for common playlist URL shapes and parse entries from real HTTP(S) page URLs across extractors; do not fabricate non-YouTube watch URLs.
- Route generic video/audio item pages from playlists through yt-dlp even when their referer is the list page; recognized direct media file extensions remain on the HTTP engine.
- Extract video/audio format composition into a pure Kotlin helper with tests for muxed media, separate-track pairing, foreign-source rejection and unsafe format IDs.
- Include actual audio language labels in the format selector and default Audio-only mode to a real available track.
- Load media thumbnails with Coil while keeping URLs and signed stream credentials out of Room task records.
- Keep existing WorkManager, Room, HTTP, HLS/DASH and yt-dlp download engines; this change improves their product flow instead of replacing them.
- Bump app version to 0.9.17 (versionCode 35).

## 0.9.16

- Show each real yt-dlp video format as an explicit download choice instead of summarizing available formats but exposing only one generic download item.
- Show audio-only tracks as separate choices and pair video-only formats with the best real audio-only format from the same video.
- Pass the selected yt-dlp format selector into the download worker; use the selected audio format rather than silently overriding it with bestaudio.
- Persist selected format selectors in Room (database migration 3 to 4) and WorkManager retry input so retries retain the chosen quality.
- Include the format selector in task identity so duplicate taps for the same quality are blocked while different qualities of one video can be queued independently.
- Bump app version to 0.9.16 (versionCode 34).

## 0.9.15

- Replace the single universal debug APK with three ABI-specific APK artifacts: arm64-v8a, armeabi-v7a and x86_64.
- Keep all three architectures supported while preventing each device from downloading the other two architectures' embedded Python and FFmpeg runtimes.
- Add a CI size guard: the arm64-v8a debug APK must remain below 65 MiB.
- Bump app version to 0.9.15 (versionCode 33).

## 0.9.14

- Fix a Room persistence race where the global WorkManager observer could overwrite a newly queued task's source URL with an empty value.
- Make the Worker the single writer of durable download state; UI observers now only update the live task list.
- Persist the URL, referer, MIME type, resource kind and destination in Room before enqueuing WorkManager, so the retry source exists even if worker creation or preflight fails.
- Add an outer Worker exception boundary so failures during task setup are reported with a sanitized cause instead of the generic "下载失败" fallback; best-effort persist the original URL and failure. Legacy rows whose URL was already erased require one re-parse because WorkInfo does not expose inputData to the UI.
- Prevent repeated taps from enqueueing duplicate active downloads for the same source URL.
- Add unit tests for same-URL queue deduplication.
- Bump app version to 0.9.14 (versionCode 32).

## 0.9.13

- Remove the incorrect Niconico account/password prompt; public Niconico links are attempted without user credentials.
- Add Seal-style automatic yt-dlp executable updates from the Nightly channel, independent of the pinned youtubedl-android wrapper dependency.
- Check for extractor updates at app startup and then weekly; if the network is unavailable, keep using the bundled executable and retry after a six-hour cooldown.
- Add unit tests for first-run, weekly update and failed-update retry scheduling.
- Bump app version to 0.9.13 (versionCode 31).
- Niconico public access still follows the service's current guest API and any genuine video visibility, membership, payment, region or DRM restrictions.

## 0.9.11

- Remove the in-app webpage viewer, WebView result screen, DOM/JavaScript injection, browser request interception and page-text restriction diagnostics.
- Remove HTTP/HTTPS and Bilibili ACTION_VIEW browser intent filters; the app receives links through paste/share only and no longer registers as a browser.
- Replace the former page-sniffing flow with a direct URL resolver: direct file URLs use the resource classifier; supported site video/audio URLs use yt-dlp; YouTube playlists are expanded into individual entries.
- Route supported video-site pages, including Bilibili where the installed yt-dlp extractor supports the URL, through the same yt-dlp + FFmpeg worker; direct media/file URLs remain on OkDownload.
- Keep Download Center, WorkManager, local files, MediaStore/SAF output, download tuning and pause/retry behavior.
- Remove WebView-only extraction and diagnostic components and their obsolete tests.
- Bump app version to 0.9.11 (versionCode 29).
- No new site authentication, EJS/JavaScript challenge or PO Token integration is included; site-specific access and DRM restrictions remain in force.

## 0.9.10

- Route Niconico metadata extraction and downloads through the upstream yt-dlp Android runtime and its dedicated Niconico extractor.
- Export available local site cookies to a private temporary Netscape cookie file for yt-dlp; delete the file after each operation.
- Route Niconico and YouTube downloads through yt-dlp + FFmpeg in WorkManager.
- Keep YouTube playlist flat-JSON support and preserve sanitized root exception information in failed task history.
- Dynamic YouTube format selection, EJS/JavaScript challenge runtime and PO Token integration remain incomplete.

## 0.9.9

- Integrated Seal's upstream youtubedl-android runtime (0.17.3) and FFmpeg.
- YouTube video pages use yt-dlp metadata output and downloads run through yt-dlp + FFmpeg.
- Increased app version to 0.9.9 (versionCode 27).

## 0.9.8

- Increased direct HTTP byte-range connection ceiling to 512 and kept HLS/DASH segment worker concurrency capped at 32.
- Increased app version to 0.9.8 (versionCode 26).

## 0.9.7

- Made public Download the default destination on Android 10+ and added configurable download tuning.
- Increased app version to 0.9.7 (versionCode 25).
