package com.offlinecyberdeck.settings

import android.content.Intent
import android.graphics.BitmapFactory
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinecyberdeck.BuildConfig
import com.offlinecyberdeck.CyberDeckApplication
import com.offlinecyberdeck.core.database.entity.AppSettingsEntity
import com.offlinecyberdeck.core.format.Formatters
import com.offlinecyberdeck.core.model.AccentColorPreset
import com.offlinecyberdeck.core.model.DesktopBackgroundPreset
import com.offlinecyberdeck.core.model.SearchEngineOption
import com.offlinecyberdeck.core.model.StorageDashboardStats
import com.offlinecyberdeck.core.model.StorageTarget
import com.offlinecyberdeck.core.model.ThemeMode
import com.offlinecyberdeck.core.settings.TerminalCustomTargetType
import com.offlinecyberdeck.desktop.DesktopLauncherManager
import com.offlinecyberdeck.desktop.DesktopWindowKind
import com.offlinecyberdeck.desktop.InstalledAppInfo
import com.offlinecyberdeck.ui.theme.DeckAmber
import com.offlinecyberdeck.ui.theme.DeckGreen
import com.offlinecyberdeck.ui.theme.DeckRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    app: CyberDeckApplication,
    onShowMessage: (String) -> Unit,
    onOpenDexPermissions: () -> Unit = {},
    onOpenTaskManager: () -> Unit = {},
    onOpenGuide: (() -> Unit)? = null
) {
    val settings by app.settingsRepository.settingsFlow
        .collectAsStateWithLifecycle(initialValue = AppSettingsEntity())
    val siteSettings by app.database.siteSettingDao().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val desktopIconSizeDp by app.settingsRepository.desktopIconSizeDpFlow
        .collectAsStateWithLifecycle()
    val desktopGridCols by app.settingsRepository.desktopGridColsFlow
        .collectAsStateWithLifecycle()
    val desktopGridRows by app.settingsRepository.desktopGridRowsFlow
        .collectAsStateWithLifecycle()
    val appDrawerIconSizeDp by app.settingsRepository.appDrawerIconSizeDpFlow
        .collectAsStateWithLifecycle()
    val terminalCustomCommands by app.settingsRepository.terminalCustomCommandsFlow
        .collectAsStateWithLifecycle()
    val hardwareKeyboardPopupEnabled by app.settingsRepository.hardwareKeyboardPopupEnabledFlow
        .collectAsStateWithLifecycle()

    var showCustomCommandDialog by remember { mutableStateOf(false) }
    var installedAppsForCustomCmd by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }

    var dashboard by remember { mutableStateOf(StorageDashboardStats()) }
    var customTemplateDraft by remember(settings.customSearchTemplate) {
        mutableStateOf(settings.customSearchTemplate)
    }
    var excludedHostsDraft by remember(settings.archiveExcludedHosts) {
        mutableStateOf(settings.archiveExcludedHosts)
    }
    var confirmClearBrowsing by remember { mutableStateOf(false) }
    var showSafeEjectDialog by remember { mutableStateOf(false) }
    var pendingWallpaperSlot by remember { mutableIntStateOf(0) }
    var deviceDetails by remember { mutableStateOf(DesktopLauncherManager.readDeviceSystemDetails(context)) }
    var liveUsage by remember { mutableStateOf(DesktopLauncherManager.readSystemUsage(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                val u = DesktopLauncherManager.readSystemUsage(context)
                val d = DesktopLauncherManager.readDeviceSystemDetails(context)
                u to d
            }.let { (u, d) ->
                liveUsage = u
                deviceDetails = d
            }
            delay(2000L)
        }
    }

    val customWallpaperPaths = remember(settings.customWallpaperPaths) {
        settings.customWallpaperPaths
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(4)
    }

    val wallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                app.driveManager.copyAndResizeWallpaper(uri)
                    .onSuccess { savedPath ->
                        val nextList = customWallpaperPaths.toMutableList()
                        val slot = pendingWallpaperSlot.coerceIn(0, 3)
                        if (slot < nextList.size) {
                            nextList[slot] = savedPath
                        } else {
                            nextList.add(savedPath)
                        }
                        val trimmed = nextList.take(4)
                        val activeIdx = if (slot < trimmed.size) slot else trimmed.lastIndex
                        app.settingsRepository.updateSettings {
                            it.copy(
                                customWallpaperPaths = trimmed.joinToString("\n"),
                                activeCustomWallpaperIndex = activeIdx
                            )
                        }
                        onShowMessage("Wallpaper #${activeIdx + 1} tersimpan & aktif")
                    }
                    .onFailure { err ->
                        onShowMessage("Gagal import wallpaper: ${err.message}")
                    }
            }
        }
    }

    suspend fun refreshDashboard() {
        dashboard = app.settingsRepository.computeStorageDashboard()
    }

    LaunchedEffect(settings.deckDriveTreeUri) {
        refreshDashboard()
    }

    val deckDrivePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val status = app.driveManager.attachDeckDrive(uri)
                app.settingsRepository.updateSettings {
                    it.copy(
                        deckDriveTreeUri = uri.toString(),
                        deckDriveLabel = status.displayName
                    )
                }
                refreshDashboard()
                onShowMessage("Deck Drive selected: ${status.displayName} (${status.statusDetail})")
            }
        }
    }

    val backupExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                app.settingsRepository.exportBackupToUri(uri)
                    .onSuccess { onShowMessage(it) }
                    .onFailure { onShowMessage("Backup export failed: ${it.message}") }
            }
        }
    }

    val backupImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                app.settingsRepository.importBackupFromUri(uri)
                    .onSuccess {
                        refreshDashboard()
                        onShowMessage(it)
                    }
                    .onFailure { onShowMessage("Restore failed: ${it.message}") }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Settings & Deck Drive", style = MaterialTheme.typography.titleLarge)
                if (onOpenGuide != null) {
                    OutlinedButton(onClick = onOpenGuide) {
                        Icon(Icons.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Panduan")
                    }
                }
            }
        }

        // DECK DRIVE & STORAGE DASHBOARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Deck Drive (External USB-C SSD via SAF)", style = MaterialTheme.typography.titleMedium)
                    val drv = dashboard.deckDrive
                    Text(
                        text = "Status: ${drv.displayName} — ${drv.statusDetail}",
                        color = when {
                            drv.isConnected && drv.isWritable -> DeckGreen
                            drv.isConnected -> DeckAmber
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "External Capacity: ${Formatters.formatBytes(drv.availableBytes)} free / ${Formatters.formatBytes(drv.totalBytes)} total",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "Internal Storage: ${Formatters.formatBytes(dashboard.internalAvailableBytes)} free / ${Formatters.formatBytes(dashboard.internalTotalBytes)} total",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "Catalog Counts: ${dashboard.archiveCount} Archives • ${dashboard.libraryCount} Vault Docs • ${dashboard.mediaLocalCount} Local Media • ${dashboard.mediaMetadataOnlyCount} Metadata Only • ${dashboard.downloadCount} Downloads",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { deckDrivePicker.launch(null) }) {
                            Text(if (drv.configuredUri == null) "Select SSD Folder" else "Change Folder")
                        }
                        OutlinedButton(onClick = {
                            scope.launch {
                                refreshDashboard()
                                onShowMessage("Deck Drive status refreshed: ${dashboard.deckDrive.statusDetail}")
                            }
                        }) {
                            Text("Scan Drive")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = drv.isWritable && drv.configuredUri != null,
                            onClick = {
                                val uri = drv.configuredUri ?: return@OutlinedButton
                                scope.launch {
                                    app.driveManager.runWriteReadSelfTest(uri)
                                        .onSuccess { onShowMessage(it) }
                                        .onFailure { onShowMessage("Self-test failed: ${it.message}") }
                                }
                            }
                        ) {
                            Text("Write/Read Test")
                        }
                        OutlinedButton(onClick = { showSafeEjectDialog = true }) {
                            Text("Safe Eject Info")
                        }
                        if (drv.configuredUri != null) {
                            TextButton(onClick = {
                                scope.launch {
                                    app.driveManager.detachDeckDrive(drv.configuredUri)
                                    app.settingsRepository.updateSettings {
                                        it.copy(
                                            deckDriveTreeUri = null,
                                            deckDriveLabel = null,
                                            archiveStorageTarget = StorageTarget.INTERNAL,
                                            downloadStorageTarget = StorageTarget.INTERNAL
                                        )
                                    }
                                    refreshDashboard()
                                    onShowMessage("Deck Drive permission forgotten.")
                                }
                            }) {
                                Text("Disconnect", color = DeckRed)
                            }
                        }
                    }
                }
            }
        }

        // APPEARANCE & UI
        item {
            SectionCard("Appearance & Browser Layout") {
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = {
                                scope.launch { app.settingsRepository.updateSettings { it.copy(themeMode = mode) } }
                            },
                            label = { Text(mode.label) }
                        )
                    }
                }
                SettingSwitchRow(
                    title = "Show Cyber Deck Status Strip",
                    subtitle = "Displays ONLINE/OFFLINE and Deck Drive capacity below toolbar",
                    checked = settings.showStatusStrip,
                    onCheckedChange = { v ->
                        scope.launch { app.settingsRepository.updateSettings { it.copy(showStatusStrip = v) } }
                    }
                )
                SettingSwitchRow(
                    title = "Show Recent Pages on New Tab",
                    subtitle = "Display recent non-private pages on New Tab page",
                    checked = settings.showRecentOnNewTab,
                    onCheckedChange = { v ->
                        scope.launch { app.settingsRepository.updateSettings { it.copy(showRecentOnNewTab = v) } }
                    }
                )
                SettingSwitchRow(
                    title = "Hide Toolbar in Landscape",
                    subtitle = "Auto-hides browser toolbar when rotated to landscape; displays floating arrow to unhide",
                    checked = settings.hideToolbarInLandscape,
                    onCheckedChange = { v ->
                        scope.launch { app.settingsRepository.updateSettings { it.copy(hideToolbarInLandscape = v) } }
                    }
                )
                OutlinedButton(
                    onClick = {
                        val next = !settings.hideToolbarInLandscape
                        scope.launch { app.settingsRepository.updateSettings { it.copy(hideToolbarInLandscape = next) } }
                        onShowMessage(
                            if (next) "Landscape toolbar auto-hide enabled (floating arrow active)"
                            else "Landscape toolbar auto-hide disabled"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (settings.hideToolbarInLandscape) "Landscape Toolbar: Auto-Hidden (Tap to Always Show)"
                        else "Hide Toolbar When Landscape"
                    )
                }
                SettingSwitchRow(
                    title = "Browser WebView Dark Mode",
                    subtitle = "Forces dark theme styling on websites opened inside Cyber Deck Browser",
                    checked = settings.webViewDarkMode,
                    onCheckedChange = { v ->
                        scope.launch { app.settingsRepository.updateSettings { it.copy(webViewDarkMode = v) } }
                    }
                )
                SettingSwitchRow(
                    title = "DeX Desktop Launcher Mode",
                    subtitle = "Shows desktop shortcuts, floating windows (Minimize/Maximize/Close), taskbar & App Drawer",
                    checked = settings.desktopLauncherMode,
                    onCheckedChange = { v ->
                        scope.launch { app.settingsRepository.updateSettings { it.copy(desktopLauncherMode = v) } }
                    }
                )
                SettingSwitchRow(
                    title = "Auto-Hide Taskbar DeX (Panah Mengambang)",
                    subtitle = "Menyembunyikan taskbar bawah dan menggantinya dengan panah mengambang",
                    checked = settings.taskbarAutoHide,
                    onCheckedChange = { v ->
                        scope.launch { app.settingsRepository.updateSettings { it.copy(taskbarAutoHide = v) } }
                    }
                )
                SettingSwitchRow(
                    title = "Suara Notifikasi",
                    subtitle = "Memutar nada notifikasi saat notif baru & popup mengambang muncul di kanan atas",
                    checked = settings.notificationSoundEnabled,
                    onCheckedChange = { v ->
                        scope.launch { app.settingsRepository.updateSettings { it.copy(notificationSoundEnabled = v) } }
                    }
                )
                SettingSwitchRow(
                    title = "Popup Input Keyboard Fisik (BT / USB)",
                    subtitle = "Menampilkan kotak pop-up inputan real-time di kanan bawah (~3/4 layar) saat mengetik ke textbox lewat keyboard Bluetooth/USB, lalu hilang 0.3 detik setelah Enter",
                    checked = hardwareKeyboardPopupEnabled,
                    onCheckedChange = { v ->
                        app.settingsRepository.setHardwareKeyboardPopupEnabled(v)
                        onShowMessage(
                            if (v) "Popup keyboard fisik (BT / USB) diaktifkan"
                            else "Popup keyboard fisik (BT / USB) dinonaktifkan"
                        )
                    }
                )
                OutlinedButton(
                    onClick = onOpenDexPermissions,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Izin DeX Launcher & Floating Apps (WA, YouTube)")
                }
                OutlinedButton(
                    onClick = { onShowMessage(DesktopLauncherManager.openSystemNavigationSettings(context)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pengaturan Navigasi Android (Beralih ke Gesture / Sembunyikan 3 Tombol)")
                }
            }
        }

        // DESKTOP BACKGROUND & WALLPAPERS
        item {
            SectionCard("Desktop Background & Custom Wallpaper (Maks. 4)") {
                Text("6 Pilihan Background Sederhana", style = MaterialTheme.typography.titleSmall)
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

                Text("Custom Wallpaper dari Perangkat (Maks. 4 Slot)", style = MaterialTheme.typography.titleSmall)
                for (slot in 0 until 4) {
                    val path = customWallpaperPaths.getOrNull(slot)
                    val isActive = settings.activeCustomWallpaperIndex == slot && path != null
                    var thumbBmp by remember(path) { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(path) {
                        thumbBmp = if (path != null) {
                            withContext(Dispatchers.IO) {
                                val f = File(path)
                                if (f.exists()) runCatching { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }.getOrNull() else null
                            }
                        } else null
                    }
                    Surface(
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (thumbBmp != null) {
                                Image(
                                    bitmap = thumbBmp!!,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("#${slot + 1}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Text(
                                text = if (path != null) "Wallpaper #${slot + 1}" else "Slot #${slot + 1} (Kosong)",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            if (path != null) {
                                TextButton(onClick = {
                                    scope.launch {
                                        app.settingsRepository.updateSettings { it.copy(activeCustomWallpaperIndex = slot) }
                                    }
                                }) {
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
                            OutlinedButton(onClick = {
                                pendingWallpaperSlot = slot
                                wallpaperPicker.launch(arrayOf("image/*"))
                            }) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (path != null) "Ganti" else "Import")
                            }
                        }
                    }
                }
            }
        }

        // MEDIA AUTO-IMPORT & ALL FILES PERMISSION
        item {
            val allFilesOk = DesktopLauncherManager.hasAllFilesAccess(context)
            SectionCard("MP3 Player, OfflineTube & File Access") {
                SettingSwitchRow(
                    title = "Auto-Import MP3 (MP3 Player)",
                    subtitle = "Otomatis scan lagu MP3/audio baru dari Internal, SD Card & Eksternal tanpa duplikat",
                    checked = settings.musicAutoImport,
                    onCheckedChange = { v ->
                        scope.launch { app.settingsRepository.updateSettings { it.copy(musicAutoImport = v) } }
                    }
                )
                SettingSwitchRow(
                    title = "Auto-Import Video (OfflineTube)",
                    subtitle = "Otomatis scan video baru dari Internal, SD Card & Eksternal tanpa duplikat",
                    checked = settings.offlineTubeAutoImport,
                    onCheckedChange = { v ->
                        scope.launch { app.settingsRepository.updateSettings { it.copy(offlineTubeAutoImport = v) } }
                    }
                )
                Text(
                    text = if (allFilesOk) "Akses Semua File (File Explorer, Offline Search & Media): AKTIF"
                    else "Akses Semua File belum aktif — aktifkan agar bisa mencari & menjelajah semua file Internal/SD/USB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (allFilesOk) DeckGreen else DeckAmber
                )
                OutlinedButton(
                    onClick = { onShowMessage(DesktopLauncherManager.openAllFilesAccessSettings(context)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (allFilesOk) "Kelola Izin Semua File" else "Izinkan Akses Semua File")
                }
            }
        }

        // DEVELOPER & DISPLAY OPTIONS
        item {
            SectionCard("Developer & Display Options") {
                SettingSwitchRow(
                    title = "Enable Developer Options",
                    subtitle = "Unlocks HTML Source Inspector, Dev HUD, and WebView debugging",
                    checked = settings.developerModeEnabled,
                    onCheckedChange = { v ->
                        scope.launch { app.settingsRepository.updateSettings { it.copy(developerModeEnabled = v) } }
                    }
                )

                Text("Accent Color Palette", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AccentColorPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = settings.accentPreset == preset,
                            onClick = {
                                scope.launch { app.settingsRepository.updateSettings { it.copy(accentPreset = preset) } }
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(preset.darkPrimaryArgb))
                                )
                            },
                            label = { Text(preset.label) }
                        )
                    }
                }

                SettingSwitchRow(
                    title = "Override Screen Brightness",
                    subtitle = "Controls in-app window brightness independently from system auto",
                    checked = settings.brightnessOverrideEnabled,
                    onCheckedChange = { v ->
                        scope.launch { app.settingsRepository.updateSettings { it.copy(brightnessOverrideEnabled = v) } }
                    }
                )
                if (settings.brightnessOverrideEnabled) {
                    Text("App Screen Brightness: ${settings.screenBrightnessPercent}%")
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

                Text("Extra Night Dimmer Overlay: ${settings.nightDimmerPercent}%")
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

                SettingSwitchRow(
                    title = "Immersive Fullscreen Mode",
                    subtitle = "Hides Android status bar & system navigation bars (swipe edge to reveal)",
                    checked = settings.immersiveFullscreen,
                    onCheckedChange = { v ->
                        scope.launch { app.settingsRepository.updateSettings { it.copy(immersiveFullscreen = v) } }
                    }
                )
                SettingSwitchRow(
                    title = "Hide App Navigation Bar in Browser",
                    subtitle = "Maximizes web viewport height; use 'Exit Full' pill or menu to return",
                    checked = settings.hideNavChromeInBrowser,
                    onCheckedChange = { v ->
                        scope.launch { app.settingsRepository.updateSettings { it.copy(hideNavChromeInBrowser = v) } }
                    }
                )

                SettingSwitchRow(
                    title = "Bottom Address Bar",
                    subtitle = "Moves browser toolbar & URL bar to bottom for comfortable thumb reach",
                    checked = settings.bottomAddressBar,
                    onCheckedChange = { v ->
                        scope.launch { app.settingsRepository.updateSettings { it.copy(bottomAddressBar = v) } }
                    }
                )
                SettingSwitchRow(
                    title = "Show Browser Tab Strip",
                    subtitle = "Display horizontal tab bar above/below address bar",
                    checked = settings.showTabStrip,
                    onCheckedChange = { v ->
                        scope.launch { app.settingsRepository.updateSettings { it.copy(showTabStrip = v) } }
                    }
                )

                Text("Address Bar Height: ${settings.addressBarHeightDp} dp")
                Slider(
                    value = settings.addressBarHeightDp.toFloat(),
                    onValueChange = { v ->
                        scope.launch {
                            app.settingsRepository.updateSettings {
                                it.copy(addressBarHeightDp = v.roundToInt().coerceIn(30, 64))
                            }
                        }
                    },
                    valueRange = 30f..64f
                )

                Text("Address Bar Text Size: ${settings.addressBarFontSp} sp")
                Slider(
                    value = settings.addressBarFontSp.toFloat(),
                    onValueChange = { v ->
                        scope.launch {
                            app.settingsRepository.updateSettings {
                                it.copy(addressBarFontSp = v.roundToInt().coerceIn(11, 20))
                            }
                        }
                    },
                    valueRange = 11f..20f,
                    steps = 8
                )

                Text("Address Bar Corner Radius: ${settings.addressBarCornerDp} dp")
                Slider(
                    value = settings.addressBarCornerDp.toFloat(),
                    onValueChange = { v ->
                        scope.launch {
                            app.settingsRepository.updateSettings {
                                it.copy(addressBarCornerDp = v.roundToInt().coerceIn(0, 24))
                            }
                        }
                    },
                    valueRange = 0f..24f
                )

                Text("Toolbar Icon Size: ${settings.toolbarButtonSizeDp} dp")
                Slider(
                    value = settings.toolbarButtonSizeDp.toFloat(),
                    onValueChange = { v ->
                        scope.launch {
                            app.settingsRepository.updateSettings {
                                it.copy(toolbarButtonSizeDp = v.roundToInt().coerceIn(26, 44))
                            }
                        }
                    },
                    valueRange = 26f..44f
                )

                Text("Global UI Font Scale: ${(settings.uiFontScale * 100).roundToInt()}% (Default: 95%)")
                Slider(
                    value = settings.uiFontScale.coerceIn(0.80f, 1.25f),
                    onValueChange = { v ->
                        val snapped = ((v * 20f).roundToInt() / 20f).coerceIn(0.80f, 1.25f)
                        scope.launch {
                            app.settingsRepository.updateSettings { it.copy(uiFontScale = snapped) }
                        }
                    },
                    valueRange = 0.80f..1.25f,
                    steps = 8
                )

                Text("WebView Page Text Zoom: ${settings.webViewTextZoom}%")
                Slider(
                    value = settings.webViewTextZoom.toFloat(),
                    onValueChange = { v ->
                        val snapped = ((v / 5f).roundToInt() * 5).coerceIn(75, 150)
                        scope.launch {
                            app.settingsRepository.updateSettings { it.copy(webViewTextZoom = snapped) }
                        }
                    },
                    valueRange = 75f..150f,
                    steps = 14
                )

                if (settings.developerModeEnabled) {
                    SettingSwitchRow(
                        title = "Developer HUD Overlay",
                        subtitle = "Shows live RAM heap, active WebView count, and screen dp",
                        checked = settings.showDevHud,
                        onCheckedChange = { v ->
                            scope.launch { app.settingsRepository.updateSettings { it.copy(showDevHud = v) } }
                        }
                    )
                    SettingSwitchRow(
                        title = "WebView USB Debugging",
                        subtitle = "Allows chrome://inspect over ADB from PC",
                        checked = settings.webViewDebuggingEnabled,
                        onCheckedChange = { v ->
                            scope.launch { app.settingsRepository.updateSettings { it.copy(webViewDebuggingEnabled = v) } }
                        }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        runCatching {
                            WebView(context).apply {
                                clearCache(true)
                                destroy()
                            }
                        }
                        onShowMessage("WebView HTTP disk cache cleared")
                    }) {
                        Text("Clear Web Cache")
                    }
                    OutlinedButton(onClick = {
                        scope.launch {
                            app.settingsRepository.updateSettings {
                                it.copy(
                                    accentPreset = AccentColorPreset.CYBER_CYAN,
                                    brightnessOverrideEnabled = false,
                                    screenBrightnessPercent = 80,
                                    nightDimmerPercent = 0,
                                    immersiveFullscreen = true,
                                    hideNavChromeInBrowser = false,
                                    addressBarHeightDp = 40,
                                    addressBarFontSp = 14,
                                    addressBarCornerDp = 10,
                                    toolbarButtonSizeDp = 34,
                                    showTabStrip = true,
                                    bottomAddressBar = false,
                                    hideToolbarInLandscape = false,
                                    uiFontScale = 0.95f,
                                    webViewTextZoom = 100,
                                    showDevHud = false,
                                    webViewDebuggingEnabled = false,
                                    webViewDarkMode = true,
                                    taskbarAutoHide = false,
                                    notificationSoundEnabled = true,
                                    desktopBackgroundId = "CYBER_NAVY",
                                    activeCustomWallpaperIndex = -1
                                )
                            }
                            app.settingsRepository.setDesktopIconSizeDp(46)
                            app.settingsRepository.setDesktopGrid(6, 4)
                            app.settingsRepository.setAppDrawerIconSizeDp(26)
                            onShowMessage("Display & developer options reset to defaults (95% UI scale)")
                        }
                    }) {
                        Text("Reset Display")
                    }
                }
            }
        }

        // DESKTOP GRID, ICON SIZES & TERMINAL CUSTOM COMMANDS (GUI)
        item {
            SectionCard("Desktop Grid, Ukuran Ikon & Terminal Custom Commands (GUI)") {
                Text(
                    "Grid Desktop: $desktopGridCols Kolom × $desktopGridRows Baris (Total ${desktopGridCols * desktopGridRows} slot)",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "5×4" to (5 to 4),
                        "6×4 (Standar)" to (6 to 4),
                        "7×4" to (7 to 4),
                        "8×5" to (8 to 5)
                    ).forEach { (label, pair) ->
                        FilterChip(
                            selected = desktopGridCols == pair.first && desktopGridRows == pair.second,
                            onClick = { app.settingsRepository.setDesktopGrid(pair.first, pair.second) },
                            label = { Text(label) }
                        )
                    }
                }
                Text("Jumlah Kolom Grid: $desktopGridCols kolom")
                Slider(
                    value = desktopGridCols.toFloat(),
                    onValueChange = { v ->
                        app.settingsRepository.setDesktopGrid(v.roundToInt().coerceIn(4, 10), desktopGridRows)
                    },
                    valueRange = 4f..10f,
                    steps = 5
                )
                Text("Jumlah Baris Grid: $desktopGridRows baris")
                Slider(
                    value = desktopGridRows.toFloat(),
                    onValueChange = { v ->
                        app.settingsRepository.setDesktopGrid(desktopGridCols, v.roundToInt().coerceIn(3, 7))
                    },
                    valueRange = 3f..7f,
                    steps = 3
                )
                OutlinedButton(onClick = {
                    app.settingsRepository.resetDesktopIconPositions()
                    onShowMessage("Posisi ikon desktop dirapikan mengikuti grid ${desktopGridCols}×${desktopGridRows}.")
                }) {
                    Text("Rapikan Posisi Ikon ke Grid ${desktopGridCols}×${desktopGridRows}")
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                Text(
                    "Ukuran Ikon Desktop: $desktopIconSizeDp dp",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Kecil" to 38, "Normal" to 46, "Besar" to 58, "Extra" to 66).forEach { (label, dpVal) ->
                        FilterChip(
                            selected = desktopIconSizeDp == dpVal,
                            onClick = { app.settingsRepository.setDesktopIconSizeDp(dpVal) },
                            label = { Text("$label (${dpVal}dp)") }
                        )
                    }
                }
                Slider(
                    value = desktopIconSizeDp.toFloat(),
                    onValueChange = { v ->
                        app.settingsRepository.setDesktopIconSizeDp(v.roundToInt().coerceIn(36, 68))
                    },
                    valueRange = 36f..68f
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                Text(
                    "Ukuran Ikon Bawaan di App Drawer: $appDrawerIconSizeDp dp",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Atur ukuran ikon fitur Cyber Deck di App Drawer agar ringkas & berbeda dari aplikasi Android terinstall.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Kecil" to 22, "Ringkas" to 26, "Sedang" to 32, "Besar" to 40).forEach { (label, dpVal) ->
                        FilterChip(
                            selected = appDrawerIconSizeDp == dpVal,
                            onClick = { app.settingsRepository.setAppDrawerIconSizeDp(dpVal) },
                            label = { Text("$label (${dpVal}dp)") }
                        )
                    }
                }
                Slider(
                    value = appDrawerIconSizeDp.toFloat(),
                    onValueChange = { v ->
                        app.settingsRepository.setAppDrawerIconSizeDp(v.roundToInt().coerceIn(20, 44))
                    },
                    valueRange = 20f..44f,
                    steps = 5
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Terminal Custom Commands (${terminalCustomCommands.size})",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "Buat perintah singkat di PowerShell Terminal untuk membuka aplikasi Cyber Deck atau aplikasi Android terinstall.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                if (installedAppsForCustomCmd.isEmpty()) {
                                    installedAppsForCustomCmd = DesktopLauncherManager.loadInstalledApps(context)
                                }
                                showCustomCommandDialog = true
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tambah")
                    }
                }

                if (terminalCustomCommands.isEmpty()) {
                    Text(
                        "Belum ada custom command terdaftar. Tambahkan lewat tombol 'Tambah' di atas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        terminalCustomCommands.forEach { cmd ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            cmd.command,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "${cmd.label} • ${cmd.targetType.label} (${cmd.targetValue})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            app.settingsRepository.deleteTerminalCustomCommand(cmd.id)
                                            onShowMessage("Custom command '${cmd.command}' dihapus.")
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Hapus Custom Command",
                                            tint = DeckRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SEARCH ENGINE
        item {
            SectionCard("Search Engine") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SearchEngineOption.entries.forEach { opt ->
                        FilterChip(
                            selected = settings.searchEngine == opt,
                            onClick = {
                                scope.launch {
                                    val template = if (opt == SearchEngineOption.CUSTOM) {
                                        customTemplateDraft
                                    } else {
                                        opt.templateUrl
                                    }
                                    app.settingsRepository.updateSettings {
                                        it.copy(searchEngine = opt, customSearchTemplate = template)
                                    }
                                }
                            },
                            label = { Text(opt.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = customTemplateDraft,
                    onValueChange = { customTemplateDraft = it },
                    label = { Text("Search URL template (%s = query)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = {
                    scope.launch {
                        app.settingsRepository.updateSettings {
                            it.copy(
                                searchEngine = SearchEngineOption.CUSTOM,
                                customSearchTemplate = customTemplateDraft.trim().ifBlank { SearchEngineOption.GOOGLE.templateUrl }
                            )
                        }
                        onShowMessage("Search engine template saved")
                    }
                }) {
                    Text("Save Search Template")
                }
            }
        }

        // ARCHIVE & DOWNLOAD STORAGE TARGETS
        item {
            SectionCard("Offline Archives & Downloads") {
                Text("Default Archive Location", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorageTarget.entries.forEach { target ->
                        FilterChip(
                            selected = settings.archiveStorageTarget == target,
                            enabled = target == StorageTarget.INTERNAL || dashboard.deckDrive.isWritable,
                            onClick = {
                                scope.launch {
                                    app.settingsRepository.updateSettings { it.copy(archiveStorageTarget = target) }
                                }
                            },
                            label = { Text(target.label) }
                        )
                    }
                }
                Text("Default Download Location", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorageTarget.entries.forEach { target ->
                        FilterChip(
                            selected = settings.downloadStorageTarget == target,
                            enabled = target == StorageTarget.INTERNAL || dashboard.deckDrive.isWritable,
                            onClick = {
                                scope.launch {
                                    app.settingsRepository.updateSettings { it.copy(downloadStorageTarget = target) }
                                }
                            },
                            label = { Text(target.label) }
                        )
                    }
                }
                SettingSwitchRow(
                    title = "Capture Static Images & CSS in Archives",
                    subtitle = "Disable for lightweight text-only Reader Snapshots",
                    checked = settings.archiveCaptureResources,
                    onCheckedChange = { v ->
                        scope.launch { app.settingsRepository.updateSettings { it.copy(archiveCaptureResources = v) } }
                    }
                )
                SettingSwitchRow(
                    title = "Ask Before Starting Web Downloads",
                    subtitle = "Shows confirmation dialog when a page triggers a download",
                    checked = settings.askBeforeDownload,
                    onCheckedChange = { v ->
                        scope.launch { app.settingsRepository.updateSettings { it.copy(askBeforeDownload = v) } }
                    }
                )
                OutlinedTextField(
                    value = excludedHostsDraft,
                    onValueChange = { excludedHostsDraft = it },
                    label = { Text("Archive Excluded Hosts (comma-separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = {
                    scope.launch {
                        app.settingsRepository.updateSettings { it.copy(archiveExcludedHosts = excludedHostsDraft) }
                        onShowMessage("Excluded hosts updated")
                    }
                }) {
                    Text("Save Exclusions")
                }
            }
        }

        // BROWSER, PERFORMANCE & PRIVACY
        item {
            SectionCard("Browser Performance (4 GB RAM) & Privacy") {
                Text("Max Live WebViews in Memory: ${settings.maxLiveWebViews}")
                Slider(
                    value = settings.maxLiveWebViews.toFloat(),
                    onValueChange = { v ->
                        scope.launch {
                            app.settingsRepository.updateSettings { it.copy(maxLiveWebViews = v.roundToInt()) }
                        }
                    },
                    valueRange = 1f..5f,
                    steps = 3
                )
                SettingSwitchRow(
                    title = "Global JavaScript",
                    subtitle = "Disable to harden static browsing; may break interactive sites",
                    checked = settings.globalJavaScriptEnabled,
                    onCheckedChange = { v ->
                        scope.launch { app.settingsRepository.updateSettings { it.copy(globalJavaScriptEnabled = v) } }
                    }
                )
                SettingSwitchRow(
                    title = "Default Desktop User-Agent",
                    subtitle = "Requests desktop layout by default on new tabs",
                    checked = settings.defaultDesktopMode,
                    onCheckedChange = { v ->
                        scope.launch { app.settingsRepository.updateSettings { it.copy(defaultDesktopMode = v) } }
                    }
                )
                SettingSwitchRow(
                    title = "Record Browsing History",
                    subtitle = "Private tabs never save history regardless of this setting",
                    checked = settings.recordHistory,
                    onCheckedChange = { v ->
                        scope.launch { app.settingsRepository.updateSettings { it.copy(recordHistory = v) } }
                    }
                )
                Button(onClick = { confirmClearBrowsing = true }) {
                    Text("Clear Browsing History & Cookies")
                }

                if (siteSettings.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Per-Site Overrides (${siteSettings.size})", style = MaterialTheme.typography.titleSmall)
                    siteSettings.forEach { site ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(site.host, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "JS: ${if (site.javaScriptEnabled) "ON" else "OFF"} • Desktop: ${if (site.desktopMode) "ON" else "OFF"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    app.database.siteSettingDao().deleteHost(site.host)
                                    onShowMessage("Reset overrides for ${site.host}")
                                }
                            }) {
                                Text("Reset")
                            }
                        }
                    }
                }
            }
        }

        // BACKUP & DIAGNOSTICS
        item {
            SectionCard("Catalog Backup, Restore & Diagnostics") {
                Text(
                    "Exports bookmarks, archive index, Vault catalog, media metadata, and settings as JSON (large media/archive files remain on storage).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { backupExporter.launch("cyberdeck_backup.json") }) {
                        Text("Export Catalog")
                    }
                    OutlinedButton(onClick = { backupImporter.launch(arrayOf("application/json")) }) {
                        Text("Restore Catalog")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Version ${BuildConfig.VERSION_NAME} • Offline-First • Zero Telemetry",
                    style = MaterialTheme.typography.labelMedium
                )
                OutlinedButton(onClick = {
                    scope.launch {
                        val file = app.settingsRepository.exportDiagnosticsFile()
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Export Diagnostics (No URLs)"))
                    }
                }) {
                    Text("Export Diagnostics Log")
                }
            }
        }

        // TENTANG APLIKASI & INFO SISTEM
        item {
            SectionCard("Tentang Aplikasi & Info Sistem") {
                SettingInfoRow("Nama Aplikasi", deviceDetails.appName)
                SettingInfoRow("Versi", "${deviceDetails.appVersionName} (Build ${deviceDetails.appVersionCode})")
                SettingInfoRow("Developer", deviceDetails.developerName)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                SettingInfoRow("Perangkat", "${deviceDetails.deviceModel} (${deviceDetails.hardwareBoard})")
                SettingInfoRow("Sistem OS", "${deviceDetails.androidVersion} (API ${deviceDetails.sdkInt})")
                SettingInfoRow("Arsitektur CPU", "${deviceDetails.cpuAbis} (${deviceDetails.cpuCores} Core)")
                SettingInfoRow(
                    "RAM Perangkat",
                    "${Formatters.formatBytes(liveUsage.ramUsedMb * 1024L * 1024L)} / ${Formatters.formatBytes(deviceDetails.ramTotalMb * 1024L * 1024L)} (${liveUsage.ramPercent}% • Tersedia ${Formatters.formatBytes(deviceDetails.ramAvailMb * 1024L * 1024L)})"
                )
                SettingInfoRow(
                    "CPU & Suhu",
                    buildString {
                        append("Beban ${liveUsage.cpuPercent}%")
                        liveUsage.cpuTempC?.let { append(String.format(Locale.US, " • %.1f°C", it)) }
                        liveUsage.cpuAvgFreqMhz?.let { append(" • $it MHz") }
                    }
                )
                SettingInfoRow(
                    "Penyimpanan Internal",
                    "Tersedia ${Formatters.formatBytes(deviceDetails.internalAvailBytes)} dari ${Formatters.formatBytes(deviceDetails.internalTotalBytes)}"
                )
                SettingInfoRow("Layar", deviceDetails.screenSummary)
                SettingInfoRow("Waktu Nyala (Uptime)", deviceDetails.uptimeFormatted)
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onOpenTaskManager,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Buka Task Manager (RAM, Suhu, Network & Kill)")
                }
            }
        }
    }

    if (confirmClearBrowsing) {
        AlertDialog(
            onDismissRequest = { confirmClearBrowsing = false },
            title = { Text("Clear Browsing Data?") },
            text = { Text("Clears recorded browsing history, WebView cookies, and WebStorage. Your bookmarks, downloads, and offline archives are preserved.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        app.database.historyDao().clearAll()
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        WebStorage.getInstance().deleteAllData()
                        onShowMessage("Browsing history, cookies, and web storage cleared.")
                    }
                    confirmClearBrowsing = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearBrowsing = false }) { Text("Cancel") }
            }
        )
    }

    if (showSafeEjectDialog) {
        AlertDialog(
            onDismissRequest = { showSafeEjectDialog = false },
            title = { Text("Safe USB-C SSD Eject Guidance") },
            text = {
                Text(
                    "Android does not permit regular apps to programmatically unmount USB drives. Before unplugging your USB-C SSD:\n\n" +
                        "1. Wait for any active downloads or page archives to finish.\n" +
                        "2. Open Android Settings → Storage (or tap the USB notification in the status bar) and tap 'Eject' / 'Unmount'.\n" +
                        "3. Unplug the cable once Android confirms it is safe.\n\n" +
                        "Your catalog entries stay visible and automatically become playable/readable again when you reconnect the drive."
                )
            },
            confirmButton = {
                Button(onClick = { showSafeEjectDialog = false }) { Text("OK") }
            }
        )
    }

    if (showCustomCommandDialog) {
        var cmdKeyword by remember { mutableStateOf("") }
        var targetType by remember { mutableStateOf(TerminalCustomTargetType.IN_APP) }
        var selectedInAppKind by remember { mutableStateOf(DesktopWindowKind.BROWSER) }
        var selectedInstalledPkg by remember(installedAppsForCustomCmd) {
            mutableStateOf(installedAppsForCustomCmd.firstOrNull()?.packageName.orEmpty())
        }
        var selectedInstalledLabel by remember(installedAppsForCustomCmd) {
            mutableStateOf(installedAppsForCustomCmd.firstOrNull()?.label.orEmpty())
        }
        var appFilter by remember { mutableStateOf("") }

        val inAppOptions = remember {
            listOf(
                DesktopWindowKind.BROWSER,
                DesktopWindowKind.SEARCH,
                DesktopWindowKind.OFFLINE_WIKI,
                DesktopWindowKind.AI_OFFLINE,
                DesktopWindowKind.MEDIA,
                DesktopWindowKind.MP3_PLAYER,
                DesktopWindowKind.FILES,
                DesktopWindowKind.LIBRARY,
                DesktopWindowKind.TASK_MANAGER,
                DesktopWindowKind.DOWNLOADS,
                DesktopWindowKind.SETTINGS
            )
        }

        AlertDialog(
            onDismissRequest = { showCustomCommandDialog = false },
            title = { Text("Tambah Custom Command Terminal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = cmdKeyword,
                        onValueChange = { cmdKeyword = it.replace(" ", "_") },
                        label = { Text("Nama Command (cth: wiki, yt, wa, musik)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TerminalCustomTargetType.entries.forEach { t ->
                            FilterChip(
                                selected = targetType == t,
                                onClick = { targetType = t },
                                label = { Text(t.label) }
                            )
                        }
                    }
                    if (targetType == TerminalCustomTargetType.IN_APP) {
                        Text("Pilih Fitur Cyber Deck:", style = MaterialTheme.typography.labelMedium)
                        Column(
                            modifier = Modifier.fillMaxWidth().height(180.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            LazyColumn {
                                items(inAppOptions.size) { idx ->
                                    val k = inAppOptions[idx]
                                    val chosen = k == selectedInAppKind
                                    Surface(
                                        color = if (chosen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth().clickable { selectedInAppKind = k }
                                    ) {
                                        Text(
                                            k.defaultTitle,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = appFilter,
                            onValueChange = { appFilter = it },
                            label = { Text("Cari aplikasi terinstall...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val filteredApps = remember(installedAppsForCustomCmd, appFilter) {
                            val q = appFilter.trim().lowercase(Locale.US)
                            installedAppsForCustomCmd.filter {
                                q.isBlank() || it.label.lowercase(Locale.US).contains(q) || it.packageName.lowercase(Locale.US).contains(q)
                            }.take(40)
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth().height(180.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(6.dp)
                        ) {
                            LazyColumn {
                                items(filteredApps.size) { idx ->
                                    val appItem = filteredApps[idx]
                                    val chosen = appItem.packageName == selectedInstalledPkg
                                    Surface(
                                        color = if (chosen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            selectedInstalledPkg = appItem.packageName
                                            selectedInstalledLabel = appItem.label
                                        }
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                            Text(appItem.label, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal)
                                            Text(
                                                appItem.packageName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = cmdKeyword.trim().isNotBlank() &&
                        (targetType == TerminalCustomTargetType.IN_APP || selectedInstalledPkg.isNotBlank()),
                    onClick = {
                        val (value, label) = if (targetType == TerminalCustomTargetType.IN_APP) {
                            selectedInAppKind.name to selectedInAppKind.defaultTitle
                        } else {
                            selectedInstalledPkg to selectedInstalledLabel.ifBlank { selectedInstalledPkg }
                        }
                        app.settingsRepository.addOrUpdateTerminalCustomCommand(
                            command = cmdKeyword.trim(),
                            label = label,
                            targetType = targetType,
                            targetValue = value
                        )
                        onShowMessage("Custom command '${cmdKeyword.trim().lowercase(Locale.US)}' disimpan.")
                        showCustomCommandDialog = false
                    }
                ) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomCommandDialog = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f)
        )
    }
}
