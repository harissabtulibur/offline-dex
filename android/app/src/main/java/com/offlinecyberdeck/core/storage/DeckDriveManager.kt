package com.offlinecyberdeck.core.storage

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.OpenableColumns
import android.system.Os
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.offlinecyberdeck.core.model.DeckDriveStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class ExternalVolumeInfo(
    val id: String,
    val label: String,
    val isMounted: Boolean,
    val mountPath: String? = null
)

data class StorageRootInfo(
    val id: String,
    val label: String,
    val pathOrUri: String,
    val isSafTree: Boolean,
    val isRemovable: Boolean,
    val totalBytes: Long?,
    val availableBytes: Long?
)

class DeckDriveManager(private val context: Context) {

    private val _status = MutableStateFlow(DeckDriveStatus())
    val status: StateFlow<DeckDriveStatus> = _status.asStateFlow()

    val internalArchivesDir: File
        get() = File(context.filesDir, "archives").apply { mkdirs() }

    val internalDownloadsDir: File
        get() = File(context.filesDir, "downloads").apply { mkdirs() }

    val internalVaultDir: File
        get() = File(context.filesDir, "vault").apply { mkdirs() }

    val internalThumbnailsDir: File
        get() = File(context.cacheDir, "thumbs").apply { mkdirs() }

    val internalWallpapersDir: File
        get() = File(context.filesDir, "wallpapers").apply { mkdirs() }

