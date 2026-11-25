package com.offlinecyberdeck.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.offlinecyberdeck.core.database.dao.DownloadDao
import com.offlinecyberdeck.core.database.entity.DownloadEntity
import com.offlinecyberdeck.core.format.Formatters
import com.offlinecyberdeck.core.model.DownloadStatus
import com.offlinecyberdeck.core.model.StorageTarget
import com.offlinecyberdeck.core.settings.SettingsRepository
import com.offlinecyberdeck.core.storage.DeckDriveManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val settingsRepo: SettingsRepository,
    private val driveManager: DeckDriveManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    val downloadsFlow: Flow<List<DownloadEntity>> = downloadDao.observeAll()

    suspend fun enqueueDownload(
        url: String,
        suggestedFileName: String? = null,
        mimeType: String? = null,
        customFolderUri: String? = null
    ): DownloadEntity {
        val settings = settingsRepo.getSettings()
        val guessedName = suggestedFileName?.takeIf { it.isNotBlank() }
            ?: URLUtil.guessFileName(url, null, mimeType)
        val cleanName = driveManager.sanitizeFileName(guessedName)
        val targetFolder = customFolderUri ?: settings.downloadFolderUri
        val targetMode = when {
            !targetFolder.isNullOrBlank() -> StorageTarget.DECK_DRIVE
            settings.downloadStorageTarget == StorageTarget.DECK_DRIVE &&
                driveManager.status.value.isWritable -> StorageTarget.DECK_DRIVE
            else -> StorageTarget.INTERNAL
        }

        val entity = DownloadEntity(
            id = UUID.randomUUID().toString(),
            url = url,
            fileName = cleanName,
            mimeType = mimeType?.takeIf { it.isNotBlank() } ?: guessMime(cleanName),
            destinationUri = targetFolder.orEmpty(),
            storageTarget = targetMode,
            status = DownloadStatus.QUEUED
        )
        downloadDao.upsert(entity)
        startJob(entity.id)
        return entity
    }

    fun pauseDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        stopServiceIfIdle()
        scope.launch {
            val current = downloadDao.getById(id) ?: return@launch
            if (current.status == DownloadStatus.DOWNLOADING || current.status == DownloadStatus.QUEUED) {
                downloadDao.upsert(current.copy(status = DownloadStatus.PAUSED))
            }
        }
    }

    fun resumeOrRetryDownload(id: String) {
        if (activeJobs[id]?.isActive == true) return
        startJob(id)
    }

    fun cancelDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        stopServiceIfIdle()
        scope.launch {
            val current = downloadDao.getById(id) ?: return@launch
            downloadDao.upsert(current.copy(status = DownloadStatus.CANCELLED))
        }
    }

    fun removeRecord(id: String, deleteFile: Boolean) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        stopServiceIfIdle()
        scope.launch {
            val current = downloadDao.getById(id)
            if (deleteFile && current != null && current.destinationUri.isNotBlank()) {
                deleteTarget(current.destinationUri)
            }
            downloadDao.deleteById(id)
        }
    }

    private fun startJob(id: String) {
        val job = scope.launch {
            executeDownloadWithRetries(id)
        }
        activeJobs[id] = job
    }

    private fun stopServiceIfIdle() {
        if (activeJobs.isEmpty()) {
            DownloadForegroundService.stop(context)
        }
    }

    private suspend fun executeDownloadWithRetries(id: String) = withContext(Dispatchers.IO) {
        val maxRetries = 6
        val backoffDelaysMs = longArrayOf(2000L, 4000L, 8000L, 12000L, 16000L, 20000L)
        var attempt = 0

        try {
            val initial = downloadDao.getById(id) ?: return@withContext
            val settings = settingsRepo.getSettings()
            val targetUriOrPath = resolveDestination(initial, settings.deckDriveTreeUri)

            DownloadForegroundService.updateProgress(
                context = context,
                title = initial.fileName,
                detail = "Starting download...",
                progressPercent = -1
            )

            while (attempt <= maxRetries) {
                val latest = downloadDao.getById(id) ?: return@withContext
                val existingBytes = measureExistingBytes(targetUriOrPath)
                try {
                    performSingleDownloadAttempt(
                        entity = latest.copy(destinationUri = targetUriOrPath),
                        targetUriOrPath = targetUriOrPath,
                        existingBytes = existingBytes
                    )
                    return@withContext
                } catch (ce: CancellationException) {
                    throw ce
                } catch (ioe: IOException) {
                    attempt++
                    if (attempt > maxRetries) {
                        val failedState = downloadDao.getById(id) ?: latest
                        downloadDao.upsert(
                            failedState.copy(
                                destinationUri = targetUriOrPath,
                                status = DownloadStatus.FAILED,
                                errorMessage = "Interrupted after retries (${ioe.message ?: "Network aborted"}). Tap Retry to resume."
                            )
                        )
                        return@withContext
                    }
                    val currentState = downloadDao.getById(id) ?: latest
                    val resumedFrom = measureExistingBytes(targetUriOrPath)
                    downloadDao.upsert(
                        currentState.copy(
                            destinationUri = targetUriOrPath,
                            bytesDownloaded = resumedFrom,
                            status = DownloadStatus.DOWNLOADING,
                            errorMessage = "Connection interrupted (${ioe.message ?: "retrying"}). Auto-resuming attempt $attempt/$maxRetries..."
                        )
                    )
                    DownloadForegroundService.updateProgress(
                        context = context,
                        title = currentState.fileName,
                        detail = "Reconnecting (attempt $attempt/$maxRetries) at ${Formatters.formatBytes(resumedFrom)}...",
                        progressPercent = if (currentState.totalBytes > 0L) {
                            ((resumedFrom * 100L) / currentState.totalBytes).toInt()
                        } else -1
                    )
                    delay(backoffDelaysMs[(attempt - 1).coerceAtMost(backoffDelaysMs.lastIndex)])
                } catch (t: Throwable) {
                    val failedState = downloadDao.getById(id) ?: latest
                    downloadDao.upsert(
                        failedState.copy(
                            destinationUri = targetUriOrPath,
                            status = DownloadStatus.FAILED,
                            errorMessage = t.message ?: "Download failed"
                        )
                    )
                    return@withContext
                }
            }
        } finally {
            activeJobs.remove(id)
            stopServiceIfIdle()
        }
    }

    private suspend fun performSingleDownloadAttempt(
        entity: DownloadEntity,
        targetUriOrPath: String,
        existingBytes: Long
    ) {
        var conn: HttpURLConnection? = null
        try {
            downloadDao.upsert(
                entity.copy(
                    destinationUri = targetUriOrPath,
                    bytesDownloaded = existingBytes,
                    status = DownloadStatus.DOWNLOADING,
                    errorMessage = null
                )
            )

            conn = openFollowingCrossProtocolRedirects(entity.url, existingBytes)
            val code = conn.responseCode

            // If server replies 416 Range Not Satisfiable when file is already complete
            if (code == 416 && existingBytes > 0L && entity.totalBytes > 0L && existingBytes >= entity.totalBytes) {
                downloadDao.upsert(
                    entity.copy(
                        destinationUri = targetUriOrPath,
                        status = DownloadStatus.COMPLETED,
                        bytesDownloaded = existingBytes,
                        totalBytes = existingBytes,
                        supportsResume = true,
                        completedAt = System.currentTimeMillis()
                    )
                )
                return
            }

            if (code !in 200..299) {
                error("HTTP $code ${conn.responseMessage ?: ""}".trim())
            }

            val acceptRanges = conn.getHeaderField("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
            val isPartial = code == HttpURLConnection.HTTP_PARTIAL
            val contentLength = conn.contentLengthLong
            val totalBytes = when {
                isPartial && contentLength > 0 -> existingBytes + contentLength
                contentLength > 0 -> contentLength
                entity.totalBytes > 0 -> entity.totalBytes
                else -> -1L
            }

            val isInternalFile = !targetUriOrPath.startsWith("content://")
            val buffer = ByteArray(64 * 1024)
            var downloaded = if (isPartial) existingBytes else 0L
            var lastEmit = System.currentTimeMillis()

            conn.inputStream.use { input ->
                if (isInternalFile) {
                    val outFile = File(targetUriOrPath)
                    outFile.parentFile?.mkdirs()
                    RandomAccessFile(outFile, "rw").use { raf ->
                        if (isPartial) {
                            raf.seek(existingBytes)
                        } else {
                            raf.setLength(0L)
                        }
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            raf.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastEmit >= 500L) {
                                lastEmit = now
                                emitProgress(entity, targetUriOrPath, downloaded, totalBytes, acceptRanges)
                            }
                        }
                    }
                } else {
                    val uri = Uri.parse(targetUriOrPath)
                    if (isPartial && existingBytes > 0L) {
                        val pfd = runCatching { context.contentResolver.openFileDescriptor(uri, "rw") }.getOrNull()
                        if (pfd != null) {
                            pfd.use { descriptor ->
                                FileOutputStream(descriptor.fileDescriptor).use { fos ->
                                    fos.channel.position(existingBytes)
                                    while (true) {
                                        val read = input.read(buffer)
                                        if (read == -1) break
                                        fos.write(buffer, 0, read)
                                        downloaded += read
                                        val now = System.currentTimeMillis()
                                        if (now - lastEmit >= 500L) {
                                            lastEmit = now
                                            emitProgress(entity, targetUriOrPath, downloaded, totalBytes, true)
                                        }
                                    }
                                    fos.flush()
                                }
                            }
                        } else {
                            val outStream = context.contentResolver.openOutputStream(uri, "wa")
                                ?: error("Cannot resume write to selected SAF destination")
                            outStream.use { out ->
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    out.write(buffer, 0, read)
                                    downloaded += read
                                    val now = System.currentTimeMillis()
                                    if (now - lastEmit >= 500L) {
                                        lastEmit = now
                                        emitProgress(entity, targetUriOrPath, downloaded, totalBytes, true)
                                    }
                                }
                                out.flush()
                            }
                        }
                    } else {
                        val outStream = context.contentResolver.openOutputStream(uri, "w")
                            ?: error("Cannot write to selected SAF destination")
                        outStream.use { out ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                out.write(buffer, 0, read)
                                downloaded += read
                                val now = System.currentTimeMillis()
                                if (now - lastEmit >= 500L) {
                                    lastEmit = now
                                    emitProgress(entity, targetUriOrPath, downloaded, totalBytes, acceptRanges)
                                }
                            }
                            out.flush()
                        }
                    }
                }
            }

            downloadDao.upsert(
                entity.copy(
                    destinationUri = targetUriOrPath,
                    status = DownloadStatus.COMPLETED,
                    bytesDownloaded = downloaded,
                    totalBytes = if (totalBytes > 0) totalBytes else downloaded,
                    supportsResume = acceptRanges || isPartial,
                    errorMessage = null,
                    completedAt = System.currentTimeMillis()
                )
            )
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun emitProgress(
        base: DownloadEntity,
        targetUriOrPath: String,
        downloaded: Long,
        totalBytes: Long,
        supportsResume: Boolean
    ) {
        downloadDao.upsert(
            base.copy(
                destinationUri = targetUriOrPath,
                status = DownloadStatus.DOWNLOADING,
                bytesDownloaded = downloaded,
                totalBytes = totalBytes,
                supportsResume = supportsResume,
                errorMessage = null
            )
        )
        val percent = if (totalBytes > 0L) ((downloaded * 100L) / totalBytes).toInt() else -1
        val detail = if (totalBytes > 0L) {
            "${Formatters.formatBytes(downloaded)} / ${Formatters.formatBytes(totalBytes)} ($percent%)"
        } else {
            "${Formatters.formatBytes(downloaded)} downloaded"
        }
        DownloadForegroundService.updateProgress(context, base.fileName, detail, percent)
    }

    private fun openFollowingCrossProtocolRedirects(
        startUrl: String,
        existingBytes: Long
    ): HttpURLConnection {
        var currentUrl = startUrl
        var hops = 0
        while (hops < 8) {
            val parsedUrl = URL(currentUrl)
            if (parsedUrl.protocol != "http" && parsedUrl.protocol != "https") {
                error("Unsupported download scheme: ${parsedUrl.protocol}")
            }
            val conn = (parsedUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "OfflineCyberDeck/1.0 Android")
                setRequestProperty("Accept-Encoding", "identity")
                if (existingBytes > 0L) {
                    setRequestProperty("Range", "bytes=$existingBytes-")
                }
            }
            conn.connect()
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    error("Redirect without Location header (HTTP $code)")
                }
                currentUrl = URL(parsedUrl, location).toString()
                hops++
                continue
            }
            return conn
        }
        error("Too many HTTP redirects")
    }

    private fun measureExistingBytes(uriOrPath: String): Long {
        if (uriOrPath.isBlank()) return 0L
        return if (uriOrPath.startsWith("content://")) {
            runCatching {
                DocumentFile.fromSingleUri(context, Uri.parse(uriOrPath))?.length()?.coerceAtLeast(0L) ?: 0L
            }.getOrDefault(0L)
        } else {
            val f = File(uriOrPath)
            if (f.exists()) f.length().coerceAtLeast(0L) else 0L
        }
    }

    private suspend fun resolveDestination(entity: DownloadEntity, deckTreeUri: String?): String {
        // Reuse existing internal file or already-created SAF document URI when retrying/resuming
        if (entity.destinationUri.isNotBlank()) {
            if (!entity.destinationUri.startsWith("content://")) {
                val existing = File(entity.destinationUri)
                if (existing.exists() || entity.status != DownloadStatus.QUEUED) {
                    return existing.absolutePath
                }
            } else if (entity.status != DownloadStatus.QUEUED) {
                val existingDoc = runCatching {
                    DocumentFile.fromSingleUri(context, Uri.parse(entity.destinationUri))
                }.getOrNull()
                if (existingDoc != null && existingDoc.isFile && existingDoc.canWrite()) {
                    return entity.destinationUri
                }
            }
        }

        // First-time destination creation
        val customFolderUri = entity.destinationUri.takeIf {
            it.startsWith("content://") && entity.status == DownloadStatus.QUEUED
        }
        if (!customFolderUri.isNullOrBlank()) {
            val treeDoc = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(customFolderUri)) }.getOrNull()
            if (treeDoc != null && treeDoc.canWrite()) {
                val created = driveManager.createUniqueSafFile(treeDoc, entity.fileName, entity.mimeType)
                if (created != null) return created.uri.toString()
            }
        }

        if (entity.storageTarget == StorageTarget.DECK_DRIVE && !deckTreeUri.isNullOrBlank()) {
            val dir = driveManager.ensureSubdirectory(deckTreeUri, "CyberDeck", "Downloads")
            if (dir != null) {
                val created = driveManager.createUniqueSafFile(dir, entity.fileName, entity.mimeType)
                if (created != null) return created.uri.toString()
            }
        }

        val internalFile = driveManager.createUniqueInternalFile(
            driveManager.internalDownloadsDir,
            entity.fileName
        )
        return internalFile.absolutePath
    }

    private fun deleteTarget(uriOrPath: String) {
        if (uriOrPath.startsWith("content://")) {
            runCatching { DocumentFile.fromSingleUri(context, Uri.parse(uriOrPath))?.delete() }
        } else {
            runCatching { File(uriOrPath).delete() }
        }
    }

    fun buildOpenIntent(item: DownloadEntity): Intent? {
        if (item.destinationUri.isBlank()) return null
        val uri = if (item.destinationUri.startsWith("content://")) {
            Uri.parse(item.destinationUri)
        } else {
            val f = File(item.destinationUri)
            if (!f.exists()) return null
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        }
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildShareIntent(item: DownloadEntity): Intent? {
        if (item.destinationUri.isBlank()) return null
        val uri = if (item.destinationUri.startsWith("content://")) {
            Uri.parse(item.destinationUri)
        } else {
            val f = File(item.destinationUri)
            if (!f.exists()) return null
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun guessMime(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
