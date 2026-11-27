package com.offlinecyberdeck.ai

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
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

    private lateinit var btnBack: Button
    private lateinit var tvActiveModel: TextView
    private lateinit var btnSelectFolder: Button
    private lateinit var btnClearChat: Button
    private lateinit var tvModelStatus: TextView
    private lateinit var layoutMissingFolder: LinearLayout
    private lateinit var tvMissingFolderMessage: TextView
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
                showMissingFolderState(MSG_MISSING_FOLDER)
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
            val found = withContext(Dispatchers.IO) {
                findModelInsideAiModelsTree(treeUri)
            }
            if (found != null) {
                saveTreeUri(treeUri)
                hideMissingFolderState()
                loadGgufModel(found.first, found.second)
            } else {
                showMissingFolderState(MSG_MISSING_FOLDER)
                updateModelStatus("MODEL: BELUM DIMUAT")
                tvActiveModel.text = "Model Aktif: Belum Ada"
                appendSystemLog("Folder yang dipilih bukan '$REQUIRED_FOLDER_NAME' atau tidak berisi file .gguf.")
            }
        }
    }

    private fun checkSavedFolderAndLoadModel() {
        if (LlamaHelper.isModelLoaded()) {
            val activeName = LlamaHelper.getActiveModelName() ?: PREFERRED_MODEL_NAME
            tvActiveModel.text = "Model Aktif: $activeName"
            updateModelStatus("MODEL: SIAP")
            hideMissingFolderState()
            return
        }

        val savedUri = getSavedTreeUri()
        if (savedUri == null || !hasPersistedReadPermission(savedUri)) {
            showMissingFolderState(MSG_MISSING_FOLDER)
            updateModelStatus("MODEL: BELUM DIMUAT")
            tvActiveModel.text = "Model Aktif: Belum Ada"
            if (!initialPickerPrompted) {
                initialPickerPrompted = true
                openAiModelsFolderPicker()
            }
            return
        }

        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) {
                findModelInsideAiModelsTree(savedUri)
            }
            if (found != null) {
                hideMissingFolderState()
                loadGgufModel(found.first, found.second)
            } else {
                showMissingFolderState(MSG_MISSING_FOLDER)
                updateModelStatus("MODEL: BELUM DIMUAT")
                tvActiveModel.text = "Model Aktif: Belum Ada"
            }
        }
    }

    /**
     * Wajib hanya mencari di dalam folder bernama tepat "AI_Models" pada SSD eksternal yang dipilih user.
     * Tidak mencari ke root SSD ataupun folder Download.
     */
    private fun findModelInsideAiModelsTree(treeUri: Uri): Pair<Uri, String>? {
        val folderDoc = runCatching { DocumentFile.fromTreeUri(this, treeUri) }.getOrNull() ?: return null
        if (!folderDoc.exists() || !folderDoc.isDirectory) return null

        val folderName = folderDoc.name.orEmpty().trim()
        if (!folderName.equals(REQUIRED_FOLDER_NAME, ignoreCase = true)) {
            return null
        }

        val files = runCatching { folderDoc.listFiles() }.getOrNull() ?: return null
        val ggufFiles = files.filter { file ->
            val name = file.name.orEmpty()
            !file.isDirectory && name.lowercase(Locale.US).endsWith(".gguf")
        }
        if (ggufFiles.isEmpty()) return null

        val preferred = ggufFiles.firstOrNull {
            it.name.equals(PREFERRED_MODEL_NAME, ignoreCase = true)
        } ?: ggufFiles.first()

        return preferred.uri to (preferred.name ?: PREFERRED_MODEL_NAME)
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
                hideMissingFolderState()
                setInputEnabled(true)
                appendSystemLog("Model dimuat langsung dari SSD (useMmap=true): $loadedName")
            }.onFailure { err ->
                tvActiveModel.text = "Model Aktif: Belum Ada"
                updateModelStatus("MODEL: BELUM DIMUAT")
                showMissingFolderState(MSG_MISSING_FOLDER)
                setInputEnabled(true)
                appendSystemLog("Gagal memuat model: ${err.message ?: "Error tidak diketahui"}")
            }
        }
    }

    private fun submitPrompt() {
        val prompt = etPrompt.text.toString().trim()
        if (prompt.isEmpty()) return

        if (!LlamaHelper.isModelLoaded()) {
            showMissingFolderState(MSG_MISSING_FOLDER)
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

    private fun showMissingFolderState(message: String) {
        tvMissingFolderMessage.text = message
        layoutMissingFolder.visibility = View.VISIBLE
    }

    private fun hideMissingFolderState() {
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
