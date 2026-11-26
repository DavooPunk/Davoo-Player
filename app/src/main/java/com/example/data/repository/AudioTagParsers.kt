package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.model.AudioTrack
import com.example.model.LyricLine
import com.example.model.LyricsData
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Pure-Kotlin parsers and serializers for synced `.lrc` lyrics, embedded FLAC/ID3 tags
 * (lyrics + ReplayGain), `.cue` sheets, and `.m3u` / `.m3u8` playlists.
 */
object AudioTagParsers {

    const val SAVED_LRC_SOURCE_LABEL = "Archivo .lrc guardado"
    const val ONLINE_LRC_SOURCE_LABEL = "Descargada (LRCLIB)"
    const val MANUAL_TRANSLATION_SOURCE_LABEL = "Traducción guardada (manual)"

    private val lrcTimeRegex = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val replayGainRegex = Regex("([-+]?\\d+(?:\\.\\d+)?)\\s*dB", RegexOption.IGNORE_CASE)

    fun isInstrumentalMarkerText(raw: String): Boolean {
        val stripped = raw.trim()
        if (stripped.isEmpty()) return true
        if (stripped.equals("[instrumental]", ignoreCase = true) ||
            stripped.equals("(instrumental)", ignoreCase = true) ||
            stripped.equals("instrumental", ignoreCase = true) ||
            stripped.equals("[music]", ignoreCase = true) ||
            stripped.equals("(music)", ignoreCase = true)
        ) {
            return true
        }
        val withoutMusicEmoji = stripped.replace("🎵", "").replace("🎶", "").trim()
        if (withoutMusicEmoji.isEmpty()) return true
        return withoutMusicEmoji.all { ch ->
            ch.isWhitespace() ||
                ch == '.' ||
                ch == '…' ||
                ch == '-' ||
                ch == '—' ||
                ch == '~' ||
                ch == '♪' ||
                ch == '♫' ||
                ch == '♩' ||
                ch == '♬' ||
                ch == '•' ||
                ch == '*'
        }
    }

    fun parseLrcText(rawText: String, sourceLabel: String): LyricsData? {
        val trimmed = rawText.removePrefix("\uFEFF").trim()
        if (trimmed.isEmpty()) return null

        val syncedLines = mutableListOf<LyricLine>()
        val plainLines = mutableListOf<LyricLine>()

        trimmed.lineSequence().forEach { line ->
            val matches = lrcTimeRegex.findAll(line).toList()
            if (matches.isNotEmpty()) {
                val lyricContent = line.replace(lrcTimeRegex, "").trim()
                val isInstr = isInstrumentalMarkerText(lyricContent)
                matches.forEach { m ->
                    val mins = m.groupValues[1].toLongOrNull() ?: 0L
                    val secs = m.groupValues[2].toLongOrNull() ?: 0L
                    val fracStr = m.groupValues[3]
                    val millis = when (fracStr.length) {
                        0 -> 0L
                        1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                        2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                        else -> fracStr.take(3).toLongOrNull() ?: 0L
                    }
                    val totalMs = mins * 60_000L + secs * 1_000L + millis
                    syncedLines.add(
                        LyricLine(
                            timestampMs = totalMs,
                            text = if (isInstr) "" else lyricContent,
                            isInstrumental = isInstr
                        )
                    )
                }
            } else if (!line.startsWith("[") && line.isNotBlank()) {
                plainLines.add(LyricLine(timestampMs = null, text = line.trim()))
            }
        }

        val hasAnyVocalOrExplicitSymbol = syncedLines.any { !it.isInstrumental } ||
            rawText.contains("♪") || rawText.contains("...") || rawText.contains("…")

        return when {
            syncedLines.isNotEmpty() && hasAnyVocalOrExplicitSymbol -> {
                val sorted = syncedLines.sortedBy { it.timestampMs ?: 0L }
                val (merged, isBilingual) = mergeBilingualSyncedLines(sorted)
                val withInterludes = insertInstrumentalInterludes(merged)
                val finalLabel = if (isBilingual && !sourceLabel.contains("Bilingüe", ignoreCase = true)) {
                    "$sourceLabel · Bilingüe"
                } else {
                    sourceLabel
                }
                LyricsData(
                    lines = withInterludes,
                    isSynced = true,
                    sourceLabel = finalLabel,
                    hasEmbeddedTranslation = isBilingual
                )
            }
            plainLines.isNotEmpty() -> LyricsData(
                lines = plainLines,
                isSynced = false,
                sourceLabel = sourceLabel
            )
            else -> null
        }
    }

