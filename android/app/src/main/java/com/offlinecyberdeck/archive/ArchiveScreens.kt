package com.offlinecyberdeck.archive

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinecyberdeck.CyberDeckApplication
import com.offlinecyberdeck.core.database.entity.ArchivedPageEntity
import com.offlinecyberdeck.core.format.Formatters
import com.offlinecyberdeck.core.model.ArchiveStatus
import com.offlinecyberdeck.ui.theme.DeckAmber
import com.offlinecyberdeck.ui.theme.DeckGreen
import com.offlinecyberdeck.ui.theme.DeckRed
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream

@Composable
fun ArchiveListScreen(
    app: CyberDeckApplication,
    onOpenArchiveReader: (String) -> Unit,
    onOpenLiveUrl: (String) -> Unit
) {
    val archives by app.archiveEngine.archivesFlow
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var readLaterOnly by remember { mutableStateOf(false) }
    var editingPage by remember { mutableStateOf<ArchivedPageEntity?>(null) }
    var deletingPage by remember { mutableStateOf<ArchivedPageEntity?>(null) }

    val filtered = remember(archives, query, favoritesOnly, readLaterOnly) {
        archives.filter { page ->
            val matchesQ = query.isBlank() ||
                page.title.contains(query, true) ||
                page.url.contains(query, true) ||
                page.tags.contains(query, true) ||
                page.folder.contains(query, true)
            val matchesFav = !favoritesOnly || page.isFavorite
            val matchesReadLater = !readLaterOnly || page.readLater
            matchesQ && matchesFav && matchesReadLater
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Offline Web Archives (${archives.size})", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Search archived pages, URLs, tags, folders") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = favoritesOnly,
                onClick = { favoritesOnly = !favoritesOnly },
                label = { Text("Favorites") }
            )
            FilterChip(
                selected = readLaterOnly,
                onClick = { readLaterOnly = !readLaterOnly },
                label = { Text("Read Later") }
            )
        }
        Spacer(Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (archives.isEmpty()) {
                        "No archived pages yet. Open any webpage in the browser and tap the Archive icon to capture an offline snapshot."
                    } else {
                        "No archived pages match your filter."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { page ->
                    val available = app.driveManager.isUriOrFileAccessible(page.directoryPathOrUri)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenArchiveReader(page.id) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    page.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    scope.launch {
                                        app.database.archiveDao().upsert(page.copy(isFavorite = !page.isFavorite))
                                    }
                                }) {
                                    Icon(
                                        if (page.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = "Favorite"
                                    )
                                }
                            }
                            Text(
                                page.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val statusColor = when {
                                    !available -> DeckRed
                                    page.status == ArchiveStatus.FULL_ARCHIVE -> DeckGreen
                                    else -> DeckAmber
                                }
                                Text(
                                    text = if (!available) "Drive Disconnected / Unavailable" else page.status.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = statusColor
                                )
                                Text(
                                    text = "${Formatters.formatBytes(page.sizeBytes)} • ${Formatters.formatDateOnly(page.capturedAt)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (page.statusNote.isNotBlank()) {
                                Text(
                                    page.statusNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                TextButton(onClick = { onOpenLiveUrl(page.url) }) {
                                    Text("Open Live")
                                }
                                IconButton(onClick = { editingPage = page }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit tags & title")
                                }
                                IconButton(onClick = { deletingPage = page }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete archive")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingPage?.let { page ->
        var title by remember { mutableStateOf(page.title) }
        var tags by remember { mutableStateOf(page.tags) }
        var folder by remember { mutableStateOf(page.folder) }
        AlertDialog(
            onDismissRequest = { editingPage = null },
            title = { Text("Edit Archive Metadata") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                    OutlinedTextField(value = folder, onValueChange = { folder = it }, label = { Text("Folder") })
                    OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("Tags") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        app.database.archiveDao().upsert(
                            page.copy(
                                title = title.ifBlank { page.title },
                                folder = folder.ifBlank { "General" },
                                tags = tags
                            )
                        )
                    }
                    editingPage = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingPage = null }) { Text("Cancel") }
            }
        )
    }

    deletingPage?.let { page ->
        AlertDialog(
            onDismissRequest = { deletingPage = null },
            title = { Text("Delete Archived Page?") },
            text = { Text("Delete offline snapshot '${page.title}' and its saved assets?") },
            confirmButton = {
                Button(onClick = {
                    scope.launch { app.archiveEngine.deleteArchive(page) }
                    deletingPage = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingPage = null }) { Text("Cancel") }
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ArchiveReaderScreen(
    app: CyberDeckApplication,
    archiveId: String,
    onBack: () -> Unit,
    onOpenArchiveId: (String) -> Unit,
    onOpenLiveUrl: (String) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    var page by remember { mutableStateOf<ArchivedPageEntity?>(null) }
    var html by remember { mutableStateOf<String?>(null) }
    var textReaderMode by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var showFind by remember { mutableStateOf(false) }

    DisposableEffect(view) {
        val prev = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = prev }
    }

    LaunchedEffect(archiveId) {
        val loaded = app.database.archiveDao().getById(archiveId)
        page = loaded
        if (loaded != null) {
            html = app.archiveEngine.loadArchivedHtml(loaded)
        }
    }

    val current = page
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        current?.title ?: "Loading archive...",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (current != null) {
                        Text(
                            "${current.status.label} • Captured ${Formatters.formatDateTime(current.capturedAt)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = { showFind = !showFind }) {
                    Icon(Icons.Filled.Search, contentDescription = "Find in archive")
                }
                IconButton(onClick = { textReaderMode = !textReaderMode }) {
                    Icon(Icons.Filled.TextFields, contentDescription = "Toggle Simplified Text Reader")
                }
                if (current != null) {
                    IconButton(onClick = { onOpenLiveUrl(current.url) }) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = "Open live URL")
                    }
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${current.title}\n${current.url}")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share archived URL"))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                }
            }
        }

        if (showFind) {
            OutlinedTextField(
                value = findQuery,
                onValueChange = { findQuery = it },
                singleLine = true,
                placeholder = { Text("Search text in snapshot...") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        if (current == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Archive record not found.")
            }
        } else if (html == null && current.readerText.isBlank()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Snapshot file is currently unavailable (${current.storageTarget.label}). Reconnect your Deck Drive if stored externally.",
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else if (textReaderMode || html == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(current.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Source: ${current.url}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val body = if (findQuery.isBlank()) current.readerText else {
                    current.readerText.lines()
                        .filter { it.contains(findQuery, ignoreCase = true) }
                        .joinToString("\n\n")
                        .ifBlank { "No matching lines for '$findQuery'." }
                }
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val reqUrl = request?.url?.toString() ?: return null
                                if (reqUrl.startsWith("https://cyberdeck.offline/assets/")) {
                                    val rel = reqUrl.removePrefix("https://cyberdeck.offline/")
                                    val asset = runBlocking {
                                        app.archiveEngine.loadArchiveAssetStream(current, rel)
                                    } ?: return null
                                    return WebResourceResponse(
                                        asset.first,
                                        "UTF-8",
                                        ByteArrayInputStream(asset.second)
                                    )
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val target = request?.url?.toString() ?: return true
                                val linked = runBlocking {
                                    app.database.archiveDao().findLatestByUrl(target)
                                }
                                if (linked != null) {
                                    onOpenArchiveId(linked.id)
                                } else {
                                    onOpenLiveUrl(target)
                                }
                                return true
                            }
                        }
                        loadDataWithBaseURL(
                            "https://cyberdeck.offline/",
                            html!!,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                },
                update = { wv ->
                    if (findQuery.isNotBlank()) wv.findAllAsync(findQuery) else wv.clearMatches()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
