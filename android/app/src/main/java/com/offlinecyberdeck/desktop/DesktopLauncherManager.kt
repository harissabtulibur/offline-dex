package com.offlinecyberdeck.desktop

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.TrafficStats
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.util.LruCache
import android.view.WindowManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.offlinecyberdeck.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean
)

data class DesktopPermissionStatus(
    val isDefaultHomeLauncher: Boolean,
    val canDrawOverlays: Boolean,
    val isFreeformEnabledInSettings: Boolean,
    val isForceResizableEnabled: Boolean,
    val hasFreeformFeature: Boolean,
    val notificationsGranted: Boolean,
    val notificationAccessGranted: Boolean,
    val canWriteSecureSettings: Boolean
) {
    val freeformReady: Boolean
        get() = isFreeformEnabledInSettings || hasFreeformFeature

    val allCoreReady: Boolean
        get() = isDefaultHomeLauncher && canDrawOverlays && freeformReady
}

data class BatteryStatusInfo(
    val percent: Int?,
    val isCharging: Boolean,
    val temperatureC: Float? = null,
    val voltageMv: Int? = null,
    val pluggedLabel: String = "Baterai",
    val statusLabel: String = "Normal"
)

data class SystemUsageInfo(
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    val ramPercent: Int,
    val cpuPercent: Int,
    val cpuCores: Int,
    val cpuTempC: Float? = null,
    val cpuAvgFreqMhz: Int? = null,
    val rxTotalBytes: Long = 0L,
    val txTotalBytes: Long = 0L
)

data class DeviceSystemDetails(
    val appName: String,
    val appVersionName: String,
    val appVersionCode: Int,
    val developerName: String,
    val deviceModel: String,
    val manufacturer: String,
    val hardwareBoard: String,
    val androidVersion: String,
    val sdkInt: Int,
    val cpuAbis: String,
    val cpuCores: Int,
    val ramTotalMb: Long,
    val ramAvailMb: Long,
    val internalTotalBytes: Long,
    val internalAvailBytes: Long,
    val screenSummary: String,
    val uptimeFormatted: String
)

data class TaskProcessItem(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val isCurrentApp: Boolean,
    val isRunningProcess: Boolean,
    val ramPssMb: Int?,
    val lastUsedMs: Long,
    val rxBytes: Long,
    val txBytes: Long
)

object DesktopLauncherManager {

    // Capped at 64 app icons (~1.3 MB max) so App Drawer stays ultra-light on 4 GB RAM tablets
    private val iconCache = LruCache<String, ImageBitmap>(64)

    fun inspectPermissions(context: Context): DesktopPermissionStatus {
        val pm = context.packageManager
        val isHome = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            rm?.isRoleHeld(RoleManager.ROLE_HOME) == true || isResolvedAsDefaultHome(context)
        } else {
            isResolvedAsDefaultHome(context)
        }

