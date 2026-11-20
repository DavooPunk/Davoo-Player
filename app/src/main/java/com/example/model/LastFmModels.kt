package com.example.model

enum class LastFmPeriod(val apiValue: String, val label: String) {
    SEVEN_DAYS("7day", "7 días"),
    ONE_MONTH("1month", "1 mes"),
    TWELVE_MONTHS("12month", "12 meses"),
    OVERALL("overall", "Siempre")
}

enum class LastFmProfileTab(val label: String) {
    TOP_TRACKS("Top Canciones"),
    LOVED_TRACKS("Favoritas"),
    RECENT_TRACKS("Historial")
}

data class LastFmSession(
    val sessionKey: String,
    val username: String,
    val avatarUrl: String? = null
)

data class LastFmUserInfo(
    val username: String,
    val realName: String = "",
    val playcount: Long = 0L,
    val avatarUrl: String? = null
)

data class DeezerTrackEnrichment(
    val deezerTrackId: Long,
    val title: String,
    val artist: String,
    val albumTitle: String,
    val coverXlUrl: String?,
    val previewUrl: String?,
    val durationSec: Int
)

data class LastFmTrackItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val playcount: Long = 0L,
    val rank: Int = 0,
    val isNowPlaying: Boolean = false,
    val isLoved: Boolean = false,
    val dateLabel: String = "",
    val fallbackArtworkUrl: String? = null,
    val deezerEnrichment: DeezerTrackEnrichment? = null,
    val deezerLookupDone: Boolean = false
) {
    val effectiveArtworkUrl: String?
        get() = deezerEnrichment?.coverXlUrl?.takeIf { it.isNotBlank() } ?: fallbackArtworkUrl

    val previewUrl: String?
        get() = deezerEnrichment?.previewUrl?.takeIf { it.isNotBlank() }

    val lookupKey: String
        get() = buildLookupKey(artist, title)

    fun toPreviewAudioTrack(): AudioTrack? {
        val url = previewUrl ?: return null
        val deezerId = deezerEnrichment?.deezerTrackId ?: id.hashCode().toLong()
        val albumName = deezerEnrichment?.albumTitle?.takeIf { it.isNotBlank() }
            ?: album.ifBlank { "Last.fm · Deezer Preview" }
        val previewDurationMs = (deezerEnrichment?.durationSec?.coerceIn(15, 35) ?: 30) * 1000L
        return AudioTrack(
            id = "$PREVIEW_TRACK_ID_PREFIX$deezerId",
            contentUri = url,
            title = title,
            artist = artist,
            album = albumName,
            albumId = -deezerId.coerceAtLeast(1L),
            albumArtUri = effectiveArtworkUrl,
            durationMs = previewDurationMs,
            format = AudioFormat.MP3,
            mimeType = "audio/mpeg",
            sizeBytes = 0L,
            dateAddedSec = System.currentTimeMillis() / 1000L,
            folderName = "Last.fm · Preview",
            isFavorite = isLoved
        )
    }

    companion object {
        const val PREVIEW_TRACK_ID_PREFIX = "deezer_preview:"

        fun buildLookupKey(artist: String, title: String): String {
            return "${artist.trim().lowercase()}|${title.trim().lowercase()}"
        }
    }
}
