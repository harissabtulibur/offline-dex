package com.offlinecyberdeck

import com.offlinecyberdeck.core.database.entity.AppSettingsEntity
import com.offlinecyberdeck.core.format.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {

    @Test
    fun formatBytes_formatsKnownAndUnknownValues() {
        assertEquals("Unknown", Formatters.formatBytes(null))
        assertEquals("Unknown", Formatters.formatBytes(-1L))
        assertEquals("512 B", Formatters.formatBytes(512L))
        assertEquals("1.00 KB", Formatters.formatBytes(1024L))
        assertEquals("1.50 MB", Formatters.formatBytes(1572864L))
    }

    @Test
    fun formatDuration_formatsMinutesAndHours() {
        assertEquals("--:--", Formatters.formatDurationMs(0L))
        assertEquals("01:05", Formatters.formatDurationMs(65_000L))
        assertEquals("1:02:03", Formatters.formatDurationMs(3_723_000L))
    }

    @Test
    fun resolveNavigationUrl_distinguishesUrlsAndSearchQueries() {
        assertEquals(
            "https://example.org",
            Formatters.resolveNavigationUrl("https://example.org", "https://duckduckgo.com/?q=%s")
        )
        assertEquals(
            "https://duckduckgo.com/?q=offline+survival+guide",
            Formatters.resolveNavigationUrl("offline survival guide", "https://duckduckgo.com/?q=%s")
        )
        assertFalse(Formatters.looksLikeUrl("hello world"))
        assertTrue(Formatters.looksLikeUrl("https://kiwix.org"))
    }

    @Test
    fun appSettings_defaultValuesMatchSpec() {
        val defaults = AppSettingsEntity()
        assertEquals(0.95f, defaults.uiFontScale, 0.001f)
        assertFalse(defaults.hideToolbarInLandscape)
        assertTrue(defaults.desktopLauncherMode)
        assertTrue(defaults.immersiveFullscreen)
        assertFalse(defaults.dexSetupPromptDismissed)
    }
}
