package com.offlinecyberdeck.library

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.util.LruCache
import com.github.luben.zstd.ZstdInputStream
import org.tukaani.xz.SingleXZInputStream
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.EOFException
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.InputStream
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.InflaterInputStream
import kotlin.math.min

data class ZimHeaderInfo(
    val majorVersion: Int,
    val minorVersion: Int,
    val entryCount: Int,
    val clusterCount: Int,
    val urlPtrPos: Long,
    val titlePtrPos: Long,
    val clusterPtrPos: Long,
    val mimeListPos: Long,
    val mainPage: Long,
    val layoutPage: Long,
    val checksumPos: Long
)

data class ZimSearchHit(
    val entryIndex: Int,
    val fullKey: String,
    val title: String,
    val mimeType: String
)

data class ZimBlobResponse(
    val finalKey: String,
    val title: String,
    val mimeType: String,
    val bytes: ByteArray
)

private sealed interface ZimDirectoryEntry {
    val index: Int
    val namespace: Char
    val url: String
    val title: String
    val fullKey: String
        get() = "$namespace/$url"

    data class Content(
        override val index: Int,
        val mimeIndex: Int,
        override val namespace: Char,
        val clusterNumber: Int,
        val blobNumber: Int,
        override val url: String,
        override val title: String
    ) : ZimDirectoryEntry

    data class Redirect(
        override val index: Int,
        override val namespace: Char,
        val redirectIndex: Int,
        override val url: String,
        override val title: String
    ) : ZimDirectoryEntry

    data class Meta(
        override val index: Int,
        override val namespace: Char,
        override val url: String,
        override val title: String
    ) : ZimDirectoryEntry
}

private class DecodedSmallCluster(
    val offsets: LongArray,
    val payloadStartOffset: Long,
    val payloadBytes: ByteArray
)

/**
 * Low-RAM streaming OpenZIM (v5/v6) reader designed for 4 GB RAM tablets.
 * - Never loads the .zim archive into memory (uses FileChannel random-access seeks directly on File or SAF ParcelFileDescriptor).
 * - Binary-searches sorted URL/Title pointer tables on disk.
 * - Bounds cluster cache strictly to 3.5 MB total and skips caching clusters > 1.5 MB uncompressed.
 */
