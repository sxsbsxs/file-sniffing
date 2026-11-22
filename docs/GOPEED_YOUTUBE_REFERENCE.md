# Gopeed YouTube Reference Analysis

**Checked:** 2026-11-21  
**Project branch reviewed:** `ui/consistency-redesign`  
**Reference:** `https://github.com/monkeyWie/gopeed-extension-youtube`  
**Input supplied by the project owner:** a 1.35 MB HTML file named `HJQh7iEnepY`, associated with `https://youtu.be/HJQh7iEnepY?si=3OOslfLAP4nipRwn`.

## 1. What the supplied file actually contains

The uploaded file is a YouTube watch-page HTML document, not a video, media manifest, Gopeed task JSON or extracted stream list.

The embedded `ytInitialPlayerResponse` contains:

- `playabilityStatus.status = LOGIN_REQUIRED`
- `playabilityStatus.reason = "Đăng nhập để xác nhận bạn không phải là robot"` (Vietnamese: sign in to confirm you are not a robot)
- The response context reports `logged_in = 0`
- No `streamingData` object
- No player `videoDetails` object

Consequently, this response exposes no `formats`, no `adaptiveFormats`, and no SABR streaming URL. The word "formats" appears elsewhere in the page's JavaScript/configuration, but it is not evidence of downloadable formats.

**Interpretation:** this particular file is evidence of the blocked HTML-page path. It does not prove Gopeed successfully extracted this video's media. A generic HTTP download of the shortened URL or its redirected watch page can save this HTML as a file while returning zero media bytes.

## 2. What the current Gopeed YouTube extension does differently

The current `monkeyWie/gopeed-extension-youtube` repository identifies extension version `2.0.1` and requires Gopeed `>= 2.0.0`. Its README explicitly describes a PO Token verification phase before media transfer, automatic audio/video merging, playlists and quality selection.

Inspected current source paths:

- `src/lib/video.js`: resolves a video title through YouTube.js `getBasicInfo()`.
- `src/lib/sabr/common.js`: creates a YouTube.js InnerTube client, uses the selected browser user-agent and exposes a PO Token expression.
- `src/lib/sabr/streams.js`: obtains a video-specific PO Token, calls `getInfo(videoId, { po_token })`, requires `server_abr_streaming_url` plus `adaptive_formats`, and prepares SABR streams.
- `src/lib/sabr/webview.js`: obtains the YouTube BotGuard challenge context and mints a Web PO Token inside a browser/WebView execution context.
- `src/index.js`: runs the verification expression in Gopeed's headless WebView, synchronizes optional YouTube cookies, then connects the resulting audio/video streams to Gopeed's FFmpeg merge runtime.

The resulting architecture is materially different from saving or scraping the watch-page HTML:

```text
YouTube URL
  -> YouTube.js / InnerTube metadata
  -> video-specific PO Token generation in a real WebView context
  -> InnerTube getInfo(..., po_token)
  -> SABR / UMP streaming endpoint and adaptive format metadata
  -> select actual video + audio tracks
  -> consume SABR streams
  -> mux audio/video to MP4
```

The README warns that transfer speed can remain at zero while PO Token verification is running. That is a useful diagnostic distinction: zero speed at this stage is not necessarily a broken downloader, but the UI must show the verification state rather than a false active-transfer percentage.

## 3. What this means for our Android project

The current project can recognize YouTube URLs, inspect WebView network requests and parse a hypothetical yt-dlp `-J` JSON response. It does not yet perform the Gopeed-style InnerTube + PO Token + SABR extraction flow.

The supplied HTML is consistent with that limitation: observing a page request does not grant the player response's media formats. A normal page request and the InnerTube metadata request with a valid client/session context and video-specific PO Token are different operations.

Recommended implementation direction:

1. Keep the normal WebView resource sniffer for ordinary websites.
2. Route recognized YouTube video links to a dedicated extractor, not the generic page-resource list.
3. Use a maintained InnerTube client to obtain real video metadata and current formats.
4. Generate a PO Token in a supported browser execution context; do not hardcode or persist tokens.
5. Use a maintained SABR/UMP implementation where required, and select the actual available audio/video tracks returned for that video.
6. Merge the downloaded tracks. First assess AndroidX Media3 Transformer/Extractor support and its real-device quality; do not silently add an FFmpeg build with incompatible licensing.
7. Treat `LOGIN_REQUIRED`, missing player data, missing SABR configuration, HTTP 403, and expired tokens as distinct actionable states. Reacquire transient metadata/tokens when appropriate.

This should be developed as an independent Android implementation. Gopeed's extension source repository currently has no GitHub-detected repository license file/license metadata, so its implementation should be treated as reference material, not copied into this project. The dependency projects below separately declare MIT licensing.

## 4. Dependency and license check

As checked on 2026-11-21:

| Component | Role in the Gopeed architecture | Declared license |
|---|---|---|
| [YouTube.js](https://github.com/LuanRT/YouTube.js) | InnerTube requests, player metadata and format discovery | MIT |
| [bgutils-js](https://github.com/LuanRT/BgUtils) | BotGuard challenge handling and PO Token generation | MIT |
| [googlevideo](https://github.com/LuanRT/googlevideo) | YouTube UMP/SABR protocol support | MIT |

These libraries are candidates for a clean-room integration, not proof of Android compatibility. Their WebView/runtime assumptions, transitive dependency sizes, supported browser APIs, streaming backpressure, memory behavior and per-ABI app footprint still need to be tested in our app. The Gopeed extension itself also relies on Gopeed-specific WebView, Blob and FFmpeg APIs which cannot be called directly from this Android project.

## 5. Current implementation status after this reference review

- **Added:** a privacy-safe parser for YouTube player-response diagnostics.
- **Added:** WebView diagnostic capture of playback status, a short reason, format counts and SABR availability. It deliberately does not transfer media URLs, headers or tokens into the app.
- **Not added:** actual InnerTube extraction, PO Token generation, SABR transfer, audio/video muxing or a claim of working YouTube download.
- **Not tested:** this URL's successful extraction through Gopeed itself. The supplied file is a blocked HTML response, not Gopeed's successful media metadata result.

Official/reference links:
- https://github.com/monkeyWie/gopeed-extension-youtube
- https://github.com/LuanRT/YouTube.js
- https://github.com/LuanRT/BgUtils
- https://github.com/LuanRT/googlevideo
