package com.offlinecyberdeck.core.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import com.google.gson.GsonBuilder
import com.offlinecyberdeck.BuildConfig
import com.offlinecyberdeck.core.database.CyberDeckDatabase
import com.offlinecyberdeck.core.database.entity.AppSettingsEntity
import com.offlinecyberdeck.core.database.entity.ArchivedPageEntity
import com.offlinecyberdeck.core.database.entity.BookmarkEntity
import com.offlinecyberdeck.core.database.entity.LibraryItemEntity
import com.offlinecyberdeck.core.database.entity.MediaItemEntity
import com.offlinecyberdeck.core.database.entity.MusicTrackEntity
import com.offlinecyberdeck.core.database.entity.PlaylistEntity
import com.offlinecyberdeck.core.database.entity.PlaylistMediaCrossRef
import com.offlinecyberdeck.core.model.StorageDashboardStats
import com.offlinecyberdeck.core.storage.DeckDriveManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

data class CyberDeckBackupPayload(
    val formatVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = BuildConfig.VERSION_NAME,
    val settings: AppSettingsEntity? = null,
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val archivedPages: List<ArchivedPageEntity> = emptyList(),
    val libraryItems: List<LibraryItemEntity> = emptyList(),
    val mediaItems: List<MediaItemEntity> = emptyList(),
    val musicTracks: List<MusicTrackEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val playlistLinks: List<PlaylistMediaCrossRef> = emptyList()
)

class SettingsRepository(
    private val context: Context,
    private val db: CyberDeckDatabase,
    private val driveManager: DeckDriveManager
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    val settingsFlow: Flow<AppSettingsEntity> = db.settingsDao().observe().map {
        it ?: AppSettingsEntity()
    }

    suspend fun getSettings(): AppSettingsEntity {
        return db.settingsDao().get() ?: AppSettingsEntity().also {
            db.settingsDao().upsert(it)
        }
    }

    suspend fun updateSettings(transform: (AppSettingsEntity) -> AppSettingsEntity) {
        val current = getSettings()
        db.settingsDao().upsert(transform(current))
    }

    suspend fun computeStorageDashboard(): StorageDashboardStats = withContext(Dispatchers.IO) {
        val settings = getSettings()
        val driveStatus = driveManager.refreshStatus(settings.deckDriveTreeUri)
        val (internalTotal, internalAvail) = driveManager.getInternalStorageCapacity()
        val archives = db.archiveDao().getAllSnapshot()
        val library = db.libraryDao().getAllSnapshot()
        val media = db.mediaDao().getAllSnapshot()
        val downloads = db.downloadDao().getAllSnapshot()

        StorageDashboardStats(
            internalTotalBytes = internalTotal,
            internalAvailableBytes = internalAvail,
            internalAppArchivesBytes = driveManager.directorySizeBytes(driveManager.internalArchivesDir),
            internalAppDownloadsBytes = driveManager.directorySizeBytes(driveManager.internalDownloadsDir),
            archiveCount = archives.size,
            libraryCount = library.size,
            mediaLocalCount = media.count { !it.localFileUri.isNullOrBlank() },
            mediaMetadataOnlyCount = media.count { it.localFileUri.isNullOrBlank() },
            downloadCount = downloads.size,
            deckDrive = driveStatus
        )
    }

    suspend fun exportBackupToUri(targetUri: Uri, includeSettings: Boolean = true): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = CyberDeckBackupPayload(
                    settings = if (includeSettings) getSettings() else null,
                    bookmarks = db.bookmarkDao().getAllSnapshot(),
                    archivedPages = db.archiveDao().getAllSnapshot(),
                    libraryItems = db.libraryDao().getAllSnapshot(),
                    mediaItems = db.mediaDao().getAllSnapshot(),
                    musicTracks = db.musicDao().getAllSnapshot(),
                    playlists = db.mediaDao().observePlaylists().first(),
                    playlistLinks = db.mediaDao().getAllPlaylistCrossRefs()
                )
                val json = gson.toJson(payload)
                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: error("Unable to open backup destination")
                "Exported ${payload.bookmarks.size} bookmarks, ${payload.archivedPages.size} archives, ${payload.libraryItems.size} vault items, ${payload.mediaItems.size} media, ${payload.musicTracks.size} music tracks"
            }
        }

    suspend fun importBackupFromUri(sourceUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val json = context.contentResolver.openInputStream(sourceUri)?.use {
                it.bufferedReader().readText()
            } ?: error("Unable to read backup file")
            val payload = gson.fromJson(json, CyberDeckBackupPayload::class.java)
                ?: error("Invalid backup JSON")
            if (payload.formatVersion != 1) {
                error("Unsupported backup format version: ${payload.formatVersion}")
            }
            payload.settings?.let { db.settingsDao().upsert(it.copy(id = 1)) }
            payload.bookmarks.forEach { db.bookmarkDao().upsert(it) }
            payload.archivedPages.forEach { db.archiveDao().upsert(it) }
            payload.libraryItems.forEach { db.libraryDao().upsert(it) }
            payload.mediaItems.forEach { db.mediaDao().upsert(it) }
            payload.musicTracks.forEach { db.musicDao().upsert(it) }
            payload.playlists.forEach { db.mediaDao().upsertPlaylist(it) }
            payload.playlistLinks.forEach { db.mediaDao().addToPlaylist(it) }
            "Restored ${payload.bookmarks.size} bookmarks, ${payload.archivedPages.size} archives, ${payload.libraryItems.size} vault items, ${payload.mediaItems.size} media, ${payload.musicTracks.size} music tracks"
        }
    }

    suspend fun buildDiagnosticsReport(): String = withContext(Dispatchers.IO) {
        val settings = getSettings()
        val stats = computeStorageDashboard()
        buildString {
            appendLine("=== Offline Cyber Deck Diagnostics ===")
            appendLine("Note: Personal browsing URLs and history are excluded.")
            appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
            appendLine("Runtime Free Mem (MB): ${Runtime.getRuntime().freeMemory() / (1024 * 1024)} / Max (MB): ${Runtime.getRuntime().maxMemory() / (1024 * 1024)}")
            appendLine("Max Live WebViews: ${settings.maxLiveWebViews}")
            appendLine("Archive Target: ${settings.archiveStorageTarget.label}")
            appendLine("Download Target: ${settings.downloadStorageTarget.label}")
            appendLine("Deck Drive Connected: ${stats.deckDrive.isConnected}, Writable: ${stats.deckDrive.isWritable}")
            appendLine("Deck Drive Status: ${stats.deckDrive.statusDetail}")
            appendLine("Catalog Counts -> Archives: ${stats.archiveCount}, Vault: ${stats.libraryCount}, Media(Local): ${stats.mediaLocalCount}, Media(Metadata): ${stats.mediaMetadataOnlyCount}, Downloads: ${stats.downloadCount}")
        }
    }

    suspend fun exportDiagnosticsFile(): File = withContext(Dispatchers.IO) {
        val report = buildDiagnosticsReport()
        val outFile = File(context.cacheDir, "cyberdeck_diagnostics.txt")
        outFile.writeText(report, Charsets.UTF_8)
        outFile
    }
}
