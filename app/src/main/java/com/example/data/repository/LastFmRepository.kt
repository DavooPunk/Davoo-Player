package com.example.data.repository

import android.content.ComponentCallbacks2
import android.net.Uri
import android.util.LruCache
import com.example.BuildConfig
import com.example.data.local.PlaybackPreferencesStore
import com.example.data.remote.LASTFM_API_KEY
import com.example.data.remote.LASTFM_SHARED_SECRET
import com.example.data.remote.MusicApiService
import com.example.model.AudioTrack
import com.example.model.DeezerTrackEnrichment
import com.example.model.LastFmPeriod
import com.example.model.LastFmSession
import com.example.model.LastFmTrackItem
import com.example.model.LastFmUserInfo
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

object LastFmScrobblePolicy {
    /**
     * Evalúa si la reproducción actual superó el umbral de scrobble:
     * - En previews de Deezer (`deezer_preview:...`): 15 segundos (o 50% si dura menos).
     * - En pistas locales completas: 50% de la duración de la pista.
     */
    fun shouldScrobble(
        trackId: String,
        positionMs: Long,
        durationMs: Long,
        alreadyScrobbled: Boolean
    ): Boolean {
        if (alreadyScrobbled || durationMs <= 0L || positionMs <= 0L) return false
        val isPreview = trackId.startsWith(LastFmTrackItem.PREVIEW_TRACK_ID_PREFIX)
        val thresholdMs = if (isPreview) {
            minOf(15_000L, (durationMs / 2L).coerceAtLeast(1_000L))
        } else {
            (durationMs / 2L).coerceAtLeast(1_000L)
        }
        return positionMs >= thresholdMs
    }
}

object LastFmTrackMatcher {
    fun findMatchingLocalTrack(
        item: LastFmTrackItem,
        localTracks: List<AudioTrack>
    ): AudioTrack? {
        if (localTracks.isEmpty()) return null
        val targetTitleRaw = item.title.trim().lowercase()
        val targetTitleClean = MusicApiService.cleanTrackTitleForSearch(item.title).lowercase()
        val targetArtistRaw = item.artist.trim().lowercase()
        val targetArtistPrimary = MusicApiService.extractPrimaryArtist(item.artist).lowercase()
        if (targetTitleRaw.isEmpty() && targetTitleClean.isEmpty()) return null

        return localTracks.firstOrNull { local ->
            val localTitleRaw = local.title.trim().lowercase()
            val localTitleClean = MusicApiService.cleanTrackTitleForSearch(local.title).lowercase()
            val titleMatches = (targetTitleRaw.isNotEmpty() && localTitleRaw == targetTitleRaw) ||
                (targetTitleClean.isNotEmpty() && localTitleClean == targetTitleClean)
            if (!titleMatches) return@firstOrNull false

            val localArtistRaw = local.artist.trim().lowercase()
            val localArtistPrimary = MusicApiService.extractPrimaryArtist(local.artist).lowercase()
            (targetArtistRaw.isNotEmpty() && localArtistRaw == targetArtistRaw) ||
                (targetArtistPrimary.isNotEmpty() && localArtistPrimary == targetArtistPrimary) ||
                (targetArtistPrimary.isNotEmpty() && localArtistRaw.contains(targetArtistPrimary)) ||
                (localArtistPrimary.isNotEmpty() && targetArtistRaw.contains(localArtistPrimary))
        }
    }
}

