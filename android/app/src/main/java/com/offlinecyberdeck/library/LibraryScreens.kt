package com.offlinecyberdeck.library

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinecyberdeck.CyberDeckApplication
import com.offlinecyberdeck.archive.ArchiveListScreen
import com.offlinecyberdeck.core.database.entity.LibraryItemEntity
import com.offlinecyberdeck.core.format.Formatters
import com.offlinecyberdeck.core.model.LibraryCategory
import com.offlinecyberdeck.core.model.LibraryFileType
import com.offlinecyberdeck.ui.theme.DeckAmber
import com.offlinecyberdeck.ui.theme.DeckGreen
import com.offlinecyberdeck.ui.theme.DeckRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LibraryScreen(
    app: CyberDeckApplication,
    initialTabIndex: Int = 0,
    onOpenArchiveReader: (String) -> Unit,
    onOpenPdfReader: (String) -> Unit,
    onOpenTextReader: (String) -> Unit,
    onOpenZimReader: (String) -> Unit,
    onOpenBrowserUrl: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    var selectedTab by remember(initialTabIndex) { mutableIntStateOf(initialTabIndex) }
    val items by app.vaultRepository.itemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by app.settingsRepository.settingsFlow.collectAsStateWithLifecycle(
        initialValue = com.offlinecyberdeck.core.database.entity.AppSettingsEntity()
    )
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<LibraryCategory?>(null) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingDelete by remember { mutableStateOf<LibraryItemEntity?>(null) }
    var showAutoImportDialog by remember { mutableStateOf(false) }
    var isAutoImporting by remember { mutableStateOf(false) }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
        }
    }

    val autoImportFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            scope.launch {
                isAutoImporting = true
                app.vaultRepository.autoImportFromFolderTree(treeUri)
                    .onSuccess { summary ->
                        showAutoImportDialog = false
                        onShowMessage(
                            "Auto Import: ${summary.importedCount} new items added (${summary.skippedCount} already in Vault)"
                        )
                    }
                    .onFailure { err ->
                        onShowMessage("Auto Import failed: ${err.message}")
                    }
                isAutoImporting = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Knowledge Vault") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Web Archives") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Packages") })
        }

        when (selectedTab) {
            1 -> {
                ArchiveListScreen(
                    app = app,
                    onOpenArchiveReader = onOpenArchiveReader,
                    onOpenLiveUrl = onOpenBrowserUrl
                )
            }
            2 -> {
                PackageDirectoryTab(
                    packages = remember { app.vaultRepository.getCuratedPackageDirectory() },
                    onOpenSourceInBrowser = onOpenBrowserUrl
                )
            }
            else -> {
                val filtered = remember(items, query, selectedCategory, favoritesOnly) {
                    items.filter { item ->
                        val qOk = query.isBlank() ||
                            item.title.contains(query, true) ||
                            item.tags.contains(query, true) ||
                            item.source.contains(query, true) ||
                            item.fileName.contains(query, true)
                        val catOk = selectedCategory == null || item.category == selectedCategory
                        val favOk = !favoritesOnly || item.isFavorite
                        qOk && catOk && favOk
                    }
                }

                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Knowledge Vault (${items.size})",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showAutoImportDialog = true }) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Auto Import")
                            }
                            Button(onClick = { docPicker.launch(arrayOf("*/*")) }) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Import")
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text("Search books, PDFs, EPUBs, guides, tags") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = favoritesOnly,
                            onClick = { favoritesOnly = !favoritesOnly },
                            label = { Text("Favorites") }
                        )
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("All Categories") }
                        )
                        LibraryCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    if (filtered.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (items.isEmpty()) {
                                    "Your offline Knowledge Vault is empty. Tap Import to catalog PDFs, EPUBs, HTML/Markdown guides, ZIM archives, or offline map packages."
                                } else {
                                    "No items match your current filter."
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filtered, key = { it.id }) { item ->
                                val available = app.vaultRepository.isItemAvailable(item)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (!available) {
                                                onShowMessage("File unavailable — check Deck Drive connection.")
                                                return@clickable
                                            }
                                            scope.launch {
                                                app.vaultRepository.updateItem(
                                                    item.copy(lastOpenedAt = System.currentTimeMillis())
                                                )
                                            }
                                            when (item.fileType) {
                                                LibraryFileType.PDF -> onOpenPdfReader(item.id)
                                                LibraryFileType.ZIM -> onOpenZimReader(item.id)
                                                LibraryFileType.EPUB,
                                                LibraryFileType.HTML,
                                                LibraryFileType.TEXT -> onOpenTextReader(item.id)
                                                else -> {
                                                    val intent = app.vaultRepository.buildExternalViewIntent(item)
                                                    if (intent == null) {
                                                        onShowMessage("Cannot open file.")
                                                    } else {
                                                        runCatching { context.startActivity(intent) }
                                                            .onFailure {
                                                                onShowMessage("No installed app handles ${item.fileType.label} files.")
                                                            }
                                                    }
                                                }
                                            }
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    "${item.category.label} • ${item.fileType.label} • ${Formatters.formatBytes(item.sizeBytes)}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(onClick = {
                                                scope.launch {
                                                    app.vaultRepository.updateItem(item.copy(isFavorite = !item.isFavorite))
                                                }
                                            }) {
                                                Icon(
                                                    if (item.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                                    contentDescription = "Favorite"
                                                )
                                            }
                                            IconButton(onClick = { pendingDelete = item }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                            }
                                        }
                                        Text(
                                            text = if (available) "Offline Ready (${item.storageTarget.label})" else "Drive Disconnected / File Unavailable",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (available) DeckGreen else DeckRed
                                        )
                                        if (item.description.isNotBlank()) {
                                            Text(
                                                item.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = DeckAmber
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingImportUri?.let { uri ->
        var selectedCat by remember { mutableStateOf(LibraryCategory.BOOKS) }
        var catMenuOpen by remember { mutableStateOf(false) }
        var language by remember { mutableStateOf("en") }
        var source by remember { mutableStateOf("Local Import") }
        var tags by remember { mutableStateOf("") }
        var copyIntoInternal by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Import to Knowledge Vault") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        OutlinedButton(onClick = { catMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Category: ${selectedCat.label}")
                        }
                        DropdownMenu(expanded = catMenuOpen, onDismissRequest = { catMenuOpen = false }) {
                            LibraryCategory.entries.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.label) },
                                    onClick = {
                                        selectedCat = c
                                        catMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(value = source, onValueChange = { source = it }, label = { Text("Source / Publisher") })
                    OutlinedTextField(value = language, onValueChange = { language = it }, label = { Text("Language") })
                    OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("Tags") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = copyIntoInternal, onCheckedChange = { copyIntoInternal = it })
                        Text("Copy file into app internal storage (leave unchecked to link directly on Deck Drive)")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = uri
                    pendingImportUri = null
                    scope.launch {
                        app.vaultRepository.importDocument(
                            uri = target,
                            category = selectedCat,
                            language = language,
                            source = source,
                            tags = tags,
                            copyIntoVault = copyIntoInternal
                        ).onSuccess {
                            onShowMessage("Imported '${it.title}' to Vault")
                        }.onFailure {
                            onShowMessage("Import failed: ${it.message}")
                        }
                    }
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("Cancel") }
            }
        )
    }

    pendingDelete?.let { item ->
        var deleteLocal by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove Vault Item?") },
            text = {
                Column {
                    Text("Remove '${item.title}' from the Knowledge Vault catalog?")
                    if (!item.fileUri.startsWith("content://")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = deleteLocal, onCheckedChange = { deleteLocal = it })
                            Text("Also delete internal copied file")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch { app.vaultRepository.deleteItem(item, deleteLocal) }
                    pendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showAutoImportDialog) {
        AlertDialog(
            onDismissRequest = { if (!isAutoImporting) showAutoImportDialog = false },
            title = { Text("Auto Import to Knowledge Vault") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Automatically scans folders for PDF, EPUB, ZIM, HTML, TXT/MD, ZIP, Offline Maps (.map/.mbtiles/.obf/.gpx), and Images without duplicating existing items.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (isAutoImporting) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Scanning files...", style = MaterialTheme.typography.labelMedium)
                    } else {
                        Button(
                            onClick = {
                                scope.launch {
                                    isAutoImporting = true
                                    app.vaultRepository.autoImportFromConfiguredSources(settings.deckDriveTreeUri)
                                        .onSuccess { summary ->
                                            showAutoImportDialog = false
                                            onShowMessage(
                                                "Auto Import: ${summary.importedCount} new items added (${summary.skippedCount} skipped)"
                                            )
                                        }
                                        .onFailure { err ->
                                            onShowMessage("Auto Import failed: ${err.message}")
                                        }
                                    isAutoImporting = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Scan App Downloads & Deck Drive")
                        }
                        OutlinedButton(
                            onClick = { autoImportFolderPicker.launch(null) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Pick Folder (Downloads / USB / SD)")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAutoImportDialog = false },
                    enabled = !isAutoImporting
                ) { Text("Close") }
            }
        )
    }
}

@Composable
private fun PackageDirectoryTab(
    packages: List<com.offlinecyberdeck.core.model.PackageDirectoryEntry>,
    onOpenSourceInBrowser: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Offline Resource Directory", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Verified external repositories for offline datasets and books. No packs are pre-installed; open a source in the browser to download packages to your Deck Drive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(packages, key = { it.id }) { pkg ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(pkg.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${pkg.category.label} • Lang: ${pkg.language} • Est. Size: ${pkg.estimatedSize}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(pkg.description, style = MaterialTheme.typography.bodySmall)
                    Text(
                        pkg.formatNote,
                        style = MaterialTheme.typography.labelMedium,
                        color = DeckAmber
                    )
                    OutlinedButton(onClick = { onOpenSourceInBrowser(pkg.sourceUrl) }) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Open Source in Browser")
                    }
                }
            }
        }
    }
}

