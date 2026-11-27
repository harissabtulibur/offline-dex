package com.offlinecyberdeck.music

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes as AndroidAudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import com.offlinecyberdeck.CyberDeckApplication
import com.offlinecyberdeck.core.database.entity.AppSettingsEntity
import com.offlinecyberdeck.core.database.entity.MusicTrackEntity
import com.offlinecyberdeck.core.format.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class FloatingMp3WindowMode {
    FLOATING,
    MINIMIZED,
    MAXIMIZED
}

private val Mp3Accent = Color(0xFFFF0033)
private val Mp3Cyan = Color(0xFF22D3EE)
private val Mp3Bg = Color(0xFF07090F)
private val Mp3Surface = Color(0xFF111520)
private val Mp3Card = Color(0xFF191F2E)
private val Mp3Ink = Color(0xFFF5F5F7)
private val Mp3Muted = Color(0xFFA3A8BA)

private enum class MusicTab(val label: String) {
    QUICK_PICKS("Pilihan Cepat"),
    SONGS("Semua Lagu"),
    ARTISTS("Artis"),
    ALBUMS("Album / Folder"),
    FAVORITES("Favorit"),
    QUEUE("Antrian")
}

private enum class MusicRepeatMode { OFF, ALL, ONE }

/**
 * Floating MP3 Player supporting Minimize, Maximize, Restore Floating Window, and Close.
 * Audio playback (ExoPlayer / MediaPlayer fallback) persists seamlessly while minimized.
 */
