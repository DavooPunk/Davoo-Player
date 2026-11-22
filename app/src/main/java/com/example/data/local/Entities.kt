package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.model.AudioFormat
import com.example.model.AudioTrack

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index(value = ["playlistId", "trackId"], unique = true)]
)
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val entryId: Long = 0,
    val playlistId: Long,
    val trackId: String,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtUri: String?,
    val durationMs: Long,
    val formatName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val folderName: String,
    val isSafImported: Boolean,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toAudioTrack(): AudioTrack {
        val format = AudioFormat.entries.firstOrNull { it.name == formatName } ?: AudioFormat.MP3
        return AudioTrack(
            id = trackId,
            contentUri = contentUri,
            title = title,
            artist = artist,
            album = album,
            albumId = albumId,
            albumArtUri = albumArtUri,
            durationMs = durationMs,
            format = format,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            dateAddedSec = addedAt / 1000L,
            folderName = folderName,
            isSafImported = isSafImported
        )
    }

    companion object {
        fun fromAudioTrack(playlistId: Long, track: AudioTrack, position: Int): PlaylistTrackEntity {
            return PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = track.id,
                contentUri = track.contentUri,
                title = track.title,
                artist = track.artist,
                album = track.album,
                albumId = track.albumId,
                albumArtUri = track.albumArtUri,
                durationMs = track.durationMs,
                formatName = track.format.name,
                mimeType = track.mimeType,
                sizeBytes = track.sizeBytes,
                folderName = track.folderName,
                isSafImported = track.isSafImported,
                position = position
            )
        }
    }
}

@Entity(tableName = "imported_tracks")
data class ImportedTrackEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val formatName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val folderName: String,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toAudioTrack(): AudioTrack {
        val format = AudioFormat.entries.firstOrNull { it.name == formatName } ?: AudioFormat.MP3
        return AudioTrack(
            id = "saf_${uri.hashCode()}",
            contentUri = uri,
            title = title,
            artist = artist,
            album = album,
            albumId = -1L,
            albumArtUri = null,
            durationMs = durationMs,
            format = format,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            dateAddedSec = addedAt / 1000L,
            folderName = folderName,
            isSafImported = true
        )
    }
}

@Entity(tableName = "track_stats")
data class TrackStatsEntity(
    @PrimaryKey val trackId: String,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L
)

@Entity(tableName = "track_tag_overrides")
data class TrackTagOverrideEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val year: Int,
    val trackNumber: Int,
    val customArtworkUri: String? = null
)

@Entity(tableName = "excluded_folders")
data class ExcludedFolderEntity(
    @PrimaryKey val folderName: String,
    val excludedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "track_lyrics")
data class TrackLyricsEntity(
    @PrimaryKey val trackId: String,
    val rawLrcText: String,
    val sourceLabel: String = "Archivo .lrc guardado",
    val updatedAt: Long = System.currentTimeMillis(),
    val offsetMs: Long = 0L
)

@Entity(tableName = "track_lyrics_translations")
data class TrackLyricsTranslationEntity(
    @PrimaryKey val trackId: String,
    val languageCode: String,
    val rawTranslatedText: String,
    val sourceLabel: String,
    val updatedAt: Long = System.currentTimeMillis()
)

data class PlaylistWithEntries(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlistId"
    )
    val entries: List<PlaylistTrackEntity>
)
