package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylistsWithEntries(): Flow<List<PlaylistWithEntries>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, description = :description WHERE id = :playlistId")
    suspend fun updatePlaylistDetails(playlistId: Long, name: String, description: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistTrack(entry: PlaylistTrackEntity): Long

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deleteTrackFromPlaylist(playlistId: Long, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId IN (:trackIds)")
    suspend fun deleteTracksFromPlaylist(playlistId: Long, trackIds: List<String>)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getEntriesOrdered(playlistId: Long): List<PlaylistTrackEntity>

    @Query("UPDATE playlist_tracks SET position = :position WHERE entryId = :entryId")
    suspend fun updateTrackPosition(entryId: Long, position: Int)

    @Transaction
    suspend fun reorderTrack(playlistId: Long, fromIndex: Int, toIndex: Int) {
        val list = getEntriesOrdered(playlistId).toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val moved = list.removeAt(fromIndex)
        list.add(toIndex, moved)
        list.forEachIndexed { idx, item ->
            if (item.position != idx) {
                updateTrackPosition(item.entryId, idx)
            }
        }
    }
}

@Dao
interface ImportedTrackDao {
    @Query("SELECT * FROM imported_tracks ORDER BY addedAt DESC")
    fun observeImportedTracks(): Flow<List<ImportedTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<ImportedTrackEntity>)

    @Query("DELETE FROM imported_tracks WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("DELETE FROM imported_tracks WHERE uri IN (:uris)")
    suspend fun deleteByUris(uris: List<String>)

    @Query("DELETE FROM imported_tracks WHERE folderName = :folderName")
    suspend fun deleteByFolderName(folderName: String)
}

@Dao
interface TrackStatsDao {
    @Query("SELECT * FROM track_stats")
    fun observeAllStats(): Flow<List<TrackStatsEntity>>

    @Query("SELECT * FROM track_stats WHERE trackId = :trackId LIMIT 1")
    suspend fun getStatsForTrack(trackId: String): TrackStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: TrackStatsEntity)

    @Transaction
    suspend fun setFavorite(trackId: String, isFavorite: Boolean): Boolean {
        val existing = getStatsForTrack(trackId)
        upsert(
            TrackStatsEntity(
                trackId = trackId,
                isFavorite = isFavorite,
                playCount = existing?.playCount ?: 0,
                lastPlayedAt = existing?.lastPlayedAt ?: 0L
            )
        )
        return isFavorite
    }

    @Transaction
    suspend fun toggleFavorite(trackId: String): Boolean {
        val existing = getStatsForTrack(trackId)
        val nextFav = !(existing?.isFavorite ?: false)
        upsert(
            TrackStatsEntity(
                trackId = trackId,
                isFavorite = nextFav,
                playCount = existing?.playCount ?: 0,
                lastPlayedAt = existing?.lastPlayedAt ?: 0L
            )
        )
        return nextFav
    }

    @Transaction
    suspend fun recordPlay(trackId: String, timestampMs: Long = System.currentTimeMillis()) {
        val existing = getStatsForTrack(trackId)
        upsert(
            TrackStatsEntity(
                trackId = trackId,
                isFavorite = existing?.isFavorite ?: false,
                playCount = (existing?.playCount ?: 0) + 1,
                lastPlayedAt = timestampMs
            )
        )
    }
}

@Dao
interface TrackTagOverrideDao {
    @Query("SELECT * FROM track_tag_overrides")
    fun observeAllOverrides(): Flow<List<TrackTagOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: TrackTagOverrideEntity)
}

@Dao
interface ExcludedFolderDao {
    @Query("SELECT * FROM excluded_folders ORDER BY folderName ASC")
    fun observeExcludedFolders(): Flow<List<ExcludedFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun excludeFolder(entity: ExcludedFolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun excludeFolders(entities: List<ExcludedFolderEntity>)

    @Query("DELETE FROM excluded_folders WHERE folderName = :folderName")
    suspend fun includeFolder(folderName: String)
}

@Dao
interface TrackLyricsDao {
    @Query("SELECT * FROM track_lyrics WHERE trackId = :trackId LIMIT 1")
    suspend fun getLyricsForTrack(trackId: String): TrackLyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrackLyricsEntity)

    @Query("DELETE FROM track_lyrics WHERE trackId = :trackId")
    suspend fun deleteForTrack(trackId: String)
}

@Dao
interface TrackLyricsTranslationDao {
    @Query("SELECT * FROM track_lyrics_translations WHERE trackId = :trackId LIMIT 1")
    suspend fun getTranslationForTrack(trackId: String): TrackLyricsTranslationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrackLyricsTranslationEntity)

    @Query("DELETE FROM track_lyrics_translations WHERE trackId = :trackId")
    suspend fun deleteForTrack(trackId: String)
}
