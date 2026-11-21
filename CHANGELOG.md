# Changelog

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
