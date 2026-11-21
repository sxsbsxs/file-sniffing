# Changelog

## 0.4.0

- Added persistent WorkManager-based download jobs with foreground notifications.
- Restored task state from WorkManager after reopening the app.
- Added cancel and retry actions for download tasks.
- Added clear HLS playlist handling: master variant selection, TS segment concatenation, and fMP4 initialization/fragment concatenation.
- Added static DASH MPD handling for SegmentList and SegmentTemplate with SegmentTimeline.
- Added Android 13+ notification permission request for download progress.
- Throttled byte-progress updates to reduce task database and notification churn.
- Added clearer limits for encrypted HLS, byte ranges, dynamic DASH, and separate DASH audio/video tracks.

## 0.3.0

- Added persistent-download worker foundation and background notification plumbing.
- Added HLS segment downloader.

## 0.2.0

- Added in-app webpage inspection and media resource classification.
- Added multi-select and batch download UI.
- Added direct file downloads to the selected SAF folder.
