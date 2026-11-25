package com.offlinecyberdeck.archive

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.offlinecyberdeck.core.database.dao.ArchiveDao
import com.offlinecyberdeck.core.database.dao.SiteSettingDao
import com.offlinecyberdeck.core.database.entity.ArchivedPageEntity
import com.offlinecyberdeck.core.format.Formatters
import com.offlinecyberdeck.core.model.ArchiveStatus
import com.offlinecyberdeck.core.model.StorageTarget
import com.offlinecyberdeck.core.settings.SettingsRepository
import com.offlinecyberdeck.core.storage.DeckDriveManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class PageArchiveEngine(
    private val context: Context,
    private val archiveDao: ArchiveDao,
    private val siteSettingDao: SiteSettingDao,
    private val settingsRepo: SettingsRepository,
    private val driveManager: DeckDriveManager
) {
    val archivesFlow: Flow<List<ArchivedPageEntity>> = archiveDao.observeAll()

    suspend fun archiveHtmlSnapshot(
        sourceUrl: String,
        pageTitle: String,
        rawHtml: String,
        onProgress: (String) -> Unit = {}
    ): Result<ArchivedPageEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = settingsRepo.getSettings()
            val host = Formatters.extractHost(sourceUrl)
            val excludedList = settings.archiveExcludedHosts
                .split(',')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
            if (host in excludedList) {
                error("Host '$host' is in your archive exclusion list.")
            }
            val siteSetting = if (host.isNotEmpty()) siteSettingDao.getForHost(host) else null
            if (siteSetting != null && !siteSetting.allowArchiveCapture) {
                error("Archive capture is disabled for '$host' in site settings.")
            }
            if (rawHtml.isBlank()) {
                error("No HTML content captured from page.")
            }

            val id = UUID.randomUUID().toString()
            val cleanTitle = pageTitle.ifBlank { sourceUrl }.take(200)
            val useDeckDrive = settings.archiveStorageTarget == StorageTarget.DECK_DRIVE &&
                !settings.deckDriveTreeUri.isNullOrBlank() &&
                driveManager.status.value.isWritable

            val doc = Jsoup.parse(rawHtml, sourceUrl)
            // Remove script elements for safe static offline replay
            val hadScripts = doc.select("script").isNotEmpty()
            doc.select("script").remove()

            val readerText = doc.body()?.text()?.take(120_000) ?: ""

            // Map already-archived links so navigating between offline pages works
            val allLinks = doc.select("a[href]")
            for (link in allLinks) {
                val absHref = link.absUrl("href")
                if (absHref.isNotBlank()) {
                    val existing = archiveDao.findLatestByUrl(absHref)
                    if (existing != null) {
                        link.attr("data-cyberdeck-archive-id", existing.id)
                    }
                }
            }

            var savedAssetCount = 0
            var missingAssetCount = 0
            var totalSavedBytes = 0L

            val maxPerAssetBytes = 2L * 1024L * 1024L // 2 MB cap per asset for 4 GB Tab A9
            val maxAssetsPerPage = 40

            val safArchiveDir: DocumentFile? = if (useDeckDrive) {
                driveManager.ensureSubdirectory(
                    settings.deckDriveTreeUri!!,
                    "CyberDeck",
                    "Archives",
                    id
                )
            } else null

            val internalArchiveDir = File(driveManager.internalArchivesDir, id).apply {
                if (!useDeckDrive || safArchiveDir == null) mkdirs()
            }
            val actualTarget = if (useDeckDrive && safArchiveDir != null) {
                StorageTarget.DECK_DRIVE
            } else {
                StorageTarget.INTERNAL
            }

            if (settings.archiveCaptureResources) {
                onProgress("Capturing images & stylesheets...")
                val assetRefs = mutableListOf<Pair<org.jsoup.nodes.Element, String>>()
                doc.select("img[src]").forEach { el ->
                    val abs = el.absUrl("src")
                    if (abs.startsWith("http://") || abs.startsWith("https://")) {
                        assetRefs.add(el to abs)
                    }
                }
                doc.select("link[rel=stylesheet][href]").forEach { el ->
                    val abs = el.absUrl("href")
                    if (abs.startsWith("http://") || abs.startsWith("https://")) {
                        assetRefs.add(el to abs)
                    }
                }

                var processed = 0
                for ((element, assetUrl) in assetRefs) {
                    if (processed >= maxAssetsPerPage) {
                        missingAssetCount++
                        continue
                    }
                    processed++
                    val bytes = downloadSmallAsset(assetUrl, maxPerAssetBytes)
                    if (bytes == null) {
                        missingAssetCount++
                        continue
                    }
                    val assetName = buildAssetFileName(processed, assetUrl, element.tagName())
                    val written = writeArchiveAsset(
                        actualTarget = actualTarget,
                        internalDir = internalArchiveDir,
                        safDir = safArchiveDir,
                        fileName = assetName,
                        bytes = bytes
                    )
                    if (written) {
                        totalSavedBytes += bytes.size
                        savedAssetCount++
                        val relPath = "assets/$assetName"
                        if (element.tagName().equals("img", ignoreCase = true)) {
                            element.attr("src", relPath)
                            element.removeAttr("srcset")
                        } else {
                            element.attr("href", relPath)
                        }
                    } else {
                        missingAssetCount++
                    }
                }
            }

            // Inject minimal readable offline header banner or base styling
            if (doc.head() != null && doc.select("meta[charset]").isEmpty()) {
                doc.head().prependElement("meta").attr("charset", "UTF-8")
            }

            val finalHtmlBytes = doc.outerHtml().toByteArray(Charsets.UTF_8)
            totalSavedBytes += finalHtmlBytes.size

            val dirRef: String
            if (actualTarget == StorageTarget.DECK_DRIVE && safArchiveDir != null) {
                val htmlDoc = safArchiveDir.createFile("text/html", "index.html")
                    ?: error("Failed writing index.html to Deck Drive")
                context.contentResolver.openOutputStream(htmlDoc.uri)?.use { out ->
                    out.write(finalHtmlBytes)
                    out.flush()
                } ?: error("Cannot open output stream for Deck Drive archive")
                dirRef = safArchiveDir.uri.toString()
            } else {
                val outFile = File(internalArchiveDir, "index.html")
                outFile.writeBytes(finalHtmlBytes)
                dirRef = internalArchiveDir.absolutePath
            }

            val computedStatus = when {
                !settings.archiveCaptureResources -> ArchiveStatus.READER_SNAPSHOT
                missingAssetCount == 0 && !hadScripts -> ArchiveStatus.FULL_ARCHIVE
                savedAssetCount > 0 || missingAssetCount == 0 -> ArchiveStatus.PARTIAL_ARCHIVE
                else -> ArchiveStatus.READER_SNAPSHOT
            }

            val statusNote = buildString {
                when (computedStatus) {
                    ArchiveStatus.FULL_ARCHIVE -> append("All static HTML and referenced assets captured.")
                    ArchiveStatus.PARTIAL_ARCHIVE -> {
                        append("Snapshot saved ($savedAssetCount assets saved")
                        if (missingAssetCount > 0) append(", $missingAssetCount assets skipped/unavailable")
                        if (hadScripts) append(", interactive scripts stripped for offline safety")
                        append(").")
                    }
                    ArchiveStatus.READER_SNAPSHOT -> append("Text and HTML snapshot saved without external assets.")
                    else -> Unit
                }
            }

            val entry = ArchivedPageEntity(
                id = id,
                url = sourceUrl,
                title = cleanTitle,
                status = computedStatus,
                storageTarget = actualTarget,
                directoryPathOrUri = dirRef,
                entryFileName = "index.html",
                readerText = readerText,
                sizeBytes = totalSavedBytes,
                assetCount = savedAssetCount,
                missingAssetCount = missingAssetCount,
                statusNote = statusNote
            )
            archiveDao.upsert(entry)
            enforceQuotaIfNeeded(settings.archiveQuotaMb)
            entry
        }
    }

    suspend fun loadArchivedHtml(page: ArchivedPageEntity): String? = withContext(Dispatchers.IO) {
        if (page.directoryPathOrUri.isBlank()) return@withContext null
        if (page.directoryPathOrUri.startsWith("content://")) {
            val dirDoc = runCatching {
                DocumentFile.fromTreeUri(context, Uri.parse(page.directoryPathOrUri))
                    ?: DocumentFile.fromSingleUri(context, Uri.parse(page.directoryPathOrUri))
            }.getOrNull() ?: return@withContext null
            val htmlFile = dirDoc.findFile(page.entryFileName) ?: return@withContext null
            runCatching {
                context.contentResolver.openInputStream(htmlFile.uri)?.use {
                    it.bufferedReader().readText()
                }
            }.getOrNull()
        } else {
            val file = File(page.directoryPathOrUri, page.entryFileName)
            if (file.exists() && file.canRead()) file.readText(Charsets.UTF_8) else null
        }
    }

    suspend fun loadArchiveAssetStream(page: ArchivedPageEntity, relativePath: String): Pair<String, ByteArray>? =
        withContext(Dispatchers.IO) {
            val cleanRel = relativePath.trimStart('/')
            val assetName = cleanRel.substringAfterLast('/')
            val mime = guessMime(assetName)
            if (page.directoryPathOrUri.startsWith("content://")) {
                val dirDoc = runCatching {
                    DocumentFile.fromTreeUri(context, Uri.parse(page.directoryPathOrUri))
                }.getOrNull() ?: return@withContext null
                val assetsDoc = dirDoc.findFile("assets") ?: return@withContext null
                val fileDoc = assetsDoc.findFile(assetName) ?: return@withContext null
                val bytes = runCatching {
                    context.contentResolver.openInputStream(fileDoc.uri)?.use { it.readBytes() }
                }.getOrNull() ?: return@withContext null
                mime to bytes
            } else {
                val file = File(page.directoryPathOrUri, cleanRel)
                if (file.exists() && file.canRead()) mime to file.readBytes() else null
            }
        }

    suspend fun deleteArchive(page: ArchivedPageEntity) = withContext(Dispatchers.IO) {
        if (page.directoryPathOrUri.startsWith("content://")) {
            runCatching {
                DocumentFile.fromTreeUri(context, Uri.parse(page.directoryPathOrUri))?.delete()
            }
        } else if (page.directoryPathOrUri.isNotBlank()) {
            runCatching { File(page.directoryPathOrUri).deleteRecursively() }
        }
        archiveDao.delete(page)
    }

    private suspend fun enforceQuotaIfNeeded(quotaMb: Int) {
        if (quotaMb <= 0) return
        val maxBytes = quotaMb.toLong() * 1024L * 1024L
        var current = archiveDao.totalArchivedBytes()
        if (current <= maxBytes) return
        val oldestNonFavorites = archiveDao.getAllSnapshot()
            .filter { !it.isFavorite }
            .sortedBy { it.capturedAt }
        for (candidate in oldestNonFavorites) {
            if (current <= maxBytes) break
            current -= candidate.sizeBytes
            deleteArchive(candidate)
        }
    }

    private fun writeArchiveAsset(
        actualTarget: StorageTarget,
        internalDir: File,
        safDir: DocumentFile?,
        fileName: String,
        bytes: ByteArray
    ): Boolean {
        return if (actualTarget == StorageTarget.DECK_DRIVE && safDir != null) {
            val assetsDir = safDir.findFile("assets") ?: safDir.createDirectory("assets") ?: return false
            val doc = assetsDir.createFile(guessMime(fileName), fileName) ?: return false
            runCatching {
                context.contentResolver.openOutputStream(doc.uri)?.use {
                    it.write(bytes)
                    it.flush()
                }
                true
            }.getOrDefault(false)
        } else {
            runCatching {
                val assetsDir = File(internalDir, "assets").apply { mkdirs() }
                File(assetsDir, fileName).writeBytes(bytes)
                true
            }.getOrDefault(false)
        }
    }

    private fun downloadSmallAsset(urlString: String, maxBytes: Long): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 8000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "OfflineCyberDeck/1.0")
            }
            if (conn.responseCode !in 200..299) return null
            if (conn.contentLengthLong > maxBytes) return null
            conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0L
                while (true) {
                    val read = input.read(buf)
                    if (read == -1) break
                    total += read
                    if (total > maxBytes) return null
                    out.write(buf, 0, read)
                }
                out.toByteArray()
            }
        } catch (_: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun buildAssetFileName(index: Int, url: String, tag: String): String {
        val path = runCatching { Uri.parse(url).lastPathSegment }.getOrNull().orEmpty()
        val ext = path.substringAfterLast('.', "").lowercase().takeIf {
            it in setOf("png", "jpg", "jpeg", "webp", "gif", "svg", "css")
        } ?: if (tag.equals("link", ignoreCase = true)) "css" else "bin"
        return "asset_${index}.$ext"
    }

    private fun guessMime(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return if (ext == "css") "text/css"
        else MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
