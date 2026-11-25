package com.offlinecyberdeck.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.offlinecyberdeck.core.model.AccentColorPreset
import com.offlinecyberdeck.core.model.ArchiveStatus
import com.offlinecyberdeck.core.model.DownloadStatus
import com.offlinecyberdeck.core.model.DuplicatePolicy
import com.offlinecyberdeck.core.model.LibraryCategory
import com.offlinecyberdeck.core.model.LibraryFileType
import com.offlinecyberdeck.core.model.SearchEngineOption
import com.offlinecyberdeck.core.model.StorageTarget
import com.offlinecyberdeck.core.model.ThemeMode

@Entity(tableName = "tabs")
data class TabEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val isIncognito: Boolean = false,
    val isDesktopMode: Boolean = false,
    val position: Int = 0,
    val isActive: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["url"], unique = false)]
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val folder: String = "General",
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "history",
    indices = [Index(value = ["visitedAt"]), Index(value = ["url"])]
)
data class HistoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "downloads",
    indices = [Index(value = ["createdAt"])]
)
data class DownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    val fileName: String,
    val mimeType: String = "application/octet-stream",
    val destinationUri: String = "",
    val storageTarget: StorageTarget = StorageTarget.INTERNAL,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val supportsResume: Boolean = false,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(
    tableName = "archived_pages",
    indices = [Index(value = ["url"]), Index(value = ["capturedAt"])]
)
data class ArchivedPageEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val status: ArchiveStatus = ArchiveStatus.READER_SNAPSHOT,
    val storageTarget: StorageTarget = StorageTarget.INTERNAL,
    val directoryPathOrUri: String = "",
    val entryFileName: String = "index.html",
    val readerText: String = "",
    val sizeBytes: Long = 0L,
    val assetCount: Int = 0,
    val missingAssetCount: Int = 0,
    val tags: String = "",
    val folder: String = "General",
    val isFavorite: Boolean = false,
    val readLater: Boolean = false,
    val statusNote: String = "",
    val capturedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "library_items",
    indices = [Index(value = ["category"]), Index(value = ["title"])]
)
data class LibraryItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val source: String = "Local Import",
    val category: LibraryCategory = LibraryCategory.BOOKS,
    val language: String = "en",
    val description: String = "",
    val fileUri: String,
    val fileName: String,
    val fileType: LibraryFileType = LibraryFileType.OTHER,
    val sizeBytes: Long = 0L,
    val storageTarget: StorageTarget = StorageTarget.INTERNAL,
    val tags: String = "",
    val collectionName: String = "Default",
    val isFavorite: Boolean = false,
    val lastReadPosition: Int = 0,
    val lastOpenedAt: Long? = null,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "media_items",
    indices = [Index(value = ["title"]), Index(value = ["videoId"])]
)
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val channel: String = "",
    val videoId: String? = null,
    val sourceUrl: String? = null,
    val description: String = "",
    val thumbnailPath: String? = null,
    val localFileUri: String? = null,
    val localFileName: String? = null,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val storageTarget: StorageTarget = StorageTarget.INTERNAL,
    val matchConfidence: Float = 0f,
    val matchConfirmed: Boolean = false,
    val watchedPositionMs: Long = 0L,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val isFavorite: Boolean = false,
    val watchLater: Boolean = false,
    val tags: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_media_cross_ref",
    primaryKeys = ["playlistId", "mediaId"]
)
data class PlaylistMediaCrossRef(
    val playlistId: String,
    val mediaId: String,
    val position: Int = 0
)

@Entity(tableName = "site_settings")
data class SiteSettingEntity(
    @PrimaryKey val host: String,
    val javaScriptEnabled: Boolean = true,
    val desktopMode: Boolean = false,
    val allowArchiveCapture: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "music_tracks",
    indices = [Index(value = ["title"]), Index(value = ["fileName"])]
)
data class MusicTrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String = "Unknown Artist",
    val album: String = "Local Music",
    val fileName: String,
    val fileUri: String,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val storageTarget: StorageTarget = StorageTarget.INTERNAL,
    val volumeLabel: String = "Internal",
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val searchEngine: SearchEngineOption = SearchEngineOption.GOOGLE,
    val customSearchTemplate: String = "https://www.google.com/search?q=%s",
    val showStatusStrip: Boolean = true,
    val showRecentOnNewTab: Boolean = true,
    val defaultDesktopMode: Boolean = false,
    val globalJavaScriptEnabled: Boolean = true,
    val maxLiveWebViews: Int = 3,
    val archiveStorageTarget: StorageTarget = StorageTarget.INTERNAL,
    val downloadStorageTarget: StorageTarget = StorageTarget.INTERNAL,
    val archiveCaptureResources: Boolean = true,
    val archiveQuotaMb: Int = 2048,
    val archiveExcludedHosts: String = "",
    val askBeforeDownload: Boolean = false,
    val downloadDuplicatePolicy: DuplicatePolicy = DuplicatePolicy.RENAME,
    val recordHistory: Boolean = true,
    val deckDriveTreeUri: String? = null,
    val deckDriveLabel: String? = null,
    val downloadFolderUri: String? = null,
    val developerModeEnabled: Boolean = true,
    val accentPreset: AccentColorPreset = AccentColorPreset.CYBER_CYAN,
    val brightnessOverrideEnabled: Boolean = false,
    val screenBrightnessPercent: Int = 80,
    val nightDimmerPercent: Int = 0,
    val immersiveFullscreen: Boolean = true,
    val hideNavChromeInBrowser: Boolean = false,
    val addressBarHeightDp: Int = 40,
    val addressBarFontSp: Int = 14,
    val addressBarCornerDp: Int = 10,
    val toolbarButtonSizeDp: Int = 34,
    val showTabStrip: Boolean = true,
    val bottomAddressBar: Boolean = false,
    val hideToolbarInLandscape: Boolean = false,
    val uiFontScale: Float = 0.95f,
    val webViewTextZoom: Int = 100,
    val webViewDebuggingEnabled: Boolean = false,
    val showDevHud: Boolean = false,
    val desktopLauncherMode: Boolean = true,
    val dexSetupPromptDismissed: Boolean = false,
    val pinnedExternalPackages: String = "",
    val webViewDarkMode: Boolean = true,
    val taskbarAutoHide: Boolean = false,
    val notificationSoundEnabled: Boolean = true,
    val desktopBackgroundId: String = "CYBER_NAVY",
    val customWallpaperPaths: String = "",
    val activeCustomWallpaperIndex: Int = -1,
    val offlineTubeGridColumns: Int = 3,
    val offlineTubeAutoImport: Boolean = true,
    val offlineTubeAutoPlayNext: Boolean = true,
    val offlineTubeDefaultSpeed: Float = 1.0f,
    val musicAutoImport: Boolean = true,
    val extraMediaSafUris: String = ""
)
