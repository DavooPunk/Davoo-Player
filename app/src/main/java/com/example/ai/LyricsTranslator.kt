package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.example.model.LyricLine
import com.example.model.LyricsData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val TAG = "LyricsTranslator"

/**
 * Selectable lyrics translation engines (user can switch anytime in Settings or Lyrics sheet).
 */
enum class TranslationEngine(
    val displayName: String,
    val shortLabel: String,
    val description: String
) {
    DEEPL(
        displayName = "DeepL",
        shortLabel = "DeepL",
        description = "Alta precisión musical usando tu API de DeepL"
    ),
    GOOGLE(
        displayName = "Google Traductor",
        shortLabel = "Google",
        description = "Rápido y sin clave API, con limpieza de modismos"
    );

    companion object {
        fun fromName(name: String?): TranslationEngine {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEEPL
        }
    }
}

/**
 * Supported target languages for DeepL API v2, Google Translate, and saved offline human translations.
 */
enum class LyricsTranslationLanguage(
    val code: String,
    val deepLCode: String,
    val displayName: String
) {
    SPANISH("es", "ES", "Español"),
    ENGLISH("en", "EN-US", "Inglés"),
    PORTUGUESE("pt", "PT-BR", "Portugués"),
    FRENCH("fr", "FR", "Francés"),
    ITALIAN("it", "IT", "Italiano"),
    GERMAN("de", "DE", "Alemán"),
    JAPANESE("ja", "JA", "Japonés"),
    KOREAN("ko", "KO", "Coreano");

    companion object {
        fun fromCode(code: String?): LyricsTranslationLanguage {
            if (code.isNullOrBlank()) return SPANISH
            return entries.firstOrNull {
                it.code.equals(code, ignoreCase = true) ||
                    it.deepLCode.equals(code, ignoreCase = true)
            } ?: SPANISH
        }
    }
}

/**
 * Display modes once a translation (native bilingual `.lrc`, saved offline, DeepL, or Google) is ready.
 */
enum class LyricsDisplayMode(val label: String) {
    BILINGUAL("Bilingüe"),
    TRANSLATED("Traducida"),
    ORIGINAL("Original")
}

sealed interface LyricsTranslationUiState {
    data object Idle : LyricsTranslationUiState
    data class Loading(
        val language: LyricsTranslationLanguage,
        val engine: TranslationEngine
    ) : LyricsTranslationUiState

    data class Success(
        val trackId: String,
        val language: LyricsTranslationLanguage,
        val translatedLyrics: LyricsData,
        val isSavedOffline: Boolean = false
    ) : LyricsTranslationUiState

    data class Error(
        val language: LyricsTranslationLanguage,
        val engine: TranslationEngine,
        val message: String
    ) : LyricsTranslationUiState
}

fun interface LyricsTranslator {
    suspend fun translate(
        trackTitle: String,
        trackArtist: String,
        lyrics: LyricsData,
        targetLanguage: LyricsTranslationLanguage
    ): LyricsData
}

class DeepLConfigurationException(message: String) : IllegalStateException(message)

/**
 * Direct search URL builders for human-translated song lyrics websites (no scraping, Play-safe).
 */
object HumanLyricsTranslationLinks {
    fun buildLetrasSearchUrl(trackTitle: String, trackArtist: String): String {
        val query = buildQuery(trackTitle, trackArtist)
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "https://www.letras.com/?q=$encoded"
    }

    fun buildLyricsTranslateSearchUrl(trackTitle: String, trackArtist: String): String {
        val query = buildQuery(trackTitle, trackArtist)
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "https://lyricstranslate.com/en/site-search?query=$encoded"
    }

    private fun buildQuery(title: String, artist: String): String {
        val cleanArtist = artist.trim().takeUnless {
            it.equals("Artista desconocido", ignoreCase = true) ||
                it.equals("<unknown>", ignoreCase = true)
        }.orEmpty()
        return listOf(cleanArtist, title.trim()).filter { it.isNotBlank() }.joinToString(" ")
    }
}

/**
 * Pre-processes common English song contractions and lyrical slang into clearer grammar
 * before passing lines to Google Translate, improving natural phrasing without AI/LLMs.
 */
