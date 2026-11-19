package com.example.model

enum class AudioFormat(val label: String) {
    FLAC("FLAC"),
    MP3("MP3"),
    WAV("WAV"),
    AAC("AAC"),
    OGG("OGG")
}

enum class LibraryFilter(val label: String) {
    ALL("Todas"),
    FLAC("FLAC"),
    MP3("MP3"),
    OTHER("Otros")
}

enum class SortOrder(val label: String) {
    RECENT("Recientes"),
    TITLE("Título"),
    ARTIST("Artista"),
    DURATION("Duración"),
    MOST_PLAYED("Más escuchadas")
}

enum class CollectionTab(val label: String) {
    ALBUMS("Álbumes"),
    ARTISTS("Artistas"),
    GENRES("Géneros"),
    FOLDERS("Carpetas")
}

enum class SmartPlaylistType(val title: String, val subtitle: String) {
    FAVORITES("Favoritas", "Canciones marcadas con corazón"),
    RECENTLY_ADDED("Añadidas recientemente", "Últimas incorporaciones a tu biblioteca"),
    MOST_PLAYED("Más reproducidas", "Tus temas más escuchados"),
    HISTORY("Historial reciente", "Reproducidas últimamente")
}

data class AudioTrack(
    val id: String,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtUri: String?,
    val durationMs: Long,
    val format: AudioFormat,
    val mimeType: String,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val folderName: String,
    val isSafImported: Boolean = false,
    val albumArtist: String = "",
    val genre: String = "",
    val year: Int = 0,
    val trackNumber: Int = 0,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L,
    val replayGainDb: Float? = null,
    val filePath: String = "",
    val isDocumentTreeTrack: Boolean = false
)

data class AudioTechSpecs(
    val sampleRateHz: Int? = null,
    val bitrateBps: Int? = null,
    val channelCount: Int? = null,
    val bitsPerSample: Int? = null
) {
    fun formatBadge(format: AudioFormat): String {
        val parts = mutableListOf(format.label)
        if (format == AudioFormat.FLAC || format == AudioFormat.WAV) {
            parts.add("LOSSLESS")
        }
        bitsPerSample?.let { bits ->
            if (bits > 0) parts.add("${bits}-bit")
        }
        sampleRateHz?.let { hz ->
            if (hz > 0) {
                val khz = hz / 1000.0
                val khzStr = if (khz % 1.0 == 0.0) "${khz.toInt()} kHz" else String.format("%.1f kHz", khz)
                parts.add(khzStr)
            }
        }
        if (format == AudioFormat.MP3 || format == AudioFormat.AAC || format == AudioFormat.OGG) {
            bitrateBps?.let { bps ->
                if (bps > 0) parts.add("${bps / 1000} kbps")
            }
        }
        channelCount?.let { ch ->
            when (ch) {
                1 -> parts.add("Mono")
                2 -> parts.add("Estéreo")
                else -> if (ch > 2) parts.add("${ch}ch")
            }
        }
        return parts.joinToString(" · ")
    }
}

data class AudioTrackMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val year: Int,
    val trackNumber: Int,
    val durationMs: Long,
    val format: AudioFormat,
    val mimeType: String,
    val sizeBytes: Long,
    val folderName: String,
    val hasEmbeddedArtwork: Boolean,
    val techSpecs: AudioTechSpecs,
    val replayGainDb: Float? = null
)

data class AlbumGroup(
    val albumName: String,
    val artist: String,
    val albumArtUri: String?,
    val tracks: List<AudioTrack>,
    val totalDurationMs: Long,
    val hasFlac: Boolean,
    val year: Int? = null,
    val genre: String? = null
)

data class ArtistGroup(
    val artistName: String,
    val albumCount: Int,
    val tracks: List<AudioTrack>,
    val albumArtUri: String?,
    val totalDurationMs: Long
)

data class GenreGroup(
    val genreName: String,
    val tracks: List<AudioTrack>,
    val totalDurationMs: Long
)

data class FolderGroup(
    val folderName: String,
    val tracks: List<AudioTrack>,
    val totalDurationMs: Long,
    val isExcluded: Boolean = false
)

data class SdCardFolder(
    val treeUri: String,
    val displayName: String,
    val addedAtMs: Long = 0L,
    val trackCount: Int = 0
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val description: String,
    val createdAt: Long,
    val tracks: List<AudioTrack>
) {
    val trackCount: Int get() = tracks.size
    val totalDurationMs: Long get() = tracks.sumOf { it.durationMs }
    val artworkUris: List<String> get() = tracks.mapNotNull { it.albumArtUri }.distinct().take(4)
    val hasFlac: Boolean get() = tracks.any { it.format == AudioFormat.FLAC }
}

data class LyricLine(
    val timestampMs: Long?,
    val text: String,
    val secondaryText: String? = null
)

data class LyricsData(
    val lines: List<LyricLine>,
    val isSynced: Boolean,
    val sourceLabel: String,
    val hasEmbeddedTranslation: Boolean = false
)

enum class EqPreset(val label: String, val gainsMb: List<Int>) {
    FLAT("Plano", listOf(0, 0, 0, 0, 0)),
    ROCK("Rock", listOf(450, 250, -100, 300, 500)),
    JAZZ("Jazz", listOf(350, 150, -150, 250, 400)),
    CLASSICAL("Clásica", listOf(400, 200, -100, 200, 450)),
    VOCAL("Vocal", listOf(-200, 100, 450, 350, -100)),
    BASS("Graves+", listOf(600, 400, 100, 0, 0)),
    CUSTOM("Personalizado", listOf(0, 0, 0, 0, 0))
}

data class AudioSettings(
    val eqEnabled: Boolean = false,
    val eqPreset: EqPreset = EqPreset.FLAT,
    val bandGainsMb: List<Int> = listOf(0, 0, 0, 0, 0),
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val crossfadeSeconds: Int = 0,
    val gaplessEnabled: Boolean = true,
    val replayGainEnabled: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val stereoBalance: Float = 0f,
    val dynamicArtworkColors: Boolean = true
)

data class EqBandInfo(
    val index: Int,
    val centerFreqHz: Int,
    val minLevelMb: Int,
    val maxLevelMb: Int,
    val currentLevelMb: Int
)

enum class ThemeMode(val label: String) {
    OBSIDIAN("Obsidiana"),
    AMOLED("OLED"),
    LIGHT("Marfil"),
    SYSTEM("Sistema")
}

enum class AccentPalette(val label: String) {
    COPPER("Ámbar"),
    CYAN("Cian"),
    EMERALD("Esmeralda"),
    CRIMSON("Carmesí"),
    VIOLET("Violeta")
}

enum class ArtworkStyle(val label: String) {
    CARD("Tarjeta"),
    VINYL("Vinilo")
}

enum class ListDensity(val label: String) {
    COMFORTABLE("Cómoda"),
    COMPACT("Compacta")
}

data class UiPreferences(
    val themeMode: ThemeMode = ThemeMode.OBSIDIAN,
    val accentPalette: AccentPalette = AccentPalette.COPPER,
    val artworkStyle: ArtworkStyle = ArtworkStyle.CARD,
    val listDensity: ListDensity = ListDensity.COMFORTABLE,
    val albumGridColumns: Int = 2,
    val showFormatBadges: Boolean = true,
    val ambientGlow: Boolean = true,
    val animatedVisualizers: Boolean = true
)

