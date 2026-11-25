package com.offlinecyberdeck.music

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.offlinecyberdeck.CyberDeckApplication
import com.offlinecyberdeck.core.database.entity.AppSettingsEntity
import com.offlinecyberdeck.core.database.entity.MusicTrackEntity
import com.offlinecyberdeck.core.format.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val YtmRed = Color(0xFFFF0033)
private val YtmBg = Color(0xFF030303)
private val YtmSurface = Color(0xFF141418)
private val YtmCard = Color(0xFF1D1D24)
private val YtmInk = Color(0xFFF5F5F7)
private val YtmMuted = Color(0xFFA3A3B2)

private enum class MusicTab(val label: String) {
    QUICK_PICKS("Pilihan Cepat"),
    SONGS("Semua Lagu"),
    ARTISTS("Artis"),
    ALBUMS("Album / Folder"),
    FAVORITES("Favorit"),
    QUEUE("Antrian")
}

private enum class MusicRepeatMode { OFF, ALL, ONE }

@Composable
fun MusicPlayerScreen(
    app: CyberDeckApplication,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tracks by app.musicRepository.tracksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by app.settingsRepository.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettingsEntity())

    var selectedTab by remember { mutableStateOf(MusicTab.QUICK_PICKS) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedGroupFilter by remember { mutableStateOf<String?>(null) }

    var isRefreshing by remember { mutableStateOf(false) }
    var autoImportedOnce by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var fullPlayerExpanded by remember { mutableStateOf(false) }

    var queue by remember { mutableStateOf<List<MusicTrackEntity>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(MusicRepeatMode.ALL) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    val currentTrack = queue.getOrNull(currentIndex)
        ?.let { cur -> tracks.firstOrNull { it.id == cur.id } ?: cur }

    fun playFromList(list: List<MusicTrackEntity>, startIdx: Int) {
        if (list.isEmpty() || startIdx !in list.indices) return
        val target = list[startIdx]
        if (!app.musicRepository.isTrackAccessible(target)) {
            onShowMessage("File audio '${target.fileName}' tidak dapat diakses. Hubungkan SD/USB atau refresh import.")
            return
        }
        queue = list
        currentIndex = startIdx
        exoPlayer.setMediaItem(MediaItem.fromUri(target.fileUri))
        exoPlayer.prepare()
        exoPlayer.setPlaybackSpeed(playbackSpeed)
        exoPlayer.playWhenReady = true
        scope.launch { app.musicRepository.recordPlay(target) }
    }

    fun playNextTrack(autoTriggered: Boolean) {
        if (queue.isEmpty()) return
        if (autoTriggered && repeatMode == MusicRepeatMode.ONE && currentIndex in queue.indices) {
            exoPlayer.seekTo(0L)
            exoPlayer.playWhenReady = true
            return
        }
        val nextIdx = if (shuffleEnabled && queue.size > 1) {
            val candidates = queue.indices.filter { it != currentIndex }
            candidates.random()
        } else {
            val candidate = currentIndex + 1
            if (candidate < queue.size) candidate
            else if (repeatMode == MusicRepeatMode.ALL) 0
            else -1
        }
        if (nextIdx in queue.indices) {
            playFromList(queue, nextIdx)
        } else {
            exoPlayer.pause()
        }
    }

    fun playPrevTrack() {
        if (queue.isEmpty()) return
        if (exoPlayer.currentPosition > 3500L) {
            exoPlayer.seekTo(0L)
            return
        }
        val prevIdx = if (currentIndex - 1 >= 0) currentIndex - 1 else queue.lastIndex
        playFromList(queue, prevIdx)
    }

    fun runRefreshImport(manual: Boolean) {
        if (isRefreshing) return
        scope.launch {
            isRefreshing = true
            val extraSaf = settings.extraMediaSafUris.split('\n').map { it.trim() }.filter { it.isNotBlank() }
            app.musicRepository.autoImportAndRefresh(settings.deckDriveTreeUri, extraSaf)
                .onSuccess { summary ->
                    if (manual) {
                        onShowMessage(
                            "Import selesai: ${summary.importedCount} MP3/audio baru ditambahkan, ${summary.skippedCount} file lama dilewati."
                        )
                    } else if (summary.importedCount > 0) {
                        onShowMessage("Auto-Import Musik: ${summary.importedCount} lagu baru ditemukan.")
                    }
                }
                .onFailure {
                    if (manual) onShowMessage("Refresh musik gagal: ${it.message}")
                }
            isRefreshing = false
        }
    }

    LaunchedEffect(settings.musicAutoImport, settings.deckDriveTreeUri) {
        if (settings.musicAutoImport && !autoImportedOnce) {
            autoImportedOnce = true
            runRefreshImport(manual = false)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                } else if (playbackState == Player.STATE_ENDED) {
                    playNextTrack(autoTriggered = true)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying, currentTrack?.id) {
        while (true) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val d = exoPlayer.duration
            if (d > 0L) durationMs = d
            delay(400L)
        }
    }

    BackHandler(enabled = fullPlayerExpanded) {
        fullPlayerExpanded = false
    }

    val audioFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                app.musicRepository.importPickedAudioFiles(uris)
                    .onSuccess { s ->
                        onShowMessage("${s.importedCount} lagu baru diimpor (${s.skippedCount} duplikat dilewati).")
                    }
                    .onFailure { onShowMessage("Gagal impor file: ${it.message}") }
            }
        }
    }

    val safFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val existingSaf = settings.extraMediaSafUris.split('\n').filter { it.isNotBlank() }.toMutableSet()
                existingSaf.add(uri.toString())
                app.settingsRepository.updateSettings {
                    it.copy(extraMediaSafUris = existingSaf.joinToString("\n"))
                }
                app.musicRepository.scanSafFolderForMusic(uri)
                    .onSuccess { s ->
                        onShowMessage("Folder dipindai: ${s.importedCount} lagu baru ditambahkan (${s.skippedCount} lama dilewati).")
                    }
                    .onFailure { onShowMessage("Gagal memindai folder: ${it.message}") }
            }
        }
    }

    val filteredTracks = remember(tracks, searchQuery, selectedTab, selectedGroupFilter) {
        tracks.filter { t ->
            val qOk = searchQuery.isBlank() ||
                t.title.contains(searchQuery, true) ||
                t.artist.contains(searchQuery, true) ||
                t.album.contains(searchQuery, true) ||
                t.fileName.contains(searchQuery, true)
            val tabOk = when (selectedTab) {
                MusicTab.FAVORITES -> t.isFavorite
                MusicTab.ARTISTS -> selectedGroupFilter == null || t.artist == selectedGroupFilter
                MusicTab.ALBUMS -> selectedGroupFilter == null || t.album == selectedGroupFilter
                else -> true
            }
            qOk && tabOk
        }
    }

    if (fullPlayerExpanded && currentTrack != null) {
        FullMusicNowPlayingOverlay(
            track = currentTrack,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = if (durationMs > 0L) durationMs else currentTrack.durationMs,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            playbackSpeed = playbackSpeed,
            showSpeedMenu = showSpeedMenu,
            onToggleSpeedMenu = { showSpeedMenu = !showSpeedMenu },
            onSelectSpeed = { sp ->
                playbackSpeed = sp
                exoPlayer.setPlaybackSpeed(sp)
                showSpeedMenu = false
            },
            onCollapse = { fullPlayerExpanded = false },
            onSeek = { targetMs -> exoPlayer.seekTo(targetMs) },
            onTogglePlayPause = {
                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
            },
            onNext = { playNextTrack(autoTriggered = false) },
            onPrev = { playPrevTrack() },
            onToggleShuffle = { shuffleEnabled = !shuffleEnabled },
            onCycleRepeat = {
                repeatMode = when (repeatMode) {
                    MusicRepeatMode.OFF -> MusicRepeatMode.ALL
                    MusicRepeatMode.ALL -> MusicRepeatMode.ONE
                    MusicRepeatMode.ONE -> MusicRepeatMode.OFF
                }
            },
            onToggleFavorite = {
                scope.launch { app.musicRepository.toggleFavorite(currentTrack) }
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(YtmBg)
    ) {
        // YouTube Music-style Top App Bar
        Surface(color = YtmSurface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(YtmRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            "Cyber Music",
                            color = YtmInk,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${tracks.size} lagu • Internal, SD & USB",
                            color = YtmMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) searchQuery = ""
                    }) {
                        Icon(
                            if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = "Cari Musik",
                            tint = YtmInk
                        )
                    }
                    IconButton(
                        onClick = { runRefreshImport(manual = true) },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = YtmRed,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh Import Lagu", tint = YtmInk)
                        }
                    }
                    IconButton(onClick = { safFolderPicker.launch(null) }) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = "Scan Folder SD / USB", tint = YtmInk)
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Pengaturan Musik", tint = YtmInk)
                    }
                    Button(
                        onClick = { audioFilePicker.launch(arrayOf("audio/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = YtmRed, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Import", fontSize = 12.sp)
                    }
                }
            }
        }

        if (searchOpen) {
            Surface(
                color = YtmCard,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = YtmMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text("Cari judul lagu, artis, album, atau nama file MP3...", color = YtmMuted, fontSize = 13.sp)
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = YtmInk, fontSize = 14.sp),
                            cursorBrush = SolidColor(YtmRed),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Hapus",
                            tint = YtmMuted,
                            modifier = Modifier.size(18.dp).clickable { searchQuery = "" }
                        )
                    }
                }
            }
        }

        // YouTube Music Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MusicTab.entries.forEach { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = {
                        selectedTab = tab
                        selectedGroupFilter = null
                    },
                    label = { Text(tab.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = YtmCard,
                        labelColor = YtmInk,
                        selectedContainerColor = Color.White,
                        selectedLabelColor = Color.Black
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedTab == tab,
                        borderColor = Color(0xFF2D2D38)
                    )
                )
            }
        }

        // Main Content Area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                tracks.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = YtmRed, modifier = Modifier.size(54.dp))
                        Text("Perpustakaan Musik Masih Kosong", color = YtmInk, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "Tekan Refresh Import untuk memindai seluruh file MP3 / audio di Internal, SD Card & USB tanpa duplikasi.",
                            color = YtmMuted,
                            fontSize = 13.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { runRefreshImport(manual = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = YtmRed, contentColor = Color.White)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Scan & Import Semua MP3")
                            }
                            OutlinedButton(onClick = { safFolderPicker.launch(null) }) {
                                Text("Pilih Folder SD / USB", color = YtmInk)
                            }
                        }
                    }
                }

                selectedTab == MusicTab.QUEUE -> {
                    if (queue.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Antrian kosong. Putar lagu apa saja untuk mengisi antrian.", color = YtmMuted)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(queue, key = { idx, t -> "${t.id}_$idx" }) { idx, track ->
                                MusicTrackRow(
                                    track = track,
                                    isCurrent = idx == currentIndex,
                                    isPlaying = idx == currentIndex && isPlaying,
                                    onPlay = { playFromList(queue, idx) },
                                    onToggleFavorite = { scope.launch { app.musicRepository.toggleFavorite(track) } },
                                    onDelete = { scope.launch { app.musicRepository.deleteTrack(track) } }
                                )
                            }
                        }
                    }
                }

                selectedTab == MusicTab.ARTISTS && selectedGroupFilter == null -> {
                    val groups = remember(filteredTracks) {
                        filteredTracks.groupBy { it.artist.ifBlank { "Unknown Artist" } }.toSortedMap()
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(groups.keys.toList(), key = { it }) { artist ->
                            val list = groups[artist].orEmpty()
                            Card(
                                colors = CardDefaults.cardColors(containerColor = YtmCard),
                                modifier = Modifier.fillMaxWidth().clickable { selectedGroupFilter = artist }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF2B2B36)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.Person, contentDescription = null, tint = YtmInk)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(artist, color = YtmInk, fontWeight = FontWeight.SemiBold)
                                        Text("${list.size} lagu", color = YtmMuted, fontSize = 12.sp)
                                    }
                                    IconButton(onClick = { playFromList(list, 0) }) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = "Putar Artis", tint = YtmRed)
                                    }
                                }
                            }
                        }
                    }
                }

                selectedTab == MusicTab.ALBUMS && selectedGroupFilter == null -> {
                    val groups = remember(filteredTracks) {
                        filteredTracks.groupBy { it.album.ifBlank { "Local Music" } }.toSortedMap()
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(groups.keys.toList(), key = { it }) { album ->
                            val list = groups[album].orEmpty()
                            Card(
                                colors = CardDefaults.cardColors(containerColor = YtmCard),
                                modifier = Modifier.fillMaxWidth().clickable { selectedGroupFilter = album }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2B2B36)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.Album, contentDescription = null, tint = YtmRed)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(album, color = YtmInk, fontWeight = FontWeight.SemiBold)
                                        Text("${list.size} lagu", color = YtmMuted, fontSize = 12.sp)
                                    }
                                    IconButton(onClick = { playFromList(list, 0) }) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = "Putar Album", tint = YtmRed)
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (selectedGroupFilter != null) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(selectedGroupFilter!!, color = YtmInk, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    TextButton(onClick = { selectedGroupFilter = null }) {
                                        Text("Semua", color = YtmRed)
                                    }
                                }
                            }
                        }

                        if (selectedTab == MusicTab.QUICK_PICKS && searchQuery.isBlank() && selectedGroupFilter == null) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Putar Cepat", color = YtmInk, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    OutlinedButton(onClick = {
                                        shuffleEnabled = true
                                        if (tracks.isNotEmpty()) playFromList(tracks.shuffled(), 0)
                                    }) {
                                        Icon(Icons.Filled.Shuffle, contentDescription = null, tint = YtmInk, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Acak Semua", color = YtmInk, fontSize = 12.sp)
                                    }
                                }
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    itemsIndexed(tracks.take(12), key = { _, t -> "qp_${t.id}" }) { idx, track ->
                                        Surface(
                                            color = YtmCard,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .width(148.dp)
                                                .clickable { playFromList(tracks, idx) }
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .aspectRatio(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            Brush.linearGradient(
                                                                listOf(Color(0xFF42121D), Color(0xFF1F2438))
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        if (currentTrack?.id == track.id) Icons.Filled.GraphicEq else Icons.Filled.MusicNote,
                                                        contentDescription = null,
                                                        tint = if (currentTrack?.id == track.id) YtmRed else Color.White.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(38.dp)
                                                    )
                                                }
                                                Spacer(Modifier.height(8.dp))
                                                Text(track.title, color = YtmInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(track.artist, color = YtmMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                                Text("Daftar Lagu", color = YtmInk, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                            }
                        }

                        itemsIndexed(filteredTracks, key = { _, t -> t.id }) { idx, track ->
                            MusicTrackRow(
                                track = track,
                                isCurrent = currentTrack?.id == track.id,
                                isPlaying = currentTrack?.id == track.id && isPlaying,
                                onPlay = { playFromList(filteredTracks, idx) },
                                onToggleFavorite = { scope.launch { app.musicRepository.toggleFavorite(track) } },
                                onDelete = { scope.launch { app.musicRepository.deleteTrack(track) } }
                            )
                        }
                    }
                }
            }
        }

        // YouTube Music-style Persistent Mini Player Bar
        if (currentTrack != null) {
            HorizontalDivider(color = Color(0xFF282832))
            Surface(
                color = YtmSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { fullPlayerExpanded = true }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val dur = (if (durationMs > 0L) durationMs else currentTrack.durationMs).coerceAtLeast(1L)
                    val prog = (positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Color(0xFF2B2B36))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(prog)
                                .height(2.dp)
                                .background(YtmRed)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brush.linearGradient(listOf(YtmRed.copy(alpha = 0.7f), Color(0xFF282836)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                currentTrack.title,
                                color = YtmInk,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${currentTrack.artist} • ${currentTrack.volumeLabel}",
                                color = YtmMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { scope.launch { app.musicRepository.toggleFavorite(currentTrack) } }) {
                            Icon(
                                if (currentTrack.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Favorit",
                                tint = if (currentTrack.isFavorite) YtmRed else YtmMuted
                            )
                        }
                        IconButton(onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(onClick = { playNextTrack(autoTriggered = false) }) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Lagu Berikutnya", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Pengaturan Cyber Music") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Import Saat Dibuka", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Otomatis memindai file MP3 baru di Internal, SD Card & USB tanpa duplikasi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.musicAutoImport,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    app.settingsRepository.updateSettings { it.copy(musicAutoImport = enabled) }
                                }
                            }
                        )
                    }
                    HorizontalDivider()
                    Button(
                        onClick = {
                            showSettingsDialog = false
                            runRefreshImport(manual = true)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh Import Lagu Baru Sekarang")
                    }
                    OutlinedButton(
                        onClick = {
                            showSettingsDialog = false
                            safFolderPicker.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tambah Folder Musik (SD / USB)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("Tutup") }
            }
        )
    }
}

