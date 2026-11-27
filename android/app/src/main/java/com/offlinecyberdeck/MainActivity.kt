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
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
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
import com.offlinecyberdeck.desktop.FloatingNavigationOverlay
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
import com.offlinecyberdeck.music.FloatingMp3PlayerOverlay
import com.offlinecyberdeck.music.FloatingMp3WindowMode
import com.offlinecyberdeck.search.UnifiedSearchScreen
import com.offlinecyberdeck.settings.SettingsScreen
import com.offlinecyberdeck.ui.theme.CyberDeckTheme
import com.offlinecyberdeck.ui.theme.DeckAmber
import com.offlinecyberdeck.ui.theme.DeckGreen
import kotlinx.coroutines.Job
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
    private var hardwareTabTerminalHandler: (() -> Unit)? = null
    private var hardwareBackspaceHandler: (() -> Boolean)? = null
    private var isComposeTextInputFocused: Boolean = false

    private var hwKeyPopupVisible by mutableStateOf(false)
    private var hwKeyBuffer by mutableStateOf("")
    private var hwKeyEnterTriggered by mutableStateOf(false)
    private var hwKeyDeviceName by mutableStateOf("Keyboard Fisik")
    private var hwKeyHideJob: Job? = null

    private val startupPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    private fun isAnyTextboxFocused(): Boolean {
        val focusedView = currentFocus
        if (focusedView is android.widget.EditText) return true
        if (focusedView?.onCheckIsTextEditor() == true) return true
        if (isComposeTextInputFocused) return true
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        return imm != null && imm.isAcceptingText && focusedView != null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val dev = event.device
        val fromPhysicalKeyboard = dev != null &&
            !dev.isVirtual &&
            event.deviceId > 0 &&
            (event.source and InputDevice.SOURCE_KEYBOARD) != 0

        if (fromPhysicalKeyboard && event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_TAB -> {
                        val hasModifiers = event.isAltPressed || event.isCtrlPressed || event.isMetaPressed || event.isShiftPressed
                        if (!hasModifiers) {
                            hardwareTabTerminalHandler?.let { handler ->
                                handler.invoke()
                                return true
                            }
                        }
                    }
                    KeyEvent.KEYCODE_DEL -> {
                        if (!isAnyTextboxFocused()) {
                            if (hardwareBackspaceHandler?.invoke() == true) {
                                return true
                            }
                        }
                    }
                }
            }

            val popupEnabled = (application as? CyberDeckApplication)
                ?.settingsRepository
                ?.isHardwareKeyboardPopupEnabledSync() == true
            val hasShortcutModifier = event.isCtrlPressed || event.isAltPressed || event.isMetaPressed
            if (popupEnabled && !hasShortcutModifier && event.keyCode != KeyEvent.KEYCODE_TAB && isAnyTextboxFocused()) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (event.repeatCount == 0 && (hwKeyPopupVisible || hwKeyBuffer.isNotEmpty())) {
                            hwKeyEnterTriggered = true
                            hwKeyHideJob?.cancel()
                            hwKeyHideJob = lifecycleScope.launch {
                                delay(300L)
                                hwKeyPopupVisible = false
                                hwKeyBuffer = ""
                                hwKeyEnterTriggered = false
                            }
                        }
                    }
                    KeyEvent.KEYCODE_DEL -> {
                        hwKeyHideJob?.cancel()
                        hwKeyEnterTriggered = false
                        if (hwKeyBuffer.isNotEmpty()) {
                            hwKeyBuffer = hwKeyBuffer.dropLast(1)
                            hwKeyPopupVisible = true
                        }
                    }
                    KeyEvent.KEYCODE_ESCAPE -> {
                        hwKeyHideJob?.cancel()
                        hwKeyEnterTriggered = false
                        hwKeyPopupVisible = false
                        hwKeyBuffer = ""
                    }
                    else -> {
                        val unicode = event.getUnicodeChar(event.metaState)
                        val isPrintable = unicode != 0 &&
                            !Character.isISOControl(unicode) &&
                            (unicode and KeyCharacterMap.COMBINING_ACCENT) == 0
                        if (isPrintable) {
                            val ch = unicode.toChar()
                            hwKeyHideJob?.cancel()
                            if (hwKeyEnterTriggered) {
                                hwKeyBuffer = ""
                                hwKeyEnterTriggered = false
                            }
                            val next = hwKeyBuffer + ch
                            hwKeyBuffer = if (next.length > 240) next.takeLast(240) else next
                            val devName = dev?.name?.trim().orEmpty()
                            hwKeyDeviceName = when {
                                devName.contains("usb", ignoreCase = true) -> "USB Keyboard"
                                devName.contains("bluetooth", ignoreCase = true) ||
                                    devName.contains("bt", ignoreCase = true) -> "BT Keyboard"
                                devName.isNotBlank() -> devName.take(22)
                                else -> "Keyboard Fisik"
                            }
                            hwKeyPopupVisible = true
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        forceImmersiveFullscreen(isDark = true)
        handleIncomingIntent(intent)
        requestStartupPermissions()

        val app = application as CyberDeckApplication
        setContent {
            val settings by app.settingsRepository.settingsFlow
                .collectAsStateWithLifecycle(initialValue = AppSettingsEntity())
            val hwPopupEnabled by app.settingsRepository.hardwareKeyboardPopupEnabledFlow
                .collectAsStateWithLifecycle(initialValue = true)
            val browserVm: BrowserViewModel = viewModel(factory = BrowserViewModel.Factory(this))

            LaunchedEffect(hwPopupEnabled) {
                if (!hwPopupEnabled) {
                    hwKeyHideJob?.cancel()
                    hwKeyPopupVisible = false
                    hwKeyBuffer = ""
                    hwKeyEnterTriggered = false
                }
            }

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
                settings.brightnessOverrideEnabled,
                settings.screenBrightnessPercent,
                isDarkNow
            ) {
                forceImmersiveFullscreen(isDark = isDarkNow)
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
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val screenH = maxHeight
                    CyberDeckRootShell(
                        app = app,
                        browserViewModel = browserVm,
                        settings = settings,
                        onRegisterTerminalTabHandler = { hardwareTabTerminalHandler = it },
                        onRegisterBackspaceHandler = { hardwareBackspaceHandler = it },
                        onTextInputFocusChanged = { isComposeTextInputFocused = it }
                    )
                    if (settings.nightDimmerPercent > 0) {
                        val alpha = (settings.nightDimmerPercent.coerceIn(0, 65) / 100f) * 0.85f
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = alpha))
                        )
                    }

                    // Real-time Physical Keyboard (BT / USB) Input Popup at bottom-right (~3/4 down from top)
                    val targetTopOffset = (screenH * 0.72f).coerceIn(16.dp, (screenH - 96.dp).coerceAtLeast(16.dp))
                    AnimatedVisibility(
                        visible = hwPopupEnabled && hwKeyPopupVisible,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(y = targetTopOffset)
                            .padding(end = 16.dp)
                            .zIndex(99999f)
                    ) {
                        HardwareKeyboardInputPopup(
                            deviceLabel = hwKeyDeviceName,
                            typedText = hwKeyBuffer,
                            enterTriggered = hwKeyEnterTriggered
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        forceImmersiveFullscreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            forceImmersiveFullscreen()
        }
    }

    private fun requestStartupPermissions() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            startupPermissionsLauncher.launch(needed.toTypedArray())
        }
    }

    private fun forceImmersiveFullscreen(isDark: Boolean? = null) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (isDark != null) {
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
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
    settings: AppSettingsEntity,
    onRegisterTerminalTabHandler: ((() -> Unit)?) -> Unit,
    onRegisterBackspaceHandler: ((() -> Boolean)?) -> Unit,
    onTextInputFocusChanged: (Boolean) -> Unit
) {
    var currentDest by remember { mutableStateOf(MainDestination.BROWSER) }
    var libraryInitialTab by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<OverlayReader?>(null) }
    var mp3PlayerOpen by remember { mutableStateOf(false) }
    var mp3WindowMode by remember { mutableStateOf(FloatingMp3WindowMode.FLOATING) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val config = LocalConfiguration.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val driveStatus by app.driveManager.status.collectAsStateWithLifecycle()

    val uiPrefs = remember(context) {
        context.getSharedPreferences("cyberdeck_ui_prefs", Context.MODE_PRIVATE)
    }
    var removableVolumes by remember { mutableStateOf<List<ExternalVolumeInfo>>(emptyList()) }
    var acknowledgedExternalKey by remember {
        mutableStateOf(uiPrefs.getString("ack_external_key", null))
    }
    var notifiedExternalKey by remember { mutableStateOf<String?>(null) }
    var showExternalDetectedDialog by remember { mutableStateOf(false) }
    var showExternalRemovedDialog by remember { mutableStateOf(false) }
    var showFirstOnlineSetupDialog by remember {
        mutableStateOf(!app.settingsRepository.isFirstOnlineSetupShownSync())
    }
    var showDexPermissionsDialog by remember { mutableStateOf(false) }
    var autoPromptChecked by remember { mutableStateOf(false) }
    var isGestureNavEnabled by remember {
        mutableStateOf(DesktopLauncherManager.isGestureNavigationEnabled(context))
    }

    LaunchedEffect(
        settings.desktopLauncherMode,
        settings.dexSetupPromptDismissed,
        showFirstOnlineSetupDialog
    ) {
        if (!showFirstOnlineSetupDialog &&
            !autoPromptChecked &&
            settings.desktopLauncherMode &&
            !settings.dexSetupPromptDismissed &&
            !app.settingsRepository.isDexSetupPromptDismissedSync()
        ) {
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
                isGestureNavEnabled = DesktopLauncherManager.isGestureNavigationEnabled(context)
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
        val extraSafList = settings.extraMediaSafUris
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (currentExternalKey == null) {
            notifiedExternalKey = null
            showExternalDetectedDialog = false
            val hadExternal = uiPrefs.getBoolean("had_external_connected", false)
            if (hadExternal) {
                uiPrefs.edit()
                    .remove("ack_external_key")
                    .putBoolean("had_external_connected", false)
                    .apply()
                acknowledgedExternalKey = null
                showExternalRemovedDialog = true
                if (settings.musicAutoImport) {
                    app.musicRepository.autoImportAndRefresh(
                        deckDriveTreeUri = settings.deckDriveTreeUri,
                        extraSafUris = extraSafList
                    )
                }
                if (settings.offlineTubeAutoImport) {
                    app.mediaRepository.autoImportAndRefreshVideos(
                        deckDriveTreeUri = settings.deckDriveTreeUri,
                        extraSafUris = extraSafList
                    )
                }
            }
        } else {
            uiPrefs.edit().putBoolean("had_external_connected", true).apply()
            if (currentExternalKey != acknowledgedExternalKey) {
                showExternalDetectedDialog = true
            }
            if (currentExternalKey != notifiedExternalKey) {
                notifiedExternalKey = currentExternalKey
                if (currentExternalKey != acknowledgedExternalKey) {
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
                }
                if (settings.musicAutoImport) {
                    app.musicRepository.autoImportAndRefresh(
                        deckDriveTreeUri = settings.deckDriveTreeUri,
                        extraSafUris = extraSafList
                    )
                }
                if (settings.offlineTubeAutoImport) {
                    app.mediaRepository.autoImportAndRefreshVideos(
                        deckDriveTreeUri = settings.deckDriveTreeUri,
                        extraSafUris = extraSafList
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
        MainDestination.FILES to Icons.Filled.Folder,
        MainDestination.TASK_MANAGER to Icons.Filled.Speed,
        MainDestination.DOWNLOADS to Icons.Filled.Download,
        MainDestination.BOOKMARKS to Icons.Filled.Bookmark,
        MainDestination.HISTORY to Icons.Filled.History,
        MainDestination.SEARCH to Icons.Filled.Search,
        MainDestination.SETTINGS to Icons.Filled.Settings
    )

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!settings.desktopLauncherMode && !useRail && !hideNavChrome) {
                NavigationBar {
                    val compactItems = listOf(
                        MainDestination.BROWSER to Icons.Filled.Public,
                        MainDestination.LIBRARY to Icons.Filled.MenuBook,
                        MainDestination.MEDIA to Icons.Filled.VideoLibrary,
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
                    onShowMessage = showMessage,
                    onRegisterToggleTerminal = { handler -> onRegisterTerminalTabHandler(handler) },
                    onRegisterActiveBackHandler = { handler -> onRegisterBackspaceHandler(handler) },
                    onTextInputFocusChanged = onTextInputFocusChanged,
                    onOpenFloatingMp3Player = {
                        mp3PlayerOpen = true
                        mp3WindowMode = FloatingMp3WindowMode.FLOATING
                    }
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
                        NavigationRailItem(
                            selected = mp3PlayerOpen,
                            onClick = {
                                mp3PlayerOpen = true
                                mp3WindowMode = FloatingMp3WindowMode.FLOATING
                            },
                            icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = "MP3 Player") },
                            label = { Text("MP3") }
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
                                    onTextInputFocusChanged = onTextInputFocusChanged,
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
                        MainDestination.FILES -> {
                            FileManagerScreen(
                                app = app,
                                onOpenZimPathOrUri = { ref ->
                                    scope.launch {
                                        app.vaultRepository.importOrFindZim(ref)
                                            .onSuccess { item -> overlay = OverlayReader.ZimReader(item.id) }
                                            .onFailure { err -> showMessage("Gagal membuka ZIM: ${err.message}") }
                                    }
                                },
                                onRegisterGoBackHandler = { handler -> onRegisterBackspaceHandler(handler) },
                                onTextInputFocusChanged = onTextInputFocusChanged,
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
                                onTextInputFocusChanged = onTextInputFocusChanged,
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

                if (!isGestureNavEnabled) {
                    FloatingNavigationOverlay(
                        onBack = {
                            if (overlay != null) {
                                overlay = null
                            } else if (currentDest != MainDestination.BROWSER) {
                                currentDest = MainDestination.BROWSER
                            } else {
                                scope.launch {
                                    app.settingsRepository.updateSettings { it.copy(desktopLauncherMode = true) }
                                }
                            }
                        },
                        onHome = {
                            overlay = null
                            scope.launch {
                                app.settingsRepository.updateSettings { it.copy(desktopLauncherMode = true) }
                            }
                        },
                        onRecentsOrDrawer = {
                            overlay = null
                            scope.launch {
                                app.settingsRepository.updateSettings { it.copy(desktopLauncherMode = true) }
                            }
                        },
                        onOpenNavSettings = {
                            showMessage(DesktopLauncherManager.openSystemNavigationSettings(context))
                        },
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
        }
        }
    }

    if (mp3PlayerOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(6000f)
        ) {
            FloatingMp3PlayerOverlay(
                app = app,
                windowMode = mp3WindowMode,
                onChangeWindowMode = { mode -> mp3WindowMode = mode },
                onClose = {
                    mp3PlayerOpen = false
                    mp3WindowMode = FloatingMp3WindowMode.FLOATING
                },
                onTextInputFocusChanged = onTextInputFocusChanged,
                onShowMessage = showMessage
            )
        }
    }
    }

    if (showFirstOnlineSetupDialog) {
        AlertDialog(
            onDismissRequest = {
                app.settingsRepository.markFirstOnlineSetupShown()
                showFirstOnlineSetupDialog = false
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Notifikasi Setup Awal (Online)")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Harap pastikan perangkat terhubung ke internet (Online) untuk proses setup pertama kali.",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Koneksi internet pertama dibutuhkan untuk memuat inisialisasi layanan web offline seperti Offline Wiki (Kiwix PWA) dan penyiapan cache awal sebelum digunakan secara penuh secara offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Pemberitahuan ini hanya muncul 1 kali pada pengaturan pertama.",
                        style = MaterialTheme.typography.labelSmall,
                        color = DeckAmber
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    app.settingsRepository.markFirstOnlineSetupShown()
                    showFirstOnlineSetupDialog = false
                }) {
                    Text("Mengerti & Lanjutkan")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    app.settingsRepository.markFirstOnlineSetupShown()
                    showFirstOnlineSetupDialog = false
                    showMessage(DesktopLauncherManager.openWifiSettings(context))
                }) {
                    Text("Cek Wi-Fi")
                }
            }
        )
    }

    if (showDexPermissionsDialog && !showFirstOnlineSetupDialog) {
        val markDexPromptClosedOnce: () -> Unit = {
            app.settingsRepository.markDexSetupPromptDismissedSync()
            showDexPermissionsDialog = false
            scope.launch {
                app.settingsRepository.updateSettings { it.copy(dexSetupPromptDismissed = true) }
            }
        }
        DexPermissionsSetupDialog(
            onDismiss = markDexPromptClosedOnce,
            onDismissForever = markDexPromptClosedOnce,
            onShowMessage = showMessage
        )
    }

    if (showExternalDetectedDialog && !showFirstOnlineSetupDialog) {
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

        val acknowledgeCurrentExternal: () -> Unit = {
            currentExternalKey?.let { key ->
                acknowledgedExternalKey = key
                uiPrefs.edit().putString("ack_external_key", key).apply()
            }
            showExternalDetectedDialog = false
        }

        AlertDialog(
            onDismissRequest = acknowledgeCurrentExternal,
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
                    acknowledgeCurrentExternal()
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
                TextButton(onClick = acknowledgeCurrentExternal) { Text("Nanti") }
            }
        )
    }

    if (showExternalRemovedDialog) {
        AlertDialog(
            onDismissRequest = { showExternalRemovedDialog = false },
            title = { Text("Penyimpanan Eksternal Terputus") },
            text = {
                Text(
                    "Drive eksternal / SSD sudah tidak terdeteksi. Jika ada Archive atau Download yang masih diarahkan ke Eksternal, Anda bisa mengalihkannya kembali ke Internal Storage.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = {
                    showExternalRemovedDialog = false
                    scope.launch {
                        app.settingsRepository.updateSettings {
                            it.copy(
                                archiveStorageTarget = StorageTarget.INTERNAL,
                                downloadStorageTarget = StorageTarget.INTERNAL
                            )
                        }
                        showMessage("Offline Archive & Download dialihkan ke Internal Storage.")
                    }
                }) { Text("Gunakan Internal") }
            },
            dismissButton = {
                TextButton(onClick = { showExternalRemovedDialog = false }) { Text("Biarkan") }
            }
        )
    }
}

@Composable
private fun HardwareKeyboardInputPopup(
    deviceLabel: String,
    typedText: String,
    enterTriggered: Boolean
) {
    val displayText = if (typedText.isBlank()) {
        if (enterTriggered) "↵ Enter" else "..."
    } else {
        typedText
    }
    Surface(
        color = Color(0xEC0D1524),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (enterTriggered) DeckGreen else Color(0xFF38BDF8).copy(alpha = 0.78f)
        ),
        shadowElevation = 10.dp,
        modifier = Modifier.widthIn(min = 190.dp, max = 360.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Filled.Keyboard,
                    contentDescription = null,
                    tint = if (enterTriggered) DeckGreen else Color(0xFF38BDF8),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = deviceLabel,
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (enterTriggered) {
                    Surface(
                        color = DeckGreen.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "↵ ENTER",
                            color = DeckGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text = displayText,
                color = Color(0xFFF8FAFC),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
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
