package com.offlinecyberdeck.ai

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
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

                // Prioritaskan jalur langsung /storage/<UUID>/AI_Models/... (0 copy, pure mmap ke exFAT SSD)
                // Jika tidak terbaca langsung oleh POSIX, gunakan FileDescriptor /proc/self/fd/<fd> tanpa menyalin ke internal.
                val resolvedPath = resolveZeroCopyMmapPath(context, modelUri)
                    ?: error("Gagal mengakses file model di SSD secara langsung tanpa menyalin ke internal.")

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

                val ok = LlamaBridge.initGenerateModel(resolvedPath)
                if (!ok) {
                    closePfdQuietly()
                    error("llama.cpp gagal memuat model GGUF '$fileName' dengan mmap.")
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

    private fun resolveZeroCopyMmapPath(context: Context, modelUri: Uri): String? {
        if (modelUri.scheme == "file") {
            val path = modelUri.path.orEmpty()
            val f = File(path)
            if (f.exists() && f.isFile && f.canRead()) {
                return f.absolutePath
            }
        }

        val directFile = resolveDirectFileFromDocumentUri(modelUri)
        if (directFile != null && directFile.exists() && directFile.isFile && directFile.canRead()) {
            return directFile.absolutePath
        }

        val pfd = runCatching {
            context.contentResolver.openFileDescriptor(modelUri, "r")
        }.getOrNull() ?: return null

        activePfd = pfd
        val fdPath = "/proc/self/fd/${pfd.fd}"
        return fdPath
    }

    private fun resolveDirectFileFromDocumentUri(uri: Uri): File? {
        return runCatching {
            val docId = DocumentsContract.getDocumentId(uri)
            val splitIdx = docId.indexOf(':')
            if (splitIdx <= 0) return@runCatching null
            val volume = docId.substring(0, splitIdx)
            val relPath = docId.substring(splitIdx + 1).trimStart('/')
            val baseDir = if (volume.equals("primary", ignoreCase = true)) {
                Environment.getExternalStorageDirectory()
            } else {
                File("/storage/$volume")
            }
            val candidate = if (relPath.isEmpty()) baseDir else File(baseDir, relPath)
            if (candidate.exists() && candidate.isFile && candidate.canRead()) candidate else null
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