@OptIn(UnstableApi::class)
@Composable
fun FloatingMp3PlayerOverlay(
    app: CyberDeckApplication,
    windowMode: FloatingMp3WindowMode,
    onChangeWindowMode: (FloatingMp3WindowMode) -> Unit,
    onClose: () -> Unit,
    onTextInputFocusChanged: (Boolean) -> Unit = {},
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val tracks by app.musicRepository.tracksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by app.settingsRepository.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettingsEntity())
    val driveStatus by app.driveManager.status.collectAsStateWithLifecycle()

    val requiredAudioPerm = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, requiredAudioPerm) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAllFilesAccess by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
        )
    }

    var accessibleIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(tracks, driveStatus, settings.extraMediaSafUris, hasAudioPermission, hasAllFilesAccess) {
        accessibleIds = withContext(Dispatchers.IO) {
            tracks.filter { app.musicRepository.isTrackAccessible(it) }
                .mapTo(HashSet()) { it.id }
        }
    }
    val visibleTracks = remember(tracks, accessibleIds) {
        tracks.filter { it.id in accessibleIds }
    }

    var selectedTab by remember { mutableStateOf(MusicTab.SONGS) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedGroupFilter by remember { mutableStateOf<String?>(null) }

    var isRefreshing by remember { mutableStateOf(false) }
    var autoImportedOnce by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var fullNowPlayingExpanded by remember { mutableStateOf(false) }

    var queue by remember { mutableStateOf<List<MusicTrackEntity>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(MusicRepeatMode.ALL) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var playRequestToken by remember { mutableLongStateOf(0L) }
    var candidateQueue by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var candidateIndex by remember { mutableIntStateOf(0) }
    var deepSafFallbackTriedTrackId by remember { mutableStateOf<String?>(null) }
    var cacheFallbackTriedTrackId by remember { mutableStateOf<String?>(null) }
    var mediaPlayerFallbackTriedTrackId by remember { mutableStateOf<String?>(null) }
    var fallbackPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var usingFallbackPlayer by remember { mutableStateOf(false) }

    // Floating & Minimized draggable offsets (dp)
    var floatXDp by remember { mutableFloatStateOf(36f) }
    var floatYDp by remember { mutableFloatStateOf(36f) }
    var miniOffsetXPx by remember { mutableFloatStateOf(16f) }
    var miniOffsetYPx by remember { mutableFloatStateOf(-64f) }

    val exoPlayer = remember {
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context, extractorsFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false
            )
            .build()
    }

    fun releaseFallbackPlayer() {
        val mp = fallbackPlayer
        fallbackPlayer = null
        usingFallbackPlayer = false
        if (mp != null) {
            runCatching { mp.stop() }
            runCatching { mp.reset() }
            runCatching { mp.release() }
        }
    }

    fun seekAudioTo(targetMs: Long) {
        val clamped = targetMs.coerceAtLeast(0L)
        if (usingFallbackPlayer) {
            runCatching { fallbackPlayer?.seekTo(clamped.toInt()) }
            positionMs = clamped
        } else {
            exoPlayer.seekTo(clamped)
        }
    }

    fun setAudioPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        if (usingFallbackPlayer) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                runCatching {
                    fallbackPlayer?.let { mp ->
                        mp.playbackParams = mp.playbackParams.setSpeed(speed)
                    }
                }
            }
        } else {
            exoPlayer.setPlaybackSpeed(speed)
        }
    }

    fun toggleAudioPlayPause() {
        if (usingFallbackPlayer) {
            val mp = fallbackPlayer ?: return
            runCatching {
                if (mp.isPlaying) {
                    mp.pause()
                    isPlaying = false
                } else {
                    mp.start()
                    isPlaying = true
                }
            }
        } else {
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        }
    }

    val currentTrack = queue.getOrNull(currentIndex)
        ?.let { cur -> tracks.firstOrNull { it.id == cur.id } ?: cur }

    fun playFromList(list: List<MusicTrackEntity>, startIdx: Int) {
        if (list.isEmpty() || startIdx !in list.indices) return
        val target = list[startIdx]
        queue = list
        currentIndex = startIdx
        positionMs = 0L
        durationMs = target.durationMs.coerceAtLeast(0L)
        candidateQueue = emptyList()
        candidateIndex = 0
        deepSafFallbackTriedTrackId = null
        cacheFallbackTriedTrackId = null
        mediaPlayerFallbackTriedTrackId = null
        releaseFallbackPlayer()
        runCatching { exoPlayer.stop() }

        val token = ++playRequestToken
        val extraSaf = settings.extraMediaSafUris.split('\n').map { it.trim() }.filter { it.isNotBlank() }

        scope.launch {
            val candidates = app.musicRepository.resolvePlayableCandidates(
                track = target,
                deckDriveTreeUri = settings.deckDriveTreeUri,
                extraSafUris = extraSaf,
                includeDeepSafSearch = false
            )
            if (token != playRequestToken) return@launch

            if (candidates.isEmpty()) {
                onShowMessage("File audio '${target.fileName}' tidak ditemukan. Coba Scan Folder SD / Refresh.")
                return@launch
            }
            candidateQueue = candidates
            candidateIndex = 0
            val playUri = candidates[0]
            usingFallbackPlayer = false
            exoPlayer.setMediaItem(MediaItem.fromUri(playUri))
            exoPlayer.prepare()
            exoPlayer.setPlaybackSpeed(playbackSpeed)
            exoPlayer.playWhenReady = true
            app.musicRepository.recordPlay(target)
        }
    }

    fun playNextTrack(autoTriggered: Boolean) {
        if (queue.isEmpty()) return
        if (autoTriggered && repeatMode == MusicRepeatMode.ONE && currentIndex in queue.indices) {
            seekAudioTo(0L)
            if (usingFallbackPlayer) {
                runCatching { fallbackPlayer?.start() }
                isPlaying = true
            } else {
                exoPlayer.playWhenReady = true
            }
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
            if (usingFallbackPlayer) {
                runCatching { fallbackPlayer?.pause() }
                isPlaying = false
            } else {
                exoPlayer.pause()
            }
        }
    }

    fun playPrevTrack() {
        if (queue.isEmpty()) return
        val curPos = if (usingFallbackPlayer) {
            runCatching { fallbackPlayer?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)
        } else {
            exoPlayer.currentPosition
        }
        if (curPos > 3500L) {
            seekAudioTo(0L)
            return
        }
        val prevIdx = if (currentIndex - 1 >= 0) currentIndex - 1 else queue.lastIndex
        playFromList(queue, prevIdx)
    }

    fun startAndroidMediaPlayerFallback(track: MusicTrackEntity, uri: Uri): Boolean {
        releaseFallbackPlayer()
        val mp = MediaPlayer()
        return runCatching {
            mp.setAudioAttributes(
                AndroidAudioAttributes.Builder()
                    .setUsage(AndroidAudioAttributes.USAGE_MEDIA)
                    .setContentType(AndroidAudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            when (uri.scheme) {
                "file", null, "" -> {
                    val p = uri.path ?: uri.toString()
                    mp.setDataSource(p)
                }
                else -> {
                    val setViaFd = runCatching {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            mp.setDataSource(pfd.fileDescriptor)
                            true
                        } ?: false
                    }.getOrDefault(false)
                    if (!setViaFd) {
                        mp.setDataSource(context, uri)
                    }
                }
            }
            mp.prepare()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && playbackSpeed != 1.0f) {
                runCatching { mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed) }
            }
            mp.setOnCompletionListener {
                playNextTrack(autoTriggered = true)
            }
            mp.setOnErrorListener { _, _, _ ->
                isPlaying = false
                releaseFallbackPlayer()
                onShowMessage("Format audio '${track.fileName}' tidak dapat diputar oleh dekoder sistem.")
                true
            }
            mp.start()
            fallbackPlayer = mp
            usingFallbackPlayer = true
            isPlaying = true
            val d = runCatching { mp.duration.toLong() }.getOrDefault(0L)
            if (d > 0L) durationMs = d
            true
        }.getOrElse {
            runCatching { mp.release() }
            false
        }
    }

    fun runRefreshImport(manual: Boolean) {
        if (isRefreshing) return
        scope.launch {
            isRefreshing = true
            val extraSaf = settings.extraMediaSafUris.split('\n').map { it.trim() }.filter { it.isNotBlank() }
            app.musicRepository.autoImportAndRefresh(settings.deckDriveTreeUri, extraSaf)
                .onSuccess { summary ->
                    if (manual) {
                        val removedPart = if (summary.removedCount > 0) ", ${summary.removedCount} lagu nonaktif dibersihkan" else ""
                        onShowMessage(
                            "Import selesai: ${summary.importedCount} MP3/audio baru ditambahkan$removedPart (${summary.skippedCount} file lama dilewati)."
                        )
                    } else if (summary.importedCount > 0 || summary.removedCount > 0) {
                        val parts = buildList {
                            if (summary.importedCount > 0) add("${summary.importedCount} lagu baru")
                            if (summary.removedCount > 0) add("${summary.removedCount} lagu terputus dibersihkan")
                        }
                        onShowMessage("Auto-Import MP3 Player: ${parts.joinToString(", ")}.")
                    }
                }
                .onFailure {
                    if (manual) onShowMessage("Refresh MP3 gagal: ${it.message}")
                }
            isRefreshing = false
        }
    }

    DisposableEffect(lifecycleOwner, requiredAudioPerm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val prevAudio = hasAudioPermission
                val prevAllFiles = hasAllFilesAccess
                hasAudioPermission = ContextCompat.checkSelfPermission(context, requiredAudioPerm) == PackageManager.PERMISSION_GRANTED
                hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
                if ((!prevAudio && hasAudioPermission) || (!prevAllFiles && hasAllFilesAccess)) {
                    runRefreshImport(manual = false)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val audioPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            runRefreshImport(manual = true)
        } else {
            onShowMessage("Izin audio ditolak. Anda masih bisa memilih Folder SD Card lewat ikon Folder (SAF).")
        }
    }

    LaunchedEffect(settings.musicAutoImport, settings.deckDriveTreeUri, settings.extraMediaSafUris) {
        if (settings.musicAutoImport && !autoImportedOnce) {
            autoImportedOnce = true
            runRefreshImport(manual = false)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                if (!usingFallbackPlayer) {
                    isPlaying = playing
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (usingFallbackPlayer) return
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                } else if (playbackState == Player.STATE_ENDED) {
                    playNextTrack(autoTriggered = true)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (usingFallbackPlayer) return
                val track = queue.getOrNull(currentIndex)
                val failedUri = exoPlayer.currentMediaItem?.localConfiguration?.uri
                val nextCandidateIdx = candidateIndex + 1
                val srcTag = failedUri?.scheme ?: "unknown"

                fun reportPlayFailure() {
                    val detail = error.localizedMessage ?: error.errorCodeName
                    val fallbackMsg = "Gagal memutar audio ($srcTag / ${error.errorCodeName}): $detail. Coba Scan Folder SD / Refresh."
                    val currentTrackSnap = track
                    if (currentTrackSnap == null) {
                        onShowMessage(fallbackMsg)
                        return
                    }
                    scope.launch {
                        val diagnosis = app.musicRepository.diagnoseAudioFailure(
                            track = currentTrackSnap,
                            candidates = candidateQueue
                        )
                        onShowMessage(diagnosis ?: fallbackMsg)
                    }
                }

                if (track != null && nextCandidateIdx < candidateQueue.size) {
                    candidateIndex = nextCandidateIdx
                    val altUri = candidateQueue[nextCandidateIdx]
                    exoPlayer.setMediaItem(MediaItem.fromUri(altUri))
                    exoPlayer.prepare()
                    exoPlayer.setPlaybackSpeed(playbackSpeed)
                    exoPlayer.playWhenReady = true
                } else if (track != null && deepSafFallbackTriedTrackId != track.id) {
                    deepSafFallbackTriedTrackId = track.id
                    val errorToken = playRequestToken
                    val extraSaf = settings.extraMediaSafUris.split('\n').map { it.trim() }.filter { it.isNotBlank() }
                    scope.launch {
                        val deepCandidates = app.musicRepository.resolvePlayableCandidates(
                            track = track,
                            deckDriveTreeUri = settings.deckDriveTreeUri,
                            extraSafUris = extraSaf,
                            includeDeepSafSearch = true
                        ).filter { it !in candidateQueue && it != failedUri }
                        if (errorToken != playRequestToken) return@launch
                        if (deepCandidates.isNotEmpty()) {
                            val startIdx = candidateQueue.size
                            candidateQueue = candidateQueue + deepCandidates
                            candidateIndex = startIdx
                            exoPlayer.setMediaItem(MediaItem.fromUri(deepCandidates.first()))
                            exoPlayer.prepare()
                            exoPlayer.setPlaybackSpeed(playbackSpeed)
                            exoPlayer.playWhenReady = true
                        } else if (cacheFallbackTriedTrackId != track.id) {
                            cacheFallbackTriedTrackId = track.id
                            val toCache = buildList {
                                failedUri?.let { add(it) }
                                addAll(candidateQueue)
                            }.distinct()
                            val cachedUri = toCache.firstNotNullOfOrNull { src ->
                                app.musicRepository.cacheUriOrFileForPlayback(track, src)
                            }
                            if (errorToken != playRequestToken) return@launch
                            if (cachedUri != null && cachedUri != failedUri) {
                                exoPlayer.setMediaItem(MediaItem.fromUri(cachedUri))
                                exoPlayer.prepare()
                                exoPlayer.setPlaybackSpeed(playbackSpeed)
                                exoPlayer.playWhenReady = true
                            } else if (mediaPlayerFallbackTriedTrackId != track.id) {
                                mediaPlayerFallbackTriedTrackId = track.id
                                val mpSource = cachedUri ?: candidateQueue.firstOrNull() ?: failedUri
                                if (mpSource == null || !startAndroidMediaPlayerFallback(track, mpSource)) {
                                    reportPlayFailure()
                                }
                            } else {
                                reportPlayFailure()
                            }
                        } else {
                            reportPlayFailure()
                        }
                    }
                } else if (track != null && cacheFallbackTriedTrackId != track.id) {
                    cacheFallbackTriedTrackId = track.id
                    val errorToken = playRequestToken
                    scope.launch {
                        val toCache = buildList {
                            failedUri?.let { add(it) }
                            addAll(candidateQueue)
                        }.distinct()
                        val cachedUri = toCache.firstNotNullOfOrNull { src ->
                            app.musicRepository.cacheUriOrFileForPlayback(track, src)
                        }
                        if (errorToken != playRequestToken) return@launch
                        if (cachedUri != null && cachedUri != failedUri) {
                            exoPlayer.setMediaItem(MediaItem.fromUri(cachedUri))
                            exoPlayer.prepare()
                            exoPlayer.setPlaybackSpeed(playbackSpeed)
                            exoPlayer.playWhenReady = true
                        } else if (mediaPlayerFallbackTriedTrackId != track.id) {
                            mediaPlayerFallbackTriedTrackId = track.id
                            val mpSource = cachedUri ?: candidateQueue.firstOrNull() ?: failedUri
                            if (mpSource == null || !startAndroidMediaPlayerFallback(track, mpSource)) {
                                reportPlayFailure()
                            }
                        } else {
                            reportPlayFailure()
                        }
                    }
                } else if (track != null && mediaPlayerFallbackTriedTrackId != track.id) {
                    mediaPlayerFallbackTriedTrackId = track.id
                    val errorToken = playRequestToken
                    scope.launch {
                        val toTry = buildList {
                            failedUri?.let { add(it) }
                            addAll(candidateQueue)
                        }.distinct()
                        val mpUri = toTry.firstNotNullOfOrNull { src ->
                            app.musicRepository.cacheUriOrFileForPlayback(track, src)
                        } ?: toTry.firstOrNull()
                        if (errorToken != playRequestToken) return@launch
                        if (mpUri == null || !startAndroidMediaPlayerFallback(track, mpUri)) {
                            reportPlayFailure()
                        }
                    }
                } else {
                    reportPlayFailure()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            onTextInputFocusChanged(false)
            exoPlayer.removeListener(listener)
            runCatching { exoPlayer.stop() }
            runCatching { exoPlayer.release() }
            releaseFallbackPlayer()
        }
    }

    LaunchedEffect(isPlaying, currentTrack?.id, usingFallbackPlayer) {
        while (true) {
            if (usingFallbackPlayer) {
                val mp = fallbackPlayer
                if (mp != null) {
                    runCatching {
                        positionMs = mp.currentPosition.toLong().coerceAtLeast(0L)
                        val d = mp.duration.toLong()
                        if (d > 0L) durationMs = d
                        isPlaying = mp.isPlaying
                    }
                }
            } else {
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                val d = exoPlayer.duration
                if (d > 0L) durationMs = d
            }
            delay(400L)
        }
    }

    LaunchedEffect(windowMode) {
        if (windowMode == FloatingMp3WindowMode.MINIMIZED) {
            onTextInputFocusChanged(false)
        }
    }

    BackHandler(enabled = fullNowPlayingExpanded || windowMode == FloatingMp3WindowMode.MAXIMIZED) {
        if (fullNowPlayingExpanded) {
            fullNowPlayingExpanded = false
        } else if (windowMode == FloatingMp3WindowMode.MAXIMIZED) {
            onChangeWindowMode(FloatingMp3WindowMode.FLOATING)
        }
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

    val filteredTracks = remember(visibleTracks, searchQuery, selectedTab, selectedGroupFilter) {
        visibleTracks.filter { t ->
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(9950f)
    ) {
        val screenW = maxWidth.value
        val screenH = maxHeight.value

        if (windowMode == FloatingMp3WindowMode.MINIMIZED) {
            // MINIMIZED FLOATING MINI MP3 BAR (Music continues uninterrupted!)
            val activeTrack = currentTrack
            val dur = if (activeTrack != null) {
                (if (durationMs > 0L) durationMs else activeTrack.durationMs).coerceAtLeast(1L)
            } else 1L
            val safePos = positionMs.coerceIn(0L, dur)

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Mp3Surface.copy(alpha = 0.96f),
                border = BorderStroke(1.5.dp, Mp3Accent.copy(alpha = 0.7f)),
                shadowElevation = 18.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset { IntOffset(miniOffsetXPx.roundToInt(), miniOffsetYPx.roundToInt()) }
                    .width(340.dp.coerceAtMost((screenW - 24f).dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            miniOffsetXPx += dragAmount.x
                            miniOffsetYPx += dragAmount.y
                        }
                    }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (activeTrack != null) {
                        LinearProgressIndicator(
                            progress = { (safePos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) },
                            color = Mp3Accent,
                            trackColor = Color(0xFF282D3C),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(Mp3Accent, Color(0xFF311B42)))
                                )
                                .clickable { onChangeWindowMode(FloatingMp3WindowMode.FLOATING) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.MusicNote,
                                contentDescription = "Restore MP3 Player",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onChangeWindowMode(FloatingMp3WindowMode.FLOATING) }
                        ) {
                            Text(
                                text = activeTrack?.title ?: "MP3 Player (Minimized)",
                                color = Mp3Ink,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (activeTrack != null) {
                                    "${Formatters.formatDurationMs(safePos)} / ${Formatters.formatDurationMs(dur)}"
                                } else {
                                    "${visibleTracks.size} lagu siap"
                                },
                                color = Mp3Muted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (activeTrack != null) {
                            IconButton(onClick = { playPrevTrack() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.SkipPrevious, contentDescription = "Sebelumnya", tint = Mp3Ink, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { toggleAudioPlayPause() }, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Mp3Accent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(onClick = { playNextTrack(autoTriggered = false) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.SkipNext, contentDescription = "Berikutnya", tint = Mp3Ink, modifier = Modifier.size(18.dp))
                            }
                        }
                        IconButton(
                            onClick = { onChangeWindowMode(FloatingMp3WindowMode.FLOATING) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Filled.OpenInFull, contentDescription = "Restore Floating", tint = Mp3Cyan, modifier = Modifier.size(15.dp))
                        }
                        IconButton(
                            onClick = { onChangeWindowMode(FloatingMp3WindowMode.MAXIMIZED) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Filled.CropSquare, contentDescription = "Maximize", tint = Mp3Ink, modifier = Modifier.size(15.dp))
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            return@BoxWithConstraints
        }

        // FLOATING WINDOW (DEFAULT) OR MAXIMIZED WINDOW
        val isMaximized = windowMode == FloatingMp3WindowMode.MAXIMIZED
        val winWidthDp = (screenW * 0.72f).coerceIn(320f, 560f)
        val winHeightDp = (screenH * 0.76f).coerceIn(320f, 580f)
        val clampedX = floatXDp.coerceIn(0f, (screenW - winWidthDp).coerceAtLeast(0f))
        val clampedY = floatYDp.coerceIn(0f, (screenH - winHeightDp).coerceAtLeast(0f))

        val containerModifier = if (isMaximized) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .offset {
                    with(density) { IntOffset(clampedX.dp.roundToPx(), clampedY.dp.roundToPx()) }
                }
                .width(winWidthDp.dp)
                .height(winHeightDp.dp)
        }

        Surface(
            shape = if (isMaximized) RoundedCornerShape(0.dp) else RoundedCornerShape(14.dp),
            color = Mp3Bg,
            border = if (isMaximized) null else BorderStroke(1.5.dp, Mp3Accent.copy(alpha = 0.7f)),
            shadowElevation = if (isMaximized) 0.dp else 20.dp,
            modifier = containerModifier
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // WINDOW CHROME TITLE BAR (Draggable in Floating mode + Minimize / Maximize / Close)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0C101A))
                        .pointerInput(isMaximized) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                if (!isMaximized) {
                                    floatXDp = (floatXDp + dragAmount.x / density.density)
                                        .coerceIn(0f, (screenW - winWidthDp).coerceAtLeast(0f))
                                    floatYDp = (floatYDp + dragAmount.y / density.density)
                                        .coerceIn(0f, (screenH - winHeightDp).coerceAtLeast(0f))
                                }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Mp3Accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MP3 Player (${visibleTracks.size} lagu)",
                            color = Mp3Ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            fullNowPlayingExpanded = false
                            onChangeWindowMode(FloatingMp3WindowMode.MINIMIZED)
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Minimize", tint = Mp3Ink, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            onChangeWindowMode(
                                if (isMaximized) FloatingMp3WindowMode.FLOATING else FloatingMp3WindowMode.MAXIMIZED
                            )
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            if (isMaximized) Icons.Filled.FilterNone else Icons.Filled.CropSquare,
                            contentDescription = if (isMaximized) "Restore Floating" else "Maximize",
                            tint = Mp3Ink,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = Color(0xFF232838))

                if (fullNowPlayingExpanded && currentTrack != null) {
                    FullMusicNowPlayingView(
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
                            setAudioPlaybackSpeed(sp)
                            showSpeedMenu = false
                        },
                        onCollapse = { fullNowPlayingExpanded = false },
                        onSeek = { targetMs -> seekAudioTo(targetMs) },
                        onTogglePlayPause = { toggleAudioPlayPause() },
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
                } else {
                    // TOOLBAR & ACTIONS
                    Surface(color = Mp3Surface) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Internal, SD & USB Audio",
                                color = Mp3Muted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        searchOpen = !searchOpen
                                        if (!searchOpen) searchQuery = ""
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                                        contentDescription = "Cari MP3",
                                        tint = Mp3Ink,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { runRefreshImport(manual = true) },
                                    enabled = !isRefreshing,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    if (isRefreshing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Mp3Accent,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh MP3", tint = Mp3Ink, modifier = Modifier.size(18.dp))
                                    }
                                }
                                IconButton(onClick = { safFolderPicker.launch(null) }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = "Folder SD / USB", tint = Mp3Ink, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { showSettingsDialog = true }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Pengaturan MP3", tint = Mp3Ink, modifier = Modifier.size(18.dp))
                                }
                                Button(
                                    onClick = { audioFilePicker.launch(arrayOf("audio/*", "application/ogg")) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Mp3Accent, contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("Import", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    if (searchOpen) {
                        Surface(
                            color = Mp3Card,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null, tint = Mp3Muted, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text("Cari judul lagu, artis, album, atau nama file MP3...", color = Mp3Muted, fontSize = 12.sp)
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(color = Mp3Ink, fontSize = 13.sp),
                                        cursorBrush = SolidColor(Mp3Accent),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { onTextInputFocusChanged(it.isFocused) }
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Hapus",
                                        tint = Mp3Muted,
                                        modifier = Modifier.size(16.dp).clickable { searchQuery = "" }
                                    )
                                }
                            }
                        }
                    }

                    // FILTER TABS
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MusicTab.entries.forEach { tab ->
                            FilterChip(
                                selected = selectedTab == tab,
                                onClick = {
                                    selectedTab = tab
                                    selectedGroupFilter = null
                                },
                                label = { Text(tab.label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Mp3Card,
                                    labelColor = Mp3Ink,
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

                    if (!hasAudioPermission || !hasAllFilesAccess) {
                        Surface(
                            color = Color(0xFF1F161D),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Mp3Accent.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    if (!hasAudioPermission) {
                                        "Berikan izin Audio agar lagu dari SD Card & Internal otomatis terbaca."
                                    } else {
                                        "Aktifkan All Files Access atau pilih folder SD Card lewat SAF."
                                    },
                                    color = Mp3Ink,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!hasAudioPermission) {
                                    Button(
                                        onClick = { audioPermLauncher.launch(requiredAudioPerm) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Mp3Accent, contentColor = Color.White),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Izinkan", fontSize = 11.sp)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(
                                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                                            Uri.parse("package:${context.packageName}")
                                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    )
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Izin File", color = Mp3Ink, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // TRACK LIST AREA
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            visibleTracks.isEmpty() -> {
                                Column(
                                    modifier = Modifier.align(Alignment.Center).padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Mp3Accent, modifier = Modifier.size(44.dp))
                                    Text("Daftar MP3 Masih Kosong", color = Mp3Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(
                                        "Tekan Scan / Refresh atau pilih Folder SD Card / USB untuk memuat lagu.",
                                        color = Mp3Muted,
                                        fontSize = 12.sp
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                if (!hasAudioPermission) {
                                                    audioPermLauncher.launch(requiredAudioPerm)
                                                } else {
                                                    runRefreshImport(manual = true)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Mp3Accent, contentColor = Color.White)
                                        ) {
                                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (!hasAudioPermission) "Izinkan & Scan" else "Scan MP3", fontSize = 12.sp)
                                        }
                                        OutlinedButton(onClick = { safFolderPicker.launch(null) }) {
                                            Text("Folder SD/USB", color = Mp3Ink, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            selectedTab == MusicTab.QUEUE -> {
                                if (queue.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Antrian kosong. Putar lagu untuk mengisi antrian.", color = Mp3Muted, fontSize = 12.sp)
                                    }
                                } else {
                                    LazyColumn(
                                        contentPadding = PaddingValues(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        itemsIndexed(queue, key = { idx, t -> "${t.id}_$idx" }) { idx, track ->
                                            Mp3TrackRow(
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
                                    contentPadding = PaddingValues(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(groups.keys.toList(), key = { it }) { artist ->
                                        val list = groups[artist].orEmpty()
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Mp3Card),
                                            modifier = Modifier.fillMaxWidth().clickable { selectedGroupFilter = artist }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF2B2B36)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Filled.Person, contentDescription = null, tint = Mp3Ink)
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(artist, color = Mp3Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                    Text("${list.size} lagu", color = Mp3Muted, fontSize = 11.sp)
                                                }
                                                IconButton(onClick = { playFromList(list, 0) }) {
                                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Putar Artis", tint = Mp3Accent)
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
                                    contentPadding = PaddingValues(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(groups.keys.toList(), key = { it }) { album ->
                                        val list = groups[album].orEmpty()
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Mp3Card),
                                            modifier = Modifier.fillMaxWidth().clickable { selectedGroupFilter = album }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2B2B36)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Filled.Album, contentDescription = null, tint = Mp3Accent)
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(album, color = Mp3Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                    Text("${list.size} lagu", color = Mp3Muted, fontSize = 11.sp)
                                                }
                                                IconButton(onClick = { playFromList(list, 0) }) {
                                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Putar Album", tint = Mp3Accent)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            else -> {
                                LazyColumn(
                                    contentPadding = PaddingValues(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (selectedGroupFilter != null) {
                                        item {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(selectedGroupFilter!!, color = Mp3Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                TextButton(onClick = { selectedGroupFilter = null }) {
                                                    Text("Semua", color = Mp3Accent)
                                                }
                                            }
                                        }
                                    }

                                    if (selectedTab == MusicTab.QUICK_PICKS && searchQuery.isBlank() && selectedGroupFilter == null) {
                                        item {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Putar Cepat", color = Mp3Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                OutlinedButton(onClick = {
                                                    shuffleEnabled = true
                                                    if (visibleTracks.isNotEmpty()) playFromList(visibleTracks.shuffled(), 0)
                                                }) {
                                                    Icon(Icons.Filled.Shuffle, contentDescription = null, tint = Mp3Ink, modifier = Modifier.size(14.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Acak Semua", color = Mp3Ink, fontSize = 11.sp)
                                                }
                                            }
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                contentPadding = PaddingValues(vertical = 6.dp)
                                            ) {
                                                itemsIndexed(visibleTracks.take(12), key = { _, t -> "qp_${t.id}" }) { idx, track ->
                                                    Surface(
                                                        color = Mp3Card,
                                                        shape = RoundedCornerShape(10.dp),
                                                        modifier = Modifier
                                                            .width(128.dp)
                                                            .clickable { playFromList(visibleTracks, idx) }
                                                    ) {
                                                        Column(modifier = Modifier.padding(8.dp)) {
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
                                                                    tint = if (currentTrack?.id == track.id) Mp3Accent else Color.White.copy(alpha = 0.85f),
                                                                    modifier = Modifier.size(32.dp)
                                                                )
                                                            }
                                                            Spacer(Modifier.height(6.dp))
                                                            Text(track.title, color = Mp3Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            Text(track.artist, color = Mp3Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    itemsIndexed(filteredTracks, key = { _, t -> t.id }) { idx, track ->
                                        Mp3TrackRow(
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

                    // DOCKED BOTTOM TRANSPORT BAR
                    if (currentTrack != null) {
                        HorizontalDivider(color = Color(0xFF282832))
                        Surface(
                            color = Mp3Surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { fullNowPlayingExpanded = true }
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val dur = (if (durationMs > 0L) durationMs else currentTrack.durationMs).coerceAtLeast(1L)
                                val safePos = positionMs.coerceIn(0L, dur)
                                Slider(
                                    value = safePos.toFloat() / dur.toFloat(),
                                    onValueChange = { frac -> seekAudioTo((frac * dur).toLong()) },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Mp3Accent,
                                        inactiveTrackColor = Color(0xFF2B2B36)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(22.dp)
                                        .padding(horizontal = 8.dp)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 10.dp, end = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Brush.linearGradient(listOf(Mp3Accent.copy(alpha = 0.7f), Color(0xFF282836)))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            currentTrack.title,
                                            color = Mp3Ink,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${currentTrack.artist} • ${Formatters.formatDurationMs(safePos)} / ${Formatters.formatDurationMs(dur)}",
                                            color = Mp3Muted,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { scope.launch { app.musicRepository.toggleFavorite(currentTrack) } }, modifier = Modifier.size(30.dp)) {
                                        Icon(
                                            if (currentTrack.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                            contentDescription = "Favorit",
                                            tint = if (currentTrack.isFavorite) Mp3Accent else Mp3Muted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(onClick = { playPrevTrack() }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Sebelumnya", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(
                                        onClick = { toggleAudioPlayPause() },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    IconButton(onClick = { playNextTrack(autoTriggered = false) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.SkipNext, contentDescription = "Berikutnya", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { fullNowPlayingExpanded = true }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Filled.OpenInFull, contentDescription = "Detail Player", tint = Mp3Muted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Pengaturan MP3 Player") },
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
private fun Mp3TrackRow(
    track: MusicTrackEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = if (isCurrent) Color(0xFF281720) else Mp3Card,
        shape = RoundedCornerShape(10.dp),
        border = if (isCurrent) BorderStroke(1.dp, Mp3Accent.copy(alpha = 0.65f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isCurrent) Mp3Accent.copy(alpha = 0.25f) else Color(0xFF262632)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isCurrent && isPlaying) Icons.Filled.GraphicEq else Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = if (isCurrent) Mp3Accent else Mp3Ink,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = if (isCurrent) Mp3Accent else Mp3Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
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
                    color = Mp3Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(30.dp)) {
                Icon(
                    if (track.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorit",
                    tint = if (track.isFavorite) Mp3Accent else Mp3Muted,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Hapus dari daftar",
                    tint = Mp3Muted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun FullMusicNowPlayingView(
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
                    listOf(Color(0xFF280B14), Mp3Bg)
                )
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Kembali ke Daftar", tint = Mp3Ink)
            }
            Text("Sedang Diputar • ${track.volumeLabel}", color = Mp3Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Box {
                TextButton(onClick = onToggleSpeedMenu) {
                    Icon(Icons.Filled.Speed, contentDescription = null, tint = Mp3Ink, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${playbackSpeed}x", color = Mp3Ink, fontSize = 12.sp)
                }
                DropdownMenu(expanded = showSpeedMenu, onDismissRequest = onToggleSpeedMenu) {
                    listOf(0.75f, 1.0f, 1.15f, 1.25f, 1.5f).forEach { sp ->
                        DropdownMenuItem(text = { Text("${sp}x") }, onClick = { onSelectSpeed(sp) })
                    }
                }
            }
        }

        Spacer(Modifier.weight(0.4f))

        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Mp3Accent.copy(alpha = 0.78f), Color(0xFF241432))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(72.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = Mp3Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${track.artist} • ${track.album}",
                    color = Mp3Muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (track.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorit",
                    tint = if (track.isFavorite) Mp3Accent else Mp3Ink
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Slider(
            value = safePos.toFloat() / safeDur.toFloat(),
            onValueChange = { frac -> onSeek((frac * safeDur).toLong()) },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Mp3Accent,
                inactiveTrackColor = Color(0xFF3A3A48)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(Formatters.formatDurationMs(safePos), color = Mp3Muted, fontSize = 11.sp)
            Text(Formatters.formatDurationMs(safeDur), color = Mp3Muted, fontSize = 11.sp)
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Acak",
                    tint = if (shuffleEnabled) Mp3Accent else Mp3Muted
                )
            }
            IconButton(onClick = onPrev, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Sebelumnya", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onTogglePlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.Black,
                    modifier = Modifier.size(30.dp)
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Berikutnya", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(
                    if (repeatMode == MusicRepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Ulangi",
                    tint = if (repeatMode != MusicRepeatMode.OFF) Mp3Accent else Mp3Muted
                )
            }
        }

        Spacer(Modifier.weight(0.4f))
    }
}