object LyricTextNormalizer {
    private val replacements: List<Pair<Regex, String>> = listOf(
        Regex("\\bwanna\\b", RegexOption.IGNORE_CASE) to "want to",
        Regex("\\bgonna\\b", RegexOption.IGNORE_CASE) to "going to",
        Regex("\\bgotta\\b", RegexOption.IGNORE_CASE) to "got to",
        Regex("\\bain't\\b", RegexOption.IGNORE_CASE) to "is not",
        Regex("(?<![\\p{L}])'cause\\b", RegexOption.IGNORE_CASE) to "because",
        Regex("\\bcuz\\b", RegexOption.IGNORE_CASE) to "because",
        Regex("\\bkinda\\b", RegexOption.IGNORE_CASE) to "kind of",
        Regex("\\bsorta\\b", RegexOption.IGNORE_CASE) to "sort of",
        Regex("\\blemme\\b", RegexOption.IGNORE_CASE) to "let me",
        Regex("\\bgimme\\b", RegexOption.IGNORE_CASE) to "give me",
        Regex("\\boutta\\b", RegexOption.IGNORE_CASE) to "out of",
        Regex("\\btryna\\b", RegexOption.IGNORE_CASE) to "trying to",
        Regex("\\by'all\\b", RegexOption.IGNORE_CASE) to "you all",
        Regex("\\bc'mon\\b", RegexOption.IGNORE_CASE) to "come on",
        Regex("\\bwhatcha\\b", RegexOption.IGNORE_CASE) to "what are you",
        Regex("\\bI'ma\\b", RegexOption.IGNORE_CASE) to "I am going to",
        Regex("\\b(\\p{L}{3,})in'(?![\\p{L}])", RegexOption.IGNORE_CASE) to "$1ing"
    )

    fun normalizeForTranslation(line: String): String {
        var result = line.trim()
        if (result.isEmpty()) return result
        for ((regex, replacement) in replacements) {
            result = regex.replace(result, replacement)
        }
        return result
    }
}

@Serializable
private data class DeepLTranslateRequest(
    val text: List<String>,
    @SerialName("target_lang")
    val targetLang: String,
    @SerialName("split_sentences")
    val splitSentences: String = "nonewlines",
    @SerialName("preserve_formatting")
    val preserveFormatting: Boolean = true,
    val context: String? = null
)

@Serializable
private data class DeepLTranslationItem(
    @SerialName("detected_source_language")
    val detectedSourceLanguage: String? = null,
    val text: String = ""
)

@Serializable
private data class DeepLTranslateResponse(
    val translations: List<DeepLTranslationItem> = emptyList()
)

private val sharedHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()
}

private val sharedJson: Json by lazy {
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
}

/**
 * Official DeepL REST API v2 translator (Kotlin + OkHttp).
 * Automatically uses `api-free.deepl.com` for Free keys (`:fx`) and `api.deepl.com` for Pro keys.
 * Sends song title/artist context and preserves exact 1-to-1 `.lrc` line timestamps.
 */
