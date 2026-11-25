package com.offlinecyberdeck

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinecyberdeck.archive.ArchiveReaderScreen
import com.offlinecyberdeck.bookmarks.BookmarksScreen
import com.offlinecyberdeck.bookmarks.HistoryScreen
import com.offlinecyberdeck.browser.BrowserScreen
import com.offlinecyberdeck.browser.BrowserViewModel
import com.offlinecyberdeck.core.database.entity.AppSettingsEntity
import com.offlinecyberdeck.core.model.LibraryFileType
import com.offlinecyberdeck.core.model.MainDestination
import com.offlinecyberdeck.core.model.StorageTarget
import com.offlinecyberdeck.core.model.ThemeMode
import com.offlinecyberdeck.core.storage.ExternalVolumeInfo
import com.offlinecyberdeck.desktop.DesktopLauncherManager
import com.offlinecyberdeck.desktop.DesktopLauncherScreen
import com.offlinecyberdeck.desktop.DexPermissionsSetupDialog
import com.offlinecyberdeck.desktop.NotificationCenterStore
import com.offlinecyberdeck.desktop.TaskManagerScreen
import com.offlinecyberdeck.downloads.DownloadsScreen
import com.offlinecyberdeck.files.FileManagerScreen
import com.offlinecyberdeck.library.LibraryScreen
import com.offlinecyberdeck.library.PdfViewerScreen
import com.offlinecyberdeck.library.TextDocumentReaderScreen
import com.offlinecyberdeck.library.ZimViewerScreen
import com.offlinecyberdeck.media.MediaPlayerScreen
import com.offlinecyberdeck.media.MediaVaultScreen
import com.offlinecyberdeck.music.MusicPlayerScreen
import com.offlinecyberdeck.search.UnifiedSearchScreen
import com.offlinecyberdeck.settings.SettingsScreen
import com.offlinecyberdeck.ui.theme.CyberDeckTheme
import com.offlinecyberdeck.ui.theme.DeckAmber
import com.offlinecyberdeck.ui.theme.DeckGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface OverlayReader {
    data class ArchiveReader(val archiveId: String) : OverlayReader
    data class PdfReader(val itemId: String) : OverlayReader
    data class TextReader(val itemId: String) : OverlayReader
    data class ZimReader(val itemId: String) : OverlayReader
    data class MediaPlayer(val mediaId: String) : OverlayReader
}

class MainActivity : ComponentActivity() {

    private var pendingExternalUrl by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val app = application as CyberDeckApplication
        setContent {
            val settings by app.settingsRepository.settingsFlow
                .collectAsStateWithLifecycle(initialValue = AppSettingsEntity())
            val browserVm: BrowserViewModel = viewModel(factory = BrowserViewModel.Factory(this))

            LaunchedEffect(pendingExternalUrl) {
                val url = pendingExternalUrl
                if (!url.isNullOrBlank()) {
                    browserVm.createNewTab(url = url, isIncognito = false)
                    pendingExternalUrl = null
                }
            }

            val isDarkNow = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            LaunchedEffect(
                settings.immersiveFullscreen,
                settings.brightnessOverrideEnabled,
                settings.screenBrightnessPercent,
                isDarkNow
            ) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !isDarkNow
                controller.isAppearanceLightNavigationBars = !isDarkNow
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (settings.immersiveFullscreen) {
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
                val lp = window.attributes
                lp.screenBrightness = if (settings.brightnessOverrideEnabled) {
                    (settings.screenBrightnessPercent.coerceIn(10, 100) / 100f).coerceIn(0.05f, 1f)
                } else {
                    WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                window.attributes = lp
            }

            LaunchedEffect(settings.developerModeEnabled, settings.webViewDebuggingEnabled) {
                WebView.setWebContentsDebuggingEnabled(
                    settings.developerModeEnabled && settings.webViewDebuggingEnabled
                )
            }

            CyberDeckTheme(
                themeMode = settings.themeMode,
                accentPreset = settings.accentPreset,
                uiFontScale = settings.uiFontScale
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CyberDeckRootShell(
                        app = app,
                        browserViewModel = browserVm,
                        settings = settings
                    )
                    if (settings.nightDimmerPercent > 0) {
                        val alpha = (settings.nightDimmerPercent.coerceIn(0, 65) / 100f) * 0.85f
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = alpha))
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.dataString
            if (!uri.isNullOrBlank() && (uri.startsWith("http://") || uri.startsWith("https://"))) {
                pendingExternalUrl = uri
            }
        }
    }
}