    /**
     * Automatically manages Spotify-style instrumental indicators (`♪`) with strict timing thresholds:
     * - Initial intro before the first vocal line: shown only when the first vocal line starts >= 4,000 ms.
     * - Explicit empty/separator lines in `.lrc`: kept only if at least 5,000 ms remain before the next vocal line
     *   (breath pauses < 5,000 ms and trailing empty lines are ignored so vocal focus stays normal).
     * - Consecutive vocal lines with no empty line in between and > 8,000 ms gap: estimates the first line's
     *   vocal duration (~65 ms/char, min 2,500 ms) and inserts an instrumental marker only if remaining free
     *   time before the next vocal line is >= 5,000 ms.
     */
    private fun insertInstrumentalInterludes(lines: List<LyricLine>): List<LyricLine> {
        if (lines.isEmpty()) return lines

        val firstVocalIndex = lines.indexOfFirst { !it.isInstrumental && it.text.isNotBlank() }
        if (firstVocalIndex < 0) {
            return lines
        }

        val result = ArrayList<LyricLine>(lines.size + 4)
        val firstVocalTs = lines[firstVocalIndex].timestampMs

        if (firstVocalTs != null && firstVocalTs >= INTRO_INSTRUMENTAL_THRESHOLD_MS) {
            val preVocalExplicit = lines
                .subList(0, firstVocalIndex)
                .firstOrNull {
                    val ts = it.timestampMs
                    ts != null && (firstVocalTs - ts) >= MIN_INSTRUMENTAL_DURATION_MS
                }
            val introTs = if (preVocalExplicit?.timestampMs != null && preVocalExplicit.timestampMs <= 1_500L) {
                preVocalExplicit.timestampMs
            } else {
                0L
            }
            result.add(LyricLine(timestampMs = introTs, text = "", isInstrumental = true))
        }

        var lastVocalTs: Long? = null
        for (i in firstVocalIndex until lines.size) {
            val current = lines[i]
            val isExplicitEmptyOrInstr = current.isInstrumental || current.text.isBlank()
            if (isExplicitEmptyOrInstr) {
                val currTs = current.timestampMs
                var nextVocalTs: Long? = null
                for (j in (i + 1) until lines.size) {
                    val candidate = lines[j]
                    if (!candidate.isInstrumental && candidate.text.isNotBlank()) {
                        nextVocalTs = candidate.timestampMs
                        break
                    }
                }
                if (currTs != null &&
                    nextVocalTs != null &&
                    currTs > (lastVocalTs ?: -1L) &&
                    (nextVocalTs - currTs) >= MIN_INSTRUMENTAL_DURATION_MS &&
                    result.lastOrNull()?.isInstrumental != true
                ) {
                    result.add(
                        LyricLine(
                            timestampMs = currTs,
                            text = "",
                            isInstrumental = true
                        )
                    )
                }
                continue
            }

            result.add(current)
            val currTs = current.timestampMs
            if (currTs != null) {
                lastVocalTs = currTs
            }

            val immediateNext = lines.getOrNull(i + 1)
            val hasDirectVocalNext = immediateNext != null &&
                !immediateNext.isInstrumental &&
                immediateNext.text.isNotBlank()
            val nextTs = immediateNext?.timestampMs
            if (hasDirectVocalNext &&
                currTs != null &&
                nextTs != null &&
                (nextTs - currTs) > INTERLUDE_GAP_THRESHOLD_MS
            ) {
                val estimatedVocalMs = estimateVocalDurationMs(current.text)
                val interludeStartTs = currTs + estimatedVocalMs
                val remainingFreeMs = nextTs - interludeStartTs
                if (remainingFreeMs >= MIN_INSTRUMENTAL_DURATION_MS) {
                    result.add(
                        LyricLine(
                            timestampMs = interludeStartTs,
                            text = "",
                            isInstrumental = true
                        )
                    )
                }
            }
        }

        return result
    }

