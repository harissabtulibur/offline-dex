package com.offlinecyberdeck.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.offlinecyberdeck.core.database.dao.ArchiveDao
import com.offlinecyberdeck.core.database.dao.BookmarkDao
import com.offlinecyberdeck.core.database.dao.DownloadDao
import com.offlinecyberdeck.core.database.dao.HistoryDao
import com.offlinecyberdeck.core.database.dao.LibraryDao
import com.offlinecyberdeck.core.database.dao.MediaDao
import com.offlinecyberdeck.core.database.dao.MusicDao
import com.offlinecyberdeck.core.database.dao.RecentSearchDao
import com.offlinecyberdeck.core.database.dao.SettingsDao
import com.offlinecyberdeck.core.database.dao.SiteSettingDao
import com.offlinecyberdeck.core.database.dao.TabDao
import com.offlinecyberdeck.core.database.entity.AppSettingsEntity
import com.offlinecyberdeck.core.database.entity.ArchivedPageEntity
import com.offlinecyberdeck.core.database.entity.BookmarkEntity
import com.offlinecyberdeck.core.database.entity.DownloadEntity
import com.offlinecyberdeck.core.database.entity.HistoryEntity
import com.offlinecyberdeck.core.database.entity.LibraryItemEntity
import com.offlinecyberdeck.core.database.entity.MediaItemEntity
import com.offlinecyberdeck.core.database.entity.MusicTrackEntity
import com.offlinecyberdeck.core.database.entity.PlaylistEntity
import com.offlinecyberdeck.core.database.entity.PlaylistMediaCrossRef
import com.offlinecyberdeck.core.database.entity.RecentSearchEntity
import com.offlinecyberdeck.core.database.entity.SiteSettingEntity
import com.offlinecyberdeck.core.database.entity.TabEntity
import com.offlinecyberdeck.core.model.AccentColorPreset
import com.offlinecyberdeck.core.model.ArchiveStatus
import com.offlinecyberdeck.core.model.DownloadStatus
import com.offlinecyberdeck.core.model.DuplicatePolicy
import com.offlinecyberdeck.core.model.LibraryCategory
import com.offlinecyberdeck.core.model.LibraryFileType
import com.offlinecyberdeck.core.model.SearchEngineOption
import com.offlinecyberdeck.core.model.StorageTarget
import com.offlinecyberdeck.core.model.ThemeMode

class CyberDeckConverters {
    @TypeConverter fun fromThemeMode(v: ThemeMode): String = v.name
    @TypeConverter fun toThemeMode(v: String): ThemeMode = enumValueOrDefault(v, ThemeMode.DARK)

    @TypeConverter fun fromAccentPreset(v: AccentColorPreset): String = v.name
    @TypeConverter fun toAccentPreset(v: String): AccentColorPreset = enumValueOrDefault(v, AccentColorPreset.CYBER_CYAN)

    @TypeConverter fun fromSearchEngine(v: SearchEngineOption): String = v.name
    @TypeConverter fun toSearchEngine(v: String): SearchEngineOption = enumValueOrDefault(v, SearchEngineOption.GOOGLE)

    @TypeConverter fun fromArchiveStatus(v: ArchiveStatus): String = v.name
    @TypeConverter fun toArchiveStatus(v: String): ArchiveStatus = enumValueOrDefault(v, ArchiveStatus.READER_SNAPSHOT)

    @TypeConverter fun fromStorageTarget(v: StorageTarget): String = v.name
    @TypeConverter fun toStorageTarget(v: String): StorageTarget = enumValueOrDefault(v, StorageTarget.INTERNAL)

    @TypeConverter fun fromDownloadStatus(v: DownloadStatus): String = v.name
    @TypeConverter fun toDownloadStatus(v: String): DownloadStatus = enumValueOrDefault(v, DownloadStatus.FAILED)

    @TypeConverter fun fromDuplicatePolicy(v: DuplicatePolicy): String = v.name
    @TypeConverter fun toDuplicatePolicy(v: String): DuplicatePolicy = enumValueOrDefault(v, DuplicatePolicy.RENAME)

    @TypeConverter fun fromLibraryCategory(v: LibraryCategory): String = v.name
    @TypeConverter fun toLibraryCategory(v: String): LibraryCategory = enumValueOrDefault(v, LibraryCategory.BOOKS)

    @TypeConverter fun fromLibraryFileType(v: LibraryFileType): String = v.name
    @TypeConverter fun toLibraryFileType(v: String): LibraryFileType = enumValueOrDefault(v, LibraryFileType.OTHER)

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(default)
}

@Database(
    entities = [
        TabEntity::class,
        BookmarkEntity::class,
        HistoryEntity::class,
        DownloadEntity::class,
        ArchivedPageEntity::class,
        LibraryItemEntity::class,
        MediaItemEntity::class,
        MusicTrackEntity::class,
        PlaylistEntity::class,
        PlaylistMediaCrossRef::class,
        SiteSettingEntity::class,
        RecentSearchEntity::class,
        AppSettingsEntity::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(CyberDeckConverters::class)
abstract class CyberDeckDatabase : RoomDatabase() {
    abstract fun tabDao(): TabDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun archiveDao(): ArchiveDao
    abstract fun libraryDao(): LibraryDao
    abstract fun mediaDao(): MediaDao
    abstract fun musicDao(): MusicDao
    abstract fun siteSettingDao(): SiteSettingDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: CyberDeckDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN developerModeEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN accentPreset TEXT NOT NULL DEFAULT 'CYBER_CYAN'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN brightnessOverrideEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN screenBrightnessPercent INTEGER NOT NULL DEFAULT 80")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN nightDimmerPercent INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN immersiveFullscreen INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN hideNavChromeInBrowser INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN addressBarHeightDp INTEGER NOT NULL DEFAULT 40")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN addressBarFontSp INTEGER NOT NULL DEFAULT 14")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN addressBarCornerDp INTEGER NOT NULL DEFAULT 10")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN toolbarButtonSizeDp INTEGER NOT NULL DEFAULT 34")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN showTabStrip INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN bottomAddressBar INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN uiFontScale REAL NOT NULL DEFAULT 0.95")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN webViewTextZoom INTEGER NOT NULL DEFAULT 100")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN webViewDebuggingEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN showDevHud INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN hideToolbarInLandscape INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN desktopLauncherMode INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN dexSetupPromptDismissed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN pinnedExternalPackages TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE app_settings SET immersiveFullscreen = 1")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN webViewDarkMode INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN taskbarAutoHide INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN notificationSoundEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN desktopBackgroundId TEXT NOT NULL DEFAULT 'CYBER_NAVY'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN customWallpaperPaths TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN activeCustomWallpaperIndex INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN offlineTubeGridColumns INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN offlineTubeAutoImport INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN offlineTubeAutoPlayNext INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN offlineTubeDefaultSpeed REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN musicAutoImport INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN extraMediaSafUris TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `music_tracks` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `fileUri` TEXT NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `storageTarget` TEXT NOT NULL,
                        `volumeLabel` TEXT NOT NULL,
                        `isFavorite` INTEGER NOT NULL,
                        `playCount` INTEGER NOT NULL,
                        `lastPlayedAt` INTEGER,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_tracks_title` ON `music_tracks` (`title`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_tracks_fileName` ON `music_tracks` (`fileName`)")
            }
        }

        fun getInstance(context: Context): CyberDeckDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CyberDeckDatabase::class.java,
                    "cyber_deck.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
