package com.offlinecyberdeck.ai

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import com.llamatik.core.platform.GenStream
import com.llamatik.core.platform.LlamaBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object LlamaHelper {

    const val SYSTEM_PROMPT =
        "Kamu adalah asisten AI offline di dalam Cyber Deck. Jawab singkat, jelas, dalam Bahasa Indonesia, gaya cyberpunk."

    const val TARGET_N_CTX = 2048
    const val TARGET_N_THREADS = 4

    private val modelMutex = Mutex()
    private val helperScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val generatingFlag = AtomicBoolean(false)

    @Volatile
    private var loaded: Boolean = false

    @Volatile
    private var currentModelName: String? = null

    @Volatile
    private var currentModelUri: String? = null

    @Volatile
    private var activePfd: ParcelFileDescriptor? = null

    fun isModelLoaded(): Boolean = loaded

    fun isGenerating(): Boolean = generatingFlag.get()

    fun getActiveModelName(): String? = currentModelName

    fun getActiveModelUri(): String? = currentModelUri

    suspend fun loadModel(
        context: Context,
        modelUri: Uri,
        fileName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        modelMutex.withLock {
            runCatching {
                val uriStr = modelUri.toString()
                if (loaded && currentModelUri == uriStr) {
                    return@runCatching fileName
                }

                unloadInternal()

                // Prioritaskan jalur POSIX langsung (/storage/<UUID>/AI_Models/...) agar mmap zero-copy
                // berjalan stabil di exFAT SSD tanpa copy ke internal, lalu fallback ke /proc/self/fd/<fd>.
                val candidates = resolveZeroCopyMmapCandidates(context, modelUri, fileName)
                if (candidates.isEmpty()) {
                    val hasAllFiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Environment.isExternalStorageManager()
                    } else true
                    error(
                        if (!hasAllFiles) {
                            "Tidak dapat membuka FD/path model '$fileName'. Aktifkan 'Izinkan pengelolaan semua file' agar SSD exFAT bisa dibaca langsung via mmap."
                        } else {
                            "Gagal membuka '$fileName' di SSD eksternal (SAF/POSIX tidak dapat diakses)."
                        }
                    )
                }

                var initOk = false
                var usedLabel = ""
                for ((label, path) in candidates) {
                    LlamaBridge.updateGenerateParams(
                        temperature = 0.7f,
                        maxTokens = 512,
                        topP = 0.9f,
                        topK = 40,
                        repeatPenalty = 1.1f,
                        contextLength = TARGET_N_CTX,
                        numThreads = TARGET_N_THREADS,
                        useMmap = true,
                        flashAttention = false,
                        batchSize = 256,
                        gpuLayers = 0
                    )
                    if (LlamaBridge.initGenerateModel(path)) {
                        initOk = true
                        usedLabel = label
                        break
                    }
                    runCatching { LlamaBridge.shutdown() }
                }

                if (!initOk) {
                    closePfdQuietly()
                    val allFilesOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Environment.isExternalStorageManager()
                    } else true
                    error(
                        buildString {
                            append("llama.cpp gagal memuat '$fileName' ($usedLabel")
                            append(candidates.joinToString { it.first })
                            append(").")
                            if (!allFilesOk) {
                                append(" Coba aktifkan Izin Akses Semua File untuk membaca langsung file .gguf di SSD OTG via mmap.")
                            }
                        }
                    )
                }

                loaded = true
                currentModelName = fileName
                currentModelUri = uriStr
                fileName
            }.onFailure {
                unloadInternal()
            }
        }
    }

    fun generateResponse(
        userPrompt: String,
        conversationHistory: List<Pair<String, String>>,
        onToken: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!loaded) {
            onError("Model belum dimuat.")
            return
        }
        if (!generatingFlag.compareAndSet(false, true)) {
            onError("Model masih memproses respons sebelumnya.")
            return
        }

        val compactContext = buildCompactHistoryBlock(conversationHistory)

        helperScope.launch {
            try {
                var finished = false
                LlamaBridge.generateStreamWithContext(
                    systemPrompt = SYSTEM_PROMPT,
                    contextBlock = compactContext,
                    userPrompt = userPrompt,
                    callback = object : GenStream {
                        override fun onDelta(text: String) {
                            if (text.isNotEmpty()) {
                                onToken(text)
                            }
                        }

                        override fun onComplete() {
                            if (!finished) {
                                finished = true
                                generatingFlag.set(false)
                                onComplete()
                            }
                        }

                        override fun onError(message: String) {
                            if (!finished) {
                                finished = true
                                generatingFlag.set(false)
                                onError(message.ifBlank { "Terjadi kesalahan saat inferensi model." })
                            }
                        }
                    }
                )
                if (!finished && generatingFlag.compareAndSet(true, false)) {
                    onComplete()
                }
            } catch (t: Throwable) {
                generatingFlag.set(false)
                onError(t.message ?: "Gagal menjalankan inferensi LLM offline.")
            }
        }
    }

    fun stopGeneration() {
        if (generatingFlag.get()) {
            runCatching { LlamaBridge.nativeCancelGenerate() }
        }
    }

    fun unloadModel() {
        stopGeneration()
        runCatching { unloadInternal() }
    }

    private fun unloadInternal() {
        if (generatingFlag.get()) {
            runCatching { LlamaBridge.nativeCancelGenerate() }
            generatingFlag.set(false)
        }
        if (loaded) {
            runCatching { LlamaBridge.shutdown() }
        }
        loaded = false
        currentModelName = null
        currentModelUri = null
        closePfdQuietly()
    }

    private fun resolveZeroCopyMmapCandidates(
        context: Context,
        modelUri: Uri,
        fileName: String
    ): List<Pair<String, String>> {
        val candidates = ArrayList<Pair<String, String>>()
        val seenPaths = HashSet<String>()

        fun addFileCandidate(label: String, file: File?) {
            if (file != null && file.exists() && file.isFile && file.canRead() && file.length() > 0L) {
                if (seenPaths.add(file.absolutePath)) {
                    candidates.add(label to file.absolutePath)
                }
            }
        }

        if (modelUri.scheme == "file") {
            addFileCandidate("POSIX", File(modelUri.path.orEmpty()))
        }

        // 1. Direct path parsed from SAF Document ID
        addFileCandidate("SAF->POSIX", resolveDirectFileFromDocumentUri(modelUri))

        // 2. Direct scan of mounted external volumes under /storage/* & StorageManager for AI_Models/<fileName>
        findFileInMountedAiModelsDirs(context, fileName)?.let { mountedFile ->
            addFileCandidate("SSD-Mount", mountedFile)
        }

        // 3. Fallback zero-copy descriptor /proc/self/fd/<fd>
        if (modelUri.scheme == "content") {
            val pfd = runCatching {
                context.contentResolver.openFileDescriptor(modelUri, "r")
            }.getOrNull()
            if (pfd != null) {
                activePfd = pfd
                val fdPath = "/proc/self/fd/${pfd.fd}"
                if (seenPaths.add(fdPath)) {
                    candidates.add("FD-mmap" to fdPath)
                }
            }
        }

        return candidates
    }

    private fun findFileInMountedAiModelsDirs(context: Context, fileName: String): File? {
        val roots = LinkedHashSet<File>()
        runCatching {
            val sm = context.getSystemService(StorageManager::class.java)
            sm?.storageVolumes?.forEach { vol ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching { vol.directory }.getOrNull()?.let { roots.add(it) }
                }
                vol.uuid?.takeIf { it.isNotBlank() }?.let { uuid ->
                    roots.add(File("/storage/$uuid"))
                    roots.add(File("/mnt/media_rw/$uuid"))
                }
            }
        }
        runCatching {
            File("/storage").listFiles()?.forEach { dir ->
                if (dir.isDirectory && dir.name !in setOf("emulated", "self")) {
                    roots.add(dir)
                }
            }
        }
        for (root in roots) {
            if (!root.exists() || !root.isDirectory) continue
            val directAiModels = root.listFiles()?.firstOrNull {
                it.isDirectory && it.name.trim().equals("AI_Models", ignoreCase = true)
            } ?: File(root, "AI_Models")
            if (directAiModels.exists() && directAiModels.isDirectory) {
                val exact = File(directAiModels, fileName)
                if (exact.exists() && exact.isFile && exact.canRead()) return exact
                val ciMatch = directAiModels.listFiles()?.firstOrNull {
                    it.isFile && it.canRead() && it.name.equals(fileName, ignoreCase = true)
                }
                if (ciMatch != null) return ciMatch
            }
        }
        return null
    }

    private fun resolveDirectFileFromDocumentUri(uri: Uri): File? {
        return runCatching {
            val docId = DocumentsContract.getDocumentId(uri)
            val splitIdx = docId.indexOf(':')
            if (splitIdx <= 0) return@runCatching null
            val volume = docId.substring(0, splitIdx)
            val relPath = docId.substring(splitIdx + 1).trimStart('/')
            val baseDirs = if (volume.equals("primary", ignoreCase = true)) {
                listOf(Environment.getExternalStorageDirectory())
            } else {
                listOf(File("/storage/$volume"), File("/mnt/media_rw/$volume"))
            }
            for (baseDir in baseDirs) {
                val candidate = if (relPath.isEmpty()) baseDir else File(baseDir, relPath)
                if (candidate.exists() && candidate.isFile && candidate.canRead()) return@runCatching candidate
            }
            null
        }.getOrNull()
    }

    private fun closePfdQuietly() {
        runCatching { activePfd?.close() }
        activePfd = null
    }

    private fun buildCompactHistoryBlock(history: List<Pair<String, String>>): String {
        if (history.isEmpty()) return ""
        val recent = history.takeLast(6)
        val sb = StringBuilder()
        for ((role, content) in recent) {
            val clean = content.trim()
            if (clean.isEmpty()) continue
            sb.append(role).append(": ").append(clean.take(360)).append('\n')
        }
        return sb.toString().trim()
    }
}
