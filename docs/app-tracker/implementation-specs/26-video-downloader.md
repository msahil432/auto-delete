# 26 — In-App Video & GIF Downloader

> **Status:** ✅ Done

**Prerequisites:** `00-conventions.md`, `02-navigation-hub.md`, `25-design-system.md`.

---

## 1. Goal

Provide an integrated in-app browser and media downloader that enables users to load any web URL, detect downloadable video and GIF assets across multiple strategies, and maintain a persistent, actionable download history with retry, cancellation, deletion, and source page reload capabilities.

---

## 2. Detection Engine Architecture

The detection engine operates via [WebViewMediaDetector.kt](file:///d:/personal/auto%20delete/app/src/main/java/com/msahil432/multitool/downloader/WebViewMediaDetector.kt) coordinating three simultaneous detection strategies:

```
┌─────────────────────────────────────────────────────────────┐
│                   WebViewMediaDetector                      │
├─────────────────┬─────────────────────────┬─────────────────┤
│   Strategy 1    │       Strategy 2        │   Strategy 3    │
│    DOM Scan     │   Network Intercept     │  Canvas Capture │
├─────────────────┼─────────────────────────┼─────────────────┤
│ Injected JS     │ shouldInterceptRequest  │ Injected JS     │
│ scans <video>,  │ inspects requests for   │ detects canvas  │
│ <source>, <img> │ video/GIF extensions    │ video players;  │
│ elements        │ and MIME headers        │ captureStream() │
└─────────────────┴─────────────────────────┴─────────────────┘
```

1. **Strategy 1: DOM Inspection ([MediaDetectionJs.kt](file:///d:/personal/auto%20delete/app/src/main/java/com/msahil432/multitool/downloader/MediaDetectionJs.kt))**
   - Automatically executed via `evaluateJavascript` upon `onPageFinished`.
   - Traverses `<video>` tags, `<source>` children, and `<img>` tags pointing to animated GIFs and WebP resources.
   - Extracts URLs, MIME types, resolutions, durations, and poster thumbnails.
   - Dispatches deduplicated JSON items back to Kotlin via `window.MultiToolBridge.onMediaDetected`.

2. **Strategy 2: Network Request Interception**
   - Intercepts requests inside `WebViewClient.shouldInterceptRequest`.
   - Filters URLs against media extensions (`mp4`, `webm`, `mkv`, `m3u8`, `ts`, `gif`, `webp`) and streaming MIME types.
   - Captures dynamic media requested by client-side JavaScript players.

3. **Strategy 3: Canvas Recording & Non-Standard Container Analysis**
   - Injected script identifies `<canvas>` elements with video-like dimensions, elements with video CSS background images, and custom video data attributes (`data-video-src`).
   - For custom canvas players, streams video frames using `canvas.captureStream(30)` into `MediaRecorder` (`video/webm;codecs=vp9`) and dispatches the recorded blob back via `window.MultiToolBridge.onCanvasCaptured`.

---

## 3. Data & Storage Layer

### 3.1 Entities & Enums ([DownloadEntities.kt](file:///d:/personal/auto%20delete/app/src/main/java/com/msahil432/multitool/data/DownloadEntities.kt))
- `DownloadStatus`: `QUEUED`, `DOWNLOADING`, `COMPLETED`, `FAILED`, `CANCELLED`
- `MediaType`: `VIDEO`, `GIF`, `ANIMATED_WEBP`, `HLS_STREAM`, `DASH_STREAM`
- `DownloadItem`: Room entity stored in `downloads` table with indexes on `status` and `createdAt`.

### 3.2 Database Migration ([AppDatabase.kt](file:///d:/personal/auto%20delete/app/src/main/java/com/msahil432/multitool/data/AppDatabase.kt))
- Database schema incremented to `version = 2`.
- `MIGRATION_1_2` executes `CREATE TABLE IF NOT EXISTS downloads` and creates indices for fast querying.
- Registered in [MultiToolApp.kt](file:///d:/personal/auto%20delete/app/src/main/java/com/msahil432/multitool/MultiToolApp.kt).

### 3.3 Storage Location & Backups
- Files are saved to app-specific external storage: `Android/data/com.msahil432.multitool/files/downloads/`.
- Download records and media files are excluded from Android auto backup (`backup_rules.xml` and `data_extraction_rules.xml`) to keep downloads device-local.

---

## 4. Download Execution Engine

### 4.1 Direct File Downloads ([MediaDownloadWorker.kt](file:///d:/personal/auto%20delete/app/src/main/java/com/msahil432/multitool/downloader/MediaDownloadWorker.kt))
- Uses WorkManager `CoroutineWorker` for background execution.
- Reads chunks in 8 KB buffers via `HttpURLConnection`.
- Automatically deconflicts existing file names (e.g. `video_(1).mp4`).
- Throttles Room progress updates to every ~50 KB to protect SQLite write throughput.
- Enforces a maximum concurrency limit of 3 simultaneous downloads.

### 4.2 HLS/DASH Stream Merging ([HlsMergeWorker.kt](file:///d:/personal/auto%20delete/app/src/main/java/com/msahil432/multitool/downloader/HlsMergeWorker.kt))
- Employs `mobile-ffmpeg-full` to remux HLS playlists directly to MP4 without re-encoding:
  `-y -i <m3u8Url> -c copy -bsf:a aac_adtstoasc <outputPath>`.
- Hooked to `Config.enableStatisticsCallback` for progress monitoring.

### 4.3 External Playback
- Configured `androidx.core.content.FileProvider` in [AndroidManifest.xml](file:///d:/personal/auto%20delete/app/src/main/AndroidManifest.xml) with [file_paths.xml](file:///d:/personal/auto%20delete/app/src/main/res/xml/file_paths.xml) allowing users to open completed downloads in any installed media player.

---

## 5. UI Architecture

1. **[VideoDownloaderScreen.kt](file:///d:/personal/auto%20delete/app/src/main/java/com/msahil432/multitool/ui/screens/VideoDownloaderScreen.kt)**
   - TopAppBar with navigation back and quick-link to downloads list.
   - Text input field with scheme auto-correction (`https://`).
   - Embedded Android `WebView` configured with JavaScript and bridge.
   - Floating Action Button with badge displaying detected media count.
   - Modal bottom sheet displaying detected candidates with select-all, individual toggles, and bulk enqueue.

2. **[DownloadListScreen.kt](file:///d:/personal/auto%20delete/app/src/main/java/com/msahil432/multitool/ui/screens/DownloadListScreen.kt)**
   - Displays download cards ([DownloadItemCard.kt](file:///d:/personal/auto%20delete/app/src/main/java/com/msahil432/multitool/ui/components/DownloadItemCard.kt)) with status badges, progress bars, and formatted size.
   - Contextual actions: `Open`, `Retry`, `Cancel`, `Delete`, and `Reload Source Page`.
   - Top overflow menu for "Clear Completed" and "Clear All".
   - `EmptyState` when history is clear.

3. **Navigation Wiring**
   - Tile added in [AppSettingsScreen.kt](file:///d:/personal/auto%20delete/app/src/main/java/com/msahil432/multitool/ui/screens/AppSettingsScreen.kt) under Section "Video Downloader".
   - Routes `"video_downloader"`, `"video_downloader/{sourceUrl}"`, and `"download_list"` wired in [BottomNavScaffold.kt](file:///d:/personal/auto%20delete/app/src/main/java/com/msahil432/multitool/ui/navigation/BottomNavScaffold.kt).

---

## 6. Sentry & Observability

- Uses `Breadcrumbs.CAT_DOWNLOADER` for structured diagnostic breadcrumbs across:
  - Page load start / finish / error
  - DOM scan and canvas detection events
  - Download worker start / progress / complete / failure
  - Stream remuxing operations
- Zero-PII adherence: URLs are logged only for navigation events; sensitive credentials, auth headers, and session tokens are never logged.
