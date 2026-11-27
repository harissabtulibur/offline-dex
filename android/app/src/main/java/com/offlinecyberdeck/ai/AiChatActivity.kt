package com.offlinecyberdeck.ai

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
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
import java.io.File
import java.util.Locale

class AiChatActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "ai_models_prefs"
        private const val KEY_AI_MODELS_TREE_URI = "ai_models_tree_uri"
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
    private lateinit var btnSelectFolder: Button
    private lateinit var btnClearChat: Button
    private lateinit var tvModelStatus: TextView
    private lateinit var layoutMissingFolder: LinearLayout
    private lateinit var tvMissingFolderMessage: TextView
    private lateinit var btnAllFilesAccess: Button
    private lateinit var btnPickFolderPrompt: Button
    private lateinit var rvChatHistory: RecyclerView
    private lateinit var etPrompt: EditText
    private lateinit var btnStop: Button
    private lateinit var btnSend: Button

    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: CyberDeckChatAdapter
    private var initialPickerPrompted = false

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            onFolderPicked(treeUri)
        } else {
            if (!LlamaHelper.isModelLoaded()) {
                showBannerState(MSG_MISSING_FOLDER, offerAllFilesButton = false)
                updateModelStatus("MODEL: BELUM DIMUAT")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyImmersiveCyberDeckMode()
        setContentView(R.layout.activity_ai_chat)

        bindViews()
        setupRecyclerView()
        setupListeners()

        appendSystemLog("Terminal AI Offline diinisialisasi (n_ctx=${LlamaHelper.TARGET_N_CTX}, threads=${LlamaHelper.TARGET_N_THREADS}, mmap=true).")
        checkSavedFolderAndLoadModel()
    }

    override fun onResume() {
        super.onResume()
        if (!LlamaHelper.isModelLoaded()) {
            val savedUri = getSavedTreeUri()
            if (savedUri != null && hasPersistedReadPermission(savedUri)) {
                lifecycleScope.launch {
                    val scan = withContext(Dispatchers.IO) {
                        findModelInsideAiModelsTree(savedUri)
                    }
                    if (scan is FolderScanResult.Found) {
                        hideBannerState()
                        loadGgufModel(scan.modelUri, scan.fileName)
                    }
                }
            }
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
        btnSelectFolder = findViewById(R.id.btnSelectFolder)
        btnClearChat = findViewById(R.id.btnClearChat)
        tvModelStatus = findViewById(R.id.tvModelStatus)
        layoutMissingFolder = findViewById(R.id.layoutMissingFolder)
        tvMissingFolderMessage = findViewById(R.id.tvMissingFolderMessage)
        btnAllFilesAccess = findViewById(R.id.btnAllFilesAccess)
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

        btnSelectFolder.setOnClickListener {
            openAiModelsFolderPicker()
        }

        btnPickFolderPrompt.setOnClickListener {
            openAiModelsFolderPicker()
        }

        btnAllFilesAccess.setOnClickListener {
            openAllFilesAccessSettings()
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

    private fun openAllFilesAccessSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            )
            runCatching { startActivity(intent) }.onFailure {
                runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }
        }
    }

    private fun getSavedTreeUri(): Uri? {
        val raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_AI_MODELS_TREE_URI, null)
            ?.trim()
            .orEmpty()
        if (raw.isEmpty()) return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    private fun saveTreeUri(uri: Uri) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_AI_MODELS_TREE_URI, uri.toString())
            .apply()
    }

    private fun hasPersistedReadPermission(uri: Uri): Boolean {
        return contentResolver.persistedUriPermissions.any { perm ->
            perm.uri == uri && perm.isReadPermission
        }
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
                    showBannerState(MSG_MISSING_FOLDER, offerAllFilesButton = false)
                    updateModelStatus("MODEL: BELUM DIMUAT")
                    tvActiveModel.text = "Model Aktif: Belum Ada"
                    appendSystemLog("Folder terpilih '${scan.pickedName}' bukan '$REQUIRED_FOLDER_NAME' (dan tidak memiliki subfolder '$REQUIRED_FOLDER_NAME').")
                }
                is FolderScanResult.NoGgufInFolder -> {
                    saveTreeUri(treeUri)
                    val sample = if (scan.seenEntries.isEmpty()) "(folder kosong / tidak terbaca)" else scan.seenEntries.take(6).joinToString(", ")
                    val msg = "Tidak ditemukan file .gguf di folder '${scan.folderName}'. Isi terbaca: $sample"
                    showBannerState(msg, offerAllFilesButton = false)
                    updateModelStatus("MODEL: BELUM DIMUAT")
                    tvActiveModel.text = "Model Aktif: Belum Ada"
                    appendSystemLog(msg)
                }
            }
        }
    }

    private fun checkSavedFolderAndLoadModel() {
        if (LlamaHelper.isModelLoaded()) {
            val activeName = LlamaHelper.getActiveModelName() ?: PREFERRED_MODEL_NAME
            tvActiveModel.text = "Model Aktif: $activeName"
            updateModelStatus("MODEL: SIAP")
            hideBannerState()
            return
        }

        val savedUri = getSavedTreeUri()
        if (savedUri == null || !hasPersistedReadPermission(savedUri)) {
            showBannerState(MSG_MISSING_FOLDER, offerAllFilesButton = false)
            updateModelStatus("MODEL: BELUM DIMUAT")
            tvActiveModel.text = "Model Aktif: Belum Ada"
            if (!initialPickerPrompted) {
                initialPickerPrompted = true
                openAiModelsFolderPicker()
            }
            return
        }

        lifecycleScope.launch {
            when (val scan = withContext(Dispatchers.IO) { findModelInsideAiModelsTree(savedUri) }) {
                is FolderScanResult.Found -> {
                    hideBannerState()
                    loadGgufModel(scan.modelUri, scan.fileName)
                }
                is FolderScanResult.WrongFolder -> {
                    showBannerState(MSG_MISSING_FOLDER, offerAllFilesButton = false)
                    updateModelStatus("MODEL: BELUM DIMUAT")
                    tvActiveModel.text = "Model Aktif: Belum Ada"
                }
                is FolderScanResult.NoGgufInFolder -> {
                    val sample = if (scan.seenEntries.isEmpty()) "(kosong)" else scan.seenEntries.take(5).joinToString(", ")
                    showBannerState(
                        "Tidak ditemukan file .gguf di folder '${scan.folderName}'. File terbaca: $sample",
                        offerAllFilesButton = false
                    )
                    updateModelStatus("MODEL: BELUM DIMUAT")
                    tvActiveModel.text = "Model Aktif: Belum Ada"
                }
            }
        }
    }

    /**
     * Wajib hanya mencari di dalam folder "AI_Models" di SSD (tidak mencari ke root SSD atau Download).
     * Jika user menunjuk ke parent/root SSD yang di dalamnya terdapat folder "AI_Models", otomatis masuk ke folder AI_Models tersebut.
     * Menggunakan 3 lapis pembacaan (DocumentsContract cursor + DocumentFile + direct POSIX jika diizinkan) agar exFAT OTG Android 14 selalu terbaca.
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
            // Check 1 level down for an explicit "AI_Models" subfolder (never scans root files!)
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
                    targetFolderDoc = DocumentFile.fromTreeUri(this, treeUri)
                        ?.findFile(childInfo.name)
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

        // Layer 1: Direct DocumentsContract child cursor (very reliable on Android 14 exFAT OTG SAF)
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

        // Layer 3: Direct POSIX /storage/<vol>/AI_Models scan if MANAGE_EXTERNAL_STORAGE is active
        if (foundGgufs.isEmpty()) {
            findDirectPosixAiModelsGguf(treeDocId)?.let { (file, name) ->
                foundGgufs += GgufCandidate(Uri.fromFile(file), name)
                if (!seenEntries.contains(name)) seenEntries += name
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

    private fun findDirectPosixAiModelsGguf(treeDocId: String): Pair<File, String>? {
        val splitIdx = treeDocId.indexOf(':')
        val volId = if (splitIdx > 0) treeDocId.substring(0, splitIdx) else ""
        val candidateDirs = buildList {
            if (volId.isNotBlank()) {
                if (volId.equals("primary", ignoreCase = true)) {
                    add(File(Environment.getExternalStorageDirectory(), REQUIRED_FOLDER_NAME))
                } else {
                    add(File("/storage/$volId/$REQUIRED_FOLDER_NAME"))
                    add(File("/mnt/media_rw/$volId/$REQUIRED_FOLDER_NAME"))
                }
            }
            File("/storage").listFiles()?.forEach { volDir ->
                if (volDir.isDirectory && volDir.name !in setOf("emulated", "self")) {
                    add(File(volDir, REQUIRED_FOLDER_NAME))
                }
            }
        }
        for (dir in candidateDirs.distinct()) {
            if (dir.exists() && dir.isDirectory && dir.canRead()) {
                val files = dir.listFiles().orEmpty().filter {
                    it.isFile && it.canRead() && it.name.lowercase(Locale.US).endsWith(".gguf")
                }
                if (files.isNotEmpty()) {
                    val pref = files.firstOrNull { it.name.equals(PREFERRED_MODEL_NAME, true) } ?: files.first()
                    return pref to pref.name
                }
            }
        }
        return null
    }

    private fun loadGgufModel(modelUri: Uri, fileName: String) {
        tvActiveModel.text = "Model Aktif: Memuat $fileName..."
        updateModelStatus("MODEL: MEMUAT...")
        setInputEnabled(false)

        lifecycleScope.launch {
            val result = LlamaHelper.loadModel(
                context = applicationContext,
                modelUri = modelUri,
                fileName = fileName
            )

            result.onSuccess { loadedName ->
                tvActiveModel.text = "Model Aktif: $loadedName"
                updateModelStatus("MODEL: SIAP")
                hideBannerState()
                setInputEnabled(true)
                appendSystemLog("Model dimuat langsung dari SSD (useMmap=true): $loadedName")
            }.onFailure { err ->
                tvActiveModel.text = "Model Aktif: Gagal memuat $fileName"
                updateModelStatus("MODEL: BELUM DIMUAT")
                val needAllFiles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()
                val errDetail = err.message ?: "Error tidak diketahui"
                showBannerState(
                    message = "Model '$fileName' ditemukan, namun gagal dimuat: $errDetail",
                    offerAllFilesButton = needAllFiles
                )
                setInputEnabled(true)
                appendSystemLog("Gagal memuat model '$fileName': $errDetail")
            }
        }
    }

    private fun submitPrompt() {
        val prompt = etPrompt.text.toString().trim()
        if (prompt.isEmpty()) return

        if (!LlamaHelper.isModelLoaded()) {
            showBannerState(MSG_MISSING_FOLDER, offerAllFilesButton = false)
            appendSystemLog("Model belum dimuat. Pilih folder AI_Models di SSD Anda terlebih dahulu.")
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

    private fun showBannerState(message: String, offerAllFilesButton: Boolean) {
        tvMissingFolderMessage.text = message
        btnAllFilesAccess.visibility = if (offerAllFilesButton) View.VISIBLE else View.GONE
        layoutMissingFolder.visibility = View.VISIBLE
    }

    private fun hideBannerState() {
        btnAllFilesAccess.visibility = View.GONE
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
