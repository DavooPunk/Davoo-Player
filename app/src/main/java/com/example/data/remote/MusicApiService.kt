package com.example.data.remote

import com.example.BuildConfig
import com.example.model.DeezerTagSuggestion
import com.example.model.DeezerTrackEnrichment
import com.example.model.LastFmPeriod
import com.example.model.LastFmSession
import com.example.model.LastFmTrackItem
import com.example.model.LastFmUserInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

// Constantes configurables para credenciales de Last.fm (también configurables vía Secrets o diálogo en Opciones)
const val LASTFM_API_KEY = ""
const val LASTFM_SHARED_SECRET = ""

/**
 * Genera la firma MD5 (`api_sig`) requerida por Last.fm Web Services 2.0:
 * ordena alfabéticamente todos los parámetros excepto `format`, concatena `clave+valor`
 * junto con `sharedSecret` al final y devuelve el hash MD5 hexadecimal en minúsculas.
 */
fun generateLastFmApiSignature(
    params: Map<String, String>,
    sharedSecret: String
): String {
    val payload = buildString {
        params.entries
            .filter { it.key != "format" && it.key != "api_sig" }
            .sortedBy { it.key }
            .forEach { (key, value) ->
                append(key)
                append(value)
            }
        append(sharedSecret)
    }
    val digest = MessageDigest.getInstance("MD5").digest(payload.toByteArray(Charsets.UTF_8))
    return buildString(digest.size * 2) {
        for (byte in digest) {
            val unsigned = byte.toInt() and 0xFF
            append(Character.forDigit(unsigned ushr 4, 16))
            append(Character.forDigit(unsigned and 0x0F, 16))
        }
    }
}

