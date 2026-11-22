# Changelog

## 0.9.14

- Fix a Room persistence race where the global WorkManager observer could overwrite a newly queued task's source URL with an empty value.
- Make the Worker the single writer of durable download state; UI observers now only update the live task list.
- Recover missing legacy retry parameters from the original WorkManager inputData, including the URL, referer, MIME type, destination and selected resource kind.
- Add an outer Worker exception boundary so failures during task setup are reported with a sanitized cause instead of the generic "下载失败" fallback; best-effort persist the original URL and failure.
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