@Composable
private fun MusicTrackRow(
    track: MusicTrackEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = if (isCurrent) Color(0xFF281720) else YtmCard,
        shape = RoundedCornerShape(10.dp),
        border = if (isCurrent) BorderStroke(1.dp, YtmRed.copy(alpha = 0.65f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isCurrent) YtmRed.copy(alpha = 0.25f) else Color(0xFF262632)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isCurrent && isPlaying) Icons.Filled.GraphicEq else Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = if (isCurrent) YtmRed else YtmInk
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = if (isCurrent) YtmRed else YtmInk,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(track.artist)
                        append(" • ")
                        append(track.volumeLabel)
                        if (track.durationMs > 0L) append(" • ${Formatters.formatDurationMs(track.durationMs)}")
                    },
                    color = YtmMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(34.dp)) {
                Icon(
                    if (track.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorit",
                    tint = if (track.isFavorite) YtmRed else YtmMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Hapus dari daftar",
                    tint = YtmMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun FullMusicNowPlayingOverlay(
    track: MusicTrackEntity,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: MusicRepeatMode,
    playbackSpeed: Float,
    showSpeedMenu: Boolean,
    onToggleSpeedMenu: () -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onCollapse: () -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val safeDur = durationMs.coerceAtLeast(1L)
    val safePos = positionMs.coerceIn(0L, safeDur)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF280B14), YtmBg)
                )
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Tutup Player", tint = YtmInk)
            }
            Text("Sedang Diputar • ${track.volumeLabel}", color = YtmMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Box {
                TextButton(onClick = onToggleSpeedMenu) {
                    Icon(Icons.Filled.Speed, contentDescription = null, tint = YtmInk, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${playbackSpeed}x", color = YtmInk)
                }
                DropdownMenu(expanded = showSpeedMenu, onDismissRequest = onToggleSpeedMenu) {
                    listOf(0.75f, 1.0f, 1.15f, 1.25f, 1.5f).forEach { sp ->
                        DropdownMenuItem(text = { Text("${sp}x") }, onClick = { onSelectSpeed(sp) })
                    }
                }
            }
        }

        Spacer(Modifier.weight(0.5f))

        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(YtmRed.copy(alpha = 0.78f), Color(0xFF241432))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(96.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = YtmInk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${track.artist} • ${track.album}",
                    color = YtmMuted,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (track.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorit",
                    tint = if (track.isFavorite) YtmRed else YtmInk
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Slider(
            value = safePos.toFloat() / safeDur.toFloat(),
            onValueChange = { frac -> onSeek((frac * safeDur).toLong()) },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = YtmRed,
                inactiveTrackColor = Color(0xFF3A3A48)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(Formatters.formatDurationMs(safePos), color = YtmMuted, fontSize = 12.sp)
            Text(Formatters.formatDurationMs(safeDur), color = YtmMuted, fontSize = 12.sp)
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Acak",
                    tint = if (shuffleEnabled) YtmRed else YtmMuted
                )
            }
            IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Sebelumnya", tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onTogglePlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Berikutnya", tint = Color.White, modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(
                    if (repeatMode == MusicRepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Ulangi",
                    tint = if (repeatMode != MusicRepeatMode.OFF) YtmRed else YtmMuted
                )
            }
        }

        Spacer(Modifier.weight(0.5f))
    }
}
