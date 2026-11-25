# Offline Cyber Deck (Native Android — Kotlin + Jetpack Compose)

Offline Cyber Deck is a browser-first, offline-capable personal digital library and archive built for Android tablets and phones (primary target: **Samsung Galaxy Tab A9, 4 GB RAM**). It requires no account, no cloud backend, no telemetry, and no API key.

## Architecture

Source tree under `app/src/main/java/com/offlinecyberdeck/`:

1. **`core/model/`** — Domain enums & models (`ThemeMode`, `ArchiveStatus`, `StorageTarget`, `LibraryCategory`, `MediaAvailability`, `DeckDriveStatus`, `StorageDashboardStats`).
2. **`core/database/`** — Room SQLite database (`CyberDeckDatabase.kt`), entities, DAOs (`TabDao`, `BookmarkDao`, `HistoryDao`, `DownloadDao`, `ArchiveDao`, `LibraryDao`, `MediaDao`, `SiteSettingDao`, `RecentSearchDao`, `SettingsDao`).
3. **`core/storage/`** — `DeckDriveManager.kt` using Android Storage Access Framework (`DocumentFile`, `takePersistableUriPermission`, `Os.fstatvfs` capacity probing, safe duplicate naming, disconnected drive handling, write/read self-test).
4. **`core/network/`** — `NetworkMonitor.kt` tracking live `ONLINE`/`OFFLINE` transport status via `ConnectivityManager`.
5. **`core/settings/`** — `SettingsRepository.kt` managing reactive app settings, JSON catalog backup/restore, and privacy-safe diagnostics export (browsing URLs/history excluded).
6. **`browser/`** — `WebViewPoolManager.kt` (conservative LRU WebView pool capped at 1–5 live views with `saveState`/`restoreState` for 4 GB RAM devices, strict SSL blocking, external scheme confirmation, zero native `@JavascriptInterface` bridges, incognito cookie/storage purge), `BrowserViewModel.kt`, and `BrowserScreen.kt`.
7. **`downloads/`** — `DownloadRepository.kt` and `DownloadsScreen.kt` (HTTP/HTTPS streaming, `Range` resume when supported, SAF destination support, `FileProvider` open/share).
8. **`archive/`** — `PageArchiveEngine.kt` and `ArchiveScreens.kt` (DOM HTML capture via `evaluateJavascript`, Jsoup parsing, static image/CSS capture, cross-archive link detection, honest `Reader Snapshot` / `Partial Archive` / `Full Archive` status, offline replay + simplified text reader).
9. **`library/`** — `KnowledgeVaultRepository.kt` and `LibraryScreens.kt` (SAF import across 13 categories, built-in `PdfRenderer` PDF viewer, built-in EPUB/HTML/TXT text reader, external app handoff, curated external package directory).
10. **`media/`** — `MediaVaultRepository.kt` and `MediaScreens.kt` (SAF folder scan/file import, `MediaMetadataRetriever` thumbnails, exact video ID & fuzzy title matching, `Available Offline` vs `No Local File` distinction, Media3 `ExoPlayer` local playback, playlists, watch later, continue watching, catalog JSON export/import).
11. **`search/`** — `UnifiedSearchRepository.kt` and `UnifiedSearchScreen.kt` (offline ranked search across archives, Knowledge Vault, Media Vault, bookmarks, and history).
12. **`bookmarks/` & `settings/` & `ui/`** — Bookmarks/History managers, Settings + Storage Dashboard, adaptive Material 3 dark/light/system theme (`NavigationRail` on tablet landscape, compact `NavigationBar` on phones).

## Build & Install on Samsung Galaxy Tab A9

> **Note:** This repository was authored in a Web workspace that lacks JDK / Android SDK / Gradle; compile the APK locally in Android Studio or via Gradle CLI.

### Option A: Android Studio
1. Open the `/android` folder in **Android Studio Ladybug (2024.2+)**.
2. Allow Gradle sync to download Android SDK 35, Kotlin 2.0.21, Room, Media3, Jsoup, and Gson.
3. On your **Samsung Galaxy Tab A9**, enable **Developer options** → **USB debugging** (Settings → About tablet → Software information → tap *Build number* 7 times).
4. Connect the tablet via USB-C and click **Run ▶** (`app`).

### Option B: Command Line APK Build
```bash
cd android
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
*(Or use Android Studio's Gradle wrapper once generated via `gradle wrapper`.)*

## External USB-C SSD ("Deck Drive") Setup & File Systems

1. Connect your USB-C SSD (via direct USB-C cable or powered USB-C hub if the SSD draws high power).
2. Recommended filesystem: **exFAT** (supported natively on modern Samsung Galaxy Tab A9 / One UI without the 4 GB single-file limit of FAT32). Avoid NTFS/ext4 unless your tablet kernel mounts them natively in Samsung My Files.
3. Open **Offline Cyber Deck → Settings → Deck Drive → Select SSD Folder**.
4. In the Android system SAF picker, navigate to the USB drive (create a dedicated folder such as `CyberDeckVault` and tap **Use this folder** → **Allow**). Android restricts granting persistable access directly to the raw root of some volumes, so selecting a dedicated folder on the SSD is recommended.
5. Tap **Write/Read Test** to verify write permission.
6. **Safe Eject:** Android does not let standard apps programmatically unmount USB drives. Before unplugging, wait for downloads/archives to finish, then open **Android Settings → Storage** (or the USB notification) and tap **Unmount / Eject**.

## Storage Folder Structure

### Internal Storage (`context.filesDir`)
- `databases/cyber_deck.db` — Room metadata database (kept on internal storage for reliability)
- `archives/<archive-id>/index.html` & `assets/` — Internal web archives
- `downloads/` — Default internal downloads
- `vault/` — Locally copied Knowledge Vault documents
- `cache/thumbs/` — Lightweight 320×180 JPEG media thumbnails

### External Deck Drive (inside user-selected SAF folder)
- `CyberDeck/Archives/<archive-id>/index.html` & `assets/`
- `CyberDeck/Downloads/`
- `CyberDeck/_SelfTest/` (temporary probe file deleted after verification)

## Technical Limitations (Documented Honestly)

- **Complex Web Replay:** Dynamic SPAs, login-walled pages, DRM/streaming media, and cross-origin restricted assets cannot be replayed identically offline. Interactive `<script>` tags are stripped during archiving for safe static replay.
- **Kiwix ZIM & Offline Maps:** `.zim`, `.mbtiles`, and `.obf` files can be imported/cataloged in the Knowledge Vault, but an in-app native ZIM/vector-map renderer is not bundled; use **Open** to launch Kiwix or an installed offline maps app.
- **No Unauthorized Stream Extraction:** Public video metadata capture only reads visible page title/OpenGraph tags on pages you visit; it never extracts YouTube video streams or bypasses authentication/DRM.
- **SAF Capacity Reporting:** `Os.fstatvfs` provides external drive free/total capacity on supported Android storage providers; if an OEM USB document provider restricts `statvfs`, capacity is shown honestly as `Unknown`.
