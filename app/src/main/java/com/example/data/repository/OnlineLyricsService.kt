package com.example.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Serializable
data class LrcLibTrackDto(
    val id: Long = 0L,
    val trackName: String = "",
    val artistName: String = "",
    val albumName: String = "",
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)

data class OnlineLyricsCandidate(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int?,
    val rawLyrics: String,
    val isSynced: Boolean
)

interface OnlineLyricsFetcher {
    suspend fun searchLyrics(
        title: String,
        artist: String,
        album: String,
        durationSec: Int?,
        customQuery: String? = null
    ): Result<List<OnlineLyricsCandidate>>
}

class LrcLibLyricsFetcher(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = DEFAULT_BASE_URL
) : OnlineLyricsFetcher {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    override suspend fun searchLyrics(
        title: String,
        artist: String,
        album: String,
        durationSec: Int?,
        customQuery: String?
    ): Result<List<OnlineLyricsCandidate>> = withContext(Dispatchers.IO) {
        runCatching {
            val candidates = linkedMapOf<Long, OnlineLyricsCandidate>()
            val cleanQuery = customQuery?.trim().orEmpty()

            if (cleanQuery.isNotEmpty()) {
                fetchSearch(q = cleanQuery).forEach { dto ->
                    dto.toCandidate()?.let { candidates[it.id] = it }
                }
            } else {
                val cleanedTitle = cleanMetadataTerm(title)
                val cleanedArtist = cleanArtistTerm(artist)
                val cleanedAlbum = cleanAlbumTerm(album)

                if (cleanedTitle.isNotEmpty() && cleanedArtist.isNotEmpty()) {
                    fetchExactGet(
                        trackName = cleanedTitle,
                        artistName = cleanedArtist,
                        albumName = cleanedAlbum,
                        durationSec = durationSec
                    )?.toCandidate()?.let { candidates[it.id] = it }
                }

                val searchQuery = listOf(cleanedArtist, cleanedTitle)
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
                    .ifEmpty { title.trim() }

                if (searchQuery.isNotEmpty()) {
                    fetchSearch(q = searchQuery).forEach { dto ->
                        dto.toCandidate()?.let { candidates.putIfAbsent(it.id, it) }
                    }
                }

                if (candidates.isEmpty() && cleanedTitle.isNotEmpty() && cleanedTitle != searchQuery) {
                    fetchSearch(q = cleanedTitle).forEach { dto ->
                        dto.toCandidate()?.let { candidates.putIfAbsent(it.id, it) }
                    }
                }
            }

            candidates.values
                .sortedWith(
                    compareByDescending<OnlineLyricsCandidate> { it.isSynced }
                        .thenBy {
                            if (durationSec != null && durationSec > 0 && it.durationSec != null) {
                                kotlin.math.abs(it.durationSec - durationSec)
                            } else Int.MAX_VALUE
                        }
                )
        }
    }

    private fun fetchExactGet(
        trackName: String,
        artistName: String,
        albumName: String?,
        durationSec: Int?
    ): LrcLibTrackDto? {
        val urlBuilder = "$baseUrl/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", trackName)
            .addQueryParameter("artist_name", artistName)
        if (!albumName.isNullOrBlank()) {
            urlBuilder.addQueryParameter("album_name", albumName)
        }
        if (durationSec != null && durationSec > 0) {
            urlBuilder.addQueryParameter("duration", durationSec.toString())
        }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return@use null
            runCatching { json.decodeFromString<LrcLibTrackDto>(body) }.getOrNull()
        }
    }

    private fun fetchSearch(q: String): List<LrcLibTrackDto> {
        val url = "$baseUrl/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()
            return json.decodeFromString<List<LrcLibTrackDto>>(body)
        }
    }

    private fun LrcLibTrackDto.toCandidate(): OnlineLyricsCandidate? {
        val synced = syncedLyrics?.trim()?.takeIf { it.isNotEmpty() }
        val plain = plainLyrics?.trim()?.takeIf { it.isNotEmpty() }
        val raw = synced ?: plain ?: return null
        return OnlineLyricsCandidate(
            id = id,
            title = trackName.ifBlank { "Sin título" },
            artist = artistName.ifBlank { "Artista desconocido" },
            album = albumName,
            durationSec = duration?.roundToInt()?.takeIf { it > 0 },
            rawLyrics = raw,
            isSynced = synced != null
        )
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://lrclib.net"
        private const val USER_AGENT = "DavooPlayer/1.0 (https://www.lyricsify.com)"

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build()
        }

        fun buildDefaultSearchQuery(title: String, artist: String): String {
            val cleanTitle = cleanMetadataTerm(title)
            val cleanArtist = cleanArtistTerm(artist)
            return listOf(cleanArtist, cleanTitle)
                .filter { it.isNotEmpty() }
                .joinToString(" ")
                .ifEmpty { title.trim() }
        }

        fun buildLyricsifySearchUrl(query: String): String {
            return "https://www.lyricsify.com/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query.trim())
                .build()
                .toString()
        }

        private fun cleanMetadataTerm(raw: String): String {
            return raw
                .replace(Regex("\\s*[\\[(](?:official\\s*(?:audio|video|music\\s*video|lyric\\s*video)|hd|hq|flac|mp3|320kbps|remaster(?:ed)?|explicit|lyrics?)[\\])]\\s*", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        private fun cleanArtistTerm(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.equals("Artista desconocido", ignoreCase = true) ||
                trimmed.equals("Unknown Artist", ignoreCase = true) ||
                trimmed.equals("<unknown>", ignoreCase = true)
            ) {
                return ""
            }
            return trimmed
        }

        private fun cleanAlbumTerm(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.equals("Álbum desconocido", ignoreCase = true) ||
                trimmed.equals("Unknown Album", ignoreCase = true) ||
                trimmed.equals("<unknown>", ignoreCase = true)
            ) {
                return ""
            }
            return trimmed
        }
    }
}
