package com.offlinecyberdeck.desktop

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Eject
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinecyberdeck.CyberDeckApplication
import com.offlinecyberdeck.ai.AiChatActivity
import com.offlinecyberdeck.archive.ArchiveReaderScreen
import com.offlinecyberdeck.bookmarks.BookmarksScreen
import com.offlinecyberdeck.bookmarks.HistoryScreen
import com.offlinecyberdeck.browser.BrowserScreen
import com.offlinecyberdeck.browser.BrowserViewModel
import com.offlinecyberdeck.core.database.entity.AppSettingsEntity
import com.offlinecyberdeck.core.format.Formatters
import com.offlinecyberdeck.core.model.DesktopBackgroundPreset
import com.offlinecyberdeck.core.model.DownloadStatus
import com.offlinecyberdeck.core.model.LibraryFileType
import com.offlinecyberdeck.core.model.StorageTarget
import com.offlinecyberdeck.core.storage.ExternalVolumeInfo
import com.offlinecyberdeck.downloads.DownloadsScreen
import com.offlinecyberdeck.files.FileManagerScreen
import com.offlinecyberdeck.library.LibraryScreen
import com.offlinecyberdeck.library.OfflineWikiScreen
import com.offlinecyberdeck.library.PdfViewerScreen
import com.offlinecyberdeck.library.TextDocumentReaderScreen
import com.offlinecyberdeck.library.ZimViewerScreen
import com.offlinecyberdeck.media.MediaPlayerScreen
import com.offlinecyberdeck.media.MediaVaultScreen
import com.offlinecyberdeck.music.MusicPlayerScreen
import com.offlinecyberdeck.search.UnifiedSearchScreen
import com.offlinecyberdeck.settings.SettingsScreen
import com.offlinecyberdeck.ui.theme.DeckAmber
import com.offlinecyberdeck.ui.theme.DeckGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

enum class DesktopWindowKind(val defaultTitle: String) {
    BROWSER("Cyber Deck Browser"),
    LIBRARY("Knowledge Vault"),
    ARCHIVES("Web Archives"),
    MEDIA("OfflineTube"),
    MUSIC("Cyber Music"),
    FILES("File Explorer"),
    TASK_MANAGER("Task Manager"),
    DOWNLOADS("Downloads"),
    BOOKMARKS("Bookmarks"),
    HISTORY("History"),
    SEARCH("Offline Search"),
    OFFLINE_WIKI("Offline Wiki"),
    TERMINAL("PowerShell Terminal"),
    SETTINGS("Settings"),
    ARCHIVE_READER("Archive Reader"),
    PDF_READER("PDF Viewer"),
    TEXT_READER("Document Reader"),
    ZIM_READER("ZIM Encyclopedia"),
    MEDIA_PLAYER("Media Player")
}

private enum class TaskbarTrayPopup {
    NETWORK, BLUETOOTH, BATTERY, CLOCK, STORAGE
}