class DeepLLyricsTranslator(
    private val apiKeyProvider: () -> String = { BuildConfig.DEEPL_API_KEY },
    private val client: OkHttpClient = sharedHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : LyricsTranslator {

    override suspend fun translate(
        trackTitle: String,
        trackArtist: String,
        lyrics: LyricsData,
        targetLanguage: LyricsTranslationLanguage
    ): LyricsData = withContext(ioDispatcher) {
        if (lyrics.lines.isEmpty()) return@withContext lyrics

        val apiKey = apiKeyProvider().trim()
        if (!isValidApiKey(apiKey)) {
            throw DeepLConfigurationException(
                "Configura tu clave DEEPL_API_KEY en el panel de Secrets de AI Studio o cambia al motor Google."
            )
        }

        val nonEmptyIndices = mutableListOf<Int>()
        val textsToTranslate = mutableListOf<String>()
        lyrics.lines.forEachIndexed { idx, line ->
            val trimmed = line.text.trim()
            if (trimmed.isNotEmpty()) {
                nonEmptyIndices.add(idx)
                textsToTranslate.add(trimmed)
            }
        }
        if (textsToTranslate.isEmpty()) return@withContext lyrics

        val endpointUrl = resolveEndpointUrl(apiKey)
        val songContext = buildSongContext(trackTitle, trackArtist)
        val translatedMap = HashMap<Int, String>(textsToTranslate.size)

        var start = 0
        while (start < textsToTranslate.size) {
            val end = (start + MAX_LINES_PER_BATCH).coerceAtMost(textsToTranslate.size)
            val batchTexts = textsToTranslate.subList(start, end)
            val batchIndices = nonEmptyIndices.subList(start, end)
            val batchTranslated = translateBatch(
                endpointUrl = endpointUrl,
                apiKey = apiKey,
                lines = batchTexts,
                targetLang = targetLanguage.deepLCode,
                context = songContext
            )
            batchIndices.forEachIndexed { offset, originalIndex ->
                val translatedLine = batchTranslated.getOrNull(offset)?.trim()
                if (!translatedLine.isNullOrEmpty()) {
                    translatedMap[originalIndex] = translatedLine
                }
            }
            start = end
        }

        val alignedLines = lyrics.lines.mapIndexed { idx, original ->
            val translatedText = translatedMap[idx]
            if (translatedText != null) {
                LyricLine(timestampMs = original.timestampMs, text = translatedText)
            } else {
                original
            }
        }

        LyricsData(
            lines = alignedLines,
            isSynced = lyrics.isSynced,
            sourceLabel = "DeepL (${targetLanguage.displayName})"
        )
    }

    private fun translateBatch(
        endpointUrl: String,
        apiKey: String,
        lines: List<String>,
        targetLang: String,
        context: String?
    ): List<String> {
        val payload = DeepLTranslateRequest(
            text = lines,
            targetLang = targetLang,
            context = context
        )
        val bodyJson = sharedJson.encodeToString(payload)
        val request = Request.Builder()
            .url(endpointUrl)
            .header("Authorization", "DeepL-Auth-Key $apiKey")
            .header("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                when (response.code) {
                    401, 403 -> throw DeepLConfigurationException(
                        "La clave DEEPL_API_KEY fue rechazada por DeepL (HTTP ${response.code}). Verifica tu clave o usa Google."
                    )
                    456 -> throw IOException(
                        "Se alcanzó la cuota mensual gratuita de DeepL (HTTP 456). Puedes cambiar a Google Traductor."
                    )
                    429 -> throw IOException("Demasiadas solicitudes a DeepL. Intenta en unos segundos.")
                    else -> throw IOException("DeepL respondió HTTP ${response.code}")
                }
            }
            val parsed = sharedJson.decodeFromString<DeepLTranslateResponse>(rawBody)
            return parsed.translations.map { it.text }
        }
    }

    private fun buildSongContext(title: String, artist: String): String? {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim().takeUnless {
            it.equals("Artista desconocido", ignoreCase = true) ||
                it.equals("<unknown>", ignoreCase = true)
        }.orEmpty()
        return when {
            cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty() ->
                "Song lyrics for '$cleanTitle' by $cleanArtist. Keep natural poetic phrasing."
            cleanTitle.isNotEmpty() ->
                "Song lyrics for '$cleanTitle'. Keep natural poetic phrasing."
            else -> "Song lyrics. Keep natural poetic phrasing."
        }
    }

    companion object {
        const val DEEPL_FREE_ENDPOINT = "https://api-free.deepl.com/v2/translate"
        const val DEEPL_PRO_ENDPOINT = "https://api.deepl.com/v2/translate"
        private const val PLACEHOLDER_KEY = "DEEPL_API_KEY_DEFAULT_VALUE"
        private const val MAX_LINES_PER_BATCH = 45
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun isValidApiKey(apiKey: String?): Boolean {
            val trimmed = apiKey?.trim().orEmpty()
            return trimmed.isNotEmpty() &&
                !trimmed.equals(PLACEHOLDER_KEY, ignoreCase = true) &&
                !trimmed.startsWith("YOUR_", ignoreCase = true)
        }

        fun resolveEndpointUrl(apiKey: String): String {
            return if (apiKey.trim().endsWith(":fx", ignoreCase = true)) {
                DEEPL_FREE_ENDPOINT
            } else {
                DEEPL_PRO_ENDPOINT
            }
        }
    }
}

/**
 * Lightweight keyless Google Translate HTTP translator (no AI/LLM, no API key required).
 * Normalizes common English song contractions before translating and preserves exact `.lrc` timestamps.
 */
