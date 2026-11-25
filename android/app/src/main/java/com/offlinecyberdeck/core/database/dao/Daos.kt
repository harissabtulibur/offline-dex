package com.offlinecyberdeck.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.offlinecyberdeck.core.database.entity.AppSettingsEntity
import com.offlinecyberdeck.core.database.entity.ArchivedPageEntity
import com.offlinecyberdeck.core.database.entity.BookmarkEntity
import com.offlinecyberdeck.core.database.entity.DownloadEntity
import com.offlinecyberdeck.core.database.entity.HistoryEntity
import com.offlinecyberdeck.core.database.entity.LibraryItemEntity
import com.offlinecyberdeck.core.database.entity.MediaItemEntity
import com.offlinecyberdeck.core.database.entity.PlaylistEntity
import com.offlinecyberdeck.core.database.entity.PlaylistMediaCrossRef
import com.offlinecyberdeck.core.database.entity.RecentSearchEntity
import com.offlinecyberdeck.core.database.entity.SiteSettingEntity
import com.offlinecyberdeck.core.database.entity.TabEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs ORDER BY position ASC")
    fun observeAll(): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs WHERE isIncognito = 0 ORDER BY position ASC")
    suspend fun getPersistentTabs(): List<TabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tabs: List<TabEntity>)

    @Query("DELETE FROM tabs WHERE isIncognito = 0")
    suspend fun clearPersistentTabs()

    @Query("DELETE FROM tabs")
    suspend fun clearAll()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE title LIKE '%' || :q || '%' OR url LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%'")
    suspend fun search(q: String): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("SELECT * FROM bookmarks")
    suspend fun getAllSnapshot(): List<BookmarkEntity>
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 500")
    fun observeRecent(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE title LIKE '%' || :q || '%' OR url LIKE '%' || :q || '%' ORDER BY visitedAt DESC LIMIT 100")
    suspend fun search(q: String): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadEntity?

    @Query("SELECT COUNT(*) FROM downloads")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM downloads")
    suspend fun getAllSnapshot(): List<DownloadEntity>
}

@Dao
interface ArchiveDao {
    @Query("SELECT * FROM archived_pages ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<ArchivedPageEntity>>

    @Query("SELECT * FROM archived_pages WHERE id = :id")
    suspend fun getById(id: String): ArchivedPageEntity?

    @Query("SELECT * FROM archived_pages WHERE url = :url ORDER BY capturedAt DESC LIMIT 1")
    suspend fun findLatestByUrl(url: String): ArchivedPageEntity?

    @Query("SELECT * FROM archived_pages WHERE title LIKE '%' || :q || '%' OR url LIKE '%' || :q || '%' OR readerText LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%'")
    suspend fun search(q: String): List<ArchivedPageEntity>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM archived_pages")
    suspend fun totalArchivedBytes(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(page: ArchivedPageEntity)

    @Delete
    suspend fun delete(page: ArchivedPageEntity)

    @Query("SELECT * FROM archived_pages")
    suspend fun getAllSnapshot(): List<ArchivedPageEntity>
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_items ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<LibraryItemEntity>>

    @Query("SELECT * FROM library_items WHERE id = :id")
    suspend fun getById(id: String): LibraryItemEntity?

    @Query("SELECT * FROM library_items WHERE title LIKE '%' || :q || '%' OR source LIKE '%' || :q || '%' OR description LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%' OR fileName LIKE '%' || :q || '%'")
    suspend fun search(q: String): List<LibraryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LibraryItemEntity)

    @Delete
    suspend fun delete(item: LibraryItemEntity)

    @Query("SELECT * FROM library_items")
    suspend fun getAllSnapshot(): List<LibraryItemEntity>
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE localFileUri = :uri LIMIT 1")
    suspend fun findByLocalUri(uri: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE videoId = :videoId LIMIT 1")
    suspend fun findByVideoId(videoId: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE title LIKE '%' || :q || '%' OR channel LIKE '%' || :q || '%' OR localFileName LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%' OR description LIKE '%' || :q || '%'")
    suspend fun search(q: String): List<MediaItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MediaItemEntity)

    @Delete
    suspend fun delete(item: MediaItemEntity)

    @Query("SELECT * FROM media_items")
    suspend fun getAllSnapshot(): List<MediaItemEntity>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToPlaylist(crossRef: PlaylistMediaCrossRef)

    @Query("DELETE FROM playlist_media_cross_ref WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun removeFromPlaylist(playlistId: String, mediaId: String)

    @Query("SELECT m.* FROM media_items m INNER JOIN playlist_media_cross_ref p ON m.id = p.mediaId WHERE p.playlistId = :playlistId ORDER BY p.position ASC")
    fun observePlaylistItems(playlistId: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM playlist_media_cross_ref")
    suspend fun getAllPlaylistCrossRefs(): List<PlaylistMediaCrossRef>
}

@Dao
interface SiteSettingDao {
    @Query("SELECT * FROM site_settings ORDER BY host ASC")
    fun observeAll(): Flow<List<SiteSettingEntity>>

    @Query("SELECT * FROM site_settings WHERE host = :host")
    suspend fun getForHost(host: String): SiteSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: SiteSettingEntity)

    @Query("DELETE FROM site_settings WHERE host = :host")
    suspend fun deleteHost(host: String)
}

@Dao
interface RecentSearchDao {
    @Query("SELECT * FROM recent_searches ORDER BY searchedAt DESC LIMIT 15")
    fun observeRecent(): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: RecentSearchEntity)

    @Query("DELETE FROM recent_searches")
    suspend fun clearAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observe(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun get(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: AppSettingsEntity)
}

@Dao
interface MusicDao {
    @Query("SELECT * FROM music_tracks ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<com.offlinecyberdeck.core.database.entity.MusicTrackEntity>>

    @Query("SELECT * FROM music_tracks")
    suspend fun getAllSnapshot(): List<com.offlinecyberdeck.core.database.entity.MusicTrackEntity>

    @Query("SELECT * FROM music_tracks WHERE id = :id")
    suspend fun getById(id: String): com.offlinecyberdeck.core.database.entity.MusicTrackEntity?

    @Query("SELECT * FROM music_tracks WHERE fileUri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): com.offlinecyberdeck.core.database.entity.MusicTrackEntity?

    @Query("SELECT * FROM music_tracks WHERE title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%' OR album LIKE '%' || :q || '%' OR fileName LIKE '%' || :q || '%'")
    suspend fun search(q: String): List<com.offlinecyberdeck.core.database.entity.MusicTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: com.offlinecyberdeck.core.database.entity.MusicTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<com.offlinecyberdeck.core.database.entity.MusicTrackEntity>)

    @Delete
    suspend fun delete(track: com.offlinecyberdeck.core.database.entity.MusicTrackEntity)

    @Query("DELETE FROM music_tracks")
    suspend fun clearAll()
}
