# Changelog

## 0.9.12

- Add an optional Niconico account dialog without restoring WebView or in-app webpage navigation.
- Store the Niconico account/password encrypted with a non-exportable Android Keystore AES-GCM key.
- Use a temporary owner-only netrc file for Niconico yt-dlp requests; remove it after each metadata or download operation.
- Route the same local credentials through both URL inspection and WorkManager downloads; credentials are only supplied for validated Niconico watch URLs.
- Show clearer guidance when Niconico requires registration or the video requires Premium, channel-membership or PPV entitlement.
- Add unit coverage for safe netrc quoting and line-break rejection.
- Bump app version to 0.9.12 (versionCode 30).
- The account does not bypass video-level access rights, membership, payment, region restrictions or DRM.

# Changelog

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