class GoogleLyricsTranslator(
    private val client: OkHttpClient = sharedHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : LyricsTranslator {

    override suspend fun translate(
        trackTitle: String,
        trackArtist: String,
        lyrics: LyricsData,
        targetLanguage: LyricsTranslationLanguage
    ): LyricsData = withContext(ioDispatcher) {
        if (lyrics.lines.isEmpty()) return@withContext lyrics

        val nonEmptyIndices = mutableListOf<Int>()
        val normalizedTexts = mutableListOf<String>()
        lyrics.lines.forEachIndexed { idx, line ->
            val trimmed = line.text.trim()
            if (trimmed.isNotEmpty()) {
                nonEmptyIndices.add(idx)
                normalizedTexts.add(LyricTextNormalizer.normalizeForTranslation(trimmed))
            }
        }
        if (normalizedTexts.isEmpty()) return@withContext lyrics

        val translatedMap = HashMap<Int, String>(normalizedTexts.size)
        var start = 0
        while (start < normalizedTexts.size) {
            var end = start
            var chars = 0
            while (end < normalizedTexts.size && end - start < MAX_LINES_PER_CHUNK) {
                val nextLen = normalizedTexts[end].length + 1
                if (end > start && chars + nextLen > MAX_CHARS_PER_CHUNK) break
                chars += nextLen
                end++
            }
            val chunkTexts = normalizedTexts.subList(start, end)
            val chunkIndices = nonEmptyIndices.subList(start, end)
            val translatedLines = translateChunk(chunkTexts, targetLanguage.code)
            chunkIndices.forEachIndexed { offset, originalIdx ->
                val t = translatedLines.getOrNull(offset)?.trim()
                if (!t.isNullOrEmpty()) {
                    translatedMap[originalIdx] = t
                }
            }
            start = end
        }

        val aligned = lyrics.lines.mapIndexed { idx, original ->
            val translatedText = translatedMap[idx]
            if (translatedText != null) {
                LyricLine(timestampMs = original.timestampMs, text = translatedText)
            } else {
                original
            }
        }

        LyricsData(
            lines = aligned,
            isSynced = lyrics.isSynced,
            sourceLabel = "Google (${targetLanguage.displayName})"
        )
    }

    private fun translateChunk(lines: List<String>, targetLangCode: String): List<String> {
        val joined = lines.joinToString("\n")
        val encodedQuery = URLEncoder.encode(joined, "UTF-8")
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLangCode&dt=t&q=$encodedQuery"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "DavooPlayer/2.0")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Google Traductor respondió HTTP ${response.code}")
            }
            val raw = response.body?.string().orEmpty()
            val fullTranslated = parseGoogleSingleResponse(raw)
            val splitLines = fullTranslated.split('\n')
            return if (splitLines.size == lines.size) {
                splitLines
            } else {
                // Safe fallback if Google merged or expanded newlines
                lines.indices.map { i -> splitLines.getOrElse(i) { lines[i] } }
            }
        }
    }

    companion object {
        private const val MAX_LINES_PER_CHUNK = 24
        private const val MAX_CHARS_PER_CHUNK = 1400

        fun parseGoogleSingleResponse(rawJson: String): String {
            val root = sharedJson.parseToJsonElement(rawJson).jsonArray
            val segments = root.firstOrNull() as? JsonArray ?: return ""
            val builder = StringBuilder()
            for (segment in segments) {
                val pair = segment as? JsonArray ?: continue
                val translatedPart = (pair.firstOrNull() as? JsonPrimitive)?.content ?: continue
                builder.append(translatedPart)
            }
            return builder.toString()
        }
    }
}

/**
 * Coordinates engine selection (DeepL or Google), in-memory + persistent Room caching,
 * cancellation, and UI state for lyrics translation.
 */
