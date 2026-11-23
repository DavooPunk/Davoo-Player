package com.example.model

import androidx.compose.runtime.Immutable

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

@Immutable
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
    val isDocumentTreeTrack: Boolean = false,
    val sourceTreeUri: String? = null
)

@Immutable
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

@Immutable
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

@Immutable
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

@Immutable
data class ArtistGroup(
    val artistName: String,
    val albumCount: Int,
    val tracks: List<AudioTrack>,
    val albumArtUri: String?,
    val totalDurationMs: Long
)

@Immutable
data class GenreGroup(
    val genreName: String,
    val tracks: List<AudioTrack>,
    val totalDurationMs: Long
)

@Immutable
data class FolderGroup(
    val folderName: String,
    val tracks: List<AudioTrack>,
    val totalDurationMs: Long,
    val isExcluded: Boolean = false
)

@Immutable
data class SdCardFolder(
    val treeUri: String,
    val displayName: String,
    val addedAtMs: Long = 0L,
    val trackCount: Int = 0
)

@Immutable
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

@Immutable
data class LyricLine(
    val timestampMs: Long?,
    val text: String,
    val secondaryText: String? = null,
    val isInstrumental: Boolean = false
)

@Immutable
data class LyricsData(
    val lines: List<LyricLine>,
    val isSynced: Boolean,
    val sourceLabel: String,
    val hasEmbeddedTranslation: Boolean = false,
    val offsetMs: Long = 0L
)

val TEN_BAND_CENTERS_HZ = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

enum class EqPreset(val label: String, val gainsMb: List<Int>, val recommendedPreampDb: Float = 0f) {
    FLAT("Plano", List(10) { 0 }, 0f),
    BASS("Graves+", listOf(650, 550, 380, 150, 0, 0, 0, 100, 150, 150), -4.5f),
    VOCAL("Vocal", listOf(-300, -200, -100, 150, 350, 480, 450, 250, 50, -150), -3.0f),
    ROCK("Rock", listOf(450, 350, 200, -100, -150, 50, 250, 400, 500, 450), -3.5f),
    JAZZ("Jazz", listOf(350, 250, 150, 100, -150, -100, 150, 250, 350, 400), -2.5f),
    CLASSICAL("Clásica", listOf(400, 300, 150, 50, -100, -50, 150, 300, 400, 450), -3.0f),
    ELECTRONIC("Electrónica", listOf(550, 450, 250, 0, -150, 100, 200, 350, 450, 500), -4.0f),
    HIP_HOP("Hip-Hop", listOf(580, 620, 340, -120, -180, 0, 180, 320, 360, 280), -4.8f),
    V_SHAPE("V-Shape", listOf(550, 450, 200, -150, -300, -250, 100, 350, 550, 500), -4.0f),
    CUSTOM("Personalizado", List(10) { 0 }, 0f);

    companion object {
        fun normalizeToTenBands(gains: List<Int>): List<Int> {
            return when {
                gains.size >= 10 -> gains.take(10).map { it.coerceIn(-1200, 1200) }
                gains.size == 5 -> listOf(
                    gains[0],
                    ((gains[0] + gains[1]) / 2),
                    gains[1],
                    ((gains[1] + gains[2]) / 2),
                    gains[2],
                    ((gains[2] + gains[3]) / 2),
                    gains[3],
                    ((gains[3] + gains[4]) / 2),
                    gains[4],
                    gains[4]
                ).map { it.coerceIn(-1200, 1200) }
                gains.isEmpty() -> List(10) { 0 }
                else -> List(10) { idx -> gains.getOrElse(idx) { 0 }.coerceIn(-1200, 1200) }
            }
        }
    }
}

@Immutable
data class HeadphoneEqProfile(
    val id: String,
    val brand: String,
    val model: String,
    val formFactor: String,
    val preampDb: Float,
    val bandGainsMb: List<Int>,
    val isCustom: Boolean = false
) {
    val fullName: String get() = if (brand.isBlank()) model else "$brand $model"
}