        val canOverlay = Settings.canDrawOverlays(context)
        val devFreeform = runCatching {
            Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) == 1
        }.getOrDefault(false)
        val forceResizable = runCatching {
            Settings.Global.getInt(context.contentResolver, "force_resizable_activities", 0) == 1
        }.getOrDefault(false)
        val sysFreeform = pm.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        val notifAccessOk = NotificationCenterStore.isNotificationAccessEnabled(context)
        val writeSecureOk = context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

        return DesktopPermissionStatus(
            isDefaultHomeLauncher = isHome,
            canDrawOverlays = canOverlay,
            isFreeformEnabledInSettings = devFreeform,
            isForceResizableEnabled = forceResizable,
            hasFreeformFeature = sysFreeform,
            notificationsGranted = notifOk,
            notificationAccessGranted = notifAccessOk,
            canWriteSecureSettings = writeSecureOk
        )
    }

    fun tryEnableFreeformFlagsDirectly(context: Context): Result<String> {
        return runCatching {
            val granted = context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                error("Izin sistem WRITE_SECURE_SETTINGS belum aktif. Aktifkan manual di Developer Options atau jalankan ADB sekali.")
            }
            Settings.Global.putInt(context.contentResolver, "enable_freeform_support", 1)
            Settings.Global.putInt(context.contentResolver, "force_resizable_activities", 1)
            "Freeform & Resizable berhasil diaktifkan! Restart tablet / buka ulang app agar One UI menerapkannya."
        }
    }

    fun buildFreeformAdbCommand(packageName: String): String =
        "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS && " +
            "adb shell settings put global enable_freeform_support 1 && " +
            "adb shell settings put global force_resizable_activities 1"

    private fun isResolvedAsDefaultHome(context: Context): Boolean {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.resolveActivity(
                homeIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun buildDefaultHomeRoleIntentOrNull(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return null
    }

    fun openHomeLauncherSettings(context: Context): String {
        val candidates = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        return launchFirstWorkingSettings(
            context,
            candidates,
            "Pilih 'Offline Cyber Deck' sebagai Aplikasi Utama / Home"
        )
    }

    fun openOverlayPermissionSettings(context: Context): String {
        val candidates = listOf(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        )
        return launchFirstWorkingSettings(
            context,
            candidates,
            "Aktifkan izin 'Tampil di atas aplikasi lain / Appear on top'"
        )
    }

    fun openDeveloperOptionsSettings(context: Context): String {
        val candidates = listOf(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        return launchFirstWorkingSettings(
            context,
            candidates,
            "Di Opsi Pengembang: aktifkan 'Enable freeform windows' & 'Force activities to be resizable'"
        )
    }

    fun openSystemMultiWindowSettings(context: Context): String {
        val candidates = listOf(
            Intent().setClassName("com.android.settings", "com.android.settings.Settings\$AdvancedAppsActivity"),
            Intent(Settings.ACTION_DISPLAY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        return launchFirstWorkingSettings(
            context,
            candidates,
            "Di Samsung One UI: buka Fitur Lanjutan → Labs → aktifkan Multi-jendela untuk semua aplikasi"
        )
    }

    fun openAppDetailsSettings(context: Context): String {
        val candidates = listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_SETTINGS)
        )
        return launchFirstWorkingSettings(
            context,
            candidates,
            "Atur Baterai ke 'Tidak dibatasi' agar launcher selalu responsif"
        )
    }

    fun isGestureNavigationEnabled(context: Context): Boolean {
        return runCatching {
            Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0) == 2
        }.getOrDefault(false)
    }

    fun openSystemNavigationSettings(context: Context): String {
        val candidates = listOf(
            Intent().setClassName("com.android.settings", "com.android.settings.Settings\$NavigationBarSettingsActivity"),
            Intent("com.android.settings.SEARCH_RESULT_TRAMPOLINE").putExtra(":settings:fragment_args_key", "gesture_system_navigation"),
            Intent(Settings.ACTION_DISPLAY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        return launchFirstWorkingSettings(
            context,
            candidates,
            "Buka Navigasi Sistem (Display → Navigation Bar) untuk beralih ke Gesture Navigation agar tombol bawah Android tersembunyi"
        )
    }

    private fun launchFirstWorkingSettings(
        context: Context,
        candidates: List<Intent>,
        successMessage: String
    ): String {
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return successMessage
        }
        return "Buka Pengaturan Android secara manual."
    }

    suspend fun loadInstalledApps(context: Context): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val byPackage = LinkedHashMap<String, InstalledAppInfo>()
        for (ri in resolved) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (pkg == context.packageName) continue
            if (byPackage.containsKey(pkg)) continue
            val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(pkg).ifBlank { pkg }
            val appFlags = ri.activityInfo?.applicationInfo?.flags ?: 0
            val isSystem = (appFlags and ApplicationInfo.FLAG_SYSTEM) != 0
            byPackage[pkg] = InstalledAppInfo(
                packageName = pkg,
                label = label,
                isSystemApp = isSystem
            )
        }
        byPackage.values.sortedBy { it.label.lowercase() }
    }

    fun loadAppIconBitmap(context: Context, packageName: String): ImageBitmap? {
        iconCache.get(packageName)?.let { return it }
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bmp = drawable.toBitmap(width = 72, height = 72, config = Bitmap.Config.ARGB_8888)
                .asImageBitmap()
            iconCache.put(packageName, bmp)
            bmp
        }.getOrNull()
    }

    fun clearIconCache() {
        iconCache.evictAll()
    }

    fun launchExternalApp(
        context: Context,
        packageName: String,
        floating: Boolean,
        cascadeIndex: Int = 0
    ): Result<String> {
        return runCatching {
            val pm = context.packageManager
            val baseIntent = pm.getLaunchIntentForPackage(packageName)
                ?: error("Aplikasi tidak memiliki launcher activity.")

            baseIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (floating) {
                // IMPORTANT: Do NOT set FLAG_ACTIVITY_LAUNCH_ADJACENT — that flag forces Android/One UI into Split-Screen!
                val dm = context.resources.displayMetrics
                val w = dm.widthPixels.coerceAtLeast(600)
                val h = dm.heightPixels.coerceAtLeast(600)
                val offset = (cascadeIndex % 5) * (w / 40).coerceAtLeast(20)
                val winW = (w * 0.64f).toInt().coerceAtLeast(420)
                val winH = (h * 0.74f).toInt().coerceAtLeast(440)
                val left = ((w - winW) / 2 + offset).coerceIn(24, (w - winW - 24).coerceAtLeast(24))
                val top = ((h - winH) / 2 + offset).coerceIn(24, (h - winH - 64).coerceAtLeast(24))
                val bounds = Rect(left, top, left + winW, top + winH)

                val options = ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    runCatching { options.setLaunchBounds(bounds) }
                }
                // WINDOWING_MODE_FREEFORM = 5 (requires Freeform Windows enabled in Developer Options or One UI Labs)
                runCatching {
                    ActivityOptions::class.java
                        .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                        .invoke(options, 5)
                }

                val perm = inspectPermissions(context)
                val startedFloating = runCatching {
                    context.startActivity(baseIntent, options.toBundle())
                    true
                }.getOrDefault(false)

                when {
                    !startedFloating -> {
                        context.startActivity(baseIntent)
                        "Dibuka normal (sistem menolak launch bounds)."
                    }
                    !perm.freeformReady -> {
                        "Mode Freeform belum aktif di sistem — buka Izin DeX → Developer Options untuk mengaktifkan 'Enable freeform windows'."
                    }
                    else -> {
                        "Membuka jendela floating freeform (gunakan bar atas jendela untuk Minimize/Maximize/Close)."
                    }
                }
            } else {
                context.startActivity(baseIntent)
                "Aplikasi dibuka."
            }
        }
    }

    fun readBatteryStatus(context: Context): BatteryStatusInfo {
        return runCatching {
            val bm = context.getSystemService(BatteryManager::class.java)
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).roundToInt() else {
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
            }
            val statusInt = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val pluggedInt = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val charging = bm?.isCharging == true ||
                statusInt == BatteryManager.BATTERY_STATUS_CHARGING ||
                statusInt == BatteryManager.BATTERY_STATUS_FULL ||
                pluggedInt != 0
            val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
            val tempC = if (tempRaw != Int.MIN_VALUE) tempRaw / 10f else null
            val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
            val pluggedLabel = when (pluggedInt) {
                BatteryManager.BATTERY_PLUGGED_AC -> "Adaptor AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB / Hub"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> "Baterai"
            }
            val statusLabel = when (statusInt) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Mengisi Daya"
                BatteryManager.BATTERY_STATUS_FULL -> "Penuh"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Digunakan"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Siaga"
                else -> if (charging) "Mengisi Daya" else "Normal"
            }
            BatteryStatusInfo(
                percent = pct,
                isCharging = charging,
                temperatureC = tempC,
                voltageMv = voltageMv,
                pluggedLabel = pluggedLabel,
                statusLabel = statusLabel
            )
        }.getOrDefault(BatteryStatusInfo(percent = null, isCharging = false))
    }

    fun readBatteryPercent(context: Context): Int? = readBatteryStatus(context).percent

    fun readSystemUsage(context: Context): SystemUsageInfo {
        val am = context.getSystemService(ActivityManager::class.java)
        val memInfo = ActivityManager.MemoryInfo()
        var ramTotalMb = 4096L
        var ramUsedMb = 0L
        var ramPct = 0
        if (am != null) {
            am.getMemoryInfo(memInfo)
            ramTotalMb = (memInfo.totalMem / (1024L * 1024L)).coerceAtLeast(1L)
            val availMb = memInfo.availMem / (1024L * 1024L)
            ramUsedMb = (ramTotalMb - availMb).coerceAtLeast(0L)
            ramPct = ((ramUsedMb.toFloat() / ramTotalMb.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        }
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val cpuPct = readApproxCpuPercent(cores)
        val cpuTemp = readCpuTemperatureCelsius(context)
        val avgMhz = readAverageCpuMhz(cores)
        val rx = TrafficStats.getTotalRxBytes().let { if (it == TrafficStats.UNSUPPORTED.toLong()) 0L else it.coerceAtLeast(0L) }
        val tx = TrafficStats.getTotalTxBytes().let { if (it == TrafficStats.UNSUPPORTED.toLong()) 0L else it.coerceAtLeast(0L) }
        return SystemUsageInfo(
            ramUsedMb = ramUsedMb,
            ramTotalMb = ramTotalMb,
            ramPercent = ramPct,
            cpuPercent = cpuPct,
            cpuCores = cores,
            cpuTempC = cpuTemp,
            cpuAvgFreqMhz = avgMhz,
            rxTotalBytes = rx,
            txTotalBytes = tx
        )
    }

    fun readCpuTemperatureCelsius(context: Context): Float? {
        var preferredCpuTemp: Float? = null
        var anyZoneTemp: Float? = null
        for (i in 0 until 24) {
            val zoneDir = File("/sys/class/thermal/thermal_zone$i")
            if (!zoneDir.exists()) continue
            val raw = runCatching { File(zoneDir, "temp").readText().trim().toFloatOrNull() }.getOrNull() ?: continue
            val normalized = when {
                raw > 1000f -> raw / 1000f
                raw > 150f -> raw / 10f
                else -> raw
            }
            if (normalized !in 15f..115f) continue
            val zoneType = runCatching { File(zoneDir, "type").readText().trim().lowercase(Locale.US) }.getOrDefault("")
            if (zoneType.contains("cpu") || zoneType.contains("soc") || zoneType.contains("ap") ||
                zoneType.contains("tsens") || zoneType.contains("mtktscpu") || zoneType.contains("big") ||
                zoneType.contains("little")
            ) {
                if (preferredCpuTemp == null || normalized > preferredCpuTemp) {
                    preferredCpuTemp = normalized
                }
            } else if (anyZoneTemp == null) {
                anyZoneTemp = normalized
            }
        }
        return preferredCpuTemp ?: anyZoneTemp ?: readBatteryStatus(context).temperatureC
    }

    private fun readAverageCpuMhz(cores: Int): Int? {
        var sumKhz = 0L
        var count = 0
        for (i in 0 until cores.coerceAtMost(12)) {
            runCatching {
                val cur = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq").readText().trim().toLongOrNull()
                if (cur != null && cur > 0L) {
                    sumKhz += cur
                    count++
                }
            }
        }
        return if (count > 0) ((sumKhz / count) / 1000L).toInt() else null
    }

    fun readDeviceSystemDetails(context: Context): DeviceSystemDetails {
        val am = context.getSystemService(ActivityManager::class.java)
        val memInfo = ActivityManager.MemoryInfo()
        var ramTotalMb = 0L
        var ramAvailMb = 0L
        if (am != null) {
            am.getMemoryInfo(memInfo)
            ramTotalMb = memInfo.totalMem / (1024L * 1024L)
            ramAvailMb = memInfo.availMem / (1024L * 1024L)
        }
        val statFs = runCatching { StatFs(Environment.getDataDirectory().absolutePath) }.getOrNull()
        val intTotal = statFs?.totalBytes ?: 0L
        val intAvail = statFs?.availableBytes ?: 0L

        val dm = context.resources.displayMetrics
        val refreshHz = runCatching {
            val wm = context.getSystemService(WindowManager::class.java)
            @Suppress("DEPRECATION")
            wm?.defaultDisplay?.refreshRate?.roundToInt()
        }.getOrNull()
        val screenStr = buildString {
            append("${dm.widthPixels} × ${dm.heightPixels} px (${dm.densityDpi} dpi)")
            if (refreshHz != null && refreshHz > 0) append(" • ${refreshHz} Hz")
        }

        val uptimeSec = SystemClock.elapsedRealtime() / 1000L
        val hours = uptimeSec / 3600L
        val mins = (uptimeSec % 3600L) / 60L
        val secs = uptimeSec % 60L
        val uptimeStr = String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)

        return DeviceSystemDetails(
            appName = "Offline Cyber Deck",
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            developerName = "harissabtulibur",
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            manufacturer = Build.MANUFACTURER,
            hardwareBoard = "${Build.HARDWARE} / ${Build.BOARD} (${Build.DEVICE})",
            androidVersion = "Android ${Build.VERSION.RELEASE}",
            sdkInt = Build.VERSION.SDK_INT,
            cpuAbis = Build.SUPPORTED_ABIS.joinToString(", "),
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            ramTotalMb = ramTotalMb,
            ramAvailMb = ramAvailMb,
            internalTotalBytes = intTotal,
            internalAvailBytes = intAvail,
            screenSummary = screenStr,
            uptimeFormatted = uptimeStr
        )
    }

    fun hasUsageStatsAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageStatsSettings(context: Context): String = launchFirstWorkingSettings(
        context,
        listOf(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        ),
        "Aktifkan 'Akses data penggunaan' untuk Offline Cyber Deck agar Task Manager bisa memantau aplikasi lain."
    )

    suspend fun loadRunningOrRecentProcesses(context: Context): List<TaskProcessItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val am = context.getSystemService(ActivityManager::class.java)
        val installed = loadInstalledApps(context)
        val installedByPkg = installed.associateBy { it.packageName }

        val activePssByPkg = HashMap<String, Int>()
        val runningSet = HashSet<String>()
        val runningProcs = runCatching { am?.runningAppProcesses }.getOrNull().orEmpty()
        for (proc in runningProcs) {
            val memArr: Array<Debug.MemoryInfo>? = runCatching {
                am?.getProcessMemoryInfo(intArrayOf(proc.pid))
            }.getOrNull()
            val pssMb = memArr?.firstOrNull()?.totalPss?.let { (it / 1024f).roundToInt().coerceAtLeast(1) }
            val pkgs = proc.pkgList?.toList().orEmpty().ifEmpty { listOf(proc.processName.substringBefore(':')) }
            for (pkg in pkgs) {
                runningSet += pkg
                if (pssMb != null) {
                    activePssByPkg[pkg] = (activePssByPkg[pkg] ?: 0) + pssMb
                }
            }
        }

        val lastUsedByPkg = HashMap<String, Long>()
        if (hasUsageStatsAccess(context)) {
            val usm = context.getSystemService(UsageStatsManager::class.java)
            val now = System.currentTimeMillis()
            val stats = runCatching {
                usm?.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 12L * 3600L * 1000L, now)
            }.getOrNull().orEmpty()
            for (st in stats) {
                if (st.lastTimeUsed > 0L) {
                    val prev = lastUsedByPkg[st.packageName] ?: 0L
                    if (st.lastTimeUsed > prev) lastUsedByPkg[st.packageName] = st.lastTimeUsed
                }
            }
        }

        val allPkgs = LinkedHashSet<String>().apply {
            add(context.packageName)
            addAll(runningSet)
            addAll(lastUsedByPkg.keys)
            addAll(installedByPkg.keys)
        }

        val result = ArrayList<TaskProcessItem>(allPkgs.size)
        for (pkg in allPkgs) {
            val isSelf = pkg == context.packageName
            val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
            if (appInfo == null && !isSelf) continue

            val label = when {
                isSelf -> "Offline Cyber Deck (Aplikasi Ini)"
                installedByPkg[pkg] != null -> installedByPkg[pkg]!!.label
                appInfo != null -> runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg)
                else -> pkg
            }
            val isSystem = if (isSelf) false else {
                installedByPkg[pkg]?.isSystemApp ?: ((appInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0)
            }

            val uid = appInfo?.uid ?: Process.myUid()
            val rx = TrafficStats.getUidRxBytes(uid).let { if (it <= 0L) 0L else it }
            val tx = TrafficStats.getUidTxBytes(uid).let { if (it <= 0L) 0L else it }
            val pss = activePssByPkg[pkg] ?: if (isSelf) {
                val rt = Runtime.getRuntime()
                ((rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)).toInt().coerceAtLeast(1)
            } else null

            result += TaskProcessItem(
                packageName = pkg,
                label = label,
                isSystemApp = isSystem,
                isCurrentApp = isSelf,
                isRunningProcess = isSelf || pkg in runningSet,
                ramPssMb = pss,
                lastUsedMs = lastUsedByPkg[pkg] ?: 0L,
                rxBytes = rx,
                txBytes = tx
            )
        }

        result.sortedWith(
            compareByDescending<TaskProcessItem> { it.isCurrentApp }
                .thenByDescending { it.isRunningProcess }
                .thenByDescending { it.ramPssMb ?: 0 }
                .thenByDescending { it.lastUsedMs }
                .thenBy { it.label.lowercase(Locale.US) }
        )
    }

    fun killAppProcess(context: Context, packageName: String): Result<String> {
        return runCatching {
            if (packageName == context.packageName) {
                error("Tidak dapat menghentikan aplikasi Cyber Deck sendiri.")
            }
            val am = context.getSystemService(ActivityManager::class.java)
                ?: error("ActivityManager tidak tersedia.")
            am.killBackgroundProcesses(packageName)
            "Proses latar '$packageName' dihentikan (untuk aplikasi foreground gunakan tombol Info/Force Stop)."
        }
    }

    suspend fun boostSystemRam(context: Context, candidatePackages: List<String>): String = withContext(Dispatchers.IO) {
        val am = context.getSystemService(ActivityManager::class.java) ?: return@withContext "ActivityManager tidak tersedia."
        val before = ActivityManager.MemoryInfo()
        am.getMemoryInfo(before)
        var killed = 0
        for (pkg in candidatePackages) {
            if (pkg != context.packageName) {
                runCatching {
                    am.killBackgroundProcesses(pkg)
                    killed++
                }
            }
        }
        System.gc()
        val after = ActivityManager.MemoryInfo()
        am.getMemoryInfo(after)
        val freedMb = ((after.availMem - before.availMem) / (1024L * 1024L)).coerceAtLeast(0L)
        if (freedMb > 0L) {
            "RAM Boost: ${freedMb} MB dibebaskan ($killed proses latar dihentikan)."
        } else {
            "RAM Boost dijalankan pada $killed proses latar + GC."
        }
    }

    @Volatile
    private var prevProcCpuMs = -1L

    @Volatile
    private var prevWallMs = -1L

    private fun readApproxCpuPercent(cores: Int): Int {
        // 1. Try /proc/stat if permitted
        runCatching {
            val line = File("/proc/stat").bufferedReader().use { it.readLine() }
            if (!line.isNullOrBlank() && line.startsWith("cpu ")) {
                val parts = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
                if (parts.size >= 4) {
                    val idle = parts[3] + (parts.getOrNull(4) ?: 0L)
                    val total = parts.sum()
                    if (total > 0L) {
                        val busyPct = (((total - idle).toDouble() / total.toDouble()) * 100.0).roundToInt()
                        if (busyPct in 1..100) return busyPct
                    }
                }
            }
        }
        // 2. Fallback: combine CPU frequency scaling across cores + app CPU time delta
        var freqRatioSum = 0.0
        var freqSamples = 0
        for (i in 0 until cores.coerceAtMost(12)) {
            runCatching {
                val cur = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq").readText().trim().toLongOrNull()
                val max = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLongOrNull()
                if (cur != null && max != null && max > 0L) {
                    freqRatioSum += (cur.toDouble() / max.toDouble()).coerceIn(0.05, 1.0)
                    freqSamples++
                }
            }
        }
        val nowWall = android.os.SystemClock.elapsedRealtime()
        val nowProc = android.os.Process.getElapsedCpuTime()
        var procPct = 0.0
        if (prevWallMs > 0L && prevProcCpuMs >= 0L && nowWall > prevWallMs) {
            val dWall = (nowWall - prevWallMs).toDouble()
            val dProc = (nowProc - prevProcCpuMs).coerceAtLeast(0L).toDouble()
            procPct = ((dProc / (dWall * cores)) * 100.0).coerceIn(0.0, 100.0)
        }
        prevWallMs = nowWall
        prevProcCpuMs = nowProc

        val freqPct = if (freqSamples > 0) (freqRatioSum / freqSamples) * 48.0 else 18.0
        return (freqPct + procPct).roundToInt().coerceIn(4, 99)
    }

    fun hasAllFilesAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun openAllFilesAccessSettings(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val candidates = listOf(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")),
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            )
            return launchFirstWorkingSettings(
                context,
                candidates,
                "Aktifkan 'Izinkan pengelolaan semua file' agar File Explorer, Offline Search & Music Player bisa mengakses seluruh Internal/SD/USB."
            )
        }
        return openAppDetailsSettings(context)
    }

    fun openWifiSettings(context: Context): String = launchFirstWorkingSettings(
        context,
        listOf(
            Intent(Settings.ACTION_WIFI_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        ),
        "Membuka pengaturan Wi-Fi & Jaringan."
    )

    fun openBatterySettings(context: Context): String = launchFirstWorkingSettings(
        context,
        listOf(
            Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        ),
        "Membuka pengaturan Baterai."
    )

    fun openDateTimeSettings(context: Context): String = launchFirstWorkingSettings(
        context,
        listOf(
            Intent(Settings.ACTION_DATE_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        ),
        "Membuka pengaturan Tanggal & Jam."
    )
}
