package com.offlinecyberdeck.media

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.offlinecyberdeck.CyberDeckApplication
import com.offlinecyberdeck.core.database.entity.AppSettingsEntity
import com.offlinecyberdeck.core.database.entity.MediaItemEntity
import com.offlinecyberdeck.core.database.entity.PlaylistEntity
import com.offlinecyberdeck.core.format.Formatters
import com.offlinecyberdeck.core.model.MediaAvailability
import com.offlinecyberdeck.ui.theme.DeckAmber
import com.offlinecyberdeck.ui.theme.DeckGreen
import com.offlinecyberdeck.ui.theme.DeckRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "ogg", "opus", "wav", "flac", "aac")

private fun isAudioMedia(item: MediaItemEntity): Boolean {
    val name = (item.localFileName ?: item.localFileUri ?: "").lowercase(Locale.US)
    val ext = name.substringAfterLast('.', "")
    return ext in AUDIO_EXTENSIONS
}

private enum class MediaFilterTab(val label: String) {
    ALL("All"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    AVAILABLE("Offline Ready"),
    WATCH_LATER("Watch Later"),
    CONTINUE("Continue Watching"),
    CHANNELS("Channels"),
    PLAYLISTS("Playlists")
}

@Composable
fun MediaVaultScreen(
    app: CyberDeckApplication,
    onPlayMedia: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val items by app.mediaRepository.mediaFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val playlists by app.mediaRepository.playlistsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by app.settingsRepository.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettingsEntity())
    val scope = rememberCoroutineScope()
    val gridColumns = settings.offlineTubeGridColumns.coerceIn(1, 4)

    val focusManager = LocalFocusManager.current
    var selectedTab by remember { mutableStateOf(MediaFilterTab.ALL) }
    var selectedChannel by remember { mutableStateOf<String?>(null) }
    var searchExpanded by remember { mutableStateOf(false) }
    var topMenuOpen by remember { mutableStateOf(false) }
    var showTubeSettingsDialog by remember { mutableStateOf(false) }
    var isRefreshingVideos by remember { mutableStateOf(false) }
    var autoImportedVideosOnce by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var editingItem by remember { mutableStateOf<MediaItemEntity?>(null) }
    var linkingTargetId by remember { mutableStateOf<String?>(null) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var addToPlaylistItem by remember { mutableStateOf<MediaItemEntity?>(null) }
    var activePlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }

    val driveStatus by app.driveManager.status.collectAsStateWithLifecycle()
    var accessibleIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(items, driveStatus) {
        accessibleIds = withContext(Dispatchers.IO) {
            items.filter { app.mediaRepository.computeAvailability(it) == MediaAvailability.AVAILABLE_OFFLINE }
                .mapTo(HashSet()) { it.id }
        }
    }
    val visibleItems = remember(items, accessibleIds) {
        items.filter { it.id in accessibleIds }
    }

    fun runRefreshImportVideos(manual: Boolean) {
        if (isRefreshingVideos) return
        scope.launch {
            isRefreshingVideos = true
            val extraSaf = settings.extraMediaSafUris.split('\n').map { it.trim() }.filter { it.isNotBlank() }
            app.mediaRepository.autoImportAndRefreshVideos(settings.deckDriveTreeUri, extraSaf)
                .onSuccess { s ->
                    if (manual) {
                        val removedPart = if (s.removedCount > 0) ", ${s.removedCount} file nonaktif dibersihkan" else ""
                        onShowMessage("Refresh OfflineTube: ${s.importedCount} video baru diimpor$removedPart (${s.skippedCount} lama dilewati).")
                    } else if (s.importedCount > 0 || s.removedCount > 0) {
                        val parts = buildList {
                            if (s.importedCount > 0) add("${s.importedCount} video baru")
                            if (s.removedCount > 0) add("${s.removedCount} file terputus dibersihkan")
                        }
                        onShowMessage("Auto-Import OfflineTube: ${parts.joinToString(", ")}.")
                    }
                }
                .onFailure {
                    if (manual) onShowMessage("Refresh video gagal: ${it.message}")
                }
            isRefreshingVideos = false
        }
    }

