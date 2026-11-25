package com.offlinecyberdeck.downloads

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinecyberdeck.CyberDeckApplication
import com.offlinecyberdeck.core.database.entity.AppSettingsEntity
import com.offlinecyberdeck.core.database.entity.DownloadEntity
import com.offlinecyberdeck.core.format.Formatters
import com.offlinecyberdeck.core.model.DownloadStatus
import com.offlinecyberdeck.ui.theme.DeckAmber
import com.offlinecyberdeck.ui.theme.DeckGreen
import com.offlinecyberdeck.ui.theme.DeckRed
import kotlinx.coroutines.launch

@Composable
fun DownloadsScreen(
    app: CyberDeckApplication,
    onShowMessage: (String) -> Unit
) {
    val downloads by app.downloadRepository.downloadsFlow
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by app.settingsRepository.settingsFlow
        .collectAsStateWithLifecycle(initialValue = AppSettingsEntity())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showNewDownloadDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DownloadEntity?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            scope.launch {
                app.settingsRepository.updateSettings { it.copy(downloadFolderUri = uri.toString()) }
                onShowMessage("Download destination folder updated via SAF.")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Downloads (${downloads.size})", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { folderPicker.launch(null) }) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Folder")
                }
                Button(onClick = { showNewDownloadDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("URL")
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (!settings.downloadFolderUri.isNullOrBlank()) {
                "Destination: SAF folder selected (${settings.downloadFolderUri})"
            } else {
                "Destination: ${settings.downloadStorageTarget.label}"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))

        if (downloads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No downloads yet. Trigger a download in the browser or tap + URL to download a direct HTTP/HTTPS file.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(downloads, key = { it.id }) { dl ->
                    DownloadCard(
                        item = dl,
                        onPause = { app.downloadRepository.pauseDownload(dl.id) },
                        onResume = { app.downloadRepository.resumeOrRetryDownload(dl.id) },
                        onCancel = { app.downloadRepository.cancelDownload(dl.id) },
                        onOpen = {
                            val intent = app.downloadRepository.buildOpenIntent(dl)
                            if (intent == null) {
                                onShowMessage("File unavailable on storage.")
                            } else {
                                runCatching { context.startActivity(intent) }
                                    .onFailure { onShowMessage("No compatible app installed for ${dl.mimeType}") }
                            }
                        },
                        onShare = {
                            val intent = app.downloadRepository.buildShareIntent(dl)
                            if (intent == null) {
                                onShowMessage("File unavailable to share.")
                            } else {
                                context.startActivity(Intent.createChooser(intent, "Share ${dl.fileName}"))
                            }
                        },
                        onDelete = { pendingDelete = dl }
                    )
                }
            }
        }
    }

    if (showNewDownloadDialog) {
        var url by remember { mutableStateOf("https://") }
        var fileName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewDownloadDialog = false },
            title = { Text("Start Direct Download") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("HTTP/HTTPS URL") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("Filename (optional)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val trimmed = url.trim()
                    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        scope.launch {
                            app.downloadRepository.enqueueDownload(
                                url = trimmed,
                                suggestedFileName = fileName.trim().ifBlank { null }
                            )
                            showNewDownloadDialog = false
                        }
                    } else {
                        onShowMessage("Enter a valid http:// or https:// URL")
                    }
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { showNewDownloadDialog = false }) { Text("Cancel") }
            }
        )
    }

    pendingDelete?.let { dl ->
        var deleteLocalFile by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove Download Record?") },
            text = {
                Column {
                    Text("Remove '${dl.fileName}' from the download list?")
                    if (dl.destinationUri.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = deleteLocalFile,
                                onCheckedChange = { deleteLocalFile = it }
                            )
                            Text("Also delete downloaded file from storage")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    app.downloadRepository.removeRecord(dl.id, deleteLocalFile)
                    pendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DownloadCard(
    item: DownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (item.status) {
        DownloadStatus.COMPLETED -> DeckGreen
        DownloadStatus.FAILED, DownloadStatus.CANCELLED -> DeckRed
        DownloadStatus.PAUSED -> DeckAmber
        else -> MaterialTheme.colorScheme.primary
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    item.status.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor
                )
            }

            Text(
                item.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PAUSED) {
                if (item.totalBytes > 0L) {
                    val progress = (item.bytesDownloaded.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            val sizeLine = buildString {
                append(Formatters.formatBytes(item.bytesDownloaded))
                if (item.totalBytes > 0L) {
                    val pct = ((item.bytesDownloaded * 100L) / item.totalBytes).coerceIn(0L, 100L)
                    append(" / ${Formatters.formatBytes(item.totalBytes)} ($pct%)")
                } else {
                    append(" / Size unknown")
                }
                append(" • ${Formatters.formatDateTime(item.createdAt)}")
            }
            Text(
                sizeLine,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!item.errorMessage.isNullOrBlank()) {
                Text(
                    "Error: ${item.errorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DeckRed
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.status == DownloadStatus.DOWNLOADING) {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause")
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Cancel, contentDescription = "Cancel")
                    }
                }
                if (item.status == DownloadStatus.PAUSED ||
                    item.status == DownloadStatus.FAILED ||
                    item.status == DownloadStatus.CANCELLED
                ) {
                    IconButton(onClick = onResume) {
                        Icon(
                            if (item.status == DownloadStatus.PAUSED) Icons.Filled.PlayArrow else Icons.Filled.Refresh,
                            contentDescription = "Resume or Retry"
                        )
                    }
                }
                if (item.status == DownloadStatus.COMPLETED) {
                    IconButton(onClick = onOpen) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Open File")
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, contentDescription = "Share File")
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove")
                }
            }
        }
    }
}
