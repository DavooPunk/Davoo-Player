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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        shortLabel = "DeepSeek",
        description = "Traducción poética y contextual con IA usando tu API de DeepSeek"
    );

    companion object {
        fun fromName(name: String?): TranslationEngine {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEEPL
        }
    }
}

/**
 * Supported target languages for DeepL API v2, Google Translate, DeepSeek AI, and saved offline human translations.
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

sealed interface DeepSeekTestUiState {
    data object Idle : DeepSeekTestUiState
    data object Testing : DeepSeekTestUiState
    data class Success(val message: String) : DeepSeekTestUiState
    data class Error(val message: String) : DeepSeekTestUiState
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

class DeepSeekConfigurationException(message: String) : IllegalStateException(message)

class LyricsTranslationNetworkException(message: String) : IOException(message)

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
            if (!line.isInstrumental && trimmed.isNotEmpty()) {
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
            if (!line.isInstrumental && trimmed.isNotEmpty()) {
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

@Serializable
private data class DeepSeekMessage(
    val role: String,
    val content: String
)

@Serializable
private data class DeepSeekResponseFormat(
    val type: String = "json_object"
)

@Serializable
private data class DeepSeekChatRequest(
    val model: String = "deepseek-chat",
    val messages: List<DeepSeekMessage>,
    val temperature: Double = 0.3,
    @SerialName("response_format")
    val responseFormat: DeepSeekResponseFormat = DeepSeekResponseFormat("json_object"),
    @SerialName("max_tokens")
    val maxTokens: Int? = null
)

@Serializable
private data class DeepSeekChoice(
    val message: DeepSeekMessage? = null
)

@Serializable
private data class DeepSeekChatResponse(
    val choices: List<DeepSeekChoice> = emptyList()
)

@Serializable
private data class DeepSeekLyricLineItem(
    val index: Int,
    val text: String
)

@Serializable
private data class DeepSeekLyricPayload(
    val title: String,
    val artist: String,
    @SerialName("target_language")
    val targetLanguage: String,
    val lines: List<DeepSeekLyricLineItem>
)

@Serializable
private data class DeepSeekTranslationsEnvelope(
    val translations: List<DeepSeekLyricLineItem> = emptyList(),
    val lines: List<DeepSeekLyricLineItem> = emptyList()
)

/**
 * DeepSeek AI chat/completions translator (`https://api.deepseek.com/chat/completions`) using
 * `deepseek-chat`, `temperature = 0.3`, and `response_format = {"type": "json_object"}`.
 * Sends all non-empty lyric lines in a single indexed JSON request to preserve poetic context,
 * idioms, and exact 1-to-1 `.lrc` line timestamps.
 */
