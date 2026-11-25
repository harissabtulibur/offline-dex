package com.offlinecyberdeck.core.format

import android.util.Patterns
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object Formatters {
    fun formatBytes(bytes: Long?): String {
        if (bytes == null || bytes < 0L) return "Unknown"
        if (bytes < 1024L) return "$bytes B"
        val unit = 1024.0
        val exp = (ln(bytes.toDouble()) / ln(unit)).toInt().coerceAtMost(4)
        val prefix = "KMGT"[exp - 1]
        val value = bytes / unit.pow(exp.toDouble())
        return String.format(Locale.US, "%.2f %sB", value, prefix)
    }

    fun formatDurationMs(ms: Long): String {
        if (ms <= 0L) return "--:--"
        val totalSeconds = ms / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatDateTime(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }

    fun formatDateOnly(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }

    fun looksLikeUrl(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.contains(' ')) return false
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true)
        ) {
            return true
        }
        return Patterns.WEB_URL.matcher(trimmed).matches() && trimmed.contains('.')
    }

    fun resolveNavigationUrl(input: String, searchTemplate: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "about:cyberdeck"
        if (trimmed == "about:cyberdeck" || trimmed == "about:blank") return trimmed
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        if (looksLikeUrl(trimmed)) {
            return "https://$trimmed"
        }
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        return if (searchTemplate.contains("%s")) {
            searchTemplate.replace("%s", encoded)
        } else {
            "https://www.google.com/search?q=$encoded"
        }
    }

    fun extractHost(url: String): String {
        return runCatching {
            android.net.Uri.parse(url).host?.lowercase(Locale.US) ?: ""
        }.getOrDefault("")
    }
}
