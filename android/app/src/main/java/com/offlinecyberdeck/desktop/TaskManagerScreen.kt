package com.offlinecyberdeck.desktop

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinecyberdeck.CyberDeckApplication
import com.offlinecyberdeck.core.format.Formatters
import com.offlinecyberdeck.ui.theme.DeckAmber
import com.offlinecyberdeck.ui.theme.DeckGreen
import com.offlinecyberdeck.ui.theme.DeckRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max

private enum class TaskManagerTab(val label: String) {
    PROCESSES("Proses & Kill"),
    PERFORMANCE("Kinerja & Network"),
    ABOUT("Tentang & Sistem")
}

@Composable
fun TaskManagerScreen(
    app: CyberDeckApplication,
    activeDesktopWindowsCount: Int = 0,
    activeWebViewCount: Int = 0,
    openWindows: List<DesktopWindowState> = emptyList(),
    onFocusWindow: ((String) -> Unit)? = null,
    onCloseWindow: ((String) -> Unit)? = null,
    onTrimAppRam: (() -> Unit)? = null,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(TaskManagerTab.PROCESSES) }
    var sysUsage by remember { mutableStateOf(DesktopLauncherManager.readSystemUsage(context)) }
    var batteryInfo by remember { mutableStateOf(DesktopLauncherManager.readBatteryStatus(context)) }
    var deviceDetails by remember { mutableStateOf(DesktopLauncherManager.readDeviceSystemDetails(context)) }

    var processes by remember { mutableStateOf<List<TaskProcessItem>>(emptyList()) }
    var onlyActiveOrRecent by remember { mutableStateOf(true) }
    var showSystemProcesses by remember { mutableStateOf(false) }
    var usageStatsGranted by remember { mutableStateOf(DesktopLauncherManager.hasUsageStatsAccess(context)) }
    var isBoosting by remember { mutableStateOf(false) }

    val cpuHistory = remember { mutableStateListOf<Float>() }
    val ramHistory = remember { mutableStateListOf<Float>() }
    val rxKbpsHistory = remember { mutableStateListOf<Float>() }
    val txKbpsHistory = remember { mutableStateListOf<Float>() }

    var prevRxBytes by remember { mutableLongStateOf(sysUsage.rxTotalBytes) }
    var prevTxBytes by remember { mutableLongStateOf(sysUsage.txTotalBytes) }
    var currentRxBytesPerSec by remember { mutableFloatStateOf(0f) }
    var currentTxBytesPerSec by remember { mutableFloatStateOf(0f) }
    var networkTransportLabel by remember { mutableStateOf(readTransportLabel(context)) }

    suspend fun refreshProcessesNow() {
        usageStatsGranted = DesktopLauncherManager.hasUsageStatsAccess(context)
        processes = DesktopLauncherManager.loadRunningOrRecentProcesses(context)
    }

    LaunchedEffect(Unit) {
        refreshProcessesNow()
    }

    // Real-time 1-second telemetry loop (CPU, CPU Temp, RAM, Network Traffic & Uptime)
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            delay(1_000L)
            val usage = withContext(Dispatchers.IO) { DesktopLauncherManager.readSystemUsage(context) }
            sysUsage = usage
            val dRx = (usage.rxTotalBytes - prevRxBytes).coerceAtLeast(0L).toFloat()
            val dTx = (usage.txTotalBytes - prevTxBytes).coerceAtLeast(0L).toFloat()
            prevRxBytes = usage.rxTotalBytes
            prevTxBytes = usage.txTotalBytes
            currentRxBytesPerSec = dRx
            currentTxBytesPerSec = dTx

            cpuHistory.add(usage.cpuPercent.toFloat())
            if (cpuHistory.size > 60) cpuHistory.removeAt(0)

            ramHistory.add(usage.ramPercent.toFloat())
            if (ramHistory.size > 60) ramHistory.removeAt(0)

            rxKbpsHistory.add(dRx / 1024f)
            if (rxKbpsHistory.size > 60) rxKbpsHistory.removeAt(0)

            txKbpsHistory.add(dTx / 1024f)
            if (txKbpsHistory.size > 60) txKbpsHistory.removeAt(0)

            if (tick % 3 == 0) {
                batteryInfo = DesktopLauncherManager.readBatteryStatus(context)
                deviceDetails = DesktopLauncherManager.readDeviceSystemDetails(context)
                networkTransportLabel = readTransportLabel(context)
            }
            if (tick % 6 == 0) {
                refreshProcessesNow()
            }
            tick++
        }
    }

    val cpuTemp = sysUsage.cpuTempC
    val tempColor = when {
        cpuTemp == null -> MaterialTheme.colorScheme.onSurfaceVariant
        cpuTemp >= 72f -> DeckRed
        cpuTemp >= 55f -> DeckAmber
        else -> DeckGreen
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // WINDOWS TASK MANAGER TOP SUMMARY BAR
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Task Manager", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "RAM ${sysUsage.ramPercent}% • CPU ${sysUsage.cpuPercent}% • Suhu ${cpuTemp?.let { String.format(Locale.US, "%.1f°C", it) } ?: "--"} • ↓${formatSpeed(currentRxBytesPerSec)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { scope.launch { refreshProcessesNow() } }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh Proses")
                        }
                        Button(
                            enabled = !isBoosting,
                            onClick = {
                                scope.launch {
                                    isBoosting = true
                                    onTrimAppRam?.invoke()
                                    val msg = DesktopLauncherManager.boostSystemRam(
                                        context,
                                        processes.filter { !it.isCurrentApp }.map { it.packageName }
                                    )
                                    sysUsage = DesktopLauncherManager.readSystemUsage(context)
                                    refreshProcessesNow()
                                    isBoosting = false
                                    onShowMessage(msg)
                                }
                            }
                        ) {
                            Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(if (isBoosting) "Membersihkan..." else "Boost RAM", maxLines = 1)
                        }
                    }
                }
            }
        }

        TabRow(selectedTabIndex = selectedTab.ordinal) {
            TaskManagerTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }

        when (selectedTab) {
            TaskManagerTab.PROCESSES -> {
                val filteredProcs = remember(processes, showSystemProcesses, onlyActiveOrRecent) {
                    processes.filter { item ->
                        val activeOrRecentOk = !onlyActiveOrRecent ||
                            item.isCurrentApp ||
                            item.isRunningProcess ||
                            item.isRecentlyOpened ||
                            item.ramPssMb != null
                        val sysOk = showSystemProcesses || !item.isSystemApp || item.isRunningProcess || item.isRecentlyOpened || item.isCurrentApp
                        activeOrRecentOk && sysOk
                    }
                }
                val killableRecentPkgs = remember(filteredProcs) {
                    filteredProcs.filter { !it.isCurrentApp && (it.isRunningProcess || it.isRecentlyOpened) }
                        .map { it.packageName }
                }

                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Internal Cyber Deck resource card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Offline Cyber Deck (Proses Utama)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "RAM Terpakai System: ${sysUsage.ramUsedMb} / ${sysUsage.ramTotalMb} MB (${sysUsage.ramPercent}%) • Window DeX: $activeDesktopWindowsCount • WebView: $activeWebViewCount",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (onTrimAppRam != null) {
                                    OutlinedButton(onClick = {
                                        onTrimAppRam()
                                        onShowMessage("Cache WebView dibersihkan untuk menghemat RAM.")
                                    }) {
                                        Text("Trim WebView", maxLines = 1)
                                    }
                                }
                            }
                            if (openWindows.isNotEmpty() && onCloseWindow != null) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                Text(
                                    "Jendela Aplikasi Cyber Deck Aktif (${openWindows.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                openWindows.forEach { win ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            win.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (onFocusWindow != null) {
                                            TextButton(onClick = { onFocusWindow(win.id) }) {
                                                Text("Fokus", fontSize = 11.sp)
                                            }
                                        }
                                        if (win.kind != DesktopWindowKind.TASK_MANAGER) {
                                            TextButton(
                                                onClick = {
                                                    onCloseWindow(win.id)
                                                    onShowMessage("Jendela '${win.title}' ditutup.")
                                                },
                                                colors = ButtonDefaults.textButtonColors(contentColor = DeckRed)
                                            ) {
                                                Text("Tutup", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!usageStatsGranted) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DeckAmber.copy(alpha = 0.14f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Aktifkan 'Akses Data Penggunaan' agar aplikasi baru dibuka (Recent Apps) & pemakaian RAM tampil lengkap.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DeckAmber,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(onClick = {
                                    onShowMessage(DesktopLauncherManager.openUsageStatsSettings(context))
                                }) {
                                    Text("Aktifkan", maxLines = 1)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = onlyActiveOrRecent,
                            onClick = { onlyActiveOrRecent = !onlyActiveOrRecent },
                            label = { Text("Aktif & Recent") }
                        )
                        FilterChip(
                            selected = showSystemProcesses,
                            onClick = { showSystemProcesses = !showSystemProcesses },
                            label = { Text("Sistem") }
                        )
                        Spacer(Modifier.weight(1f))
                        if (killableRecentPkgs.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val msg = DesktopLauncherManager.killAllRecentApps(context, killableRecentPkgs)
                                        processes = processes.filterNot { it.packageName in killableRecentPkgs }
                                        sysUsage = DesktopLauncherManager.readSystemUsage(context)
                                        refreshProcessesNow()
                                        onShowMessage(msg)
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = DeckRed),
                                border = BorderStroke(1.dp, DeckRed.copy(alpha = 0.55f))
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Kill Semua (${killableRecentPkgs.size})", fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }

                    // Table header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Aplikasi (${filteredProcs.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(2f))
                        Text("RAM / Status", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.2f))
                        Text("Kill Langsung", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(96.dp))
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredProcs, key = { it.packageName }) { item ->
                            ProcessRowCard(
                                item = item,
                                onKill = {
                                    DesktopLauncherManager.killAppProcess(context, item.packageName)
                                        .onSuccess { msg ->
                                            processes = processes.map {
                                                if (it.packageName == item.packageName) {
                                                    it.copy(isRunningProcess = false, isRecentlyOpened = false, ramPssMb = null, lastUsedMs = 0L)
                                                } else it
                                            }
                                            onShowMessage(msg)
                                            scope.launch { refreshProcessesNow() }
                                        }
                                        .onFailure { err ->
                                            onShowMessage(err.message ?: "Gagal meng-kill proses")
                                        }
                                }
                            )
                        }
                    }
                }
            }

            TaskManagerTab.PERFORMANCE -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // CPU & SUHU CPU REALTIME CARD
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.DeveloperBoard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("CPU & Suhu Prosesor", style = MaterialTheme.typography.titleMedium)
                                }
                                Surface(
                                    color = tempColor.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, tempColor.copy(alpha = 0.55f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Filled.Thermostat, contentDescription = null, tint = tempColor, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = cpuTemp?.let { String.format(Locale.US, "%.1f°C", it) } ?: "Sensor N/A",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = tempColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Utilisasi: ${sysUsage.cpuPercent}%",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = buildString {
                                        append("${sysUsage.cpuCores} Core")
                                        sysUsage.cpuAvgFreqMhz?.let { append(" • Avg $it MHz") }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RealtimeLineChart(
                                primarySeries = cpuHistory.toList(),
                                maxY = 100f,
                                primaryColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // RAM REALTIME CARD
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.Memory, contentDescription = null, tint = Color(0xFFA855F7))
                                    Text("Memori (RAM)", style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    text = "${sysUsage.ramUsedMb} MB / ${sysUsage.ramTotalMb} MB (${sysUsage.ramPercent}%)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFC084FC)
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (sysUsage.ramPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(8.dp)
                            )
                            Text(
                                text = "Tersedia: ${(sysUsage.ramTotalMb - sysUsage.ramUsedMb).coerceAtLeast(0L)} MB • Window DeX: $activeDesktopWindowsCount • WebView: $activeWebViewCount",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            RealtimeLineChart(
                                primarySeries = ramHistory.toList(),
                                maxY = 100f,
                                primaryColor = Color(0xFFA855F7)
                            )
                        }
                    }

                    // NETWORK TRAFFIC REALTIME CARD
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.NetworkCheck, contentDescription = null, tint = DeckGreen)
                                    Text("Traffic Network Realtime", style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    text = networkTransportLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (networkTransportLabel == "Offline") DeckAmber else DeckGreen
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Download (RX)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "↓ ${formatSpeed(currentRxBytesPerSec)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = DeckGreen,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Upload (TX)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "↑ ${formatSpeed(currentTxBytesPerSec)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFF22D3EE),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            val maxNetKbps = max(
                                64f,
                                max(rxKbpsHistory.maxOrNull() ?: 64f, txKbpsHistory.maxOrNull() ?: 64f) * 1.2f
                            )
                            RealtimeLineChart(
                                primarySeries = rxKbpsHistory.toList(),
                                secondarySeries = txKbpsHistory.toList(),
                                maxY = maxNetKbps,
                                primaryColor = DeckGreen,
                                secondaryColor = Color(0xFF22D3EE)
                            )
                            Text(
                                text = "Total sejak boot — Terima: ${Formatters.formatBytes(sysUsage.rxTotalBytes)} • Kirim: ${Formatters.formatBytes(sysUsage.txTotalBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            TaskManagerTab.ABOUT -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = deviceDetails.appName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Versi ${deviceDetails.appVersionName} (Build ${deviceDetails.appVersionCode})",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Developer: ${deviceDetails.developerName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = DeckGreen
                            )
                            Text(
                                text = "Lingkungan kerja DeX offline-first: Browser Dark Mode, ZIM Wikipedia Viewer, File Explorer, OfflineTube, Cyber Music, dan Task Manager.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Informasi Perangkat & Sistem", style = MaterialTheme.typography.titleMedium)
                            }
                            HorizontalDivider()
                            SystemInfoRow("Nama Device", deviceDetails.deviceModel)
                            SystemInfoRow("Pabrikan", deviceDetails.manufacturer)
                            SystemInfoRow("Hardware / Board", deviceDetails.hardwareBoard)
                            SystemInfoRow("Sistem Operasi", "${deviceDetails.androidVersion} (API ${deviceDetails.sdkInt})")
                            SystemInfoRow("Arsitektur CPU", deviceDetails.cpuAbis)
                            SystemInfoRow("Jumlah Core CPU", "${deviceDetails.cpuCores} core (${sysUsage.cpuPercent}% aktif)")
                            SystemInfoRow("Suhu CPU", cpuTemp?.let { String.format(Locale.US, "%.1f °C", it) } ?: "Tidak tersedia")
                            SystemInfoRow("RAM Total", "${deviceDetails.ramTotalMb} MB (${Formatters.formatBytes(deviceDetails.ramTotalMb * 1024L * 1024L)})")
                            SystemInfoRow("RAM Tersedia", "${deviceDetails.ramAvailMb} MB (Terpakai ${sysUsage.ramPercent}%)")
                            SystemInfoRow(
                                "Storage Internal",
                                "${Formatters.formatBytes(deviceDetails.internalAvailBytes)} bebas / ${Formatters.formatBytes(deviceDetails.internalTotalBytes)} total"
                            )
                            SystemInfoRow(
                                "Baterai",
                                "${batteryInfo.percent ?: "--"}% • ${batteryInfo.statusLabel} (${batteryInfo.pluggedLabel})" +
                                    (batteryInfo.temperatureC?.let { String.format(Locale.US, " • %.1f°C", it) } ?: "")
                            )
                            SystemInfoRow("Layar", deviceDetails.screenSummary)
                            SystemInfoRow("System Uptime", deviceDetails.uptimeFormatted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessRowCard(
    item: TaskProcessItem,
    onKill: () -> Unit
) {
    val context = LocalContext.current
    var iconBmp by remember(item.packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.packageName) {
        iconBmp = withContext(Dispatchers.IO) {
            DesktopLauncherManager.loadAppIconBitmap(context, item.packageName)
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (item.isRunningProcess) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val bmp = iconBmp
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = item.label,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            Column(modifier = Modifier.weight(2f)) {
                Text(item.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = item.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(modifier = Modifier.weight(1.2f)) {
                Text(
                    text = item.ramPssMb?.let { "$it MB RAM" }
                        ?: if (item.isRunningProcess) "Aktif"
                        else if (item.isRecentlyOpened || item.lastUsedMs > 0L) "Recent"
                        else "Terinstall",
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        (item.ramPssMb ?: 0) >= 250 -> DeckRed
                        item.isRunningProcess || item.isRecentlyOpened -> DeckGreen
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontFamily = FontFamily.Monospace
                )
                if (item.rxBytes > 0L || item.txBytes > 0L) {
                    Text(
                        text = "↓${Formatters.formatBytes(item.rxBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Row(
                modifier = Modifier.width(96.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!item.isCurrentApp) {
                    OutlinedButton(
                        onClick = onKill,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DeckRed),
                        border = BorderStroke(1.dp, DeckRed.copy(alpha = 0.5f)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Kill", fontSize = 12.sp)
                    }
                } else {
                    Text("Aktif", style = MaterialTheme.typography.labelSmall, color = DeckGreen)
                }
            }
        }
    }
}

@Composable
private fun RealtimeLineChart(
    primarySeries: List<Float>,
    secondarySeries: List<Float> = emptyList(),
    maxY: Float,
    primaryColor: Color,
    secondaryColor: Color = Color.Transparent
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0A0F18))
            .padding(6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (step in 1..3) {
                val y = h * (step / 4f)
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            fun drawSeries(values: List<Float>, strokeColor: Color, fillArea: Boolean) {
                if (values.size < 2) return
                val path = Path()
                val fillPath = Path()
                val n = values.size
                val dx = w / (n - 1).coerceAtLeast(1)
                values.forEachIndexed { idx, v ->
                    val x = idx * dx
                    val norm = (v / maxY.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val y = h - (norm * h)
                    if (idx == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, h)
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                if (fillArea) {
                    fillPath.lineTo((n - 1) * dx, h)
                    fillPath.close()
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            listOf(strokeColor.copy(alpha = 0.35f), strokeColor.copy(alpha = 0.02f))
                        )
                    )
                }
                drawPath(path = path, color = strokeColor, style = Stroke(width = 2.5f))
            }
            drawSeries(primarySeries, primaryColor, fillArea = true)
            if (secondarySeries.isNotEmpty()) {
                drawSeries(secondarySeries, secondaryColor, fillArea = false)
            }
        }
    }
}

@Composable
private fun SystemInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.58f)
        )
    }
}

private fun formatSpeed(bytesPerSec: Float): String {
    return when {
        bytesPerSec >= 1024f * 1024f -> String.format(Locale.US, "%.2f MB/s", bytesPerSec / (1024f * 1024f))
        bytesPerSec >= 1024f -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1024f)
        else -> "${bytesPerSec.toInt()} B/s"
    }
}

private fun readTransportLabel(context: android.content.Context): String {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return "Offline"
    val net = cm.activeNetwork ?: return "Offline"
    val caps = cm.getNetworkCapabilities(net) ?: return "Offline"
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi Terhubung"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Seluler Terhubung"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet LAN"
        else -> "Online"
    }
}