class ZimArchiveReader(
    private val pfd: ParcelFileDescriptor
) : Closeable {

    private val fis = FileInputStream(pfd.fileDescriptor)
    private val channel: FileChannel = fis.channel
    private val fd: FileDescriptor = pfd.fileDescriptor
    private val fileSize: Long = runCatching { channel.size() }.getOrNull()?.takeIf { it > 0L }
        ?: pfd.statSize.takeIf { it > 0L }
        ?: Long.MAX_VALUE
    private val ioLock = Any()

    val header: ZimHeaderInfo
    val titleEntryCount: Int
    val mimeTypes: List<String>

    // Strict 3.5 MB LRU cache for small uncompressed clusters (saves repeated XZ/Zstd decompress of CSS/JS/articles)
    private val clusterCache = object : LruCache<Int, DecodedSmallCluster>(3_500_000) {
        override fun sizeOf(key: Int, value: DecodedSmallCluster): Int {
            return value.payloadBytes.size.coerceAtLeast(1)
        }
    }

    init {
        val hdrBuf = readBytesAt(0L, 80)
        val bb = ByteBuffer.wrap(hdrBuf).order(ByteOrder.LITTLE_ENDIAN)
        val magic = bb.getInt(0)
        if (magic != 72173914) {
            close()
            error("Invalid ZIM file signature (magic=$magic)")
        }
        val major = bb.getShort(4).toInt() and 0xFFFF
        val minor = bb.getShort(6).toInt() and 0xFFFF
        val entryCount = (bb.getInt(24).toLong() and 0xFFFFFFFFL).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val clusterCount = (bb.getInt(28).toLong() and 0xFFFFFFFFL).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val urlPtrPos = bb.getLong(32)
        val titlePtrPos = bb.getLong(40)
        val clusterPtrPos = bb.getLong(48)
        val mimeListPos = bb.getLong(56)
        val mainPage = bb.getInt(64).toLong() and 0xFFFFFFFFL
        val layoutPage = bb.getInt(68).toLong() and 0xFFFFFFFFL
        val checksumPos = bb.getLong(72).let { if (it in 80L until fileSize) it else fileSize }

        header = ZimHeaderInfo(
            majorVersion = major,
            minorVersion = minor,
            entryCount = entryCount,
            clusterCount = clusterCount,
            urlPtrPos = urlPtrPos,
            titlePtrPos = titlePtrPos,
            clusterPtrPos = clusterPtrPos,
            mimeListPos = mimeListPos,
            mainPage = mainPage,
            layoutPage = layoutPage,
            checksumPos = checksumPos
        )
        titleEntryCount = if (titlePtrPos in 80L until clusterPtrPos) {
            ((clusterPtrPos - titlePtrPos) / 4L).toInt().coerceIn(0, entryCount)
        } else {
            0
        }
        mimeTypes = readMimeList(mimeListPos)
    }

    override fun close() {
        runCatching { clusterCache.evictAll() }
        runCatching { channel.close() }
        runCatching { fis.close() }
        runCatching { pfd.close() }
    }

    fun getMainPageKey(): String? {
        if (header.mainPage != 0xFFFFFFFFL && header.mainPage < header.entryCount.toLong()) {
            val resolved = resolveEntryIndex(header.mainPage.toInt())
            if (resolved != null) {
                val mime = mimeTypes.getOrNull(resolved.mimeIndex).orEmpty()
                if (mime.contains("html", ignoreCase = true) || mime.isBlank()) {
                    return resolved.fullKey
                }
            }
        }
        val commonMains = listOf(
            'W' to "mainPage",
            'C' to "mainPage",
            'C' to "index.html",
            'C' to "index.htm",
            'C' to "index.xhtml",
            'C' to "index",
            'C' to "Main_Page",
            'C' to "Halaman_Utama",
            'C' to "Halaman_utama",
            'C' to "Wikipedia:Halaman_utama",
            'C' to "Wikipedia",
            'C' to "Home",
            'C' to "home.html",
            'C' to "Start",
            'C' to "welcome.html",
            'A' to "index.html",
            'A' to "index.xhtml",
            'A' to "Main_Page",
            'A' to "Halaman_Utama",
            'A' to "Halaman_utama",
            'A' to "index"
        )
        for ((ns, url) in commonMains) {
            val idx = binarySearchUrlTable(ns, url)
            if (idx >= 0) {
                resolveEntryIndex(idx)?.let { return it.fullKey }
            }
        }
        return findFirstHtmlEntry()?.fullKey
    }

    private fun findFirstHtmlEntry(): ZimDirectoryEntry.Content? {
        // 1. Check title pointer table first (modern OpenZIM puts only front HTML articles here)
        if (titleEntryCount > 0 && header.titlePtrPos in 80L until fileSize) {
            val titleScanLimit = min(titleEntryCount, 300)
            for (t in 0 until titleScanLimit) {
                val entryIdx = readUInt32At(header.titlePtrPos + t.toLong() * 4L)
                if (entryIdx !in 0 until header.entryCount) continue
                val resolved = resolveEntryIndex(entryIdx) ?: continue
                if ((resolved.namespace == 'C' || resolved.namespace == 'A') && !resolved.url.startsWith("-/")) {
                    val mime = mimeTypes.getOrNull(resolved.mimeIndex).orEmpty()
                    if (mime.contains("html", ignoreCase = true)) {
                        return resolved
                    }
                }
            }
        }

        // 2. Probe namespaces C and A, jumping past '-/' CSS/JS/asset prefixes
        val probeNamespaces = listOf('C', 'A')
        for (ns in probeNamespaces) {
            for (startPrefix in listOf("A", "0", "")) {
                val startIdx = if (startPrefix.isEmpty()) lowerBoundNamespace(ns) else lowerBoundNamespaceUrl(ns, startPrefix)
                val scanLimit = min(header.entryCount, startIdx + 12000)
                var checked = 0
                var i = startIdx
                while (i < header.entryCount && checked < 4000 && i < scanLimit) {
                    val raw = readDirectoryEntry(i)
                    if (raw.namespace != ns) break
                    checked++
                    val e = if (raw is ZimDirectoryEntry.Redirect) resolveEntryIndex(raw.index) else raw as? ZimDirectoryEntry.Content
                    if (e != null && (e.namespace == 'C' || e.namespace == 'A') && !e.url.startsWith("-/")) {
                        val mime = mimeTypes.getOrNull(e.mimeIndex).orEmpty()
                        if (mime.contains("html", ignoreCase = true)) {
                            return e
                        }
                    }
                    i++
                }
            }
        }

        val fallbackLimit = min(header.entryCount, 4000)
        for (i in 0 until fallbackLimit) {
            val resolved = resolveEntryIndex(i)
            if (resolved != null && (resolved.namespace == 'C' || resolved.namespace == 'A') && !resolved.url.startsWith("-/")) {
                val mime = mimeTypes.getOrNull(resolved.mimeIndex).orEmpty()
                if (mime.contains("html", ignoreCase = true)) {
                    return resolved
                }
            }
        }
        return null
    }

    fun getSampleArticles(limit: Int = 30): List<ZimSearchHit> {
        val out = mutableListOf<ZimSearchHit>()
        val seen = HashSet<String>()

        // 1. Sample from title pointer table first (cleanest list of user-facing articles)
        if (titleEntryCount > 0 && header.titlePtrPos in 80L until fileSize) {
            val step = (titleEntryCount / limit.coerceAtLeast(1)).coerceAtLeast(1)
            var t = 0
            var visited = 0
            while (t < titleEntryCount && out.size < limit && visited < limit * 6) {
                visited++
                val entryIdx = readUInt32At(header.titlePtrPos + t.toLong() * 4L)
                if (entryIdx in 0 until header.entryCount) {
                    val e = resolveEntryIndex(entryIdx)
                    if (e != null && (e.namespace == 'C' || e.namespace == 'A') && !e.url.startsWith("-/")) {
                        val mime = mimeTypes.getOrNull(e.mimeIndex).orEmpty()
                        if (mime.contains("html", ignoreCase = true) && seen.add(e.fullKey)) {
                            out += ZimSearchHit(
                                entryIndex = e.index,
                                fullKey = e.fullKey,
                                title = e.title.ifBlank { e.url },
                                mimeType = mime
                            )
                        }
                    }
                }
                t += step
            }
        }

        // 2. Fallback scan across C and A namespaces starting from 'A' then namespace start
        for (ns in listOf('C', 'A')) {
            if (out.size >= limit) break
            val startIdx = lowerBoundNamespaceUrl(ns, "A").let { aIdx ->
                if (aIdx < header.entryCount) aIdx else lowerBoundNamespace(ns)
            }
            val span = (header.entryCount - startIdx).coerceAtLeast(1)
            val step = if (span > 4000) (span / 2500).coerceAtLeast(1) else 1
            var idx = startIdx
            var scanned = 0
            while (idx < header.entryCount && out.size < limit && scanned < 3000) {
                scanned++
                val raw = readDirectoryEntry(idx)
                if (raw.namespace != ns) break
                val e = if (raw is ZimDirectoryEntry.Redirect) resolveEntryIndex(raw.index) else raw as? ZimDirectoryEntry.Content
                if (e != null && (e.namespace == 'C' || e.namespace == 'A') && !e.url.startsWith("-/")) {
                    val mime = mimeTypes.getOrNull(e.mimeIndex).orEmpty()
                    if (mime.contains("html", ignoreCase = true) && seen.add(e.fullKey)) {
                        out += ZimSearchHit(
                            entryIndex = e.index,
                            fullKey = e.fullKey,
                            title = e.title.ifBlank { e.url },
                            mimeType = mime
                        )
                    }
                }
                idx += step
            }
        }
        return out
    }

    fun loadWebPath(rawPath: String): Result<ZimBlobResponse> = runCatching {
        val noQuery = rawPath.substringBefore('#').substringBefore('?')
        val cleanDecoded = decodeCleanPath(noQuery)
        val cleanRaw = normalizePathSegments(noQuery)
        val entry = findContentByWebPath(cleanDecoded)
            ?: (if (cleanRaw != cleanDecoded) findContentByWebPath(cleanRaw) else null)
            ?: error("Article or resource not found in ZIM: $cleanDecoded")
        val mime = mimeTypes.getOrNull(entry.mimeIndex)
            ?.substringBefore(';')
            ?.trim()
            ?.ifBlank { "application/octet-stream" }
            ?: "application/octet-stream"
        val bytes = readBlob(entry.clusterNumber, entry.blobNumber)
        ZimBlobResponse(
            finalKey = entry.fullKey,
            title = entry.title.ifBlank { entry.url },
            mimeType = mime,
            bytes = bytes
        )
    }

    fun searchTitles(query: String, limit: Int = 35): List<ZimSearchHit> {
        val q = query.trim()
        if (q.isBlank() || header.entryCount <= 0) return emptyList()
        val results = mutableListOf<ZimSearchHit>()
        val seenKeys = hashSetOf<String>()

        // 1. Fast binary prefix search in sorted titlePtrPos (OpenZIM sorts by <namespace><title>)
        if (titleEntryCount > 0 && header.titlePtrPos in 80L until fileSize) {
            val prefixTargets = listOf(q, "C$q", "A$q")
            for (target in prefixTargets) {
                if (results.size >= limit) break
                val prefixPos = lowerBoundTitle(target)
                val scanAround = min(titleEntryCount, prefixPos + 120)
                for (tIdx in prefixPos until scanAround) {
                    if (results.size >= limit) break
                    val entryIdx = readUInt32At(header.titlePtrPos + tIdx.toLong() * 4L)
                    if (entryIdx < 0 || entryIdx >= header.entryCount) continue
                    val resolved = resolveEntryIndex(entryIdx) ?: continue
                    if (resolved.namespace != 'C' && resolved.namespace != 'A') continue
                    val mime = mimeTypes.getOrNull(resolved.mimeIndex).orEmpty()
                    if (!mime.contains("html", ignoreCase = true)) continue
                    val displayTitle = resolved.title.ifBlank { resolved.url }
                    if (displayTitle.contains(q, ignoreCase = true) || resolved.url.contains(q, ignoreCase = true)) {
                        if (seenKeys.add(resolved.fullKey)) {
                            results += ZimSearchHit(resolved.index, resolved.fullKey, displayTitle, mime)
                        }
                    }
                }
            }
        }

        // 2. Also probe direct URL prefix match in C/A (for exact/camelCase article titles)
        if (results.size < limit) {
            for (ns in listOf('C', 'A')) {
                val urlVariants = linkedSetOf(q, q.replace(' ', '_'), q.replaceFirstChar { it.uppercase() }.replace(' ', '_'))
                for (uv in urlVariants) {
                    val startIdx = lowerBoundNamespaceUrl(ns, uv)
                    val endIdx = min(header.entryCount, startIdx + 25)
                    for (i in startIdx until endIdx) {
                        if (results.size >= limit) break
                        val raw = readDirectoryEntry(i)
                        if (raw.namespace != ns) break
                        val resolved = if (raw is ZimDirectoryEntry.Redirect) resolveEntryIndex(raw.index) else raw as? ZimDirectoryEntry.Content
                        if (resolved != null && !resolved.url.startsWith("-/")) {
                            val mime = mimeTypes.getOrNull(resolved.mimeIndex).orEmpty()
                            if (mime.contains("html", ignoreCase = true)) {
                                val t = resolved.title.ifBlank { resolved.url }
                                if ((t.contains(q, ignoreCase = true) || resolved.url.contains(q, ignoreCase = true)) &&
                                    seenKeys.add(resolved.fullKey)
                                ) {
                                    results += ZimSearchHit(resolved.index, resolved.fullKey, t, mime)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Bounded scan across C/A entries for substring matches if prefix hits were sparse
        if (results.size < limit) {
            val cStart = lowerBoundNamespace('C')
            val start = if (cStart < header.entryCount) cStart else 0
            val totalSpan = (header.entryCount - start).coerceAtLeast(1)
            val step = if (totalSpan > 8000) totalSpan / 3000 else 1
            var idx = start
            while (idx < header.entryCount && results.size < limit) {
                val raw = readDirectoryEntry(idx)
                val resolved = if (raw is ZimDirectoryEntry.Redirect) resolveEntryIndex(raw.index) else raw as? ZimDirectoryEntry.Content
                if (resolved != null && (resolved.namespace == 'C' || resolved.namespace == 'A') && !resolved.url.startsWith("-/")) {
                    val mime = mimeTypes.getOrNull(resolved.mimeIndex).orEmpty()
                    if (mime.contains("html", ignoreCase = true)) {
                        val t = resolved.title.ifBlank { resolved.url }
                        if ((t.contains(q, ignoreCase = true) || resolved.url.contains(q, ignoreCase = true)) &&
                            seenKeys.add(resolved.fullKey)
                        ) {
                            results += ZimSearchHit(resolved.index, resolved.fullKey, t, mime)
                        }
                    }
                }
                idx += step
            }
        }
        return results
    }

    private fun decodeCleanPath(rawPath: String): String {
        val noQuery = rawPath.substringBefore('#').substringBefore('?')
        val decoded = runCatching { URLDecoder.decode(noQuery.replace("+", "%2B"), "UTF-8") }
            .getOrElse { runCatching { Uri.decode(noQuery) }.getOrDefault(noQuery) }
        return normalizePathSegments(decoded)
    }

    private fun normalizePathSegments(path: String): String {
        val parts = path.trimStart('/').split('/')
        val stack = ArrayList<String>(parts.size)
        for (part in parts) {
            when (part) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                else -> stack += part
            }
        }
        return stack.joinToString("/")
    }

    private fun findContentByWebPath(cleanPath: String): ZimDirectoryEntry.Content? {
        if (cleanPath.isBlank()) {
            val main = header.mainPage
            if (main != 0xFFFFFFFFL && main < header.entryCount.toLong()) {
                return resolveEntryIndex(main.toInt())
            }
            return findFirstHtmlEntry()
        }

        val candidates = linkedSetOf<Pair<Char, String>>()
        fun addVariants(ns: Char, url: String) {
            val u = url.trimStart('/')
            if (u.isEmpty()) return
            candidates += ns to u
            if (u.contains(' ')) candidates += ns to u.replace(' ', '_')
            if (u.contains('_')) candidates += ns to u.replace('_', ' ')
            if (!u.endsWith(".html") && !u.contains('.')) {
                candidates += ns to "$u/index.html"
            }
        }

        if (cleanPath.length >= 2 && cleanPath[1] == '/') {
            val ns = cleanPath[0]
            val rest = cleanPath.substring(2)
            addVariants(ns, rest)
            addVariants('C', cleanPath)
            addVariants('A', cleanPath)
            addVariants('C', rest)
            addVariants('A', rest)
            addVariants('-', rest)
            addVariants('I', rest)
            addVariants('M', rest)
            if (rest.length >= 2 && rest[1] == '/') {
                val nestedNs = rest[0]
                val nestedRest = rest.substring(2)
                addVariants(nestedNs, nestedRest)
                addVariants('C', nestedRest)
                addVariants('A', nestedRest)
            }
        } else {
            addVariants('C', cleanPath)
            addVariants('A', cleanPath)
            addVariants('-', cleanPath)
            addVariants('I', cleanPath)
            addVariants('M', cleanPath)
            if (cleanPath.startsWith("wiki/")) {
                val stripped = cleanPath.removePrefix("wiki/")
                addVariants('C', stripped)
                addVariants('A', stripped)
            }
        }

        // Also handle deep relative asset paths like "C/foo/-/mw/style.css" or "A/bar/I/pic.png"
        for (marker in listOf("/-/", "/I/", "/C/", "/A/", "/M/")) {
            val idx = cleanPath.indexOf(marker)
            if (idx > 0) {
                val markerNs = marker[1]
                val tail = cleanPath.substring(idx + 3)
                addVariants(markerNs, tail)
                addVariants('C', "${markerNs}/$tail")
                addVariants('C', tail)
            }
        }

        for ((ns, url) in candidates) {
            val idx = binarySearchUrlTable(ns, url)
            if (idx >= 0) {
                val resolved = resolveEntryIndex(idx)
                if (resolved != null) return resolved
            }
        }
        return null
    }

    private fun resolveEntryIndex(startIndex: Int): ZimDirectoryEntry.Content? {
        var curIdx = startIndex
        repeat(12) {
            if (curIdx < 0 || curIdx >= header.entryCount) return null
            when (val entry = readDirectoryEntry(curIdx)) {
                is ZimDirectoryEntry.Content -> return entry
                is ZimDirectoryEntry.Redirect -> curIdx = entry.redirectIndex
                is ZimDirectoryEntry.Meta -> return null
            }
        }
        return null
    }

    private fun lowerBoundNamespace(ns: Char): Int =
        lowerBoundNamespaceUrl(ns, "")

    private fun lowerBoundNamespaceUrl(ns: Char, urlPrefix: String): Int {
        val targetBytes = (ns.toString() + urlPrefix).toByteArray(Charsets.UTF_8)
        var low = 0
        var high = header.entryCount
        while (low < high) {
            val mid = (low + high) ushr 1
            val keyBytes = readEntryKeyBytes(mid)
            if (compareUnsignedBytes(keyBytes, targetBytes) < 0) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return low
    }

    private fun binarySearchUrlTable(namespace: Char, url: String): Int {
        val targetBytes = (namespace.toString() + url).toByteArray(Charsets.UTF_8)
        var low = 0
        var high = header.entryCount - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val keyBytes = readEntryKeyBytes(mid)
            val cmp = compareUnsignedBytes(keyBytes, targetBytes)
            when {
                cmp == 0 -> return mid
                cmp < 0 -> low = mid + 1
                else -> high = mid - 1
            }
        }
        return -1
    }

    private fun lowerBoundTitle(queryKey: String): Int {
        if (titleEntryCount <= 0) return 0
        val hasNsPrefix = queryKey.length >= 2 && (queryKey[0] == 'C' || queryKey[0] == 'A')
        val targetBytes = (if (hasNsPrefix) queryKey[0] + queryKey.substring(1).lowercase() else queryKey.lowercase())
            .toByteArray(Charsets.UTF_8)
        var low = 0
        var high = titleEntryCount
        while (low < high) {
            val mid = (low + high) ushr 1
            val entryIdx = readUInt32At(header.titlePtrPos + mid.toLong() * 4L)
            val titleBytes = if (entryIdx in 0 until header.entryCount) {
                val e = readDirectoryEntry(entryIdx)
                val baseTitle = e.title.ifBlank { e.url }.lowercase()
                val keyStr = if (hasNsPrefix) "${e.namespace}$baseTitle" else baseTitle
                keyStr.toByteArray(Charsets.UTF_8)
            } else ByteArray(0)
            if (compareUnsignedBytes(titleBytes, targetBytes) < 0) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return low
    }

    private fun compareUnsignedBytes(a: ByteArray, b: ByteArray): Int {
        val len = min(a.size, b.size)
        for (i in 0 until len) {
            val av = a[i].toInt() and 0xFF
            val bv = b[i].toInt() and 0xFF
            if (av != bv) return av - bv
        }
        return a.size - b.size
    }

    private fun readEntryKeyBytes(entryIdx: Int): ByteArray {
        val dirPos = readLongAt(header.urlPtrPos + entryIdx.toLong() * 8L)
        val fixed = readBytesAt(dirPos, 16)
        val mime = ((fixed[1].toInt() and 0xFF) shl 8) or (fixed[0].toInt() and 0xFF)
        val nsByte = fixed[3]
        val urlStart = when (mime) {
            0xFFFF -> dirPos + 12L
            0xFFFE, 0xFFFD -> dirPos + 8L
            else -> dirPos + 16L
        }
        val urlBytes = readNullTerminatedBytes(urlStart, 1024)
        return ByteArray(1 + urlBytes.size).apply {
            this[0] = nsByte
            System.arraycopy(urlBytes, 0, this, 1, urlBytes.size)
        }
    }

    private fun readDirectoryEntry(entryIdx: Int): ZimDirectoryEntry {
        val dirPos = readLongAt(header.urlPtrPos + entryIdx.toLong() * 8L)
        val fixed = readBytesAt(dirPos, 16)
        val bb = ByteBuffer.wrap(fixed).order(ByteOrder.LITTLE_ENDIAN)
        val mime = bb.getShort(0).toInt() and 0xFFFF
        val ns = (fixed[3].toInt() and 0xFF).toChar()

        return when (mime) {
            0xFFFF -> {
                val redirectIdx = (bb.getInt(8).toLong() and 0xFFFFFFFFL).toInt()
                val (url, nextPos) = readUtf8WithNextPos(dirPos + 12L)
                val (title, _) = readUtf8WithNextPos(nextPos)
                ZimDirectoryEntry.Redirect(entryIdx, ns, redirectIdx, url, title)
            }
            0xFFFE, 0xFFFD -> {
                val (url, nextPos) = readUtf8WithNextPos(dirPos + 8L)
                val (title, _) = readUtf8WithNextPos(nextPos)
                ZimDirectoryEntry.Meta(entryIdx, ns, url, title)
            }
            else -> {
                val clusterNum = (bb.getInt(8).toLong() and 0xFFFFFFFFL).toInt()
                val blobNum = (bb.getInt(12).toLong() and 0xFFFFFFFFL).toInt()
                val (url, nextPos) = readUtf8WithNextPos(dirPos + 16L)
                val (title, _) = readUtf8WithNextPos(nextPos)
                ZimDirectoryEntry.Content(entryIdx, mime, ns, clusterNum, blobNum, url, title)
            }
        }
    }

    private fun readBlob(clusterNumber: Int, blobNumber: Int): ByteArray {
        if (clusterNumber < 0 || clusterNumber >= header.clusterCount) {
            error("Invalid cluster number $clusterNumber")
        }

        clusterCache.get(clusterNumber)?.let { cached ->
            return extractFromCachedCluster(cached, blobNumber)
        }

        val clusterStart = readLongAt(header.clusterPtrPos + clusterNumber.toLong() * 8L)
        var nextClusterOffset = Long.MAX_VALUE
        val lookaheadEnd = min(header.clusterCount, clusterNumber + 64)
        for (c in (clusterNumber + 1) until lookaheadEnd) {
            val cand = readLongAt(header.clusterPtrPos + c.toLong() * 8L)
            if (cand > clusterStart && cand < nextClusterOffset) {
                nextClusterOffset = cand
                if (c == clusterNumber + 1) break
            }
        }
        val clusterEnd = when {
            nextClusterOffset < Long.MAX_VALUE -> nextClusterOffset
            header.checksumPos > clusterStart -> header.checksumPos
            else -> fileSize
        }
        val clusterDataStart = clusterStart + 1L
        val clusterCompLen = (clusterEnd - clusterDataStart).coerceAtLeast(0L)
        val infoByte = readBytesAt(clusterStart, 1).firstOrNull()?.toInt()?.and(0xFF)
            ?: error("Unable to read ZIM cluster header at offset $clusterStart")
        val compression = infoByte and 0x0F
        val extended = (infoByte and 0x10) != 0
        val offsetSize = if (extended) 8 else 4

        openDecompressedClusterStream(clusterDataStart, clusterCompLen, compression).use { stream ->
            val firstOffsetBytes = readExact(stream, offsetSize)
            val firstOffset = parseOffset(firstOffsetBytes, extended)
            if (firstOffset < offsetSize || firstOffset > 8_000_000L) {
                error("Corrupt or oversized ZIM cluster offset table ($firstOffset)")
            }
            val blobCount = (firstOffset / offsetSize).toInt() - 1
            if (blobNumber < 0 || blobNumber >= blobCount) {
                error("Blob index $blobNumber out of bounds (count=$blobCount)")
            }

            val offsets = LongArray(blobCount + 1)
            offsets[0] = firstOffset
            val remainingTableBytes = readExact(stream, blobCount * offsetSize)
            for (i in 1..blobCount) {
                offsets[i] = parseOffsetAt(remainingTableBytes, (i - 1) * offsetSize, extended)
            }

            val totalClusterDecompressed = offsets[blobCount]
            val blobStart = offsets[blobNumber]
            val blobEnd = offsets[blobNumber + 1]
            if (blobStart < firstOffset || blobEnd < blobStart) {
                error("Invalid ZIM blob offsets ($blobStart..$blobEnd)")
            }
            val blobLen = (blobEnd - blobStart).coerceAtLeast(0L)
            if (blobLen > 16L * 1024L * 1024L) {
                error("ZIM entry too large (${blobLen / (1024 * 1024)} MB) for 4 GB RAM safety limit")
            }

            // If entire uncompressed cluster payload is <= 1.5 MB, cache it so sibling CSS/HTML/assets hit RAM instantly
            val payloadSize = totalClusterDecompressed - firstOffset
            if (payloadSize in 1..1_500_000L) {
                val allPayload = readExact(stream, payloadSize.toInt())
                val cached = DecodedSmallCluster(offsets, firstOffset, allPayload)
                clusterCache.put(clusterNumber, cached)
                return extractFromCachedCluster(cached, blobNumber)
            }

            // Otherwise stream-skip directly to requested blob without allocating the rest of the cluster
            val toSkip = blobStart - firstOffset
            if (toSkip > 0L) {
                skipExact(stream, toSkip)
            }
            return readExact(stream, blobLen.toInt())
        }
    }

    private fun extractFromCachedCluster(cluster: DecodedSmallCluster, blobNumber: Int): ByteArray {
        val start = (cluster.offsets[blobNumber] - cluster.payloadStartOffset).toInt()
        val end = (cluster.offsets[blobNumber + 1] - cluster.payloadStartOffset).toInt()
        return cluster.payloadBytes.copyOfRange(
            start.coerceIn(0, cluster.payloadBytes.size),
            end.coerceIn(start, cluster.payloadBytes.size)
        )
    }

    private fun openDecompressedClusterStream(
        dataStart: Long,
        compressedLen: Long,
        compression: Int
    ): InputStream {
        val slice = BufferedInputStream(ChannelSliceInputStream(channel, fd, dataStart, compressedLen, ioLock), 16384)
        return when (compression) {
            0, 1 -> slice
            2 -> InflaterInputStream(slice)
            4 -> SingleXZInputStream(slice)
            5 -> ZstdInputStream(slice).apply { setContinuous(false) }
            else -> {
                slice.close()
                error("Unsupported ZIM compression type ($compression)")
            }
        }
    }

    private fun parseOffset(bytes: ByteArray, extended: Boolean): Long =
        parseOffsetAt(bytes, 0, extended)

    private fun parseOffsetAt(bytes: ByteArray, pos: Int, extended: Boolean): Long {
        val bb = ByteBuffer.wrap(bytes, pos, if (extended) 8 else 4).order(ByteOrder.LITTLE_ENDIAN)
        return if (extended) bb.getLong(0) else (bb.getInt(0).toLong() and 0xFFFFFFFFL)
    }

    private fun readMimeList(pos: Long): List<String> {
        val list = mutableListOf<String>()
        var cursor = pos
        while (list.size < 512 && cursor < fileSize) {
            val (str, next) = readUtf8WithNextPos(cursor)
            if (str.isEmpty()) break
            list += str
            cursor = next
        }
        return list
    }

    private fun readLongAt(pos: Long): Long {
        val b = readBytesAt(pos, 8)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getLong(0)
    }

    private fun readUInt32At(pos: Long): Int {
        val b = readBytesAt(pos, 4)
        return (ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt(0).toLong() and 0xFFFFFFFFL).toInt()
    }

    private fun readUtf8WithNextPos(startPos: Long): Pair<String, Long> {
        val raw = readNullTerminatedBytes(startPos, 2048)
        return raw.toString(Charsets.UTF_8) to (startPos + raw.size + 1L)
    }

    private fun readNullTerminatedBytes(startPos: Long, maxLen: Int): ByteArray {
        val chunk = readBytesAt(startPos, min(maxLen.toLong(), (fileSize - startPos).coerceAtLeast(0L)).toInt())
        val zeroIdx = chunk.indexOf(0)
        return if (zeroIdx >= 0) chunk.copyOf(zeroIdx) else chunk
    }

    private fun readBytesAt(pos: Long, len: Int): ByteArray {
        if (len <= 0) return ByteArray(0)
        val out = ByteArray(len)
        val buf = ByteBuffer.wrap(out)
        var readCount = 0
        synchronized(ioLock) {
            var offset = pos
            val channelOk = runCatching {
                while (buf.hasRemaining()) {
                    val n = channel.read(buf, offset)
                    if (n <= 0) break
                    offset += n
                }
                true
            }.getOrDefault(false)
            if (channelOk && buf.position() > 0) {
                readCount = buf.position()
            } else {
                var off = 0
                var curPos = pos
                while (off < len) {
                    val n = runCatching { Os.pread(fd, out, off, len - off, curPos) }.getOrDefault(-1)
                    if (n <= 0) break
                    off += n
                    curPos += n
                }
                readCount = off
            }
        }
        return if (readCount == len) out else out.copyOf(readCount)
    }

    private fun readExact(stream: InputStream, count: Int): ByteArray {
        val out = ByteArray(count)
        var off = 0
        while (off < count) {
            val n = stream.read(out, off, count - off)
            if (n < 0) throw EOFException("Unexpected EOF in ZIM stream (read $off/$count)")
            off += n
        }
        return out
    }

    private fun skipExact(stream: InputStream, totalToSkip: Long) {
        var remaining = totalToSkip
        val scratch = ByteArray(8192)
        while (remaining > 0L) {
            val toRead = min(scratch.size.toLong(), remaining).toInt()
            val n = stream.read(scratch, 0, toRead)
            if (n < 0) throw EOFException("Unexpected EOF while skipping ZIM stream")
            remaining -= n
        }
    }
}

private class ChannelSliceInputStream(
    private val channel: FileChannel,
    private val fd: FileDescriptor,
    startOffset: Long,
    length: Long,
    private val lock: Any
) : InputStream() {
    private var cursor = startOffset
    private val end = startOffset + length
    private val single = ByteArray(1)

    override fun read(): Int {
        val n = read(single, 0, 1)
        return if (n <= 0) -1 else (single[0].toInt() and 0xFF)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        val remaining = end - cursor
        if (remaining <= 0L) return -1
        val toRead = min(len.toLong(), remaining).toInt()
        val buf = ByteBuffer.wrap(b, off, toRead)
        val readBytes = synchronized(lock) {
            val ch = runCatching { channel.read(buf, cursor) }.getOrNull()
            if (ch != null && ch > 0) {
                ch
            } else {
                val p = runCatching { Os.pread(fd, b, off, toRead, cursor) }.getOrDefault(-1)
                if (p > 0) p else (ch ?: -1)
            }
        }
        if (readBytes <= 0) return -1
        cursor += readBytes
        return readBytes
    }
}
