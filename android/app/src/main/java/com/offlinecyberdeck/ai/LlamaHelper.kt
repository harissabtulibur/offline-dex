package com.offlinecyberdeck.ai

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
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
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Singleton manager for offline llama.cpp inference via SAF + ParcelFileDescriptor.
 *
 * Design guarantees (GitHub-friendly & Scoped Storage compliant):
 * - Zero MANAGE_EXTERNAL_STORAGE / All Files Access usage.
 * - Zero copying of .gguf files into internal storage or getExternalFilesDir().
 * - Opens content:// URI via ContentResolver.openFileDescriptor(uri, "r") and passes
 *   "/proc/self/fd/<fd>" to llama.cpp while keeping [activePfd] alive until unloadModel().
 */
object LlamaHelper {

    const val SYSTEM_PROMPT =
        "Kamu adalah asisten AI offline di dalam Cyber Deck. Jawab singkat, jelas, dalam Bahasa Indonesia, gaya cyberpunk."

    const val TARGET_N_CTX = 2048
    const val TARGET_N_THREADS = 4

    private data class LoadProfile(
        val label: String,
        val useMmap: Boolean,
        val contextLength: Int,
        val batchSize: Int
    )

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
    private var currentLoadModeLabel: String = "FD"

    /**
     * Global ParcelFileDescriptor held open for the entire lifetime of the loaded model.
     * Closing this while native llama.cpp still holds mmap/file pointers would invalidate /proc/self/fd/<fd>.
     */
    @Volatile
    private var activePfd: ParcelFileDescriptor? = null

    fun isModelLoaded(): Boolean = loaded

    fun isGenerating(): Boolean = generatingFlag.get()

    fun getActiveModelName(): String? = currentModelName

    fun getActiveModelUri(): String? = currentModelUri

    fun getActiveLoadMode(): String = currentLoadModeLabel

    /**
     * Loads a GGUF model directly from an external SAF content:// URI using /proc/self/fd/<fd>.
     * Never uses raw /storage/xxxx-xxxx/ paths and never copies the model to internal storage.
     */
    suspend fun loadModel(
        context: Context,
        modelUri: Uri,
        fileName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        modelMutex.withLock {
            runCatching {
                val uriStr = modelUri.toString()
                if (loaded && currentModelUri == uriStr && activePfd != null) {
                    return@runCatching fileName
                }

                unloadInternal()

                val resolver = context.applicationContext.contentResolver
                val probePfd = runCatching { resolver.openFileDescriptor(modelUri, "r") }.getOrNull()
                    ?: error("Gagal membuka File Descriptor untuk '$fileName'. Pastikan izin SAF masih aktif dan SSD terhubung.")

                // Validate FD readability and GGUF magic header ("GGUF") before handing to native llama.cpp
                val validation = validateGgufFileDescriptor(probePfd)
                runCatching { probePfd.close() }

                if (!validation.isValidGguf) {
                    error(validation.diagnosticMessage)
                }

                // Attempt mmap first for speed/low RAM, then fallback to useMmap = false for FUSE/OTG FDs
                // where Android's kernel/FUSE proxy rejects mmap().
                val profiles = listOf(
                    LoadProfile("FD-MMAP", useMmap = true, contextLength = TARGET_N_CTX, batchSize = 256),
                    LoadProfile("FD-RAM", useMmap = false, contextLength = TARGET_N_CTX, batchSize = 128),
                    LoadProfile("FD-RAM-1K", useMmap = false, contextLength = 1024, batchSize = 64)
                )

                var initOk = false
                var succeededMode = ""

                for (profile in profiles) {
                    closePfdQuietly()
                    val pfd = runCatching { resolver.openFileDescriptor(modelUri, "r") }.getOrNull()
                        ?: continue
                    activePfd = pfd
                    runCatching { Os.lseek(pfd.fileDescriptor, 0L, OsConstants.SEEK_SET) }

                    val fdPath = "/proc/self/fd/${pfd.fd}"

                    LlamaBridge.updateGenerateParams(
                        temperature = 0.7f,
                        maxTokens = 512,
                        topP = 0.9f,
                        topK = 40,
                        repeatPenalty = 1.1f,
                        contextLength = profile.contextLength,
                        numThreads = TARGET_N_THREADS,
                        useMmap = profile.useMmap,
                        flashAttention = false,
                        batchSize = profile.batchSize,
                        gpuLayers = 0
                    )

                    if (LlamaBridge.initGenerateModel(fdPath)) {
                        initOk = true
                        succeededMode = profile.label
                        break
                    }

                    runCatching { LlamaBridge.shutdown() }
                    closePfdQuietly()
                }

                if (!initOk) {
                    closePfdQuietly()
                    error(buildFailureDiagnostic(fileName, validation))
                }

                loaded = true
                currentModelName = fileName
                currentModelUri = uriStr
                currentLoadModeLabel = succeededMode
                fileName
            }.onFailure {
                unloadInternal()
            }
        }
    }