    LaunchedEffect(settings.offlineTubeAutoImport, settings.deckDriveTreeUri) {
        if (settings.offlineTubeAutoImport && !autoImportedVideosOnce) {
            autoImportedVideosOnce = true
            runRefreshImportVideos(manual = false)
        }
    }

    val multiFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                app.mediaRepository.importLocalMediaFiles(uris)
                    .onSuccess { count -> onShowMessage("Imported/matched $count media file(s)") }
                    .onFailure { onShowMessage("Import error: ${it.message}") }
            }
        }
    }

    val folderScanner = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val existingSaf = settings.extraMediaSafUris.split('\n').filter { it.isNotBlank() }.toMutableSet()
                existingSaf.add(uri.toString())
                app.settingsRepository.updateSettings {
                    it.copy(extraMediaSafUris = existingSaf.joinToString("\n"))
                }
                app.mediaRepository.scanSafFolderForMedia(uri)
                    .onSuccess { count -> onShowMessage("Scanned folder: $count media item(s) added/matched") }
                    .onFailure { onShowMessage("Scan failed: ${it.message}") }
            }
        }
    }

    val singleLinkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val id = linkingTargetId
        linkingTargetId = null
        if (uri != null && id != null) {
            scope.launch {
                app.mediaRepository.linkLocalFileToMetadata(id, uri)
                    .onSuccess { onShowMessage("Local file linked to metadata entry") }
                    .onFailure { onShowMessage("Link failed: ${it.message}") }
            }
        }
    }

    val catalogExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                app.mediaRepository.exportMetadataCatalog(uri)
                    .onSuccess { count -> onShowMessage("Exported $count media catalog records") }
                    .onFailure { onShowMessage("Export failed: ${it.message}") }
            }
        }
    }

    val catalogImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                app.mediaRepository.importMetadataCatalog(uri)
                    .onSuccess { count -> onShowMessage("Imported $count media metadata records") }
                    .onFailure { onShowMessage("Import failed: ${it.message}") }
            }
        }
    }

    val filteredItems = remember(visibleItems, selectedTab, selectedChannel, query) {
        visibleItems.filter { item ->
            val qOk = query.isBlank() ||
                item.title.contains(query, true) ||
                item.channel.contains(query, true) ||
                item.tags.contains(query, true) ||
                item.localFileName.orEmpty().contains(query, true)
            val tabOk = when (selectedTab) {
                MediaFilterTab.ALL -> true
                MediaFilterTab.VIDEOS -> !isAudioMedia(item)
                MediaFilterTab.AUDIO -> isAudioMedia(item)
                MediaFilterTab.AVAILABLE -> true
                MediaFilterTab.WATCH_LATER -> item.watchLater
                MediaFilterTab.CONTINUE -> item.watchedPositionMs > 5000L
                MediaFilterTab.CHANNELS -> selectedChannel == null ||
                    item.channel.ifBlank { "Local Library" } == selectedChannel
                MediaFilterTab.PLAYLISTS -> true
            }
            qOk && tabOk
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
        // YouTube-style top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = DeckRed,
                    shape = RoundedCornerShape(7.dp)
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp).size(18.dp)
                    )
                }
                Text("OfflineTube", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${visibleItems.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    searchExpanded = !searchExpanded
                    if (!searchExpanded) {
                        query = ""
                        focusManager.clearFocus()
                    }
                }) {
                    Icon(
                        if (searchExpanded) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = "Search Media"
                    )
                }
                IconButton(
                    onClick = { runRefreshImportVideos(manual = true) },
                    enabled = !isRefreshingVideos
                ) {
                    if (isRefreshingVideos) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh Import Video")
                    }
                }
                IconButton(onClick = { folderScanner.launch(null) }) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = "Scan Folder")
                }
                IconButton(onClick = { showTubeSettingsDialog = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Pengaturan OfflineTube")
                }
                Button(
                    onClick = { multiFilePicker.launch(arrayOf("video/*", "audio/*")) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
                Box {
                    IconButton(onClick = { topMenuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Media options")
                    }
                    DropdownMenu(expanded = topMenuOpen, onDismissRequest = { topMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Scan USB-C / Local Folder") },
                            onClick = {
                                topMenuOpen = false
                                folderScanner.launch(null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export Catalog JSON") },
                            onClick = {
                                topMenuOpen = false
                                catalogExporter.launch("cyberdeck_media_catalog.json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import Catalog JSON") },
                            onClick = {
                                topMenuOpen = false
                                catalogImporter.launch(arrayOf("application/json"))
                            }
                        )
                    }
                }
            }
        }

        if (searchExpanded) {
            Spacer(Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "Search OfflineTube videos, channels, tags...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp).clickable { query = "" }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MediaFilterTab.entries.forEach { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = {
                        selectedTab = tab
                        if (tab != MediaFilterTab.PLAYLISTS) activePlaylist = null
                        if (tab != MediaFilterTab.CHANNELS) selectedChannel = null
                    },
                    label = { Text(tab.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                        selectedLabelColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        when {
            selectedTab == MediaFilterTab.PLAYLISTS -> {
                PlaylistsPane(
                    app = app,
                    playlists = playlists,
                    activePlaylist = activePlaylist,
                    onSelectPlaylist = { activePlaylist = it },
                    onCreatePlaylist = { showNewPlaylistDialog = true },
                    onPlayMedia = onPlayMedia
                )
            }
            selectedTab == MediaFilterTab.CHANNELS && selectedChannel == null -> {
                val grouped = remember(visibleItems) {
                    visibleItems.groupBy { it.channel.ifBlank { "Local Library" } }.toSortedMap()
                }
                if (grouped.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No channels yet. Import or scan local videos first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(grouped.keys.toList(), key = { it }) { channelName ->
                            val count = grouped[channelName]?.size ?: 0
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedChannel = channelName }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ChannelAvatar(channelName)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(channelName, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "$count video(s) in catalog",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            filteredItems.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (visibleItems.isEmpty()) {
                            "No local media yet. Tap Scan Folder to index your USB-C Deck Drive or tablet Movies folder, or Add to pick files. Video details, thumbnails, duration, and resolution are generated automatically from your local files."
                        } else {
                            "No media items match this filter."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (selectedTab == MediaFilterTab.CHANNELS && selectedChannel != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            IconButton(onClick = { selectedChannel = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "All channels")
                            }
                            Text(selectedChannel!!, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            YouTubeFeedVideoCard(
                                item = item,
                                onPlay = { onPlayMedia(item.id) },
                                onEdit = { editingItem = item },
                                onLinkFile = {
                                    linkingTargetId = item.id
                                    singleLinkPicker.launch(arrayOf("video/*", "audio/*"))
                                },
                                onUnlink = { scope.launch { app.mediaRepository.unlinkLocalFile(item) } },
                                onConfirmMatch = { scope.launch { app.mediaRepository.confirmMatch(item) } },
                                onToggleWatchLater = {
                                    scope.launch { app.mediaRepository.updateMediaItem(item.copy(watchLater = !item.watchLater)) }
                                },
                                onToggleFavorite = {
                                    scope.launch { app.mediaRepository.updateMediaItem(item.copy(isFavorite = !item.isFavorite)) }
                                },
                                onAddToPlaylist = { addToPlaylistItem = item },
                                onDelete = { scope.launch { app.mediaRepository.deleteMediaItem(item) } }
                            )
                        }
                    }
                }
            }
        }
    }

    editingItem?.let { item ->
        var title by remember { mutableStateOf(item.title) }
        var channel by remember { mutableStateOf(item.channel) }
        var tags by remember { mutableStateOf(item.tags) }
        var desc by remember { mutableStateOf(item.description) }
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("Edit Video Info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                    OutlinedTextField(value = channel, onValueChange = { channel = it }, label = { Text("Channel / Creator") })
                    OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("Tags") })
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, maxLines = 4)
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        app.mediaRepository.updateMediaItem(
                            item.copy(
                                title = title.ifBlank { item.title },
                                channel = channel,
                                tags = tags,
                                description = desc
                            )
                        )
                    }
                    editingItem = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) { Text("Cancel") }
            }
        )
    }

    if (showNewPlaylistDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewPlaylistDialog = false },
            title = { Text("Create Playlist") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Playlist Name") })
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch { app.mediaRepository.createPlaylist(name) }
                    showNewPlaylistDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlaylistDialog = false }) { Text("Cancel") }
            }
        )
    }

    addToPlaylistItem?.let { item ->
        AlertDialog(
            onDismissRequest = { addToPlaylistItem = null },
            title = { Text("Save to Playlist") },
            text = {
                if (playlists.isEmpty()) {
                    Text("No playlists yet. Create a playlist in the Playlists tab first.")
                } else {
                    Column {
                        playlists.forEach { pl ->
                            TextButton(onClick = {
                                scope.launch { app.mediaRepository.addMediaToPlaylist(pl.id, item.id) }
                                addToPlaylistItem = null
                                onShowMessage("Saved to '${pl.name}'")
                            }) {
                                Text(pl.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { addToPlaylistItem = null }) { Text("Close") }
            }
        )
    }

    if (showTubeSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showTubeSettingsDialog = false },
            title = { Text("Pengaturan OfflineTube") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Import Video Baru", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Otomatis scan video baru di Internal, SD Card & USB tanpa duplikasi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.offlineTubeAutoImport,
                            onCheckedChange = { enabled ->
                                scope.launch { app.settingsRepository.updateSettings { it.copy(offlineTubeAutoImport = enabled) } }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Putar Video Berikutnya Otomatis", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Lanjutkan otomatis ke video berikutnya saat selesai.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.offlineTubeAutoPlayNext,
                            onCheckedChange = { enabled ->
                                scope.launch { app.settingsRepository.updateSettings { it.copy(offlineTubeAutoPlayNext = enabled) } }
                            }
                        )
                    }

                    HorizontalDivider()
                    Text("Jumlah Kolom Video Grid", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3, 4).forEach { cols ->
                            FilterChip(
                                selected = settings.offlineTubeGridColumns == cols,
                                onClick = {
                                    scope.launch { app.settingsRepository.updateSettings { it.copy(offlineTubeGridColumns = cols) } }
                                },
                                label = { Text("${cols} Kolom") }
                            )
                        }
                    }

                    Text("Kecepatan Pemutaran Default", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { sp ->
                            FilterChip(
                                selected = settings.offlineTubeDefaultSpeed == sp,
                                onClick = {
                                    scope.launch { app.settingsRepository.updateSettings { it.copy(offlineTubeDefaultSpeed = sp) } }
                                },
                                label = { Text("${sp}x") }
                            )
                        }
                    }

                    HorizontalDivider()
                    Button(
                        onClick = {
                            showTubeSettingsDialog = false
                            runRefreshImportVideos(manual = true)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh Import Video Sekarang")
                    }
                    OutlinedButton(
                        onClick = {
                            showTubeSettingsDialog = false
                            folderScanner.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tambah Folder Video SD / USB")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTubeSettingsDialog = false }) { Text("Tutup") }
            }
        )
    }
}

@Composable
private fun YouTubeFeedVideoCard(
    item: MediaItemEntity,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onLinkFile: () -> Unit,
    onUnlink: () -> Unit,
    onConfirmMatch: () -> Unit,
    onToggleWatchLater: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit
) {
    val thumbBitmap = remember(item.thumbnailPath) {
        item.thumbnailPath?.let { path ->
            runCatching { android.graphics.BitmapFactory.decodeFile(path) }.getOrNull()
        }
    }
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onPlay)
    ) {
        // 16:9 Thumbnail Container (YouTube-style)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val isAudio = isAudioMedia(item)
            if (thumbBitmap != null) {
                Image(
                    bitmap = thumbBitmap.asImageBitmap(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isAudio) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "OFFLINE AUDIO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Icon(
                    Icons.Filled.VideoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Duration pill bottom-right (classic YouTube badge)
            if (item.durationMs > 0L) {
                Surface(
                    color = Color.Black.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                ) {
                    Text(
                        text = Formatters.formatDurationMs(item.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Watched progress bar along bottom edge of thumbnail
            if (item.durationMs > 0L && item.watchedPositionMs > 0L) {
                val fraction = (item.watchedPositionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    color = DeckRed,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                )
            }
        }

        // Metadata row under thumbnail (Avatar + Title + Channel/Stats + 3-dot Menu)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            ChannelAvatar(item.channel.ifBlank { "Local" })
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val channelLabel = item.channel.ifBlank { "Local Library" }
                val metaParts = buildList {
                    add(channelLabel)
                    if (item.playCount > 0) add("${item.playCount} views")
                    if (item.sizeBytes > 0L) add(Formatters.formatBytes(item.sizeBytes))
                    if (!item.matchConfirmed && item.localFileUri != null) {
                        add("Match ${(item.matchConfidence * 100).toInt()}%")
                    }
                }
                Text(
                    text = metaParts.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Video options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (!item.matchConfirmed && item.localFileUri != null) {
                        DropdownMenuItem(
                            text = { Text("Confirm File Match") },
                            onClick = { menuOpen = false; onConfirmMatch() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (item.watchLater) "Remove from Watch Later" else "Save to Watch Later") },
                        onClick = { menuOpen = false; onToggleWatchLater() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (item.isFavorite) "Remove Favorite" else "Add to Favorites") },
                        onClick = { menuOpen = false; onToggleFavorite() }
                    )
                    DropdownMenuItem(
                        text = { Text("Save to Playlist") },
                        onClick = { menuOpen = false; onAddToPlaylist() }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit Video Info") },
                        onClick = { menuOpen = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Link Local File") },
                        onClick = { menuOpen = false; onLinkFile() }
                    )
                    if (item.localFileUri != null) {
                        DropdownMenuItem(
                            text = { Text("Unlink Local File") },
                            onClick = { menuOpen = false; onUnlink() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete from Catalog") },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelAvatar(name: String, sizeDp: Int = 36) {
    val letter = name.trim().firstOrNull()?.uppercaseChar() ?: 'L'
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun PlaylistsPane(
    app: CyberDeckApplication,
    playlists: List<PlaylistEntity>,
    activePlaylist: PlaylistEntity?,
    onSelectPlaylist: (PlaylistEntity?) -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlayMedia: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    if (activePlaylist == null) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onCreatePlaylist) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("New Playlist")
            }
            if (playlists.isEmpty()) {
                Text("No playlists created yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(playlists, key = { it.id }) { pl ->
                        Card(modifier = Modifier.fillMaxWidth().clickable { onSelectPlaylist(pl) }) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.PlaylistPlay, contentDescription = null)
                                Spacer(Modifier.width(10.dp))
                                Text(pl.name, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    scope.launch { app.mediaRepository.deletePlaylist(pl.id) }
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete Playlist")
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        val playlistItems by app.mediaRepository.observePlaylistItems(activePlaylist.id)
            .collectAsStateWithLifecycle(initialValue = emptyList())
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSelectPlaylist(null) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to playlists")
                }
                Text(activePlaylist.name, style = MaterialTheme.typography.titleMedium)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(playlistItems, key = { it.id }) { item ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onPlayMedia(item.id) }) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(item.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = {
                                scope.launch {
                                    app.mediaRepository.removeMediaFromPlaylist(activePlaylist.id, item.id)
                                }
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove from playlist")
                            }
                        }
                    }
                }
            }
        }
    }
}

@AndroidxOptIn(UnstableApi::class)
@Composable
fun MediaPlayerScreen(
    app: CyberDeckApplication,
    mediaId: String,
    onBack: () -> Unit,
    onSelectOtherMedia: (String) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val allMedia by app.mediaRepository.mediaFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val playlists by app.mediaRepository.playlistsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val tubeSettings by app.settingsRepository.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettingsEntity())
    var item by remember { mutableStateOf<MediaItemEntity?>(null) }
    var descriptionExpanded by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var playbackSpeed by remember(tubeSettings.offlineTubeDefaultSpeed) {
        mutableFloatStateOf(tubeSettings.offlineTubeDefaultSpeed)
    }
    var showSpeedMenu by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    val currentItem = item
    val isAudio = currentItem?.let { isAudioMedia(it) } == true
    val related = remember(allMedia, mediaId) {
        allMedia.filter {
            it.id != mediaId && app.mediaRepository.computeAvailability(it) == MediaAvailability.AVAILABLE_OFFLINE
        }.take(15)
    }

    DisposableEffect(view) {
        val prev = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = prev }
    }

    DisposableEffect(exoPlayer, tubeSettings.offlineTubeAutoPlayNext, related) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && tubeSettings.offlineTubeAutoPlayNext) {
                    related.firstOrNull()?.let { next -> onSelectOtherMedia(next.id) }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    LaunchedEffect(mediaId) {
        val loaded = app.database.mediaDao().getById(mediaId)
        item = loaded
        val uriStr = loaded?.localFileUri
        val canOpen = !uriStr.isNullOrBlank() && withContext(Dispatchers.IO) {
            app.driveManager.isUriOrFileAccessible(uriStr)
        }
        if (!uriStr.isNullOrBlank() && canOpen) {
            val playUri = if (uriStr.startsWith("content://") || uriStr.startsWith("file://")) {
                Uri.parse(uriStr)
            } else {
                Uri.fromFile(File(uriStr))
            }
            exoPlayer.setMediaItem(MediaItem.fromUri(playUri))
            exoPlayer.prepare()
            if (loaded.watchedPositionMs > 0L) {
                exoPlayer.seekTo(loaded.watchedPositionMs)
            }
            exoPlayer.setPlaybackSpeed(playbackSpeed)
            exoPlayer.playWhenReady = true
            app.mediaRepository.updatePlaybackProgress(loaded.id, loaded.watchedPositionMs, incrementPlayCount = true)
            item = app.database.mediaDao().getById(mediaId)
        }
    }

    LaunchedEffect(mediaId) {
        while (true) {
            delay(4000L)
            val current = item
            if (current != null && exoPlayer.isPlaying) {
                app.mediaRepository.updatePlaybackProgress(
                    id = current.id,
                    positionMs = exoPlayer.currentPosition,
                    incrementPlayCount = false
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val current = item
            if (current != null) {
                val pos = exoPlayer.currentPosition
                scope.launch {
                    app.mediaRepository.updatePlaybackProgress(current.id, pos, incrementPlayCount = false)
                }
            }
            exoPlayer.release()
        }
    }

    if (isFullscreen && currentItem != null && !currentItem.localFileUri.isNullOrBlank() && !isAudio) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                update = { it.player = exoPlayer },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = { isFullscreen = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Filled.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White)
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    currentItem?.title ?: "Offline Player",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (currentItem == null || currentItem.localFileUri.isNullOrBlank()) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text("No local file linked for playback.", color = Color.White)
            }
        } else if (isAudio) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(Color(0xFF14181F))
            ) {
                Column(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp, start = 20.dp, end = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        currentItem.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        currentItem.channel.ifBlank { "Offline Audio" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                            controllerShowTimeoutMs = 0
                        }
                    },
                    update = { it.player = exoPlayer },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxWidth().height(92.dp).align(Alignment.BottomCenter)
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    update = { it.player = exoPlayer },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { isFullscreen = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .size(36.dp)
                ) {
                    Icon(Icons.Filled.Fullscreen, contentDescription = "Fullscreen", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            currentItem?.let { m ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            m.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            buildString {
                                append("${m.playCount} views")
                                if (m.durationMs > 0L) append(" • ${Formatters.formatDurationMs(m.durationMs)}")
                                if (m.sizeBytes > 0L) append(" • ${Formatters.formatBytes(m.sizeBytes)}")
                                append(" • ${m.storageTarget.label}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Channel row (YouTube-style)
                        val channelName = m.channel.ifBlank { "Local Library" }
                        val channelVideoCount = allMedia.count { it.channel.ifBlank { "Local Library" } == channelName }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ChannelAvatar(channelName, sizeDp = 40)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(channelName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "$channelVideoCount offline file(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    "Offline Ready",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // YouTube-style Action Bar
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    val updated = m.copy(isFavorite = !m.isFavorite)
                                    app.mediaRepository.updateMediaItem(updated)
                                    item = updated
                                }
                            }) {
                                Icon(
                                    Icons.Filled.ThumbUp,
                                    contentDescription = null,
                                    tint = if (m.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (m.isFavorite) "Liked" else "Like")
                            }
                            Box {
                                OutlinedButton(onClick = { showSpeedMenu = true }) {
                                    Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("${playbackSpeed}x")
                                }
                                DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                                    listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { sp ->
                                        DropdownMenuItem(
                                            text = { Text("${sp}x") },
                                            onClick = {
                                                playbackSpeed = sp
                                                exoPlayer.setPlaybackSpeed(sp)
                                                showSpeedMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    val updated = m.copy(watchLater = !m.watchLater)
                                    app.mediaRepository.updateMediaItem(updated)
                                    item = updated
                                }
                            }) {
                                Icon(Icons.Filled.WatchLater, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (m.watchLater) "In Watch Later" else "Watch Later")
                            }
                            OutlinedButton(onClick = { showPlaylistDialog = true }) {
                                Icon(Icons.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save")
                            }
                            OutlinedButton(onClick = { showEditDialog = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit Info")
                            }
                            OutlinedButton(onClick = {
                                val localUri = m.localFileUri ?: return@OutlinedButton
                                val shareUri = if (localUri.startsWith("content://")) {
                                    Uri.parse(localUri)
                                } else {
                                    val path = if (localUri.startsWith("file://")) Uri.parse(localUri).path.orEmpty() else localUri
                                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
                                }
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (isAudio) "audio/*" else "video/*"
                                    putExtra(Intent.EXTRA_STREAM, shareUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share ${m.title}"))
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share")
                            }
                        }

                        // Expandable Description Card (auto-generated from local video + custom notes)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { descriptionExpanded = !descriptionExpanded }
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = m.description.ifBlank {
                                        "Local file: ${m.localFileName ?: m.title}\nAdded: ${Formatters.formatDateTime(m.addedAt)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = if (descriptionExpanded) 20 else 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (m.tags.isNotBlank()) {
                                    Text(
                                        "Tags: ${m.tags}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    if (descriptionExpanded) "Show less" else "Show more",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (related.isNotEmpty()) {
                item {
                    Text(
                        "Up Next (Offline)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(related, key = { it.id }) { rel ->
                    val relThumb = remember(rel.thumbnailPath) {
                        rel.thumbnailPath?.let { path ->
                            runCatching { android.graphics.BitmapFactory.decodeFile(path) }.getOrNull()
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectOtherMedia(rel.id) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(140.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (relThumb != null) {
                                Image(
                                    bitmap = relThumb.asImageBitmap(),
                                    contentDescription = rel.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            }
                            if (rel.durationMs > 0L) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                ) {
                                    Text(
                                        Formatters.formatDurationMs(rel.durationMs),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                rel.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                rel.channel.ifBlank { "Local Library" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${rel.playCount} plays • ${Formatters.formatBytes(rel.sizeBytes)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPlaylistDialog && currentItem != null) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("Save to Playlist") },
            text = {
                if (playlists.isEmpty()) {
                    Text("No playlists yet. Create one in Offline Tube → Playlists.")
                } else {
                    Column {
                        playlists.forEach { pl ->
                            TextButton(onClick = {
                                scope.launch { app.mediaRepository.addMediaToPlaylist(pl.id, currentItem.id) }
                                showPlaylistDialog = false
                            }) {
                                Text(pl.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistDialog = false }) { Text("Close") }
            }
        )
    }

    if (showEditDialog && currentItem != null) {
        var title by remember { mutableStateOf(currentItem.title) }
        var channel by remember { mutableStateOf(currentItem.channel) }
        var tags by remember { mutableStateOf(currentItem.tags) }
        var desc by remember { mutableStateOf(currentItem.description) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Video Info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                    OutlinedTextField(value = channel, onValueChange = { channel = it }, label = { Text("Channel") })
                    OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("Tags") })
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, maxLines = 4)
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val updated = currentItem.copy(
                            title = title.ifBlank { currentItem.title },
                            channel = channel,
                            tags = tags,
                            description = desc
                        )
                        app.mediaRepository.updateMediaItem(updated)
                        item = updated
                    }
                    showEditDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }
}
