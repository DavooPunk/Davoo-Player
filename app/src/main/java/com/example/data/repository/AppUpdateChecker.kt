package com.example.data.repository

import com.example.BuildConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val latestVersion: String,
    val releaseUrl: String,
    val releaseNotes: String = ""
)

sealed interface UpdateCheckResult {
    data object RepoNotConfigured : UpdateCheckResult
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String, val repoUrl: String) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

/**
 * Lightweight, optional GitHub release/tag update checker (zero background polling when disabled).
 * Uses OkHttp + kotlinx.serialization directly without heavy updater dependencies.
 */
class AppUpdateChecker(
    private val client: OkHttpClient = defaultClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val apiBaseUrl: String = "https://api.github.com"
) {

    suspend fun checkForUpdate(
        repoUrl: String,
        currentVersion: String = BuildConfig.VERSION_NAME
    ): UpdateCheckResult = withContext(ioDispatcher) {
        val normalizedUrl = normalizeRepoUrl(repoUrl)
        val ownerRepo = parseOwnerAndRepo(normalizedUrl) ?: return@withContext UpdateCheckResult.RepoNotConfigured
        val (owner, repo) = ownerRepo

        runCatching {
            val latestRequest = Request.Builder()
                .url("$apiBaseUrl/repos/$owner/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "DavooPlayer/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            client.newCall(latestRequest).execute().use { response ->
                when (response.code) {
                    in 200..299 -> {
                        val body = response.body?.string().orEmpty()
                        parseReleaseResponse(body, currentVersion, normalizedUrl)
                    }
                    404 -> {
                        // Fallback to repository tags when no formal GitHub Release has been published yet
                        val tagsRequest = Request.Builder()
                            .url("$apiBaseUrl/repos/$owner/$repo/tags?per_page=1")
                            .header("Accept", "application/vnd.github+json")
                            .header("User-Agent", "DavooPlayer/${BuildConfig.VERSION_NAME}")
                            .get()
                            .build()
                        client.newCall(tagsRequest).execute().use { tagsResp ->
                            if (!tagsResp.isSuccessful) {
                                return@withContext UpdateCheckResult.Error("GitHub respondió HTTP ${tagsResp.code}")
                            }
                            val tagsBody = tagsResp.body?.string().orEmpty()
                            parseTagFallbackResponse(tagsBody, currentVersion, "$normalizedUrl/releases")
                        }
                    }
                    else -> UpdateCheckResult.Error("No se pudo consultar GitHub (HTTP ${response.code})")
                }
            }
        }.getOrElse { err ->
            UpdateCheckResult.Error(
                err.message?.takeIf { it.isNotBlank() } ?: "Error de red al comprobar actualizaciones"
            )
        }
    }

    companion object {
        const val DEFAULT_GITHUB_REPO_URL = "https://github.com/DavooPunk/Davoo-Player"
        const val DEFAULT_GITHUB_RELEASES_URL = "https://github.com/DavooPunk/Davoo-Player/releases"
        const val PERIODIC_INTERVAL_MS = 24L * 60L * 60L * 1000L

        private val json = Json { ignoreUnknownKeys = true }

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build()
        }

        fun normalizeRepoUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            if (trimmed.isEmpty() || trimmed.contains("PEGA AQUÍ", ignoreCase = true)) return ""
            val pair = parseOwnerAndRepo(trimmed) ?: return ""
            return "https://github.com/${pair.first}/${pair.second}"
        }

        fun parseOwnerAndRepo(rawUrl: String): Pair<String, String>? {
            val trimmed = rawUrl.trim().removeSuffix("/")
            if (trimmed.isEmpty() || trimmed.contains("PEGA AQUÍ", ignoreCase = true)) return null

            val clean = trimmed
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .removePrefix("github.com/")
                .removePrefix("api.github.com/repos/")

            val segments = clean.split('/')
                .map { it.trim().removeSuffix(".git") }
                .filter { it.isNotEmpty() }

            if (segments.size < 2) return null
            val owner = segments[0]
            val repo = segments[1]
            if (owner.isBlank() || repo.isBlank() || owner.contains(' ') || repo.contains(' ')) return null
            return owner to repo
        }

        fun parseReleaseResponse(
            rawJson: String,
            currentVersion: String,
            fallbackRepoUrl: String
        ): UpdateCheckResult {
            val obj = runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull()
                ?: return UpdateCheckResult.Error("Respuesta de GitHub inválida")
            val tagName = obj["tag_name"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (tagName.isEmpty()) {
                return UpdateCheckResult.UpToDate(currentVersion, fallbackRepoUrl)
            }
            val htmlUrl = obj["html_url"]?.jsonPrimitive?.content?.trim()
                ?.takeIf { it.startsWith("http") }
                ?: "$fallbackRepoUrl/releases"
            val notes = obj["body"]?.jsonPrimitive?.content?.trim().orEmpty()
            val cleanRemote = cleanVersionLabel(tagName)

            return if (isNewerVersion(cleanRemote, currentVersion)) {
                UpdateCheckResult.UpdateAvailable(
                    AppUpdateInfo(
                        latestVersion = cleanRemote,
                        releaseUrl = htmlUrl,
                        releaseNotes = notes.take(400)
                    )
                )
            } else {
                UpdateCheckResult.UpToDate(currentVersion, fallbackRepoUrl)
            }
        }

        fun parseTagFallbackResponse(
            rawJson: String,
            currentVersion: String,
            fallbackReleasesUrl: String
        ): UpdateCheckResult {
            val arr = runCatching { json.parseToJsonElement(rawJson).jsonArray }.getOrNull()
                ?: return UpdateCheckResult.UpToDate(currentVersion, fallbackReleasesUrl)
            val firstObj = arr.firstOrNull()?.jsonObject
                ?: return UpdateCheckResult.UpToDate(currentVersion, fallbackReleasesUrl)
            val tagName = firstObj["name"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (tagName.isEmpty()) {
                return UpdateCheckResult.UpToDate(currentVersion, fallbackReleasesUrl)
            }
            val cleanRemote = cleanVersionLabel(tagName)
            return if (isNewerVersion(cleanRemote, currentVersion)) {
                UpdateCheckResult.UpdateAvailable(
                    AppUpdateInfo(
                        latestVersion = cleanRemote,
                        releaseUrl = fallbackReleasesUrl,
                        releaseNotes = ""
                    )
                )
            } else {
                UpdateCheckResult.UpToDate(currentVersion, fallbackReleasesUrl)
            }
        }

        fun cleanVersionLabel(version: String): String {
            return version.trim().removePrefix("v").removePrefix("V")
        }

        fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
            val remoteParts = extractNumericSegments(remoteVersion)
            val currentParts = extractNumericSegments(currentVersion)
            if (remoteParts.isEmpty() || currentParts.isEmpty()) return false
            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }

        private fun extractNumericSegments(version: String): List<Int> {
            val base = cleanVersionLabel(version).substringBefore('-').substringBefore('+')
            return base.split('.').mapNotNull { part ->
                part.takeWhile { it.isDigit() }.toIntOrNull()
            }
        }
    }
}