    suspend fun attachDeckDrive(treeUri: Uri): DeckDriveStatus = withContext(Dispatchers.IO) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        }
        refreshStatus(treeUri.toString())
    }

    suspend fun detachDeckDrive(configuredUri: String?): DeckDriveStatus = withContext(Dispatchers.IO) {
        if (!configuredUri.isNullOrBlank()) {
            val uri = Uri.parse(configuredUri)
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.releasePersistableUriPermission(uri, flags)
            }
        }
        val empty = DeckDriveStatus(statusDetail = "Deck Drive disconnected by user")
        _status.value = empty
        empty
    }

    suspend fun refreshStatus(configuredUri: String?): DeckDriveStatus = withContext(Dispatchers.IO) {
        if (configuredUri.isNullOrBlank()) {
            val empty = DeckDriveStatus()
            _status.value = empty
            return@withContext empty
        }
        val uri = Uri.parse(configuredUri)
        val hasPersisted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        val doc = runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
        val exists = runCatching { doc?.exists() == true }.getOrDefault(false)
        val readable = runCatching { doc?.canRead() == true }.getOrDefault(false)
        val writable = runCatching { doc?.canWrite() == true }.getOrDefault(false)
        val name = runCatching { doc?.name }.getOrNull() ?: uri.lastPathSegment ?: "External Drive"

        val (totalBytes, availableBytes) = if (exists && readable) {
            probeSafCapacity(uri)
        } else {
            null to null
        }

        val detail = when {
            !hasPersisted -> "SAF permission missing or revoked; re-select Deck Drive"
            !exists || !readable -> "Drive disconnected or unavailable"
            !writable -> "Connected (Read-Only) — cannot write new archives here"
            else -> "Connected & Writable"
        }

        val next = DeckDriveStatus(
            configuredUri = configuredUri,
            displayName = name,
            isConnected = hasPersisted && exists && readable,
            isWritable = hasPersisted && exists && writable,
            hasPersistedPermission = hasPersisted,
            totalBytes = totalBytes,
            availableBytes = availableBytes,
            statusDetail = detail
        )
        _status.value = next
        next
    }

    private fun probeSafCapacity(treeUri: Uri): Pair<Long?, Long?> {
        return runCatching {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@runCatching null to null
            context.contentResolver.openFileDescriptor(rootDoc.uri, "r")?.use { pfd ->
                val stat = Os.fstatvfs(pfd.fileDescriptor)
                val blockSize = stat.f_frsize.takeIf { it > 0 } ?: stat.f_bsize
                val total = stat.f_blocks * blockSize
                val avail = stat.f_bavail * blockSize
                if (total > 0) total to avail else null to null
            } ?: (null to null)
        }.getOrDefault(null to null)
    }

    fun getInternalStorageCapacity(): Pair<Long?, Long?> {
        return runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.totalBytes to stat.availableBytes
        }.getOrDefault(null to null)
    }

    fun getPathCapacity(path: String): Pair<Long?, Long?> {
        return runCatching {
            val stat = StatFs(path)
            stat.totalBytes to stat.availableBytes
        }.getOrDefault(null to null)
    }

    fun directorySizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        var sum = 0L
        dir.walkTopDown().forEach { if (it.isFile) sum += it.length() }
        return sum
    }

    suspend fun ensureSubdirectory(treeUriString: String, vararg pathSegments: String): DocumentFile? =
        withContext(Dispatchers.IO) {
            val root = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(treeUriString)) }.getOrNull()
                ?: return@withContext null
            if (!root.exists() || !root.canWrite()) return@withContext null
            var current: DocumentFile = root
            for (segment in pathSegments) {
                val safe = sanitizeSegment(segment)
                val existing = current.findFile(safe)
                current = if (existing != null && existing.isDirectory) {
                    existing
                } else {
                    current.createDirectory(safe) ?: return@withContext null
                }
            }
            current
        }

    suspend fun createUniqueSafFile(
        parentDir: DocumentFile,
        desiredName: String,
        mimeType: String
    ): DocumentFile? = withContext(Dispatchers.IO) {
        val cleanName = sanitizeFileName(desiredName)
        val dotIdx = cleanName.lastIndexOf('.')
        val base = if (dotIdx > 0) cleanName.substring(0, dotIdx) else cleanName
        val ext = if (dotIdx > 0) cleanName.substring(dotIdx) else ""
        var candidate = cleanName
        var attempt = 1
        while (parentDir.findFile(candidate) != null && attempt <= 200) {
            candidate = "${base}_($attempt)$ext"
            attempt++
        }
        parentDir.createFile(mimeType, candidate)
    }

    fun createUniqueInternalFile(parentDir: File, desiredName: String): File {
        parentDir.mkdirs()
        val cleanName = sanitizeFileName(desiredName)
        val dotIdx = cleanName.lastIndexOf('.')
        val base = if (dotIdx > 0) cleanName.substring(0, dotIdx) else cleanName
        val ext = if (dotIdx > 0) cleanName.substring(dotIdx) else ""
        var candidate = File(parentDir, cleanName)
        var attempt = 1
        while (candidate.exists() && attempt <= 200) {
            candidate = File(parentDir, "${base}_($attempt)$ext")
            attempt++
        }
        return candidate
    }

    fun openInputStream(uriOrPath: String): InputStream? {
        if (uriOrPath.isBlank()) return null
        return if (uriOrPath.startsWith("content://")) {
            runCatching { context.contentResolver.openInputStream(Uri.parse(uriOrPath)) }.getOrNull()
        } else {
            val file = File(uriOrPath)
            if (file.exists() && file.canRead()) file.inputStream() else null
        }
    }

    fun openOutputStream(uriOrPath: String): OutputStream? {
        if (uriOrPath.isBlank()) return null
        return if (uriOrPath.startsWith("content://")) {
            runCatching { context.contentResolver.openOutputStream(Uri.parse(uriOrPath), "w") }.getOrNull()
        } else {
            val file = File(uriOrPath)
            file.parentFile?.mkdirs()
            runCatching { file.outputStream() }.getOrNull()
        }
    }

    fun openReadFileDescriptor(uriOrPath: String): ParcelFileDescriptor? {
        if (uriOrPath.isBlank()) return null
        return if (uriOrPath.startsWith("content://")) {
            runCatching { context.contentResolver.openFileDescriptor(Uri.parse(uriOrPath), "r") }.getOrNull()
        } else {
            val file = File(uriOrPath)
            if (file.exists() && file.canRead()) {
                runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
            } else null
        }
    }

    private fun resolveVolumeDirectory(vol: StorageVolume): File? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { vol.directory }.getOrNull()?.let { return it }
        }
        runCatching {
            val getPath = vol.javaClass.getMethod("getPath")
            val p = getPath.invoke(vol) as? String
            if (!p.isNullOrBlank()) {
                val f = File(p)
                if (f.exists()) return f
            }
        }
        val uuid = vol.uuid
        if (!uuid.isNullOrBlank()) {
            val candidate = File("/storage/$uuid")
            if (candidate.exists()) return candidate
        }
        return null
    }

    fun detectRemovableExternalVolumes(): List<ExternalVolumeInfo> {
        return runCatching {
            val sm = context.getSystemService(StorageManager::class.java) ?: return emptyList()
            sm.storageVolumes
                .filter { vol ->
                    vol.isRemovable && !vol.isPrimary && vol.state == Environment.MEDIA_MOUNTED
                }
                .mapIndexed { index, vol ->
                    val label = runCatching { vol.getDescription(context) }.getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: "External USB / SD Storage"
                    val id = vol.uuid ?: "${label}_$index"
                    val dir = resolveVolumeDirectory(vol)
                    ExternalVolumeInfo(
                        id = id,
                        label = label,
                        isMounted = true,
                        mountPath = dir?.absolutePath
                    )
                }
        }.getOrDefault(emptyList())
    }

    fun getAllStorageRoots(configuredSafUri: String?): List<StorageRootInfo> {
        val list = mutableListOf<StorageRootInfo>()
        val primaryExt = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        if (primaryExt != null && primaryExt.exists()) {
            val (tot, avail) = getPathCapacity(primaryExt.absolutePath)
            list += StorageRootInfo(
                id = "internal_shared",
                label = "Internal Storage",
                pathOrUri = primaryExt.absolutePath,
                isSafTree = false,
                isRemovable = false,
                totalBytes = tot,
                availableBytes = avail
            )
        }
        val (appTot, appAvail) = getInternalStorageCapacity()
        list += StorageRootInfo(
            id = "app_vault",
            label = "Cyber Deck App Vault",
            pathOrUri = context.filesDir.absolutePath,
            isSafTree = false,
            isRemovable = false,
            totalBytes = appTot,
            availableBytes = appAvail
        )
        detectRemovableExternalVolumes().forEach { vol ->
            val mp = vol.mountPath
            if (!mp.isNullOrBlank()) {
                val (tot, avail) = getPathCapacity(mp)
                list += StorageRootInfo(
                    id = "vol_${vol.id}",
                    label = vol.label,
                    pathOrUri = mp,
                    isSafTree = false,
                    isRemovable = true,
                    totalBytes = tot,
                    availableBytes = avail
                )
            }
        }
        if (!configuredSafUri.isNullOrBlank()) {
            val uri = Uri.parse(configuredSafUri)
            val doc = runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
            if (doc != null && doc.exists() && doc.canRead()) {
                val (tot, avail) = probeSafCapacity(uri)
                list += StorageRootInfo(
                    id = "saf_deck_drive",
                    label = "Deck Drive (${doc.name ?: "SAF"})",
                    pathOrUri = configuredSafUri,
                    isSafTree = true,
                    isRemovable = true,
                    totalBytes = tot,
                    availableBytes = avail
                )
            }
        }
        return list
    }

    suspend fun copyAndResizeWallpaper(sourceUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            } ?: error("Gagal membaca file gambar.")

            val srcW = boundsOpts.outWidth
            val srcH = boundsOpts.outHeight
            if (srcW <= 0 || srcH <= 0) error("Format gambar tidak valid.")

            val maxDim = 1920
            var sampleSize = 1
            while (srcW / sampleSize > maxDim * 2 || srcH / sampleSize > maxDim * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: error("Gagal memuat gambar wallpaper.")

            val longest = max(decoded.width, decoded.height)
            val finalBmp = if (longest > maxDim) {
                val ratio = maxDim.toFloat() / longest.toFloat()
                val dstW = (decoded.width * ratio).roundToInt().coerceAtLeast(1)
                val dstH = (decoded.height * ratio).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, dstW, dstH, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            val outFile = File(internalWallpapersDir, "wallpaper_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { fos ->
                finalBmp.compress(Bitmap.CompressFormat.JPEG, 88, fos)
                fos.flush()
            }
            finalBmp.recycle()
            outFile.absolutePath
        }
    }

    fun isUriOrFileAccessible(uriOrPath: String?): Boolean {
        if (uriOrPath.isNullOrBlank()) return false
        return if (uriOrPath.startsWith("content://")) {
            runCatching {
                val doc = DocumentFile.fromSingleUri(context, Uri.parse(uriOrPath))
                doc?.exists() == true && doc.canRead()
            }.getOrDefault(false)
        } else {
            val f = File(uriOrPath)
            f.exists() && f.canRead()
        }
    }

    suspend fun runWriteReadSelfTest(treeUriString: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val testDir = ensureSubdirectory(treeUriString, "CyberDeck", "_SelfTest")
                ?: error("Cannot create CyberDeck/_SelfTest folder on selected Deck Drive")
            val testFile = createUniqueSafFile(testDir, "deck_probe.txt", "text/plain")
                ?: error("Cannot create test file on selected Deck Drive")
            val payload = "Offline Cyber Deck SAF probe ${System.currentTimeMillis()}"
            context.contentResolver.openOutputStream(testFile.uri)?.use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: error("Failed opening output stream on Deck Drive")

            val readBack = context.contentResolver.openInputStream(testFile.uri)?.use { input ->
                input.bufferedReader().readText()
            } ?: error("Failed reading test file back from Deck Drive")

            val deleted = testFile.delete()
            if (readBack != payload) {
                error("Read-back verification mismatch")
            }
            "Write + read-back verified (${if (deleted) "temp file cleaned up" else "cleanup skipped"})"
        }
    }

    fun queryDisplayNameAndSize(uri: Uri): Pair<String, Long> {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            return (file.name.ifBlank { "file" }) to file.length()
        }
        var name = uri.lastPathSegment ?: "document"
        var size = -1L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        }
        return name to size
    }

    fun sanitizeFileName(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            .trim()
            .ifBlank { "cyberdeck_file" }
            .take(120)
    }

    fun guessMimeType(name: String, explicitMime: String? = null): String {
        if (!explicitMime.isNullOrBlank() && explicitMime != "*/*") return explicitMime
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isNotBlank()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        }
        return when (ext) {
            "zim" -> "application/octet-stream"
            "md", "txt", "log", "csv", "json", "xml", "kt", "java" -> "text/plain"
            "pdf" -> "application/pdf"
            "mp3", "m4a", "flac", "wav", "ogg", "opus" -> "audio/*"
            "mp4", "mkv", "webm", "mov", "avi" -> "video/*"
            else -> "*/*"
        }
    }

    fun openPathOrUriWithSystem(pathOrUri: String, explicitMime: String? = null): Result<Unit> = runCatching {
        val uri = if (pathOrUri.startsWith("content://")) {
            Uri.parse(pathOrUri)
        } else {
            val file = File(pathOrUri)
            if (!file.exists()) error("File tidak ditemukan: ${file.name}")
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val mime = guessMimeType(pathOrUri.substringAfterLast('/'), explicitMime)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Buka dengan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun sanitizeSegment(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").trim().ifBlank { "CyberDeck" }
    }
}
