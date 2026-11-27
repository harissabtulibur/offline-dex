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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

data class MusicRefreshSummary(
    val importedCount: Int,
    val skippedCount: Int,
    val removedCount: Int = 0
)

class MusicRepository(
    private val context: Context,
    private val musicDao: MusicDao,
    private val driveManager: DeckDriveManager
) {
    private val audioExtensions = setOf("mp3", "m4a", "flac", "wav", "ogg", "opus", "aac", "wma")
    private val refreshMutex = Mutex()

    val tracksFlow: Flow<List<MusicTrackEntity>> = musicDao.observeAll()

    fun isTrackAccessible(track: MusicTrackEntity): Boolean {
        val ref = track.fileUri
        if (ref.isBlank()) return false
        if (ref.startsWith("content://")) {
            if (driveManager.isUriOrFileAccessible(ref)) return true
            val parsed = Uri.parse(ref)
            if (driveManager.resolveDirectFileFromContentUri(parsed)?.let { isDirectFileReadable(it) } == true) {
                return true
            }
            return findMediaStoreUriForTrack(track) != null
        }
        val path = if (ref.startsWith("file://")) Uri.parse(ref).path.orEmpty() else ref
        val file = File(path)
        return isDirectFileReadable(file) || findMediaStoreUriForTrack(track) != null
    }

    private fun isDirectFileReadable(file: File): Boolean {
        if (!file.exists() || !file.isFile || !file.canRead()) return false
        return runCatching {
            FileInputStream(file).use { fis ->
                fis.read()
                true
            }
        }.getOrDefault(false)
    }

    private fun findMediaStoreUriForTrack(track: MusicTrackEntity): Uri? {
        val candidates = buildList {
            add(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { MediaStore.getExternalVolumeNames(context) }.getOrNull().orEmpty().forEach { vol ->
                    val volUri = MediaStore.Audio.Media.getContentUri(vol)
                    if (volUri != MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) add(volUri)
                }
            }
        }
        val proj = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DISPLAY_NAME)
        val sel = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
        val args = arrayOf(track.fileName)
        for (collection in candidates) {
            val match = runCatching {
                context.contentResolver.query(collection, proj, sel, args, null)?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val sizeIdx = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                    var fallbackId: Long? = null
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIdx)
                        val sz = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                        if (track.sizeBytes > 0L && sz == track.sizeBytes) {
                            return@use ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                                .takeIf { driveManager.isUriOrFileAccessible(it.toString()) }
                                ?: ContentUris.withAppendedId(collection, id)
                        }
                        if (fallbackId == null) fallbackId = id
                    }
                    fallbackId?.let { id ->
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                            .takeIf { driveManager.isUriOrFileAccessible(it.toString()) }
                            ?: ContentUris.withAppendedId(collection, id)
                    }
                }
            }.getOrNull()
            if (match != null && driveManager.isUriOrFileAccessible(match.toString())) {
                return match
            }
        }
        return null
    }

    private fun isSeekableContentUri(uri: Uri): Boolean {
        val afdOk = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length != 0L
            } ?: false
        }.getOrDefault(false)
        if (afdOk) return true
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                pfd.statSize != 0L
            } ?: false
        }.getOrDefault(false)
    }

    private fun findSafUriInTree(treeUri: Uri, fileName: String, sizeBytes: Long): Uri? {
        val root = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull() ?: return null
        val queue = ArrayDeque<DocumentFile>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 1500) {
            val dir = queue.removeFirst()
            visited++
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                val name = child.name ?: continue
                if (name.startsWith(".")) continue
                if (child.isDirectory) {
                    if (name.lowercase(Locale.US) !in setOf("android", "cache", "lost.dir")) {
                        queue.add(child)
                    }
                } else if (child.isFile && name.equals(fileName, ignoreCase = true)) {
                    val cLen = child.length()
                    if (sizeBytes <= 0L || cLen <= 0L || cLen == sizeBytes) {
                        return child.uri
                    }
                }
            }
        }
        return null
    }

    private fun collectSafTreeStrings(deckDriveTreeUri: String?, extraSafUris: List<String>): List<String> {
        val persistedTreeUris = runCatching {
            context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { it.uri.toString() }
        }.getOrDefault(emptyList())
        return (listOfNotNull(deckDriveTreeUri) + extraSafUris + persistedTreeUris)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun canOpenContentUriStreamOrAfd(uri: Uri): Boolean {
        val afdOk = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
        if (afdOk) return true
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        }.getOrDefault(false)
    }

    suspend fun resolvePlayableCandidates(
        track: MusicTrackEntity,
        deckDriveTreeUri: String?,
        extraSafUris: List<String>,
        includeDeepSafSearch: Boolean = false
    ): List<Uri> = withContext(Dispatchers.IO) {
        val candidates = LinkedHashSet<Uri>()
        val raw = track.fileUri.trim()

        // 1. Primary stored content:// URI FIRST (matches OfflineTube reliable ExoPlayer playback!)
        if (raw.startsWith("content://")) {
            val parsed = Uri.parse(raw)
            if (parsed.authority == MediaStore.AUTHORITY) {
                val id = runCatching { ContentUris.parseId(parsed) }.getOrDefault(-1L)
                if (id > 0L) {
                    val canonical = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    if (canOpenContentUriStreamOrAfd(canonical)) {
                        candidates += canonical
                    }
                }
            }
            if (canOpenContentUriStreamOrAfd(parsed) || driveManager.isUriOrFileAccessible(raw)) {
                candidates += parsed
            }
        }

        // 2. MediaStore lookup by fileName + size (fast SQLite query; rescues legacy file-path records on SD/OTG too)
        findMediaStoreUriForTrack(track)?.let { resolved ->
            candidates += resolved
        }

        // 3. Direct POSIX file candidate (primary for imported_music/, secondary fallback for /storage/...)
        if (raw.isNotBlank() && !raw.startsWith("content://")) {
            val path = if (raw.startsWith("file://")) Uri.parse(raw).path.orEmpty() else raw
            val file = File(path)
            if (isDirectFileReadable(file)) {
                candidates += Uri.fromFile(file)
            }
        } else if (raw.startsWith("content://")) {
            driveManager.resolveDirectFileFromContentUri(Uri.parse(raw))
                ?.takeIf { isDirectFileReadable(it) }
                ?.let { candidates += Uri.fromFile(it) }
        }

        // 4. Configured SAF trees + persisted SAF folder permissions (only traverse 1500 dirs when needed)
        if (includeDeepSafSearch || candidates.isEmpty()) {
            val trees = collectSafTreeStrings(deckDriveTreeUri, extraSafUris)
            for (treeStr in trees) {
                val safUri = findSafUriInTree(Uri.parse(treeStr), track.fileName, track.sizeBytes)
                if (safUri != null && (canOpenContentUriStreamOrAfd(safUri) || driveManager.isUriOrFileAccessible(safUri.toString()))) {
                    candidates += safUri
                }
            }
        }

        candidates.toList()
    }

    suspend fun resolvePlayableUri(
        track: MusicTrackEntity,
        deckDriveTreeUri: String?,
        extraSafUris: List<String>
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val candidates = resolvePlayableCandidates(track, deckDriveTreeUri, extraSafUris)
            for (cand in candidates) {
                if (cand.scheme == "file" || isSeekableContentUri(cand) || driveManager.isUriOrFileAccessible(cand.toString())) {
                    return@runCatching cand
                }
            }
            for (cand in candidates) {
                cacheUriOrFileForPlaybackSync(track, cand)?.let { return@runCatching it }
            }
            error("File audio '${track.fileName}' tidak dapat dibuka. Pastikan izin Audio / Folder SD aktif.")
        }
    }

    private fun cacheUriOrFileForPlaybackSync(track: MusicTrackEntity, sourceUri: Uri): Uri? {
        return runCatching {
            val cacheDir = File(context.cacheDir, "music_play_cache").apply { mkdirs() }
            val ext = track.fileName.substringAfterLast('.', "mp3")
                .lowercase(Locale.US)
                .ifBlank { "mp3" }
            val outFile = File(cacheDir, "${track.id}.$ext")
            if (!outFile.exists() || outFile.length() <= 0L) {
                val existing = cacheDir.listFiles()?.sortedBy { it.lastModified() }.orEmpty()
                if (existing.size >= 6) {
                    existing.take(existing.size - 5).forEach { runCatching { it.delete() } }
                }
                val tmpFile = File(cacheDir, "${track.id}.$ext.tmp")
                runCatching { tmpFile.delete() }
                val inputStream = if (sourceUri.scheme == "content") {
                    context.contentResolver.openInputStream(sourceUri)
                } else {
                    val path = if (sourceUri.scheme == "file") sourceUri.path.orEmpty() else sourceUri.toString()
                    val f = File(path)
                    if (f.exists() && f.isFile && f.canRead()) f.inputStream() else null
                } ?: return@runCatching null

                inputStream.use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        val copied = input.copyTo(output, bufferSize = 64 * 1024)
                        output.flush()
                        if (copied <= 0L) {
                            runCatching { tmpFile.delete() }
                            return@runCatching null
                        }
                    }
                }
                if (outFile.exists()) outFile.delete()
                if (!tmpFile.renameTo(outFile)) {
                    tmpFile.copyTo(outFile, overwrite = true)
                    runCatching { tmpFile.delete() }
                }
            }
            if (outFile.exists() && outFile.length() > 0L) Uri.fromFile(outFile) else null
        }.getOrNull()
    }

    suspend fun cacheUriOrFileForPlayback(track: MusicTrackEntity, sourceUri: Uri): Uri? =
        withContext(Dispatchers.IO) {
            cacheUriOrFileForPlaybackSync(track, sourceUri)
        }

    suspend fun cacheContentUriForPlayback(track: MusicTrackEntity, sourceUri: Uri): Uri? =
        cacheUriOrFileForPlayback(track, sourceUri)

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

    private fun dedupKey(fileName: String, sizeBytes: Long): String {
        val norm = fileName.lowercase(Locale.US)
        return if (sizeBytes > 0L) "$norm|$sizeBytes" else norm
    }

    private fun isAppPrivatePath(ref: String): Boolean {
        val filesRoot = context.filesDir.absolutePath
        return ref.startsWith(filesRoot)
    }

    suspend fun importPickedAudioFiles(uris: List<Uri>): Result<MusicRefreshSummary> =
        withContext(Dispatchers.IO) {
            refreshMutex.withLock {
                runCatching {
                    val existing = musicDao.getAllSnapshot()
                    val seenUris = existing.mapTo(HashSet()) { it.fileUri }
                    val existingByKey = existing.associateByTo(HashMap()) { dedupKey(it.fileName, it.sizeBytes) }

                    val toUpsert = mutableListOf<MusicTrackEntity>()
                    var skipped = 0

                    for (uri in uris) {
                        val persisted = runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                            context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
                        }.getOrDefault(false)

                        val (fileName, sizeBytes) = driveManager.queryDisplayNameAndSize(uri)
                        val key = dedupKey(fileName, sizeBytes)
                        val originalUriStr = uri.toString()

                        // If picker provider didn't grant persistable URI (common with OTG / Samsung MyFiles single pick),
                        // copy into private imported_music folder so playback always stays reliable!
                        val storedPathOrUri: String = if (persisted || uri.authority == MediaStore.AUTHORITY) {
                            originalUriStr
                        } else {
                            val importedDir = File(context.filesDir, "imported_music").apply { mkdirs() }
                            val ext = fileName.substringAfterLast('.', "mp3").lowercase(Locale.US).ifBlank { "mp3" }
                            val localCopy = File(importedDir, "${UUID.randomUUID()}.$ext")
                            val copied = runCatching {
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    FileOutputStream(localCopy).use { output ->
                                        input.copyTo(output, bufferSize = 64 * 1024) > 0L
                                    }
                                } ?: false
                            }.getOrDefault(false)
                            if (copied && localCopy.exists() && localCopy.length() > 0L) {
                                localCopy.absolutePath
                            } else {
                                runCatching { localCopy.delete() }
                                originalUriStr
                            }
                        }

                        val existingTrack = existingByKey[key]
                        val shouldUpgradeExisting = existingTrack != null && (
                            (!existingTrack.fileUri.startsWith("content://") && !isAppPrivatePath(existingTrack.fileUri)) ||
                                !isTrackAccessible(existingTrack)
                            )

                        if (existingTrack != null && !shouldUpgradeExisting) {
                            skipped++
                            continue
                        }
                        if (seenUris.contains(storedPathOrUri) && existingTrack == null) {
                            skipped++
                            continue
                        }

                        val target = if (originalUriStr.contains("external", true) || !originalUriStr.contains("primary", true)) {
                            StorageTarget.DECK_DRIVE
                        } else {
                            StorageTarget.INTERNAL
                        }
                        val volumeLabel = if (target == StorageTarget.DECK_DRIVE) "External / SD" else "Internal"
                        val extracted = extractTrackFromUri(uri, storedPathOrUri, fileName, sizeBytes, target, volumeLabel)
                        val finalTrack = if (existingTrack != null) {
                            existingTrack.copy(
                                title = extracted.title.ifBlank { existingTrack.title },
                                artist = if (extracted.artist != "Unknown Artist") extracted.artist else existingTrack.artist,
                                album = if (extracted.album != "Local Music") extracted.album else existingTrack.album,
                                fileUri = storedPathOrUri,
                                durationMs = if (extracted.durationMs > 0L) extracted.durationMs else existingTrack.durationMs,
                                sizeBytes = if (sizeBytes > 0L) sizeBytes else existingTrack.sizeBytes,
                                storageTarget = target,
                                volumeLabel = volumeLabel
                            )
                        } else {
                            extracted
                        }
                        seenUris += storedPathOrUri
                        seenUris += originalUriStr
                        existingByKey[key] = finalTrack
                        toUpsert += finalTrack
                    }

                    if (toUpsert.isNotEmpty()) {
                        musicDao.upsertAll(toUpsert)
                    }
                    MusicRefreshSummary(importedCount = toUpsert.size, skippedCount = skipped)
                }
            }
        }

    suspend fun scanSafFolderForMusic(treeUri: Uri): Result<MusicRefreshSummary> =
        withContext(Dispatchers.IO) {
            refreshMutex.withLock {
                runCatching {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    val existing = musicDao.getAllSnapshot()
                    val seenUris = existing.mapTo(HashSet()) { it.fileUri }
                    val existingByKey = existing.associateByTo(HashMap()) { dedupKey(it.fileName, it.sizeBytes) }
                    val toUpsert = mutableListOf<MusicTrackEntity>()
                    var skipped = 0

                    val docName = runCatching { DocumentFile.fromTreeUri(context, treeUri)?.name }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: "SD / External"

                    scanSafTreeInternal(
                        treeUri = treeUri,
                        seenUris = seenUris,
                        existingByKey = existingByKey,
                        target = StorageTarget.DECK_DRIVE,
                        volumeLabel = docName,
                        out = toUpsert,
                        onSkipped = { skipped++ }
                    )

                    if (toUpsert.isNotEmpty()) {
                        musicDao.upsertAll(toUpsert)
                    }
                    MusicRefreshSummary(importedCount = toUpsert.size, skippedCount = skipped)
                }
            }
        }

    suspend fun autoImportAndRefresh(
        deckDriveTreeUri: String?,
        extraSafUris: List<String>
    ): Result<MusicRefreshSummary> = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            runCatching {
                val safTrees = collectSafTreeStrings(deckDriveTreeUri, extraSafUris)
                var removed = 0
                for (track in musicDao.getAllSnapshot()) {
                    val raw = track.fileUri
                    val directOk = if (!raw.startsWith("content://")) {
                        val path = if (raw.startsWith("file://")) Uri.parse(raw).path.orEmpty() else raw
                        isDirectFileReadable(File(path))
                    } else {
                        canOpenContentUriStreamOrAfd(Uri.parse(raw)) || driveManager.isUriOrFileAccessible(raw)
                    }
                    if (!directOk) {
                        val relinked = findMediaStoreUriForTrack(track)
                            ?: safTrees.firstNotNullOfOrNull { treeStr ->
                                findSafUriInTree(Uri.parse(treeStr), track.fileName, track.sizeBytes)
                                    ?.takeIf { canOpenContentUriStreamOrAfd(it) || driveManager.isUriOrFileAccessible(it.toString()) }
                            }
                        if (relinked != null) {
                            musicDao.upsert(track.copy(fileUri = relinked.toString()))
                        } else {
                            musicDao.delete(track)
                            removed++
                        }
                    } else if (!raw.startsWith("content://") && !isAppPrivatePath(raw)) {
                        // Upgrade legacy raw /storage/... paths to canonical MediaStore or SAF content:// URIs
                        val upgraded = findMediaStoreUriForTrack(track)
                            ?: safTrees.firstNotNullOfOrNull { treeStr ->
                                findSafUriInTree(Uri.parse(treeStr), track.fileName, track.sizeBytes)
                                    ?.takeIf { canOpenContentUriStreamOrAfd(it) || driveManager.isUriOrFileAccessible(it.toString()) }
                            }
                        if (upgraded != null) {
                            musicDao.upsert(track.copy(fileUri = upgraded.toString()))
                        }
                    }
                }

                val existing = musicDao.getAllSnapshot()
                val seenUris = existing.mapTo(HashSet()) { it.fileUri }
                val existingByKey = existing.associateByTo(HashMap()) { dedupKey(it.fileName, it.sizeBytes) }
                val toUpsert = mutableListOf<MusicTrackEntity>()
                var skipped = 0

                // 1. MediaStore.Audio (Internal Shared Storage + SD Card / OTG volumes indexed by Android)
                scanMediaStoreAudio(
                    seenUris = seenUris,
                    existingByKey = existingByKey,
                    out = toUpsert,
                    onSkipped = { skipped++ }
                )

                // 2. Persisted SAF Trees BEFORE raw /storage/ POSIX scan so reliable content:// URIs win
                for (treeStr in safTrees) {
                    runCatching {
                        val uri = Uri.parse(treeStr)
                        val doc = DocumentFile.fromTreeUri(context, uri)
                        if (doc != null && doc.exists() && doc.canRead()) {
                            scanSafTreeInternal(
                                treeUri = uri,
                                seenUris = seenUris,
                                existingByKey = existingByKey,
                                target = StorageTarget.DECK_DRIVE,
                                volumeLabel = doc.name ?: "SD / External",
                                out = toUpsert,
                                onSkipped = { skipped++ }
                            )
                        }
                    }
                }

                // 3. Direct filesystem fallback on Internal, App Downloads, and Removable SD/USB mount paths
                val directDirs = linkedMapOf<File, Pair<StorageTarget, String>>()
                driveManager.detectRemovableExternalVolumes().forEach { vol ->
                    val mp = vol.mountPath?.let { File(it) }
                    if (mp != null && mp.exists() && mp.canRead()) {
                        directDirs[mp] = StorageTarget.DECK_DRIVE to vol.label
                    }
                }
                directDirs[driveManager.internalDownloadsDir] = StorageTarget.INTERNAL to "App Downloads"
                runCatching { Environment.getExternalStorageDirectory() }.getOrNull()?.let { extDir ->
                    if (extDir.exists() && extDir.canRead()) {
                        directDirs[extDir] = StorageTarget.INTERNAL to "Internal"
                    }
                }

                val seenKeysForPosix = existingByKey.keys.toMutableSet()
                for ((dir, meta) in directDirs) {
                    scanDirectoryFiles(
                        rootDir = dir,
                        target = meta.first,
                        volumeLabel = meta.second,
                        seenUris = seenUris,
                        seenKeys = seenKeysForPosix,
                        out = toUpsert,
                        onSkipped = { skipped++ }
                    )
                }

                if (toUpsert.isNotEmpty()) {
                    musicDao.upsertAll(toUpsert)
                }
                MusicRefreshSummary(importedCount = toUpsert.size, skippedCount = skipped, removedCount = removed)
            }
        }
    }

    private fun scanMediaStoreAudio(
        seenUris: MutableSet<String>,
        existingByKey: MutableMap<String, MusicTrackEntity>,
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
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            }
            @Suppress("DEPRECATION")
            add(MediaStore.Audio.Media.DATA)
        }.toTypedArray()

        val targetUris = buildList {
            add("external" to MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val vols = runCatching { MediaStore.getExternalVolumeNames(context) }.getOrNull().orEmpty()
                for (vol in vols) {
                    val volUri = MediaStore.Audio.Media.getContentUri(vol)
                    if (volUri != MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) {
                        add(vol to volUri)
                    }
                }
            }
        }

        for ((volumeTag, collectionUri) in targetUris) {
            runCatching {
                context.contentResolver.query(
                    collectionUri,
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
                    val relIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                    } else -1
                    @Suppress("DEPRECATION")
                    val dataIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIdx)
                        val filePath = if (dataIdx >= 0) cursor.getString(dataIdx)?.trim().orEmpty() else ""
                        val fileName = (if (nameIdx >= 0) cursor.getString(nameIdx) else null)
                            ?.takeIf { it.isNotBlank() }
                            ?: filePath.substringAfterLast('/').takeIf { it.isNotBlank() }
                            ?: continue
                        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
                        if (ext !in audioExtensions) continue

                        val sizeBytes = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                        if (sizeBytes in 1..24_000L && ext != "mp3") continue // Skip tiny notification tones

                        val directFile = filePath.takeIf { it.startsWith("/") }?.let { File(it) }
                        val canonicalUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        val volumeUri = ContentUris.withAppendedId(collectionUri, id)
                        val contentUri = if (canonicalUri != volumeUri && driveManager.isUriOrFileAccessible(canonicalUri.toString())) {
                            canonicalUri
                        } else {
                            volumeUri
                        }
                        // Store content:// URI (identical to OfflineTube!) instead of raw /storage/... FUSE paths
                        val preferredUriStr = contentUri.toString()

                        val key = dedupKey(fileName, sizeBytes)
                        val existingTrack = existingByKey[key]
                        if (seenUris.contains(preferredUriStr)) {
                            onSkipped()
                            continue
                        }
                        if (existingTrack != null && existingTrack.fileUri.startsWith("content://")) {
                            onSkipped()
                            continue
                        }

                        val durMs = if (durIdx >= 0 && !cursor.isNull(durIdx)) cursor.getLong(durIdx) else 0L
                        val rawTitle = if (titleIdx >= 0) cursor.getString(titleIdx)?.trim() else null
                        val rawArtist = if (artistIdx >= 0) cursor.getString(artistIdx)?.trim() else null
                        val rawAlbum = if (albumIdx >= 0) cursor.getString(albumIdx)?.trim() else null
                        val relFolder = if (relIdx >= 0) {
                            cursor.getString(relIdx)?.trim('/')?.substringAfterLast('/')
                        } else {
                            directFile?.parentFile?.name
                        }

                        val volName = if (volIdx >= 0) cursor.getString(volIdx).orEmpty().ifBlank { volumeTag } else {
                            if (filePath.contains("/storage/emulated")) "external_primary" else volumeTag
                        }
                        val isPrimary = volName.equals("external_primary", ignoreCase = true) ||
                            volName.equals("external", ignoreCase = true) ||
                            volName.isBlank()

                        val entity = if (existingTrack != null && !isAppPrivatePath(existingTrack.fileUri)) {
                            existingTrack.copy(
                                fileUri = preferredUriStr,
                                storageTarget = if (isPrimary) StorageTarget.INTERNAL else StorageTarget.DECK_DRIVE,
                                volumeLabel = if (isPrimary) "Internal" else "SD Card ($volName)"
                            )
                        } else if (existingTrack == null) {
                            MusicTrackEntity(
                                id = UUID.randomUUID().toString(),
                                title = rawTitle?.takeIf { it.isNotBlank() } ?: cleanAudioTitle(fileName),
                                artist = rawArtist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown Artist",
                                album = rawAlbum?.takeIf { it.isNotBlank() && it != "<unknown>" }
                                    ?: relFolder?.takeIf { it.isNotBlank() }
                                    ?: "Local Music",
                                fileName = fileName,
                                fileUri = preferredUriStr,
                                durationMs = durMs.coerceAtLeast(0L),
                                sizeBytes = sizeBytes.coerceAtLeast(0L),
                                storageTarget = if (isPrimary) StorageTarget.INTERNAL else StorageTarget.DECK_DRIVE,
                                volumeLabel = if (isPrimary) "Internal" else "SD Card ($volName)"
                            )
                        } else {
                            onSkipped()
                            continue
                        }

                        seenUris += preferredUriStr
                        seenUris += contentUri.toString()
                        existingByKey[key] = entity
                        out += entity
                    }
                }
            }
        }
    }

    private fun scanDirectoryFiles(
        rootDir: File,
        target: StorageTarget,
        volumeLabel: String,
        seenUris: MutableSet<String>,
        seenKeys: MutableSet<String>,
        out: MutableList<MusicTrackEntity>,
        onSkipped: () -> Unit
    ) {
        val priorityDirs = setOf("music", "audio", "mp3", "songs", "lagu", "download", "downloads", "podcasts", "audiobooks")
        val skipDirs = setOf("android", "notifications", "ringtones", "alarms", "cache", "thumbnails", ".thumbnails")
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(rootDir to 0)
        var visitedDirs = 0
        while (queue.isNotEmpty() && visitedDirs < 1200) {
            val (dir, depth) = queue.removeFirst()
            visitedDirs++
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            val sortedChildren = children.sortedWith(
                compareByDescending<File> { it.isDirectory && it.name.lowercase(Locale.US) in priorityDirs }
                    .thenBy { it.name.lowercase(Locale.US) }
            )
            for (child in sortedChildren) {
                val name = child.name
                if (name.startsWith(".")) continue
                if (child.isDirectory) {
                    if (depth < 7 && name.lowercase(Locale.US) !in skipDirs) {
                        queue.add(child to depth + 1)
                    }
                } else if (child.isFile) {
                    val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
                    if (ext in audioExtensions) {
                        val sizeBytes = child.length().coerceAtLeast(0L)
                        if (sizeBytes in 1..24_000L && ext != "mp3") continue
                        if (!isDirectFileReadable(child)) continue
                        val pathStr = child.absolutePath
                        val key = dedupKey(name, sizeBytes)
                        if (seenUris.contains(pathStr) || seenKeys.contains(key)) {
                            onSkipped()
                            continue
                        }
                        val track = extractTrackFromFile(child, target, volumeLabel)
                        seenUris += pathStr
                        seenKeys += key
                        out += track
                    }
                }
            }
        }
    }

    private fun scanSafTreeInternal(
        treeUri: Uri,
        seenUris: MutableSet<String>,
        existingByKey: MutableMap<String, MusicTrackEntity>,
        target: StorageTarget,
        volumeLabel: String,
        out: MutableList<MusicTrackEntity>,
        onSkipped: () -> Unit
    ) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return
        val queue = ArrayDeque<DocumentFile>()
        queue.add(root)
        var visitedDirs = 0
        while (queue.isNotEmpty() && visitedDirs < 400) {
            val dir = queue.removeFirst()
            visitedDirs++
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                val name = child.name ?: continue
                if (name.startsWith(".")) continue
                if (child.isDirectory) {
                    if (name.lowercase(Locale.US) !in setOf("android", "cache", "lost.dir")) {
                        queue.add(child)
                    }
                } else if (child.isFile) {
                    val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
                    if (ext in audioExtensions) {
                        val uriStr = child.uri.toString()
                        val sizeBytes = child.length().coerceAtLeast(0L)
                        if (sizeBytes in 1..24_000L && ext != "mp3") continue
                        val key = dedupKey(name, sizeBytes)
                        val existingTrack = existingByKey[key]
                        if (seenUris.contains(uriStr)) {
                            onSkipped()
                            continue
                        }
                        if (existingTrack != null && (existingTrack.fileUri.startsWith("content://") || isAppPrivatePath(existingTrack.fileUri)) && isTrackAccessible(existingTrack)) {
                            onSkipped()
                            continue
                        }
                        val extracted = extractTrackFromUri(
                            uri = child.uri,
                            uriStr = uriStr,
                            fileName = name,
                            sizeBytes = sizeBytes,
                            target = target,
                            volumeLabel = volumeLabel
                        )
                        val entity = if (existingTrack != null) {
                            existingTrack.copy(
                                title = extracted.title.ifBlank { existingTrack.title },
                                artist = if (extracted.artist != "Unknown Artist") extracted.artist else existingTrack.artist,
                                album = if (extracted.album != "Local Music") extracted.album else existingTrack.album,
                                fileUri = uriStr,
                                durationMs = if (extracted.durationMs > 0L) extracted.durationMs else existingTrack.durationMs,
                                sizeBytes = if (sizeBytes > 0L) sizeBytes else existingTrack.sizeBytes,
                                storageTarget = target,
                                volumeLabel = volumeLabel
                            )
                        } else {
                            extracted
                        }
                        seenUris += uriStr
                        existingByKey[key] = entity
                        out += entity
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
