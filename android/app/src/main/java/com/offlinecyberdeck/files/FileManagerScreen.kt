package com.offlinecyberdeck.files

import android.net.Uri
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinecyberdeck.CyberDeckApplication
import com.offlinecyberdeck.core.database.entity.AppSettingsEntity
import com.offlinecyberdeck.core.format.Formatters
import com.offlinecyberdeck.core.storage.StorageRootInfo
import com.offlinecyberdeck.desktop.DesktopLauncherManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.math.roundToInt

private val WinBlue = Color(0xFF0078D4)
private val WinFolderGold = Color(0xFFFFC107)

private sealed interface ExplorerLocation {
    data object ThisPc : ExplorerLocation
    data class LocalDir(val path: String, val label: String? = null) : ExplorerLocation
    data class SafDir(val treeUri: String, val docUri: String, val trail: List<Pair<String, String>>) : ExplorerLocation
}

private data class ExplorerEntry(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val mimeType: String?,
    val localPath: String? = null,
    val safUri: String? = null
) {
    val stableKey: String
        get() = localPath ?: safUri ?: name
}

private data class FileClipboardItem(
    val entries: List<ExplorerEntry>,
    val isCut: Boolean
)

private data class PasteProgressState(
    val active: Boolean = false,
    val isCut: Boolean = false,
    val currentFileName: String = "",
    val completedFiles: Int = 0,
    val totalFiles: Int = 0,
    val copiedBytes: Long = 0L,
    val totalBytes: Long = 0L
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(
    app: CyberDeckApplication,
    onShowMessage: (String) -> Unit,
    onOpenZimPathOrUri: ((String) -> Unit)? = null,
    onRegisterGoBackHandler: ((() -> Boolean) -> Unit)? = null,
    onTextInputFocusChanged: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isTabletWide = LocalConfiguration.current.screenWidthDp >= 640
    val settings by app.settingsRepository.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettingsEntity())

    val navHistory = remember { mutableStateListOf<ExplorerLocation>(ExplorerLocation.ThisPc) }
    var navIndex by remember { mutableIntStateOf(0) }
    val currentLocation = navHistory.getOrNull(navIndex) ?: ExplorerLocation.ThisPc

    var roots by remember { mutableStateOf<List<StorageRootInfo>>(emptyList()) }
    var entries by remember { mutableStateOf<List<ExplorerEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var gridView by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }

    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val inSelectionMode = selectedKeys.isNotEmpty()
    var clipboardItem by remember { mutableStateOf<FileClipboardItem?>(null) }
    var pasteProgress by remember { mutableStateOf(PasteProgressState()) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var renamingEntry by remember { mutableStateOf<ExplorerEntry?>(null) }
    var deletingEntries by remember { mutableStateOf<List<ExplorerEntry>>(emptyList()) }
    var propertiesEntry by remember { mutableStateOf<ExplorerEntry?>(null) }
    var textPreviewTitle by remember { mutableStateOf<String?>(null) }
    var textPreviewContent by remember { mutableStateOf<String?>(null) }

    val hasAllFiles = DesktopLauncherManager.hasAllFilesAccess(context)

    fun navigateTo(loc: ExplorerLocation) {
        while (navHistory.size > navIndex + 1) {
            navHistory.removeAt(navHistory.lastIndex)
        }
        if (navHistory.lastOrNull() != loc) {
            navHistory.add(loc)
            navIndex = navHistory.lastIndex
        }
        selectedKeys = emptySet()
        searchQuery = ""
    }

    fun goBack(): Boolean {
        return if (navIndex > 0) {
            navIndex--
            selectedKeys = emptySet()
            searchQuery = ""
            true
        } else false
    }

    DisposableEffect(onRegisterGoBackHandler) {
        onRegisterGoBackHandler?.invoke {
            if (!isSearchFocused && !showNewFolderDialog && renamingEntry == null && textPreviewTitle == null) {
                goBack()
            } else {
                false
            }
        }
        onDispose {
            onTextInputFocusChanged?.invoke(false)
        }
    }

    fun goForward() {
        if (navIndex < navHistory.lastIndex) {
            navIndex++
            selectedKeys = emptySet()
            searchQuery = ""
        }
    }

    fun goUp() {
        when (val loc = currentLocation) {
            ExplorerLocation.ThisPc -> Unit
            is ExplorerLocation.LocalDir -> {
                val matchedRoot = roots.firstOrNull { !it.isSafTree && it.pathOrUri == loc.path }
                val parent = File(loc.path).parentFile
                if (matchedRoot != null || parent == null || !parent.canRead()) {
                    navigateTo(ExplorerLocation.ThisPc)
                } else {
                    navigateTo(ExplorerLocation.LocalDir(parent.absolutePath))
                }
            }
            is ExplorerLocation.SafDir -> {
                if (loc.trail.size <= 1) {
                    navigateTo(ExplorerLocation.ThisPc)
                } else {
                    val parentTrail = loc.trail.dropLast(1)
                    navigateTo(ExplorerLocation.SafDir(loc.treeUri, parentTrail.last().first, parentTrail))
                }
            }
        }
    }

    val safFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val doc = DocumentFile.fromTreeUri(context, uri)
                app.driveManager.attachDeckDrive(uri)
                app.settingsRepository.updateSettings {
                    it.copy(
                        deckDriveTreeUri = uri.toString(),
                        deckDriveLabel = doc?.name ?: "External Drive"
                    )
                }
                val docUri = doc?.uri?.toString() ?: uri.toString()
                val label = doc?.name ?: "External Drive"
                navigateTo(ExplorerLocation.SafDir(uri.toString(), docUri, listOf(docUri to label)))
                refreshTick++
            }
        }
    }

    LaunchedEffect(currentLocation, settings.deckDriveTreeUri, refreshTick) {
        isLoading = true
        withContext(Dispatchers.IO) {
            roots = app.driveManager.getAllStorageRoots(settings.deckDriveTreeUri)
            entries = when (val loc = currentLocation) {
                ExplorerLocation.ThisPc -> emptyList()
                is ExplorerLocation.LocalDir -> {
                    val dir = File(loc.path)
                    val list = runCatching { dir.listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())
                    list.map { f ->
                        ExplorerEntry(
                            name = f.name,
                            isDirectory = f.isDirectory,
                            sizeBytes = if (f.isDirectory) 0L else f.length(),
                            lastModifiedMs = f.lastModified(),
                            mimeType = if (f.isDirectory) null else app.driveManager.guessMimeType(f.name),
                            localPath = f.absolutePath
                        )
                    }.sortedWith(compareByDescending<ExplorerEntry> { it.isDirectory }.thenBy { it.name.lowercase(Locale.US) })
                }
                is ExplorerLocation.SafDir -> {
                    val doc = runCatching {
                        if (loc.docUri == loc.treeUri) {
                            DocumentFile.fromTreeUri(context, Uri.parse(loc.treeUri))
                        } else {
                            DocumentFile.fromTreeUri(context, Uri.parse(loc.docUri))
                                ?: DocumentFile.fromSingleUri(context, Uri.parse(loc.docUri))
                        }
                    }.getOrNull()
                    val children = runCatching { doc?.listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())
                    children.mapNotNull { c ->
                        val name = c.name ?: return@mapNotNull null
                        ExplorerEntry(
                            name = name,
                            isDirectory = c.isDirectory,
                            sizeBytes = if (c.isDirectory) 0L else c.length(),
                            lastModifiedMs = c.lastModified(),
                            mimeType = c.type ?: app.driveManager.guessMimeType(name),
                            safUri = c.uri.toString()
                        )
                    }.sortedWith(compareByDescending<ExplorerEntry> { it.isDirectory }.thenBy { it.name.lowercase(Locale.US) })
                }
            }
        }
        isLoading = false
    }

    BackHandler(enabled = inSelectionMode || navIndex > 0) {
        if (inSelectionMode) {
            selectedKeys = emptySet()
        } else {
            goBack()
        }
    }

    fun toggleSelection(entry: ExplorerEntry) {
        val key = entry.stableKey
        selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
    }

    fun openEntry(entry: ExplorerEntry) {
        if (entry.isDirectory) {
            if (entry.localPath != null) {
                navigateTo(ExplorerLocation.LocalDir(entry.localPath))
            } else if (entry.safUri != null && currentLocation is ExplorerLocation.SafDir) {
                navigateTo(
                    ExplorerLocation.SafDir(
                        treeUri = currentLocation.treeUri,
                        docUri = entry.safUri,
                        trail = currentLocation.trail + (entry.safUri to entry.name)
                    )
                )
            }
        } else {
            val ext = entry.name.substringAfterLast('.', "").lowercase(Locale.US)
            if (ext == "zim" && onOpenZimPathOrUri != null) {
                val ref = entry.localPath ?: entry.safUri ?: return
                onOpenZimPathOrUri(ref)
            } else if (ext in setOf("txt", "md", "json", "log", "csv", "xml", "yml", "yaml") && entry.sizeBytes <= 512_000L) {
                val ref = entry.localPath ?: entry.safUri ?: return
                scope.launch {
                    val content = withContext(Dispatchers.IO) {
                        app.driveManager.openInputStream(ref)?.use { it.bufferedReader().readText() }
                    }
                    if (content != null) {
                        textPreviewTitle = entry.name
                        textPreviewContent = content
                    } else {
                        app.driveManager.openPathOrUriWithSystem(ref, entry.mimeType)
                            .onFailure { onShowMessage("Gagal membuka file: ${it.message}") }
                    }
                }
            } else {
                val ref = entry.localPath ?: entry.safUri ?: return
                app.driveManager.openPathOrUriWithSystem(ref, entry.mimeType)
                    .onFailure { onShowMessage("Tidak ada aplikasi untuk membuka file ini: ${it.message}") }
            }
        }
    }

    fun pasteClipboardHere() {
        val clip = clipboardItem ?: return
        if (pasteProgress.active) return
        if (currentLocation == ExplorerLocation.ThisPc) {
            onShowMessage("Buka folder tujuan terlebih dahulu sebelum menempel.")
            return
        }

        fun countFilesAndBytes(list: List<ExplorerEntry>): Pair<Int, Long> {
            var count = 0
            var bytes = 0L
            fun walkLocal(f: File) {
                if (f.isDirectory) {
                    f.listFiles().orEmpty().forEach { walkLocal(it) }
                } else {
                    count++
                    bytes += f.length().coerceAtLeast(0L)
                }
            }
            fun walkSaf(uriStr: String) {
                val doc = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
                    ?: DocumentFile.fromSingleUri(context, Uri.parse(uriStr))
                    ?: return
                if (doc.isDirectory) {
                    doc.listFiles().forEach { walkSaf(it.uri.toString()) }
                } else {
                    count++
                    bytes += doc.length().coerceAtLeast(0L)
                }
            }
            for (e in list) {
                if (e.localPath != null) walkLocal(File(e.localPath))
                else if (e.safUri != null) walkSaf(e.safUri)
            }
            return count.coerceAtLeast(1) to bytes
        }

        var copiedBytesAcc = 0L
        var completedFilesAcc = 0
        var totalFilesExpected = 1
        var totalBytesExpected = 0L

        fun reportProgress(fileName: String) {
            val pctSnap = copiedBytesAcc
            val filesSnap = completedFilesAcc
            scope.launch {
                pasteProgress = PasteProgressState(
                    active = true,
                    isCut = clip.isCut,
                    currentFileName = fileName,
                    completedFiles = filesSnap,
                    totalFiles = totalFilesExpected,
                    copiedBytes = pctSnap,
                    totalBytes = totalBytesExpected
                )
            }
        }

        fun copyStreamWithProgress(fileName: String, input: InputStream, output: OutputStream) {
            val buf = ByteArray(64 * 1024)
            var sinceLastReport = 0L
            reportProgress(fileName)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                output.write(buf, 0, n)
                copiedBytesAcc += n
                sinceLastReport += n
                if (sinceLastReport >= 256 * 1024L) {
                    sinceLastReport = 0L
                    reportProgress(fileName)
                }
            }
            output.flush()
            completedFilesAcc++
            reportProgress(fileName)
        }

        fun copyLocalRecursiveToLocal(src: File, dstParent: File) {
            if (src.isDirectory) {
                val dstDir = File(dstParent, app.driveManager.sanitizeFileName(src.name)).apply { mkdirs() }
                src.listFiles().orEmpty().forEach { child -> copyLocalRecursiveToLocal(child, dstDir) }
            } else {
                val targetFile = app.driveManager.createUniqueInternalFile(dstParent, src.name)
                src.inputStream().use { input ->
                    targetFile.outputStream().use { output ->
                        copyStreamWithProgress(src.name, input, output)
                    }
                }
            }
        }

        fun copyEntryToLocal(entry: ExplorerEntry, dstParent: File) {
            if (entry.localPath != null) {
                val srcFile = File(entry.localPath)
                if (!srcFile.exists()) error("Sumber tidak ditemukan: ${entry.name}")
                copyLocalRecursiveToLocal(srcFile, dstParent)
            } else if (entry.safUri != null) {
                val srcDoc = DocumentFile.fromTreeUri(context, Uri.parse(entry.safUri))
                    ?: DocumentFile.fromSingleUri(context, Uri.parse(entry.safUri))
                    ?: error("Sumber SAF tidak dapat diakses")
                if (entry.isDirectory) {
                    val subDir = File(dstParent, app.driveManager.sanitizeFileName(entry.name)).apply { mkdirs() }
                    srcDoc.listFiles().forEach { child ->
                        copyEntryToLocal(
                            ExplorerEntry(
                                name = child.name ?: "item",
                                isDirectory = child.isDirectory,
                                sizeBytes = child.length(),
                                lastModifiedMs = child.lastModified(),
                                mimeType = child.type,
                                safUri = child.uri.toString()
                            ),
                            subDir
                        )
                    }
                } else {
                    val targetFile = app.driveManager.createUniqueInternalFile(dstParent, entry.name)
                    app.driveManager.openInputStream(entry.safUri)?.use { input ->
                        targetFile.outputStream().use { output ->
                            copyStreamWithProgress(entry.name, input, output)
                        }
                    } ?: error("Gagal membaca sumber SAF: ${entry.name}")
                }
            }
        }

        suspend fun copyEntryToSaf(entry: ExplorerEntry, dstDirDoc: DocumentFile) {
            if (entry.isDirectory) {
                val cleanName = app.driveManager.sanitizeFileName(entry.name)
                val existing = dstDirDoc.findFile(cleanName)
                val subDoc = if (existing != null && existing.isDirectory) existing
                else dstDirDoc.createDirectory(cleanName) ?: error("Gagal membuat folder $cleanName di drive eksternal")
                if (entry.localPath != null) {
                    for (child in File(entry.localPath).listFiles().orEmpty()) {
                        copyEntryToSaf(
                            ExplorerEntry(
                                name = child.name,
                                isDirectory = child.isDirectory,
                                sizeBytes = child.length(),
                                lastModifiedMs = child.lastModified(),
                                mimeType = if (child.isDirectory) null else app.driveManager.guessMimeType(child.name),
                                localPath = child.absolutePath
                            ),
                            subDoc
                        )
                    }
                } else if (entry.safUri != null) {
                    val srcDoc = DocumentFile.fromTreeUri(context, Uri.parse(entry.safUri))
                        ?: DocumentFile.fromSingleUri(context, Uri.parse(entry.safUri))
                    for (child in srcDoc?.listFiles().orEmpty()) {
                        copyEntryToSaf(
                            ExplorerEntry(
                                name = child.name ?: "item",
                                isDirectory = child.isDirectory,
                                sizeBytes = child.length(),
                                lastModifiedMs = child.lastModified(),
                                mimeType = child.type,
                                safUri = child.uri.toString()
                            ),
                            subDoc
                        )
                    }
                }
            } else {
                val srcRef = entry.localPath ?: entry.safUri ?: error("Sumber tidak valid")
                val created = app.driveManager.createUniqueSafFile(
                    dstDirDoc,
                    entry.name,
                    entry.mimeType ?: "application/octet-stream"
                ) ?: error("Gagal membuat file di drive eksternal")
                app.driveManager.openInputStream(srcRef)?.use { input ->
                    context.contentResolver.openOutputStream(created.uri)?.use { output ->
                        copyStreamWithProgress(entry.name, input, output)
                    }
                } ?: error("Gagal menyalin data file ${entry.name}")
            }
        }

        scope.launch {
            pasteProgress = PasteProgressState(
                active = true,
                isCut = clip.isCut,
                currentFileName = clip.entries.firstOrNull()?.name ?: "Menghitung file...",
                completedFiles = 0,
                totalFiles = clip.entries.size.coerceAtLeast(1),
                copiedBytes = 0L,
                totalBytes = 0L
            )
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val (tFiles, tBytes) = countFilesAndBytes(clip.entries)
                    totalFilesExpected = tFiles
                    totalBytesExpected = tBytes
                    reportProgress(clip.entries.firstOrNull()?.name ?: "Menyalin...")

                    var count = 0
                    for (entry in clip.entries) {
                        when (val loc = currentLocation) {
                            is ExplorerLocation.LocalDir -> {
                                copyEntryToLocal(entry, File(loc.path))
                            }
                            is ExplorerLocation.SafDir -> {
                                val dirDoc = DocumentFile.fromTreeUri(context, Uri.parse(loc.docUri))
                                    ?: DocumentFile.fromSingleUri(context, Uri.parse(loc.docUri))
                                    ?: error("Folder tujuan SAF tidak dapat diakses")
                                copyEntryToSaf(entry, dirDoc)
                            }
                            ExplorerLocation.ThisPc -> Unit
                        }
                        if (clip.isCut) {
                            if (entry.localPath != null) File(entry.localPath).deleteRecursively()
                            else if (entry.safUri != null) DocumentFile.fromSingleUri(context, Uri.parse(entry.safUri))?.delete()
                        }
                        count++
                    }
                    count
                }
            }
            pasteProgress = PasteProgressState()
            res.onSuccess { count ->
                if (clip.isCut) clipboardItem = null
                refreshTick++
                onShowMessage("Berhasil ${if (clip.isCut) "memindahkan" else "menempel"} $count item.")
            }.onFailure {
                onShowMessage("Gagal menempel item: ${it.message}")
            }
        }
    }

    val displayedEntries = remember(entries, searchQuery) {
        if (searchQuery.isBlank()) entries
        else entries.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val selectedEntries = remember(entries, selectedKeys) {
        entries.filter { it.stableKey in selectedKeys }
    }

    val quickFolders = remember(roots) {
        val extRoot = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        buildList {
            if (extRoot != null && extRoot.exists()) {
                add("Download" to File(extRoot, Environment.DIRECTORY_DOWNLOADS).absolutePath)
                add("Documents" to File(extRoot, Environment.DIRECTORY_DOCUMENTS).absolutePath)
                add("Music" to File(extRoot, Environment.DIRECTORY_MUSIC).absolutePath)
                add("Movies" to File(extRoot, Environment.DIRECTORY_MOVIES).absolutePath)
                add("Pictures" to File(extRoot, Environment.DIRECTORY_PICTURES).absolutePath)
            }
            add("App Downloads" to app.driveManager.internalDownloadsDir.absolutePath)
            add("App Archives" to app.driveManager.internalArchivesDir.absolutePath)
            add("App Vault" to app.driveManager.internalVaultDir.absolutePath)
        }.filter { File(it.second).exists() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { ev ->
                if (
                    ev.type == KeyEventType.KeyDown &&
                    ev.key == Key.Backspace &&
                    !isSearchFocused &&
                    !showNewFolderDialog &&
                    renamingEntry == null &&
                    textPreviewTitle == null &&
                    navIndex > 0
                ) {
                    goBack()
                } else {
                    false
                }
            }
    ) {
        // Windows Explorer Top Command Ribbon & Address Bar
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = { goBack() }, enabled = navIndex > 0, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(onClick = { goForward() }, enabled = navIndex < navHistory.lastIndex, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                    }
                    IconButton(onClick = { goUp() }, enabled = currentLocation != ExplorerLocation.ThisPc, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Up")
                    }
                    IconButton(onClick = { refreshTick++ }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }

                    // Breadcrumb Path Box (Windows Address Bar)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.Computer, contentDescription = null, tint = WinBlue, modifier = Modifier.size(16.dp))
                            Text(
                                text = "This PC",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { navigateTo(ExplorerLocation.ThisPc) }
                            )
                            when (val loc = currentLocation) {
                                ExplorerLocation.ThisPc -> Unit
                                is ExplorerLocation.LocalDir -> {
                                    Text(" › ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    Text(
                                        text = loc.path,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                is ExplorerLocation.SafDir -> {
                                    loc.trail.forEachIndexed { idx, (uriStr, label) ->
                                        Text(" › ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            modifier = Modifier.clickable {
                                                navigateTo(ExplorerLocation.SafDir(loc.treeUri, uriStr, loc.trail.take(idx + 1)))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Folder Search Box
                    if (isTabletWide && currentLocation != ExplorerLocation.ThisPc) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                            modifier = Modifier.width(190.dp).height(34.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text("Cari folder...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp),
                                        cursorBrush = SolidColor(WinBlue),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged {
                                                isSearchFocused = it.isFocused
                                                onTextInputFocusChanged?.invoke(it.isFocused)
                                            }
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(14.dp).clickable { searchQuery = "" }
                                    )
                                }
                            }
                        }
                    }

                    if (clipboardItem != null && currentLocation != ExplorerLocation.ThisPc) {
                        Button(
                            onClick = { pasteClipboardHere() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Tempel", fontSize = 12.sp)
                        }
                    }

                    if (currentLocation != ExplorerLocation.ThisPc) {
                        IconButton(onClick = { showNewFolderDialog = true }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = "Folder Baru")
                        }
                    }
                    IconButton(onClick = { safFolderPicker.launch(null) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Filled.Usb, contentDescription = "Mount Folder SD/USB", tint = WinBlue)
                    }
                    IconButton(onClick = { gridView = !gridView }, modifier = Modifier.size(34.dp)) {
                        Icon(if (gridView) Icons.Filled.ViewList else Icons.Filled.GridView, contentDescription = "Ubah Tampilan")
                    }
                }

                // Multi-Selection Contextual Command Bar
                if (inSelectionMode && currentLocation != ExplorerLocation.ThisPc) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = WinBlue.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, WinBlue.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "${selectedEntries.size} dipilih",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WinBlue
                            )
                            TextButton(
                                onClick = {
                                    selectedKeys = if (selectedKeys.size == displayedEntries.size) {
                                        emptySet()
                                    } else {
                                        displayedEntries.mapTo(HashSet()) { it.stableKey }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Filled.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (selectedKeys.size == displayedEntries.size) "Batal Semua" else "Pilih Semua",
                                    fontSize = 12.sp
                                )
                            }
                            TextButton(
                                onClick = {
                                    if (selectedEntries.isNotEmpty()) {
                                        clipboardItem = FileClipboardItem(selectedEntries, isCut = false)
                                        onShowMessage("${selectedEntries.size} item disalin ke clipboard.")
                                        selectedKeys = emptySet()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy", fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = {
                                    if (selectedEntries.isNotEmpty()) {
                                        clipboardItem = FileClipboardItem(selectedEntries, isCut = true)
                                        onShowMessage("${selectedEntries.size} item ditandai untuk dipindahkan.")
                                        selectedKeys = emptySet()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Cut", fontSize = 12.sp)
                            }
                            if (selectedEntries.size == 1) {
                                val single = selectedEntries.first()
                                TextButton(
                                    onClick = { renamingEntry = single },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Rename", fontSize = 12.sp)
                                }
                                TextButton(
                                    onClick = { propertiesEntry = single },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Properti", fontSize = 12.sp)
                                }
                            }
                            TextButton(
                                onClick = {
                                    if (selectedEntries.isNotEmpty()) {
                                        deletingEntries = selectedEntries
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Hapus", fontSize = 12.sp, color = Color(0xFFEF4444))
                            }
                            IconButton(
                                onClick = { selectedKeys = emptySet() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Tutup Seleksi", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

        if (!hasAllFiles) {
            Surface(color = WinBlue.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = WinBlue, modifier = Modifier.size(18.dp))
                        Text(
                            "Aktifkan 'Izin Akses Semua File' agar File Explorer bisa membaca seluruh file & folder di Internal / SD Card.",
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = {
                        onShowMessage(DesktopLauncherManager.openAllFilesAccessSettings(context))
                    }) {
                        Text("Aktifkan", color = WinBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Body: Left Navigation Pane + Right Explorer Canvas
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isTabletWide) {
                Column(
                    modifier = Modifier
                        .width(210.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "Quick Access",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    SidebarNavRow(
                        label = "This PC",
                        icon = Icons.Filled.Computer,
                        iconTint = WinBlue,
                        selected = currentLocation == ExplorerLocation.ThisPc,
                        onClick = { navigateTo(ExplorerLocation.ThisPc) }
                    )
                    quickFolders.forEach { (label, path) ->
                        SidebarNavRow(
                            label = label,
                            icon = Icons.Filled.Folder,
                            iconTint = WinFolderGold,
                            selected = (currentLocation as? ExplorerLocation.LocalDir)?.path == path,
                            onClick = { navigateTo(ExplorerLocation.LocalDir(path, label)) }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Text(
                        "Drives & Storage",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    roots.forEach { root ->
                        val driveIcon = when {
                            root.isSafTree -> Icons.Filled.Usb
                            root.isRemovable -> Icons.Filled.SdStorage
                            else -> Icons.Filled.Storage
                        }
                        SidebarNavRow(
                            label = root.label,
                            icon = driveIcon,
                            iconTint = if (root.isRemovable || root.isSafTree) WinBlue else MaterialTheme.colorScheme.primary,
                            selected = if (root.isSafTree) {
                                (currentLocation as? ExplorerLocation.SafDir)?.treeUri == root.pathOrUri
                            } else {
                                (currentLocation as? ExplorerLocation.LocalDir)?.path == root.pathOrUri
                            },
                            onClick = {
                                if (root.isSafTree) {
                                    navigateTo(ExplorerLocation.SafDir(root.pathOrUri, root.pathOrUri, listOf(root.pathOrUri to root.label)))
                                } else {
                                    navigateTo(ExplorerLocation.LocalDir(root.pathOrUri, root.label))
                                }
                            }
                        )
                    }
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            }

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                when {
                    isLoading -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter), color = WinBlue)
                    }

                    currentLocation == ExplorerLocation.ThisPc -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                Text("Devices and Drives (${roots.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                            item {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(if (isTabletWide) 2 else 1),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.height(((roots.size / (if (isTabletWide) 2 else 1) + 1) * 108).dp.coerceAtMost(420.dp))
                                ) {
                                    items(roots, key = { it.id }) { root ->
                                        val used = if (root.totalBytes != null && root.availableBytes != null) {
                                            (root.totalBytes - root.availableBytes).coerceAtLeast(0L)
                                        } else null
                                        val frac = if (root.totalBytes != null && root.totalBytes > 0L && used != null) {
                                            (used.toFloat() / root.totalBytes.toFloat()).coerceIn(0f, 1f)
                                        } else 0f
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                            modifier = Modifier.clickable {
                                                if (root.isSafTree) {
                                                    navigateTo(ExplorerLocation.SafDir(root.pathOrUri, root.pathOrUri, listOf(root.pathOrUri to root.label)))
                                                } else {
                                                    navigateTo(ExplorerLocation.LocalDir(root.pathOrUri, root.label))
                                                }
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = when {
                                                        root.isSafTree -> Icons.Filled.Usb
                                                        root.isRemovable -> Icons.Filled.SdStorage
                                                        else -> Icons.Filled.Storage
                                                    },
                                                    contentDescription = null,
                                                    tint = WinBlue,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(root.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    LinearProgressIndicator(
                                                        progress = { frac },
                                                        color = if (frac > 0.9f) Color(0xFFD93025) else WinBlue,
                                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                                    )
                                                    Text(
                                                        text = if (root.totalBytes != null && root.availableBytes != null) {
                                                            "${Formatters.formatBytes(root.availableBytes)} tersedia dari ${Formatters.formatBytes(root.totalBytes)}"
                                                        } else root.pathOrUri,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                Text("Quick Folders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                            items(quickFolders.chunked(if (isTabletWide) 4 else 2)) { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    row.forEach { (label, path) ->
                                        Card(
                                            modifier = Modifier.weight(1f).clickable { navigateTo(ExplorerLocation.LocalDir(path, label)) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(Icons.Filled.Folder, contentDescription = null, tint = WinFolderGold, modifier = Modifier.size(26.dp))
                                                Text(label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                    repeat((if (isTabletWide) 4 else 2) - row.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    displayedEntries.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Folder kosong atau izin baca tidak diberikan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    gridView -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 116.dp),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(displayedEntries, key = { it.stableKey }) { entry ->
                                var menuOpen by remember { mutableStateOf(false) }
                                val isSelected = entry.stableKey in selectedKeys
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) WinBlue.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            if (isSelected) 1.5.dp else 1.dp,
                                            if (isSelected) WinBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    if (inSelectionMode) toggleSelection(entry) else openEntry(entry)
                                                },
                                                onLongClick = { toggleSelection(entry) }
                                            )
                                    ) {
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            if (inSelectionMode) {
                                                Icon(
                                                    imageVector = if (isSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                                    contentDescription = null,
                                                    tint = if (isSelected) WinBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(6.dp)
                                                        .size(18.dp)
                                                )
                                            }
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = explorerEntryIcon(entry),
                                                    contentDescription = null,
                                                    tint = explorerEntryTint(entry, MaterialTheme.colorScheme.primary),
                                                    modifier = Modifier.size(38.dp)
                                                )
                                                Text(
                                                    entry.name,
                                                    fontSize = 12.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                    ExplorerContextMenu(
                                        expanded = menuOpen,
                                        entry = entry,
                                        onDismiss = { menuOpen = false },
                                        onSelect = { toggleSelection(entry) },
                                        onOpen = { openEntry(entry) },
                                        onCopy = { clipboardItem = FileClipboardItem(listOf(entry), isCut = false) },
                                        onCut = { clipboardItem = FileClipboardItem(listOf(entry), isCut = true) },
                                        onRename = { renamingEntry = entry },
                                        onProperties = { propertiesEntry = entry },
                                        onDelete = { deletingEntries = listOf(entry) }
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Windows Explorer Details Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Nama", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(2.2f))
                                Text("Tanggal Diubah", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.2f))
                                Text("Tipe", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.9f))
                                Text("Ukuran", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(80.dp), textAlign = TextAlign.End)
                                Spacer(Modifier.width(34.dp))
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(displayedEntries, key = { it.stableKey }) { entry ->
                                    var menuOpen by remember { mutableStateOf(false) }
                                    val isSelected = entry.stableKey in selectedKeys
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (isSelected) WinBlue.copy(alpha = 0.14f) else Color.Transparent)
                                            .combinedClickable(
                                                onClick = {
                                                    if (inSelectionMode) toggleSelection(entry) else openEntry(entry)
                                                },
                                                onLongClick = { toggleSelection(entry) }
                                            )
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(2.2f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (inSelectionMode) {
                                                Icon(
                                                    imageVector = if (isSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                                    contentDescription = null,
                                                    tint = if (isSelected) WinBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(19.dp)
                                                )
                                            }
                                            Icon(
                                                imageVector = explorerEntryIcon(entry),
                                                contentDescription = null,
                                                tint = explorerEntryTint(entry, MaterialTheme.colorScheme.primary),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(entry.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Text(
                                            if (entry.lastModifiedMs > 0L) Formatters.formatDateTime(entry.lastModifiedMs) else "-",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1.2f),
                                            maxLines = 1
                                        )
                                        Text(
                                            if (entry.isDirectory) "Folder" else entry.name.substringAfterLast('.', "File").uppercase(Locale.US),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(0.9f),
                                            maxLines = 1
                                        )
                                        Text(
                                            if (entry.isDirectory) "-" else Formatters.formatBytes(entry.sizeBytes),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.width(80.dp),
                                            textAlign = TextAlign.End
                                        )
                                        Box {
                                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(30.dp)) {
                                                Icon(Icons.Filled.MoreVert, contentDescription = "Opsi File", modifier = Modifier.size(18.dp))
                                            }
                                            ExplorerContextMenu(
                                                expanded = menuOpen,
                                                entry = entry,
                                                onDismiss = { menuOpen = false },
                                                onSelect = { toggleSelection(entry) },
                                                onOpen = { openEntry(entry) },
                                                onCopy = { clipboardItem = FileClipboardItem(listOf(entry), isCut = false) },
                                                onCut = { clipboardItem = FileClipboardItem(listOf(entry), isCut = true) },
                                                onRename = { renamingEntry = entry },
                                                onProperties = { propertiesEntry = entry },
                                                onDelete = { deletingEntries = listOf(entry) }
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Windows Explorer Status Bar
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (currentLocation == ExplorerLocation.ThisPc) {
                        "${roots.size} penyimpanan terdeteksi"
                    } else if (inSelectionMode) {
                        "${selectedEntries.size} dari ${displayedEntries.size} item dipilih (tahan item lain untuk tambah)"
                    } else {
                        "${displayedEntries.size} item (tahan lama untuk multi-pilih)"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (clipboardItem != null) {
                    val clip = clipboardItem!!
                    val summary = if (clip.entries.size == 1) clip.entries.first().name else "${clip.entries.size} item"
                    Text(
                        "${if (clip.isCut) "Cut" else "Copy"}: $summary",
                        fontSize = 11.sp,
                        color = WinBlue
                    )
                }
            }
        }
    }

    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("New Folder") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Buat Folder Baru") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Nama Folder") },
                    singleLine = true,
                    modifier = Modifier.onFocusChanged { onTextInputFocusChanged?.invoke(it.isFocused) }
                )
            },
            confirmButton = {
                Button(onClick = {
                    val clean = app.driveManager.sanitizeFileName(folderName)
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            when (val loc = currentLocation) {
                                is ExplorerLocation.LocalDir -> File(loc.path, clean).mkdirs()
                                is ExplorerLocation.SafDir -> {
                                    val doc = DocumentFile.fromTreeUri(context, Uri.parse(loc.docUri))
                                        ?: DocumentFile.fromSingleUri(context, Uri.parse(loc.docUri))
                                    doc?.createDirectory(clean) != null
                                }
                                ExplorerLocation.ThisPc -> false
                            }
                        }
                        if (ok) refreshTick++ else onShowMessage("Gagal membuat folder.")
                    }
                    showNewFolderDialog = false
                }) { Text("Buat") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Batal") }
            }
        )
    }

    renamingEntry?.let { entry ->
        var newName by remember(entry) { mutableStateOf(entry.name) }
        AlertDialog(
            onDismissRequest = { renamingEntry = null },
            title = { Text("Ubah Nama") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nama Baru") },
                    singleLine = true,
                    modifier = Modifier.onFocusChanged { onTextInputFocusChanged?.invoke(it.isFocused) }
                )
            },
            confirmButton = {
                Button(onClick = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotBlank()) {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                if (entry.localPath != null) {
                                    val src = File(entry.localPath)
                                    src.renameTo(File(src.parentFile, trimmed))
                                } else if (entry.safUri != null) {
                                    DocumentFile.fromSingleUri(context, Uri.parse(entry.safUri))?.renameTo(trimmed) == true
                                } else false
                            }
                            if (ok) refreshTick++ else onShowMessage("Gagal mengganti nama.")
                        }
                    }
                    renamingEntry = null
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { renamingEntry = null }) { Text("Batal") }
            }
        )
    }

    if (deletingEntries.isNotEmpty()) {
        val targets = deletingEntries
        AlertDialog(
            onDismissRequest = { deletingEntries = emptyList() },
            title = { Text("Hapus ${targets.size} Item?") },
            text = {
                Text(
                    if (targets.size == 1) "Apakah Anda yakin ingin menghapus '${targets.first().name}'?"
                    else "Apakah Anda yakin ingin menghapus ${targets.size} file/folder yang dipilih?"
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val failedCount = withContext(Dispatchers.IO) {
                            var failed = 0
                            for (entry in targets) {
                                val ok = if (entry.localPath != null) File(entry.localPath).deleteRecursively()
                                else if (entry.safUri != null) DocumentFile.fromSingleUri(context, Uri.parse(entry.safUri))?.delete() == true
                                else false
                                if (!ok) failed++
                            }
                            failed
                        }
                        selectedKeys = emptySet()
                        refreshTick++
                        if (failedCount > 0) {
                            onShowMessage("$failedCount item gagal dihapus.")
                        } else {
                            onShowMessage("${targets.size} item berhasil dihapus.")
                        }
                    }
                    deletingEntries = emptyList()
                }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { deletingEntries = emptyList() }) { Text("Batal") }
            }
        )
    }

    propertiesEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { propertiesEntry = null },
            title = { Text("Properti: ${entry.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tipe: ${if (entry.isDirectory) "Folder" else entry.mimeType ?: "File"}")
                    Text("Ukuran: ${if (entry.isDirectory) "-" else Formatters.formatBytes(entry.sizeBytes)}")
                    Text("Diubah: ${if (entry.lastModifiedMs > 0L) Formatters.formatDateTime(entry.lastModifiedMs) else "-"}")
                    Text("Lokasi: ${entry.localPath ?: entry.safUri ?: "-"}")
                }
            },
            confirmButton = {
                TextButton(onClick = { propertiesEntry = null }) { Text("Tutup") }
            }
        )
    }

    if (textPreviewTitle != null && textPreviewContent != null) {
        AlertDialog(
            onDismissRequest = {
                textPreviewTitle = null
                textPreviewContent = null
            },
            title = { Text(textPreviewTitle!!) },
            text = {
                Text(
                    text = textPreviewContent!!,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.height(320.dp).verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    textPreviewTitle = null
                    textPreviewContent = null
                }) { Text("Tutup") }
            }
        )
    }

    if (pasteProgress.active) {
        val frac = when {
            pasteProgress.totalBytes > 0L ->
                (pasteProgress.copiedBytes.toFloat() / pasteProgress.totalBytes.toFloat()).coerceIn(0f, 1f)
            pasteProgress.totalFiles > 0 ->
                (pasteProgress.completedFiles.toFloat() / pasteProgress.totalFiles.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }
        val pct = (frac * 100f).roundToInt().coerceIn(0, 100)
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(if (pasteProgress.isCut) "Memindahkan File..." else "Menyalin File...")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        pasteProgress.currentFileName,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    LinearProgressIndicator(
                        progress = { frac },
                        color = WinBlue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${pasteProgress.completedFiles} / ${pasteProgress.totalFiles} file",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "$pct%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = WinBlue
                        )
                    }
                    if (pasteProgress.totalBytes > 0L) {
                        Text(
                            "${Formatters.formatBytes(pasteProgress.copiedBytes)} / ${Formatters.formatBytes(pasteProgress.totalBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun SidebarNavRow(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) WinBlue.copy(alpha = 0.16f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExplorerContextMenu(
    expanded: Boolean,
    entry: ExplorerEntry,
    onDismiss: () -> Unit,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onRename: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Buka") }, onClick = { onDismiss(); onOpen() })
        DropdownMenuItem(
            text = { Text("Pilih (Multi-Select)") },
            leadingIcon = { Icon(Icons.Filled.SelectAll, contentDescription = null) },
            onClick = { onDismiss(); onSelect() }
        )
        DropdownMenuItem(
            text = { Text("Salin (Copy)") },
            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
            onClick = { onDismiss(); onCopy() }
        )
        DropdownMenuItem(
            text = { Text("Pindahkan (Cut)") },
            leadingIcon = { Icon(Icons.Filled.ContentCut, contentDescription = null) },
            onClick = { onDismiss(); onCut() }
        )
        DropdownMenuItem(
            text = { Text("Ubah Nama") },
            leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
            onClick = { onDismiss(); onRename() }
        )
        DropdownMenuItem(
            text = { Text("Properti") },
            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
            onClick = { onDismiss(); onProperties() }
        )
        DropdownMenuItem(
            text = { Text("Hapus") },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            onClick = { onDismiss(); onDelete() }
        )
    }
}

private fun explorerEntryIcon(entry: ExplorerEntry): ImageVector {
    if (entry.isDirectory) return Icons.Filled.Folder
    return when (entry.name.substringAfterLast('.', "").lowercase(Locale.US)) {
        "pdf" -> Icons.Filled.PictureAsPdf
        "zim", "epub" -> Icons.Filled.MenuBook
        "mp3", "m4a", "flac", "wav", "ogg", "opus", "aac" -> Icons.Filled.AudioFile
        "mp4", "mkv", "webm", "mov", "avi" -> Icons.Filled.Movie
        "jpg", "jpeg", "png", "webp", "gif" -> Icons.Filled.Image
        "zip", "rar", "7z", "tar", "gz" -> Icons.Filled.Archive
        "txt", "md", "json", "csv", "xml", "log" -> Icons.Filled.Description
        else -> Icons.Filled.InsertDriveFile
    }
}

private fun explorerEntryTint(entry: ExplorerEntry, defaultTint: Color): Color {
    if (entry.isDirectory) return WinFolderGold
    return when (entry.name.substringAfterLast('.', "").lowercase(Locale.US)) {
        "pdf" -> Color(0xFFE53935)
        "mp3", "m4a", "flac", "wav", "ogg" -> Color(0xFFFF4081)
        "mp4", "mkv", "webm", "mov" -> Color(0xFF7C4DFF)
        "jpg", "jpeg", "png", "webp", "gif" -> Color(0xFF26A69A)
        "zim" -> Color(0xFF0288D1)
        else -> defaultTint
    }
}