    /**
     * Streams tokens from the loaded model on a background IO coroutine.
     */
    fun generateResponse(
        userPrompt: String,
        conversationHistory: List<Pair<String, String>>,
        onToken: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!loaded || activePfd == null) {
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

    /**
     * Unloads native llama.cpp context and releases the held ParcelFileDescriptor.
     */
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
        currentLoadModeLabel = "FD"
        closePfdQuietly()
    }

    private fun closePfdQuietly() {
        runCatching { activePfd?.close() }
        activePfd = null
    }

    private data class FdValidation(
        val isValidGguf: Boolean,
        val fileSizeBytes: Long,
        val procSelfFdReadable: Boolean,
        val diagnosticMessage: String
    )

    private fun validateGgufFileDescriptor(pfd: ParcelFileDescriptor): FdValidation {
        val fd = pfd.fileDescriptor
        val sizeBytes = runCatching { Os.fstat(fd).st_size }.getOrDefault(pfd.statSize)
        val header = ByteArray(4)
        val readBytes = runCatching { Os.pread(fd, header, 0, 4, 0L) }.getOrDefault(-1)
        val magic = if (readBytes == 4) String(header, Charsets.US_ASCII) else ""

        if (readBytes < 4 || magic != "GGUF") {
            return FdValidation(
                isValidGguf = false,
                fileSizeBytes = sizeBytes,
                procSelfFdReadable = false,
                diagnosticMessage = "File bukan format GGUF valid (header='$magic', size=$sizeBytes)."
            )
        }

        // Check whether reopening /proc/self/fd/<fd> via POSIX open() succeeds under Android SELinux
        val procSelfFdPath = "/proc/self/fd/${pfd.fd}"
        val procReadable = runCatching {
            FileInputStream(File(procSelfFdPath)).use { fis ->
                fis.read() >= 0
            }
        }.getOrDefault(false)

        return FdValidation(
            isValidGguf = true,
            fileSizeBytes = sizeBytes,
            procSelfFdReadable = procReadable,
            diagnosticMessage = "OK"
        )
    }

    private fun buildFailureDiagnostic(fileName: String, validation: FdValidation): String {
        val sizeMb = if (validation.fileSizeBytes > 0L) {
            "${validation.fileSizeBytes / (1024 * 1024)} MB"
        } else {
            "unknown"
        }
        return if (!validation.procSelfFdReadable) {
            "FD SAF terbaca ($sizeMb GGUF), tetapi SELinux/FUSE Android menolak pembukaan ulang symlink /proc/self/fd oleh library native. Coba pilih ulang file .gguf lewat tombol 'PILIH FILE .GGUF'."
        } else {
            "llama.cpp gagal memuat '$fileName' ($sizeMb) via /proc/self/fd (MMAP & RAM fallback). Pastikan file GGUF tidak korup dan RAM tersedia cukup."
        }
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
