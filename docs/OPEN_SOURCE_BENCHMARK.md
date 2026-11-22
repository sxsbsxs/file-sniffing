# Open-source benchmark and product review

**Research snapshot:** 2026-11-22. GitHub star counts are approximate repository metadata snapshots; stars measure attention, not software quality. Projects are grouped by relevance, not ranked as implementation dependencies.

## 1. Reference projects (26)

### Android video/audio frontends

| Project | Stars | What it does well | What is relevant to file-sniffing |
|---|---:|---|---|
| [NewPipe](https://github.com/TeamNewPipe/NewPipe) | 40.9k | Lightweight Android media frontend, multiple services, stream metadata, background audio, local playlists and downloads | Anonymous-first service extraction; show useful media details before an action; keep extraction separate from UI |
| [Seal](https://github.com/JunkFood02/Seal) | 30.9k | Android Compose + yt-dlp, explicit format page, playlist selection, task states, action sheets, update controls | The closest product reference: one clean entry, then a focused configuration sheet; typed task state rather than a pile of buttons |
| [LibreTube](https://github.com/libre-tube/LibreTube) | 13.0k | Material 3 Expressive Android UI, privacy-oriented defaults, clear media presentation | Better hierarchy, restraint, and coherent use of Material 3 components |
| [YTDLnis](https://github.com/deniscerri/ytdlnis) | 11.2k | Rich format choices, playlist/bulk configuration, quick mode, scheduled queue, retry, download history, detailed settings | Format selection, batch semantics, persisted failed fetches and re-download flows |
| [PipePipe](https://github.com/InfinityLoop1308/PipePipe) | 7.3k | Android multi-service frontend based on NewPipe, playlists and player controls | Service adapters and clear separation between service extraction and presentation |

### General download managers and transfer engines

| Project | Stars | What it does well | What is relevant to file-sniffing |
|---|---:|---|---|
| [Motrix](https://github.com/agalwood/Motrix) | 58.6k | Clean UI, persistent SQLite tasks, dashboard, transfer profiles, task recovery and a separate download core | Keep UI independent of engines; make queue state survive restarts; show useful transfer stats |
| [aria2](https://github.com/aria2/aria2) | 43.6k | Multi-source and multi-protocol transfer engine | Mature transfer engine concepts; do not reimplement HTTP range mechanics unnecessarily |
| [Gopeed](https://github.com/GopeedLab/gopeed) | 27.9k | Cross-platform manager, HTTP/torrent/magnet/ed2k, extensions, REST/CLI and browser handoff | Clear core/UI boundary and extension seams without coupling extraction to screens |
| [AB Download Manager](https://github.com/amir1376/ab-download-manager) | 19.4k | Queues, scheduler, browser handoff, Android release and modern themes | Queue overview, scheduling model and visual clarity |
| [Xtreme Download Manager](https://github.com/subhra74/xdm) | 8.7k | Segmented/resumable HTTP downloads, HLS/DASH choices, subtitles, batch downloads, retry and scheduling | Resume semantics, direct-to-final-file strategy, quality/audio-track choices and retry details |
| [Persepolis](https://github.com/persepolisdm/persepolis) | 7.5k | GUI around aria2, queue and scheduling | Keep advanced transfer knobs away from the primary screen |

### Extraction, streams and site-specific tools

| Project | Stars | What it does well | What is relevant to file-sniffing |
|---|---:|---|---|
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | 209.7k | Large extractor ecosystem, real format metadata, playlists, post-processing and update channels | Use its actual format IDs and extractor metadata; never invent quality options |
| [FFmpeg](https://github.com/FFmpeg/FFmpeg) | 67.8k | Media muxing/transcoding toolbox | Use only where needed; avoid re-encoding when a stream-copy merge is sufficient |
| [Cobalt](https://github.com/imputnet/cobalt) | 46.4k | Paste-link-first experience, simple output, privacy/no tracking, resolver/API separation | Keep the public flow very short; expose complexity only when a user opens options |
| [Bili23-Downloader](https://github.com/ScottSloan/Bili23-Downloader) | 9.4k | Explicit video/audio/codec choices, preview of exactly what will download, subtitles/danmaku/metadata, detailed tasks and tests | Add a "will download" preview; model selections separately; test task completion durability |
| [N_m3u8DL-RE](https://github.com/nilaoda/N_m3u8DL-RE) | 9.1k | HLS/DASH/ISM VOD and live, track filtering, stream compatibility and robust mux planning | Preserve track identity, audio/video compatibility and timeline correctness; do not concatenate segments manually |
| [cat-catch](https://github.com/xifangczy/cat-catch) | 23.1k | Browser media-resource detection and batch handling | Useful as a reference for resource classification, but not a reason to restore the in-app browser |
| [HeiSir2014/M3U8-Downloader](https://github.com/HeiSir2014/M3U8-Downloader) | 4.2k | Multi-threaded HLS download, resume/cache and merge | HLS task lifecycle and segment integrity ideas |
| [HLS Downloader](https://github.com/puemos/hls-downloader) | 2.8k | Stream discovery, resolution/audio-language selection, disk-backed output and mux tests | Track selection and durable streaming output |
| [youtubedl-android](https://github.com/yausername/youtubedl-android) | 1.4k | Android wrapper for yt-dlp/Python, optional FFmpeg/aria2 and ABI packaging guidance | Treat wrapper, executable, FFmpeg and optional tools as separate size/update decisions |
| [JeffMony/VideoDownloader](https://github.com/JeffMony/VideoDownloader) | 517 | Android MP4/HLS queue, database, merge and customizable title/cover | A smaller Android-specific reference for queue persistence and HLS output |
| [Tyrrrz/YoutubeDownloader](https://github.com/Tyrrrz/YoutubeDownloader) | 16.8k | Clear source-driven quality and playlist download workflows | Keep quality choices tied to the source's returned formats |
| [Ghost Downloader 3](https://github.com/XiaoYouChR/Ghost-Downloader-3) | 14.6k | Broad protocol coverage and an extensible general downloader | Separate site extraction from transfer engines; make advanced features optional |
| [Parabolic](https://github.com/NickvisionApps/Parabolic) | 7.8k | Simple yt-dlp GUI for video/audio downloads | Keep the default path approachable while offering a deliberate media choice step |

### Historical-only references

| Project | Stars | Status | Use |
|---|---:|---|---|
| [DownKyi](https://github.com/leiurayer/downkyi) | 24.3k | Deprecated project | Historical Bilibili workflow ideas only; not a dependency or compatibility baseline |
| [BBDown](https://github.com/nilaoda/BBDown) | 13.8k | Archived | Historical format and Bilibili API reference only; not a maintained foundation |

## 2. What the current repository actually has

Inspected on `ui/consistency-redesign`:

- A browser-free URL/share resolver using yt-dlp for supported site pages.
- Direct-file classification and OkDownload/OkHttp transfers.
- Media3 handling for supported HLS/DASH manifests.
- FFmpeg through youtubedl-android for site extraction/audio extraction/merging.
- WorkManager foreground workers, Room task history, DataStore preferences and MediaStore/SAF output.
- ABI-specific APKs. The arm64 debug APK from CI was measured at 58,255,871 bytes (55.5 MiB).
- Real yt-dlp format IDs are now represented in `SniffedResource`, passed to the Worker and saved as `formatSelector` for retry.

### Main product gaps found

1. `MainActivity.kt` still owns the root app and most screen UI in one large file; future changes should split Home, Downloads and Local Files into focused presentation files.
2. The previous per-format card stack has now been replaced on the branch with one media preview card per URL and a dedicated download-options sheet.
3. Thumbnail, duration, uploader, codec, frame rate and estimated format sizes are now carried from yt-dlp into the preview/configuration flow; thumbnail loading is handled by Coil.
4. The new configuration sheet allows video format choice, audio-only mode, an actual audio format for video-only streams, and a pre-enqueue output preview. It deliberately keeps a muxed format's embedded audio rather than attaching a second audio stream.
5. Common playlist URL shapes now dispatch to yt-dlp's generic flat-playlist path; the parser accepts generic entries only when yt-dlp supplies a real HTTP(S) page URL. This is heuristic coverage, not a universal playlist detector for every extractor.
6. Download Center now has search by file/error text and sort by recent/name/progress; a focused task-detail/error view and bulk task actions remain future work.
7. The new configuration sheet and preview flow still require CI and device-level interaction verification; source-code presence is not proof of a successful download.
8. Existing worker, Room and transfer code should be strengthened and tested, not replaced wholesale.

## 3. Design decisions for this repository

The product should remain simpler than YTDLnis. Do not copy its terminal, cookie profiles, hundreds of settings or background channel monitoring.

Adopt these patterns instead:

- **Seal:** URL-first entry; a dedicated configuration sheet; explicit typed task states; per-task actions.
- **YTDLnis:** format-specific selection; playlist/bulk data model; queued/failed/cancelled/history separation; retry of saved failed requests.
- **Bili23:** preview the exact video/audio/subtitle outputs before enqueue; persist task transitions durably; integrity tests.
- **NewPipe:** anonymous-first extraction and clear media metadata before download.
- **Motrix / Gopeed / aria2:** a UI-independent transfer core; persistent queue; sensible defaults; visible speed/size/ETA.
- **N_m3u8DL-RE / HLS Downloader / XDM:** explicit stream/track compatibility, robust muxing, retry and resume semantics.
- **youtubedl-android:** keep native ABI packaging modular; optional engines should not be bundled merely because they exist.

Do not copy source code blindly. Check each project's license and dependency boundary before adopting code. Do not bypass DRM, account restrictions, region restrictions, paywalls or entitlement checks.

## 4. Implementation plan

### P0 — make the core flow understandable
- [Implemented on the current branch; awaiting CI/device verification] Replace the long per-format card list with one media preview card per resolved item.
- [Implemented on the current branch; awaiting CI/device verification] Show available video formats and audio tracks inside a bottom-sheet configuration flow.
- [Implemented on the current branch; awaiting CI/device verification] Allow Video / Audio-only mode, select a real source format, select a compatible audio track where needed, and preview the output name/size before enqueue.
- [Already present and extended] Keep the selected format selector attached to the task and Room retry record.

### P0 — make downloads manageable
- [Implemented on the current branch; awaiting CI/device verification] Add search and sorting to Download Center.
- Make active/queued/paused/completed/failed states visually distinct.
- Show speed, bytes, ETA and concise failure summary; reveal full sanitized error details on demand.
- Keep duplicate prevention scoped to URL + selected format.

### P1 — broaden reliable extractor behavior
- [Implemented on the current branch; awaiting CI/device verification] Support common non-YouTube yt-dlp playlist URL shapes and generic entries with real HTTP(S) page URLs. A universal per-extractor playlist catalogue and user item-selection screen remain future work.
- Add offline tests for multi-format metadata, video/audio pair validation and task-state durability.
- Improve extractor error messages without hiding the diagnostic cause.

### P1 — harden the architecture
- Split the screen code into Home, Downloads and Local Files presentation files.
- Keep UI free of network/Room orchestration.
- Keep Room as the durable task source and WorkManager as the executor.
- Audit pause/resume behavior separately for direct HTTP, yt-dlp and HLS/DASH, because their recovery guarantees are not identical.

### P2 — reduce maintenance and package risk
- Review yt-dlp Android wrapper upgrades separately from the Python extractor update cadence.
- Keep ABI-specific artifacts and the arm64 size guard.
- Audit unused dependencies, cleartext networking and release minification only after verifying each dependency's runtime requirements.

## 5. Success criteria

- One resolved video produces one compact preview, not a long stack of download buttons.
- The user can choose a real available video format and a valid audio track before pressing Download.
- The queued task shows exactly the selected output and retries with the same format.
- Search/sort works across persisted and live tasks; errors include a recoverable explanation.
- HLS/DASH/HTTP/site downloads have separate, tested pause/resume behavior.
- Arm64 debug APK remains below 65 MiB unless a measured, justified feature changes the budget.