    internal fun estimateVocalDurationMs(text: String): Long {
        val charCount = text.trim().length
        return maxOf(MIN_ESTIMATED_VOCAL_MS, charCount * MS_PER_VOCAL_CHAR)
    }

    private const val INTRO_INSTRUMENTAL_THRESHOLD_MS = 4_000L
    private const val MIN_INSTRUMENTAL_DURATION_MS = 5_000L
    private const val INTERLUDE_GAP_THRESHOLD_MS = 8_000L
    private const val MIN_ESTIMATED_VOCAL_MS = 2_500L
    private const val MS_PER_VOCAL_CHAR = 65L

    /**
     * Detects bilingual `.lrc` files where two consecutive lines share the exact same timestamp
     * (e.g., original line followed by its human translation) and merges them cleanly.
     */
    private fun mergeBilingualSyncedLines(sorted: List<LyricLine>): Pair<List<LyricLine>, Boolean> {
        if (sorted.size < 4) return sorted to false

        val grouped = mutableListOf<MutableList<LyricLine>>()
        for (line in sorted) {
            val lastGroup = grouped.lastOrNull()
            if (lastGroup != null && lastGroup.first().timestampMs == line.timestampMs) {
                lastGroup.add(line)
            } else {
                grouped.add(mutableListOf(line))
            }
        }

        val vocalGroups = grouped.filter { group -> group.any { !it.isInstrumental && it.text.isNotBlank() } }
        val pairedCount = vocalGroups.count { group ->
            val distinctTexts = group.filter { !it.isInstrumental && it.text.isNotBlank() }
                .map { it.text.trim() }
            distinctTexts.size >= 2 && !distinctTexts[0].equals(distinctTexts[1], ignoreCase = true)
        }
        val isBilingual = vocalGroups.isNotEmpty() &&
            pairedCount >= 2 &&
            (pairedCount.toFloat() / vocalGroups.size.toFloat()) >= 0.3f
        if (!isBilingual) {
            val deduped = grouped.map { group ->
                group.firstOrNull { !it.isInstrumental && it.text.isNotBlank() } ?: group.first()
            }
            return deduped to false
        }

        val merged = grouped.map { group ->
            val vocalEntries = group.filter { !it.isInstrumental && it.text.isNotBlank() }
            if (vocalEntries.isEmpty()) {
                group.first().copy(text = "", secondaryText = null, isInstrumental = true)
            } else {
                val primary = vocalEntries.first()
                val secondary = vocalEntries.drop(1)
                    .map { it.text.trim() }
                    .filter { it.isNotEmpty() && !it.equals(primary.text, ignoreCase = true) }
                    .joinToString(" / ")
                    .ifEmpty { null }
                primary.copy(secondaryText = secondary)
            }
        }
        return merged to true
    }