class MusicApiService(
    private val client: OkHttpClient = defaultClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val lastFmBaseUrl: String = LASTFM_API_BASE_URL,
    private val deezerBaseUrl: String = DEEZER_API_BASE_URL
) {

    suspend fun getSession(
        apiKey: String,
        sharedSecret: String,
        token: String
    ): Result<LastFmSession> = withContext(ioDispatcher) {
        runCatching {
            val signedParams = signedParamsMap(
                base = mapOf(
                    "method" to "auth.getSession",
                    "api_key" to apiKey,
                    "token" to token
                ),
                sharedSecret = sharedSecret
            )
            val body = executeLastFmGet(signedParams)
            val root = parseAndValidateLastFmJson(body)
            val sessionObj = root["session"]?.jsonObject
                ?: throw IllegalStateException("Last.fm no devolvió datos de sesión válidos")
            val key = sessionObj["key"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val name = sessionObj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (key.isEmpty() || name.isEmpty()) {
                throw IllegalStateException("Sesión de Last.fm incompleta")
            }
            LastFmSession(sessionKey = key, username = name)
        }
    }

    suspend fun getUserInfo(
        apiKey: String,
        username: String
    ): Result<LastFmUserInfo> = withContext(ioDispatcher) {
        runCatching {
            val body = executeLastFmGet(
                mapOf(
                    "method" to "user.getInfo",
                    "user" to username,
                    "api_key" to apiKey
                )
            )
            val root = parseAndValidateLastFmJson(body)
            val userObj = root["user"]?.jsonObject
                ?: throw IllegalStateException("No se encontró el perfil de usuario")
            LastFmUserInfo(
                username = userObj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { username },
                realName = userObj["realname"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                playcount = userObj["playcount"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                avatarUrl = extractLastFmImage(userObj["image"])
            )
        }
    }

    suspend fun getTopTracks(
        apiKey: String,
        username: String,
        period: LastFmPeriod,
        limit: Int = 30
    ): Result<List<LastFmTrackItem>> = withContext(ioDispatcher) {
        runCatching {
            val body = executeLastFmGet(
                mapOf(
                    "method" to "user.getTopTracks",
                    "user" to username,
                    "period" to period.apiValue,
                    "limit" to limit.toString(),
                    "api_key" to apiKey
                )
            )
            val root = parseAndValidateLastFmJson(body)
            val container = root["toptracks"]?.jsonObject ?: return@runCatching emptyList()
            extractTrackObjects(container["track"]).mapIndexed { idx, obj ->
                val title = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val artist = extractArtistName(obj["artist"])
                val rank = obj["@attr"]?.jsonObject?.get("rank")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: (idx + 1)
                val playcount = obj["playcount"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                LastFmTrackItem(
                    id = "top_${period.apiValue}_${rank}_${artist}_${title}",
                    title = title,
                    artist = artist,
                    playcount = playcount,
                    rank = rank,
                    fallbackArtworkUrl = extractLastFmImage(obj["image"])
                )
            }.filter { it.title.isNotBlank() && it.artist.isNotBlank() }
        }
    }

    suspend fun getLovedTracks(
        apiKey: String,
        username: String,
        limit: Int = 30
    ): Result<List<LastFmTrackItem>> = withContext(ioDispatcher) {
        runCatching {
            val body = executeLastFmGet(
                mapOf(
                    "method" to "user.getLovedTracks",
                    "user" to username,
                    "limit" to limit.toString(),
                    "api_key" to apiKey
                )
            )
            val root = parseAndValidateLastFmJson(body)
            val container = root["lovedtracks"]?.jsonObject ?: return@runCatching emptyList()
            extractTrackObjects(container["track"]).mapIndexed { idx, obj ->
                val title = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val artist = extractArtistName(obj["artist"])
                val dateLabel = obj["date"]?.jsonObject?.get("#text")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                LastFmTrackItem(
                    id = "loved_${idx}_${artist}_${title}",
                    title = title,
                    artist = artist,
                    isLoved = true,
                    dateLabel = dateLabel,
                    fallbackArtworkUrl = extractLastFmImage(obj["image"])
                )
            }.filter { it.title.isNotBlank() && it.artist.isNotBlank() }
        }
    }

    suspend fun getRecentTracks(
        apiKey: String,
        username: String,
        limit: Int = 30
    ): Result<List<LastFmTrackItem>> = withContext(ioDispatcher) {
        runCatching {
            val body = executeLastFmGet(
                mapOf(
                    "method" to "user.getRecentTracks",
                    "user" to username,
                    "limit" to limit.toString(),
                    "api_key" to apiKey
                )
            )
            val root = parseAndValidateLastFmJson(body)
            val container = root["recenttracks"]?.jsonObject ?: return@runCatching emptyList()
            extractTrackObjects(container["track"]).mapIndexed { idx, obj ->
                val title = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val artist = extractArtistName(obj["artist"])
                val album = extractTextField(obj["album"])
                val nowPlaying = obj["@attr"]?.jsonObject?.get("nowplaying")
                    ?.jsonPrimitive?.contentOrNull?.equals("true", ignoreCase = true) == true
                val dateLabel = if (nowPlaying) {
                    "Reproduciendo ahora"
                } else {
                    obj["date"]?.jsonObject?.get("#text")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                }
                LastFmTrackItem(
                    id = "recent_${idx}_${if (nowPlaying) "np" else dateLabel}_${artist}_${title}",
                    title = title,
                    artist = artist,
                    album = album,
                    isNowPlaying = nowPlaying,
                    dateLabel = dateLabel,
                    fallbackArtworkUrl = extractLastFmImage(obj["image"])
                )
            }.filter { it.title.isNotBlank() && it.artist.isNotBlank() }
        }
    }

    suspend fun updateNowPlaying(
        apiKey: String,
        sharedSecret: String,
        sessionKey: String,
        artist: String,
        track: String,
        album: String? = null,
        durationSec: Int? = null
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val params = buildMap {
                put("method", "track.updateNowPlaying")
                put("api_key", apiKey)
                put("sk", sessionKey)
                put("artist", artist)
                put("track", track)
                if (!album.isNullOrBlank()) put("album", album)
                if (durationSec != null && durationSec > 0) put("duration", durationSec.toString())
            }
            val signed = signedParamsMap(params, sharedSecret)
            val body = executeLastFmPost(signed)
            parseAndValidateLastFmJson(body)
            Unit
        }
    }

    suspend fun scrobbleTrack(
        apiKey: String,
        sharedSecret: String,
        sessionKey: String,
        artist: String,
        track: String,
        timestampSec: Long,
        album: String? = null,
        durationSec: Int? = null
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val params = buildMap {
                put("method", "track.scrobble")
                put("api_key", apiKey)
                put("sk", sessionKey)
                put("artist", artist)
                put("track", track)
                put("timestamp", timestampSec.toString())
                if (!album.isNullOrBlank()) put("album", album)
                if (durationSec != null && durationSec > 0) put("duration", durationSec.toString())
            }
            val signed = signedParamsMap(params, sharedSecret)
            val body = executeLastFmPost(signed)
            parseAndValidateLastFmJson(body)
            Unit
        }
    }

    suspend fun loveTrack(
        apiKey: String,
        sharedSecret: String,
        sessionKey: String,
        artist: String,
        track: String,
        love: Boolean
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val params = mapOf(
                "method" to if (love) "track.love" else "track.unlove",
                "api_key" to apiKey,
                "sk" to sessionKey,
                "artist" to artist,
                "track" to track
            )
            val signed = signedParamsMap(params, sharedSecret)
            val body = executeLastFmPost(signed)
            parseAndValidateLastFmJson(body)
            Unit
        }
    }

    suspend fun searchDeezerTrack(
        artist: String,
        track: String
    ): DeezerTrackEnrichment? = withContext(ioDispatcher) {
        val primaryArtist = extractPrimaryArtist(artist)
        val cleanTrack = cleanTrackTitleForSearch(track)
        if (primaryArtist.isEmpty() || cleanTrack.isEmpty()) return@withContext null

        // Intento 1: consulta etiquetada artist:"..." track:"..." con artista principal y título limpio
        val strictQuery = "artist:\"$primaryArtist\" track:\"$cleanTrack\""
        executeDeezerSearchQuery(strictQuery)?.let { return@withContext it }

        // Intento 2 (Fallback): búsqueda abierta sin etiquetas si Deezer devolvió data vacío
        val openQuery = "$primaryArtist $cleanTrack".trim()
        executeDeezerSearchQuery(openQuery)
    }

    /**
     * Búsqueda pública en Deezer para auto-etiquetado de archivos locales (sin ARL ni audio).
     * Devuelve sugerencias ordenadas por coincidencia de título/artista y duración.
     */
    suspend fun searchDeezerTagSuggestions(
        title: String,
        artist: String,
        album: String = "",
        durationSec: Int? = null,
        customQuery: String? = null,
        limit: Int = 5
    ): Result<List<DeezerTagSuggestion>> = withContext(ioDispatcher) {
        runCatching {
            val safeLimit = limit.coerceIn(1, 10)
            val explicitQuery = customQuery?.trim().orEmpty()
            val queries = if (explicitQuery.isNotEmpty()) {
                listOf(explicitQuery)
            } else {
                val primaryArtist = cleanUnknownTerm(extractPrimaryArtist(artist))
                val cleanTrack = cleanTrackTitleForSearch(title)
                val cleanAlbum = cleanUnknownTerm(album)
                buildList {
                    if (primaryArtist.isNotEmpty() && cleanTrack.isNotEmpty()) {
                        add("artist:\"$primaryArtist\" track:\"$cleanTrack\"")
                        add("$primaryArtist $cleanTrack")
                    } else if (cleanTrack.isNotEmpty() && cleanAlbum.isNotEmpty()) {
                        add("$cleanTrack $cleanAlbum")
                    }
                    if (cleanTrack.isNotEmpty()) {
                        add(cleanTrack)
                    }
                }.distinct()
            }
            if (queries.isEmpty()) return@runCatching emptyList()

            for (query in queries) {
                val rawBody = executeDeezerSearchRaw(query, safeLimit) ?: continue
                val parsed = parseDeezerTagSearchResponse(rawBody, durationSec)
                if (parsed.isNotEmpty()) {
                    return@runCatching parsed
                }
            }
            emptyList()
        }
    }

    /**
     * Consulta los endpoints públicos `/track/{id}` y `/album/{albumId}` de Deezer
     * para obtener año de lanzamiento, número de pista, artista del álbum, género y carátula HD.
     */
    suspend fun fetchDeezerTrackTagDetails(
        suggestion: DeezerTagSuggestion
    ): DeezerTagSuggestion = withContext(ioDispatcher) {
        var enriched = suggestion
        val base = deezerBaseUrl.trimEnd('/')

        val trackBody = executePublicDeezerGet("$base/track/${suggestion.deezerTrackId}")
        if (!trackBody.isNullOrBlank()) {
            enriched = parseDeezerTrackDetailsResponse(trackBody, enriched)
        }

        val albumId = enriched.albumId
        val needsAlbumDetails = albumId > 0L && (
            enriched.year <= 0 ||
                enriched.albumArtist.isBlank() ||
                enriched.genre.isBlank() ||
                enriched.coverXlUrl.isNullOrBlank()
            )
        if (needsAlbumDetails) {
            val albumBody = executePublicDeezerGet("$base/album/$albumId")
            if (!albumBody.isNullOrBlank()) {
                enriched = parseDeezerAlbumDetailsResponse(albumBody, enriched)
            }
        }
        enriched
    }

    private fun executeDeezerSearchQuery(rawQuery: String): DeezerTrackEnrichment? {
        val body = executeDeezerSearchRaw(rawQuery, limit = 1) ?: return null
        return parseDeezerSearchResponse(body)
    }

    private fun executeDeezerSearchRaw(rawQuery: String, limit: Int): String? {
        return runCatching {
            val encodedQuery = URLEncoder.encode(rawQuery, "UTF-8")
            val base = deezerBaseUrl.trimEnd('/')
            val url = "$base/search?q=$encodedQuery&limit=$limit"
            executePublicDeezerGet(url)
        }.getOrNull()
    }

    private fun executePublicDeezerGet(url: String): String? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "DavooPlayer/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                response.body?.string()?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    private fun signedParamsMap(
        base: Map<String, String>,
        sharedSecret: String
    ): Map<String, String> {
        val sig = generateLastFmApiSignature(base, sharedSecret)
        return base + ("api_sig" to sig) + ("format" to "json")
    }

    private fun executeLastFmGet(params: Map<String, String>): String {
        val urlBuilder = lastFmBaseUrl.toHttpUrl().newBuilder()
        params.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        if (!params.containsKey("format")) {
            urlBuilder.addQueryParameter("format", "json")
        }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("Accept", "application/json")
            .header("User-Agent", "DavooPlayer/${BuildConfig.VERSION_NAME}")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful && raw.isBlank()) {
                throw IllegalStateException("Last.fm respondió HTTP ${response.code}")
            }
            return raw
        }
    }

    private fun executeLastFmPost(params: Map<String, String>): String {
        val formBody = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
            if (!params.containsKey("format")) add("format", "json")
        }.build()

        val request = Request.Builder()
            .url(lastFmBaseUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "DavooPlayer/${BuildConfig.VERSION_NAME}")
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful && raw.isBlank()) {
                throw IllegalStateException("Last.fm respondió HTTP ${response.code}")
            }
            return raw
        }
    }

    private fun parseAndValidateLastFmJson(rawJson: String): JsonObject {
        val obj = json.parseToJsonElement(rawJson).jsonObject
        val errorCode = obj["error"]?.jsonPrimitive?.intOrNull
        if (errorCode != null) {
            val apiMsg = obj["message"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val friendly = when (errorCode) {
                4, 9 -> "Sesión o firma de Last.fm inválida. Vuelve a iniciar sesión."
                10 -> "API Key de Last.fm inválida. Revisa tus credenciales."
                14 -> "Token de Last.fm expirado o no autorizado."
                26 -> "Clave de API suspendida por Last.fm."
                else -> apiMsg.ifBlank { "Error de Last.fm (código $errorCode)" }
            }
            throw IllegalStateException(friendly)
        }
        return obj
    }

    private fun extractTrackObjects(element: JsonElement?): List<JsonObject> {
        return when (element) {
            is JsonArray -> element.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(element)
            else -> emptyList()
        }
    }

    private fun extractArtistName(element: JsonElement?): String {
        return when (element) {
            is JsonObject -> {
                element["name"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: element["#text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            }
            else -> element?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        }
    }

    private fun extractTextField(element: JsonElement?): String {
        return when (element) {
            is JsonObject -> {
                element["#text"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: element["title"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: element["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            }
            else -> element?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        }
    }

    private fun extractLastFmImage(element: JsonElement?): String? {
        val arr = element as? JsonArray ?: return null
        val bySize = arr.mapNotNull { it as? JsonObject }
            .associate { obj ->
                val size = obj["size"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
                val url = obj["#text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                size to url
            }
        val candidate = bySize["extralarge"]?.takeIf { it.isNotBlank() }
            ?: bySize["large"]?.takeIf { it.isNotBlank() }
            ?: bySize["mega"]?.takeIf { it.isNotBlank() }
            ?: bySize["medium"]?.takeIf { it.isNotBlank() }
            ?: bySize.values.firstOrNull { it.isNotBlank() }
            ?: return null
        if (candidate.contains(LASTFM_GENERIC_STAR_HASH)) return null
        return candidate.ensureHttps()
    }

    companion object {
        const val LASTFM_API_BASE_URL = "https://ws.audioscrobbler.com/2.0/"
        const val DEEZER_API_BASE_URL = "https://api.deezer.com"
        private const val LASTFM_GENERIC_STAR_HASH = "2a96cbd8b46e442fc41c2b86b821562f"
        private val ARTIST_SPLIT_REGEX = Regex(
            pattern = """\s*(?:,|&|;|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b)\s*""",
            option = RegexOption.IGNORE_CASE
        )
        private val PAREN_BRACKET_REGEX = Regex("""\s*[\(\[][^\)\]]*[\)\]]""")

        private val json = Json { ignoreUnknownKeys = true }

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build()
        }

        /**
         * Extrae el artista principal antes de la primera coma `,`, `&`, `feat.` o `ft.`
         * para mejorar drásticamente la tasa de acierto en colaboraciones de Deezer.
         */
        fun extractPrimaryArtist(rawArtist: String): String {
            val sanitized = rawArtist.replace("\"", "").trim()
            if (sanitized.isEmpty()) return ""
            val primary = ARTIST_SPLIT_REGEX.split(sanitized, limit = 2)
                .firstOrNull()
                ?.trim()
                .orEmpty()
            return primary.ifBlank { sanitized }
        }

        /**
         * Elimina paréntesis/corchetes extra del título (por ejemplo `(feat. ...)` o `(Remastered)`)
         * conservando el título original si la limpieza lo dejara vacío.
         */
        fun cleanTrackTitleForSearch(rawTitle: String): String {
            val sanitized = rawTitle.replace("\"", "").trim()
            if (sanitized.isEmpty()) return ""
            val stripped = PAREN_BRACKET_REGEX.replace(sanitized, " ")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
            return stripped.ifBlank { sanitized }
        }

        fun parseDeezerSearchResponse(rawJson: String): DeezerTrackEnrichment? {
            val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull() ?: return null
            val first = root["data"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
            val id = first["id"]?.jsonPrimitive?.longOrNull ?: return null
            val title = first["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val artist = first["artist"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val albumObj = first["album"]?.jsonObject
            val albumTitle = albumObj?.get("title")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val coverXl = albumObj?.get("cover_xl")?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                ?: albumObj?.get("cover_big")?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                ?: albumObj?.get("cover_medium")?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
            val preview = first["preview"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
            val duration = first["duration"]?.jsonPrimitive?.intOrNull ?: 30
            return DeezerTrackEnrichment(
                deezerTrackId = id,
                title = title,
                artist = artist,
                albumTitle = albumTitle,
                coverXlUrl = coverXl?.ensureHttps(),
                previewUrl = preview?.ensureHttps(),
                durationSec = duration
            )
        }

        fun parseDeezerTagSearchResponse(
            rawJson: String,
            targetDurationSec: Int? = null
        ): List<DeezerTagSuggestion> {
            val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull()
                ?: return emptyList()
            val dataArr = root["data"]?.jsonArray ?: return emptyList()
            val list = dataArr.mapNotNull { elem ->
                val obj = elem as? JsonObject ?: return@mapNotNull null
                val id = obj["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (title.isEmpty()) return@mapNotNull null
                val artist = obj["artist"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val albumObj = obj["album"]?.jsonObject
                val albumId = albumObj?.get("id")?.jsonPrimitive?.longOrNull ?: 0L
                val albumTitle = albumObj?.get("title")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val coverXl = extractBestDeezerCover(albumObj)
                val duration = obj["duration"]?.jsonPrimitive?.intOrNull ?: 0
                DeezerTagSuggestion(
                    deezerTrackId = id,
                    title = title,
                    artist = artist,
                    album = albumTitle,
                    albumId = albumId,
                    coverXlUrl = coverXl,
                    durationSec = duration
                )
            }
            return if (targetDurationSec != null && targetDurationSec > 0) {
                list.sortedBy { candidate ->
                    if (candidate.durationSec > 0) kotlin.math.abs(candidate.durationSec - targetDurationSec)
                    else Int.MAX_VALUE
                }
            } else {
                list
            }
        }

        fun parseDeezerTrackDetailsResponse(
            rawJson: String,
            fallback: DeezerTagSuggestion
        ): DeezerTagSuggestion {
            val obj = runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull()
                ?: return fallback
            if (obj["error"] != null) return fallback
            val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                ?: fallback.title
            val artist = obj["artist"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull?.trim()
                ?.takeIf { it.isNotBlank() } ?: fallback.artist
            val albumObj = obj["album"]?.jsonObject
            val albumId = albumObj?.get("id")?.jsonPrimitive?.longOrNull ?: fallback.albumId
            val albumTitle = albumObj?.get("title")?.jsonPrimitive?.contentOrNull?.trim()
                ?.takeIf { it.isNotBlank() } ?: fallback.album
            val coverXl = extractBestDeezerCover(albumObj) ?: fallback.coverXlUrl
            val trackPos = obj["track_position"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }
                ?: fallback.trackNumber
            val releaseYear = parseReleaseYear(
                obj["release_date"]?.jsonPrimitive?.contentOrNull
                    ?: albumObj?.get("release_date")?.jsonPrimitive?.contentOrNull
            ).takeIf { it > 0 } ?: fallback.year
            val duration = obj["duration"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }
                ?: fallback.durationSec
            return fallback.copy(
                title = title,
                artist = artist,
                album = albumTitle,
                albumId = albumId,
                year = releaseYear,
                trackNumber = trackPos,
                coverXlUrl = coverXl,
                durationSec = duration
            )
        }

        fun parseDeezerAlbumDetailsResponse(
            rawJson: String,
            current: DeezerTagSuggestion
        ): DeezerTagSuggestion {
            val obj = runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull()
                ?: return current
            if (obj["error"] != null) return current
            val albumArtist = obj["artist"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull?.trim()
                ?.takeIf { it.isNotBlank() } ?: current.albumArtist
            val genre = obj["genres"]?.jsonObject?.get("data")?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull?.trim()
                ?.takeIf { it.isNotBlank() } ?: current.genre
            val releaseYear = current.year.takeIf { it > 0 }
                ?: parseReleaseYear(obj["release_date"]?.jsonPrimitive?.contentOrNull)
            val coverXl = current.coverXlUrl ?: extractBestDeezerCover(obj)
            return current.copy(
                albumArtist = albumArtist,
                genre = genre,
                year = releaseYear,
                coverXlUrl = coverXl
            )
        }

        private fun extractBestDeezerCover(albumObj: JsonObject?): String? {
            if (albumObj == null) return null
            val raw = albumObj["cover_xl"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                ?: albumObj["cover_big"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                ?: albumObj["cover_medium"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                ?: return null
            return raw.ensureHttps()
        }

        private fun parseReleaseYear(rawDate: String?): Int {
            val yearToken = rawDate?.trim()?.take(4) ?: return 0
            return yearToken.toIntOrNull()?.takeIf { it in 1900..2100 } ?: 0
        }

        private fun cleanUnknownTerm(raw: String): String {
            val trimmed = raw.trim()
            return if (trimmed.equals("Artista desconocido", ignoreCase = true) ||
                trimmed.equals("Álbum desconocido", ignoreCase = true) ||
                trimmed.equals("Unknown Artist", ignoreCase = true) ||
                trimmed.equals("Unknown Album", ignoreCase = true) ||
                trimmed.equals("<unknown>", ignoreCase = true)
            ) {
                ""
            } else {
                trimmed
            }
        }

        private fun String.ensureHttps(): String {
            return if (startsWith("http://", ignoreCase = true)) {
                "https://" + substring(7)
            } else this
        }
    }
}
