package com.offlinecyberdeck.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private const val AI_PREFS_NAME = "ai_models_prefs"
private const val KEY_AI_MODELS_TREE_URI = "ai_models_tree_uri"
private const val KEY_MODEL_DOC_URI = "ai_model_doc_uri"
private const val KEY_MODEL_FILE_NAME = "ai_model_file_name"
private const val REQUIRED_FOLDER_NAME = "AI_Models"
private const val PREFERRED_MODEL_NAME = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
private const val MSG_MISSING_FOLDER =
    "Folder AI_Models tidak ditemukan. Pilih folder AI_Models atau file .gguf di SSD Anda."

enum class AiSenderRole {
    USER, ASSISTANT, SYSTEM
}

private data class AiRoleStyle(
    val bg: Color,
    val tagColor: Color,
    val textColor: Color,
    val tag: String
)

data class AiChatMsg(
    val id: Long,
    val role: AiSenderRole,
    val text: String
)

private sealed class AiFolderScanResult {
    data class Found(
        val modelUri: Uri,
        val fileName: String,
        val folderName: String
    ) : AiFolderScanResult()

    data class WrongFolder(val pickedName: String) : AiFolderScanResult()

    data class NoGgufInFolder(
        val folderName: String,
        val seenEntries: List<String>
    ) : AiFolderScanResult()
}

object AiChatSessionStore {
    val messages = mutableStateListOf<AiChatMsg>()
    private var nextId = 1L
    var isInitialized = false
        private set

    fun ensureWelcome() {
        if (!isInitialized && messages.isEmpty()) {
            isInitialized = true
            appendSystem(
                "Terminal AI Offline siap (SAF FD • n_ctx=${LlamaHelper.TARGET_N_CTX}, threads=${LlamaHelper.TARGET_N_THREADS})."
            )
        }
    }

    fun appendUser(text: String): Long {
        val id = nextId++
        messages.add(AiChatMsg(id, AiSenderRole.USER, text))
        return id
    }

    fun appendAssistant(text: String): Long {
        val id = nextId++
        messages.add(AiChatMsg(id, AiSenderRole.ASSISTANT, text))
        return id
    }

    fun appendSystem(text: String): Long {
        val id = nextId++
        messages.add(AiChatMsg(id, AiSenderRole.SYSTEM, text))
        return id
    }

    fun appendTokenToMessage(id: Long, token: String) {
        val idx = messages.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val cur = messages[idx]
            messages[idx] = cur.copy(text = cur.text + token)
        }
    }

    fun finalizeEmptyAssistant(id: Long, fallback: String) {
        val idx = messages.indexOfFirst { it.id == id }
        if (idx >= 0 && messages[idx].text.isBlank()) {
            messages[idx] = messages[idx].copy(text = fallback)
        }
    }

    fun clearAll() {
        messages.clear()
        isInitialized = false
    }
}

private val AiMatrixGreen = Color(0xFF00FF41)
private val AiBg = Color(0xFF020604)
private val AiHeaderBg = Color(0xFF07130A)
private val AiSurface = Color(0xFF061009)
private val AiAmber = Color(0xFFFFD166)
private val AiRed = Color(0xFFFF6B6B)

