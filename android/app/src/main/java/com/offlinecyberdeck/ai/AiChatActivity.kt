package com.offlinecyberdeck.ai

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.offlinecyberdeck.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Offline AI terminal activity for Cyber Deck.
 *
 * Storage & Security Design:
 * - Strictly uses Android Storage Access Framework (SAF):
 *   1. ACTION_OPEN_DOCUMENT_TREE to pick the "AI_Models" directory on USB OTG SSD.
 *   2. ACTION_OPEN_DOCUMENT (MIME all types) to pick a ".gguf" model file directly.
 * - Persists URI permissions via takePersistableUriPermission + SharedPreferences so the user
 *   does not need to re-pick the model or folder after restarting the app.
 * - Never requests MANAGE_EXTERNAL_STORAGE and never copies .gguf files to internal storage.
 *   Models are opened directly as a ParcelFileDescriptor (/proc/self/fd/<fd>) in [LlamaHelper].
 */
class AiChatActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "ai_models_prefs"
        private const val KEY_AI_MODELS_TREE_URI = "ai_models_tree_uri"
        private const val KEY_MODEL_DOC_URI = "ai_model_doc_uri"
        private const val KEY_MODEL_FILE_NAME = "ai_model_file_name"

        private const val REQUIRED_FOLDER_NAME = "AI_Models"
        private const val PREFERRED_MODEL_NAME = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
        private const val MSG_MISSING_FOLDER =
            "Folder AI_Models tidak ditemukan. Pilih folder AI_Models di SSD Anda"
    }

    private enum class SenderRole {
        USER, ASSISTANT, SYSTEM
    }

    private data class ChatMessage(
        val role: SenderRole,
        var text: String
    )

    private sealed class FolderScanResult {
        data class Found(
            val modelUri: Uri,
            val fileName: String,
            val folderName: String
        ) : FolderScanResult()

        data class WrongFolder(val pickedName: String) : FolderScanResult()

        data class NoGgufInFolder(
            val folderName: String,
            val seenEntries: List<String>
        ) : FolderScanResult()
    }

    private lateinit var btnBack: Button
    private lateinit var tvActiveModel: TextView
    private lateinit var btnPickFile: Button
    private lateinit var btnSelectFolder: Button
    private lateinit var btnClearChat: Button
    private lateinit var tvModelStatus: TextView
    private lateinit var tvLoadMode: TextView
    private lateinit var layoutMissingFolder: LinearLayout
    private lateinit var tvMissingFolderMessage: TextView
    private lateinit var btnPickFilePrompt: Button
    private lateinit var btnPickFolderPrompt: Button
    private lateinit var rvChatHistory: RecyclerView
    private lateinit var etPrompt: EditText
    private lateinit var btnStop: Button
    private lateinit var btnSend: Button

    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: CyberDeckChatAdapter
    private var initialPickerPrompted = false
    private var isLoadingModel = false

    /**
     * SAF folder picker (ACTION_OPEN_DOCUMENT_TREE) for selecting "AI_Models" on USB OTG SSD.
     */
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            onFolderPicked(treeUri)
        } else if (!LlamaHelper.isModelLoaded() && !isLoadingModel) {
            showBannerState(MSG_MISSING_FOLDER)
            updateModelStatus("MODEL: BELUM DIMUAT")
        }
    }

    /**
     * SAF single-file picker (ACTION_OPEN_DOCUMENT) for selecting a .gguf file directly.
     */
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { docUri: Uri? ->
        if (docUri != null) {
            onModelFilePicked(docUri)
        } else if (!LlamaHelper.isModelLoaded() && !isLoadingModel) {
            showBannerState(MSG_MISSING_FOLDER)
            updateModelStatus("MODEL: BELUM DIMUAT")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyImmersiveCyberDeckMode()
        setContentView(R.layout.activity_ai_chat)

        bindViews()
        setupRecyclerView()
        setupListeners()

        appendSystemLog(
            "Terminal AI Offline siap (SAF FD • n_ctx=${LlamaHelper.TARGET_N_CTX}, threads=${LlamaHelper.TARGET_N_THREADS})."
        )
        restorePersistedModelOrPrompt()
    }

    override fun onResume() {
        super.onResume()
        // If user plugged the SSD back in while activity was paused, re-attempt loading from persisted URI
        if (!LlamaHelper.isModelLoaded() && !isLoadingModel) {
            restorePersistedUriQuietly()
        }
    }

    private fun applyImmersiveCyberDeckMode() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBack)
        tvActiveModel = findViewById(R.id.tvActiveModel)
        btnPickFile = findViewById(R.id.btnPickFile)
        btnSelectFolder = findViewById(R.id.btnSelectFolder)
        btnClearChat = findViewById(R.id.btnClearChat)
        tvModelStatus = findViewById(R.id.tvModelStatus)
        tvLoadMode = findViewById(R.id.tvLoadMode)
        layoutMissingFolder = findViewById(R.id.layoutMissingFolder)
        tvMissingFolderMessage = findViewById(R.id.tvMissingFolderMessage)
        btnPickFilePrompt = findViewById(R.id.btnPickFilePrompt)
        btnPickFolderPrompt = findViewById(R.id.btnPickFolderPrompt)
        rvChatHistory = findViewById(R.id.rvChatHistory)
        etPrompt = findViewById(R.id.etPrompt)
        btnStop = findViewById(R.id.btnStop)
        btnSend = findViewById(R.id.btnSend)
    }

    private fun setupRecyclerView() {
        chatAdapter = CyberDeckChatAdapter(chatMessages)
        rvChatHistory.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvChatHistory.adapter = chatAdapter
        rvChatHistory.itemAnimator = null
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnPickFile.setOnClickListener {
            openGgufFilePicker()
        }

        btnPickFilePrompt.setOnClickListener {
            openGgufFilePicker()
        }

        btnSelectFolder.setOnClickListener {
            openAiModelsFolderPicker()
        }

        btnPickFolderPrompt.setOnClickListener {
            openAiModelsFolderPicker()
        }

        btnClearChat.setOnClickListener {
            if (LlamaHelper.isGenerating()) {
                LlamaHelper.stopGeneration()
            }
            chatMessages.clear()
            chatAdapter.notifyDataSetChanged()
            appendSystemLog("Riwayat chat dibersihkan.")
        }

        btnSend.setOnClickListener {
            submitPrompt()
        }

        btnStop.setOnClickListener {
            LlamaHelper.stopGeneration()
        }

        etPrompt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitPrompt()
                true
            } else {
                false
            }
        }

        etPrompt.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN && !event.isShiftPressed) {
                submitPrompt()
                true
            } else {
                false
            }
        }
    }

    private fun openAiModelsFolderPicker() {
        folderPickerLauncher.launch(null)
    }

    private fun openGgufFilePicker() {
        filePickerLauncher.launch(arrayOf("*/*"))
    }

    private fun getSavedTreeUri(): Uri? {
        val raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_AI_MODELS_TREE_URI, null)
            ?.trim()
            .orEmpty()
        return if (raw.isEmpty()) null else runCatching { Uri.parse(raw) }.getOrNull()
    }

    private fun getSavedModelDocUri(): Pair<Uri, String>? {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val raw = prefs.getString(KEY_MODEL_DOC_URI, null)?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        val savedName = prefs.getString(KEY_MODEL_FILE_NAME, null)?.trim().orEmpty()
        return uri to savedName.ifBlank { PREFERRED_MODEL_NAME }
    }

    private fun saveTreeUri(uri: Uri) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_AI_MODELS_TREE_URI, uri.toString())
            .remove(KEY_MODEL_DOC_URI)
            .remove(KEY_MODEL_FILE_NAME)
            .apply()
    }

    private fun saveModelDocUri(uri: Uri, fileName: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_MODEL_DOC_URI, uri.toString())
            .putString(KEY_MODEL_FILE_NAME, fileName)
            .apply()
    }

    private fun hasPersistedReadPermission(uri: Uri): Boolean {
        return contentResolver.persistedUriPermissions.any { perm ->
            perm.uri == uri && perm.isReadPermission
        }
    }

    private fun onModelFilePicked(docUri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                docUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val resolvedName = resolveDocumentDisplayName(docUri)
        if (!resolvedName.lowercase(Locale.US).endsWith(".gguf")) {
            val msg = "File '$resolvedName' bukan model .gguf. Pilih file berformat .gguf di SSD Anda."
            showBannerState(msg)
            updateModelStatus("MODEL: BELUM DIMUAT")
            appendSystemLog(msg)
            return
        }

        saveModelDocUri(docUri, resolvedName)
        hideBannerState()
        appendSystemLog("File GGUF dipilih via SAF: $resolvedName")
        loadGgufModel(docUri, resolvedName)
    }

    private fun onFolderPicked(treeUri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        lifecycleScope.launch {
            val scan = withContext(Dispatchers.IO) {
                findModelInsideAiModelsTree(treeUri)
            }
            when (scan) {
                is FolderScanResult.Found -> {
                    saveTreeUri(treeUri)
                    hideBannerState()
                    appendSystemLog("File GGUF ditemukan di ${scan.folderName}: ${scan.fileName}")
                    loadGgufModel(scan.modelUri, scan.fileName)
                }
                is FolderScanResult.WrongFolder -> {
                    showBannerState(MSG_MISSING_FOLDER)
                    updateModelStatus("MODEL: BELUM DIMUAT")
                    tvActiveModel.text = "Model Aktif: Belum Ada"
                    appendSystemLog(
                        "Folder terpilih '${scan.pickedName}' bukan '$REQUIRED_FOLDER_NAME' (dan tidak memiliki subfolder '$REQUIRED_FOLDER_NAME')."
                    )
                }
                is FolderScanResult.NoGgufInFolder -> {
                    saveTreeUri(treeUri)
                    val sample = if (scan.seenEntries.isEmpty()) "(folder kosong / tidak terbaca)" else scan.seenEntries.take(6).joinToString(", ")
                    val msg = "Tidak ditemukan file .gguf di folder '${scan.folderName}'. Isi terbaca: $sample"
                    showBannerState(msg)
                    updateModelStatus("MODEL: BELUM DIMUAT")
                    tvActiveModel.text = "Model Aktif: Belum Ada"
                    appendSystemLog(msg)
                }
            }
        }
    }

    /**
     * Restores the previously persisted SAF document or AI_Models folder URI on launch.
     * Prompts the SAF folder picker automatically only on the very first run when no URI is saved.
     */
    private fun restorePersistedModelOrPrompt() {
        if (LlamaHelper.isModelLoaded()) {
            val activeName = LlamaHelper.getActiveModelName() ?: PREFERRED_MODEL_NAME
            tvActiveModel.text = "Model Aktif: $activeName"
            tvLoadMode.text = "100% OFFLINE • ${LlamaHelper.getActiveLoadMode()}"
            updateModelStatus("MODEL: SIAP")
            hideBannerState()
            return
        }

        val savedDoc = getSavedModelDocUri()
        if (savedDoc != null && hasPersistedReadPermission(savedDoc.first)) {
            hideBannerState()
            loadGgufModel(savedDoc.first, savedDoc.second)
            return
        }

        val savedTree = getSavedTreeUri()
        if (savedTree != null && hasPersistedReadPermission(savedTree)) {
            lifecycleScope.launch {
                when (val scan = withContext(Dispatchers.IO) { findModelInsideAiModelsTree(savedTree) }) {
                    is FolderScanResult.Found -> {
                        hideBannerState()
                        loadGgufModel(scan.modelUri, scan.fileName)
                    }
                    is FolderScanResult.WrongFolder -> {
                        showBannerState(MSG_MISSING_FOLDER)
                        updateModelStatus("MODEL: BELUM DIMUAT")
                        tvActiveModel.text = "Model Aktif: Belum Ada"
                    }
                    is FolderScanResult.NoGgufInFolder -> {
                        val sample = if (scan.seenEntries.isEmpty()) "(kosong)" else scan.seenEntries.take(5).joinToString(", ")
                        showBannerState("Tidak ditemukan file .gguf di folder '${scan.folderName}'. File terbaca: $sample")
                        updateModelStatus("MODEL: BELUM DIMUAT")
                        tvActiveModel.text = "Model Aktif: Belum Ada"
                    }
                }
            }
            return
        }

        // First-time launch with no saved SAF permission
        showBannerState(MSG_MISSING_FOLDER)
        updateModelStatus("MODEL: BELUM DIMUAT")
        tvActiveModel.text = "Model Aktif: Belum Ada"
        if (!initialPickerPrompted) {
            initialPickerPrompted = true
            openAiModelsFolderPicker()
        }
    }

    private fun restorePersistedUriQuietly() {
        val savedDoc = getSavedModelDocUri()
        if (savedDoc != null && hasPersistedReadPermission(savedDoc.first)) {
            hideBannerState()
            loadGgufModel(savedDoc.first, savedDoc.second)
            return
        }

        val savedTree = getSavedTreeUri()
        if (savedTree != null && hasPersistedReadPermission(savedTree)) {
            lifecycleScope.launch {
                val scan = withContext(Dispatchers.IO) { findModelInsideAiModelsTree(savedTree) }
                if (scan is FolderScanResult.Found) {
                    hideBannerState()
                    loadGgufModel(scan.modelUri, scan.fileName)
                }
            }
        }
    }

    private fun resolveDocumentDisplayName(uri: Uri): String {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)?.trim().orEmpty()
                        if (name.isNotEmpty()) return name
                    }
                }
            }
        }
        val docName = runCatching { DocumentFile.fromSingleUri(this, uri)?.name?.trim() }.getOrNull().orEmpty()
        if (docName.isNotEmpty()) return docName
        return uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':').orEmpty()
            .ifBlank { PREFERRED_MODEL_NAME }
    }

    /**
     * Scans strictly inside the "AI_Models" directory via SAF (DocumentsContract + DocumentFile).
     * If the user selects an SSD root containing an "AI_Models" subfolder, descends 1 level into it.
     */
    private fun findModelInsideAiModelsTree(treeUri: Uri): FolderScanResult {
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull().orEmpty()
        val folderDoc = runCatching { DocumentFile.fromTreeUri(this, treeUri) }.getOrNull()

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
                val childInfo = queryDirectChildren(treeUri, treeDocId).firstOrNull { child ->
                    child.isDirectory && child.name.trim().equals(REQUIRED_FOLDER_NAME, ignoreCase = true)
                }
                if (childInfo != null) {
                    targetDirDocId = childInfo.docId
                    targetFolderDoc = DocumentFile.fromTreeUri(this, treeUri)?.findFile(childInfo.name)
                    targetDisplayName = REQUIRED_FOLDER_NAME
                }
            }
        }

        if (targetDirDocId == null && targetFolderDoc == null) {
            return FolderScanResult.WrongFolder(effectiveName.ifBlank { "(tidak diketahui)" })
        }

        data class GgufCandidate(val uri: Uri, val name: String)
        val foundGgufs = ArrayList<GgufCandidate>()
        val seenEntries = ArrayList<String>()

        // Layer 1: Direct DocumentsContract child cursor
        if (!targetDirDocId.isNullOrBlank()) {
            val entries = queryDirectChildren(treeUri, targetDirDocId)
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

        // Layer 2: DocumentFile traversal fallback
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
            return FolderScanResult.NoGgufInFolder(targetDisplayName, seenEntries)
        }

        val preferred = foundGgufs.firstOrNull {
            it.name.equals(PREFERRED_MODEL_NAME, ignoreCase = true)
        } ?: foundGgufs.first()

        return FolderScanResult.Found(
            modelUri = preferred.uri,
            fileName = preferred.name,
            folderName = targetDisplayName
        )
    }

    private data class SafChildEntry(
        val docId: String,
        val name: String,
        val isDirectory: Boolean
    )

    private fun queryDirectChildren(treeUri: Uri, parentDocId: String): List<SafChildEntry> {
        val out = ArrayList<SafChildEntry>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        runCatching {
            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
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
                        out += SafChildEntry(id, effectiveName, isDir)
                    }
                }
            }
        }
        return out
    }

    private fun loadGgufModel(modelUri: Uri, fileName: String) {
        if (isLoadingModel) return
        isLoadingModel = true
        tvActiveModel.text = "Model Aktif: Memuat $fileName..."
        updateModelStatus("MODEL: MEMUAT...")
        setInputEnabled(false)

        lifecycleScope.launch {
            val result = try {
                LlamaHelper.loadModel(
                    context = applicationContext,
                    modelUri = modelUri,
                    fileName = fileName
                )
            } finally {
                isLoadingModel = false
            }

            result.onSuccess { loadedName ->
                val mode = LlamaHelper.getActiveLoadMode()
                tvActiveModel.text = "Model Aktif: $loadedName"
                tvLoadMode.text = "100% OFFLINE • $mode"
                updateModelStatus("MODEL: SIAP")
                hideBannerState()
                setInputEnabled(true)
                appendSystemLog("Model dimuat langsung dari SSD ($mode, tanpa copy ke internal): $loadedName")
            }.onFailure { err ->
                tvActiveModel.text = "Model Aktif: Gagal memuat $fileName"
                tvLoadMode.text = "100% OFFLINE • SAF FD"
                updateModelStatus("MODEL: BELUM DIMUAT")
                val errDetail = err.message ?: "Error tidak diketahui"
                showBannerState("Model '$fileName' ditemukan, namun gagal dimuat: $errDetail")
                setInputEnabled(true)
                appendSystemLog("Gagal memuat model '$fileName': $errDetail")
            }
        }
    }

    private fun submitPrompt() {
        val prompt = etPrompt.text.toString().trim()
        if (prompt.isEmpty()) return

        if (!LlamaHelper.isModelLoaded()) {
            showBannerState(MSG_MISSING_FOLDER)
            appendSystemLog("Model belum dimuat. Pilih folder AI_Models atau file .gguf di SSD Anda terlebih dahulu.")
            return
        }

        if (LlamaHelper.isGenerating()) return

        val historySnapshot = chatMessages
            .filter { it.role == SenderRole.USER || it.role == SenderRole.ASSISTANT }
            .map { msg ->
                val tag = if (msg.role == SenderRole.USER) "User" else "Assistant"
                tag to msg.text
            }

        etPrompt.setText("")
        chatMessages.add(ChatMessage(SenderRole.USER, prompt))
        val userIdx = chatMessages.lastIndex
        chatAdapter.notifyItemInserted(userIdx)

        val assistantMsg = ChatMessage(SenderRole.ASSISTANT, "")
        chatMessages.add(assistantMsg)
        val assistantIdx = chatMessages.lastIndex
        chatAdapter.notifyItemInserted(assistantIdx)
        scrollToBottom()

        updateModelStatus("MODEL: SEDANG BERPIKIR...")
        btnSend.visibility = View.GONE
        btnStop.visibility = View.VISIBLE
        etPrompt.isEnabled = false

        LlamaHelper.generateResponse(
            userPrompt = prompt,
            conversationHistory = historySnapshot,
            onToken = { token ->
                runOnUiThread {
                    if (assistantIdx in chatMessages.indices) {
                        chatMessages[assistantIdx].text += token
                        chatAdapter.notifyItemChanged(assistantIdx)
                        scrollToBottom()
                    }
                }
            },
            onComplete = {
                runOnUiThread {
                    if (assistantIdx in chatMessages.indices && chatMessages[assistantIdx].text.isBlank()) {
                        chatMessages[assistantIdx].text = "[Selesai tanpa output]"
                        chatAdapter.notifyItemChanged(assistantIdx)
                    }
                    updateModelStatus("MODEL: SIAP")
                    btnStop.visibility = View.GONE
                    btnSend.visibility = View.VISIBLE
                    etPrompt.isEnabled = true
                    etPrompt.requestFocus()
                }
            },
            onError = { errMsg ->
                runOnUiThread {
                    if (assistantIdx in chatMessages.indices) {
                        val prefix = if (chatMessages[assistantIdx].text.isBlank()) "" else "\n"
                        chatMessages[assistantIdx].text += "${prefix}[ERROR: $errMsg]"
                        chatAdapter.notifyItemChanged(assistantIdx)
                    }
                    updateModelStatus(if (LlamaHelper.isModelLoaded()) "MODEL: SIAP" else "MODEL: BELUM DIMUAT")
                    btnStop.visibility = View.GONE
                    btnSend.visibility = View.VISIBLE
                    etPrompt.isEnabled = true
                }
            }
        )
    }

    private fun appendSystemLog(text: String) {
        chatMessages.add(ChatMessage(SenderRole.SYSTEM, text))
        chatAdapter.notifyItemInserted(chatMessages.lastIndex)
        scrollToBottom()
    }

    private fun scrollToBottom() {
        if (chatMessages.isNotEmpty()) {
            rvChatHistory.scrollToPosition(chatMessages.lastIndex)
        }
    }

    private fun showBannerState(message: String) {
        tvMissingFolderMessage.text = message
        layoutMissingFolder.visibility = View.VISIBLE
    }

    private fun hideBannerState() {
        layoutMissingFolder.visibility = View.GONE
    }

    private fun updateModelStatus(statusText: String) {
        tvModelStatus.text = statusText
        tvModelStatus.setTextColor(
            when {
                statusText.contains("SIAP") -> Color.parseColor("#00FF41")
                statusText.contains("BERPIKIR") || statusText.contains("MEMUAT") -> Color.parseColor("#FFD166")
                else -> Color.parseColor("#FF6B6B")
            }
        )
    }

    private fun setInputEnabled(enabled: Boolean) {
        etPrompt.isEnabled = enabled
        btnSend.isEnabled = enabled
    }

    override fun onDestroy() {
        if (isFinishing) {
            LlamaHelper.unloadModel()
        } else {
            LlamaHelper.stopGeneration()
        }
        super.onDestroy()
    }

    private class CyberDeckChatAdapter(
        private val items: List<ChatMessage>
    ) : RecyclerView.Adapter<CyberDeckChatAdapter.ChatViewHolder>() {

        class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val container: LinearLayout = view.findViewById(R.id.messageContainer)
            val tvSender: TextView = view.findViewById(R.id.tvSender)
            val tvMessage: TextView = view.findViewById(R.id.tvMessage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ai_chat_message, parent, false)
            return ChatViewHolder(v)
        }

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            val item = items[position]
            when (item.role) {
                SenderRole.USER -> {
                    holder.container.setBackgroundColor(Color.parseColor("#0A1A0D"))
                    holder.tvSender.text = "[USER]"
                    holder.tvSender.setTextColor(Color.parseColor("#7CFF9B"))
                    holder.tvMessage.setTextColor(Color.parseColor("#E6FFEC"))
                }
                SenderRole.ASSISTANT -> {
                    holder.container.setBackgroundColor(Color.parseColor("#050D06"))
                    holder.tvSender.text = "[AI_DECK]"
                    holder.tvSender.setTextColor(Color.parseColor("#00FF41"))
                    holder.tvMessage.setTextColor(Color.parseColor("#00FF41"))
                }
                SenderRole.SYSTEM -> {
                    holder.container.setBackgroundColor(Color.parseColor("#101010"))
                    holder.tvSender.text = "[SYS]"
                    holder.tvSender.setTextColor(Color.parseColor("#FFB84D"))
                    holder.tvMessage.setTextColor(Color.parseColor("#B5B5B5"))
                }
            }
            holder.tvMessage.text = item.text.ifEmpty { "..." }
        }

        override fun getItemCount(): Int = items.size
    }
}
