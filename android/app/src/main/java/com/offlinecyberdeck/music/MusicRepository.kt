package com.offlinecyberdeck.music

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.offlinecyberdeck.core.database.dao.MusicDao
import com.offlinecyberdeck.core.database.entity.MusicTrackEntity
import com.offlinecyberdeck.core.model.StorageTarget
import com.offlinecyberdeck.core.storage.DeckDriveManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

data class MusicRefreshSummary(
    val importedCount: Int,
    val skippedCount: Int
)

class MusicRepository(
    private val context: Context,
    private val musicDao: MusicDao,
    private val driveManager: DeckDriveManager
) {
    private val audioExtensions = setOf("mp3", "m4a", "flac", "wav", "ogg", "opus", "aac", "wma")

    val tracksFlow: Flow<List<MusicTrackEntity>> = musicDao.observeAll()

    fun isTrackAccessible(track: MusicTrackEntity): Boolean =
        driveManager.isUriOrFileAccessible(track.fileUri)

    suspend fun toggleFavorite(track: MusicTrackEntity) {
        musicDao.upsert(track.copy(isFavorite = !track.isFavorite))
    }

    suspend fun recordPlay(track: MusicTrackEntity) {
        musicDao.upsert(
            track.copy(
                playCount = track.playCount + 1,
                lastPlayedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteTrack(track: MusicTrackEntity) {
        musicDao.delete(track)
    }

    suspend fun importPickedAudioFiles(uris: List<Uri>): Result<MusicRefreshSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val existing = musicDao.getAllSnapshot()
                val seenUris = existing.mapTo(HashSet()) { it.fileUri }
                val seenNames = existing.mapTo(HashSet()) { it.fileName.lowercase(Locale.US) }

                val toInsert = mutableListOf<MusicTrackEntity>()
                var skipped = 0

                for (uri in uris) {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    val uriStr = uri.toString()
                    val (fileName, sizeBytes) = driveManager.queryDisplayNameAndSize(uri)
                    val normName = fileName.lowercase(Locale.US)
                    if (seenUris.contains(uriStr) || seenNames.contains(normName)) {
                        skipped++
                        continue
                    }
                    val target = if (uriStr.contains("external", true)) StorageTarget.DECK_DRIVE else StorageTarget.INTERNAL
                    val volumeLabel = if (target == StorageTarget.DECK_DRIVE) "External" else "Internal"
                    val track = extractTrackFromUri(uri, uriStr, fileName, sizeBytes, target, volumeLabel)
                    seenUris += uriStr
                    seenNames += normName
                    toInsert += track
                }

                if (toInsert.isNotEmpty()) {
                    musicDao.upsertAll(toInsert)
                }
                MusicRefreshSummary(importedCount = toInsert.size, skippedCount = skipped)
            }
        }

    suspend fun scanSafFolderForMusic(treeUri: Uri): Result<MusicRefreshSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val existing = musicDao.getAllSnapshot()
                val seenUris = existing.mapTo(HashSet()) { it.fileUri }
                val seenNames = existing.mapTo(HashSet()) { it.fileName.lowercase(Locale.US) }
                val toInsert = mutableListOf<MusicTrackEntity>()
                var skipped = 0

                scanSafTreeInternal(
                    treeUri = treeUri,
                    seenUris = seenUris,
                    seenNames = seenNames,
                    target = StorageTarget.DECK_DRIVE,
                    volumeLabel = "External Drive",
                    out = toInsert,
                    onSkipped = { skipped++ }
                )

                if (toInsert.isNotEmpty()) {
                    musicDao.upsertAll(toInsert)
                }
                MusicRefreshSummary(importedCount = toInsert.size, skippedCount = skipped)
            }
        }

    suspend fun autoImportAndRefresh(
        deckDriveTreeUri: String?,
        extraSafUris: List<String>
    ): Result<MusicRefreshSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = musicDao.getAllSnapshot()
            val seenUris = existing.mapTo(HashSet()) { it.fileUri }
            val seenNames = existing.mapTo(HashSet()) { it.fileName.lowercase(Locale.US) }
            val toInsert = mutableListOf<MusicTrackEntity>()
            var skipped = 0

            // 1. MediaStore.Audio (Internal Shared Storage + SD Card volumes indexed by Android)
            scanMediaStoreAudio(
                seenUris = seenUris,
                seenNames = seenNames,
                out = toInsert,
                onSkipped = { skipped++ }
            )

            // 2. Direct filesystem scan on Internal Shared Storage, App Downloads, and Removable SD/USB mount paths
            val directDirs = linkedMapOf<File, Pair<StorageTarget, String>>()
            directDirs[driveManager.internalDownloadsDir] = StorageTarget.INTERNAL to "App Downloads"
            runCatching { Environment.getExternalStorageDirectory() }.getOrNull()?.let { extDir ->
                if (extDir.exists() && extDir.canRead()) {
                    directDirs[extDir] = StorageTarget.INTERNAL to "Internal"
                }
            }
            driveManager.detectRemovableExternalVolumes().forEach { vol ->
                val mp = vol.mountPath?.let { File(it) }
                if (mp != null && mp.exists() && mp.canRead()) {
                    directDirs[mp] = StorageTarget.DECK_DRIVE to vol.label
                }
            }

            for ((dir, meta) in directDirs) {
                scanDirectoryFiles(
                    rootDir = dir,
                    target = meta.first,
                    volumeLabel = meta.second,
                    seenUris = seenUris,
                    seenNames = seenNames,
                    out = toInsert,
                    onSkipped = { skipped++ }
                )
            }

            // 3. Persisted SAF Trees (Deck Drive + extra user-granted SD/USB music folders)
            val safTrees = (listOfNotNull(deckDriveTreeUri) + extraSafUris)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            for (treeStr in safTrees) {
                runCatching {
                    val uri = Uri.parse(treeStr)
                    val doc = DocumentFile.fromTreeUri(context, uri)
                    if (doc != null && doc.exists() && doc.canRead()) {
                        scanSafTreeInternal(
                            treeUri = uri,
                            seenUris = seenUris,
                            seenNames = seenNames,
                            target = StorageTarget.DECK_DRIVE,
                            volumeLabel = doc.name ?: "External Drive",
                            out = toInsert,
                            onSkipped = { skipped++ }
                        )
                    }
                }
            }

            if (toInsert.isNotEmpty()) {
                musicDao.upsertAll(toInsert)
            }
            MusicRefreshSummary(importedCount = toInsert.size, skippedCount = skipped)
        }
    }

    private fun scanMediaStoreAudio(
        seenUris: MutableSet<String>,
        seenNames: MutableSet<String>,
        out: MutableList<MusicTrackEntity>,
        onSkipped: () -> Unit
    ) {
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.SIZE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.VOLUME_NAME)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Audio.Media.DATA)
            }
        }.toTypedArray()

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleIdx = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistIdx = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumIdx = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val volIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.VOLUME_NAME)
                } else -1
                val dataIdx = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val fileName = (if (nameIdx >= 0) cursor.getString(nameIdx) else null)
                        ?.takeIf { it.isNotBlank() } ?: continue
                    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
                    if (ext !in audioExtensions) continue

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val uriStr = contentUri.toString()
                    val normName = fileName.lowercase(Locale.US)
                    if (seenUris.contains(uriStr) || seenNames.contains(normName)) {
                        onSkipped()
                        continue
                    }

                    val sizeBytes = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                    val durMs = if (durIdx >= 0 && !cursor.isNull(durIdx)) cursor.getLong(durIdx) else 0L
                    if (sizeBytes in 1..24_000L && ext != "mp3") continue // Skip tiny notification tones

                    val rawTitle = if (titleIdx >= 0) cursor.getString(titleIdx)?.trim() else null
                    val rawArtist = if (artistIdx >= 0) cursor.getString(artistIdx)?.trim() else null
                    val rawAlbum = if (albumIdx >= 0) cursor.getString(albumIdx)?.trim() else null

                    val volName = if (volIdx >= 0) cursor.getString(volIdx).orEmpty() else {
                        val path = if (dataIdx >= 0) cursor.getString(dataIdx).orEmpty() else ""
                        if (path.contains("/storage/emulated")) "external_primary" else "external"
                    }
                    val isPrimary = volName.equals("external_primary", ignoreCase = true) || volName.isBlank()

                    seenUris += uriStr
                    seenNames += normName
                    out += MusicTrackEntity(
                        id = UUID.randomUUID().toString(),
                        title = rawTitle?.takeIf { it.isNotBlank() } ?: cleanAudioTitle(fileName),
                        artist = rawArtist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown Artist",
                        album = rawAlbum?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Local Music",
                        fileName = fileName,
                        fileUri = uriStr,
                        durationMs = durMs.coerceAtLeast(0L),
                        sizeBytes = sizeBytes.coerceAtLeast(0L),
                        storageTarget = if (isPrimary) StorageTarget.INTERNAL else StorageTarget.DECK_DRIVE,
                        volumeLabel = if (isPrimary) "Internal" else "SD / USB ($volName)"
                    )
                }
            }
        }
    }

    private fun scanDirectoryFiles(
        rootDir: File,
        target: StorageTarget,
        volumeLabel: String,
        seenUris: MutableSet<String>,
        seenNames: MutableSet<String>,
        out: MutableList<MusicTrackEntity>,
        onSkipped: () -> Unit
    ) {
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(rootDir to 0)
        var visitedDirs = 0
        while (queue.isNotEmpty() && visitedDirs < 260) {
            val (dir, depth) = queue.removeFirst()
            visitedDirs++
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                val name = child.name
                if (name.startsWith(".")) continue
                if (child.isDirectory) {
                    if (depth < 6 && name !in setOf("Android", "Notifications", "Ringtones", "Alarms", "cache")) {
                        queue.add(child to depth + 1)
                    }
                } else if (child.isFile) {
                    val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
                    if (ext in audioExtensions) {
                        val pathStr = child.absolutePath
                        val normName = name.lowercase(Locale.US)
                        if (seenUris.contains(pathStr) || seenNames.contains(normName)) {
                            onSkipped()
                            continue
                        }
                        val track = extractTrackFromFile(child, target, volumeLabel)
                        seenUris += pathStr
                        seenNames += normName
                        out += track
                    }
                }
            }
        }
    }

    private fun scanSafTreeInternal(
        treeUri: Uri,
        seenUris: MutableSet<String>,
        seenNames: MutableSet<String>,
        target: StorageTarget,
        volumeLabel: String,
        out: MutableList<MusicTrackEntity>,
        onSkipped: () -> Unit
    ) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return
        val queue = ArrayDeque<DocumentFile>()
        queue.add(root)
        var visitedDirs = 0
        while (queue.isNotEmpty() && visitedDirs < 180) {
            val dir = queue.removeFirst()
            visitedDirs++
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                val name = child.name ?: continue
                if (name.startsWith(".")) continue
                if (child.isDirectory) {
                    queue.add(child)
                } else if (child.isFile) {
                    val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
                    if (ext in audioExtensions) {
                        val uriStr = child.uri.toString()
                        val normName = name.lowercase(Locale.US)
                        if (seenUris.contains(uriStr) || seenNames.contains(normName)) {
                            onSkipped()
                            continue
                        }
                        val track = extractTrackFromUri(
                            uri = child.uri,
                            uriStr = uriStr,
                            fileName = name,
                            sizeBytes = child.length(),
                            target = target,
                            volumeLabel = volumeLabel
                        )
                        seenUris += uriStr
                        seenNames += normName
                        out += track
                    }
                }
            }
        }
    }

    private fun extractTrackFromFile(
        file: File,
        target: StorageTarget,
        volumeLabel: String
    ): MusicTrackEntity {
        val retriever = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durMs = 0L
        try {
            retriever.setDataSource(file.absolutePath)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()
            durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
        } finally {
            runCatching { retriever.release() }
        }
        return MusicTrackEntity(
            id = UUID.randomUUID().toString(),
            title = title?.takeIf { it.isNotBlank() } ?: cleanAudioTitle(file.name),
            artist = artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
            album = album?.takeIf { it.isNotBlank() } ?: file.parentFile?.name ?: "Local Music",
            fileName = file.name,
            fileUri = file.absolutePath,
            durationMs = durMs.coerceAtLeast(0L),
            sizeBytes = file.length().coerceAtLeast(0L),
            storageTarget = target,
            volumeLabel = volumeLabel
        )
    }

    private fun extractTrackFromUri(
        uri: Uri,
        uriStr: String,
        fileName: String,
        sizeBytes: Long,
        target: StorageTarget,
        volumeLabel: String
    ): MusicTrackEntity {
        val retriever = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durMs = 0L
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()
            durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
        } finally {
            runCatching { retriever.release() }
        }
        return MusicTrackEntity(
            id = UUID.randomUUID().toString(),
            title = title?.takeIf { it.isNotBlank() } ?: cleanAudioTitle(fileName),
            artist = artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
            album = album?.takeIf { it.isNotBlank() } ?: "Local Music",
            fileName = fileName,
            fileUri = uriStr,
            durationMs = durMs.coerceAtLeast(0L),
            sizeBytes = sizeBytes.coerceAtLeast(0L),
            storageTarget = target,
            volumeLabel = volumeLabel
        )
    }

    private fun cleanAudioTitle(fileName: String): String =
        fileName.substringBeforeLast('.')
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { fileName }
}
