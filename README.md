# File Sniffing

An Android-first media resource discovery and download manager.

## Product direction

A clean, mobile-native experience: paste a page URL, inspect the media resources exposed by that page, choose exactly what to save, choose a destination folder, and manage downloads locally.

The visual direction takes cues from the clarity and polish of leading consumer apps (Douyin, Kuaishou, YouTube and Bilibili) without copying their branding or layouts.

## Current milestone

**Milestone 0 — Android UI foundation**

- [x] Create the Android project structure.
- [x] Build the URL entry and sniffing home screen.
- [x] Add a native Android folder picker using the Storage Access Framework.
- [x] Persist the user's selected destination folder permission.
- [x] Add the downloads destination and empty state.
- [x] Add a minimal adaptive launcher icon.
- [x] Add a GitHub Actions debug-build workflow.
- [ ] Connect a real page/resource inspection engine.
- [ ] Add resource filtering, quality/type selection and a real download queue.
- [ ] Add pause, resume, retry, progress notifications and storage management.

The UI foundation is interactive, but it does not fabricate sniff results or pretend downloads are working before the inspection and transfer engines are integrated. The URL is currently validated locally and the app explains that real inspection is the next milestone.

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

Open the project in Android Studio (JDK 17+, Android SDK 35). The project uses Gradle 8.9 and Android Gradle Plugin 8.7.3. GitHub Actions runs the Gradle assembleDebug task and publishes the debug APK as a workflow artifact.
