package com.offlinecyberdeck.search

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.offlinecyberdeck.core.database.CyberDeckDatabase
import com.offlinecyberdeck.core.database.entity.RecentSearchEntity
import com.offlinecyberdeck.core.format.Formatters
import com.offlinecyberdeck.core.model.SearchResultType
import com.offlinecyberdeck.core.model.UnifiedSearchResult
import com.offlinecyberdeck.core.storage.DeckDriveManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class UnifiedSearchRepository(
    private val context: Context,
    private val db: CyberDeckDatabase,
    private val driveManager: DeckDriveManager
) {
    val recentSearchesFlow: Flow<List<RecentSearchEntity>> = db.recentSearchDao().observeRecent()

    suspend fun searchOffline(
        query: String,
        enabledTypes: Set<SearchResultType> = SearchResultType.entries.toSet()
    ): List<UnifiedSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val lower = trimmed.lowercase(Locale.US)
        val results = mutableListOf<UnifiedSearchResult>()

        if (SearchResultType.ARCHIVE in enabledTypes) {
            db.archiveDao().search(trimmed).forEach { page ->
                val score = scoreMatch(lower, page.title, page.url, page.tags, page.readerText)
                results += UnifiedSearchResult(
                    id = page.id,
                    type = SearchResultType.ARCHIVE,
                    title = page.title,
                    subtitle = "${page.status.label} • ${page.url}",
                    tags = page.tags,
                    score = score + 12,
                    isAvailableOffline = driveManager.isUriOrFileAccessible(page.directoryPathOrUri),
                    targetRef = page.id
                )
            }
        }

        if (SearchResultType.LIBRARY in enabledTypes) {
            db.libraryDao().search(trimmed).forEach { item ->
                val score = scoreMatch(lower, item.title, item.fileName, item.tags, item.description)
                results += UnifiedSearchResult(
                    id = item.id,
                    type = SearchResultType.LIBRARY,
                    title = item.title,
                    subtitle = "${item.category.label} • ${item.fileType.label}",
                    tags = item.tags,
                    score = score + 10,
                    isAvailableOffline = driveManager.isUriOrFileAccessible(item.fileUri),
                    targetRef = item.id
                )
            }
        }

        if (SearchResultType.MEDIA in enabledTypes) {
            db.mediaDao().search(trimmed).forEach { media ->
                val score = scoreMatch(
                    lower,
                    media.title,
                    media.channel,
                    media.tags,
                    media.localFileName.orEmpty()
                )
                val hasLocal = !media.localFileUri.isNullOrBlank() &&
                    driveManager.isUriOrFileAccessible(media.localFileUri)
                results += UnifiedSearchResult(
                    id = media.id,
                    type = SearchResultType.MEDIA,
                    title = media.title,
                    subtitle = buildString {
                        append(if (hasLocal) "Available Offline" else "Metadata Only (No Local File)")
                        if (media.channel.isNotBlank()) append(" • ${media.channel}")
                    },
                    tags = media.tags,
                    score = score + if (hasLocal) 8 else 3,
                    isAvailableOffline = hasLocal,
                    targetRef = media.id
                )
            }
        }

        if (SearchResultType.BOOKMARK in enabledTypes) {
            db.bookmarkDao().search(trimmed).forEach { bm ->
                val score = scoreMatch(lower, bm.title, bm.url, bm.tags, bm.folder)
                results += UnifiedSearchResult(
                    id = bm.id,
                    type = SearchResultType.BOOKMARK,
                    title = bm.title,
                    subtitle = bm.url,
                    tags = bm.tags,
                    score = score + 6,
                    isAvailableOffline = false,
                    targetRef = bm.url
                )
            }
        }

        if (SearchResultType.HISTORY in enabledTypes) {
            db.historyDao().search(trimmed).forEach { hist ->
                val score = scoreMatch(lower, hist.title, hist.url, "", "")
                results += UnifiedSearchResult(
                    id = hist.id,
                    type = SearchResultType.HISTORY,
                    title = hist.title.ifBlank { hist.url },
                    subtitle = hist.url,
                    tags = "",
                    score = score + 2,
                    isAvailableOffline = false,
                    targetRef = hist.url
                )
            }
        }

        if (SearchResultType.FILE in enabledTypes) {
            searchDeviceAndExternalFiles(trimmed, lower, limit = 65).forEach { fileRes ->
                results += fileRes
            }
        }

        results.sortedByDescending { it.score }
    }

    private suspend fun searchDeviceAndExternalFiles(
        trimmed: String,
        queryLower: String,
        limit: Int
    ): List<UnifiedSearchResult> {
        val out = mutableListOf<UnifiedSearchResult>()
        val seenRefs = HashSet<String>()
        val seenNamesAndSize = HashSet<String>()

        fun addCandidate(
            name: String,
            ref: String,
            volumeLabel: String,
            sizeBytes: Long,
            mimeType: String?
        ) {
            if (out.size >= limit) return
            if (!seenRefs.add(ref)) return
            val sig = "${name.lowercase(Locale.US)}_$sizeBytes"
            if (!seenNamesAndSize.add(sig)) return
            val score = scoreMatch(queryLower, name, volumeLabel, mimeType.orEmpty(), ref)
            out += UnifiedSearchResult(
                id = "file_${ref.hashCode()}_${out.size}",
                type = SearchResultType.FILE,
                title = name,
                subtitle = "$volumeLabel • ${Formatters.formatBytes(sizeBytes)} • ${ref.takeLast(64)}",
                tags = name.substringAfterLast('.', "").uppercase(Locale.US),
                score = score + 9,
                isAvailableOffline = true,
                targetRef = ref,
                mimeType = mimeType,
                fileSizeBytes = sizeBytes,
                volumeLabel = volumeLabel
            )
        }

        // 1. MediaStore.Files query (searches Internal Shared Storage & indexed SD Cards for ANY file type)
        runCatching {
            val filesUri = MediaStore.Files.getContentUri("external")
            val projection = buildList {
                add(MediaStore.Files.FileColumns._ID)
                add(MediaStore.Files.FileColumns.DISPLAY_NAME)
                add(MediaStore.Files.FileColumns.MIME_TYPE)
                add(MediaStore.Files.FileColumns.SIZE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(MediaStore.Files.FileColumns.VOLUME_NAME)
                }
                @Suppress("DEPRECATION")
                add(MediaStore.Files.FileColumns.DATA)
            }.toTypedArray()

            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            val args = arrayOf("%$trimmed%")

            context.contentResolver.query(
                filesUri,
                projection,
                selection,
                args,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val volIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Files.FileColumns.VOLUME_NAME)
                } else -1
                @Suppress("DEPRECATION")
                val dataIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                while (cursor.moveToNext() && out.size < limit) {
                    val id = cursor.getLong(idIdx)
                    val dataPath = if (dataIdx >= 0) cursor.getString(dataIdx) else null
                    val name = (if (nameIdx >= 0) cursor.getString(nameIdx) else null)
                        ?.takeIf { it.isNotBlank() }
                        ?: dataPath?.substringAfterLast('/')
                        ?: continue
                    if (name.startsWith(".")) continue
                    val sizeBytes = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else null
                    val vol = if (volIdx >= 0) cursor.getString(volIdx).orEmpty() else ""

                    val usableFile = dataPath?.let { File(it) }?.takeIf { it.exists() && it.canRead() && !it.isDirectory }
                    val targetRef = usableFile?.absolutePath
                        ?: ContentUris.withAppendedId(filesUri, id).toString()

                    val label = when {
                        dataPath?.contains("/storage/emulated") == true || vol.equals("external_primary", true) -> "Internal Storage"
                        vol.isNotBlank() -> "SD / USB ($vol)"
                        else -> "Penyimpanan Perangkat"
                    }
                    addCandidate(name, targetRef, label, sizeBytes, mime)
                }
            }
        }

        // 2. Direct filesystem walk across Internal Shared, App Vault/Downloads, and Removable SD/USB mount paths
        val roots = linkedMapOf<File, String>()
        roots[driveManager.internalDownloadsDir] = "App Downloads"
        roots[driveManager.internalArchivesDir] = "App Archives"
        roots[driveManager.internalVaultDir] = "App Vault"
        runCatching { Environment.getExternalStorageDirectory() }.getOrNull()?.let { ext ->
            if (ext.exists() && ext.canRead()) roots[ext] = "Internal Storage"
        }
        driveManager.detectRemovableExternalVolumes().forEach { vol ->
            val mp = vol.mountPath?.let { File(it) }
            if (mp != null && mp.exists() && mp.canRead()) {
                roots[mp] = vol.label
            }
        }

        for ((rootDir, label) in roots) {
            if (out.size >= limit) break
            val queue = ArrayDeque<Pair<File, Int>>()
            queue.add(rootDir to 0)
            var visitedDirs = 0
            while (queue.isNotEmpty() && visitedDirs < 250 && out.size < limit) {
                val (dir, depth) = queue.removeFirst()
                visitedDirs++
                val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
                for (child in children) {
                    val name = child.name
                    if (name.startsWith(".")) continue
                    if (child.isDirectory) {
                        if (depth < 6 && name != "Android" && name != "cache") {
                            queue.add(child to depth + 1)
                        }
                    } else if (child.isFile && name.contains(queryLower, ignoreCase = true)) {
                        addCandidate(name, child.absolutePath, label, child.length(), null)
                    }
                }
            }
        }

        // 3. SAF Trees (Deck Drive + extra user-selected SD/USB folders)
        val settings = runCatching { db.settingsDao().get() }.getOrNull()
        val safUris = buildList {
            settings?.deckDriveTreeUri?.takeIf { it.isNotBlank() }?.let { add(it) }
            settings?.extraMediaSafUris
                ?.split('\n')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.forEach { add(it) }
        }.distinct()

        for (treeStr in safUris) {
            if (out.size >= limit) break
            runCatching {
                val docRoot = DocumentFile.fromTreeUri(context, Uri.parse(treeStr)) ?: return@runCatching
                if (!docRoot.exists() || !docRoot.canRead()) return@runCatching
                val driveLabel = "Eksternal (${docRoot.name ?: "SAF"})"
                val queue = ArrayDeque<DocumentFile>()
                queue.add(docRoot)
                var visited = 0
                while (queue.isNotEmpty() && visited < 140 && out.size < limit) {
                    val cur = queue.removeFirst()
                    visited++
                    for (child in cur.listFiles()) {
                        val name = child.name ?: continue
                        if (name.startsWith(".")) continue
                        if (child.isDirectory) {
                            queue.add(child)
                        } else if (child.isFile && name.contains(queryLower, ignoreCase = true)) {
                            addCandidate(name, child.uri.toString(), driveLabel, child.length(), child.type)
                        }
                    }
                }
            }
        }

        return out
    }

    suspend fun recordSearch(query: String) {
        val clean = query.trim()
        if (clean.isNotEmpty()) {
            db.recentSearchDao().upsert(RecentSearchEntity(query = clean))
        }
    }

    suspend fun clearRecentSearches() {
        db.recentSearchDao().clearAll()
    }

    private fun scoreMatch(
        queryLower: String,
        title: String,
        secondary: String,
        tags: String,
        body: String
    ): Int {
        val t = title.lowercase(Locale.US)
        val s = secondary.lowercase(Locale.US)
        val tg = tags.lowercase(Locale.US)
        val b = body.lowercase(Locale.US)
        var score = 0
        if (t == queryLower) score += 100
        else if (t.startsWith(queryLower)) score += 65
        else if (t.contains(queryLower)) score += 45
        if (tg.contains(queryLower)) score += 30
        if (s.contains(queryLower)) score += 20
        if (b.contains(queryLower)) score += 10
        return score
    }
}