class DeepSeekLyricsTranslator(
    private val apiKeyProvider: () -> String = { "" },
    private val client: OkHttpClient = sharedHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val endpointUrl: String = DEEPSEEK_ENDPOINT
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
            throw DeepSeekConfigurationException(
                "Configura tu API Key de DeepSeek en Opciones > Traductor de letras para usar este motor."
            )
        }

        val nonEmptyIndices = mutableListOf<Int>()
        val indexedItems = mutableListOf<DeepSeekLyricLineItem>()
        lyrics.lines.forEachIndexed { idx, line ->
            val trimmed = line.text.trim()
            if (!line.isInstrumental && trimmed.isNotEmpty()) {
                val seqIndex = indexedItems.size
                nonEmptyIndices.add(idx)
                indexedItems.add(DeepSeekLyricLineItem(index = seqIndex, text = trimmed))
            }
        }
        if (indexedItems.isEmpty()) return@withContext lyrics

        val userPayload = DeepSeekLyricPayload(
            title = trackTitle.trim().ifEmpty { "Unknown" },
            artist = trackArtist.trim().takeUnless {
                it.equals("Artista desconocido", ignoreCase = true) ||
                    it.equals("<unknown>", ignoreCase = true)
            }.orEmpty(),
            targetLanguage = "${targetLanguage.displayName} (${targetLanguage.code})",
            lines = indexedItems
        )

        val targetLangLabel = "${targetLanguage.displayName} (${targetLanguage.code})"
        val systemPrompt = buildString {
            append("Eres un traductor experto de letras de canciones con sensibilidad poética. ")
            append("Traduce cada verso numerado al idioma destino solicitado ($targetLangLabel), preservando el sentido poético, metáforas, modismos, matices emocionales y el índice exacto de cada línea.\n")
            append("Regla estricta obligatoria:\n")
            append("- La canción puede estar escrita en uno o varios idiomas mezclados dentro de la misma letra o incluso dentro del mismo verso (por ejemplo: español con inglés, japonés con inglés, coreano con inglés, etc.). Tu tarea es traducir absolutamente TODAS las líneas y TODAS las palabras o frases en idiomas extranjeros al idioma destino solicitado ($targetLangLabel), sin importar cuál sea el idioma principal de la canción.\n")
            append("- Nunca dejes versos completos ni palabras sueltas en inglés u otro idioma sin traducir al idioma destino (salvo nombres propios de personas o lugares). Por ejemplo, si el idioma destino es Español, tanto una canción en español con versos en inglés como una en japonés con versos en inglés deben quedar 100% traducidas al español.\n")
            append("- Si una línea ya está íntegramente en $targetLangLabel sin palabras ni frases extranjeras, consérvala intacta en su mismo índice.\n")
            append("Responde ÚNICAMENTE con un objeto JSON válido usando el esquema {\"translations\":[{\"index\":0,\"text\":\"...\"}]}. ")
            append("Incluye cada índice de entrada una sola vez y no fusiones, omitas ni agregues líneas.")
        }

        val requestPayload = DeepSeekChatRequest(
            model = MODEL_NAME,
            messages = listOf(
                DeepSeekMessage(role = "system", content = systemPrompt),
                DeepSeekMessage(role = "user", content = sharedJson.encodeToString(userPayload))
            ),
            temperature = DEFAULT_TEMPERATURE,
            responseFormat = DeepSeekResponseFormat("json_object")
        )

        val bodyJson = sharedJson.encodeToString(requestPayload)
        val request = Request.Builder()
            .url(endpointUrl)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val rawResponseBody = executeWithMappedErrors(request)
        val parsedTranslations = parseTranslationsFromChatResponse(rawResponseBody, indexedItems.size)
        if (parsedTranslations.isEmpty()) {
            throw LyricsTranslationNetworkException(
                "DeepSeek devolvió una respuesta vacía o incompatible. Intenta de nuevo."
            )
        }

        val translatedMap = HashMap<Int, String>(nonEmptyIndices.size)
        nonEmptyIndices.forEachIndexed { seqIdx, originalLineIdx ->
            val translatedText = parsedTranslations[seqIdx]?.trim()
            if (!translatedText.isNullOrEmpty()) {
                translatedMap[originalLineIdx] = translatedText
            }
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
            sourceLabel = "DeepSeek (${targetLanguage.displayName})"
        )
    }

    suspend fun testConnection(apiKeyOverride: String? = null): Result<String> = withContext(ioDispatcher) {
        runCatching {
            val apiKey = (apiKeyOverride ?: apiKeyProvider()).trim()
            if (!isValidApiKey(apiKey)) {
                throw DeepSeekConfigurationException(
                    "Ingresa una API Key de DeepSeek válida antes de probar la conexión."
                )
            }
            val pingRequest = DeepSeekChatRequest(
                model = MODEL_NAME,
                messages = listOf(
                    DeepSeekMessage(role = "system", content = "Reply with JSON object {\"status\":\"ok\"}."),
                    DeepSeekMessage(role = "user", content = "ping")
                ),
                temperature = DEFAULT_TEMPERATURE,
                responseFormat = DeepSeekResponseFormat("json_object"),
                maxTokens = 24
            )
            val request = Request.Builder()
                .url(endpointUrl)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(sharedJson.encodeToString(pingRequest).toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val raw = executeWithMappedErrors(request)
            val chatResp = sharedJson.decodeFromString<DeepSeekChatResponse>(raw)
            val content = chatResp.choices.firstOrNull()?.message?.content.orEmpty()
            if (content.isBlank()) {
                throw LyricsTranslationNetworkException("DeepSeek respondió sin contenido válido.")
            }
            "Conexión exitosa con DeepSeek AI"
        }
    }

    private fun executeWithMappedErrors(request: Request): String {
        try {
            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    when (response.code) {
                        401 -> throw DeepSeekConfigurationException(
                            "API Key de DeepSeek inválida (HTTP 401). Revísala en Opciones > Traductor de letras."
                        )
                        402 -> throw LyricsTranslationNetworkException(
                            "Saldo insuficiente en tu cuenta de DeepSeek (HTTP 402). Verifica tu crédito o cambia de motor."
                        )
                        429 -> throw LyricsTranslationNetworkException(
                            "Límite de peticiones de DeepSeek alcanzado (HTTP 429). Intenta en unos segundos."
                        )
                        else -> throw LyricsTranslationNetworkException(
                            "DeepSeek respondió HTTP ${response.code}. Intenta de nuevo más tarde."
                        )
                    }
                }
                return rawBody
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (ce: DeepSeekConfigurationException) {
            throw ce
        } catch (ne: LyricsTranslationNetworkException) {
            throw ne
        } catch (e: java.net.SocketTimeoutException) {
            throw LyricsTranslationNetworkException(
                "Tiempo de espera agotado al conectar con DeepSeek. Revisa tu conexión."
            )
        } catch (e: IOException) {
            throw LyricsTranslationNetworkException(
                "No se pudo conectar con DeepSeek. Verifica tu conexión a internet."
            )
        }
    }

    companion object {
        const val DEEPSEEK_ENDPOINT = "https://api.deepseek.com/chat/completions"
        const val MODEL_NAME = "deepseek-chat"
        const val DEFAULT_TEMPERATURE = 0.3
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun isValidApiKey(apiKey: String?): Boolean {
            val trimmed = apiKey?.trim().orEmpty()
            return trimmed.length >= 8 && !trimmed.startsWith("YOUR_", ignoreCase = true)
        }

        fun parseTranslationsFromChatResponse(rawResponseJson: String, expectedCount: Int): Map<Int, String> {
            val chatResponse = sharedJson.decodeFromString<DeepSeekChatResponse>(rawResponseJson)
            val content = chatResponse.choices.firstOrNull()?.message?.content?.trim().orEmpty()
            if (content.isEmpty()) return emptyMap()

            val cleanedJson = stripMarkdownJsonFence(content)
            val result = LinkedHashMap<Int, String>(expectedCount)

            val envelope = runCatching {
                sharedJson.decodeFromString<DeepSeekTranslationsEnvelope>(cleanedJson)
            }.getOrNull()
            val primaryList = envelope?.translations?.takeIf { it.isNotEmpty() } ?: envelope?.lines.orEmpty()
            if (primaryList.isNotEmpty()) {
                primaryList.forEach { item ->
                    val txt = item.text.trim()
                    if (txt.isNotEmpty()) {
                        result[item.index] = txt
                    }
                }
                if (result.isNotEmpty()) return result
            }

            // Fallback tolerance for alternative valid JSON structures (e.g., {"0": "...", "1": "..."} or string array)
            val root = runCatching { sharedJson.parseToJsonElement(cleanedJson) }.getOrNull()
            if (root is JsonObject) {
                val arrayNode = (root["translations"] as? JsonArray) ?: (root["lines"] as? JsonArray)
                if (arrayNode != null) {
                    arrayNode.forEachIndexed { idx, el ->
                        when (el) {
                            is JsonPrimitive -> el.content.trim().takeIf { it.isNotEmpty() }?.let { result[idx] = it }
                            is JsonObject -> {
                                val lineIdx = el["index"]?.jsonPrimitive?.intOrNull ?: idx
                                val lineTxt = el["text"]?.jsonPrimitive?.content?.trim()
                                    ?: el["translation"]?.jsonPrimitive?.content?.trim()
                                if (!lineTxt.isNullOrEmpty()) result[lineIdx] = lineTxt
                            }
                            else -> Unit
                        }
                    }
                } else {
                    for ((k, v) in root) {
                        val keyIdx = k.toIntOrNull() ?: continue
                        val lineTxt = (v as? JsonPrimitive)?.content?.trim() ?: continue
                        if (lineTxt.isNotEmpty()) result[keyIdx] = lineTxt
                    }
                }
            }
            return result
        }

        private fun stripMarkdownJsonFence(raw: String): String {
            var trimmed = raw.trim()
            if (trimmed.startsWith("```")) {
                trimmed = trimmed.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
                if (trimmed.endsWith("```")) {
                    trimmed = trimmed.removeSuffix("```")
                }
            }
            return trimmed.trim()
        }
    }
}

