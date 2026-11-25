package com.offlinecyberdeck.desktop

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

data class DeckNotificationItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAtMs: Long,
    val isClearable: Boolean,
    val contentIntent: PendingIntent?
)

object NotificationCenterStore {
    private val _notifications = MutableStateFlow<List<DeckNotificationItem>>(emptyList())
    val notifications: StateFlow<List<DeckNotificationItem>> = _notifications.asStateFlow()

    private val _newNotificationEvents = MutableSharedFlow<DeckNotificationItem>(extraBufferCapacity = 16)
    val newNotificationEvents: SharedFlow<DeckNotificationItem> = _newNotificationEvents.asSharedFlow()

    private val seenSignatures = ConcurrentHashMap<String, Long>()
    private val internalAlerts = mutableListOf<DeckNotificationItem>()

    @Volatile
    private var initialSeedDone = false

    @Volatile
    private var lastSoundMs = 0L

    @Volatile
    private var serviceRef: WeakReference<CyberDeckNotificationService>? = null

    internal fun attachService(service: CyberDeckNotificationService) {
        serviceRef = WeakReference(service)
    }

    internal fun detachService(service: CyberDeckNotificationService) {
        if (serviceRef?.get() === service) {
            serviceRef = null
        }
    }

    internal fun updateFromActive(items: List<DeckNotificationItem>) {
        if (!initialSeedDone) {
            items.forEach { seenSignatures[it.key] = it.postedAtMs }
            initialSeedDone = true
        } else {
            items.forEach { item ->
                val prevTs = seenSignatures[item.key]
                if (prevTs == null || prevTs != item.postedAtMs) {
                    seenSignatures[item.key] = item.postedAtMs
                    _newNotificationEvents.tryEmit(item)
                }
            }
        }
        val activeKeys = items.mapTo(HashSet()) { it.key }
        seenSignatures.keys.removeAll { !it.startsWith("internal_") && !activeKeys.contains(it) }

        val merged = synchronized(internalAlerts) {
            (internalAlerts + items)
                .distinctBy { it.key }
                .sortedByDescending { it.postedAtMs }
                .take(25)
        }
        _notifications.value = merged
    }

    fun postInternalAlert(
        title: String,
        text: String,
        appLabel: String = "Offline Cyber Deck",
        keyPrefix: String = "internal"
    ): DeckNotificationItem {
        val now = System.currentTimeMillis()
        val item = DeckNotificationItem(
            key = "${keyPrefix}_$now",
            packageName = "com.offlinecyberdeck",
            appLabel = appLabel,
            title = title,
            text = text,
            postedAtMs = now,
            isClearable = true,
            contentIntent = null
        )
        seenSignatures[item.key] = now
        val merged = synchronized(internalAlerts) {
            internalAlerts.add(0, item)
            if (internalAlerts.size > 10) {
                internalAlerts.subList(10, internalAlerts.size).clear()
            }
            val currentNonInternal = _notifications.value.filterNot { it.key.startsWith("internal_") || it.key.startsWith("${keyPrefix}_") }
            (internalAlerts + currentNonInternal)
                .distinctBy { it.key }
                .sortedByDescending { it.postedAtMs }
                .take(25)
        }
        _notifications.value = merged
        _newNotificationEvents.tryEmit(item)
        return item
    }

    fun playNotificationSound(context: Context, enabled: Boolean = true) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (now - lastSoundMs < 650L) return
        lastSoundMs = now
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtone.play()
        }
    }

    fun dismiss(key: String) {
        synchronized(internalAlerts) {
            internalAlerts.removeAll { it.key == key }
        }
        val svc = serviceRef?.get()
        if (svc != null && !key.startsWith("internal_")) {
            runCatching { svc.cancelNotification(key) }
        }
        _notifications.value = _notifications.value.filterNot { it.key == key }
    }

    fun clearAllClearable() {
        synchronized(internalAlerts) {
            internalAlerts.clear()
        }
        val svc = serviceRef?.get()
        val clearableKeys = _notifications.value.filter { it.isClearable }.map { it.key }
        if (svc != null) {
            clearableKeys.forEach { key -> runCatching { svc.cancelNotification(key) } }
        }
        _notifications.value = _notifications.value.filterNot { it.isClearable }
    }

    fun isNotificationAccessEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        if (flat.isBlank()) return false
        val expected = ComponentName(context, CyberDeckNotificationService::class.java)
        return flat.split(':').any { token ->
            ComponentName.unflattenFromString(token) == expected ||
                token.contains(context.packageName)
        }
    }

    fun openNotificationAccessSettings(context: Context): String {
        val candidates = listOf(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) {
                return "Aktifkan 'Offline Cyber Deck' di Akses Notifikasi agar notif WA/YouTube/sistem tampil di taskbar."
            }
        }
        return "Buka Setelan Android → Akses Notifikasi."
    }
}

class CyberDeckNotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationCenterStore.attachService(this)
        refreshSnapshot()
    }

    override fun onListenerDisconnected() {
        NotificationCenterStore.detachService(this)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshSnapshot()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshSnapshot()
    }

    private fun refreshSnapshot() {
        val raw = runCatching { activeNotifications }.getOrNull() ?: return
        val pm = packageManager
        val list = ArrayList<DeckNotificationItem>(25)
        for (sbn in raw) {
            val notif = sbn.notification ?: continue
            if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue
            if (sbn.packageName == packageName && sbn.isOngoing) continue

            val extras = notif.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
            val body = (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras?.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty().trim()
            if (title.isBlank() && body.isBlank()) continue

            val appLabel = runCatching {
                val ai = pm.getApplicationInfo(sbn.packageName, 0)
                pm.getApplicationLabel(ai).toString()
            }.getOrDefault(sbn.packageName)

            list += DeckNotificationItem(
                key = sbn.key,
                packageName = sbn.packageName,
                appLabel = appLabel,
                title = title.ifBlank { appLabel },
                text = body,
                postedAtMs = sbn.postTime,
                isClearable = sbn.isClearable,
                contentIntent = notif.contentIntent
            )
            if (list.size >= 25) break
        }
        NotificationCenterStore.updateFromActive(list)
    }
}