    /**
     * Aligns pasted/edited translated lyrics (either synced `.lrc` or plain text copied from
     * Letras.com / LyricsTranslate) onto the original track's [LyricsData] lines and timestamps.
     */
    fun alignTranslationLines(
        original: LyricsData,
        rawTranslatedText: String,
        sourceLabel: String = MANUAL_TRANSLATION_SOURCE_LABEL
    ): LyricsData? {
        val cleaned = rawTranslatedText.removePrefix("\uFEFF").replace("\r\n", "\n").trim()
        if (cleaned.isEmpty() || original.lines.isEmpty()) return null

        val parsedCandidate = parseLrcText(cleaned, sourceLabel)
        val alignedLines: List<LyricLine> = if (parsedCandidate != null && parsedCandidate.isSynced && original.isSynced) {
            val byTimestamp = parsedCandidate.lines
                .filter { !it.isInstrumental && it.text.isNotBlank() }
                .associateBy { it.timestampMs }
            val candidateVocal = parsedCandidate.lines.filter { !it.isInstrumental && it.text.isNotBlank() }
            var vocalCursor = 0
            original.lines.map { origLine ->
                if (origLine.isInstrumental || origLine.text.isBlank()) {
                    LyricLine(timestampMs = origLine.timestampMs, text = "", isInstrumental = origLine.isInstrumental)
                } else {
                    val matchedText = byTimestamp[origLine.timestampMs]?.text
                        ?: candidateVocal.getOrNull(vocalCursor)?.text
                        ?: origLine.text
                    vocalCursor++
                    LyricLine(timestampMs = origLine.timestampMs, text = matchedText)
                }
            }
        } else {
            val rawLines = cleaned.lines().map { it.trim() }
            // Preserve exact 1-to-1 line count if it already matches original lines (e.g., saved Room cache)
            if (rawLines.size == original.lines.size) {
                original.lines.mapIndexed { index, origLine ->
                    if (origLine.isInstrumental) {
                        LyricLine(timestampMs = origLine.timestampMs, text = "", isInstrumental = true)
                    } else {
                        LyricLine(
                            timestampMs = origLine.timestampMs,
                            text = rawLines[index].ifEmpty { origLine.text }
                        )
                    }
                }
            } else {
                val nonEmptyTranslated = rawLines.filter { it.isNotEmpty() }
                if (nonEmptyTranslated.isEmpty()) return null
                var cursor = 0
                original.lines.map { origLine ->
                    if (origLine.isInstrumental || origLine.text.isBlank()) {
                        LyricLine(
                            timestampMs = origLine.timestampMs,
                            text = "",
                            isInstrumental = origLine.isInstrumental
                        )
                    } else {
                        val next = nonEmptyTranslated.getOrNull(cursor)?.also { cursor++ }
                        LyricLine(timestampMs = origLine.timestampMs, text = next ?: origLine.text)
                    }
                }
            }
        }

        return LyricsData(
            lines = alignedLines,
            isSynced = original.isSynced,
            sourceLabel = sourceLabel
        )
    }

    fun serializeTranslationLines(translated: LyricsData): String {
        return translated.lines.joinToString(separator = "\n") {
            if (it.isInstrumental) "" else it.text.replace("\n", " ").trim()
        }
    }

    fun formatLrcTimestamp(timestampMs: Long): String {
        val safeMs = timestampMs.coerceAtLeast(0L)
        val totalSeconds = safeMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        val centis = (safeMs % 1000L) / 10L
        return String.format("[%02d:%02d.%02d]", minutes, seconds, centis)
    }