class LyricsTranslationCoordinator(
    private val scope: CoroutineScope,
    private val deepLTranslator: LyricsTranslator = DeepLLyricsTranslator(),
    private val googleTranslator: LyricsTranslator = GoogleLyricsTranslator(),
    private val onPersistTranslation: (suspend (trackId: String, language: LyricsTranslationLanguage, translated: LyricsData) -> Unit)? = null
) {
    private data class CacheKey(
        val trackId: String,
        val lyricsSignature: Int,
        val language: LyricsTranslationLanguage,
        val engine: TranslationEngine
    )

    private val _state = MutableStateFlow<LyricsTranslationUiState>(
        LyricsTranslationUiState.Idle
    )
    val state: StateFlow<LyricsTranslationUiState> = _state

    private val cache = object : LinkedHashMap<CacheKey, LyricsData>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, LyricsData>?): Boolean {
            return size > 24
        }
    }

    private var activeJob: Job? = null

    fun restoreSavedTranslation(
        trackId: String,
        originalLyrics: LyricsData,
        language: LyricsTranslationLanguage,
        translatedLyrics: LyricsData
    ) {
        activeJob?.cancel()
        val guessedEngine = if (translatedLyrics.sourceLabel.contains("Google", ignoreCase = true)) {
            TranslationEngine.GOOGLE
        } else {
            TranslationEngine.DEEPL
        }
        val key = CacheKey(trackId, originalLyrics.lines.hashCode(), language, guessedEngine)
        cache[key] = translatedLyrics
        _state.value = LyricsTranslationUiState.Success(
            trackId = trackId,
            language = language,
            translatedLyrics = translatedLyrics,
            isSavedOffline = true
        )
    }

    fun translate(
        trackId: String,
        trackTitle: String,
        trackArtist: String,
        lyrics: LyricsData,
        targetLanguage: LyricsTranslationLanguage,
        engine: TranslationEngine = TranslationEngine.DEEPL
    ) {
        val signature = lyrics.lines.hashCode()
        val key = CacheKey(trackId, signature, targetLanguage, engine)
        cache[key]?.let { cached ->
            _state.value = LyricsTranslationUiState.Success(
                trackId = trackId,
                language = targetLanguage,
                translatedLyrics = cached,
                isSavedOffline = true
            )
            return
        }

        val translator = when (engine) {
            TranslationEngine.DEEPL -> deepLTranslator
            TranslationEngine.GOOGLE -> googleTranslator
        }

        activeJob?.cancel()
        activeJob = scope.launch {
            _state.value = LyricsTranslationUiState.Loading(targetLanguage, engine)
            try {
                val translated = translator.translate(
                    trackTitle = trackTitle,
                    trackArtist = trackArtist,
                    lyrics = lyrics,
                    targetLanguage = targetLanguage
                )
                cache[key] = translated
                val persisted = runCatching {
                    onPersistTranslation?.invoke(trackId, targetLanguage, translated)
                    onPersistTranslation != null
                }.getOrDefault(false)
                _state.value = LyricsTranslationUiState.Success(
                    trackId = trackId,
                    language = targetLanguage,
                    translatedLyrics = translated,
                    isSavedOffline = persisted
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: DeepLConfigurationException) {
                Log.w(TAG, "DeepL key configuration issue: ${e.message}")
                _state.value = LyricsTranslationUiState.Error(
                    language = targetLanguage,
                    engine = engine,
                    message = e.message ?: "Configura tu clave DEEPL_API_KEY en Secrets o cambia a Google Traductor."
                )
            } catch (e: IOException) {
                Log.w(TAG, "Translation network error (${engine.name}): ${e.message}")
                _state.value = LyricsTranslationUiState.Error(
                    language = targetLanguage,
                    engine = engine,
                    message = e.message?.takeIf { it.contains("cuota", ignoreCase = true) || it.contains("solicitudes", ignoreCase = true) }
                        ?: "No se pudo conectar con ${engine.displayName}. Verifica tu conexión o cambia de traductor."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Lyrics translation failed (${engine.name})", e)
                _state.value = LyricsTranslationUiState.Error(
                    language = targetLanguage,
                    engine = engine,
                    message = "No se pudo traducir con ${engine.displayName}. Intenta de nuevo o cambia de motor."
                )
            }
        }
    }

    fun invalidateTrack(trackId: String) {
        val iterator = cache.keys.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().trackId == trackId) {
                iterator.remove()
            }
        }
        val current = _state.value
        if (current is LyricsTranslationUiState.Success && current.trackId == trackId) {
            reset()
        }
    }

    fun reset() {
        activeJob?.cancel()
        activeJob = null
        _state.value = LyricsTranslationUiState.Idle
    }
}