@Composable
fun AiOfflineWindowScreen(
    onTextInputFocusChanged: (Boolean) -> Unit = {},
    onShowMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val loadProgress by LlamaHelper.loadProgressFlow.collectAsStateWithLifecycle()

    var activeModelLabel by remember {
        mutableStateOf(
            LlamaHelper.getActiveModelName()?.let { "Model Aktif: $it" } ?: "Model Aktif: Belum Ada"
        )
    }
    var loadModeLabel by remember {
        mutableStateOf("100% OFFLINE • ${LlamaHelper.getActiveLoadMode()}")
    }
    var statusLabel by remember {
        mutableStateOf(
            when {
                LlamaHelper.isGenerating() -> "MODEL: SEDANG BERPIKIR..."
                LlamaHelper.isModelLoaded() -> "MODEL: SIAP"
                else -> "MODEL: BELUM DIMUAT"
            }
        )
    }
    var isScanningFolder by remember { mutableStateOf(false) }
    var isLocalLoading by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(LlamaHelper.isGenerating()) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var promptText by remember { mutableStateOf("") }
    var initialCheckDone by remember { mutableStateOf(false) }
    var streamTargetMsgId by remember { mutableLongStateOf(-1L) }

    val messages = AiChatSessionStore.messages

    fun getSavedTreeUri(): Uri? {
        val raw = context.getSharedPreferences(AI_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AI_MODELS_TREE_URI, null)
            ?.trim()
            .orEmpty()
        return if (raw.isEmpty()) null else runCatching { Uri.parse(raw) }.getOrNull()
    }

    fun getSavedModelDocUri(): Pair<Uri, String>? {
        val prefs = context.getSharedPreferences(AI_PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_MODEL_DOC_URI, null)?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        val savedName = prefs.getString(KEY_MODEL_FILE_NAME, null)?.trim().orEmpty()
        return uri to savedName.ifBlank { PREFERRED_MODEL_NAME }
    }

    fun saveTreeUri(uri: Uri) {
        context.getSharedPreferences(AI_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AI_MODELS_TREE_URI, uri.toString())
            .remove(KEY_MODEL_DOC_URI)
            .remove(KEY_MODEL_FILE_NAME)
            .apply()
    }

    fun saveModelDocUri(uri: Uri, fileName: String) {
        context.getSharedPreferences(AI_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODEL_DOC_URI, uri.toString())
            .putString(KEY_MODEL_FILE_NAME, fileName)
            .apply()
    }

    fun hasPersistedReadPermission(uri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any { perm ->
            perm.uri == uri && perm.isReadPermission
        }
    }

    fun startLoadModel(modelUri: Uri, fileName: String) {
        if (isLocalLoading || loadProgress.isLoading) return
        isLocalLoading = true
        activeModelLabel = "Memuat: $fileName..."
        statusLabel = "MODEL: MEMUAT..."
        bannerMessage = null

        scope.launch {
            val result = try {
                LlamaHelper.loadModel(
                    context = context.applicationContext,
                    modelUri = modelUri,
                    fileName = fileName
                )
            } finally {
                isLocalLoading = false
            }

            result.onSuccess { loadedName ->
                val mode = LlamaHelper.getActiveLoadMode()
                activeModelLabel = "Model Aktif: $loadedName"
                loadModeLabel = "100% OFFLINE • $mode"
                statusLabel = "MODEL: SIAP"
                bannerMessage = null
                AiChatSessionStore.appendSystem(
                    "Model dimuat langsung dari SSD ($mode, tanpa copy ke internal): $loadedName"
                )
            }.onFailure { err ->
                activeModelLabel = "Gagal memuat: $fileName"
                loadModeLabel = "100% OFFLINE • SAF FD"
                statusLabel = "MODEL: BELUM DIMUAT"
                val detail = err.message ?: "Error tidak diketahui"
                bannerMessage = "Model '$fileName' ditemukan, namun gagal dimuat: $detail"
                AiChatSessionStore.appendSystem("Gagal memuat model '$fileName': $detail")
                onShowMessage("Gagal memuat AI: $detail")
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            scope.launch {
                isScanningFolder = true
                val scan = withContext(Dispatchers.IO) {
                    findModelInsideAiModelsTree(context, treeUri)
                }
                isScanningFolder = false
                when (scan) {
                    is AiFolderScanResult.Found -> {
                        saveTreeUri(treeUri)
                        bannerMessage = null
                        AiChatSessionStore.appendSystem(
                            "File GGUF ditemukan di ${scan.folderName}: ${scan.fileName}"
                        )
                        startLoadModel(scan.modelUri, scan.fileName)
                    }
                    is AiFolderScanResult.WrongFolder -> {
                        bannerMessage = MSG_MISSING_FOLDER
                        statusLabel = "MODEL: BELUM DIMUAT"
                        activeModelLabel = "Model Aktif: Belum Ada"
                        AiChatSessionStore.appendSystem(
                            "Folder terpilih '${scan.pickedName}' bukan '$REQUIRED_FOLDER_NAME'."
                        )
                    }
                    is AiFolderScanResult.NoGgufInFolder -> {
                        saveTreeUri(treeUri)
                        val sample = if (scan.seenEntries.isEmpty()) "(kosong)" else scan.seenEntries.take(6).joinToString(", ")
                        val msg = "Tidak ditemukan file .gguf di '${scan.folderName}'. Isi: $sample"
                        bannerMessage = msg
                        statusLabel = "MODEL: BELUM DIMUAT"
                        activeModelLabel = "Model Aktif: Belum Ada"
                        AiChatSessionStore.appendSystem(msg)
                    }
                }
            }
        } else if (!LlamaHelper.isModelLoaded() && !isLocalLoading) {
            bannerMessage = MSG_MISSING_FOLDER
            statusLabel = "MODEL: BELUM DIMUAT"
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { docUri: Uri? ->
        if (docUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    docUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val resolvedName = resolveDocumentDisplayName(context, docUri)
            if (!resolvedName.lowercase(Locale.US).endsWith(".gguf")) {
                val msg = "File '$resolvedName' bukan model .gguf. Pilih file berformat .gguf."
                bannerMessage = msg
                statusLabel = "MODEL: BELUM DIMUAT"
                AiChatSessionStore.appendSystem(msg)
                return@rememberLauncherForActivityResult
            }
            saveModelDocUri(docUri, resolvedName)
            bannerMessage = null
            AiChatSessionStore.appendSystem("File GGUF dipilih via SAF: $resolvedName")
            startLoadModel(docUri, resolvedName)
        } else if (!LlamaHelper.isModelLoaded() && !isLocalLoading) {
            bannerMessage = MSG_MISSING_FOLDER
            statusLabel = "MODEL: BELUM DIMUAT"
        }
    }

    fun submitPrompt() {
        val prompt = promptText.trim()
        if (prompt.isEmpty()) return
        if (!LlamaHelper.isModelLoaded()) {
            bannerMessage = MSG_MISSING_FOLDER
            AiChatSessionStore.appendSystem("Model belum dimuat. Pilih folder AI_Models atau file .gguf terlebih dahulu.")
            return
        }
        if (LlamaHelper.isGenerating() || isGenerating) return

        val historySnapshot = messages
            .filter { it.role == AiSenderRole.USER || it.role == AiSenderRole.ASSISTANT }
            .map { m -> (if (m.role == AiSenderRole.USER) "User" else "Assistant") to m.text }

        promptText = ""
        AiChatSessionStore.appendUser(prompt)
        val assistantMsgId = AiChatSessionStore.appendAssistant("")
        streamTargetMsgId = assistantMsgId
        isGenerating = true
        statusLabel = "MODEL: SEDANG BERPIKIR..."

        LlamaHelper.generateResponse(
            userPrompt = prompt,
            conversationHistory = historySnapshot,
            onToken = { token ->
                AiChatSessionStore.appendTokenToMessage(assistantMsgId, token)
            },
            onComplete = {
                AiChatSessionStore.finalizeEmptyAssistant(assistantMsgId, "[Selesai tanpa output]")
                isGenerating = false
                statusLabel = "MODEL: SIAP"
            },
            onError = { errMsg ->
                AiChatSessionStore.appendTokenToMessage(assistantMsgId, "\n[ERROR: $errMsg]")
                isGenerating = false
                statusLabel = if (LlamaHelper.isModelLoaded()) "MODEL: SIAP" else "MODEL: BELUM DIMUAT"
            }
        )
    }

    LaunchedEffect(Unit) {
        AiChatSessionStore.ensureWelcome()
        if (!initialCheckDone) {
            initialCheckDone = true
            if (LlamaHelper.isModelLoaded()) {
                val activeName = LlamaHelper.getActiveModelName() ?: PREFERRED_MODEL_NAME
                activeModelLabel = "Model Aktif: $activeName"
                loadModeLabel = "100% OFFLINE • ${LlamaHelper.getActiveLoadMode()}"
                statusLabel = if (LlamaHelper.isGenerating()) "MODEL: SEDANG BERPIKIR..." else "MODEL: SIAP"
                bannerMessage = null
            } else {
                val savedDoc = getSavedModelDocUri()
                if (savedDoc != null && hasPersistedReadPermission(savedDoc.first)) {
                    startLoadModel(savedDoc.first, savedDoc.second)
                } else {
                    val savedTree = getSavedTreeUri()
                    if (savedTree != null && hasPersistedReadPermission(savedTree)) {
                        isScanningFolder = true
                        val scan = withContext(Dispatchers.IO) {
                            findModelInsideAiModelsTree(context, savedTree)
                        }
                        isScanningFolder = false
                        when (scan) {
                            is AiFolderScanResult.Found -> startLoadModel(scan.modelUri, scan.fileName)
                            is AiFolderScanResult.WrongFolder -> {
                                bannerMessage = MSG_MISSING_FOLDER
                                statusLabel = "MODEL: BELUM DIMUAT"
                            }
                            is AiFolderScanResult.NoGgufInFolder -> {
                                val sample = if (scan.seenEntries.isEmpty()) "(kosong)" else scan.seenEntries.take(5).joinToString(", ")
                                bannerMessage = "Tidak ditemukan file .gguf di folder '${scan.folderName}'. File: $sample"
                                statusLabel = "MODEL: BELUM DIMUAT"
                            }
                        }
                    } else {
                        bannerMessage = MSG_MISSING_FOLDER
                        statusLabel = "MODEL: BELUM DIMUAT"
                    }
                }
            }
        }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    DisposableEffect(Unit) {
        onDispose { onTextInputFocusChanged(false) }
    }

    val isImportingOrLoading = isScanningFolder || isLocalLoading || loadProgress.isLoading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AiBg)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // TOP PROGRESS BAR WHEN AI MODEL IS IMPORTING / SCANNING
        if (isImportingOrLoading) {
            Surface(
                color = Color(0xFF0A1D12),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AiMatrixGreen.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val title = when {
                            isScanningFolder -> "Memindai folder AI_Models (SAF)..."
                            loadProgress.fileName.isNotBlank() -> "Mengimpor ${loadProgress.fileName}"
                            else -> "Memuat model AI Offline..."
                        }
                        Text(
                            text = title,
                            color = AiMatrixGreen,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        val pctText = if (isScanningFolder) {
                            "SCAN"
                        } else {
                            "${(loadProgress.progress.coerceIn(0f, 1f) * 100f).roundToInt()}%"
                        }
                        Text(
                            text = pctText,
                            color = AiAmber,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    if (isScanningFolder) {
                        LinearProgressIndicator(
                            color = AiMatrixGreen,
                            trackColor = Color(0xFF0F2D1B),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { loadProgress.progress.coerceIn(0.05f, 1f) },
                            color = AiMatrixGreen,
                            trackColor = Color(0xFF0F2D1B),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )
                    }
                    if (loadProgress.stage.isNotBlank() && !isScanningFolder) {
                        Text(
                            text = loadProgress.stage,
                            color = Color(0xFF8CE9A2),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // AI HEADER & MODEL CONTROLS
        Surface(
            color = AiHeaderBg,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AiMatrixGreen.copy(alpha = 0.28f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.SmartToy,
                        contentDescription = null,
                        tint = AiMatrixGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI OFFLINE // CYBER DECK",
                            color = AiMatrixGreen,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = activeModelLabel,
                            color = Color(0xFF78E891),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        enabled = !isImportingOrLoading,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.InsertDriveFile, contentDescription = null, tint = AiMatrixGreen, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pilih .GGUF", color = AiMatrixGreen, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { folderPicker.launch(null) },
                        enabled = !isImportingOrLoading,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = AiMatrixGreen, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Folder SSD", color = AiMatrixGreen, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            if (LlamaHelper.isGenerating()) {
                                LlamaHelper.stopGeneration()
                            }
                            isGenerating = false
                            AiChatSessionStore.clearAll()
                            AiChatSessionStore.appendSystem("Riwayat chat dibersihkan.")
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = AiRed, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear", color = AiRed, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusLabel,
                        color = when {
                            statusLabel.contains("SIAP") -> AiMatrixGreen
                            statusLabel.contains("BERPIKIR") || statusLabel.contains("MEMUAT") -> AiAmber
                            else -> AiRed
                        },
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = loadModeLabel,
                        color = Color(0xFF479E59),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // MISSING FOLDER / GGUF PROMPT BANNER
        if (!bannerMessage.isNullOrBlank()) {
            Surface(
                color = Color(0xFF1A0E05),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AiAmber.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = bannerMessage.orEmpty(),
                        color = AiAmber,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Pilih .GGUF", color = AiMatrixGreen, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                    Button(
                        onClick = { folderPicker.launch(null) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AiMatrixGreen,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Pilih Folder", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // CHAT HISTORY LIST
        Surface(
            color = AiSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AiMatrixGreen.copy(alpha = 0.2f)),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(messages, key = { it.id }) { msg ->
                    val style = when (msg.role) {
                        AiSenderRole.USER -> AiRoleStyle(
                            bg = Color(0xFF0A1A0D),
                            tagColor = Color(0xFF7CFF9B),
                            textColor = Color(0xFFE6FFEC),
                            tag = "[USER]"
                        )
                        AiSenderRole.ASSISTANT -> AiRoleStyle(
                            bg = Color(0xFF050D06),
                            tagColor = AiMatrixGreen,
                            textColor = AiMatrixGreen,
                            tag = "[AI_DECK]"
                        )
                        AiSenderRole.SYSTEM -> AiRoleStyle(
                            bg = Color(0xFF101214),
                            tagColor = AiAmber,
                            textColor = Color(0xFFB5B8BC),
                            tag = "[SYS]"
                        )
                    }
                    Surface(
                        color = style.bg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = style.tag,
                                color = style.tagColor,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = msg.text.ifEmpty { "..." },
                                color = style.textColor,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }

        // PROMPT INPUT BAR
        Surface(
            color = AiHeaderBg,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    enabled = !isImportingOrLoading && !isGenerating,
                    placeholder = {
                        Text(
                            "Ketik prompt ke AI Cyber Deck...",
                            color = Color(0xFF3A7A49),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = AiMatrixGreen,
                        fontFamily = FontFamily.Monospace
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AiMatrixGreen,
                        unfocusedBorderColor = AiMatrixGreen.copy(alpha = 0.35f),
                        focusedContainerColor = Color(0xFF020502),
                        unfocusedContainerColor = Color(0xFF020502),
                        cursorColor = AiMatrixGreen
                    ),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitPrompt() }),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { onTextInputFocusChanged(it.isFocused) }
                        .onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown &&
                                (ev.key == Key.Enter || ev.key == Key.NumPadEnter) &&
                                !ev.isShiftPressed
                            ) {
                                submitPrompt()
                                true
                            } else {
                                false
                            }
                        }
                )

                if (isGenerating) {
                    Button(
                        onClick = { LlamaHelper.stopGeneration() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3B1111),
                            contentColor = AiRed
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("STOP", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = { submitPrompt() },
                        enabled = !isImportingOrLoading && promptText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AiMatrixGreen,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "Send", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("SEND", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun resolveDocumentDisplayName(context: Context, uri: Uri): String {
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = cursor.getString(idx)?.trim().orEmpty()
                    if (name.isNotEmpty()) return name
                }
            }
        }
    }
    val docName = runCatching { DocumentFile.fromSingleUri(context, uri)?.name?.trim() }.getOrNull().orEmpty()
    if (docName.isNotEmpty()) return docName
    return uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':').orEmpty()
        .ifBlank { PREFERRED_MODEL_NAME }
}

private fun findModelInsideAiModelsTree(context: Context, treeUri: Uri): AiFolderScanResult {
    val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull().orEmpty()
    val folderDoc = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()

    val treeTail = treeDocId.substringAfterLast(':').substringAfterLast('/').trim()
    val docName = folderDoc?.name?.trim().orEmpty()
    val effectiveName = docName.ifBlank { treeTail }.ifBlank { treeUri.lastPathSegment.orEmpty() }

    var targetDirDocId: String? = null
    var targetFolderDoc: DocumentFile? = null
    var targetDisplayName = effectiveName

    val pickedIsAiModels = docName.equals(REQUIRED_FOLDER_NAME, ignoreCase = true) ||
        treeTail.equals(REQUIRED_FOLDER_NAME, ignoreCase = true)

    if (pickedIsAiModels) {
        targetDirDocId = treeDocId.ifBlank { null }
        targetFolderDoc = folderDoc
        targetDisplayName = REQUIRED_FOLDER_NAME
    } else {
        val subChildDoc = folderDoc?.listFiles()?.firstOrNull {
            it.isDirectory && it.name.orEmpty().trim().equals(REQUIRED_FOLDER_NAME, ignoreCase = true)
        }
        if (subChildDoc != null) {
            targetFolderDoc = subChildDoc
            targetDirDocId = runCatching { DocumentsContract.getDocumentId(subChildDoc.uri) }.getOrNull()
            targetDisplayName = REQUIRED_FOLDER_NAME
        } else if (treeDocId.isNotBlank()) {
            val childInfo = queryAiDirectChildren(context, treeUri, treeDocId).firstOrNull { child ->
                child.isDirectory && child.name.trim().equals(REQUIRED_FOLDER_NAME, ignoreCase = true)
            }
            if (childInfo != null) {
                targetDirDocId = childInfo.docId
                targetFolderDoc = DocumentFile.fromTreeUri(context, treeUri)?.findFile(childInfo.name)
                targetDisplayName = REQUIRED_FOLDER_NAME
            }
        }
    }

    if (targetDirDocId == null && targetFolderDoc == null) {
        return AiFolderScanResult.WrongFolder(effectiveName.ifBlank { "(tidak diketahui)" })
    }

    data class GgufCandidate(val uri: Uri, val name: String)
    val foundGgufs = ArrayList<GgufCandidate>()
    val seenEntries = ArrayList<String>()

    if (!targetDirDocId.isNullOrBlank()) {
        val entries = queryAiDirectChildren(context, treeUri, targetDirDocId)
        for (entry in entries) {
            seenEntries += entry.name
            val nameLower = entry.name.trim().lowercase(Locale.US)
            val idLower = entry.docId.trim().lowercase(Locale.US)
            val resolvedName = when {
                nameLower.endsWith(".gguf") -> entry.name.trim()
                idLower.endsWith(".gguf") -> entry.docId.substringAfterLast('/').substringAfterLast(':').trim()
                else -> null
            }
            if (!entry.isDirectory && resolvedName != null) {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.docId)
                foundGgufs += GgufCandidate(docUri, resolvedName)
            }
        }
    }

    if (foundGgufs.isEmpty() && targetFolderDoc != null) {
        val files = runCatching { targetFolderDoc.listFiles() }.getOrNull().orEmpty()
        for (file in files) {
            val n = file.name.orEmpty().trim()
            if (n.isNotEmpty() && !seenEntries.contains(n)) seenEntries += n
            val uriStrLower = file.uri.toString().lowercase(Locale.US)
            if (!file.isDirectory && (n.lowercase(Locale.US).endsWith(".gguf") || uriStrLower.endsWith(".gguf"))) {
                val cleanName = if (n.lowercase(Locale.US).endsWith(".gguf")) n else PREFERRED_MODEL_NAME
                foundGgufs += GgufCandidate(file.uri, cleanName)
            }
        }
    }

    if (foundGgufs.isEmpty()) {
        return AiFolderScanResult.NoGgufInFolder(targetDisplayName, seenEntries)
    }

    val preferred = foundGgufs.firstOrNull {
        it.name.equals(PREFERRED_MODEL_NAME, ignoreCase = true)
    } ?: foundGgufs.first()

    return AiFolderScanResult.Found(
        modelUri = preferred.uri,
        fileName = preferred.name,
        folderName = targetDisplayName
    )
}

private data class AiSafChildEntry(
    val docId: String,
    val name: String,
    val isDirectory: Boolean
)

private fun queryAiDirectChildren(
    context: Context,
    treeUri: Uri,
    parentDocId: String
): List<AiSafChildEntry> {
    val out = ArrayList<AiSafChildEntry>()
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE
    )
    runCatching {
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = if (idIdx >= 0) cursor.getString(idIdx).orEmpty() else ""
                val dispName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx).orEmpty() else ""
                val fallbackName = id.substringAfterLast('/').substringAfterLast(':')
                val effectiveName = dispName.ifBlank { fallbackName }
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                if (id.isNotBlank() && effectiveName.isNotBlank()) {
                    out += AiSafChildEntry(id, effectiveName, isDir)
                }
            }
        }
    }
    return out
}
