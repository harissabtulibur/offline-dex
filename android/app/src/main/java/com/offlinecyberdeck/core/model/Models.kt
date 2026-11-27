package com.offlinecyberdeck.core.model

enum class ThemeMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System")
}

enum class AccentColorPreset(
    val label: String,
    val darkPrimaryArgb: Long,
    val lightPrimaryArgb: Long,
    val darkContainerArgb: Long,
    val lightContainerArgb: Long
) {
    CYBER_CYAN("Cyber Cyan", 0xFF22D3EE, 0xFF0E7490, 0xFF0E3A46, 0xFFCFFAFE),
    EMERALD("Emerald", 0xFF10B981, 0xFF047857, 0xFF064E3B, 0xFFD1FAE5),
    AMBER("Amber", 0xFFF59E0B, 0xFFB45309, 0xFF451A03, 0xFFFEF3C7),
    CRIMSON("Crimson", 0xFFEF4444, 0xFFB91C1C, 0xFF450A0A, 0xFFFEE2E2),
    VIOLET("Violet", 0xFFA855F7, 0xFF7E22CE, 0xFF3B0764, 0xFFF3E8FF),
    SLATE_MONO("Mono", 0xFFE2E8F0, 0xFF334155, 0xFF1E293B, 0xFFE2E8F0)
}

enum class SearchEngineOption(
    val label: String,
    val templateUrl: String
) {
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query=%s"),
    WIKIPEDIA("Wikipedia", "https://en.wikipedia.org/w/index.php?search=%s"),
    CUSTOM("Custom", "https://www.google.com/search?q=%s")
}

enum class ArchiveStatus(val label: String) {
    QUEUED("Queued"),
    CAPTURING("Capturing"),
    READER_SNAPSHOT("Reader Snapshot"),
    PARTIAL_ARCHIVE("Partial Archive"),
    FULL_ARCHIVE("Full Archive"),
    FAILED("Failed")
}

enum class StorageTarget(val label: String) {
    INTERNAL("Internal Storage"),
    DECK_DRIVE("Deck Drive (USB-C SSD)")
}

enum class DownloadStatus(val label: String) {
    QUEUED("Queued"),
    DOWNLOADING("Downloading"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

enum class DuplicatePolicy(val label: String) {
    RENAME("Auto-rename"),
    ASK("Ask / Keep both")
}

enum class LibraryCategory(val label: String) {
    ENCYCLOPEDIA("Encyclopedia & Knowledge"),
    BOOKS("Books & Literature"),
    EDUCATION("Education & Courses"),
    PROGRAMMING("Programming & IT"),
    NETWORKING("Networking & Cybersecurity"),
    SCIENCE("Science & Nature"),
    HISTORY("History & Culture"),
    DIY("DIY & Repair"),
    LANGUAGES("Languages & Dictionaries"),
    MAPS("Maps & Travel"),
    DATABASES("Databases & Public Data"),
    MEDIA("Media"),
    FOOD("Food & Recipes")
}

enum class LibraryFileType(val label: String) {
    PDF("PDF"),
    EPUB("EPUB"),
    HTML("HTML"),
    TEXT("Text / Markdown"),
    IMAGE("Image"),
    AUDIO("Audio"),
    VIDEO("Video"),
    ZIP("Archive (ZIP)"),
    ZIM("Kiwix (ZIM)"),
    MAP("Map Package"),
    OTHER("Document")
}

enum class MediaAvailability(val label: String) {
    AVAILABLE_OFFLINE("Available Offline"),
    METADATA_ONLY("No Local File"),
    DRIVE_DISCONNECTED("Drive Disconnected")
}

enum class MainDestination(val route: String, val label: String) {
    BROWSER("browser", "Browser"),
    LIBRARY("library", "Vault"),
    MEDIA("media", "Video"),
    FILES("files", "Explorer"),
    DOWNLOADS("downloads", "Downloads"),
    BOOKMARKS("bookmarks", "Bookmarks"),
    HISTORY("history", "History"),
    SEARCH("search", "Search"),
    TASK_MANAGER("task_manager", "Task Mgr"),
    SETTINGS("settings", "Settings")
}

enum class DesktopBackgroundPreset(
    val id: String,
    val label: String,
    val topColorArgb: Long,
    val midColorArgb: Long,
    val bottomColorArgb: Long,
    val accentColorArgb: Long,
    val isLight: Boolean = false
) {
    CYBER_NAVY("CYBER_NAVY", "Cyber Navy", 0xFF070B12, 0xFF0E1726, 0xFF09111B, 0xFF22D3EE),
    MINIMAL_CHARCOAL("MINIMAL_CHARCOAL", "Matte Charcoal", 0xFF111317, 0xFF181B20, 0xFF0F1115, 0xFF94A3B8),
    EMERALD_AURORA("EMERALD_AURORA", "Emerald Aurora", 0xFF04120E, 0xFF08261E, 0xFF051510, 0xFF10B981),
    MIDNIGHT_VIOLET("MIDNIGHT_VIOLET", "Midnight Violet", 0xFF0E0919, 0xFF1B1230, 0xFF0C0816, 0xFFA855F7),
    CARBON_AMBER("CARBON_AMBER", "Carbon Dusk", 0xFF14100C, 0xFF211811, 0xFF100C09, 0xFFF59E0B),
    ARCTIC_CLEAN("ARCTIC_CLEAN", "Arctic Clean", 0xFFDCE6F2, 0xFFEAF0F7, 0xFFCFDCEB, 0xFF0E7490, isLight = true);

    companion object {
        fun fromId(id: String): DesktopBackgroundPreset =
            entries.firstOrNull { it.id == id } ?: CYBER_NAVY
    }
}

data class DeckDriveStatus(
    val configuredUri: String? = null,
    val displayName: String = "Not connected",
    val isConnected: Boolean = false,
    val isWritable: Boolean = false,
    val hasPersistedPermission: Boolean = false,
    val totalBytes: Long? = null,
    val availableBytes: Long? = null,
    val statusDetail: String = "No external Deck Drive selected"
)

data class StorageDashboardStats(
    val internalTotalBytes: Long? = null,
    val internalAvailableBytes: Long? = null,
    val internalAppArchivesBytes: Long = 0L,
    val internalAppDownloadsBytes: Long = 0L,
    val archiveCount: Int = 0,
    val libraryCount: Int = 0,
    val mediaLocalCount: Int = 0,
    val mediaMetadataOnlyCount: Int = 0,
    val downloadCount: Int = 0,
    val deckDrive: DeckDriveStatus = DeckDriveStatus()
)

enum class SearchResultType(val label: String) {
    FILE("Device & External Files"),
    ARCHIVE("Archived Pages"),
    LIBRARY("Knowledge Vault"),
    MEDIA("Media Vault"),
    BOOKMARK("Bookmarks"),
    HISTORY("History")
}

data class UnifiedSearchResult(
    val id: String,
    val type: SearchResultType,
    val title: String,
    val subtitle: String,
    val tags: String = "",
    val score: Int = 0,
    val isAvailableOffline: Boolean = true,
    val targetRef: String = "",
    val mimeType: String = "",
    val fileSizeBytes: Long = 0L,
    val volumeLabel: String = ""
)

data class PackageDirectoryEntry(
    val id: String,
    val name: String,
    val category: LibraryCategory,
    val language: String,
    val estimatedSize: String,
    val description: String,
    val sourceUrl: String,
    val formatNote: String
)