@Composable
fun PdfViewerScreen(
    app: CyberDeckApplication,
    itemId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var item by remember { mutableStateOf<LibraryItemEntity?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var rendererHolder by remember { mutableStateOf<Pair<ParcelFileDescriptor, PdfRenderer>?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var nightInvertMode by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpInput by remember { mutableStateOf("") }

    val invertColorFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    DisposableEffect(view) {
        val prev = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = prev }
    }

    LaunchedEffect(itemId) {
        val loaded = app.database.libraryDao().getById(itemId)
        item = loaded
        if (loaded == null) {
            errorText = "Document record not found."
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val pfd = if (loaded.fileUri.startsWith("content://")) {
                    context.contentResolver.openFileDescriptor(Uri.parse(loaded.fileUri), "r")
                } else {
                    ParcelFileDescriptor.open(File(loaded.fileUri), ParcelFileDescriptor.MODE_READ_ONLY)
                } ?: error("Cannot open PDF file")
                val renderer = PdfRenderer(pfd)
                rendererHolder = pfd to renderer
                pageCount = renderer.pageCount
                currentPage = loaded.lastReadPosition.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
            }.onFailure {
                errorText = "Unable to open PDF: ${it.message}"
            }
        }
    }

    LaunchedEffect(rendererHolder, currentPage) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        val pair = rendererHolder ?: return@LaunchedEffect
        val renderer = pair.second
        if (renderer.pageCount <= 0) return@LaunchedEffect
        val safePage = currentPage.coerceIn(0, renderer.pageCount - 1)
        val bmp = withContext(Dispatchers.IO) {
            renderer.openPage(safePage).use { page ->
                fun renderBounded(maxWidth: Int, maxHeight: Int, config: Bitmap.Config): Bitmap {
                    val pageW = page.width.coerceAtLeast(1)
                    val pageH = page.height.coerceAtLeast(1)
                    val widthScale = maxWidth.toFloat() / pageW
                    val heightScale = maxHeight.toFloat() / pageH
                    val factor = minOf(widthScale, heightScale)
                    val targetW = (pageW * factor).toInt().coerceAtLeast(1)
                    val targetH = (pageH * factor).toInt().coerceAtLeast(1)
                    return Bitmap.createBitmap(targetW, targetH, config).apply {
                        eraseColor(android.graphics.Color.WHITE)
                        page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
                try {
                    renderBounded(1080, 1920, Bitmap.Config.ARGB_8888)
                } catch (_: OutOfMemoryError) {
                    System.gc()
                    renderBounded(540, 960, Bitmap.Config.ARGB_8888)
                }
            }
        }
        val old = pageBitmap
        pageBitmap = bmp
        old?.recycle()
        item?.let {
            scope.launch { app.vaultRepository.updateItem(it.copy(lastReadPosition = safePage)) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pageBitmap?.recycle()
            pageBitmap = null
            rendererHolder?.let { (pfd, renderer) ->
                runCatching { renderer.close() }
                runCatching { pfd.close() }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    item?.title ?: "PDF Reader",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { nightInvertMode = !nightInvertMode }) {
                    Icon(
                        Icons.Filled.Contrast,
                        contentDescription = "Night Invert PDF",
                        tint = if (nightInvertMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { if (currentPage > 0) currentPage-- },
                    enabled = currentPage > 0
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page")
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable(enabled = pageCount > 1) {
                        jumpInput = "${currentPage + 1}"
                        showJumpDialog = true
                    }
                ) {
                    Text(
                        text = if (pageCount > 0) "${currentPage + 1}/$pageCount" else "0/0",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                IconButton(
                    onClick = { if (currentPage + 1 < pageCount) currentPage++ },
                    enabled = currentPage + 1 < pageCount
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page")
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .background(if (nightInvertMode) Color.Black else MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            val bmp = pageBitmap
            if (errorText != null) {
                Text(errorText!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            } else if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF Page ${currentPage + 1}",
                    contentScale = ContentScale.Fit,
                    colorFilter = if (nightInvertMode) invertColorFilter else null,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(currentPage) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1.05f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        scale = 2.0f
                                    }
                                }
                            )
                        }
                        .pointerInput(currentPage) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 3.5f)
                                scale = newScale
                                if (newScale > 1.02f) {
                                    val maxX = (size.width * (newScale - 1f)) / 2f
                                    val maxY = (size.height * (newScale - 1f)) / 2f
                                    offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                    offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                )
            } else {
                Text("Rendering page...")
            }

            if (scale > 1.05f) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clickable {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }
                ) {
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.1f", scale)}x • Reset",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }

    if (showJumpDialog && pageCount > 0) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Page (1 – $pageCount)") },
            text = {
                OutlinedTextField(
                    value = jumpInput,
                    onValueChange = { jumpInput = it.filter { ch -> ch.isDigit() } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Page number") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    val target = jumpInput.toIntOrNull()?.coerceIn(1, pageCount)
                    if (target != null) currentPage = target - 1
                    showJumpDialog = false
                }) { Text("Go") }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun TextDocumentReaderScreen(
    app: CyberDeckApplication,
    itemId: String,
    onBack: () -> Unit
) {
    val view = LocalView.current
    var title by remember { mutableStateOf("Document Reader") }
    var body by remember { mutableStateOf("Loading...") }

    DisposableEffect(view) {
        val prev = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = prev }
    }

    LaunchedEffect(itemId) {
        val loaded = app.database.libraryDao().getById(itemId)
        if (loaded == null) {
            body = "Document record not found."
        } else {
            title = loaded.title
            app.vaultRepository.loadTextOrEpubContent(loaded)
                .onSuccess { body = it }
                .onFailure { body = "Error loading document: ${it.message}" }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
