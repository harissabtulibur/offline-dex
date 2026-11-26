package com.offlinecyberdeck.desktop

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinecyberdeck.CyberDeckApplication
import com.offlinecyberdeck.core.format.Formatters
import com.offlinecyberdeck.core.settings.TerminalCustomCommand
import com.offlinecyberdeck.core.settings.TerminalCustomTargetType
import kotlinx.coroutines.launch
import java.util.Locale

private val PsBg = Color(0xFF012456)
private val PsHeader = Color(0xFF041A3B)
private val PsInputBg = Color(0xFF01193B)
private val PsPrompt = Color(0xFFFACC15)
private val PsCyan = Color(0xFF38BDF8)
private val PsGreen = Color(0xFF4ADE80)
private val PsRed = Color(0xFFF87171)
private val PsInk = Color(0xFFF1F5F9)
private val PsMuted = Color(0xFF94A3B8)

private enum class TerminalTone(val color: Color) {
    NORMAL(PsInk),
    INFO(PsCyan),
    SUCCESS(PsGreen),
    WARN(PsPrompt),
    ERROR(PsRed),
    MUTED(PsMuted)
}

private data class TerminalLine(
    val id: Long,
    val text: String,
    val tone: TerminalTone = TerminalTone.NORMAL
)

@Composable
fun PowerShellTerminalScreen(
    app: CyberDeckApplication,
    installedApps: List<InstalledAppInfo>,
    openWindowCount: Int,
    customCommands: List<TerminalCustomCommand>,
    onOpenWindow: (DesktopWindowKind) -> Unit,
    onLaunchPackage: (String) -> Unit,
    onCloseTerminal: () -> Unit,
    isWindowFocused: Boolean = true,
    onTextInputFocusChanged: (Boolean) -> Unit = {},
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    val lines = remember {
        mutableStateListOf(
            TerminalLine(1L, "Windows PowerShell [CyberDeck Edition v1.2]", TerminalTone.INFO),
            TerminalLine(2L, "Copyright (C) Offline Cyber Deck. Tekan TAB pada keyboard untuk buka/tutup cepat.", TerminalTone.MUTED),
            TerminalLine(3L, "Ketik 'help' untuk daftar perintah, shortcut sistem, dan custom command.", TerminalTone.WARN),
            TerminalLine(4L, "")
        )
    }
    var nextLineId by remember { mutableStateOf(10L) }
    val commandHistory = remember { mutableStateListOf<String>() }
    var historyCursor by remember { mutableIntStateOf(-1) }
    var commandText by remember { mutableStateOf("") }

    fun appendLine(text: String, tone: TerminalTone = TerminalTone.NORMAL) {
        val id = nextLineId++
        lines.add(TerminalLine(id, text, tone))
        if (lines.size > 400) {
            lines.removeRange(0, lines.size - 400)
        }
    }

    fun printHelp() {
        appendLine("=== BANTUAN TERMINAL CYBER DECK ===", TerminalTone.INFO)
        appendLine("Informasi Perangkat & Aplikasi:", TerminalTone.WARN)
        appendLine("  sysinfo / system      -> Info hardware, Android, CPU, storage & baterai")
        appendLine("  appinfo               -> Info Cyber Deck, versi, izin & window aktif")
        appendLine("  ram / mem             -> Info penggunaan RAM real-time & proses aktif teratas")
        appendLine("Buka Modul In-App:", TerminalTone.WARN)
        appendLine("  open browser          -> Buka Cyber Deck Browser")
        appendLine("  open search           -> Buka Offline Search")
        appendLine("  open wiki             -> Buka Offline Wiki (Kiwix PWA)")
        appendLine("  open tube             -> Buka OfflineTube (Media Vault)")
        appendLine("  open music            -> Buka Cyber Music")
        appendLine("  open files            -> Buka File Explorer")
        appendLine("  open vault            -> Buka Knowledge Vault")
        appendLine("  open taskmgr          -> Buka Task Manager")
        appendLine("  open downloads        -> Buka Tersimpan & Unduhan")
        appendLine("  open settings         -> Buka Pengaturan GUI")
        appendLine("Aplikasi Android Terinstall:", TerminalTone.WARN)
        appendLine("  apps [filter]         -> Lihat daftar aplikasi Android terinstall")
        appendLine("  launch <nama/pkg>     -> Jalankan aplikasi Android terinstall")
        appendLine("Activity System Shortcuts (Android):", TerminalTone.WARN)
        appendLine("  sys wifi | bluetooth | display | storage | battery | dev")
        appendLine("  sys usage | nav | apps | allfiles | datetime | labs")
        appendLine("Custom Commands (diatur lewat Settings GUI):", TerminalTone.WARN)
        if (customCommands.isEmpty()) {
            appendLine("  (Belum ada custom command — tambahkan melalui Settings GUI)", TerminalTone.MUTED)
        } else {
            customCommands.forEach { cmd ->
                appendLine("  ${cmd.command.padEnd(18)} -> ${cmd.label} [${cmd.targetType.label}]", TerminalTone.SUCCESS)
            }
        }
        appendLine("Lainnya: cls / clear, exit (tutup terminal), TAB (toggle cepat).", TerminalTone.MUTED)
    }

    fun executeCommand(rawInput: String) {
        val trimmed = rawInput.trim()
        appendLine("PS C:\\CyberDeck> $trimmed", TerminalTone.WARN)
        if (trimmed.isEmpty()) return

        commandHistory.add(trimmed)
        if (commandHistory.size > 50) commandHistory.removeAt(0)
        historyCursor = -1

        val tokens = trimmed.split(Regex("\\s+"))
        val cmd = tokens.first().lowercase(Locale.US)
        val arg = trimmed.substringAfter(tokens.first(), "").trim()

        // 1. Check User Custom Commands first (from Settings GUI)
        val customMatch = customCommands.firstOrNull { it.command.equals(cmd, ignoreCase = true) }
        if (customMatch != null) {
            when (customMatch.targetType) {
                TerminalCustomTargetType.IN_APP -> {
                    val kind = DesktopWindowKind.entries.firstOrNull {
                        it.name.equals(customMatch.targetValue, ignoreCase = true)
                    }
                    if (kind != null) {
                        onOpenWindow(kind)
                        appendLine("Custom Command '${customMatch.command}': membuka ${kind.defaultTitle}.", TerminalTone.SUCCESS)
                    } else {
                        appendLine("Target in-app '${customMatch.targetValue}' tidak ditemukan.", TerminalTone.ERROR)
                    }
                }
                TerminalCustomTargetType.INSTALLED_APP -> {
                    onLaunchPackage(customMatch.targetValue)
                    appendLine("Custom Command '${customMatch.command}': meluncurkan ${customMatch.label} (${customMatch.targetValue}).", TerminalTone.SUCCESS)
                }
            }
            return
        }

        // 2. Built-in PowerShell Commands
        when (cmd) {
            "help", "?", "get-help" -> printHelp()

            "cls", "clear" -> {
                lines.clear()
                appendLine("Terminal dibersihkan. Ketik 'help' untuk daftar perintah.", TerminalTone.MUTED)
            }

            "exit", "quit" -> onCloseTerminal()

            "sysinfo", "systeminfo", "system", "infosystem" -> {
                val info = DesktopLauncherManager.readDeviceSystemDetails(context)
                val bat = DesktopLauncherManager.readBatteryStatus(context)
                appendLine("=== INFO SISTEM ===", TerminalTone.INFO)
                appendLine("Perangkat   : ${info.deviceModel}")
                appendLine("Board / SoC : ${info.hardwareBoard}")
                appendLine("OS Android  : ${info.androidVersion} (API ${info.sdkInt})")
                appendLine("CPU         : ${info.cpuCores} core • ABI ${info.cpuAbis}")
                appendLine("Layar       : ${info.screenSummary}")
                appendLine("Storage Int : ${Formatters.formatBytes(info.internalAvailBytes)} bebas / ${Formatters.formatBytes(info.internalTotalBytes)}")
                appendLine("Baterai     : ${bat.percent ?: "--"}% (${bat.statusLabel}, ${bat.pluggedLabel})")
                appendLine("Uptime      : ${info.uptimeFormatted}")
            }

            "appinfo", "infoapp", "ver" -> {
                val info = DesktopLauncherManager.readDeviceSystemDetails(context)
                val perm = DesktopLauncherManager.inspectPermissions(context)
                appendLine("=== INFO APLIKASI ===", TerminalTone.INFO)
                appendLine("Nama        : ${info.appName}")
                appendLine("Paket       : ${context.packageName}")
                appendLine("Versi       : ${info.appVersionName} (Build ${info.appVersionCode})")
                appendLine("Developer   : ${info.developerName}")
                appendLine("Window DeX  : $openWindowCount jendela aktif")
                appendLine("App Drawer  : ${installedApps.size} aplikasi Android terdeteksi")
                appendLine("Custom Cmd  : ${customCommands.size} command terdaftar (Settings GUI)")
                appendLine(
                    "Izin Inti   : Home=${perm.isDefaultHomeLauncher}, Overlay=${perm.canDrawOverlays}, Freeform=${perm.freeformReady}, Notif=${perm.notificationsGranted}",
                    if (perm.allCoreReady) TerminalTone.SUCCESS else TerminalTone.WARN
                )
            }

            "ram", "mem", "memory", "inforam" -> {
                scope.launch {
                    val usage = DesktopLauncherManager.readSystemUsage(context)
                    val procs = DesktopLauncherManager.loadRunningOrRecentProcesses(context)
                    appendLine("=== INFO RAM & PROSES ===", TerminalTone.INFO)
                    appendLine(
                        "RAM Sistem  : ${usage.ramUsedMb} MB terpakai / ${usage.ramTotalMb} MB (${usage.ramPercent}%)",
                        if (usage.ramPercent > 82) TerminalTone.WARN else TerminalTone.SUCCESS
                    )
                    appendLine("CPU Load    : ${usage.cpuPercent}% • ${usage.cpuCores} core" +
                        (usage.cpuTempC?.let { " • ${String.format(Locale.US, "%.1f", it)}°C" } ?: ""))
                    appendLine("Proses Aktif / Baru Dibuka:", TerminalTone.WARN)
                    val top = procs.filter { it.isRunningProcess || it.ramPssMb != null }.take(8)
                    if (top.isEmpty()) {
                        appendLine("  (Aktifkan Usage Access di Settings agar RAM app lain terbaca lebih lengkap)", TerminalTone.MUTED)
                    } else {
                        top.forEach { p ->
                            val ramStr = p.ramPssMb?.let { "${it} MB" } ?: "-- MB"
                            appendLine("  • ${p.label.take(28).padEnd(28)} ${ramStr.padStart(8)}  [${p.packageName}]")
                        }
                    }
                }
            }

            "open", "start-app" -> {
                val targetKey = arg.lowercase(Locale.US).replace(" ", "")
                val kind = when (targetKey) {
                    "browser", "web", "cyberbrowser" -> DesktopWindowKind.BROWSER
                    "search", "offlinesearch", "cari" -> DesktopWindowKind.SEARCH
                    "wiki", "offlinewiki", "kiwix" -> DesktopWindowKind.OFFLINE_WIKI
                    "tube", "offlinetube", "media", "video" -> DesktopWindowKind.MEDIA
                    "music", "cybermusic", "musik", "mp3" -> DesktopWindowKind.MUSIC
                    "files", "explorer", "fileexplorer", "file" -> DesktopWindowKind.FILES
                    "vault", "library", "knowledge" -> DesktopWindowKind.LIBRARY
                    "taskmgr", "taskmanager", "task", "ram" -> DesktopWindowKind.TASK_MANAGER
                    "downloads", "download", "unduhan", "tersimpan" -> DesktopWindowKind.DOWNLOADS
                    "bookmarks", "bookmark" -> DesktopWindowKind.BOOKMARKS
                    "history", "riwayat" -> DesktopWindowKind.HISTORY
                    "archives", "archive", "arsip" -> DesktopWindowKind.ARCHIVES
                    "settings", "setting", "pengaturan", "config" -> DesktopWindowKind.SETTINGS
                    else -> null
                }
                if (kind != null) {
                    onOpenWindow(kind)
                    appendLine("Membuka ${kind.defaultTitle}...", TerminalTone.SUCCESS)
                } else {
                    appendLine("Target tidak dikenal: '$arg'. Contoh: open browser | open search | open wiki | open tube | open files | open taskmgr | open settings", TerminalTone.ERROR)
                }
            }

            "apps", "listapps", "get-app" -> {
                val q = arg.lowercase(Locale.US)
                val matches = installedApps.filter {
                    q.isBlank() || it.label.lowercase(Locale.US).contains(q) || it.packageName.lowercase(Locale.US).contains(q)
                }
                appendLine("=== APLIKASI TERINSTALL (${matches.size}) ===", TerminalTone.INFO)
                matches.take(30).forEach { appInfo ->
                    appendLine("  • ${appInfo.label.take(26).padEnd(26)} -> ${appInfo.packageName}")
                }
                if (matches.size > 30) {
                    appendLine("  ... dan ${matches.size - 30} lagi. Gunakan 'apps <kata_kunci>' untuk menyaring.", TerminalTone.MUTED)
                }
            }

            "launch", "run" -> {
                if (arg.isBlank()) {
                    appendLine("Penggunaan: launch <nama_aplikasi atau packageName>", TerminalTone.ERROR)
                    return
                }
                val match = installedApps.firstOrNull {
                    it.packageName.equals(arg, ignoreCase = true) ||
                        it.label.equals(arg, ignoreCase = true)
                } ?: installedApps.firstOrNull {
                    it.label.contains(arg, ignoreCase = true) ||
                        it.packageName.contains(arg, ignoreCase = true)
                }
                if (match != null) {
                    onLaunchPackage(match.packageName)
                    appendLine("Meluncurkan '${match.label}' (${match.packageName})...", TerminalTone.SUCCESS)
                } else {
                    appendLine("Aplikasi '$arg' tidak ditemukan. Ketik 'apps' untuk melihat daftar aplikasi.", TerminalTone.ERROR)
                }
            }

            "sys", "shortcut", "activity" -> {
                if (arg.isBlank()) {
                    appendLine("Shortcut Activity Sistem tersedia:", TerminalTone.INFO)
                    appendLine("  sys wifi | sys bluetooth | sys display | sys storage | sys battery")
                    appendLine("  sys dev | sys usage | sys nav | sys apps | sys allfiles | sys datetime | sys labs")
                } else {
                    DesktopLauncherManager.openAndroidSystemShortcut(context, arg)
                        .onSuccess { msg ->
                            appendLine(msg, TerminalTone.SUCCESS)
                            onShowMessage(msg)
                        }
                        .onFailure { err ->
                            appendLine(err.message ?: "Shortcut sistem tidak dikenal.", TerminalTone.ERROR)
                        }
                }
            }

            "custom", "aliases" -> {
                if (customCommands.isEmpty()) {
                    appendLine("Belum ada custom command. Buka Settings GUI untuk menambah custom command.", TerminalTone.WARN)
                } else {
                    appendLine("=== CUSTOM COMMANDS ===", TerminalTone.INFO)
                    customCommands.forEach { c ->
                        appendLine("  ${c.command} -> ${c.label} (${c.targetType.label}: ${c.targetValue})", TerminalTone.SUCCESS)
                    }
                }
            }

            else -> {
                appendLine("Perintah '$cmd' tidak dikenal. Ketik 'help' untuk daftar perintah.", TerminalTone.ERROR)
            }
        }
    }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    LaunchedEffect(isWindowFocused) {
        if (isWindowFocused) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    DisposableEffect(Unit) {
        onDispose { onTextInputFocusChanged(false) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PsBg)
    ) {
        // PowerShell Header Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PsHeader)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Terminal, contentDescription = null, tint = PsCyan, modifier = Modifier.size(16.dp))
            Text(
                "Windows PowerShell — Cyber Deck Terminal",
                color = PsInk,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { executeCommand("help") }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.HelpOutline, contentDescription = "Help", tint = PsPrompt, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = { executeCommand("cls") }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear", tint = PsMuted, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onCloseTerminal, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = PsInk, modifier = Modifier.size(16.dp))
            }
        }

        // Quick-command toolbar chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF021F4B))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val quickCmds = listOf("help", "sysinfo", "appinfo", "ram", "open browser", "open search", "open wiki", "apps", "sys wifi", "sys bluetooth", "cls")
            quickCmds.forEach { qc ->
                Surface(
                    color = Color(0xFF0A326D),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable { executeCommand(qc) }
                ) {
                    Text(
                        qc,
                        color = PsCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            customCommands.forEach { cc ->
                Surface(
                    color = Color(0xFF0F3F36),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable { executeCommand(cc.command) }
                ) {
                    Text(
                        cc.command,
                        color = PsGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Output Scroll Buffer
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(lines, key = { it.id }) { line ->
                Text(
                    text = line.text,
                    color = line.tone.color,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // Prompt Input Bar
        Surface(color = PsInputBg) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PS C:\\CyberDeck>",
                    color = PsPrompt,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (commandText.isEmpty()) {
                        Text(
                            "Ketik perintah (help, sysinfo, ram, open browser, sys wifi)...",
                            color = PsMuted.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                    BasicTextField(
                        value = commandText,
                        onValueChange = { commandText = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = PsInk,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        ),
                        cursorBrush = SolidColor(PsPrompt),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            val toRun = commandText
                            commandText = ""
                            executeCommand(toRun)
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { onTextInputFocusChanged(it.isFocused) }
                            .onPreviewKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown) {
                                    when (ev.key) {
                                        Key.Enter, Key.NumPadEnter -> {
                                            val toRun = commandText
                                            commandText = ""
                                            executeCommand(toRun)
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            if (commandHistory.isNotEmpty()) {
                                                historyCursor = if (historyCursor < 0) {
                                                    commandHistory.lastIndex
                                                } else {
                                                    (historyCursor - 1).coerceAtLeast(0)
                                                }
                                                commandText = commandHistory[historyCursor]
                                            }
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            if (historyCursor >= 0) {
                                                if (historyCursor + 1 <= commandHistory.lastIndex) {
                                                    historyCursor += 1
                                                    commandText = commandHistory[historyCursor]
                                                } else {
                                                    historyCursor = -1
                                                    commandText = ""
                                                }
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                    )
                }
                IconButton(
                    onClick = {
                        val toRun = commandText
                        commandText = ""
                        executeCommand(toRun)
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Jalankan", tint = PsGreen, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