@Composable
private fun CyberDeckRootShell(
    app: CyberDeckApplication,
    browserViewModel: BrowserViewModel,
    settings: AppSettingsEntity
) {
    var currentDest by remember { mutableStateOf(MainDestination.BROWSER) }
    var libraryInitialTab by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<OverlayReader?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val config = LocalConfiguration.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val driveStatus by app.driveManager.status.collectAsStateWithLifecycle()

    var removableVolumes by remember { mutableStateOf<List<ExternalVolumeInfo>>(emptyList()) }
    var handledExternalKey by remember { mutableStateOf<String?>(null) }
    var notifiedExternalKey by remember { mutableStateOf<String?>(null) }
    var showExternalDetectedDialog by remember { mutableStateOf(false) }
    var showDexPermissionsDialog by remember { mutableStateOf(false) }
    var autoPromptChecked by remember { mutableStateOf(false) }

    LaunchedEffect(settings.desktopLauncherMode, settings.dexSetupPromptDismissed) {
        if (!autoPromptChecked && settings.desktopLauncherMode && !settings.dexSetupPromptDismissed) {
            autoPromptChecked = true
            val perm = DesktopLauncherManager.inspectPermissions(context)
            if (!perm.allCoreReady) {
                showDexPermissionsDialog = true
            }
        }
    }

    fun refreshExternalDevices() {
        removableVolumes = app.driveManager.detectRemovableExternalVolumes()
        scope.launch { app.driveManager.refreshStatus(settings.deckDriveTreeUri) }
    }

    val externalSafPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val status = app.driveManager.attachDeckDrive(uri)
                app.settingsRepository.updateSettings {
                    it.copy(
                        deckDriveTreeUri = uri.toString(),
                        deckDriveLabel = status.displayName,
                        archiveStorageTarget = if (status.isWritable) StorageTarget.DECK_DRIVE else it.archiveStorageTarget,
                        downloadStorageTarget = if (status.isWritable) StorageTarget.DECK_DRIVE else it.downloadStorageTarget
                    )
                }
                snackbarHostState.showSnackbar("Deck Drive connected: ${status.displayName}")
            }
        }
    }

    DisposableEffect(context, lifecycleOwner, settings.deckDriveTreeUri) {
        refreshExternalDevices()
        val mediaReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                refreshExternalDevices()
            }
        }
        val mediaFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addDataScheme("file")
        }
        val usbFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        runCatching {
            ContextCompat.registerReceiver(context, mediaReceiver, mediaFilter, ContextCompat.RECEIVER_EXPORTED)
            ContextCompat.registerReceiver(context, mediaReceiver, usbFilter, ContextCompat.RECEIVER_EXPORTED)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshExternalDevices()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            runCatching { context.unregisterReceiver(mediaReceiver) }
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val currentExternalKey = remember(removableVolumes, driveStatus.isConnected, driveStatus.isWritable, driveStatus.configuredUri) {
        when {
            removableVolumes.isNotEmpty() -> removableVolumes.joinToString("|") { it.id }
            driveStatus.isConnected && driveStatus.isWritable -> "saf:${driveStatus.configuredUri}"
            else -> null
        }
    }

    LaunchedEffect(currentExternalKey) {
        if (currentExternalKey == null) {
            handledExternalKey = null
            notifiedExternalKey = null
            showExternalDetectedDialog = false
        } else {
            if (currentExternalKey != handledExternalKey) {
                showExternalDetectedDialog = true
            }
            if (currentExternalKey != notifiedExternalKey) {
                notifiedExternalKey = currentExternalKey
                val label = removableVolumes.firstOrNull()?.label
                    ?: driveStatus.displayName.takeIf { driveStatus.isConnected }
                    ?: "Penyimpanan Eksternal"
                NotificationCenterStore.postInternalAlert(
                    title = "Penyimpanan Eksternal Terdeteksi",
                    text = "$label siap. Pilih penyimpanan Offline Archive & Download (Internal / Eksternal).",
                    appLabel = "Cyber Deck Storage",
                    keyPrefix = "ext_storage"
                )
                NotificationCenterStore.playNotificationSound(
                    context = context,
                    enabled = settings.notificationSoundEnabled
                )
                postExternalStorageNotification(context, label)
                if (settings.musicAutoImport) {
                    app.musicRepository.autoImportAndRefresh(
                        deckDriveTreeUri = settings.deckDriveTreeUri,
                        extraSafUrisCsv = settings.extraMediaSafUris
                    )
                }
                if (settings.offlineTubeAutoImport) {
                    app.mediaRepository.autoImportAndRefreshVideos(
                        deckDriveTreeUri = settings.deckDriveTreeUri,
                        extraSafUrisCsv = settings.extraMediaSafUris
                    )
                }
            }
        }
    }

    val useRail = config.screenWidthDp >= 720
    val hideNavChrome = overlay != null ||
        (currentDest == MainDestination.BROWSER && settings.hideNavChromeInBrowser)

    LaunchedEffect(Unit) {
        browserViewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val showMessage: (String) -> Unit = { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    val openUrlInBrowser: (String, Boolean) -> Unit = { url, incognito ->
        overlay = null
        currentDest = MainDestination.BROWSER
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
                showMessage("Library item not found.")
            } else if (!app.vaultRepository.isItemAvailable(item)) {
                showMessage("File unavailable — reconnect Deck Drive.")
            } else {
                when (item.fileType) {
                    LibraryFileType.PDF -> overlay = OverlayReader.PdfReader(item.id)
                    LibraryFileType.ZIM -> overlay = OverlayReader.ZimReader(item.id)
                    LibraryFileType.EPUB,
                    LibraryFileType.HTML,
                    LibraryFileType.TEXT -> overlay = OverlayReader.TextReader(item.id)
                    else -> {
                        val intent = app.vaultRepository.buildExternalViewIntent(item)
                        if (intent != null) {
                            runCatching { context.startActivity(intent) }
                                .onFailure { showMessage("No installed app handles ${item.fileType.label}") }
                        }
                    }
                }
            }
        }
    }

    BackHandler(enabled = overlay != null || currentDest != MainDestination.BROWSER || !settings.desktopLauncherMode) {
        if (overlay != null) {
            overlay = null
        } else if (currentDest != MainDestination.BROWSER) {
            currentDest = MainDestination.BROWSER
        } else if (!settings.desktopLauncherMode) {
            scope.launch {
                app.settingsRepository.updateSettings { it.copy(desktopLauncherMode = true) }
            }
        }
    }

    val navItems = listOf(
        MainDestination.BROWSER to Icons.Filled.Public,
        MainDestination.LIBRARY to Icons.Filled.MenuBook,
        MainDestination.MEDIA to Icons.Filled.VideoLibrary,
        MainDestination.MUSIC to Icons.Filled.LibraryMusic,
        MainDestination.FILES to Icons.Filled.Folder,
        MainDestination.TASK_MANAGER to Icons.Filled.Speed,
        MainDestination.DOWNLOADS to Icons.Filled.Download,
        MainDestination.BOOKMARKS to Icons.Filled.Bookmark,
        MainDestination.HISTORY to Icons.Filled.History,
        MainDestination.SEARCH to Icons.Filled.Search,
        MainDestination.SETTINGS to Icons.Filled.Settings
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .let { if (settings.immersiveFullscreen) it else it.safeDrawingPadding() },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!settings.desktopLauncherMode && !useRail && !hideNavChrome) {
                NavigationBar {
                    val compactItems = listOf(
                        MainDestination.BROWSER to Icons.Filled.Public,
                        MainDestination.LIBRARY to Icons.Filled.MenuBook,
                        MainDestination.MEDIA to Icons.Filled.VideoLibrary,
                        MainDestination.MUSIC to Icons.Filled.LibraryMusic,
                        MainDestination.FILES to Icons.Filled.Folder,
                        MainDestination.SETTINGS to Icons.Filled.Settings
                    )
                    compactItems.forEach { (dest, icon) ->
                        NavigationBarItem(
                            selected = currentDest == dest,
                            onClick = {
                                if (dest == MainDestination.LIBRARY) libraryInitialTab = 0
                                currentDest = dest
                            },
                            icon = { Icon(icon, contentDescription = dest.label) },
                            label = {
                                Text(dest.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (settings.desktopLauncherMode) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                DesktopLauncherScreen(
                    app = app,
                    browserViewModel = browserViewModel,
                    settings = settings,
                    onShowPermissionsDialog = { showDexPermissionsDialog = true },
                    onSwitchToClassicMode = {
                        scope.launch {
                            app.settingsRepository.updateSettings { it.copy(desktopLauncherMode = false) }
                        }
                    },
                    onShowMessage = showMessage
                )
            }
        } else {
        Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (useRail && !hideNavChrome) {
                NavigationRail {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        NavigationRailItem(
                            selected = false,
                            onClick = {
                                scope.launch {
                                    app.settingsRepository.updateSettings { it.copy(desktopLauncherMode = true) }
                                }
                            },
                            icon = { Icon(Icons.Filled.GridView, contentDescription = "DeX Launcher") },
                            label = { Text("Launcher") }
                        )
                        navItems.forEach { (dest, icon) ->
                            NavigationRailItem(
                                selected = currentDest == dest,
                                onClick = {
                                    if (dest == MainDestination.LIBRARY) libraryInitialTab = 0
                                    currentDest = dest
                                },
                                icon = { Icon(icon, contentDescription = dest.label) },
                                label = { Text(dest.label) }
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                val activeOverlay = overlay
                if (activeOverlay != null) {
                    when (activeOverlay) {
                        is OverlayReader.ArchiveReader -> {
                            ArchiveReaderScreen(
                                app = app,
                                archiveId = activeOverlay.archiveId,
                                onBack = { overlay = null },
                                onOpenArchiveId = { nextId -> overlay = OverlayReader.ArchiveReader(nextId) },
                                onOpenLiveUrl = { url -> openUrlInBrowser(url, false) }
                            )
                        }
                        is OverlayReader.PdfReader -> {
                            PdfViewerScreen(
                                app = app,
                                itemId = activeOverlay.itemId,
                                onBack = { overlay = null }
                            )
                        }
                        is OverlayReader.TextReader -> {
                            TextDocumentReaderScreen(
                                app = app,
                                itemId = activeOverlay.itemId,
                                onBack = { overlay = null }
                            )
                        }
                        is OverlayReader.ZimReader -> {
                            ZimViewerScreen(
                                app = app,
                                itemId = activeOverlay.itemId,
                                webTextZoom = settings.webViewTextZoom,
                                onBack = { overlay = null }
                            )
                        }
                        is OverlayReader.MediaPlayer -> {
                            MediaPlayerScreen(
                                app = app,
                                mediaId = activeOverlay.mediaId,
                                onBack = { overlay = null },
                                onSelectOtherMedia = { nextId -> overlay = OverlayReader.MediaPlayer(nextId) }
                            )
                        }
                    }
                } else {
                    when (currentDest) {
                        MainDestination.BROWSER -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                BrowserScreen(
                                    viewModel = browserViewModel,
                                    onOpenDownloads = { currentDest = MainDestination.DOWNLOADS },
                                    onOpenUnifiedSearch = { currentDest = MainDestination.SEARCH },
                                    onOpenArchives = {
                                        libraryInitialTab = 1
                                        currentDest = MainDestination.LIBRARY
                                    },
                                    onOpenBookmarks = { currentDest = MainDestination.BOOKMARKS },
                                    onOpenHistory = { currentDest = MainDestination.HISTORY },
                                    onOpenSettings = { currentDest = MainDestination.SETTINGS },
                                    onReturnToLauncher = {
                                        scope.launch {
                                            app.settingsRepository.updateSettings { it.copy(desktopLauncherMode = true) }
                                        }
                                    },
                                    onShowMessage = showMessage
                                )
                            }
                        }
                        MainDestination.LIBRARY -> {
                            LibraryScreen(
                                app = app,
                                initialTabIndex = libraryInitialTab,
                                onOpenArchiveReader = { id -> overlay = OverlayReader.ArchiveReader(id) },
                                onOpenPdfReader = { id -> overlay = OverlayReader.PdfReader(id) },
                                onOpenTextReader = { id -> overlay = OverlayReader.TextReader(id) },
                                onOpenZimReader = { id -> overlay = OverlayReader.ZimReader(id) },
                                onOpenBrowserUrl = { url -> openUrlInBrowser(url, false) },
                                onShowMessage = showMessage
                            )
                        }
                        MainDestination.MEDIA -> {
                            MediaVaultScreen(
                                app = app,
                                onPlayMedia = { id -> overlay = OverlayReader.MediaPlayer(id) },
                                onShowMessage = showMessage
                            )
                        }
                        MainDestination.MUSIC -> {
                            MusicPlayerScreen(
                                app = app,
                                onShowMessage = showMessage
                            )
                        }
                        MainDestination.FILES -> {
                            FileManagerScreen(
                                app = app,
                                onShowMessage = showMessage
                            )
                        }
                        MainDestination.TASK_MANAGER -> {
                            TaskManagerScreen(
                                app = app,
                                activeDesktopWindowsCount = 0,
                                activeWebViewCount = browserViewModel.webViewPool.getLiveCount(),
                                onTrimAppRam = { browserViewModel.webViewPool.clearAllWebViewCaches() },
                                onShowMessage = showMessage
                            )
                        }
                        MainDestination.DOWNLOADS -> {
                            DownloadsScreen(app = app, onShowMessage = showMessage)
                        }
                        MainDestination.BOOKMARKS -> {
                            BookmarksScreen(
                                app = app,
                                onOpenUrl = openUrlInBrowser
                            )
                        }
                        MainDestination.HISTORY -> {
                            HistoryScreen(
                                app = app,
                                onOpenUrl = { url -> openUrlInBrowser(url, false) }
                            )
                        }
                        MainDestination.SEARCH -> {
                            UnifiedSearchScreen(
                                app = app,
                                onOpenArchive = { id -> overlay = OverlayReader.ArchiveReader(id) },
                                onOpenLibraryItem = openLibraryItemById,
                                onOpenMediaItem = { id -> overlay = OverlayReader.MediaPlayer(id) },
                                onOpenUrl = { url -> openUrlInBrowser(url, false) },
                                onOpenFileExplorer = { currentDest = MainDestination.FILES },
                                onShowMessage = showMessage
                            )
                        }
                        MainDestination.SETTINGS -> {
                            SettingsScreen(
                                app = app,
                                onShowMessage = showMessage,
                                onOpenDexPermissions = { showDexPermissionsDialog = true },
                                onOpenTaskManager = { currentDest = MainDestination.TASK_MANAGER }
                            )
                        }
                    }
                }

                if (settings.developerModeEnabled && settings.showDevHud) {
                    var heapUsedMb by remember { mutableLongStateOf(0L) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            val rt = Runtime.getRuntime()
                            heapUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)
                            delay(1500L)
                        }
                    }
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "DEV • ${config.screenWidthDp}x${config.screenHeightDp}dp • WV:${browserViewModel.webViewPool.getLiveCount()} • RAM:${heapUsedMb}MB • ${currentDest.label}",
                            color = DeckGreen,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
        }
    }

    if (showDexPermissionsDialog) {
        DexPermissionsSetupDialog(
            onDismiss = { showDexPermissionsDialog = false },
            onDismissForever = {
                showDexPermissionsDialog = false
                scope.launch {
                    app.settingsRepository.updateSettings { it.copy(dexSetupPromptDismissed = true) }
                }
            },
            onShowMessage = showMessage
        )
    }

    if (showExternalDetectedDialog) {
        val detectedName = removableVolumes.firstOrNull()?.label
            ?: driveStatus.displayName.takeIf { driveStatus.isConnected }
            ?: "External Storage"
        var chosenArchiveTarget by remember(settings.archiveStorageTarget, driveStatus.isWritable) {
            mutableStateOf(
                if (driveStatus.isWritable) StorageTarget.DECK_DRIVE else settings.archiveStorageTarget
            )
        }
        var chosenDownloadTarget by remember(settings.downloadStorageTarget, driveStatus.isWritable) {
            mutableStateOf(
                if (driveStatus.isWritable) StorageTarget.DECK_DRIVE else settings.downloadStorageTarget
            )
        }

        AlertDialog(
            onDismissRequest = {
                handledExternalKey = currentExternalKey
                showExternalDetectedDialog = false
            },
            title = { Text("External Storage Detected") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Penyimpanan Eksternal Terdeteksi: $detectedName",
                        style = MaterialTheme.typography.titleSmall,
                        color = DeckGreen
                    )
                    if (!driveStatus.isWritable) {
                        Text(
                            text = "Pilih folder pada perangkat eksternal (SAF) agar Offline Archive dan Download otomatis dapat disimpan ke Eksternal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DeckAmber
                        )
                        OutlinedButton(
                            onClick = { externalSafPicker.launch(null) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Pilih Folder Eksternal / SD / USB (SAF)")
                        }
                    } else {
                        Text(
                            text = "Deck Drive aktif: ${driveStatus.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text("Lokasi Default Offline Archive", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StorageTarget.entries.forEach { target ->
                            FilterChip(
                                selected = chosenArchiveTarget == target,
                                enabled = target == StorageTarget.INTERNAL || driveStatus.isWritable,
                                onClick = { chosenArchiveTarget = target },
                                label = { Text(if (target == StorageTarget.INTERNAL) "Internal" else "Eksternal Drive") }
                            )
                        }
                    }

                    Text("Lokasi Default Download", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StorageTarget.entries.forEach { target ->
                            FilterChip(
                                selected = chosenDownloadTarget == target,
                                enabled = target == StorageTarget.INTERNAL || driveStatus.isWritable,
                                onClick = { chosenDownloadTarget = target },
                                label = { Text(if (target == StorageTarget.INTERNAL) "Internal" else "Eksternal Drive") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    handledExternalKey = currentExternalKey
                    showExternalDetectedDialog = false
                    scope.launch {
                        app.settingsRepository.updateSettings {
                            it.copy(
                                archiveStorageTarget = chosenArchiveTarget,
                                downloadStorageTarget = chosenDownloadTarget
                            )
                        }
                        showMessage(
                            "Penyimpanan diperbarui • Archive: ${chosenArchiveTarget.label}, Download: ${chosenDownloadTarget.label}"
                        )
                    }
                }) { Text("Simpan Pengaturan") }
            },
            dismissButton = {
                TextButton(onClick = {
                    handledExternalKey = currentExternalKey
                    showExternalDetectedDialog = false
                }) { Text("Nanti") }
            }
        )
    }
}

private fun postExternalStorageNotification(context: Context, volumeLabel: String) {
    val canPost = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
    if (!canPost) return

    runCatching {
        val channelId = "cyberdeck_storage"
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Storage & External Drives", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_cyber_deck_launcher)
            .setContentTitle("Penyimpanan Eksternal Terdeteksi")
            .setContentText("$volumeLabel siap — atur Offline Archive & Download (Internal/Eksternal).")
            .setAutoCancel(true)
            .build()
        nm.notify(4011, notif)
    }
}