data class DesktopWindowState(
    val id: String = UUID.randomUUID().toString(),
    val kind: DesktopWindowKind,
    val title: String = kind.defaultTitle,
    val refId: String = "",
    val isMinimized: Boolean = false,
    val isMaximized: Boolean = false,
    val xDp: Float = 40f,
    val yDp: Float = 20f,
    val widthDp: Float = 760f,
    val heightDp: Float = 520f,
    val zIndex: Int = 1
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DesktopLauncherScreen(
    app: CyberDeckApplication,
    browserViewModel: BrowserViewModel,
    settings: AppSettingsEntity,
    onShowPermissionsDialog: () -> Unit,
    onShowMessage: (String) -> Unit,
    onRegisterToggleTerminal: ((() -> Unit)?) -> Unit = {},
    onRegisterActiveBackHandler: ((() -> Boolean)?) -> Unit = {},
    onTextInputFocusChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val isOnline by browserViewModel.isOnline.collectAsStateWithLifecycle()

    val windows = remember { mutableStateListOf<DesktopWindowState>() }
    var topZ by remember { mutableIntStateOf(10) }
    var appDrawerOpen by remember { mutableStateOf(false) }
    var notifPanelOpen by remember { mutableStateOf(false) }
    var launchExternalFloating by remember { mutableStateOf(true) }
    var externalCascadeIndex by remember { mutableIntStateOf(0) }

    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var appSearchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var permStatus by remember { mutableStateOf(DesktopLauncherManager.inspectPermissions(context)) }
    var bluetoothStatus by remember { mutableStateOf(DesktopLauncherManager.readBluetoothStatus(context)) }
    var clockText by remember { mutableStateOf("") }
    var clockSecondsText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var fullDateText by remember { mutableStateOf("") }
    var batteryStatus by remember { mutableStateOf(DesktopLauncherManager.readBatteryStatus(context)) }
    var systemUsage by remember { mutableStateOf(DesktopLauncherManager.readSystemUsage(context)) }
    val systemNotifications by NotificationCenterStore.notifications.collectAsStateWithLifecycle()
    val downloads by app.database.downloadDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val driveStatus by app.driveManager.status.collectAsStateWithLifecycle()
    val desktopIconSizeDp by app.settingsRepository.desktopIconSizeDpFlow.collectAsStateWithLifecycle(initialValue = 46)
    val desktopIconPositions by app.settingsRepository.desktopIconPositionsFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val desktopSnapToGrid by app.settingsRepository.desktopSnapToGridFlow.collectAsStateWithLifecycle(initialValue = true)
    val terminalCustomCommands by app.settingsRepository.terminalCustomCommandsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var isEditingDesktopIcons by remember { mutableStateOf(false) }
    var mountedPartitions by remember { mutableStateOf(app.driveManager.detectAllMountedRemovablePartitions()) }
    var isGestureNavEnabled by remember { mutableStateOf(DesktopLauncherManager.isGestureNavigationEnabled(context)) }
    var isBlinkingNetIndicator by remember { mutableStateOf(false) }

    var taskbarVisible by remember(settings.taskbarAutoHide) { mutableStateOf(!settings.taskbarAutoHide) }
    var activeTrayPopup by remember { mutableStateOf<TaskbarTrayPopup?>(null) }
    var windowMenuId by remember { mutableStateOf<String?>(null) }
    var showSavedHubDialog by remember { mutableStateOf(false) }
    var showOfflineHubDialog by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var pendingWallpaperSlot by remember { mutableIntStateOf(0) }
    var floatingNotif by remember { mutableStateOf<DeckNotificationItem?>(null) }
    var filesGoBackHandler by remember { mutableStateOf<(() -> Boolean)?>(null) }
    val notifSoundEnabledState by rememberUpdatedState(settings.notificationSoundEnabled)
    val launchAiOfflineActivity: () -> Unit = {
        runCatching {
            context.startActivity(Intent(context, AiChatActivity::class.java))
        }.onFailure { err ->
            onShowMessage("Gagal membuka AI Offline: ${err.message}")
        }
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        bluetoothStatus = DesktopLauncherManager.readBluetoothStatus(context)
    }

    LaunchedEffect(driveStatus) {
        mountedPartitions = app.driveManager.detectAllMountedRemovablePartitions()
    }

    fun refreshAfterStorageChange() {
        mountedPartitions = app.driveManager.detectAllMountedRemovablePartitions()
        val extraSafList = settings.extraMediaSafUris
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        scope.launch {
            app.driveManager.refreshStatus(settings.deckDriveTreeUri)
            app.musicRepository.autoImportAndRefresh(
                deckDriveTreeUri = settings.deckDriveTreeUri,
                extraSafUris = extraSafList
            )
            app.mediaRepository.autoImportAndRefreshVideos(
                deckDriveTreeUri = settings.deckDriveTreeUri,
                extraSafUris = extraSafList
            )
            app.vaultRepository.autoImportFromConfiguredSources(settings.deckDriveTreeUri)
        }
    }

    val customWallpaperPaths = remember(settings.customWallpaperPaths) {
        settings.customWallpaperPaths
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(4)
    }
    var activeWallpaperBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(settings.activeCustomWallpaperIndex, customWallpaperPaths) {
        val idx = settings.activeCustomWallpaperIndex
        val path = if (idx in customWallpaperPaths.indices) customWallpaperPaths[idx] else null
        activeWallpaperBitmap = if (path != null) {
            withContext(Dispatchers.IO) {
                val f = File(path)
                if (f.exists() && f.canRead()) {
                    runCatching { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }.getOrNull()
                } else null
            }
        } else null
    }

    val wallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                app.driveManager.copyAndResizeWallpaper(uri)
                    .onSuccess { savedPath ->
                        val nextList = customWallpaperPaths.toMutableList()
                        val targetSlot = pendingWallpaperSlot.coerceIn(0, 3)
                        if (targetSlot < nextList.size) {
                            nextList[targetSlot] = savedPath
                        } else {
                            nextList.add(savedPath)
                        }
                        val trimmed = nextList.take(4)
                        val activeIdx = if (targetSlot < trimmed.size) targetSlot else trimmed.lastIndex
                        app.settingsRepository.updateSettings {
                            it.copy(
                                customWallpaperPaths = trimmed.joinToString("\n"),
                                activeCustomWallpaperIndex = activeIdx
                            )
                        }
                        onShowMessage("Wallpaper #${activeIdx + 1} berhasil dipasang.")
                    }
                    .onFailure { err ->
                        onShowMessage("Gagal memuat wallpaper: ${err.message}")
                    }
            }
        }
    }

    // Listen for incoming notifications -> play sound & show 2-second floating banner
    LaunchedEffect(Unit) {
        NotificationCenterStore.newNotificationEvents.collect { item ->
            NotificationCenterStore.playNotificationSound(
                context = context,
                enabled = notifSoundEnabledState
            )
            floatingNotif = item
        }
    }

    // Auto-dismiss floating notification banner after 2 seconds
    LaunchedEffect(floatingNotif?.key, floatingNotif?.postedAtMs) {
        val current = floatingNotif ?: return@LaunchedEffect
        delay(2000L)
        if (floatingNotif?.key == current.key && floatingNotif?.postedAtMs == current.postedAtMs) {
            floatingNotif = null
        }
    }

    // Load installed apps cleanly on startup without auto-opening Cyber Deck Browser
    LaunchedEffect(Unit) {
        installedApps = DesktopLauncherManager.loadInstalledApps(context)
    }

    // Check online status every 10 minutes and blink indicator for 10 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(600_000L)
            app.networkMonitor.refresh()
            isBlinkingNetIndicator = true
            delay(10_000L)
            isBlinkingNetIndicator = false
        }
    }

    // Refresh permissions, storage partitions, navigation mode & installed apps on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permStatus = DesktopLauncherManager.inspectPermissions(context)
                bluetoothStatus = DesktopLauncherManager.readBluetoothStatus(context)
                batteryStatus = DesktopLauncherManager.readBatteryStatus(context)
                systemUsage = DesktopLauncherManager.readSystemUsage(context)
                mountedPartitions = app.driveManager.detectAllMountedRemovablePartitions()
                isGestureNavEnabled = DesktopLauncherManager.isGestureNavigationEnabled(context)
                scope.launch {
                    installedApps = DesktopLauncherManager.loadInstalledApps(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Live clock, date, battery & RAM/CPU status for fullscreen taskbar & desktop
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            val now = Date()
            clockText = DateFormat.format("HH:mm", now).toString()
            clockSecondsText = DateFormat.format("HH:mm:ss", now).toString()
            dateText = DateFormat.format("dd MMM", now).toString()
            fullDateText = java.text.SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(now)
            if (tick % 2 == 0) {
                systemUsage = DesktopLauncherManager.readSystemUsage(context)
            }
            if (tick % 10 == 0) {
                batteryStatus = DesktopLauncherManager.readBatteryStatus(context)
            }
            tick++
            delay(1_000L)
        }
    }

    val pinnedPackages = remember(settings.pinnedExternalPackages) {
        settings.pinnedExternalPackages
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun bringToFront(winId: String) {
        val idx = windows.indexOfFirst { it.id == winId }
        if (idx >= 0) {
            windows[idx] = windows[idx].copy(isMinimized = false, zIndex = ++topZ)
        }
    }

    fun openOrFocusWindow(
        kind: DesktopWindowKind,
        refId: String = "",
        title: String = kind.defaultTitle,
        startMaximized: Boolean = false
    ) {
        val existingIdx = windows.indexOfFirst {
            it.kind == kind && (refId.isBlank() || it.refId == refId)
        }
        if (existingIdx >= 0) {
            windows[existingIdx] = windows[existingIdx].copy(
                title = title,
                isMinimized = false,
                zIndex = ++topZ
            )
            appDrawerOpen = false
            return
        }

        // Enforce 4-window memory cap for 4 GB RAM tablets (close oldest non-browser window if > 3)
        if (windows.size >= 4) {
            val evictIdx = windows.indexOfFirst { it.kind != DesktopWindowKind.BROWSER }
            if (evictIdx >= 0) windows.removeAt(evictIdx)
        }

        val cascade = (windows.size % 4) * 28f
        windows += DesktopWindowState(
            id = if (kind == DesktopWindowKind.BROWSER) "main_browser" else UUID.randomUUID().toString(),
            kind = kind,
            title = title,
            refId = refId,
            isMinimized = false,
            isMaximized = startMaximized,
            xDp = 56f + cascade,
            yDp = 24f + cascade,
            widthDp = 780f,
            heightDp = 500f,
            zIndex = ++topZ
        )
        appDrawerOpen = false
    }

    fun toggleMinimize(winId: String) {
        val idx = windows.indexOfFirst { it.id == winId }
        if (idx >= 0) {
            val w = windows[idx]
            if (w.isMinimized) {
                windows[idx] = w.copy(isMinimized = false, zIndex = ++topZ)
            } else {
                windows[idx] = w.copy(isMinimized = true)
            }
        }
    }

    fun toggleMaximize(winId: String) {
        val idx = windows.indexOfFirst { it.id == winId }
        if (idx >= 0) {
            val w = windows[idx]
            windows[idx] = w.copy(isMaximized = !w.isMaximized, isMinimized = false, zIndex = ++topZ)
        }
    }

    fun closeWindow(winId: String) {
        windows.removeAll { it.id == winId }
    }

    fun minimizeAllWindows() {
        for (i in windows.indices) {
            if (!windows[i].isMinimized) {
                windows[i] = windows[i].copy(isMinimized = true)
            }
        }
        appDrawerOpen = false
        notifPanelOpen = false
    }

    fun togglePinExternalApp(pkg: String) {
        scope.launch {
            val next = if (pkg in pinnedPackages) {
                pinnedPackages - pkg
            } else {
                pinnedPackages + pkg
            }
            app.settingsRepository.updateSettings {
                it.copy(pinnedExternalPackages = next.joinToString(","))
            }
            onShowMessage(
                if (pkg in next) "Aplikasi disematkan ke Desktop & Taskbar"
                else "Pin desktop dilepas"
            )
        }
    }

    fun launchExternalPackage(pkg: String) {
        DesktopLauncherManager.launchExternalApp(
            context = context,
            packageName = pkg,
            floating = launchExternalFloating,
            cascadeIndex = externalCascadeIndex++
        ).onSuccess { msg ->
            DesktopLauncherManager.recordAppLaunched(pkg)
            appDrawerOpen = false
            onShowMessage(msg)
        }.onFailure { err ->
            onShowMessage("Gagal membuka app: ${err.message}")
        }
    }

    val topFrontWinId = windows.filter { !it.isMinimized }.maxByOrNull { it.zIndex }?.id
    val desktopOverlayBlocking = appDrawerOpen ||
        notifPanelOpen ||
        activeTrayPopup != null ||
        showOfflineHubDialog ||
        showSavedHubDialog ||
        showWallpaperDialog

    LaunchedEffect(topFrontWinId, desktopOverlayBlocking) {
        focusManager.clearFocus(force = true)
        onTextInputFocusChanged(false)
    }

    LaunchedEffect(appDrawerOpen) {
        if (!appDrawerOpen) {
            onTextInputFocusChanged(false)
        }
    }

    DisposableEffect(Unit) {
        onRegisterToggleTerminal {
            val termWin = windows.firstOrNull { it.kind == DesktopWindowKind.TERMINAL }
            if (termWin != null && !termWin.isMinimized) {
                closeWindow(termWin.id)
            } else {
                activeTrayPopup = null
                notifPanelOpen = false
                appDrawerOpen = false
                showOfflineHubDialog = false
                showSavedHubDialog = false
                showWallpaperDialog = false
                openOrFocusWindow(DesktopWindowKind.TERMINAL)
            }
        }
        onRegisterActiveBackHandler {
            val topWin = windows.filter { !it.isMinimized }.maxByOrNull { it.zIndex }
            if (topWin?.kind == DesktopWindowKind.FILES && !desktopOverlayBlocking) {
                filesGoBackHandler?.invoke() ?: false
            } else {
                false
            }
        }
        onDispose {
            onRegisterToggleTerminal(null)
            onRegisterActiveBackHandler(null)
            onTextInputFocusChanged(false)
        }
    }

    val openUrlInDesktopBrowser: (String, Boolean) -> Unit = { url, incognito ->
        openOrFocusWindow(DesktopWindowKind.BROWSER, startMaximized = false)
        if (incognito) {
            browserViewModel.createNewTab(url = url, isIncognito = true)
        } else {
            browserViewModel.navigateFromInput(url)
        }
    }

    val openLibraryItemById: (String) -> Unit = { itemId ->
        scope.launch {
            val item = app.database.libraryDao().getById(itemId)
            if (item == null) {
                onShowMessage("Item tidak ditemukan.")
            } else if (!app.vaultRepository.isItemAvailable(item)) {
                onShowMessage("File tidak tersedia — cek koneksi Deck Drive.")
            } else {
                when (item.fileType) {
                    LibraryFileType.PDF -> openOrFocusWindow(DesktopWindowKind.PDF_READER, item.id, item.title)
                    LibraryFileType.ZIM -> openOrFocusWindow(DesktopWindowKind.ZIM_READER, item.id, item.title)
                    LibraryFileType.EPUB,
                    LibraryFileType.HTML,
                    LibraryFileType.TEXT -> openOrFocusWindow(DesktopWindowKind.TEXT_READER, item.id, item.title)
                    else -> {
                        val intent = app.vaultRepository.buildExternalViewIntent(item)
                        if (intent != null) {
                            runCatching { context.startActivity(intent) }
                                .onFailure { onShowMessage("Tidak ada aplikasi untuk format ${item.fileType.label}") }
                        }
                    }
                }
            }
        }
    }

    BackHandler(enabled = appDrawerOpen || notifPanelOpen || activeTrayPopup != null || windows.any { !it.isMinimized }) {
        when {
            activeTrayPopup != null -> activeTrayPopup = null
            notifPanelOpen -> notifPanelOpen = false
            appDrawerOpen -> appDrawerOpen = false
            else -> {
                val topWin = windows.filter { !it.isMinimized }.maxByOrNull { it.zIndex }
                if (topWin != null) {
                    toggleMinimize(topWin.id)
                }
            }
        }
    }

    val activeBgPreset = remember(settings.desktopBackgroundId) {
        DesktopBackgroundPreset.fromId(settings.desktopBackgroundId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(activeBgPreset.topColorArgb),
                        Color(activeBgPreset.midColorArgb),
                        Color(activeBgPreset.bottomColorArgb)
                    )
                )
            )
    ) {
        val customBmp = activeWallpaperBitmap
        if (customBmp != null) {
            Image(
                bitmap = customBmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // DESKTOP WORKSPACE AREA
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val deskWidthDp = maxWidth.value
                val deskHeightDp = maxHeight.value
                val density = LocalDensity.current

                // Desktop Shortcuts Grid (always underneath windows)
                val builtInShortcuts = listOf(
                    Triple(DesktopWindowKind.BROWSER, "Cyber Browser", Icons.Filled.Public),
                    Triple(DesktopWindowKind.LIBRARY, "Knowledge Vault", Icons.Filled.MenuBook),
                    Triple(DesktopWindowKind.MUSIC, "Cyber Music", Icons.Filled.LibraryMusic),
                    Triple(DesktopWindowKind.FILES, "File Explorer", Icons.Filled.Folder),
                    Triple(DesktopWindowKind.TASK_MANAGER, "Task Manager", Icons.Filled.Speed),
                    Triple(DesktopWindowKind.TERMINAL, "Terminal", Icons.Filled.Terminal),
                    Triple(DesktopWindowKind.SETTINGS, "Settings", Icons.Filled.Settings)
                )

                val pinnedInfos = remember(installedApps, pinnedPackages) {
                    pinnedPackages.mapNotNull { pkg -> installedApps.firstOrNull { it.packageName == pkg } }
                }

                val isLightDesktop = customBmp == null && activeBgPreset.isLight
                val desktopTextColor = if (isLightDesktop) Color(0xFF0F172A) else Color.White

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "net_indicator_blink")
                        val blinkAlpha by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 0.2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 450),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "net_blink_alpha"
                        )
                        val dotAlpha = if (isBlinkingNetIndicator) blinkAlpha else 1f
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        (if (isOnline) DeckGreen else Color(0xFFEF4444)).copy(alpha = dotAlpha)
                                    )
                            )
                            Text(
                                text = "CYBER DECK DESKTOP",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isLightDesktop) Color(0xFF0E7490) else MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Live RAM, CPU & Temp Usage Pill + Desktop Icon Customization Toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isEditingDesktopIcons) {
                                Surface(
                                    color = Color(0xFF0B1220).copy(alpha = 0.88f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (desktopSnapToGrid) DeckGreen.copy(alpha = 0.7f) else Color(0xFF64748B)
                                    ),
                                    modifier = Modifier.clickable {
                                        app.settingsRepository.setDesktopSnapToGrid(!desktopSnapToGrid)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.GridView,
                                            contentDescription = "Snap Grid",
                                            tint = if (desktopSnapToGrid) DeckGreen else Color(0xFF94A3B8),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (desktopSnapToGrid) "Snap: ON" else "Bebas",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                Surface(
                                    color = Color(0xFF0B1220).copy(alpha = 0.88f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, DeckAmber.copy(alpha = 0.65f)),
                                    modifier = Modifier.clickable {
                                        app.settingsRepository.resetDesktopIconPositions()
                                        onShowMessage("Posisi ikon desktop dirapikan ke grid awal.")
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Refresh,
                                            contentDescription = "Rapikan Ikon",
                                            tint = DeckAmber,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Rapikan",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            Surface(
                                color = if (isEditingDesktopIcons) DeckGreen.copy(alpha = 0.24f) else Color(0xFF0B1220).copy(alpha = 0.78f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isEditingDesktopIcons) DeckGreen else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.clickable {
                                    isEditingDesktopIcons = !isEditingDesktopIcons
                                    if (isEditingDesktopIcons) {
                                        onShowMessage("Mode Atur Ikon aktif — geser ikon ke posisi yang diinginkan.")
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        if (isEditingDesktopIcons) Icons.Filled.CheckCircle else Icons.Filled.Edit,
                                        contentDescription = "Atur Ikon Desktop",
                                        tint = if (isEditingDesktopIcons) DeckGreen else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isEditingDesktopIcons) "Selesai" else "Atur Ikon",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            val tempSuffix = systemUsage.cpuTempC?.let {
                                String.format(Locale.US, " • %.0f°C", it)
                            }.orEmpty()
                            Surface(
                                color = Color(0xFF0B1220).copy(alpha = 0.82f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        systemUsage.ramPercent >= 85 -> Color(0xFFEF4444).copy(alpha = 0.7f)
                                        systemUsage.ramPercent >= 70 -> DeckAmber.copy(alpha = 0.65f)
                                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                    }
                                ),
                                modifier = Modifier.clickable { openOrFocusWindow(DesktopWindowKind.TASK_MANAGER) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Memory,
                                        contentDescription = "Task Manager",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "RAM ${systemUsage.ramPercent}% (${Formatters.formatBytes(systemUsage.ramUsedMb * 1024L * 1024L)}/${Formatters.formatBytes(systemUsage.ramTotalMb * 1024L * 1024L)}) • CPU ${systemUsage.cpuPercent}%$tempSuffix",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    val iconBoxSize = desktopIconSizeDp.coerceIn(36, 68)
                    val innerIconSize = (iconBoxSize * 0.56f).roundToInt().coerceIn(20, 40)
                    val tileWidthDp = (iconBoxSize + 44).toFloat()
                    val tileHeightDp = (iconBoxSize + 48).toFloat()
                    val cellStepX = tileWidthDp + 12f
                    val cellStepY = tileHeightDp + 12f

                    val desktopEntries = buildList<Pair<String, Triple<String, () -> Unit, @Composable () -> Unit>>> {
                        builtInShortcuts.forEach { (kind, label, icon) ->
                            add(
                                "builtin_${kind.name}" to Triple(
                                    label,
                                    { openOrFocusWindow(kind) },
                                    {
                                        Box(
                                            modifier = Modifier
                                                .size(iconBoxSize.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                icon,
                                                contentDescription = label,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(innerIconSize.dp)
                                            )
                                        }
                                    }
                                )
                            )
                        }
                        add(
                            "unified_offline_hub" to Triple(
                                "Offline",
                                { showOfflineHubDialog = true },
                                {
                                    Box(
                                        modifier = Modifier
                                            .size(iconBoxSize.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF0EA5E9).copy(alpha = 0.22f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.CloudOff,
                                            contentDescription = "Offline Wiki, OfflineTube, AI Offline & Offline Search",
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(innerIconSize.dp)
                                        )
                                    }
                                }
                            )
                        )
                        add(
                            "unified_saved_downloads" to Triple(
                                "Tersimpan & Unduhan",
                                { showSavedHubDialog = true },
                                {
                                    Box(
                                        modifier = Modifier
                                            .size(iconBoxSize.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(DeckGreen.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Bookmarks,
                                            contentDescription = "Bookmarks, History & Downloads",
                                            tint = DeckGreen,
                                            modifier = Modifier.size(innerIconSize.dp)
                                        )
                                    }
                                }
                            )
                        )
                        add(
                            "wallpaper_selector" to Triple(
                                "Wallpaper & Latar",
                                { showWallpaperDialog = true },
                                {
                                    Box(
                                        modifier = Modifier
                                            .size(iconBoxSize.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFFA855F7).copy(alpha = 0.22f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Wallpaper,
                                            contentDescription = "Wallpaper & Latar",
                                            tint = Color(0xFFC084FC),
                                            modifier = Modifier.size(innerIconSize.dp)
                                        )
                                    }
                                }
                            )
                        )
                        pinnedInfos.forEach { extApp ->
                            add(
                                "pin_${extApp.packageName}" to Triple(
                                    extApp.label,
                                    { launchExternalPackage(extApp.packageName) },
                                    {
                                        InstalledAppIcon(
                                            packageName = extApp.packageName,
                                            modifier = Modifier.size(iconBoxSize.dp)
                                        )
                                    }
                                )
                            )
                        }
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .focusProperties { canFocus = (topFrontWinId == null && !desktopOverlayBlocking) }
                            .onPreviewKeyEvent { topFrontWinId != null || desktopOverlayBlocking }
                    ) {
                        val canvasWidthDp = maxWidth.value
                        val canvasHeightDp = maxHeight.value
                        val rowsPerCol = ((canvasHeightDp / cellStepY).toInt()).coerceAtLeast(1)
                        val maxX = (canvasWidthDp - tileWidthDp).coerceAtLeast(0f)
                        val maxY = (canvasHeightDp - tileHeightDp).coerceAtLeast(0f)

                        val liveDragPos = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
                        var activeDragId by remember { mutableStateOf<String?>(null) }

                        LaunchedEffect(desktopIconPositions) {
                            liveDragPos.clear()
                            desktopIconPositions.forEach { (id, pos) ->
                                liveDragPos[id] = pos.xDp to pos.yDp
                            }
                        }

                        desktopEntries.forEachIndexed { idx, (id, meta) ->
                            val (label, onTileClick, iconContent) = meta
                            val col = idx / rowsPerCol
                            val row = idx % rowsPerCol
                            val defaultX = (col * cellStepX).coerceIn(0f, maxX)
                            val defaultY = (row * cellStepY).coerceIn(0f, maxY)
                            val currentPos = liveDragPos[id]
                                ?: desktopIconPositions[id]?.let { it.xDp to it.yDp }
                                ?: (defaultX to defaultY)
                            val posX = currentPos.first.coerceIn(0f, maxX)
                            val posY = currentPos.second.coerceIn(0f, maxY)
                            val isBeingDragged = activeDragId == id

                            fun commitDraggedPosition(rawX: Float, rawY: Float) {
                                val finalX = if (desktopSnapToGrid) {
                                    ((rawX / cellStepX).roundToInt() * cellStepX).coerceIn(0f, maxX)
                                } else {
                                    rawX.coerceIn(0f, maxX)
                                }
                                val finalY = if (desktopSnapToGrid) {
                                    ((rawY / cellStepY).roundToInt() * cellStepY).coerceIn(0f, maxY)
                                } else {
                                    rawY.coerceIn(0f, maxY)
                                }
                                liveDragPos[id] = finalX to finalY
                                app.settingsRepository.saveDesktopIconPosition(id, finalX, finalY)
                            }

                            key(id) {
                                val dragModifier = if (isEditingDesktopIcons) {
                                    Modifier.pointerInput(id, desktopSnapToGrid, maxX, maxY) {
                                        detectDragGestures(
                                            onDragStart = {
                                                activeDragId = id
                                                if (!liveDragPos.containsKey(id)) {
                                                    liveDragPos[id] = posX to posY
                                                }
                                            },
                                            onDragEnd = {
                                                val p = liveDragPos[id] ?: (posX to posY)
                                                commitDraggedPosition(p.first, p.second)
                                                activeDragId = null
                                            },
                                            onDragCancel = {
                                                activeDragId = null
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val cur = liveDragPos[id] ?: (posX to posY)
                                                val nx = (cur.first + dragAmount.x / density.density).coerceIn(0f, maxX)
                                                val ny = (cur.second + dragAmount.y / density.density).coerceIn(0f, maxY)
                                                liveDragPos[id] = nx to ny
                                            }
                                        )
                                    }
                                } else {
                                    Modifier.pointerInput(id, desktopSnapToGrid, maxX, maxY) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                activeDragId = id
                                                if (!liveDragPos.containsKey(id)) {
                                                    liveDragPos[id] = posX to posY
                                                }
                                            },
                                            onDragEnd = {
                                                val p = liveDragPos[id] ?: (posX to posY)
                                                commitDraggedPosition(p.first, p.second)
                                                activeDragId = null
                                            },
                                            onDragCancel = {
                                                activeDragId = null
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val cur = liveDragPos[id] ?: (posX to posY)
                                                val nx = (cur.first + dragAmount.x / density.density).coerceIn(0f, maxX)
                                                val ny = (cur.second + dragAmount.y / density.density).coerceIn(0f, maxY)
                                                liveDragPos[id] = nx to ny
                                            }
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .offset {
                                            with(density) {
                                                IntOffset(posX.dp.roundToPx(), posY.dp.roundToPx())
                                            }
                                        }
                                        .width(tileWidthDp.dp)
                                        .zIndex(if (isBeingDragged) 10f else 1f)
                                        .then(
                                            if (isBeingDragged || isEditingDesktopIcons) {
                                                Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .border(
                                                        width = if (isBeingDragged) 1.5.dp else 1.dp,
                                                        color = if (isBeingDragged) DeckGreen
                                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                                        shape = RoundedCornerShape(10.dp)
                                                    )
                                                    .background(
                                                        Color.Black.copy(alpha = if (isBeingDragged) 0.45f else 0.18f)
                                                    )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .then(dragModifier)
                                ) {
                                    DesktopShortcutTile(
                                        label = label,
                                        labelColor = desktopTextColor,
                                        onClick = {
                                            if (isEditingDesktopIcons) {
                                                onShowMessage("Tahan & geser '$label' untuk memindahkan posisinya.")
                                            } else {
                                                onTileClick()
                                            }
                                        },
                                        iconContent = iconContent
                                    )
                                }
                            }
                        }
                    }
                }

            // OPEN INTERNAL FLOATING WINDOWS
            val sortedWindows = windows.sortedBy { it.zIndex }
            sortedWindows.forEach { win ->
                if (!win.isMinimized) {
                    key(win.id) {
                        val isTopFront = win.id == topFrontWinId && !desktopOverlayBlocking
                        val clampedW = win.widthDp.coerceIn(300f, deskWidthDp.coerceAtLeast(320f))
                        val clampedH = win.heightDp.coerceIn(240f, deskHeightDp.coerceAtLeast(260f))
                        val clampedX = win.xDp.coerceIn(0f, (deskWidthDp - clampedW).coerceAtLeast(0f))
                        val clampedY = win.yDp.coerceIn(0f, (deskHeightDp - clampedH).coerceAtLeast(0f))

                        val baseWindowModifier = if (win.isMaximized) {
                            Modifier
                                .fillMaxSize()
                                .zIndex(win.zIndex.toFloat())
                        } else {
                            Modifier
                                .offset {
                                    with(density) {
                                        IntOffset(clampedX.dp.roundToPx(), clampedY.dp.roundToPx())
                                    }
                                }
                                .width(clampedW.dp)
                                .height(clampedH.dp)
                                .zIndex(win.zIndex.toFloat())
                        }
                        val windowModifier = baseWindowModifier.pointerInput(win.id) {
                            awaitPointerEventScope {
                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    if (ev.type == PointerEventType.Press) {
                                        bringToFront(win.id)
                                    }
                                }
                            }
                        }

                        Surface(
                            shape = if (win.isMaximized) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.background,
                            shadowElevation = if (win.isMaximized) 0.dp else if (isTopFront) 16.dp else 6.dp,
                            border = if (win.isMaximized) null else BorderStroke(
                                if (isTopFront) 1.5.dp else 1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isTopFront) 0.9f else 0.25f)
                            ),
                            modifier = windowModifier
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // DeX-style Window Title Bar (Draggable + Minimize / Maximize / Close)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface)
                                            .pointerInput(win.id, win.isMaximized) {
                                                detectDragGestures(
                                                    onDragStart = { bringToFront(win.id) },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        if (!win.isMaximized) {
                                                            val idx = windows.indexOfFirst { it.id == win.id }
                                                            if (idx >= 0) {
                                                                val cur = windows[idx]
                                                                val dxDp = dragAmount.x / density.density
                                                                val dyDp = dragAmount.y / density.density
                                                                windows[idx] = cur.copy(
                                                                    xDp = (cur.xDp + dxDp).coerceIn(
                                                                        0f,
                                                                        (deskWidthDp - cur.widthDp).coerceAtLeast(0f)
                                                                    ),
                                                                    yDp = (cur.yDp + dyDp).coerceIn(
                                                                        0f,
                                                                        (deskHeightDp - cur.heightDp).coerceAtLeast(0f)
                                                                    )
                                                                )
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                            .clickable { bringToFront(win.id) }
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = windowIconForKind(win.kind),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = win.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        // Minimize
                                        IconButton(
                                            onClick = { toggleMinimize(win.id) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Remove,
                                                contentDescription = "Minimize",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        // Maximize / Restore
                                        IconButton(
                                            onClick = { toggleMaximize(win.id) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                if (win.isMaximized) Icons.Filled.FilterNone else Icons.Filled.CropSquare,
                                                contentDescription = if (win.isMaximized) "Restore" else "Maximize",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        // Close
                                        IconButton(
                                            onClick = { closeWindow(win.id) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "Close",
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                                    // Window Content (keyboard input & focus strictly restricted to frontmost window)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .focusProperties { canFocus = isTopFront }
                                            .onPreviewKeyEvent { !isTopFront }
                                    ) {
                                        when (win.kind) {
                                            DesktopWindowKind.BROWSER -> {
                                                BrowserScreen(
                                                    viewModel = browserViewModel,
                                                    onOpenDownloads = { openOrFocusWindow(DesktopWindowKind.DOWNLOADS) },
                                                    onOpenUnifiedSearch = { openOrFocusWindow(DesktopWindowKind.SEARCH) },
                                                    onOpenArchives = { openOrFocusWindow(DesktopWindowKind.ARCHIVES) },
                                                    onOpenBookmarks = { openOrFocusWindow(DesktopWindowKind.BOOKMARKS) },
                                                    onOpenHistory = { openOrFocusWindow(DesktopWindowKind.HISTORY) },
                                                    onOpenSettings = { openOrFocusWindow(DesktopWindowKind.SETTINGS) },
                                                    onReturnToLauncher = { toggleMinimize(win.id) },
                                                    onShowMessage = onShowMessage
                                                )
                                            }
                                            DesktopWindowKind.LIBRARY -> {
                                                LibraryScreen(
                                                    app = app,
                                                    initialTabIndex = 0,
                                                    onOpenArchiveReader = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.ARCHIVE_READER, id, "Archive Reader")
                                                    },
                                                    onOpenPdfReader = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.PDF_READER, id, "PDF Viewer")
                                                    },
                                                    onOpenTextReader = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.TEXT_READER, id, "Document Reader")
                                                    },
                                                    onOpenZimReader = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.ZIM_READER, id, "ZIM Reader")
                                                    },
                                                    onOpenBrowserUrl = { url -> openUrlInDesktopBrowser(url, false) },
                                                    onShowMessage = onShowMessage
                                                )
                                            }
                                            DesktopWindowKind.ARCHIVES -> {
                                                LibraryScreen(
                                                    app = app,
                                                    initialTabIndex = 1,
                                                    onOpenArchiveReader = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.ARCHIVE_READER, id, "Archive Reader")
                                                    },
                                                    onOpenPdfReader = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.PDF_READER, id, "PDF Viewer")
                                                    },
                                                    onOpenTextReader = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.TEXT_READER, id, "Document Reader")
                                                    },
                                                    onOpenZimReader = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.ZIM_READER, id, "ZIM Reader")
                                                    },
                                                    onOpenBrowserUrl = { url -> openUrlInDesktopBrowser(url, false) },
                                                    onShowMessage = onShowMessage
                                                )
                                            }
                                            DesktopWindowKind.MEDIA -> {
                                                MediaVaultScreen(
                                                    app = app,
                                                    onPlayMedia = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.MEDIA_PLAYER, id, "OfflineTube Player")
                                                    },
                                                    onShowMessage = onShowMessage
                                                )
                                            }
                                            DesktopWindowKind.MUSIC -> {
                                                MusicPlayerScreen(
                                                    app = app,
                                                    onShowMessage = onShowMessage
                                                )
                                            }
                                            DesktopWindowKind.FILES -> {
                                                DisposableEffect(win.id) {
                                                    onDispose { filesGoBackHandler = null }
                                                }
                                                FileManagerScreen(
                                                    app = app,
                                                    onOpenZimPathOrUri = { ref ->
                                                        scope.launch {
                                                            app.vaultRepository.importOrFindZim(ref)
                                                                .onSuccess { item ->
                                                                    openOrFocusWindow(
                                                                        DesktopWindowKind.ZIM_READER,
                                                                        item.id,
                                                                        item.title
                                                                    )
                                                                }
                                                                .onFailure { err ->
                                                                    onShowMessage("Gagal membuka ZIM: ${err.message}")
                                                                }
                                                        }
                                                    },
                                                    onRegisterGoBackHandler = { handler -> filesGoBackHandler = handler },
                                                    isWindowFocused = isTopFront,
                                                    onTextInputFocusChanged = onTextInputFocusChanged,
                                                    onShowMessage = onShowMessage
                                                )
                                            }
                                            DesktopWindowKind.TASK_MANAGER -> {
                                                TaskManagerScreen(
                                                    app = app,
                                                    activeDesktopWindowsCount = windows.size,
                                                    activeWebViewCount = browserViewModel.webViewPool.getLiveCount(),
                                                    openWindows = windows.toList(),
                                                    onFocusWindow = { targetId -> bringToFront(targetId) },
                                                    onCloseWindow = { targetId -> closeWindow(targetId) },
                                                    onTrimAppRam = { browserViewModel.webViewPool.clearAllWebViewCaches() },
                                                    onShowMessage = onShowMessage
                                                )
                                            }
                                            DesktopWindowKind.DOWNLOADS -> {
                                                DownloadsScreen(app = app, onShowMessage = onShowMessage)
                                            }
                                            DesktopWindowKind.BOOKMARKS -> {
                                                BookmarksScreen(
                                                    app = app,
                                                    onOpenUrl = openUrlInDesktopBrowser
                                                )
                                            }
                                            DesktopWindowKind.HISTORY -> {
                                                HistoryScreen(
                                                    app = app,
                                                    onOpenUrl = { url -> openUrlInDesktopBrowser(url, false) }
                                                )
                                            }
                                            DesktopWindowKind.SEARCH -> {
                                                UnifiedSearchScreen(
                                                    app = app,
                                                    onOpenArchive = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.ARCHIVE_READER, id, "Archive Reader")
                                                    },
                                                    onOpenLibraryItem = openLibraryItemById,
                                                    onOpenMediaItem = { id ->
                                                        openOrFocusWindow(DesktopWindowKind.MEDIA_PLAYER, id, "OfflineTube Player")
                                                    },
                                                    onOpenUrl = { url -> openUrlInDesktopBrowser(url, false) },
                                                    onOpenFileExplorer = { openOrFocusWindow(DesktopWindowKind.FILES) },
                                                    isWindowFocused = isTopFront,
                                                    onTextInputFocusChanged = onTextInputFocusChanged,
                                                    onShowMessage = onShowMessage
                                                )
                                            }
                                            DesktopWindowKind.OFFLINE_WIKI -> {
                                                OfflineWikiScreen(
                                                    app = app,
                                                    onBack = { closeWindow(win.id) },
                                                    onShowMessage = onShowMessage
                                                )
                                            }
                                            DesktopWindowKind.TERMINAL -> {
                                                PowerShellTerminalScreen(
                                                    app = app,
                                                    installedApps = installedApps,
                                                    openWindowCount = windows.size,
                                                    customCommands = terminalCustomCommands,
                                                    onOpenWindow = { targetKind -> openOrFocusWindow(targetKind) },
                                                    onLaunchPackage = { pkg -> launchExternalPackage(pkg) },
                                                    onLaunchAiOffline = launchAiOfflineActivity,
                                                    onCloseTerminal = { closeWindow(win.id) },
                                                    isWindowFocused = isTopFront,
                                                    onTextInputFocusChanged = onTextInputFocusChanged,
                                                    onShowMessage = onShowMessage
                                                )
                                            }
                                            DesktopWindowKind.SETTINGS -> {
                                                SettingsScreen(
                                                    app = app,
                                                    onShowMessage = onShowMessage,
                                                    onOpenDexPermissions = onShowPermissionsDialog,
                                                    onOpenTaskManager = { openOrFocusWindow(DesktopWindowKind.TASK_MANAGER) }
                                                )
                                            }
                                            DesktopWindowKind.ARCHIVE_READER -> {
                                                ArchiveReaderScreen(
                                                    app = app,
                                                    archiveId = win.refId,
                                                    onBack = { closeWindow(win.id) },
                                                    onOpenArchiveId = { nextId ->
                                                        openOrFocusWindow(DesktopWindowKind.ARCHIVE_READER, nextId, "Archive Reader")
                                                    },
                                                    onOpenLiveUrl = { url -> openUrlInDesktopBrowser(url, false) }
                                                )
                                            }
                                            DesktopWindowKind.PDF_READER -> {
                                                PdfViewerScreen(
                                                    app = app,
                                                    itemId = win.refId,
                                                    onBack = { closeWindow(win.id) }
                                                )
                                            }
                                            DesktopWindowKind.TEXT_READER -> {
                                                TextDocumentReaderScreen(
                                                    app = app,
                                                    itemId = win.refId,
                                                    onBack = { closeWindow(win.id) }
                                                )
                                            }
                                            DesktopWindowKind.ZIM_READER -> {
                                                ZimViewerScreen(
                                                    app = app,
                                                    itemId = win.refId,
                                                    webTextZoom = settings.webViewTextZoom,
                                                    onBack = { closeWindow(win.id) }
                                                )
                                            }
                                            DesktopWindowKind.MEDIA_PLAYER -> {
                                                MediaPlayerScreen(
                                                    app = app,
                                                    mediaId = win.refId,
                                                    onBack = { closeWindow(win.id) },
                                                    onSelectOtherMedia = { nextId ->
                                                        openOrFocusWindow(DesktopWindowKind.MEDIA_PLAYER, nextId, "OfflineTube Player")
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Bottom-right Resize Grip when floating
                                if (!win.isMaximized) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(22.dp)
                                            .pointerInput(win.id) {
                                                detectDragGestures(
                                                    onDragStart = { bringToFront(win.id) },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val idx = windows.indexOfFirst { it.id == win.id }
                                                        if (idx >= 0) {
                                                            val cur = windows[idx]
                                                            val dw = dragAmount.x / density.density
                                                            val dh = dragAmount.y / density.density
                                                            windows[idx] = cur.copy(
                                                                widthDp = (cur.widthDp + dw).coerceIn(
                                                                    300f,
                                                                    (deskWidthDp - cur.xDp).coerceAtLeast(300f)
                                                                ),
                                                                heightDp = (cur.heightDp + dh).coerceIn(
                                                                    240f,
                                                                    (deskHeightDp - cur.yDp).coerceAtLeast(240f)
                                                                )
                                                            )
                                                        }
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.OpenInFull,
                                            contentDescription = "Resize window",
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // APP DRAWER (START MENU) OVERLAY
            if (appDrawerOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(9999f)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { appDrawerOpen = false }
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        shadowElevation = 16.dp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .widthIn(max = 760.dp)
                            .fillMaxWidth(0.95f)
                            .heightIn(max = (deskHeightDp * 0.85f).dp)
                            .clickable(enabled = false) {}
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Header: "App Drawer & Cyber Deck" + [Floating | Fullscreen | Tampilkan App Sistem] + [X]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "App Drawer & Cyber Deck",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1
                                )
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = launchExternalFloating,
                                        onClick = { launchExternalFloating = true },
                                        label = { Text("Buka Floating (Freeform)", maxLines = 1) }
                                    )
                                    FilterChip(
                                        selected = !launchExternalFloating,
                                        onClick = { launchExternalFloating = false },
                                        label = { Text("Buka Fullscreen", maxLines = 1) }
                                    )
                                    FilterChip(
                                        selected = showSystemApps,
                                        onClick = { showSystemApps = !showSystemApps },
                                        label = { Text("Tampilkan App Sistem", maxLines = 1) }
                                    )
                                }
                                IconButton(
                                    onClick = { appDrawerOpen = false },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Tutup")
                                }
                            }

                            OutlinedTextField(
                                value = appSearchQuery,
                                onValueChange = { appSearchQuery = it },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                placeholder = { Text("Cari Cyber Browser, OfflineTube, WhatsApp, atau aplikasi lain...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { onTextInputFocusChanged(it.isFocused) }
                            )

                            HorizontalDivider()

                            val builtInDrawerApps = remember(appSearchQuery) {
                                listOf(
                                    Triple(DesktopWindowKind.BROWSER, "Cyber Browser", Icons.Filled.Public),
                                    Triple(DesktopWindowKind.MEDIA, "OfflineTube", Icons.Filled.VideoLibrary),
                                    Triple(DesktopWindowKind.OFFLINE_WIKI, "Offline Wiki", Icons.Filled.CloudOff),
                                    Triple(DesktopWindowKind.SEARCH, "Offline Search", Icons.Filled.Search),
                                    Triple(DesktopWindowKind.MUSIC, "Cyber Music", Icons.Filled.LibraryMusic),
                                    Triple(DesktopWindowKind.LIBRARY, "Knowledge Vault", Icons.Filled.MenuBook),
                                    Triple(DesktopWindowKind.FILES, "File Explorer", Icons.Filled.Folder),
                                    Triple(DesktopWindowKind.TASK_MANAGER, "Task Manager", Icons.Filled.Speed),
                                    Triple(DesktopWindowKind.TERMINAL, "Terminal PowerShell", Icons.Filled.Terminal),
                                    Triple(DesktopWindowKind.DOWNLOADS, "Unduhan", Icons.Filled.Download),
                                    Triple(DesktopWindowKind.BOOKMARKS, "Bookmarks", Icons.Filled.Bookmark),
                                    Triple(DesktopWindowKind.SETTINGS, "Settings", Icons.Filled.Settings)
                                ).filter { (kind, label, _) ->
                                    appSearchQuery.isBlank() ||
                                        label.contains(appSearchQuery, true) ||
                                        kind.name.contains(appSearchQuery, true)
                                }
                            }

                            val filteredApps = remember(installedApps, appSearchQuery, showSystemApps) {
                                installedApps.filter { appInfo ->
                                    val sysOk = showSystemApps || !appInfo.isSystemApp ||
                                        appInfo.label.contains("youtube", true) ||
                                        appInfo.label.contains("chrome", true) ||
                                        appInfo.label.contains("files", true) ||
                                        appInfo.label.contains("gallery", true) ||
                                        appInfo.label.contains("play store", true)
                                    val qOk = appSearchQuery.isBlank() ||
                                        appInfo.label.contains(appSearchQuery, true) ||
                                        appInfo.packageName.contains(appSearchQuery, true)
                                    sysOk && qOk
                                }
                            }

                            val showAiOfflineInDrawer = appSearchQuery.isBlank() ||
                                "ai offline".contains(appSearchQuery, true) ||
                                "qwen".contains(appSearchQuery, true) ||
                                "llm".contains(appSearchQuery, true)
                            val builtInCount = builtInDrawerApps.size + (if (showAiOfflineInDrawer) 1 else 0)

                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 112.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (builtInCount > 0) {
                                    item(
                                        key = "header_builtin_drawer",
                                        span = { GridItemSpan(maxLineSpan) }
                                    ) {
                                        Text(
                                            "Aplikasi Cyber Deck ($builtInCount)",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (showAiOfflineInDrawer) {
                                        item(key = "builtin_ai_offline") {
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = Color(0xFF00FF41).copy(alpha = 0.14f)
                                                ),
                                                modifier = Modifier.clickable {
                                                    appDrawerOpen = false
                                                    launchAiOfflineActivity()
                                                }
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.fillMaxWidth().padding(10.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(Color(0xFF00FF41).copy(alpha = 0.22f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Filled.SmartToy,
                                                            contentDescription = "AI Offline",
                                                            tint = Color(0xFF00FF41),
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                    Spacer(Modifier.height(6.dp))
                                                    Text(
                                                        text = "AI Offline",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    items(builtInDrawerApps, key = { "builtin_${it.first.name}" }) { (kind, label, icon) ->
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                            ),
                                            modifier = Modifier.clickable {
                                                appDrawerOpen = false
                                                openOrFocusWindow(kind)
                                            }
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.fillMaxWidth().padding(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        icon,
                                                        contentDescription = label,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }

                                item(
                                    key = "header_installed_drawer",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Text(
                                        "Aplikasi Android Terinstall (${filteredApps.size})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }

                                items(filteredApps, key = { it.packageName }) { extApp ->
                                    val isPinned = extApp.packageName in pinnedPackages
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                        ),
                                        modifier = Modifier.clickable { launchExternalPackage(extApp.packageName) }
                                    ) {
                                        Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                            IconButton(
                                                onClick = { togglePinExternalApp(extApp.packageName) },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.PushPin,
                                                    contentDescription = "Pin to Desktop",
                                                    tint = if (isPinned) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                            ) {
                                                InstalledAppIcon(
                                                    packageName = extApp.packageName,
                                                    modifier = Modifier.size(40.dp)
                                                )
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                    text = extApp.label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center
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
            // NOTIFICATION CENTER OVERLAY (Taskbar Right Panel)
            if (notifPanelOpen) {
                val activeDownloads = downloads.filter {
                    it.status == DownloadStatus.DOWNLOADING ||
                        it.status == DownloadStatus.QUEUED ||
                        it.status == DownloadStatus.FAILED
                }.take(3)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(9998f)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { notifPanelOpen = false }
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        shadowElevation = 16.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .widthIn(max = 420.dp)
                            .fillMaxWidth(0.92f)
                            .heightIn(max = (deskHeightDp * 0.82f).dp)
                            .clickable(enabled = false) {}
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.NotificationsActive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text("Notifikasi & Status", style = MaterialTheme.typography.titleMedium)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (systemNotifications.any { it.isClearable }) {
                                        IconButton(onClick = { NotificationCenterStore.clearAllClearable() }) {
                                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Bersihkan Semua")
                                        }
                                    }
                                    IconButton(onClick = { notifPanelOpen = false }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Tutup")
                                    }
                                }
                            }

                            if (!permStatus.notificationAccessGranted) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = DeckAmber.copy(alpha = 0.16f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            "Tampilkan Notifikasi WA, YouTube & Sistem di sini",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = DeckAmber
                                        )
                                        Text(
                                            "Aktifkan Akses Notifikasi agar saat Fullscreen semua notif tetap muncul di taskbar.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                onShowMessage(NotificationCenterStore.openNotificationAccessSettings(context))
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Aktifkan Akses Notifikasi")
                                        }
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Built-in Cyber Deck live alerts
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            notifPanelOpen = false
                                            openOrFocusWindow(DesktopWindowKind.SETTINGS)
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = if (driveStatus.isConnected) "SSD / Deck Drive Terhubung"
                                            else "Deck Drive Belum Terpasang",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (driveStatus.isConnected) DeckGreen else DeckAmber
                                        )
                                        Text(
                                            text = if (driveStatus.isConnected) driveStatus.displayName
                                            else "Colok USB-C SSD atau atur folder di Settings → Deck Drive.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                activeDownloads.forEach { dl ->
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                notifPanelOpen = false
                                                openOrFocusWindow(DesktopWindowKind.DOWNLOADS)
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = "Download (${dl.status.label}): ${dl.fileName}",
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val prog = if (dl.totalBytes > 0) {
                                                "${Formatters.formatBytes(dl.bytesDownloaded)} / ${Formatters.formatBytes(dl.totalBytes)}"
                                            } else {
                                                Formatters.formatBytes(dl.bytesDownloaded)
                                            }
                                            Text(
                                                text = dl.errorMessage ?: prog,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (dl.status == DownloadStatus.FAILED) Color(0xFFEF4444)
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider()

                                if (systemNotifications.isEmpty()) {
                                    Text(
                                        text = if (permStatus.notificationAccessGranted) {
                                            "Belum ada notifikasi masuk saat ini."
                                        } else {
                                            "Izin Akses Notifikasi belum aktif — gunakan tombol di atas untuk menampilkan notif aplikasi lain."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                } else {
                                    systemNotifications.forEach { item ->
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val sent = runCatching {
                                                        item.contentIntent?.send()
                                                        true
                                                    }.getOrDefault(false)
                                                    if (!sent) {
                                                        launchExternalPackage(item.packageName)
                                                    }
                                                    notifPanelOpen = false
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                InstalledAppIcon(
                                                    packageName = item.packageName,
                                                    modifier = Modifier.size(30.dp)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = item.appLabel,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        Text(
                                                            text = DateFormat.format("HH:mm", Date(item.postedAtMs)).toString(),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Text(
                                                        text = item.title,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (item.text.isNotBlank()) {
                                                        Text(
                                                            text = item.text,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                if (item.isClearable) {
                                                    IconButton(
                                                        onClick = { NotificationCenterStore.dismiss(item.key) },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Filled.Close,
                                                            contentDescription = "Hapus Notifikasi",
                                                            modifier = Modifier.size(14.dp)
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
            }

            // CLICKABLE TASKBAR TRAY POPUPS (Net / Battery / Clock + Calendar)
            if (activeTrayPopup != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(9997f)
                        .background(Color.Black.copy(alpha = 0.28f))
                        .clickable { activeTrayPopup = null }
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        shadowElevation = 16.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .widthIn(min = 290.dp, max = 380.dp)
                            .clickable(enabled = false) {}
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            when (activeTrayPopup) {
                                TaskbarTrayPopup.NETWORK -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isOnline) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                                                contentDescription = null,
                                                tint = if (isOnline) DeckGreen else DeckAmber
                                            )
                                            Text("Jaringan & Internet", style = MaterialTheme.typography.titleMedium)
                                        }
                                        IconButton(
                                            onClick = { activeTrayPopup = null },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = "Tutup", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isOnline) DeckGreen.copy(alpha = 0.14f) else DeckAmber.copy(alpha = 0.14f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = if (isOnline) "Terhubung ke Internet (ONLINE)" else "Mode Offline (Tidak Ada Koneksi)",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (isOnline) DeckGreen else DeckAmber
                                            )
                                            Text(
                                                text = if (isOnline) "Cyber Browser, Search & Download online siap digunakan."
                                                else "ZIM Encyclopedia, OfflineTube, Cyber Music & File Explorer tetap 100% aktif.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                activeTrayPopup = null
                                                openOrFocusWindow(DesktopWindowKind.BROWSER)
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Buka Browser", maxLines = 1)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                activeTrayPopup = null
                                                onShowMessage(DesktopLauncherManager.openWifiSettings(context))
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Setelan Wi-Fi", maxLines = 1)
                                        }
                                    }
                                }

                                TaskbarTrayPopup.BLUETOOTH -> {
                                    val btConnectedCount = bluetoothStatus.pairedDevices.count { it.isConnected }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = when {
                                                    !bluetoothStatus.enabled -> Icons.Filled.BluetoothDisabled
                                                    btConnectedCount > 0 -> Icons.Filled.BluetoothConnected
                                                    else -> Icons.Filled.Bluetooth
                                                },
                                                contentDescription = null,
                                                tint = when {
                                                    btConnectedCount > 0 -> DeckGreen
                                                    bluetoothStatus.enabled -> MaterialTheme.colorScheme.primary
                                                    else -> DeckAmber
                                                }
                                            )
                                            Text("Bluetooth & Perangkat", style = MaterialTheme.typography.titleMedium)
                                        }
                                        IconButton(
                                            onClick = { activeTrayPopup = null },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = "Tutup", modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (bluetoothStatus.enabled) DeckGreen.copy(alpha = 0.14f)
                                            else DeckAmber.copy(alpha = 0.14f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = when {
                                                    !bluetoothStatus.supported -> "Adapter Bluetooth tidak tersedia"
                                                    btConnectedCount > 0 -> "Bluetooth Aktif • $btConnectedCount terhubung"
                                                    bluetoothStatus.enabled -> "Bluetooth Aktif (Siaga)"
                                                    else -> "Bluetooth Nonaktif"
                                                },
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (bluetoothStatus.enabled) DeckGreen else DeckAmber
                                            )
                                            Text(
                                                text = if (!bluetoothStatus.permissionGranted) {
                                                    "Izin Perangkat Terdekat diperlukan untuk membaca keyboard/mouse Bluetooth terpasang."
                                                } else if (bluetoothStatus.pairedDevices.isEmpty()) {
                                                    "Belum ada perangkat terpasang atau Bluetooth sedang mati."
                                                } else {
                                                    "${bluetoothStatus.pairedDevices.size} perangkat terpasang di sistem."
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (!bluetoothStatus.permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        Button(
                                            onClick = { btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Izinkan Akses Bluetooth (Perangkat Terdekat)")
                                        }
                                    }

                                    if (bluetoothStatus.pairedDevices.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 200.dp)
                                                .verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            bluetoothStatus.pairedDevices.forEach { dev ->
                                                Card(
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                    ),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = dev.name,
                                                                style = MaterialTheme.typography.labelLarge,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            if (dev.address.isNotBlank()) {
                                                                Text(
                                                                    text = dev.address,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }
                                                        Text(
                                                            text = if (dev.isConnected) "Terhubung" else "Terpasang",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (dev.isConnected) DeckGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { bluetoothStatus = DesktopLauncherManager.readBluetoothStatus(context) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Refresh", maxLines = 1)
                                        }
                                        Button(
                                            onClick = {
                                                activeTrayPopup = null
                                                onShowMessage(DesktopLauncherManager.openBluetoothSettings(context))
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Setelan Bluetooth", maxLines = 1)
                                        }
                                    }
                                }

                                TaskbarTrayPopup.BATTERY -> {
                                    val pct = batteryStatus.percent
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (batteryStatus.isCharging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryFull,
                                                contentDescription = null,
                                                tint = if (batteryStatus.isCharging) DeckGreen else MaterialTheme.colorScheme.primary
                                            )
                                            Text("Baterai & Layar", style = MaterialTheme.typography.titleMedium)
                                        }
                                        IconButton(
                                            onClick = { activeTrayPopup = null },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = "Tutup", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "${pct ?: "--"}% • ${batteryStatus.statusLabel}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = if (batteryStatus.isCharging) DeckGreen else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = batteryStatus.pluggedLabel,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            if (pct != null) {
                                                LinearProgressIndicator(
                                                    progress = { (pct / 100f).coerceIn(0f, 1f) },
                                                    modifier = Modifier.fillMaxWidth().height(6.dp)
                                                )
                                            }
                                            val detailParts = buildList {
                                                batteryStatus.temperatureC?.let { add(String.format(Locale.US, "%.1f°C", it)) }
                                                batteryStatus.voltageMv?.let { add("${it} mV") }
                                            }
                                            if (detailParts.isNotEmpty()) {
                                                Text(
                                                    text = "Sensor: ${detailParts.joinToString(" • ")}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Kecerahan Layar App", style = MaterialTheme.typography.labelLarge)
                                        Switch(
                                            checked = settings.brightnessOverrideEnabled,
                                            onCheckedChange = { v ->
                                                scope.launch {
                                                    app.settingsRepository.updateSettings { it.copy(brightnessOverrideEnabled = v) }
                                                }
                                            }
                                        )
                                    }
                                    if (settings.brightnessOverrideEnabled) {
                                        Slider(
                                            value = settings.screenBrightnessPercent.toFloat(),
                                            onValueChange = { v ->
                                                scope.launch {
                                                    app.settingsRepository.updateSettings {
                                                        it.copy(screenBrightnessPercent = v.roundToInt().coerceIn(10, 100))
                                                    }
                                                }
                                            },
                                            valueRange = 10f..100f
                                        )
                                    }
                                    Text(
                                        text = "Night Dimmer: ${settings.nightDimmerPercent}%",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Slider(
                                        value = settings.nightDimmerPercent.toFloat(),
                                        onValueChange = { v ->
                                            scope.launch {
                                                app.settingsRepository.updateSettings {
                                                    it.copy(nightDimmerPercent = v.roundToInt().coerceIn(0, 65))
                                                }
                                            }
                                        },
                                        valueRange = 0f..65f
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            activeTrayPopup = null
                                            onShowMessage(DesktopLauncherManager.openBatterySettings(context))
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Setelan Baterai Android")
                                    }
                                }

                                TaskbarTrayPopup.CLOCK -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Text("Jam & Kalender", style = MaterialTheme.typography.titleMedium)
                                        }
                                        IconButton(
                                            onClick = { activeTrayPopup = null },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = "Tutup", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Text(
                                        text = clockSecondsText.ifBlank { clockText },
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = fullDateText.ifBlank { dateText },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    MiniMonthCalendarCard()
                                    OutlinedButton(
                                        onClick = {
                                            activeTrayPopup = null
                                            onShowMessage(DesktopLauncherManager.openDateTimeSettings(context))
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Setelan Tanggal & Jam")
                                    }
                                }

                                TaskbarTrayPopup.STORAGE -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Filled.Usb, contentDescription = null, tint = DeckGreen)
                                            Text("Penyimpanan Eksternal & SSD", style = MaterialTheme.typography.titleMedium)
                                        }
                                        IconButton(
                                            onClick = { activeTrayPopup = null },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = "Tutup", modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    val activeParts = mountedPartitions.filter { !it.isEjectedInSession }
                                    if (mountedPartitions.isEmpty() && !driveStatus.isConnected) {
                                        Text(
                                            "Tidak ada SSD / Flashdisk / Kartu SD eksternal terpasang saat ini.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 260.dp)
                                                .verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            mountedPartitions.forEachIndexed { idx, part ->
                                                Card(
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                    ),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Filled.Storage,
                                                            contentDescription = null,
                                                            tint = if (part.isEjectedInSession) DeckAmber else DeckGreen,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = "Partisi #${idx + 1}: ${part.label}",
                                                                style = MaterialTheme.typography.labelLarge,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = if (part.isEjectedInSession) "Sudah di-Eject (Lepas aman)"
                                                                else part.mountPath ?: part.id,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        if (!part.isEjectedInSession) {
                                                            OutlinedButton(
                                                                onClick = {
                                                                    scope.launch {
                                                                        val (safDetached, msg) = app.driveManager.ejectExternalPartition(
                                                                            part,
                                                                            settings.deckDriveTreeUri
                                                                        )
                                                                        if (safDetached) {
                                                                            app.settingsRepository.updateSettings {
                                                                                it.copy(
                                                                                    deckDriveTreeUri = null,
                                                                                    deckDriveLabel = "Not connected",
                                                                                    archiveStorageTarget = StorageTarget.INTERNAL,
                                                                                    downloadStorageTarget = StorageTarget.INTERNAL
                                                                                )
                                                                            }
                                                                        }
                                                                        refreshAfterStorageChange()
                                                                        onShowMessage(msg)
                                                                    }
                                                                }
                                                            ) {
                                                                Icon(Icons.Filled.Eject, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                Spacer(Modifier.width(4.dp))
                                                                Text("Eject")
                                                            }
                                                        } else {
                                                            TextButton(
                                                                onClick = {
                                                                    app.driveManager.remountVolumeInApp(part.id)
                                                                    refreshAfterStorageChange()
                                                                    onShowMessage("${part.label} diaktifkan kembali.")
                                                                }
                                                            ) {
                                                                Text("Pasang Lagi")
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            if (driveStatus.isConnected) {
                                                Card(
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                                    ),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                "Deck Drive SAF: ${driveStatus.displayName}",
                                                                style = MaterialTheme.typography.labelLarge,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                if ((driveStatus.totalBytes ?: 0L) > 0L) {
                                                                    "Tersedia ${Formatters.formatBytes(driveStatus.availableBytes ?: 0L)}"
                                                                } else "Terhubung",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        OutlinedButton(
                                                            onClick = {
                                                                scope.launch {
                                                                    app.driveManager.detachDeckDrive(settings.deckDriveTreeUri)
                                                                    app.settingsRepository.updateSettings {
                                                                        it.copy(
                                                                            deckDriveTreeUri = null,
                                                                            deckDriveLabel = "Not connected",
                                                                            archiveStorageTarget = StorageTarget.INTERNAL,
                                                                            downloadStorageTarget = StorageTarget.INTERNAL
                                                                        )
                                                                    }
                                                                    refreshAfterStorageChange()
                                                                    onShowMessage("Deck Drive SAF dilepas.")
                                                                }
                                                            }
                                                        ) {
                                                            Text("Lepas SAF")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (activeParts.size > 1) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    var safCleared = false
                                                    activeParts.forEach { p ->
                                                        val (detached, _) = app.driveManager.ejectExternalPartition(
                                                            p,
                                                            settings.deckDriveTreeUri
                                                        )
                                                        if (detached) safCleared = true
                                                    }
                                                    if (safCleared) {
                                                        app.settingsRepository.updateSettings {
                                                            it.copy(
                                                                deckDriveTreeUri = null,
                                                                deckDriveLabel = "Not connected",
                                                                archiveStorageTarget = StorageTarget.INTERNAL,
                                                                downloadStorageTarget = StorageTarget.INTERNAL
                                                            )
                                                        }
                                                    }
                                                    refreshAfterStorageChange()
                                                    onShowMessage("${activeParts.size} partisi eksternal berhasil di-eject.")
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Filled.Eject, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Eject Semua Partisi (${activeParts.size})")
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            activeTrayPopup = null
                                            onShowMessage(app.driveManager.openAndroidStorageSettings())
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Setelan Storage Android (Unmount Fisik)")
                                    }
                                }

                                null -> Unit
                            }
                        }
                    }
                }
            }

            // FLOATING NOTIFICATION BANNER (Top-Right, Auto-Dismiss 2 Seconds, Slide Right to Dismiss)
            val currentBanner = floatingNotif
            if (currentBanner != null) {
                key(currentBanner.key, currentBanner.postedAtMs) {
                    var dragOffsetX by remember { mutableFloatStateOf(0f) }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                        shadowElevation = 14.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 12.dp, end = 14.dp)
                            .widthIn(min = 250.dp, max = 350.dp)
                            .offset { IntOffset(dragOffsetX.roundToInt(), 0) }
                            .zIndex(10000f)
                            .pointerInput(currentBanner.key, currentBanner.postedAtMs) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (dragOffsetX > 64f) {
                                            floatingNotif = null
                                        } else {
                                            dragOffsetX = 0f
                                        }
                                    },
                                    onDragCancel = { dragOffsetX = 0f },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetX = (dragOffsetX + dragAmount).coerceAtLeast(0f)
                                        if (dragOffsetX > 110f) {
                                            floatingNotif = null
                                        }
                                    }
                                )
                            }
                            .clickable {
                                val sent = runCatching {
                                    currentBanner.contentIntent?.send()
                                    true
                                }.getOrDefault(false)
                                if (!sent) {
                                    notifPanelOpen = true
                                }
                                floatingNotif = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            InstalledAppIcon(
                                packageName = currentBanner.packageName,
                                modifier = Modifier.size(30.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentBanner.appLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Geser kanan →",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                                Text(
                                    text = currentBanner.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (currentBanner.text.isNotBlank()) {
                                    Text(
                                        text = currentBanner.text,
                                        style = MaterialTheme.typography.bodySmall,
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

            // FLOATING ARROW TO REVEAL AUTO-HIDDEN TASKBAR
            if (settings.taskbarAutoHide && !taskbarVisible) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(9995f)
                        .clickable { taskbarVisible = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Tampilkan Taskbar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Taskbar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // MINIMIZABLE FLOATING NAVIGATION OVERLAY (Hidden automatically when Gesture Nav is active)
            if (!isGestureNavEnabled) {
                FloatingNavigationOverlay(
                    onBack = {
                        when {
                            activeTrayPopup != null -> activeTrayPopup = null
                            notifPanelOpen -> notifPanelOpen = false
                            appDrawerOpen -> appDrawerOpen = false
                            else -> {
                                val topWin = windows.filter { !it.isMinimized }.maxByOrNull { it.zIndex }
                                if (topWin != null) {
                                    toggleMinimize(topWin.id)
                                }
                            }
                        }
                    },
                    onHome = {
                        activeTrayPopup = null
                        notifPanelOpen = false
                        appDrawerOpen = false
                        minimizeAllWindows()
                    },
                    onRecentsOrDrawer = {
                        activeTrayPopup = null
                        notifPanelOpen = false
                        appDrawerOpen = !appDrawerOpen
                    },
                    onOpenNavSettings = {
                        onShowMessage(DesktopLauncherManager.openSystemNavigationSettings(context))
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .zIndex(9996f)
                )
            }
        }

        // BOTTOM DeX TASKBAR (Collapsible when Auto-Hide enabled)
        if (!settings.taskbarAutoHide || taskbarVisible) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Start / App Drawer Button
                    Surface(
                        color = if (appDrawerOpen) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable {
                            activeTrayPopup = null
                            notifPanelOpen = false
                            appDrawerOpen = !appDrawerOpen
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Filled.Apps,
                                contentDescription = "App Drawer",
                                tint = if (appDrawerOpen) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Apps",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (appDrawerOpen) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    // Quick Show Desktop / Minimize All Button
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable {
                            activeTrayPopup = null
                            minimizeAllWindows()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.GridView,
                                contentDescription = "Desktop Launcher",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                "Desktop",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // Quick Taskbar items (Open/Minimized Windows with Hold-to-Close + Pinned External Apps)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (windows.none { it.kind == DesktopWindowKind.BROWSER }) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { openOrFocusWindow(DesktopWindowKind.BROWSER) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Browser", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        windows.forEach { win ->
                            val active = !win.isMinimized && windows.maxByOrNull { it.zIndex }?.id == win.id
                            Box {
                                Surface(
                                    color = when {
                                        active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                        win.isMinimized -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (active) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.combinedClickable(
                                        onClick = { toggleMinimize(win.id) },
                                        onLongClick = { windowMenuId = win.id }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            windowIconForKind(win.kind),
                                            contentDescription = null,
                                            tint = if (active) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = win.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.widthIn(max = 120.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = windowMenuId == win.id,
                                    onDismissRequest = { windowMenuId = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (win.isMinimized) "Fokuskan Jendela" else "Minimize Jendela") },
                                        leadingIcon = { Icon(Icons.Filled.Remove, contentDescription = null) },
                                        onClick = {
                                            windowMenuId = null
                                            toggleMinimize(win.id)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (win.isMaximized) "Restore Ukuran" else "Maximize Jendela") },
                                        leadingIcon = { Icon(Icons.Filled.CropSquare, contentDescription = null) },
                                        onClick = {
                                            windowMenuId = null
                                            toggleMaximize(win.id)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Tutup Jendela", color = Color(0xFFEF4444)) },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Close, contentDescription = null, tint = Color(0xFFEF4444))
                                        },
                                        onClick = {
                                            windowMenuId = null
                                            closeWindow(win.id)
                                        }
                                    )
                                    if (windows.size > 1) {
                                        DropdownMenuItem(
                                            text = { Text("Tutup Semua Jendela") },
                                            leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                                            onClick = {
                                                windowMenuId = null
                                                windows.clear()
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Pinned External Apps quick icons on Taskbar
                        pinnedPackages.forEach { pkg ->
                            val label = installedApps.firstOrNull { it.packageName == pkg }?.label ?: pkg
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { launchExternalPackage(pkg) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    InstalledAppIcon(packageName = pkg, modifier = Modifier.size(18.dp))
                                    Text(
                                        label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.widthIn(max = 80.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    // Right System Tray (Notifications + Permissions + Classic Toggle + Clickable Net + Battery + Clock/Date + Hide Arrow)
                    val notifBadgeCount = systemNotifications.size + downloads.count {
                        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.FAILED
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Notification Bell + Badge
                        Surface(
                            color = if (notifPanelOpen) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable {
                                activeTrayPopup = null
                                appDrawerOpen = false
                                notifPanelOpen = !notifPanelOpen
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (notifBadgeCount > 0) Icons.Filled.NotificationsActive
                                    else Icons.Filled.Notifications,
                                    contentDescription = "Notifikasi",
                                    tint = if (notifBadgeCount > 0 || notifPanelOpen) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp)
                                )
                                if (notifBadgeCount > 0) {
                                    Text(
                                        text = notifBadgeCount.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // SSD / External Partition Eject Tray
                        val activePartitionCount = mountedPartitions.count { !it.isEjectedInSession }
                        if (mountedPartitions.isNotEmpty() || driveStatus.isConnected) {
                            Surface(
                                color = if (activeTrayPopup == TaskbarTrayPopup.STORAGE) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable {
                                    appDrawerOpen = false
                                    notifPanelOpen = false
                                    activeTrayPopup = if (activeTrayPopup == TaskbarTrayPopup.STORAGE) null else TaskbarTrayPopup.STORAGE
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Usb,
                                        contentDescription = "Eject SSD / USB",
                                        tint = if (activePartitionCount > 0 || driveStatus.isConnected) DeckGreen else DeckAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (mountedPartitions.size > 1) "SSD (${activePartitionCount}/${mountedPartitions.size})"
                                        else if (activePartitionCount > 0) "SSD"
                                        else "SAF",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (activePartitionCount > 0 || driveStatus.isConnected) DeckGreen else DeckAmber
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onShowPermissionsDialog,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Filled.AdminPanelSettings,
                                contentDescription = "Izin DeX & Floating",
                                tint = if (permStatus.allCoreReady) DeckGreen else DeckAmber,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // Clickable Bluetooth Pill
                        val btConnected = bluetoothStatus.pairedDevices.any { it.isConnected }
                        val btTint = when {
                            btConnected -> DeckGreen
                            bluetoothStatus.enabled -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            color = if (activeTrayPopup == TaskbarTrayPopup.BLUETOOTH) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            else Color.Transparent,
                            shape = RoundedCornerShape(7.dp),
                            modifier = Modifier.clickable {
                                appDrawerOpen = false
                                notifPanelOpen = false
                                bluetoothStatus = DesktopLauncherManager.readBluetoothStatus(context)
                                activeTrayPopup = if (activeTrayPopup == TaskbarTrayPopup.BLUETOOTH) null else TaskbarTrayPopup.BLUETOOTH
                            }
                        ) {
                            Icon(
                                imageVector = when {
                                    !bluetoothStatus.enabled -> Icons.Filled.BluetoothDisabled
                                    btConnected -> Icons.Filled.BluetoothConnected
                                    else -> Icons.Filled.Bluetooth
                                },
                                contentDescription = "Bluetooth",
                                tint = btTint,
                                modifier = Modifier
                                    .padding(horizontal = 5.dp, vertical = 5.dp)
                                    .size(16.dp)
                            )
                        }

                        // Clickable CONN (Network) Pill
                        Surface(
                            color = if (activeTrayPopup == TaskbarTrayPopup.NETWORK) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            else Color.Transparent,
                            shape = RoundedCornerShape(7.dp),
                            modifier = Modifier.clickable {
                                appDrawerOpen = false
                                notifPanelOpen = false
                                activeTrayPopup = if (activeTrayPopup == TaskbarTrayPopup.NETWORK) null else TaskbarTrayPopup.NETWORK
                            }
                        ) {
                            Text(
                                text = "CONN",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOnline) DeckGreen else DeckAmber,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
                            )
                        }

                        // Clickable Battery Pill
                        val pct = batteryStatus.percent
                        val batteryTint = when {
                            batteryStatus.isCharging -> DeckGreen
                            pct != null && pct <= 20 -> Color(0xFFEF4444)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        val batteryIcon = when {
                            batteryStatus.isCharging -> Icons.Filled.BatteryChargingFull
                            pct != null && pct <= 15 -> Icons.Filled.BatteryAlert
                            else -> Icons.Filled.BatteryFull
                        }
                        Surface(
                            color = if (activeTrayPopup == TaskbarTrayPopup.BATTERY) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            else Color.Transparent,
                            shape = RoundedCornerShape(7.dp),
                            modifier = Modifier.clickable {
                                appDrawerOpen = false
                                notifPanelOpen = false
                                activeTrayPopup = if (activeTrayPopup == TaskbarTrayPopup.BATTERY) null else TaskbarTrayPopup.BATTERY
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = batteryIcon,
                                    contentDescription = "Baterai",
                                    tint = batteryTint,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (pct != null) "$pct%" else "--%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = batteryTint,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Clickable Clock & Date Pill
                        if (clockText.isNotBlank()) {
                            Surface(
                                color = if (activeTrayPopup == TaskbarTrayPopup.CLOCK) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                else Color.Transparent,
                                shape = RoundedCornerShape(7.dp),
                                modifier = Modifier.clickable {
                                    appDrawerOpen = false
                                    notifPanelOpen = false
                                    activeTrayPopup = if (activeTrayPopup == TaskbarTrayPopup.CLOCK) null else TaskbarTrayPopup.CLOCK
                                }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = clockText,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    if (dateText.isNotBlank()) {
                                        Text(
                                            text = dateText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }

                        // Hide Taskbar arrow when auto-hide setting is enabled
                        if (settings.taskbarAutoHide) {
                            IconButton(
                                onClick = {
                                    activeTrayPopup = null
                                    appDrawerOpen = false
                                    notifPanelOpen = false
                                    taskbarVisible = false
                                },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Sembunyikan Taskbar",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // UNIFIED OFFLINE HUB CHOOSER DIALOG (Offline Wiki + OfflineTube + Offline Search + AI Offline)
    if (showOfflineHubDialog) {
        AlertDialog(
            onDismissRequest = { showOfflineHubDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null, tint = Color(0xFF38BDF8))
                    Text("Offline")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Akses ensiklopedia, media video, AI chat lokal, dan pencarian offline:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Offline Wiki right beside OfflineTube
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    showOfflineHubDialog = false
                                    openOrFocusWindow(DesktopWindowKind.OFFLINE_WIKI)
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.MenuBook, contentDescription = null, tint = Color(0xFF38BDF8))
                                Text("Offline Wiki", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Kiwix PWA & simpan halaman wiki offline",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    showOfflineHubDialog = false
                                    openOrFocusWindow(DesktopWindowKind.MEDIA)
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("OfflineTube", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Putar video & media lokal SD/SSD",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    showOfflineHubDialog = false
                                    openOrFocusWindow(DesktopWindowKind.SEARCH)
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Offline Search", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Cari arsip, buku ZIM, video & musik",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF071A0C)),
                            border = BorderStroke(1.dp, Color(0xFF00FF41).copy(alpha = 0.65f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    showOfflineHubDialog = false
                                    launchAiOfflineActivity()
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.SmartToy, contentDescription = null, tint = Color(0xFF00FF41))
                                Text("AI OFFLINE", style = MaterialTheme.typography.titleSmall, color = Color(0xFF00FF41))
                                Text(
                                    "Chat LLM lokal tanpa internet (SSD/AI_Models)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFA7F3D0)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOfflineHubDialog = false }) { Text("Tutup") }
            }
        )
    }

    // UNIFIED SAVED & DOWNLOADS CHOOSER DIALOG
    if (showSavedHubDialog) {
        AlertDialog(
            onDismissRequest = { showSavedHubDialog = false },
            title = { Text("Tersimpan & Unduhan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Pilih menu yang ingin dibuka ke jendela Desktop:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SavedHubOptionCard(
                        icon = Icons.Filled.Bookmark,
                        title = "Bookmarks",
                        subtitle = "Halaman web & link favorit tersimpan",
                        onClick = {
                            showSavedHubDialog = false
                            openOrFocusWindow(DesktopWindowKind.BOOKMARKS)
                        }
                    )
                    SavedHubOptionCard(
                        icon = Icons.Filled.History,
                        title = "History",
                        subtitle = "Riwayat kunjungan Cyber Browser",
                        onClick = {
                            showSavedHubDialog = false
                            openOrFocusWindow(DesktopWindowKind.HISTORY)
                        }
                    )
                    SavedHubOptionCard(
                        icon = Icons.Filled.Download,
                        title = "Downloads",
                        subtitle = "Daftar unduhan file & status background",
                        onClick = {
                            showSavedHubDialog = false
                            openOrFocusWindow(DesktopWindowKind.DOWNLOADS)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSavedHubDialog = false }) { Text("Tutup") }
            }
        )
    }

    // WALLPAPER & BACKGROUND PICKER DIALOG (6 Simple Presets + 4 Device Wallpapers)
    if (showWallpaperDialog) {
        AlertDialog(
            onDismissRequest = { showWallpaperDialog = false },
            title = { Text("Background & Wallpaper Desktop") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("6 Pilihan Background Simple", style = MaterialTheme.typography.titleSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DesktopBackgroundPreset.entries.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { preset ->
                                    val selected = settings.activeCustomWallpaperIndex < 0 &&
                                        settings.desktopBackgroundId == preset.id
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(
                                            if (selected) 2.dp else 1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                scope.launch {
                                                    app.settingsRepository.updateSettings {
                                                        it.copy(
                                                            desktopBackgroundId = preset.id,
                                                            activeCustomWallpaperIndex = -1
                                                        )
                                                    }
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .background(
                                                    Brush.horizontalGradient(
                                                        listOf(Color(preset.topColorArgb), Color(preset.midColorArgb))
                                                    )
                                                )
                                                .padding(horizontal = 10.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = preset.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (preset.isLight) Color(0xFF0F172A) else Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (selected) {
                                                Icon(
                                                    Icons.Filled.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color(preset.accentColorArgb),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Text("Wallpaper Import dari Perangkat (Maks. 4)", style = MaterialTheme.typography.titleSmall)
                    for (slot in 0 until 4) {
                        val path = customWallpaperPaths.getOrNull(slot)
                        val isActive = settings.activeCustomWallpaperIndex == slot && path != null
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (path != null) "Wallpaper #${slot + 1}" else "Slot #${slot + 1} (Kosong)",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                if (path != null) {
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                app.settingsRepository.updateSettings {
                                                    it.copy(activeCustomWallpaperIndex = slot)
                                                }
                                            }
                                        }
                                    ) {
                                        Text(if (isActive) "Aktif" else "Pakai")
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                runCatching { File(path).delete() }
                                                val updated = customWallpaperPaths.toMutableList()
                                                if (slot < updated.size) updated.removeAt(slot)
                                                val nextActive = when {
                                                    settings.activeCustomWallpaperIndex == slot -> -1
                                                    settings.activeCustomWallpaperIndex > slot -> settings.activeCustomWallpaperIndex - 1
                                                    else -> settings.activeCustomWallpaperIndex
                                                }
                                                app.settingsRepository.updateSettings {
                                                    it.copy(
                                                        customWallpaperPaths = updated.joinToString("\n"),
                                                        activeCustomWallpaperIndex = nextActive
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Hapus", modifier = Modifier.size(18.dp))
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        pendingWallpaperSlot = slot
                                        wallpaperPicker.launch(arrayOf("image/*"))
                                    }
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (path != null) "Ganti" else "Import")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showWallpaperDialog = false }) { Text("Selesai") }
            }
        )
    }
}
}

@Composable
fun DexPermissionsSetupDialog(
    onDismiss: () -> Unit,
    onDismissForever: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var status by remember { mutableStateOf(DesktopLauncherManager.inspectPermissions(context)) }

    val homeRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        status = DesktopLauncherManager.inspectPermissions(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                status = DesktopLauncherManager.inspectPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Izin Launcher DeX & Floating Apps")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Aktifkan izin & pengaturan sistem berikut agar Cyber Deck bisa menjadi Home Launcher, menampilkan notif di taskbar, dan membuka WA/YouTube sebagai jendela floating:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                PermissionShortcutRow(
                    title = "1. Home Launcher Utama",
                    subtitle = if (status.isDefaultHomeLauncher) "Cyber Deck sudah menjadi Home Launcher utama."
                    else "Atur Cyber Deck sebagai aplikasi layar utama / launcher tablet.",
                    granted = status.isDefaultHomeLauncher,
                    buttonLabel = "Pilih Launcher",
                    onAction = {
                        val roleIntent = DesktopLauncherManager.buildDefaultHomeRoleIntentOrNull(context)
                        if (roleIntent != null) {
                            runCatching { homeRoleLauncher.launch(roleIntent) }
                                .onFailure {
                                    onShowMessage(DesktopLauncherManager.openHomeLauncherSettings(context))
                                }
                        } else {
                            onShowMessage(DesktopLauncherManager.openHomeLauncherSettings(context))
                        }
                    }
                )

                PermissionShortcutRow(
                    title = "2. Izin Overlay (Tampil di Atas App Lain)",
                    subtitle = if (status.canDrawOverlays) "Sudah diizinkan."
                    else "Diperlukan untuk dukungan overlay & kontrol mengambang.",
                    granted = status.canDrawOverlays,
                    buttonLabel = "Izinkan Overlay",
                    onAction = {
                        onShowMessage(DesktopLauncherManager.openOverlayPermissionSettings(context))
                    }
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = if (status.freeformReady && status.isForceResizableEnabled) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                contentDescription = null,
                                tint = if (status.freeformReady) DeckGreen else DeckAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("3. Freeform / Floating Windows (WA, YouTube)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        }
                        Text(
                            text = "Freeform: ${if (status.freeformReady) "AKTIF" else "MATI"} • Force Resizable: ${if (status.isForceResizableEnabled) "AKTIF" else "MATI"}\n" +
                                "Jika terbuka Split Screen/Fullscreen di One UI, pastikan di Developer Options aktifkan 'Enable freeform windows' + 'Force activities to be resizable' lalu restart tablet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    onShowMessage(DesktopLauncherManager.openDeveloperOptionsSettings(context))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Developer Options", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (status.canWriteSecureSettings) {
                                Button(
                                    onClick = {
                                        DesktopLauncherManager.tryEnableFreeformFlagsDirectly(context)
                                            .onSuccess { msg ->
                                                status = DesktopLauncherManager.inspectPermissions(context)
                                                onShowMessage(msg)
                                            }
                                            .onFailure { err -> onShowMessage(err.message ?: "Gagal") }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Aktifkan", maxLines = 1)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(
                                            AnnotatedString(DesktopLauncherManager.buildFreeformAdbCommand(context.packageName))
                                        )
                                        onShowMessage("Perintah ADB disalin ke clipboard (opsional jika Developer Options tidak menyimpan).")
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Salin ADB", maxLines = 1)
                                }
                            }
                        }
                    }
                }

                PermissionShortcutRow(
                    title = "4. Akses Notifikasi Taskbar",
                    subtitle = if (status.notificationAccessGranted) "Aktif — notif sistem, WA, YouTube tampil di taskbar."
                    else "Aktifkan agar notif muncul di panel Notifikasi taskbar saat fullscreen.",
                    granted = status.notificationAccessGranted,
                    buttonLabel = "Buka Akses Notifikasi",
                    onAction = {
                        onShowMessage(NotificationCenterStore.openNotificationAccessSettings(context))
                    }
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("5. Shortcut Samsung One UI / Baterai", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Untuk Galaxy Tab A9: aktifkan Multi-jendela untuk semua aplikasi (Fitur Lanjutan → Labs) dan atur baterai Cyber Deck ke Tidak Dibatasi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onShowMessage(DesktopLauncherManager.openSystemMultiWindowSettings(context)) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Setelan Display/Labs", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = { onShowMessage(DesktopLauncherManager.openAppDetailsSettings(context)) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Info App & Baterai", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        OutlinedButton(
                            onClick = { onShowMessage(DesktopLauncherManager.openSystemNavigationSettings(context)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Navigasi Sistem (Aktifkan Gesture / Sembunyikan 3 Tombol)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { status = DesktopLauncherManager.inspectPermissions(context) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cek Lagi")
                }
                Button(onClick = onDismiss) { Text("Selesai") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissForever) { Text("Jangan Tampilkan Lagi") }
        }
    )
}

@Composable
private fun PermissionShortcutRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    buttonLabel: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (granted) DeckGreen else DeckAmber,
                    modifier = Modifier.size(18.dp)
                )
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun SavedHubOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MiniMonthCalendarCard() {
    val cal = remember { Calendar.getInstance() }
    val monthTitle = remember {
        java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    }
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = remember {
        (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }.get(Calendar.DAY_OF_WEEK)
    }
    val leadingBlanks = (firstDayOfWeek - Calendar.SUNDAY).coerceAtLeast(0)
    val cells = buildList<Int?> {
        repeat(leadingBlanks) { add(null) }
        for (d in 1..maxDay) add(d)
        while (size % 7 != 0) add(null)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(monthTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab").forEach { d ->
                    Text(
                        text = d,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        val isToday = day == today
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(1.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day?.toString().orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopShortcutTile(
    label: String,
    labelColor: Color = Color.White,
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(6.dp)
    ) {
        iconContent()
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun InstalledAppIcon(
    packageName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        bitmap = withContext(Dispatchers.IO) {
            DesktopLauncherManager.loadAppIconBitmap(context, packageName)
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = packageName,
            modifier = modifier.clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Apps, contentDescription = null)
        }
    }
}

private fun windowIconForKind(kind: DesktopWindowKind): ImageVector = when (kind) {
    DesktopWindowKind.BROWSER -> Icons.Filled.Public
    DesktopWindowKind.LIBRARY,
    DesktopWindowKind.ARCHIVES,
    DesktopWindowKind.ARCHIVE_READER,
    DesktopWindowKind.PDF_READER,
    DesktopWindowKind.TEXT_READER,
    DesktopWindowKind.ZIM_READER -> Icons.Filled.MenuBook
    DesktopWindowKind.MEDIA,
    DesktopWindowKind.MEDIA_PLAYER -> Icons.Filled.VideoLibrary
    DesktopWindowKind.MUSIC -> Icons.Filled.LibraryMusic
    DesktopWindowKind.FILES -> Icons.Filled.Folder
    DesktopWindowKind.TASK_MANAGER -> Icons.Filled.Speed
    DesktopWindowKind.DOWNLOADS -> Icons.Filled.Download
    DesktopWindowKind.BOOKMARKS -> Icons.Filled.Bookmark
    DesktopWindowKind.HISTORY -> Icons.Filled.History
    DesktopWindowKind.SEARCH -> Icons.Filled.Search
    DesktopWindowKind.OFFLINE_WIKI -> Icons.Filled.CloudOff
    DesktopWindowKind.TERMINAL -> Icons.Filled.Terminal
    DesktopWindowKind.SETTINGS -> Icons.Filled.Settings
}

@Composable
fun FloatingNavigationOverlay(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onRecentsOrDrawer: () -> Unit,
    onOpenNavSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("cyberdeck_ui_prefs", Context.MODE_PRIVATE)
    }
    var isMinimized by remember {
        mutableStateOf(prefs.getBoolean("floating_nav_minimized", false))
    }
    var offsetX by remember { mutableFloatStateOf(-16f) }
    var offsetY by remember { mutableFloatStateOf(-16f) }

    fun setMinimizedState(min: Boolean) {
        isMinimized = min
        prefs.edit().putBoolean("floating_nav_minimized", min).apply()
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
    ) {
        if (isMinimized) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                shadowElevation = 10.dp,
                modifier = Modifier.clickable { setMinimizedState(false) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Filled.OpenWith,
                        contentDescription = "Buka Navigasi Floating",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Nav",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onHome,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Filled.Home, contentDescription = "Home", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onRecentsOrDrawer,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Filled.Apps, contentDescription = "Apps / Recents", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onOpenNavSettings,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Filled.Tune,
                            contentDescription = "Pengaturan Navigasi",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { setMinimizedState(true) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowRight,
                            contentDescription = "Minimize Navigasi",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
