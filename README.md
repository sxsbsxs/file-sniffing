# File Sniffing

An Android-first media resource discovery and download manager.

## Product direction

A clean, mobile-native experience: paste a page URL, inspect the media resources exposed by that page, choose exactly what to save, choose a destination folder, and manage downloads locally.

The visual direction takes cues from the clarity and polish of leading consumer apps (Douyin, Kuaishou, YouTube and Bilibili) without copying their branding or layouts.

## Current milestone

**Milestone 0 — Android UI foundation**

- [x] Create the Android project structure.
- [ ] Build the URL entry and sniffing home screen.
- [ ] Add a native Android folder picker using the Storage Access Framework.
- [ ] Add the downloads destination and empty states.
- [ ] Connect a real page/resource inspection engine.
- [ ] Add resource filtering, quality/type selection and download queue.
- [ ] Add pause, resume, retry, progress, notifications and storage management.

The first UI milestone intentionally does not fabricate sniff results or pretend downloads are working before the inspection and transfer engines are integrated.

## Planned architecture

- **Android UI:** Kotlin + Jetpack Compose.
- **Page inspection:** isolated sniffing interfaces with pluggable extractors.
- **Network:** scoped HTTP clients, redirect handling, response-header inspection and stream metadata.
- **Downloads:** Android DownloadManager or a controlled foreground service, selected per resource requirements.
- **Storage:** Android Storage Access Framework (SAF), including persisted folder permissions.

## Privacy and scope

- No user account or server is required for the basic workflow.
- Page inspection is limited to resources the user is authorized to access.
- DRM circumvention, authentication bypass, and extraction of protected streams are out of scope.
- Cookies or credentials, if ever supported, must remain local, be opt-in, and never be uploaded by default.

## Build

Open the project in Android Studio (JDK 17+, Android SDK 35) and run the `app` configuration. The GitHub Actions workflow also builds the debug APK.
