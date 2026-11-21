# YouTube Extraction Research

**Research snapshot:** 2026-11-21  
**Target:** Android app in `sxsbsxs/file-sniffing`  
**Decision:** Modify the current sniffer into a staged extractor pipeline. Do not add an opaque yt-dlp AAR or GPL Android extractor as a shortcut.

## Executive decision

The app currently recognizes YouTube URLs and records redacted request diagnostics, but it does not extract YouTube stream formats. The right backend direction is a project-owned yt-dlp runtime adapter with a real JSON metadata boundary, a pinned JavaScript challenge runtime, and an optional PO Token provider interface.

This is not a one-library integration. A functioning YouTube extraction path needs all of the following to line up:

1. A maintained yt-dlp Python runtime compatible with the Python version required by the pinned yt-dlp release.
2. The matching `yt-dlp-ejs` package and an executable JavaScript runtime.
3. A metadata command that returns the actual per-video format list (for example, `yt-dlp -J`), not a download-only wrapper.
4. A separate optional PO Token provider for clients/requests that require one.
5. A download adapter that consumes only format IDs from that same parse result, merges separate audio/video tracks, and never persists signed media URLs or authorization headers.

A recognized URL is not proof that a video can be extracted. A parsed format list is not proof that every format can be downloaded: URL expiry, region, client, account, consent and server-side policy may change the result between parse and download.

## Current project facts

- The app is an Android/Kotlin/Compose application with existing Java and C++ modules, OkHttp, WorkManager, Media3 and Room.
- The shared OkHttp client now receives URL-scoped WebView CookieManager cookies through a network interceptor. This does not automatically transfer cookies to a future standalone Python/yt-dlp process.
- `YouTubeUrlParser` validates common URL shapes and extracts 11-character video IDs. It does not call YouTube Player API, decode n/s signatures, run EJS or generate PO Tokens.
- `YtDlpInfoJsonParser` added with this report is a pure parser for one yt-dlp JSON object. It is not wired to a process/runtime or the UI yet.
- HLS/DASH export uses Media3. Selected segments are cached per task before remuxing, and the download center exposes pause/continue. CI passes, but a real-device stream matrix remains outstanding.

## Authoritative runtime requirements

### yt-dlp and Python

The latest stable yt-dlp release observed for this research snapshot is `2026.10.26`. Its package metadata declares `requires-python >=3.11`; therefore an Android port that embeds an older Python interpreter cannot simply bundle the latest yt-dlp.

The release metadata also pins `yt-dlp-ejs==0.8.0`. The yt-dlp README explains that EJS and a supported JavaScript engine are required for full YouTube support.

Sources:
- https://github.com/yt-dlp/yt-dlp/releases/tag/2026.10.26
- https://github.com/yt-dlp/yt-dlp/blob/2026.10.26/pyproject.toml
- https://github.com/yt-dlp/yt-dlp/wiki/EJS

### EJS JavaScript runtime

The `yt-dlp/ejs` README lists the supported runtimes:
- Deno >= 2.3
- Node.js >= 22
- QuickJS >= 2023-12-09
- QuickJS-NG (any version supported by the package)

For a compact Android-first build, QuickJS is a candidate, but the exact binary must be run on each supported ABI and tested against actual YouTube challenge fixtures. Deno/Node provide a second runtime option but have a larger runtime and packaging footprint. Merely adding the EJS Python package without a working JS executable is incomplete.

Sources:
- https://github.com/yt-dlp/ejs
- https://github.com/yt-dlp/ejs/releases/tag/0.8.0
- https://quickjs-ng.github.io/quickjs/supported_platforms/

### PO Token is a separate capability

The official PO Token guide describes tokens for distinct request stages/clients, including GVS, Player and subtitle requests. The applicable requirement varies by YouTube client and format path. Tokens are not a permanent account-wide cookie replacement and should not be stored as a fixed application constant.

The guide recommends using provider plugins rather than manually extracting/reusing tokens. The BgUtils provider project explicitly cautions that a PO Token does not guarantee that 403/bot checks will disappear. It has two pieces: a provider implementation and a yt-dlp provider plugin.

Security requirements for any local provider:
- Bind an HTTP provider to loopback only.
- Never expose an unauthenticated token service on LAN/public interfaces.
- Never log or persist returned tokens.
- Cache only within the provider's documented scope/expiry.
- Make it optional; do not block ordinary non-YouTube downloads when it is unavailable.

Sources:
- https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide
- https://github.com/Brainicism/bgutil-ytdlp-pot-provider
- https://github.com/LuanRT/BgUtils

## Android implementation options

| Option | Advantages | Material drawbacks | Decision |
|---|---|---|---|
| MIT `yt-dlp-android` AAR | Quick Java API; MIT; no need to embed our own Python | Published architecture is a large Python 3.13 AAR; upstream docs describe download-to-file usage rather than a reliable metadata/format API; free variant lacks curl-cffi; bundled EJS/JS runtime and PO-token path are not established | Do not use as the core extractor |
| YTDLnis runtime/package architecture | Proven Android-oriented split between Python, yt-dlp, JS runtimes and FFmpeg; JSON-driven format UI; updateable runtime packages | YTDLnis repository is GPL-3.0; its implementation cannot simply be copied into this repository without addressing project-wide licensing; helper APK/package and executable-path rules add engineering work | Architecture reference only |
| NewPipeExtractor | Native Java extraction; Android use precedent | GPL-3.0; current upstream `YoutubeStreamExtractor.setPoTokenProvider` is a no-op pending SABR work, so it does not solve the PO Token requirement | Do not adopt as the primary backend |
| Project-owned yt-dlp process adapter | Full control of JSON, EJS runtime, PO provider seam, format IDs, retries and process cancellation; can preserve current app license choice | Requires packaging Python >=3.11 plus native JS runtime for each ABI; ongoing yt-dlp/EJS updates and device testing are necessary | Recommended |

