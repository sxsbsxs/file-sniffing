# Changelog

## 0.8.0

- Reused the original SAF document URI for direct-download retries so OkDownload can continue saved breakpoints.
- Preserved partial document URIs in WorkManager progress and Room history.
- Added legacy SharedPreferences-to-DataStore migration for the selected download folder.
- Added explicit HLS/DASH MIME hints for extensionless adaptive manifest URLs.
- Updated the task action label to distinguish continuing a breakpoint download from starting a fresh retry.

## 0.7.0

- Aligned the toolchain to Kotlin 2.3.20, KSP 2.3.12, AGP 8.13.2 and Gradle 8.14.8.
- Updated AndroidX Hilt Work integration/compiler to 1.4.0 for KSP2 processing.
- Updated Compose BOM to 2026.09.00 and Lifecycle to 2.11.0.
- Switched CI to install Android SDK 36 and build with the checked-in Gradle Wrapper.
- Added the WorkManager data-sync foreground service type to the merged manifest.
- Improved OkDownload progress for multi-block and resumed transfers.
- Made Media3-to-SAF export stop promptly on worker cancellation.
- Preserved partial SAF documents and OkDownload breakpoint offsets across cancelled/failed direct-download retries.

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
