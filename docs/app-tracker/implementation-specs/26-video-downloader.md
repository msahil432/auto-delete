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
- `TranscodeStatus`: `NOT_APPLICABLE`, `PENDING`, `TRANSCODING`, `COMPLETED`, `FAILED`
- `DownloadItem`: Room entity stored in `downloads` table with indexes on `status` and `createdAt`. Tracks `transcodeStatus`, `originalSizeBytes`, and `compressedSizeBytes`.

### 3.2 Database Migration ([AppDatabase.kt](file:///d:/personal/auto%20delete/app/src/main/java/com/msahil432/multitool/data/AppDatabase.kt))
- Database schema incremented to `version = 3`.
- `MIGRATION_1_2` executes `CREATE TABLE IF NOT EXISTS downloads` and creates indices for fast querying.
- `MIGRATION_2_3` adds `transcodeStatus`, `originalSizeBytes`, and `compressedSizeBytes` columns to `downloads`.
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
- Schedules `H265TranscodeWorker` upon completion for eligible video assets.

### 4.2 HLS/DASH Stream Merging ([HlsMergeWorker.kt](file:///d:/personal/auto%20delete/app/src/main/java/com/msahil432/multitool/downloader/HlsMergeWorker.kt))
- Employs `dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7` (hosted on Maven Central) to remux HLS playlists directly to MP4 without re-encoding:
  `-y -i <m3u8Url> -c copy -bsf:a aac_adtstoasc <outputPath>`.
- Hooked to `FFmpegKitConfig.enableStatisticsCallback` for progress monitoring.
- Schedules `H265TranscodeWorker` upon completion for eligible video streams.

### 4.3 External Playback
- Configured `androidx.core.content.FileProvider` in [AndroidManifest.xml](file:///d:/personal/auto%20delete/app/src/main/AndroidManifest.xml) with [file_paths.xml](file:///d:/personal/auto%20delete/app/src/main/res/xml/file_paths.xml) allowing users to open completed downloads in any installed media player.

### 4.4 H.265 Hardware Transcoding ([H265TranscodeWorker.kt](file:///d:/personal/auto%20delete/app/src/main/java/com/msahil432/multitool/downloader/H265TranscodeWorker.kt))
- Executes FFmpegKit hardware encoding (`-c:v hevc_mediacodec -c:a copy -tag:v hvc1`) to compress completed videos.
- Enforces single-job concurrency (`transcodingCount == 0`) and requires non-low battery constraint.
- Preserves original files if transcoding fails, and updates original vs compressed sizes in database.

---

## 5. UI Architecture

1. **[VideoDownloaderScreen.kt](file:///d:/personal/auto%20delete/app/src/main/java/com/msahil432/multitool/ui/screens/VideoDownloaderScreen.kt)**
   - Compact `CenterAlignedTopAppBar` and rounded URL bar maximizing webview viewport.
   - Embedded Android `WebView` configured with JavaScript and bridge.
   - Floating Action Button with badge displaying detected media count.
   - Modal bottom sheet displaying detected candidates with candidate filters, filename grouping, and "Select all except for existing".

2. **[DownloadListScreen.kt](file:///d:/personal/auto%20delete/app/src/main/java/com/msahil432/multitool/ui/screens/DownloadListScreen.kt)**
   - Displays download cards ([DownloadItemCard.kt](file:///d:/personal/auto%20delete/app/src/main/java/com/msahil432/multitool/ui/components/DownloadItemCard.kt)) with status badges, progress bars, original → compressed sizes, and transcoding state.
   - Contextual actions: `Open`, `Retry`, `Cancel`, `Delete`, and `Reload Source Page`.
   - Top overflow menu for "Clear Completed", "Clear All" (retains local files), and "Delete All" (deletes files + database entries with confirmation dialog).
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
  - H.265 transcoding start / complete / failure
- Zero-PII adherence: URLs are logged only for navigation events; sensitive credentials, auth headers, and session tokens are never logged.

---

## 7. Updates (v2)

1. **Compact Top Bar & URL Bar:** Replaced default `TopAppBar` with a 48dp `CenterAlignedTopAppBar` and condensed the URL input into a pill-shaped `BasicTextField` row, maximizing WebView viewport area.
2. **Candidate Filters:** Added `MediaFilterBar` in the media selection bottom sheet supporting multi-select type chips (`VIDEO`, `GIF`, etc.) and decimal minimum size filtering with configurable measurement unit (`KB`, `MB`, `GB`).
3. **Candidate Grouping by Filename:** Flattens detected candidates sharing the same `suggestedFileName` under a visual `GroupHeaderRow` with indented child items (`isGroupChild = true`).
4. **Already Downloaded Status & Selective Action:** Marks candidates that already exist in completed history with a "Downloaded" chip and strikethrough size comparison, adding a "Select all except for existing" bulk action.
5. **H.265 On-Device Hardware Transcoding:** Completed video downloads are automatically and sequentially re-encoded to H.265 via `H265TranscodeWorker`. Download cards display original and compressed sizes (`12 MB → 5 MB (h265)`).
6. **Throttled Toast Notifications:** `DownloadToastController` delivers non-spammy lifecycle updates (15s throttle on progress updates, immediate notification on all downloads completed).
7. **Delete All vs Clear All:** Added "Delete All" with confirmation guard removing both database records and physical disk files, reserving "Clear All" for database record removal only.

