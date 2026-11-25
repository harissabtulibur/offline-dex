package com.offlinecyberdeck

import android.app.Application
import com.offlinecyberdeck.archive.PageArchiveEngine
import com.offlinecyberdeck.core.database.CyberDeckDatabase
import com.offlinecyberdeck.core.network.NetworkMonitor
import com.offlinecyberdeck.core.settings.SettingsRepository
import com.offlinecyberdeck.core.storage.DeckDriveManager
import com.offlinecyberdeck.downloads.DownloadRepository
import com.offlinecyberdeck.library.KnowledgeVaultRepository
import com.offlinecyberdeck.media.MediaVaultRepository
import com.offlinecyberdeck.music.MusicRepository
import com.offlinecyberdeck.search.UnifiedSearchRepository

class CyberDeckApplication : Application() {

    lateinit var database: CyberDeckDatabase
        private set
    lateinit var networkMonitor: NetworkMonitor
        private set
    lateinit var driveManager: DeckDriveManager
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var downloadRepository: DownloadRepository
        private set
    lateinit var archiveEngine: PageArchiveEngine
        private set
    lateinit var vaultRepository: KnowledgeVaultRepository
        private set
    lateinit var mediaRepository: MediaVaultRepository
        private set
    lateinit var musicRepository: MusicRepository
        private set
    lateinit var searchRepository: UnifiedSearchRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = CyberDeckDatabase.getInstance(this)
        networkMonitor = NetworkMonitor(this)
        driveManager = DeckDriveManager(this)
        settingsRepository = SettingsRepository(this, database, driveManager)
        downloadRepository = DownloadRepository(
            context = this,
            downloadDao = database.downloadDao(),
            settingsRepo = settingsRepository,
            driveManager = driveManager
        )
        archiveEngine = PageArchiveEngine(
            context = this,
            archiveDao = database.archiveDao(),
            siteSettingDao = database.siteSettingDao(),
            settingsRepo = settingsRepository,
            driveManager = driveManager
        )
        vaultRepository = KnowledgeVaultRepository(
            context = this,
            libraryDao = database.libraryDao(),
            driveManager = driveManager
        )
        mediaRepository = MediaVaultRepository(
            context = this,
            mediaDao = database.mediaDao(),
            driveManager = driveManager
        )
        musicRepository = MusicRepository(
            context = this,
            musicDao = database.musicDao(),
            driveManager = driveManager
        )
        searchRepository = UnifiedSearchRepository(
            context = this,
            db = database,
            driveManager = driveManager
        )
    }
}
