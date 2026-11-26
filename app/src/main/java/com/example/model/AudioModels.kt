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
        // KZ (Knowledge Zenith) IEMs
        HeadphoneEqProfile(
            id = "autoeq_kz_edx",
            brand = "KZ",
            model = "EDX",
            formFactor = "IEM Dinámico",
            preampDb = -4.2f,
            bandGainsMb = listOf(-120, -180, -240, -140, 110, 240, -180, -320, -190, 140)
        ),
        HeadphoneEqProfile(
            id = "autoeq_kz_edx_pro",
            brand = "KZ",
            model = "EDX Pro",
            formFactor = "IEM Dinámico",
            preampDb = -4.6f,
            bandGainsMb = listOf(-140, -220, -290, -150, 130, 280, -240, -380, -220, 160)
        ),
        HeadphoneEqProfile(
            id = "autoeq_kz_edx_pro_x",
            brand = "KZ",
            model = "EDX Pro X",
            formFactor = "IEM Dinámico",
            preampDb = -4.1f,
            bandGainsMb = listOf(-80, -150, -210, -110, 120, 260, -190, -300, -160, 150)
        ),
        HeadphoneEqProfile(
            id = "autoeq_kz_edx_pro_2",
            brand = "KZ",
            model = "EDX Pro 2",
            formFactor = "IEM Dinámico",
            preampDb = -3.9f,
            bandGainsMb = listOf(-60, -120, -180, -90, 110, 230, -160, -260, -130, 120)
        ),
        HeadphoneEqProfile(
            id = "autoeq_kz_castor_harman",
            brand = "KZ",
            model = "Castor (Harman)",
            formFactor = "IEM Dual Dinámico",
            preampDb = -3.5f,
            bandGainsMb = listOf(60, 40, -90, -110, 90, 210, 120, -140, -80, 100)
        ),
        HeadphoneEqProfile(
            id = "autoeq_kz_castor_bass",
            brand = "KZ",
            model = "Castor (Bass)",
            formFactor = "IEM Dual Dinámico",
            preampDb = -4.5f,
            bandGainsMb = listOf(-260, -320, -240, -80, 140, 270, 160, -120, -90, 110)
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
            id = "autoeq_kz_zsn_pro_x",
            brand = "KZ",
            model = "ZSN Pro X",
            formFactor = "IEM Híbrido",
            preampDb = -4.4f,
            bandGainsMb = listOf(80, -160, -260, -120, 160, 270, -240, -410, -260, 170)
        ),
        HeadphoneEqProfile(
            id = "autoeq_kz_zs10_pro",
            brand = "KZ",
            model = "ZS10 Pro",
            formFactor = "IEM 5 Drivers",
            preampDb = -4.0f,
            bandGainsMb = listOf(120, -140, -220, -90, 150, 250, -190, -340, -210, 160)
        ),
        HeadphoneEqProfile(
            id = "autoeq_kz_zs10_pro_x",
            brand = "KZ",
            model = "ZS10 Pro X",
            formFactor = "IEM 5 Drivers",
            preampDb = -3.7f,
            bandGainsMb = listOf(90, -90, -180, -80, 120, 220, -140, -290, -180, 150)
        ),
        HeadphoneEqProfile(
            id = "autoeq_kz_dfi",
            brand = "KZ",
            model = "D-Fi",
            formFactor = "IEM Tunable",
            preampDb = -3.2f,
            bandGainsMb = listOf(110, 50, -110, -80, 90, 190, 140, -110, -70, 90)
        ),
        HeadphoneEqProfile(
            id = "autoeq_kz_pr2",
            brand = "KZ",
            model = "PR2",
            formFactor = "IEM Planar",
            preampDb = -4.9f,
            bandGainsMb = listOf(180, 100, -130, -120, 110, 240, -120, -420, -280, 120)
        ),
        // Chi-Fi IEMs populares
        HeadphoneEqProfile(
            id = "autoeq_moondrop_chu",
            brand = "Moondrop",
            model = "Chu",
            formFactor = "IEM Dinámico",
            preampDb = -3.6f,
            bandGainsMb = listOf(220, 160, 40, -60, 60, 130, -160, -260, -140, 100)
        ),
        HeadphoneEqProfile(
            id = "autoeq_moondrop_chu_2",
            brand = "Moondrop",
            model = "Chu II",
            formFactor = "IEM Dinámico",
            preampDb = -2.8f,
            bandGainsMb = listOf(110, 60, -70, -60, 70, 160, 90, -90, 60, 110)
        ),
        HeadphoneEqProfile(
            id = "autoeq_moondrop_aria",
            brand = "Moondrop",
            model = "Aria",
            formFactor = "IEM LCP",
            preampDb = -2.6f,
            bandGainsMb = listOf(120, 40, -90, -70, 80, 150, 120, -80, 40, 120)
        ),
        HeadphoneEqProfile(
            id = "autoeq_tangzu_waner",
            brand = "Tangzu",
            model = "Wan'er S.G",
            formFactor = "IEM Dinámico",
            preampDb = -2.9f,
            bandGainsMb = listOf(130, 50, -80, -60, 90, 140, 80, -120, 50, 140)
        ),
        HeadphoneEqProfile(
            id = "autoeq_7hz_salnotes_zero",
            brand = "7Hz",
            model = "Salnotes Zero",
            formFactor = "IEM Neutro",
            preampDb = -3.1f,
            bandGainsMb = listOf(210, 140, -40, -70, 80, 140, -90, -170, -60, 110)
        ),
        HeadphoneEqProfile(
            id = "autoeq_7hz_zero_2",
            brand = "7Hz",
            model = "x Crinacle Zero:2",
            formFactor = "IEM Dinámico",
            preampDb = -2.5f,
            bandGainsMb = listOf(90, 20, -100, -80, 60, 170, 130, -60, 40, 120)
        ),
        HeadphoneEqProfile(
            id = "autoeq_truthear_zero",
            brand = "Truthear",
            model = "x Crinacle Zero",
            formFactor = "IEM Dual DD",
            preampDb = -3.0f,
            bandGainsMb = listOf(140, 80, -40, 90, 100, -110, -180, -60, 90, 130)
        ),
        HeadphoneEqProfile(
            id = "autoeq_truthear_zero_red",
            brand = "Truthear",
            model = "Zero:RED",
            formFactor = "IEM Dual DD",
            preampDb = -2.4f,
            bandGainsMb = listOf(150, 100, 30, -40, 60, 110, 90, 60, 40, 110)
        ),
        HeadphoneEqProfile(
            id = "autoeq_truthear_hola",
            brand = "Truthear",
            model = "Hola",
            formFactor = "IEM Dinámico",
            preampDb = -3.1f,
            bandGainsMb = listOf(120, 40, -90, -70, 80, 190, 160, 90, 120, 160)
        ),
        HeadphoneEqProfile(
            id = "autoeq_cca_cra",
            brand = "CCA",
            model = "CRA",
            formFactor = "IEM V-Shape",
            preampDb = -4.7f,
            bandGainsMb = listOf(-180, -240, -210, -110, 140, 290, -220, -390, -240, 110)
        ),
        HeadphoneEqProfile(
            id = "autoeq_qkz_hbb",
            brand = "QKZ",
            model = "x HBB",
            formFactor = "IEM Warm Bass",
            preampDb = -4.3f,
            bandGainsMb = listOf(-210, -310, -290, -140, 130, 260, 210, 80, 120, 180)
        ),
        // Over-Ear, Estudio y TWS
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
            id = "autoeq_sony_mdr_7506",
            brand = "Sony",
            model = "MDR-7506",
            formFactor = "Estudio Cerrado",
            preampDb = -4.5f,
            bandGainsMb = listOf(280, 190, 40, -90, 60, 110, -210, -340, -240, 120)
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
            id = "autoeq_sennheiser_hd650",
            brand = "Sennheiser",
            model = "HD 650",
            formFactor = "Open-Back Estudio",
            preampDb = -5.8f,
            bandGainsMb = listOf(580, 440, 140, -120, -160, 60, 40, 240, 310, 340)
        ),
        HeadphoneEqProfile(
            id = "autoeq_philips_shp9500",
            brand = "Philips",
            model = "SHP9500",
            formFactor = "Open-Back",
            preampDb = -5.2f,
            bandGainsMb = listOf(520, 380, 190, 40, -60, 90, -80, -260, 140, 210)
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

object AutoEqGraphicEqParser {
    private val pointRegex = Regex("""(\d+(?:\.\d+)?)\s+([+-]?\d+(?:\.\d+)?)""")

    fun parseGraphicEqTxt(rawText: String, fileNameHint: String? = null): HeadphoneEqProfile? {
        if (rawText.isBlank()) return null
        val graphicLine = rawText
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("GraphicEQ:", ignoreCase = true) }
            ?: return null

        val payload = graphicLine.substringAfter(':', "").trim()
        if (payload.isBlank()) return null

        val points = payload.split(';')
            .mapNotNull { entry ->
                val match = pointRegex.find(entry.trim()) ?: return@mapNotNull null
                val hz = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                val db = match.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
                if (!hz.isFinite() || hz <= 0.0 || !db.isFinite()) return@mapNotNull null
                hz to db.coerceIn(-18.0, 18.0)
            }
            .sortedBy { it.first }

        if (points.size < 2) return null

        val gainsMb = TEN_BAND_CENTERS_HZ.map { centerHz ->
            val targetHz = centerHz.toDouble()
            val interpolatedDb = when {
                targetHz <= points.first().first -> points.first().second
                targetHz >= points.last().first -> points.last().second
                else -> {
                    val hiIdx = points.indexOfFirst { it.first >= targetHz }
                    if (hiIdx <= 0) {
                        points.first().second
                    } else {
                        val (f0, g0) = points[hiIdx - 1]
                        val (f1, g1) = points[hiIdx]
                        if (kotlin.math.abs(f1 - f0) < 1e-6) {
                            g0
                        } else {
                            val logTarget = kotlin.math.ln(targetHz)
                            val log0 = kotlin.math.ln(f0)
                            val log1 = kotlin.math.ln(f1)
                            val t = ((logTarget - log0) / (log1 - log0)).coerceIn(0.0, 1.0)
                            g0 + (g1 - g0) * t
                        }
                    }
                }
            }
            (interpolatedDb * 100.0).toInt().coerceIn(-1200, 1200)
        }

        val maxPeakDb = (gainsMb.maxOrNull() ?: 0) / 100f
        val safePreampDb = if (maxPeakDb > 0f) {
            (-kotlin.math.ceil((maxPeakDb * 10f).toDouble()).toFloat() / 10f).coerceIn(-12f, 0f)
        } else {
            0f
        }

        val cleanName = fileNameHint
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.replace("GraphicEQ", "", ignoreCase = true)
            ?.replace('_', ' ')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "AutoEq Importado"

        val idSlug = cleanName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { System.currentTimeMillis().toString() }

        return HeadphoneEqProfile(
            id = "autoeq_import_$idSlug",
            brand = "AutoEq .txt",
            model = cleanName,
            formFactor = "GraphicEQ 10B",
            preampDb = safePreampDb,
            bandGainsMb = gainsMb,
            isCustom = true
        )
    }
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
    val replayGainPreampDb: Float = 3.0f,
    val replayGainUntaggedPreampDb: Float = -4.0f,
    val playbackSpeed: Float = 1.0f,
    val stereoBalance: Float = 0f,
    val dynamicArtworkColors: Boolean = true,
    val fadeOnPauseEnabled: Boolean = false
)

@Immutable
data class ReplayGainScanUiState(
    val isVisible: Boolean = false,
    val isRunning: Boolean = false,
    val completed: Boolean = false,
    val onlyMissing: Boolean = true,
    val totalCount: Int = 0,
    val processedCount: Int = 0,
    val analyzedCount: Int = 0,
    val skippedCount: Int = 0,
    val failedCount: Int = 0,
    val cachedTracksCount: Int = 0,
    val currentTrackLabel: String = ""
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