class LastFmRepository(
    private val preferencesStore: PlaybackPreferencesStore,
    private val apiService: MusicApiService = MusicApiService()
) {
    private data class DeezerCacheEntry(val enrichment: DeezerTrackEnrichment?)

    private val deezerLock = Any()
    private val deezerCache = LruCache<String, DeezerCacheEntry>(MAX_DEEZER_CACHE_ENTRIES)
    private val deezerConcurrencyLimiter = Semaphore(permits = 3)

    init {
        activeRepositories.add(WeakReference(this))
    }

    fun getEffectiveApiKey(): String {
        val overrideKey = preferencesStore.loadLastFmApiKeyOverride()
        if (overrideKey.isNotBlank()) return overrideKey
        val buildKey = BuildConfig.LASTFM_API_KEY.trim()
        if (buildKey.isNotBlank() && buildKey != PLACEHOLDER_API_KEY) return buildKey
        return LASTFM_API_KEY.trim()
    }

    fun getEffectiveSharedSecret(): String {
        val overrideSecret = preferencesStore.loadLastFmSharedSecretOverride()
        if (overrideSecret.isNotBlank()) return overrideSecret
        val buildSecret = BuildConfig.LASTFM_SHARED_SECRET.trim()
        if (buildSecret.isNotBlank() && buildSecret != PLACEHOLDER_SHARED_SECRET) return buildSecret
        return LASTFM_SHARED_SECRET.trim()
    }

    fun hasValidCredentials(): Boolean {
        return getEffectiveApiKey().isNotBlank() && getEffectiveSharedSecret().isNotBlank()
    }

    fun saveCredentials(apiKey: String, sharedSecret: String) {
        preferencesStore.saveLastFmCredentialsOverride(apiKey, sharedSecret)
    }

    fun loadSession(): LastFmSession? = preferencesStore.loadLastFmSession()

    fun hasActiveSession(): Boolean = hasValidCredentials() && loadSession() != null

    fun buildAuthUrl(): String? {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank()) return null
        return "https://www.last.fm/api/auth/?api_key=${Uri.encode(apiKey)}&cb=${Uri.encode(CALLBACK_URI)}"
    }

    suspend fun completeOAuthCallback(token: String): Result<LastFmSession> {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) {
            return Result.failure(IllegalArgumentException("Token de Last.fm vacío"))
        }
        val apiKey = getEffectiveApiKey()
        val secret = getEffectiveSharedSecret()
        if (apiKey.isBlank() || secret.isBlank()) {
            return Result.failure(IllegalStateException("Configura API Key y Shared Secret de Last.fm"))
        }

        val sessionResult = apiService.getSession(apiKey, secret, cleanToken)
        return sessionResult.map { baseSession ->
            val info = apiService.getUserInfo(apiKey, baseSession.username).getOrNull()
            val fullSession = baseSession.copy(avatarUrl = info?.avatarUrl)
            preferencesStore.saveLastFmSession(fullSession)
            fullSession
        }
    }

    fun logout() {
        preferencesStore.clearLastFmSession()
    }

    suspend fun fetchUserInfo(): Result<LastFmUserInfo> {
        val apiKey = getEffectiveApiKey()
        val session = loadSession()
            ?: return Result.failure(IllegalStateException("No hay sesión activa de Last.fm"))
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Falta configurar API Key de Last.fm"))
        }
        return apiService.getUserInfo(apiKey, session.username).onSuccess { info ->
            if (!info.avatarUrl.isNullOrBlank()) {
                preferencesStore.updateLastFmAvatar(info.avatarUrl)
            }
        }
    }

    suspend fun fetchTopTracks(period: LastFmPeriod, limit: Int = 30): Result<List<LastFmTrackItem>> {
        val apiKey = getEffectiveApiKey()
        val session = loadSession()
            ?: return Result.failure(IllegalStateException("Conecta tu cuenta de Last.fm"))
        return apiService.getTopTracks(apiKey, session.username, period, limit).map { items ->
            applyCachedDeezerToList(items)
        }
    }

    suspend fun fetchLovedTracks(limit: Int = 30): Result<List<LastFmTrackItem>> {
        val apiKey = getEffectiveApiKey()
        val session = loadSession()
            ?: return Result.failure(IllegalStateException("Conecta tu cuenta de Last.fm"))
        return apiService.getLovedTracks(apiKey, session.username, limit).map { items ->
            applyCachedDeezerToList(items)
        }
    }

    suspend fun fetchRecentTracks(limit: Int = 30): Result<List<LastFmTrackItem>> {
        val apiKey = getEffectiveApiKey()
        val session = loadSession()
            ?: return Result.failure(IllegalStateException("Conecta tu cuenta de Last.fm"))
        return apiService.getRecentTracks(apiKey, session.username, limit).map { items ->
            applyCachedDeezerToList(items)
        }
    }

    /**
     * Consulta la API pública de Deezer (`artist:"..." track:"..."&limit=1`) usando caché LRU en memoria
     * y semáforo de concurrencia para ahorrar datos, batería y CPU durante el scroll.
     */
    suspend fun buscarEnDeezer(artista: String, cancion: String): DeezerTrackEnrichment? {
        val key = LastFmTrackItem.buildLookupKey(artista, cancion)
        if (key == "|") return null

        synchronized(deezerLock) {
            deezerCache.get(key)?.let { return it.enrichment }
        }

        val fetched = deezerConcurrencyLimiter.withPermit {
            // Doble verificación tras adquirir permiso para evitar peticiones duplicadas en ráfaga
            synchronized(deezerLock) {
                deezerCache.get(key)?.let { return@withPermit it.enrichment }
            }
            apiService.searchDeezerTrack(artista, cancion)
        }

        synchronized(deezerLock) {
            deezerCache.put(key, DeezerCacheEntry(fetched))
        }
        return fetched
    }

    fun getCachedDeezer(artista: String, cancion: String): DeezerTrackEnrichment? {
        val key = LastFmTrackItem.buildLookupKey(artista, cancion)
        return synchronized(deezerLock) {
            deezerCache.get(key)?.enrichment
        }
    }

    fun clearEmptyDeezerCacheEntries() {
        synchronized(deezerLock) {
            val emptyKeys = deezerCache.snapshot()
                .filterValues { it.enrichment == null }
                .keys
            emptyKeys.forEach { deezerCache.remove(it) }
        }
    }

    private fun applyCachedDeezerToList(items: List<LastFmTrackItem>): List<LastFmTrackItem> {
        return items.map { item ->
            val key = item.lookupKey
            val cached = synchronized(deezerLock) { deezerCache.get(key) }
            if (cached != null) {
                item.copy(deezerEnrichment = cached.enrichment, deezerLookupDone = true)
            } else {
                item
            }
        }
    }

    suspend fun updateNowPlaying(
        artist: String,
        track: String,
        album: String?,
        durationMs: Long
    ): Result<Unit> {
        val apiKey = getEffectiveApiKey()
        val secret = getEffectiveSharedSecret()
        val session = loadSession() ?: return Result.failure(IllegalStateException("Sin sesión Last.fm"))
        if (apiKey.isBlank() || secret.isBlank()) {
            return Result.failure(IllegalStateException("Faltan credenciales Last.fm"))
        }
        val cleanAlbum = album?.takeIf {
            it.isNotBlank() && !it.equals("Álbum desconocido", ignoreCase = true)
        }
        val durationSec = (durationMs / 1000L).toInt().takeIf { it > 0 }
        return apiService.updateNowPlaying(
            apiKey = apiKey,
            sharedSecret = secret,
            sessionKey = session.sessionKey,
            artist = artist,
            track = track,
            album = cleanAlbum,
            durationSec = durationSec
        )
    }

    suspend fun scrobbleTrack(
        artist: String,
        track: String,
        timestampSec: Long,
        album: String?,
        durationMs: Long
    ): Result<Unit> {
        val apiKey = getEffectiveApiKey()
        val secret = getEffectiveSharedSecret()
        val session = loadSession() ?: return Result.failure(IllegalStateException("Sin sesión Last.fm"))
        if (apiKey.isBlank() || secret.isBlank()) {
            return Result.failure(IllegalStateException("Faltan credenciales Last.fm"))
        }
        val cleanAlbum = album?.takeIf {
            it.isNotBlank() && !it.equals("Álbum desconocido", ignoreCase = true)
        }
        val durationSec = (durationMs / 1000L).toInt().takeIf { it > 0 }
        return apiService.scrobbleTrack(
            apiKey = apiKey,
            sharedSecret = secret,
            sessionKey = session.sessionKey,
            artist = artist,
            track = track,
            timestampSec = timestampSec,
            album = cleanAlbum,
            durationSec = durationSec
        )
    }

    suspend fun setTrackLove(
        artist: String,
        track: String,
        love: Boolean
    ): Result<Unit> {
        val apiKey = getEffectiveApiKey()
        val secret = getEffectiveSharedSecret()
        val session = loadSession() ?: return Result.failure(IllegalStateException("Sin sesión Last.fm"))
        if (apiKey.isBlank() || secret.isBlank()) {
            return Result.failure(IllegalStateException("Faltan credenciales Last.fm"))
        }
        return apiService.loveTrack(
            apiKey = apiKey,
            sharedSecret = secret,
            sessionKey = session.sessionKey,
            artist = artist,
            track = track,
            love = love
        )
    }

    fun trimMemory(level: Int) {
        synchronized(deezerLock) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                deezerCache.evictAll()
            } else if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                deezerCache.trimToSize(MAX_DEEZER_CACHE_ENTRIES / 3)
            }
        }
    }

    companion object {
        const val CALLBACK_URI = "davooplayer://lastfm-callback"
        private const val PLACEHOLDER_API_KEY = "LASTFM_API_KEY_DEFAULT_VALUE"
        private const val PLACEHOLDER_SHARED_SECRET = "LASTFM_SHARED_SECRET_DEFAULT_VALUE"
        private const val MAX_DEEZER_CACHE_ENTRIES = 200

        private val activeRepositories = CopyOnWriteArrayList<WeakReference<LastFmRepository>>()

        fun trimActiveCaches(level: Int) {
            val iterator = activeRepositories.iterator()
            while (iterator.hasNext()) {
                val ref = iterator.next()
                val repo = ref.get()
                if (repo == null) {
                    activeRepositories.remove(ref)
                } else {
                    repo.trimMemory(level)
                }
            }
        }
    }
}
