package com.offlinecyberdeck.bookmarks

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinecyberdeck.CyberDeckApplication
import com.offlinecyberdeck.core.database.entity.BookmarkEntity
import com.offlinecyberdeck.core.database.entity.HistoryEntity
import com.offlinecyberdeck.core.format.Formatters
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun BookmarksScreen(
    app: CyberDeckApplication,
    onOpenUrl: (url: String, incognito: Boolean) -> Unit
) {
    val bookmarks by app.database.bookmarkDao().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<BookmarkEntity?>(null) }

    val filtered = remember(bookmarks, query) {
        if (query.isBlank()) bookmarks
        else bookmarks.filter {
            it.title.contains(query, true) ||
                it.url.contains(query, true) ||
                it.tags.contains(query, true) ||
                it.folder.contains(query, true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Bookmarks (${bookmarks.size})", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Add")
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Filter bookmarks by title, URL, folder, tag") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (bookmarks.isEmpty()) {
                        "No bookmarks yet. Tap the star icon in the browser toolbar or Add above."
                    } else {
                        "No bookmarks match '$query'."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { bm ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenUrl(bm.url, false) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Bookmark,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(bm.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    bm.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "Folder: ${bm.folder}" + if (bm.tags.isNotBlank()) " • ${bm.tags}" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            IconButton(onClick = { onOpenUrl(bm.url, true) }) {
                                Icon(Icons.Filled.VisibilityOff, contentDescription = "Open in Private Tab")
                            }
                            IconButton(onClick = { pendingDelete = bm }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete bookmark")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("https://") }
        var folder by remember { mutableStateOf("General") }
        var tags by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Bookmark") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL") })
                    OutlinedTextField(value = folder, onValueChange = { folder = it }, label = { Text("Folder") })
                    OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("Tags (comma separated)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (url.isNotBlank()) {
                        scope.launch {
                            app.database.bookmarkDao().upsert(
                                BookmarkEntity(
                                    id = UUID.randomUUID().toString(),
                                    title = title.ifBlank { url },
                                    url = url.trim(),
                                    folder = folder.ifBlank { "General" },
                                    tags = tags.trim()
                                )
                            )
                            showAddDialog = false
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    pendingDelete?.let { bm ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Bookmark?") },
            text = { Text("Remove '${bm.title}' from bookmarks?") },
            confirmButton = {
                Button(onClick = {
                    scope.launch { app.database.bookmarkDao().delete(bm) }
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun HistoryScreen(
    app: CyberDeckApplication,
    onOpenUrl: (String) -> Unit
) {
    val history by app.database.historyDao().observeRecent()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var confirmClearAll by remember { mutableStateOf(false) }

    val filtered = remember(history, query) {
        if (query.isBlank()) history
        else history.filter { it.title.contains(query, true) || it.url.contains(query, true) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("History (${history.size})", style = MaterialTheme.typography.titleLarge)
            TextButton(
                onClick = { confirmClearAll = true },
                enabled = history.isNotEmpty()
            ) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                Text("Clear All")
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Search browsing history") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (history.isEmpty()) {
                        "No browsing history recorded yet. Private tabs never appear here."
                    } else {
                        "No history entries match '$query'."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { item ->
                    HistoryRow(
                        item = item,
                        onOpen = { onOpenUrl(item.url) },
                        onDelete = {
                            scope.launch { app.database.historyDao().deleteById(item.id) }
                        }
                    )
                }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear All Browsing History?") },
            text = { Text("This permanently removes all recorded page visits from the local database.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch { app.database.historyDao().clearAll() }
                    confirmClearAll = false
                }) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HistoryRow(
    item: HistoryEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.History, contentDescription = null)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    Formatters.formatDateTime(item.visitedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
            }
        }
    }
}