object AutoEqProfilesCatalog {
    val builtInProfiles: List<HeadphoneEqProfile> = listOf(
        HeadphoneEqProfile(
            id = "autoeq_sony_xm4",
            brand = "Sony",
            model = "WH-1000XM4",
            formFactor = "Over-Ear ANC",
            preampDb = -4.8f,
            bandGainsMb = listOf(-150, -450, -520, -280, 120, 240, 320, 480, 360, 180)
        ),
        HeadphoneEqProfile(
            id = "autoeq_sony_xm5",
            brand = "Sony",
            model = "WH-1000XM5",
            formFactor = "Over-Ear ANC",
            preampDb = -4.2f,
            bandGainsMb = listOf(-100, -380, -450, -220, 140, 260, 310, 420, 300, 150)
        ),
        HeadphoneEqProfile(
            id = "autoeq_airpods_pro_2",
            brand = "Apple",
            model = "AirPods Pro (2.ª gen)",
            formFactor = "TWS In-Ear",
            preampDb = -3.4f,
            bandGainsMb = listOf(180, 120, -120, -180, 60, 220, 340, 260, 180, 220)
        ),
        HeadphoneEqProfile(
            id = "autoeq_galaxy_buds2_pro",
            brand = "Samsung",
            model = "Galaxy Buds2 Pro",
            formFactor = "TWS In-Ear",
            preampDb = -3.2f,
            bandGainsMb = listOf(120, 40, -150, -120, 140, 240, 180, -140, -220, 150)
        ),
        HeadphoneEqProfile(
            id = "autoeq_sennheiser_hd600",
            brand = "Sennheiser",
            model = "HD 600",
            formFactor = "Open-Back Estudio",
            preampDb = -5.6f,
            bandGainsMb = listOf(560, 420, 180, -80, -140, 40, -120, 180, 260, 320)
        ),
        HeadphoneEqProfile(
            id = "autoeq_ath_m50x",
            brand = "Audio-Technica",
            model = "ATH-M50x",
            formFactor = "Estudio Cerrado",
            preampDb = -4.4f,
            bandGainsMb = listOf(220, -180, -320, -120, 180, 280, 440, 180, -240, 160)
        ),
        HeadphoneEqProfile(
            id = "autoeq_kz_zsn_pro",
            brand = "KZ",
            model = "ZSN Pro (IEM)",
            formFactor = "IEM Híbrido",
            preampDb = -3.8f,
            bandGainsMb = listOf(160, -120, -240, -100, 180, 260, -280, -360, -220, 180)
        ),
        HeadphoneEqProfile(
            id = "autoeq_jbl_tune_720bt",
            brand = "JBL",
            model = "Tune 720BT",
            formFactor = "Over-Ear Bluetooth",
            preampDb = -3.6f,
            bandGainsMb = listOf(120, -220, -340, -160, 120, 240, 360, 280, 180, 140)
        ),
        HeadphoneEqProfile(
            id = "autoeq_bose_qc_ultra",
            brand = "Bose",
            model = "QuietComfort / QC Ultra",
            formFactor = "Over-Ear ANC",
            preampDb = -4.0f,
            bandGainsMb = listOf(-80, -280, -360, -140, 160, 280, 350, 400, 240, 120)
        ),
        HeadphoneEqProfile(
            id = "autoeq_neutral_iem",
            brand = "Harman",
            model = "IEM Neutro Referencia",
            formFactor = "Target Neutro",
            preampDb = -1.5f,
            bandGainsMb = listOf(150, 100, 0, -50, 0, 80, 150, 120, 50, 0)
        )
    )
}

@Immutable
data class AudioSettings(
    val eqEnabled: Boolean = false,
    val eqPreset: EqPreset = EqPreset.FLAT,
    val bandGainsMb: List<Int> = List(10) { 0 },
    val preampDb: Float = 0f,
    val activeProfileId: String? = null,
    val activeProfileName: String? = null,
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val crossfadeSeconds: Int = 0,
    val gaplessEnabled: Boolean = true,
    val replayGainEnabled: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val stereoBalance: Float = 0f,
    val dynamicArtworkColors: Boolean = true
)

@Immutable
data class EqBandInfo(
    val index: Int,
    val centerFreqHz: Int,
    val minLevelMb: Int,
    val maxLevelMb: Int,
    val currentLevelMb: Int
)

enum class AppThemeMode(val label: String, val description: String) {
    DARK("Oscuro", "Tema oscuro clásico (por defecto)"),
    LIGHT("Claro", "Superficies claras de alto contraste"),
    SYSTEM("Automático", "Sigue el tema claro u oscuro del sistema")
}

enum class ThemePreset(
    val label: String,
    val primaryPreviewArgb: Long,
    val secondaryPreviewArgb: Long,
    val darkBgPreviewArgb: Long
) {
    COPPER_AMBER("Ámbar Cobre", 0xFFF5A623L, 0xFF38BDF8L, 0xFF0C0E13L),
    LOSSLESS_CYAN("Cian Lossless", 0xFF38BDF8L, 0xFFF5A623L, 0xFF091118L),
    EMERALD("Esmeralda", 0xFF10B981L, 0xFF34D399L, 0xFF091310L),
    NEON_VIOLET("Violeta Neón", 0xFFA855F7L, 0xFF22D3EEL, 0xFF100C18L),
    CRIMSON("Carmesí", 0xFFF43F5EL, 0xFFFB923CL, 0xFF150B0FL),
    MONOCHROME("Monocromo", 0xFFE5E7EBL, 0xFF9CA3AFL, 0xFF0A0A0CL),
    CUSTOM("Personalizado", 0xFFF5A623L, 0xFF38BDF8L, 0xFF0C0E13L)
}

enum class CustomizableColorSlot(val label: String, val description: String) {
    PRIMARY("Primario / Acento", "Botones principales, sliders y resaltado activo"),
    SECONDARY("Secundario", "Etiquetas lossless, indicadores y acentos DSP"),
    BACKGROUND("Fondo Principal", "Lienzo base de las pantallas principales"),
    SURFACE("Tarjetas / Superficies", "Contenedores, listas y menús de opciones"),
    TEXT("Texto Principal", "Tipografía principal (con contraste WCAG)")
}

@Immutable
data class CustomThemeColors(
    val primaryArgb: Long = 0xFFF5A623L,
    val secondaryArgb: Long = 0xFF38BDF8L,
    val backgroundArgb: Long = 0xFF0C0E13L,
    val surfaceArgb: Long = 0xFF14171FL,
    val textArgb: Long? = null
)

@Immutable
data class AppearancePreferences(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val preset: ThemePreset = ThemePreset.COPPER_AMBER,
    val customColors: CustomThemeColors = CustomThemeColors(),
    val dynamicArtworkColors: Boolean = true
)