    fun exportLyricsToLrc(
        trackTitle: String,
        trackArtist: String,
        trackAlbum: String,
        lyrics: LyricsData,
        translatedLyrics: LyricsData? = null,
        includeTranslation: Boolean = false
    ): String {
        val sb = StringBuilder()
        if (trackTitle.isNotBlank()) sb.appendLine("[ti:${trackTitle.trim()}]")
        if (trackArtist.isNotBlank()) sb.appendLine("[ar:${trackArtist.trim()}]")
        if (trackAlbum.isNotBlank()) sb.appendLine("[al:${trackAlbum.trim()}]")
        sb.appendLine("[by:Davoo Player]")

        val offsetMs = lyrics.offsetMs
        val transLines = translatedLyrics?.lines
        lyrics.lines.forEachIndexed { idx, line ->
            val cleanText = line.text.trim()
            if (line.isInstrumental || cleanText.isEmpty()) return@forEachIndexed
            val translated = if (includeTranslation) {
                (transLines?.getOrNull(idx)?.text ?: line.secondaryText)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && !it.equals(cleanText, ignoreCase = true) }
            } else null

            val ts = line.timestampMs
            if (lyrics.isSynced && ts != null) {
                val adjustedMs = (ts - offsetMs).coerceAtLeast(0L)
                val tag = formatLrcTimestamp(adjustedMs)
                sb.appendLine("$tag$cleanText")
                if (translated != null) {
                    sb.appendLine("$tag$translated")
                }
            } else {
                sb.appendLine(cleanText)
                if (translated != null) {
                    sb.appendLine(translated)
                }
            }
        }
        return sb.toString().trimEnd() + "\n"
    }

    fun buildSafeLrcFileName(artist: String, title: String): String {
        val raw = listOf(artist.trim(), title.trim())
            .filter { it.isNotBlank() }
            .joinToString(" - ")
            .ifBlank { "letra" }
        val sanitized = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).trimEnd('.')
        val base = sanitized.ifBlank { "letra" }
        return "$base.lrc"
    }

    fun extractLyricsForTrack(context: Context, track: AudioTrack): LyricsData? {
        // 1. Check companion .lrc file on filesystem if path is known
        if (track.filePath.isNotBlank()) {
            val audioFile = File(track.filePath)
            val parent = audioFile.parentFile
            if (parent != null) {
                val lrcFile = File(parent, "${audioFile.nameWithoutExtension}.lrc")
                if (lrcFile.exists() && lrcFile.canRead()) {
                    val content = runCatching { lrcFile.readText(StandardCharsets.UTF_8) }.getOrNull()
                    if (!content.isNullOrBlank()) {
                        parseLrcText(content, "Archivo .lrc local")?.let { return it }
                    }
                }
            }
        }

        // 2. Scan embedded Vorbis comments (FLAC) or ID3v2 USLT/SYLT/TXXX (MP3)
        val embeddedText = extractEmbeddedTextTag(context, Uri.parse(track.contentUri))
        if (!embeddedText.isNullOrBlank()) {
            parseLrcText(embeddedText, "Etiqueta incrustada")?.let { return it }
        }
        return null
    }

    fun extractReplayGainDb(context: Context, uri: Uri): Float? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(16 * 1024)
                val read = stream.read(header)
                if (read <= 0) return@use null
                val text = String(header, 0, read, StandardCharsets.ISO_8859_1)
                val keyIdx = listOf(
                    "REPLAYGAIN_TRACK_GAIN",
                    "REPLAYGAIN_ALBUM_GAIN",
                    "replaygain_track_gain"
                ).firstNotNullOfOrNull { key ->
                    val idx = text.indexOf(key)
                    if (idx >= 0) idx else null
                } ?: return@use null
                val snippet = text.substring(keyIdx, (keyIdx + 96).coerceAtMost(text.length))
                replayGainRegex.find(snippet)?.groupValues?.getOrNull(1)?.toFloatOrNull()
            }
        }.getOrNull()
    }

    private fun extractEmbeddedTextTag(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(128 * 1024)
                val read = stream.read(buffer)
                if (read <= 0) return@use null
                val latin = String(buffer, 0, read, StandardCharsets.ISO_8859_1)

                // FLAC Vorbis comment: LYRICS=... or UNSYNCEDLYRICS=...
                val vorbisKeys = listOf("LYRICS=", "UNSYNCEDLYRICS=", "lyrics=", "unsyncedlyrics=")
                for (key in vorbisKeys) {
                    val idx = latin.indexOf(key)
                    if (idx >= 4) {
                        val lenOffset = idx - 4
                        val commentLen = (buffer[lenOffset].toInt() and 0xFF) or
                            ((buffer[lenOffset + 1].toInt() and 0xFF) shl 8) or
                            ((buffer[lenOffset + 2].toInt() and 0xFF) shl 16) or
                            ((buffer[lenOffset + 3].toInt() and 0xFF) shl 24)
                        val valueStart = idx + key.length
                        val available = (read - valueStart).coerceAtLeast(0)
                        val valueLen = (commentLen - key.length).coerceIn(0, available)
                        if (valueLen > 0) {
                            return@use String(buffer, valueStart, valueLen, StandardCharsets.UTF_8).trim()
                        }
                    }
                }

                // ID3v2 USLT frame (Unsynchronised lyrics)
                val usltIdx = latin.indexOf("USLT")
                if (usltIdx >= 0 && usltIdx + 14 < read) {
                    val frameSize = ((buffer[usltIdx + 4].toInt() and 0xFF) shl 24) or
                        ((buffer[usltIdx + 5].toInt() and 0xFF) shl 16) or
                        ((buffer[usltIdx + 6].toInt() and 0xFF) shl 8) or
                        (buffer[usltIdx + 7].toInt() and 0xFF)
                    val payloadStart = usltIdx + 10
                    val payloadEnd = (payloadStart + frameSize).coerceAtMost(read)
                    if (payloadEnd > payloadStart + 4) {
                        val encoding = buffer[payloadStart].toInt() and 0xFF
                        val charset = when (encoding) {
                            1, 2 -> StandardCharsets.UTF_16
                            3 -> StandardCharsets.UTF_8
                            else -> StandardCharsets.ISO_8859_1
                        }
                        // Skip encoding(1) + language(3) + content descriptor up to null separator
                        var textStart = payloadStart + 4
                        while (textStart < payloadEnd && buffer[textStart].toInt() != 0) {
                            textStart++
                        }
                        while (textStart < payloadEnd && buffer[textStart].toInt() == 0) {
                            textStart++
                        }
                        if (textStart < payloadEnd) {
                            return@use String(buffer, textStart, payloadEnd - textStart, charset).trim()
                        }
                    }
                }
                null
            }
        }.getOrNull()
    }

    data class CueTrackEntry(
        val trackNumber: Int,
        val title: String,
        val performer: String,
        val startMs: Long
    )

    fun parseCueSheet(rawCue: String): List<CueTrackEntry> {
        val entries = mutableListOf<CueTrackEntry>()
        var currentTrackNum = 0
        var currentTitle = ""
        var currentPerformer = ""
        rawCue.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("TRACK ", ignoreCase = true) -> {
                    currentTrackNum = line.split(Regex("\\s+")).getOrNull(1)?.toIntOrNull() ?: 0
                }
                line.startsWith("TITLE ", ignoreCase = true) -> {
                    currentTitle = line.substringAfter("TITLE ").trim().trim('"')
                }
                line.startsWith("PERFORMER ", ignoreCase = true) -> {
                    currentPerformer = line.substringAfter("PERFORMER ").trim().trim('"')
                }
                line.startsWith("INDEX 01 ", ignoreCase = true) && currentTrackNum > 0 -> {
                    val tc = line.substringAfter("INDEX 01 ").trim()
                    val parts = tc.split(":")
                    if (parts.size == 3) {
                        val mm = parts[0].toLongOrNull() ?: 0L
                        val ss = parts[1].toLongOrNull() ?: 0L
                        val ff = parts[2].toLongOrNull() ?: 0L // 75 frames/sec in CUE standard
                        val ms = mm * 60_000L + ss * 1_000L + (ff * 1_000L / 75L)
                        entries.add(
                            CueTrackEntry(
                                trackNumber = currentTrackNum,
                                title = currentTitle.ifBlank { "Pista $currentTrackNum" },
                                performer = currentPerformer,
                                startMs = ms
                            )
                        )
                    }
                }
            }
        }
        return entries
    }

    fun exportPlaylistToM3u(playlistName: String, tracks: List<AudioTrack>): String {
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#PLAYLIST:$playlistName")
        tracks.forEach { track ->
            val seconds = (track.durationMs / 1000L).coerceAtLeast(0L)
            sb.appendLine("#EXTINF:$seconds,${track.artist} - ${track.title}")
            sb.appendLine(track.filePath.ifBlank { track.contentUri })
        }
        return sb.toString()
    }

    fun parseM3uPaths(rawM3u: String): List<String> {
        return rawM3u.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
    }
}