/**
 * Coordinates engine selection (DeepL, Google, or DeepSeek), in-memory + persistent Room caching,
 * cancellation, and UI state for lyrics translation.
 */
class LyricsTranslationCoordinator(
    private val scope: CoroutineScope,
    private val deepLTranslator: LyricsTranslator = DeepLLyricsTranslator(),
    private val googleTranslator: LyricsTranslator = GoogleLyricsTranslator(),
    private val deepSeekTranslator: LyricsTranslator = DeepSeekLyricsTranslator(),
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
        val detectedEngine = when {
            translatedLyrics.sourceLabel.contains("DeepSeek", ignoreCase = true) -> TranslationEngine.DEEPSEEK
            translatedLyrics.sourceLabel.contains("Google", ignoreCase = true) -> TranslationEngine.GOOGLE
            translatedLyrics.sourceLabel.contains("DeepL", ignoreCase = true) -> TranslationEngine.DEEPL
            else -> null
        }
        if (detectedEngine != null) {
            val key = CacheKey(trackId, originalLyrics.lines.hashCode(), language, detectedEngine)
            cache[key] = translatedLyrics
        }
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
        engine: TranslationEngine = TranslationEngine.DEEPL,
        forceRefresh: Boolean = false
    ) {
        val signature = lyrics.lines.hashCode()
        val key = CacheKey(trackId, signature, targetLanguage, engine)
        if (!forceRefresh) {
            cache[key]?.let { cached ->
                _state.value = LyricsTranslationUiState.Success(
                    trackId = trackId,
                    language = targetLanguage,
                    translatedLyrics = cached,
                    isSavedOffline = true
                )
                return
            }
        }

        val translator = when (engine) {
            TranslationEngine.DEEPL -> deepLTranslator
            TranslationEngine.GOOGLE -> googleTranslator
            TranslationEngine.DEEPSEEK -> deepSeekTranslator
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
            } catch (e: DeepSeekConfigurationException) {
                Log.w(TAG, "DeepSeek key configuration issue: ${e.message}")
                _state.value = LyricsTranslationUiState.Error(
                    language = targetLanguage,
                    engine = engine,
                    message = e.message ?: "Configura tu API Key de DeepSeek en Opciones > Traductor de letras."
                )
            } catch (e: LyricsTranslationNetworkException) {
                Log.w(TAG, "Translation service error (${engine.name}): ${e.message}")
                _state.value = LyricsTranslationUiState.Error(
                    language = targetLanguage,
                    engine = engine,
                    message = e.message ?: "No se pudo conectar con ${engine.displayName}. Verifica tu conexión o cambia de traductor."
                )
            } catch (e: IOException) {
                Log.w(TAG, "Translation network error (${engine.name}): ${e.message}")
                _state.value = LyricsTranslationUiState.Error(
                    language = targetLanguage,
                    engine = engine,
                    message = e.message?.takeIf {
                        it.contains("cuota", ignoreCase = true) ||
                            it.contains("solicitudes", ignoreCase = true) ||
                            it.contains("saldo", ignoreCase = true) ||
                            it.contains("límite", ignoreCase = true)
                    } ?: "No se pudo conectar con ${engine.displayName}. Verifica tu conexión o cambia de traductor."
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
