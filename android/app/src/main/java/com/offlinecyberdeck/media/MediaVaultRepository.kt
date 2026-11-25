package com.offlinecyberdeck.media

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.offlinecyberdeck.core.database.dao.MediaDao
import com.offlinecyberdeck.core.database.entity.MediaItemEntity
import com.offlinecyberdeck.core.database.entity.PlaylistEntity
import com.offlinecyberdeck.core.database.entity.PlaylistMediaCrossRef
import com.offlinecyberdeck.core.model.MediaAvailability
import com.offlinecyberdeck.core.model.StorageTarget
import com.offlinecyberdeck.core.storage.DeckDriveManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

data class VideoRefreshSummary(
    val importedCount: Int,
    val skippedCount: Int
)

class MediaVaultRepository(
    private val context: Context,
    private val mediaDao: MediaDao,
    private val driveManager: DeckDriveManager
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    val mediaFlow: Flow<List<MediaItemEntity>> = mediaDao.observeAll()
    val playlistsFlow: Flow<List<PlaylistEntity>> = mediaDao.observePlaylists()

    fun observePlaylistItems(playlistId: String): Flow<List<MediaItemEntity>> =
        mediaDao.observePlaylistItems(playlistId)

    fun computeAvailability(item: MediaItemEntity): MediaAvailability {
        val uri = item.localFileUri
        if (uri.isNullOrBlank()) return MediaAvailability.METADATA_ONLY
        return if (driveManager.isUriOrFileAccessible(uri)) {
            MediaAvailability.AVAILABLE_OFFLINE
        } else {
            MediaAvailability.DRIVE_DISCONNECTED
        }
    }

    suspend fun importLocalMediaFiles(uris: List<Uri>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var imported = 0
            for (uri in uris) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                if (ingestMediaUri(uri)) imported++
            }
            imported
        }
    }

    private data class ExtractedLocalMediaInfo(
        val durationMs: Long,
        val thumbPath: String?,
        val embeddedTitle: String?,
        val channelName: String?,
        val generatedDescription: String,
        val generatedTags: String
    )

    suspend fun autoImportAndRefreshVideos(
        deckDriveTreeUri: String?,
        extraSafUris: List<String>
    ): Result<VideoRefreshSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = mediaDao.getAllSnapshot()
            val seenUris = existing.mapNotNull { it.localFileUri }.toMutableSet()
            val seenNames = existing.mapNotNull { it.localFileName?.lowercase(Locale.US) }.toMutableSet()
            var imported = 0
            var skipped = 0

            // 1. MediaStore.Video across Internal + SD Card
            val projection = buildList {
                add(MediaStore.Video.Media._ID)
                add(MediaStore.Video.Media.DISPLAY_NAME)
                add(MediaStore.Video.Media.SIZE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(MediaStore.Video.Media.VOLUME_NAME)
                }
            }.toTypedArray()

            runCatching {
                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameIdx = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    val volIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Video.Media.VOLUME_NAME)
                    } else -1

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIdx)
                        val fileName = (if (nameIdx >= 0) cursor.getString(nameIdx) else null)
                            ?.takeIf { it.isNotBlank() } ?: continue
                        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
                        if (ext !in setOf("mp4", "mkv", "webm", "mov", "avi", "m4v")) continue

                        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        val uriStr = uri.toString()
                        val normName = fileName.lowercase(Locale.US)
                        if (seenUris.contains(uriStr) || seenNames.contains(normName)) {
                            skipped++
                            continue
                        }
                        val vol = if (volIdx >= 0) cursor.getString(volIdx).orEmpty() else ""
                        val folderHint = if (vol.isBlank() || vol.equals("external_primary", true)) {
                            "Internal"
                        } else {
                            "SD / USB ($vol)"
                        }
                        if (ingestMediaUri(uri, folderHint = folderHint, seenNames = seenNames, seenUris = seenUris)) {
                            imported++
                        } else {
                            skipped++
                        }
                    }
                }
            }

            // 2. Direct filesystem scan on Internal Shared Storage, Downloads, and Removable volumes
            val dirsToScan = linkedMapOf<File, String>()
            dirsToScan[driveManager.internalDownloadsDir] = "App Downloads"
            runCatching { Environment.getExternalStorageDirectory() }.getOrNull()?.let { extDir ->
                if (extDir.exists() && extDir.canRead()) dirsToScan[extDir] = "Internal"
            }
            driveManager.detectRemovableExternalVolumes().forEach { vol ->
                val mp = vol.mountPath?.let { File(it) }
                if (mp != null && mp.exists() && mp.canRead()) {
                    dirsToScan[mp] = vol.label
                }
            }
            for ((dir, label) in dirsToScan) {
                val queue = ArrayDeque<Pair<File, Int>>()
                queue.add(dir to 0)
                var visited = 0
                while (queue.isNotEmpty() && visited < 220) {
                    val (cur, depth) = queue.removeFirst()
                    visited++
                    val children = runCatching { cur.listFiles() }.getOrNull() ?: continue
                    for (child in children) {
                        val name = child.name
                        if (name.startsWith(".")) continue
                        if (child.isDirectory) {
                            if (depth < 5 && name != "Android" && name != "cache") {
                                queue.add(child to depth + 1)
                            }
                        } else if (child.isFile && isSupportedVideoFile(name)) {
                            val pathStr = child.absolutePath
                            val normName = name.lowercase(Locale.US)
                            if (seenUris.contains(pathStr) || seenNames.contains(normName)) {
                                skipped++
                            } else {
                                val fileUri = Uri.fromFile(child)
                                if (ingestMediaUri(fileUri, rawPathOverride = pathStr, folderHint = cur.name.ifBlank { label }, seenNames = seenNames, seenUris = seenUris)) {
                                    imported++
                                } else {
                                    skipped++
                                }
                            }
                        }
                    }
                }
            }

            // 3. SAF Trees (Deck Drive + extra SAF folders)
            val safTrees = (listOfNotNull(deckDriveTreeUri) + extraSafUris)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            for (treeStr in safTrees) {
                runCatching {
                    val treeUri = Uri.parse(treeStr)
                    val root = DocumentFile.fromTreeUri(context, treeUri)
                    if (root != null && root.exists() && root.canRead()) {
                        val q = ArrayDeque<Pair<DocumentFile, String>>()
                        q.add(root to (root.name ?: "External Drive"))
                        var visited = 0
                        while (q.isNotEmpty() && visited < 150) {
                            val (d, label) = q.removeFirst()
                            visited++
                            for (child in d.listFiles()) {
                                if (child.isDirectory) {
                                    q.add(child to (child.name ?: label))
                                } else if (child.isFile && isSupportedVideoFile(child.name.orEmpty())) {
                                    val cUriStr = child.uri.toString()
                                    val cNormName = child.name.orEmpty().lowercase(Locale.US)
                                    if (seenUris.contains(cUriStr) || seenNames.contains(cNormName)) {
                                        skipped++
                                    } else if (ingestMediaUri(child.uri, folderHint = label, seenNames = seenNames, seenUris = seenUris)) {
                                        imported++
                                    } else {
                                        skipped++
                                    }
                                }
                            }
                        }
                    }
                }
            }

            VideoRefreshSummary(importedCount = imported, skippedCount = skipped)
        }
    }

    suspend fun scanSafFolderForMedia(treeUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: error("Cannot access selected folder")
            val existing = mediaDao.getAllSnapshot()
            val seenUris = existing.mapNotNull { it.localFileUri }.toMutableSet()
            val seenNames = existing.mapNotNull { it.localFileName?.lowercase(Locale.US) }.toMutableSet()
            var count = 0
            val queue = ArrayDeque<Pair<DocumentFile, String>>()
            queue.add(root to (root.name ?: "Local Folder"))
            var visitedDirs = 0
            while (queue.isNotEmpty() && visitedDirs < 150) {
                val (dir, folderLabel) = queue.removeFirst()
                visitedDirs++
                for (child in dir.listFiles()) {
                    if (child.isDirectory) {
                        queue.add(child to (child.name ?: folderLabel))
                    } else if (child.isFile && isSupportedMediaFile(child.name.orEmpty())) {
                        if (ingestMediaUri(child.uri, folderHint = folderLabel, seenNames = seenNames, seenUris = seenUris)) {
                            count++
                        }
                    }
                }
            }
            count
        }
    }

    private suspend fun ingestMediaUri(
        uri: Uri,
        folderHint: String? = null,
        rawPathOverride: String? = null,
        seenNames: MutableSet<String>? = null,
        seenUris: MutableSet<String>? = null
    ): Boolean {
        val uriStr = rawPathOverride ?: uri.toString()
        if (seenUris != null && seenUris.contains(uriStr)) return false
        val existingByUri = mediaDao.findByLocalUri(uriStr)
        if (existingByUri != null) {
            seenUris?.add(uriStr)
            existingByUri.localFileName?.lowercase(Locale.US)?.let { seenNames?.add(it) }
            return false
        }

        val (fileName, sizeBytes) = driveManager.queryDisplayNameAndSize(uri)
        val normName = fileName.lowercase(Locale.US)
        if (seenNames != null && seenNames.contains(normName)) return false
        val existingAll = mediaDao.getAllSnapshot()
        if (existingAll.any { it.localFileName?.equals(fileName, ignoreCase = true) == true && !it.localFileUri.isNullOrBlank() }) {
            seenNames?.add(normName)
            seenUris?.add(uriStr)
            return false
        }
        seenNames?.add(normName)
        seenUris?.add(uriStr)

        val cleanTitle = cleanTitleFromFileName(fileName)
        val extractedVideoId = extractVideoIdFromString(fileName)
        val storageTarget = if (uriStr.contains("external", ignoreCase = true) ||
            uri.authority?.contains("externalstorage") == true ||
            (rawPathOverride != null && !rawPathOverride.contains("/emulated/") && !rawPathOverride.startsWith(context.filesDir.absolutePath))
        ) StorageTarget.DECK_DRIVE else StorageTarget.INTERNAL
        val extracted = extractMediaMetadataAndThumb(uri, fileName, sizeBytes, storageTarget, folderHint)
        val resolvedTitle = extracted.embeddedTitle?.takeIf { it.isNotBlank() } ?: cleanTitle
        val resolvedChannel = extracted.channelName ?: folderHint ?: "Local Library"

        // Attempt match against existing metadata-only entries
        val allItems = mediaDao.getAllSnapshot().filter { it.localFileUri.isNullOrBlank() }
        var bestMatch: MediaItemEntity? = null
        var bestScore = 0f

        if (extractedVideoId != null) {
            bestMatch = allItems.firstOrNull { it.videoId == extractedVideoId }
            if (bestMatch != null) bestScore = 1.0f
        }
        if (bestMatch == null) {
            for (candidate in allItems) {
                val score = titleSimilarity(resolvedTitle, candidate.title)
                if (score > bestScore && score >= 0.45f) {
                    bestScore = score
                    bestMatch = candidate
                }
            }
        }

        if (bestMatch != null) {
            mediaDao.upsert(
                bestMatch.copy(
                    localFileUri = uriStr,
                    localFileName = fileName,
                    channel = bestMatch.channel.ifBlank { resolvedChannel },
                    description = bestMatch.description.ifBlank { extracted.generatedDescription },
                    tags = bestMatch.tags.ifBlank { extracted.generatedTags },
                    durationMs = if (extracted.durationMs > 0L) extracted.durationMs else bestMatch.durationMs,
                    sizeBytes = sizeBytes.coerceAtLeast(0L),
                    thumbnailPath = extracted.thumbPath ?: bestMatch.thumbnailPath,
                    storageTarget = storageTarget,
                    matchConfidence = bestScore,
                    matchConfirmed = bestScore >= 0.99f
                )
            )
            return true
        }

        mediaDao.upsert(
            MediaItemEntity(
                id = UUID.randomUUID().toString(),
                title = resolvedTitle,
                channel = resolvedChannel,
                videoId = extractedVideoId,
                description = extracted.generatedDescription,
                thumbnailPath = extracted.thumbPath,
                localFileUri = uriStr,
                localFileName = fileName,
                durationMs = extracted.durationMs,
                sizeBytes = sizeBytes.coerceAtLeast(0L),
                storageTarget = storageTarget,
                matchConfidence = 1.0f,
                matchConfirmed = true,
                tags = extracted.generatedTags
            )
        )
        return true
    }

    suspend fun savePublicWebMetadata(
        title: String,
        channel: String,
        videoId: String?,
        sourceUrl: String,
        description: String
    ): MediaItemEntity {
        val existing = if (!videoId.isNullOrBlank()) mediaDao.findByVideoId(videoId) else null
        if (existing != null) {
            val updated = existing.copy(
                title = title.ifBlank { existing.title },
                channel = channel.ifBlank { existing.channel },
                sourceUrl = sourceUrl,
                description = description.ifBlank { existing.description }
            )
            mediaDao.upsert(updated)
            return updated
        }

        // Check if an unlinked local file matches this newly cataloged metadata
        val unlinkedLocal = mediaDao.getAllSnapshot().filter { !it.localFileUri.isNullOrBlank() && it.sourceUrl.isNullOrBlank() }
        var matchedLocal: MediaItemEntity? = null
        var score = 0f
        if (!videoId.isNullOrBlank()) {
            matchedLocal = unlinkedLocal.firstOrNull { it.videoId == videoId }
            if (matchedLocal != null) score = 1.0f
        }
        if (matchedLocal == null) {
            for (local in unlinkedLocal) {
                val s = titleSimilarity(title, local.title)
                if (s > score && s >= 0.55f) {
                    score = s
                    matchedLocal = local
                }
            }
        }

        val entry = if (matchedLocal != null) {
            matchedLocal.copy(
                title = title.ifBlank { matchedLocal.title },
                channel = channel,
                videoId = videoId ?: matchedLocal.videoId,
                sourceUrl = sourceUrl,
                description = description,
                matchConfidence = score,
                matchConfirmed = score >= 0.99f
            )
        } else {
            MediaItemEntity(
                id = UUID.randomUUID().toString(),
                title = title.ifBlank { sourceUrl },
                channel = channel,
                videoId = videoId,
                sourceUrl = sourceUrl,
                description = description
            )
        }
        mediaDao.upsert(entry)
        return entry
    }

    suspend fun linkLocalFileToMetadata(metadataId: String, localFileUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        localFileUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val item = mediaDao.getById(metadataId) ?: error("Metadata record not found")
                val (fileName, size) = driveManager.queryDisplayNameAndSize(localFileUri)
                val extracted = extractMediaMetadataAndThumb(localFileUri, fileName, size, item.storageTarget, null)
                mediaDao.upsert(
                    item.copy(
                        localFileUri = localFileUri.toString(),
                        localFileName = fileName,
                        channel = item.channel.ifBlank { extracted.channelName ?: "Local Library" },
                        description = item.description.ifBlank { extracted.generatedDescription },
                        tags = item.tags.ifBlank { extracted.generatedTags },
                        durationMs = if (extracted.durationMs > 0L) extracted.durationMs else item.durationMs,
                        sizeBytes = size.coerceAtLeast(0L),
                        thumbnailPath = extracted.thumbPath ?: item.thumbnailPath,
                        matchConfidence = 1.0f,
                        matchConfirmed = true
                    )
                )
            }
        }

    suspend fun unlinkLocalFile(item: MediaItemEntity) {
        mediaDao.upsert(
            item.copy(
                localFileUri = null,
                localFileName = null,
                matchConfidence = 0f,
                matchConfirmed = false
            )
        )
    }

    suspend fun confirmMatch(item: MediaItemEntity) {
        mediaDao.upsert(item.copy(matchConfirmed = true))
    }

    suspend fun updatePlaybackProgress(id: String, positionMs: Long, incrementPlayCount: Boolean) {
        val item = mediaDao.getById(id) ?: return
        mediaDao.upsert(
            item.copy(
                watchedPositionMs = positionMs.coerceAtLeast(0L),
                playCount = if (incrementPlayCount) item.playCount + 1 else item.playCount,
                lastPlayedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateMediaItem(item: MediaItemEntity) {
        mediaDao.upsert(item)
    }

    suspend fun deleteMediaItem(item: MediaItemEntity) {
        item.thumbnailPath?.let { path ->
            runCatching { File(path).delete() }
        }
        mediaDao.delete(item)
    }

    suspend fun createPlaylist(name: String, description: String = "") {
        if (name.isBlank()) return
        mediaDao.upsertPlaylist(
            PlaylistEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                description = description.trim()
            )
        )
    }

    suspend fun deletePlaylist(playlistId: String) {
        mediaDao.deletePlaylist(playlistId)
    }

    suspend fun addMediaToPlaylist(playlistId: String, mediaId: String) {
        mediaDao.addToPlaylist(PlaylistMediaCrossRef(playlistId, mediaId, 0))
    }

    suspend fun removeMediaFromPlaylist(playlistId: String, mediaId: String) {
        mediaDao.removeFromPlaylist(playlistId, mediaId)
    }

    suspend fun exportMetadataCatalog(targetUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val items = mediaDao.getAllSnapshot()
            val json = gson.toJson(items)
            context.contentResolver.openOutputStream(targetUri)?.use {
                it.write(json.toByteArray(Charsets.UTF_8))
                it.flush()
            } ?: error("Cannot write catalog file")
            items.size
        }
    }

    suspend fun importMetadataCatalog(sourceUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val json = context.contentResolver.openInputStream(sourceUri)?.use {
                it.bufferedReader().readText()
            } ?: error("Cannot read catalog file")
            val type = object : TypeToken<List<MediaItemEntity>>() {}.type
            val list: List<MediaItemEntity> = gson.fromJson(json, type) ?: emptyList()
            for (item in list) {
                mediaDao.upsert(item)
            }
            list.size
        }
    }

    private fun extractMediaMetadataAndThumb(
        uri: Uri,
        fileName: String,
        sizeBytes: Long,
        storageTarget: StorageTarget,
        folderHint: String?
    ): ExtractedLocalMediaInfo {
        val retriever = MediaMetadataRetriever()
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        return try {
            retriever.setDataSource(context, uri)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durStr?.toLongOrNull() ?: 0L
            val embeddedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)?.trim()
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.trim()
            val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.trim()

            var thumbPath: String? = null
            val isAudioOnly = ext in setOf("mp3", "m4a", "ogg", "opus", "wav", "flac")
            if (!isAudioOnly) {
                val captureUs = if (durationMs > 4000L) {
                    minOf(durationMs / 4L, 3000L) * 1000L
                } else {
                    500_000L
                }
                val frame = retriever.getFrameAtTime(captureUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val scaled = Bitmap.createScaledBitmap(frame, 480, 270, true)
                    if (scaled != frame) frame.recycle()
                    val thumbFile = File(driveManager.internalThumbnailsDir, "${UUID.randomUUID()}.jpg")
                    thumbFile.outputStream().use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 78, out)
                    }
                    scaled.recycle()
                    thumbPath = thumbFile.absolutePath
                }
            }

            val resolutionStr = if (width != null && height != null && width > 0 && height > 0) {
                "${width}x${height} (${height}p)"
            } else null

            val bitrateStr = if (bitrate != null && bitrate > 0L) {
                String.format(Locale.US, "%.1f Mbps", bitrate / 1_000_000.0)
            } else null

            val generatedDesc = buildString {
                appendLine("Local offline media file: $fileName")
                append("Storage: ${storageTarget.label}")
                if (sizeBytes > 0L) append(" • Size: ${com.offlinecyberdeck.core.format.Formatters.formatBytes(sizeBytes)}")
                appendLine()
                if (resolutionStr != null) append("Resolution: $resolutionStr")
                if (bitrateStr != null) append(if (resolutionStr != null) " • Bitrate: $bitrateStr" else "Bitrate: $bitrateStr")
                if (mime != null) append(" • Format: $mime")
                if (!date.isNullOrBlank()) append("\nRecorded/Tagged: $date")
            }.trim()

            val tagParts = mutableListOf<String>()
            if (ext.isNotBlank()) tagParts += ext
            if (height != null && height > 0) tagParts += "${height}p"
            if (!folderHint.isNullOrBlank()) tagParts += folderHint
            tagParts += if (isAudioOnly) "offline-audio" else "offline-video"

            ExtractedLocalMediaInfo(
                durationMs = durationMs,
                thumbPath = thumbPath,
                embeddedTitle = embeddedTitle,
                channelName = artist?.takeIf { it.isNotBlank() },
                generatedDescription = generatedDesc,
                generatedTags = tagParts.distinct().joinToString(", ")
            )
        } catch (_: Throwable) {
            ExtractedLocalMediaInfo(
                durationMs = 0L,
                thumbPath = null,
                embeddedTitle = null,
                channelName = null,
                generatedDescription = "Local media file: $fileName (${storageTarget.label})",
                generatedTags = if (ext.isNotBlank()) "$ext, offline-media" else "offline-media"
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun isSupportedVideoFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        return ext in setOf("mp4", "mkv", "webm", "mov", "avi", "m4v")
    }

    private fun isSupportedMediaFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        return ext in setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "mp3", "m4a", "ogg", "opus", "wav", "flac")
    }

    private fun cleanTitleFromFileName(name: String): String {
        return name.substringBeforeLast('.')
            .replace(Regex("\\[[A-Za-z0-9_-]{11}]"), "")
            .replace('_', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { name }
    }

    fun extractVideoIdFromString(input: String): String? {
        val bracketed = Regex("\\[([A-Za-z0-9_-]{11})]").find(input)?.groupValues?.getOrNull(1)
        if (bracketed != null) return bracketed
        val urlParam = Regex("[?&]v=([A-Za-z0-9_-]{11})").find(input)?.groupValues?.getOrNull(1)
        if (urlParam != null) return urlParam
        val shortUrl = Regex("youtu\\.be/([A-Za-z0-9_-]{11})").find(input)?.groupValues?.getOrNull(1)
        return shortUrl
    }

    fun titleSimilarity(a: String, b: String): Float {
        val tokensA = tokenize(a)
        val tokensB = tokenize(b)
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0f
        val intersection = tokensA.intersect(tokensB).size.toFloat()
        val union = tokensA.union(tokensB).size.toFloat()
        return if (union == 0f) 0f else (intersection / union).coerceIn(0f, 0.95f)
    }

    private fun tokenize(text: String): Set<String> {
        return text.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
            .toSet()
    }
}