### Runtime packaging constraints

The app currently declares ABI filters `arm64-v8a`, `armeabi-v7a` and `x86_64`. The proposed runtime must either supply and test binaries for all three, or explicitly detect unsupported ABI and disable only YouTube extraction on that device. Do not ship a runtime that installs successfully but fails on launch for 32-bit devices.

The current NDK is `27.2.12479018`; QuickJS-NG documentation lists Android NDK support from 26.0.10792818, but that is only a toolchain compatibility statement, not evidence that our exact build works on the device.

The app has no declared top-level project license file at the research snapshot. Before importing or linking a GPL-3.0 component, the project owner must make an explicit licensing decision. This report therefore borrows architecture, not third-party source code.

## Recommended staged architecture

```text
YouTube URL
   |
   v
URL normalizer + video ID validator
   |
   v
YtDlpRuntime (own adapter; process ID + cancellation + timeout)
   |
   +--> yt-dlp -J --skip-download <URL>
   |        |
   |        v
   |    YtDlpInfoJsonParser
   |        |
   |        v
   |    Real format options (format_id, codecs, height, bitrate, size)
   |
   +--> EJS challenge execution (pinned yt-dlp-ejs + installed JS runtime)
   |
   +--> optional PO Token provider plugin (never required for unrelated sites)
            |
            v
      download exact selected format IDs
            |
            v
      FFmpeg merge/remux -> SAF output
```

### Format selection rules

- Use the current URL's actual `formats[]` list; never synthesize a fixed 360p/720p/1080p menu.
- Show only non-DRM formats with usable IDs.
- Distinguish muxed audio/video formats from video-only and audio-only formats.
- When the chosen video format is video-only, pair it with an actual audio-only format returned by the same metadata response.
- Pass each selector as one argument to `ProcessBuilder`; never build a shell command string.
- A successful metadata request must not claim that download is guaranteed; revalidate/handle 403, expired URLs, missing format and changed client state.
- Do not save `url`, `manifest_url`, `http_headers`, Cookie, Authorization or PO Token fields in Room or diagnostics. Signed URLs are short-lived transport details, not durable app state.

## Cookie/session bridge

Current WebView CookieManager -> OkHttp interceptor wiring is useful for direct resources and Media3, but it is not automatically visible to a Python subprocess.

Before yt-dlp runtime is connected:
1. Define a short-lived credential bridge interface.
2. Query cookies only for the exact YouTube host/request URL needed.
3. Avoid logging values; avoid storing them in Room.
4. If Netscape cookie export is used, make it private, temporary, best-effort, and delete it when the parse/download process exits.
5. Document that Android CookieManager does not expose full cookie metadata (domain/path/expiry/SameSite) through the simple `getCookie(url)` string; do not pretend this reconstructs every browser cookie attribute.
6. Test login, logout, cookie expiry and cross-host requests on a physical device.

## Risks and controls

| Risk | Consequence | Control / acceptance test |
|---|---|---|
| Python below 3.11 | Latest yt-dlp refuses to run | Assert runtime version at initialization and run a pinned extractor smoke test |
| EJS absent or wrong JS engine | Signature/n challenge errors or missing formats | Test a known challenge fixture and assert the runtime is actually invoked |
| PO Token missing | Some clients/formats return 403 or are omitted | Surface provider capability status; retry only with a supported provider/client strategy |
| Expiring signed URLs | Parse succeeds but later download fails | Fetch fresh info on expiry/403; never reuse a persisted signed URL |
| ABI mismatch | Native runtime crashes/does not start | Device/CI matrix for arm64-v8a, armeabi-v7a and x86_64; graceful feature disable otherwise |
| License mismatch | Distribution obligation or incompatibility | Keep GPL components out unless project licensing is explicitly resolved |
| Large package | Install/update friction | Measure per-ABI APK and installed footprint before shipping; prefer optional component package only if integrity/update process is reliable |

## Acceptance criteria before calling YouTube support complete

- A real YouTube watch URL returns non-empty JSON with title, ID and actual `formats[]` on a clean install.
- The same video is tested logged-out and with a WebView-authenticated session.
- Test HLS/DASH, muxed and separate A/V streams; confirm the chosen `format_id` pair downloads and merges.
- EJS challenge logs show a real installed runtime was used; no “URL recognized” false success.
- PO Token provider is separately reported as available/unavailable; test a case requiring it and a case not requiring it.
- Re-parse on expired URL / HTTP 403; no stale signed URL is persisted.
- No Cookie, Authorization header or PO Token appears in logs, crash reports, Room or analytics.
- Debug APK tested on physical arm64 Android 14, then additional ABI smoke tests; measure APK and installed storage.
- CI covers JSON parser tests, runtime availability probes, cancellation/process cleanup and a small set of deterministic extractor fixtures.

## Explicit current status

**Implemented:** YouTube URL validation; WebView/OkHttp cookie bridge for direct network requests; redacted YouTube request diagnostics; pure parser/test groundwork for yt-dlp JSON; HLS/DASH segment cache pause/continue.

**Not implemented:** Bundled Python >=3.11 yt-dlp runtime; bundled `yt-dlp-ejs`; executable QuickJS/Deno/Node runtime; `-J` process invocation; real format chooser wiring; PO Token provider; yt-dlp download/merge adapter; on-device YouTube integration tests.

This distinction is intentional: parser groundwork must not be advertised as working YouTube extraction.
