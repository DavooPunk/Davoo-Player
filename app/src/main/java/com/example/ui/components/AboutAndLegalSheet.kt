package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Copyright
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig

private enum class AboutSheetSection(val label: String, val tag: String) {
    ABOUT("Acerca", "about_tab_about"),
    LEGAL("Legal", "about_tab_legal"),
    LICENSES("Licencias", "about_tab_licenses"),
    CHANGELOG("Cambios", "about_tab_changelog")
}

private data class OpenSourceNotice(
    val library: String,
    val author: String,
    val license: String,
    val purpose: String
)

private data class ChangelogRelease(
    val version: String,
    val tag: String,
    val items: List<String>
)

private const val SUPPORT_EMAIL = "davidmarin2704@gmail.com"

private val openSourceNotices = listOf(
    OpenSourceNotice(
        library = "AndroidX Media3 (ExoPlayer & MediaSession)",
        author = "The Android Open Source Project / Google LLC",
        license = "Apache License 2.0",
        purpose = "Motor de reproducción de audio Hi-Res, controles multimedia y soporte Android Auto"
    ),
    OpenSourceNotice(
        library = "Jetpack Compose & Material 3",
        author = "The Android Open Source Project / Google LLC",
        license = "Apache License 2.0",
        purpose = "Interfaz declarativa Material Design 3 y diseño adaptativo"
    ),
    OpenSourceNotice(
        library = "AndroidX Room & Lifecycle",
        author = "The Android Open Source Project / Google LLC",
        license = "Apache License 2.0",
        purpose = "Base de datos local para listas, estadísticas, letras y etiquetas"
    ),
    OpenSourceNotice(
        library = "Material Design Icons / Symbols",
        author = "Google LLC",
        license = "Apache License 2.0",
        purpose = "Iconografía vectorial del reproductor"
    ),
    OpenSourceNotice(
        library = "Coil (Compose Image Loader)",
        author = "Coil Contributors",
        license = "Apache License 2.0",
        purpose = "Carga eficiente y caché en memoria de carátulas de álbumes"
    ),
    OpenSourceNotice(
        library = "OkHttp",
        author = "Square, Inc.",
        license = "Apache License 2.0",
        purpose = "Cliente HTTP ligero para descarga opcional de letras (LRCLIB), carátulas Deezer y traducción con DeepL / Google Traductor / DeepSeek AI"
    ),
    OpenSourceNotice(
        library = "Kotlin, Coroutines & Kotlinx Serialization",
        author = "JetBrains s.r.o. y colaboradores",
        license = "Apache License 2.0",
        purpose = "Lenguaje, concurrencia segura y serialización de preferencias"
    )
)

private val changelogReleases = listOf(
    ChangelogRelease(
        version = "v2.1.1",
        tag = "Actual",
        items = listOf(
            "Reproduciendo ahora mejorado con barra inferior fija estilo YT Music (Cola, Letras, Info): elimina gestos accidentales en paneles modales migrando toda la app a vistas fijas a pantalla completa o diálogos centrados, incorpora títulos deslizantes (marquee) y quita la cápsula intermedia sobre la carátula.",
            "Clips de letra estilo Spotify sin límite de versos (hasta 1:30) y guardado en el dispositivo: selecciona cualquier cantidad de versos dentro de 90 segundos con scroll vertical continuo suavizado, botón directo «Guardar» en Películas o Imágenes vía Scoped Storage y botón «Compartir» sin perder el archivo generado.",
            "Traductor DeepSeek AI multiidioma estricto: traduce íntegramente canciones con mezclas de idiomas (español/inglés, japonés/inglés, etc.) sin omitir frases extranjeras, sobrescribiendo la caché local al regenerar.",
            "Catálogo AutoEq ampliado e importador GraphicEQ .txt: más de 30 perfiles calibrados (incluyendo línea KZ EDX/Castor/ZS10/D-Fi/PR2, Moondrop Chu II, Tangzu Wan'er y Truthear Zero) más botón «Importar .txt» con interpolación logarítmica de 10 bandas y preamp seguro.",
            "Preamp de ReplayGain dedicado en Opciones (3.5 mm / DAC): deslizador independiente de Preamp (+3 dB por defecto, -6 a +12 dB) y pistas sin etiqueta (-4 dB por defecto, -12 a 0 dB) con persistencia, respaldo .json y aprovechamiento dinámico de rango para auriculares exigentes."
        )
    ),
    ChangelogRelease(
        version = "v2.1.0",
        tag = "Anterior",
        items = listOf(
            "Integración de DeepSeek AI como motor de traducción de letras: añade DeepSeek junto a DeepL y Google Traductor en Opciones > Traductor de letras y en el menú rápido del visor de letras, enviando los versos en una sola petición JSON estructurada para preservar contexto poético, modismos y marcas de tiempo .lrc (con prueba de conexión y caché offline).",
            "Auto-etiquetado desde la API pública de Deezer (sin ARL ni descargas de audio): autocompleta carátulas HD (1000×1000), álbum, artista, año y número de pista en el editor de etiquetas o por lotes para archivos MP3/FLAC locales.",
            "Descarga de letras sincronizadas (.lrc) por lotes desde LRCLIB: obtén letras con marcas de tiempo para toda tu biblioteca existente y expórtalas a carpeta si lo deseas.",
            "Compartir letras mejorado hasta 10 líneas, límite ampliado de hasta 1:30 (90 segundos), texto más grande y video 1080p: selecciona hasta 10 líneas .lrc o un fragmento de hasta 1:30 (90s), elige el tamaño de letra (Compacto a Muy grande) en imágenes y videos, y exporta MP4 en 1080p Full HD (con respaldo automático a 720p en hardware ligero).",
            "Orden de biblioteca persistente: Davoo Player recuerda automáticamente el criterio de ordenamiento elegido (título, artista, álbum, duración o más reciente) entre sesiones.",
            "Escáner de sonoridad ReplayGain (-18 dBFS) y fade suave al pausar: analiza tu música local bajo demanda desde Opciones para normalizar volumen sin modificar archivos y activa un fade suave (~180 ms) al pausar o reanudar.",
            "Telemetría de salida de audio y DAC USB: muestra en vivo la ruta activa (DAC USB, Audífonos, Bluetooth, Altavoz), frecuencias soportadas y profundidad de bits en Reproduciendo ahora y Metadatos sin permisos adicionales.",
            "Explorador jerárquico de carpetas físicas: navega por subcarpetas reales con ruta superior interactiva, reproducción completa por subcarpeta y botón Atrás en Colección > Carpetas."
        )
    ),
    ChangelogRelease(
        version = "v2.0.6",
        tag = "Anterior",
        items = listOf(
            "Compartir letras y fragmentos en Video 9:16 (.mp4) e Imagen (.png): exporta clips verticales listos para Estados de WhatsApp e Historias directamente desde el icono de compartir en la esquina superior derecha del visor inmersivo de letras.",
            "Selección inteligente de versos y tiempos: toca de 1 a 6 líneas consecutivas en letras .lrc (con offset calibrado, tope de 30s y modo Original o Bilingüe) o ajusta un fragmento de 15s a 30s con visualizador animado en pistas sin letra sincronizada.",
            "Vista previa en bucle y 3 estilos visuales: previsualiza en tiempo real con reproducción en bucle (pausando/restaurando la música principal) y elige entre Color dinámico, Oscuro ámbar o Carátula difuminada.",
            "Motor nativo compatible con FLAC Hi-Res: usa MediaCodec/MediaExtractor/MediaMuxer (H.264 720x1280 30fps + AAC 192 kbps) con conversión automática de 24/32-bit/float y 88.2/96 kHz, fade-in/fade-out suave, progreso real cancelable y limpieza de caché shared_clips.",
            "Indicador musical en interludios instrumentales: las introducciones largas, pausas instrumentales (> 8s) o líneas vacías/con puntos en archivos .lrc muestran un icono animado de nota musical (♪) interactivo para saltar al instante.",
            "Acerca de, Legal y Cambios a pantalla completa y actualizaciones por defecto: vista inmersiva sin cierres accidentales al deslizar y avisos automáticos de nuevas versiones activados de fábrica (desactivables en Opciones).",
            "Tema Oscuro, Claro y Automático + Personalización total de colores: elige entre Oscuro (por defecto), Claro o Automático del sistema con adaptación dinámica de barras del sistema, 6 paletas predefinidas y selector manual interactivo (RGB/Hex) con verificación de contraste WCAG para primario, secundario, fondo, superficie y texto.",
            "Copia de seguridad completa (.json): exporta e importa desde Opciones toda tu configuración de apariencia, colores personalizados, preferencias de audio y listas de reproducción en un archivo .json.",
            "Opciones organizadas y optimización general: controles de reproducción de audio (Crossfade, Normalizar volumen y Gapless) centralizados en Ecualizador y DSP, ajuste de colores dinámicos unificado en Apariencia y mayor fluidez en toda la interfaz."
        )
    ),
    ChangelogRelease(
        version = "v2.0.5",
        tag = "Anterior",
        items = listOf(
            "Visor de letras inmersivo a pantalla completa (estilo Spotify): elimina cierres accidentales al deslizar, añade cabecera limpia con flecha para cerrar y título/artista centrados sobre el fondo degradado dinámico sin cajas contenedoras.",
            "Navegación libre en letras: al tocar o deslizar la letra se pausa el auto-scroll por 4.5 segundos manteniendo el resaltado de la línea actual y mostrando el botón flotante «Sincronizar» para volver al instante.",
            "Ajuste fino de sincronización (±0.5s) y exportación .lrc: calibra el adelanto o retraso por canción persistente en Room y exporta/comparte archivos .lrc (original o bilingüe) con timestamps corregidos mediante SAF y FileProvider.",
            "Reanudación exacta de sesión al abrir la app: restaura en segundo plano la última canción, cola completa ordenada y milisegundo exacto siempre en estado pausado para continuar sin reproducción automática indeseada.",
            "Interfaz de reproducción más limpia y proporcionada: elimina los botones de salto de 10 segundos y ajusta la carátula y controles al alto disponible de pantalla, con gestos de deslizado en el mini reproductor y enlaces directos a Artista y Álbum.",
            "Optimización de fluidez y respuesta: desacopla el reloj de progreso de la raíz de la interfaz para evitar recomposiciones globales cada 350 ms, añade caché en memoria de sesión e IPC, limita hilos de decodificación de carátulas/duración y optimiza animaciones en GPU para eliminar tirones."
        )
    ),
    ChangelogRelease(
        version = "v2.0.4",
        tag = "Anterior",
        items = listOf(
            "Crossfade real entre canciones (0 a 12 s): transición solapada de doble canal con curva equal-power para mantener volumen constante, ecualizador/balance independientes, soporte ReplayGain y control continuo desde Ajustes y Ecualizador.",
            "Colores adaptativos en Letras y Traducción: el menú de letras, su degradado de fondo, la línea traducida activa, la barra de progreso y los menús flotantes de traducción (DeepL/Google e idiomas) y gestión .lrc ahora usan el color dinámico extraído de la carátula de la canción en reproducción.",
            "Carátulas Deezer y avatar Last.fm mejorados: búsqueda flexible tolerante a colaboraciones (artistas separados por coma, & o feat.) con respaldo en búsqueda abierta, URLs HTTPS seguras y carga de foto de perfil en user.getInfo.",
            "Ir a la canción actual y cola reorganizada: nuevo botón de ubicación en Tu Música para saltar directo a la pista en reproducción; la cola ahora fija siempre la canción actual arriba («Sonando ahora»), muestra solo las siguientes con reordenamiento arrastrando y botón rápido para volver arriba.",
            "Eliminación física real de canciones del dispositivo (individual y por lotes) compatible con Android 10–15+ (MediaStore y tarjetas SD / SAF), limpiando automáticamente la cola, listas, letras, estadísticas y biblioteca sin re-escanear.",
            "Renombrado físico y escritura real de etiquetas: edita el nombre del archivo conservando la extensión y guarda etiquetas ID3v2 (MP3), Vorbis Comment (FLAC) y MediaStore directamente en disco con refresco vía MediaScannerConnection."
        )
    ),
    ChangelogRelease(
        version = "v2.0.3",
        tag = "Anterior",
        items = listOf(
            "Integración completa con Last.fm: flujo OAuth seguro con callback davooplayer://lastfm-callback, firma MD5 nativa (api_sig) y gestión de API Key / Shared Secret en Opciones o Perfil.",
            "Nueva pestaña Perfil Last.fm con Top Canciones (7 días, 1 mes, 12 meses y siempre), Favoritas y Historial Reciente destacando la pista en reproducción actual.",
            "Enriquecimiento con API pública de Deezer: carga carátulas en alta resolución (cover_xl) y reproduce vistas previas de 30 segundos directamente en Davoo Player con caché LRU y control de concurrencia.",
            "Scrobbling en vivo automático: envía updateNowPlaying al iniciar, registra track.scrobble al superar el 50% (o 15s en previews) con aviso discreto y sincroniza Favoritas (track.love / track.unlove).",
            "Protección de memoria y batería: consultas diferidas durante el scroll y liberación automática de caché remota ante avisos de baja memoria.",
            "Corrección crítica en el buscador global: estado local síncrono en el campo de texto para evitar saltos de cursor o inversión de letras con Gboard, y filtrado asíncrono en segundo plano (Dispatchers.Default) con debounce de 150 ms y collectLatest para bibliotecas de miles de canciones."
        )
    ),
    ChangelogRelease(
        version = "v2.0.2",
        tag = "Anterior",
        items = listOf(
            "Intercambio de botones en Tu Música: botón de búsqueda (lupa) a la izquierda y botón de Opciones (tuerca) en el extremo derecho de la cabecera.",
            "Nuevo preset Hip-Hop en el ecualizador de 10 bandas con realce calibrado en subgraves/bajos (31–125 Hz) y Preamp compensado (-4.8 dB) anti-saturación.",
            "Vista de letras minimalista estilo Spotify: oculta controles superiores al cargar letra, añade menú compacto de traducción (DeepL/Google e idiomas) abajo a la izquierda, gestión LRCLIB/.lrc abajo a la derecha y barra de progreso integrada.",
            "Comprobador opcional de actualizaciones en GitHub con aviso dentro de la app, notificación nativa opcional y redirección directa al navegador externo.",
            "Optimización de memoria y CPU: visualizador de espectro con escalado GPU sin relayouts, limpieza de caché de colores de carátula ante baja memoria, bypass rápido del ecualizador y limpieza de Ajustes."
        )
    ),
    ChangelogRelease(
        version = "v2.0.1",
        tag = "Anterior",
        items = listOf(
            "Buscador global fijo en cabecera: botón de lupa siempre visible arriba a la derecha en Biblioteca, Álbumes y Playlists para filtrar canciones, álbumes, artistas y listas en tiempo real desde un único lugar.",
            "Ecualizador avanzado de 10 bandas (31 Hz a 16 kHz) con Preamp dedicado (-12 a +12 dB) y limitador suave en tiempo real mediante procesamiento PCM bicuádrico.",
            "Perfiles calibrados AutoEq para audífonos populares (Sony WH-1000XM4/XM5, AirPods Pro 2, Galaxy Buds2 Pro, Sennheiser HD 600, ATH-M50x, KZ ZSN Pro, JBL Tune, Bose QC Ultra) con buscador rápido y guardado de perfiles personalizados.",
            "Optimización al quitar carpetas: eliminación instantánea en pantalla con indicador sutil, borrado por lotes en Room sin re-escanear el resto de carpetas y limpieza asíncrona de caché de carátulas."
        )
    ),
    ChangelogRelease(
        version = "v2.0.0",
        tag = "Anterior",
        items = listOf(
            "Interfaz rediseñada y limpia: biblioteca unificada sin menús de MP3/FLAC y nuevo submenú de Opciones (icono de tuerca) para memoria, actualización, ecualizador, personalización y créditos.",
            "Traductor dual seleccionable: elige entre DeepL API v2 (con contexto musical) y Google Traductor (con limpieza de modismos), además de .lrc bilingües y traducciones humanas guardadas offline.",
            "Rendimiento ultraligero para teléfonos de bajos recursos: barra de desplazamiento lateral reescrita con aceleración GPU sin lag, carga limitada de carátulas y eliminación de librerías pesadas.",
            "Licencia de Código Abierto (MIT) y nuevo canal oficial de contacto: davidmarin2704@gmail.com."
        )
    ),
    ChangelogRelease(
        version = "v1.3.0",
        tag = "Anterior",
        items = listOf(
            "Normalizar volumen integrado directamente en el menú de Ecualizador y DSP (ReplayGain + nivelador automático activable/desactivable).",
            "Detección nativa de archivos .lrc bilingües y editor offline para traducciones humanas (Letras.com / LyricsTranslate).",
            "Soporte completo para tarjetas SD y blindaje del ecualizador contra cierres inesperados.",
            "Nueva sección Acerca de, Política de Privacidad, Términos de Uso, Descargo DMCA, Licencias Open Source y Registro de Cambios."
        )
    ),
    ChangelogRelease(
        version = "v1.2.0",
        tag = "Anterior",
        items = listOf(
            "Botón flotante de búsqueda rápida al desplazarse en bibliotecas extensas.",
            "Barra lateral con indicador alfabético para desplazamiento veloz en canciones y playlists.",
            "Selección múltiple para añadir a listas, encolar o eliminar varias canciones a la vez."
        )
    ),
    ChangelogRelease(
        version = "v1.1.0",
        tag = "Anterior",
        items = listOf(
            "Descarga y almacenamiento offline de letras sincronizadas .lrc.",
            "Optimización de batería y RAM al cerrar desde multitarea.",
            "Créditos de autoría para DavooPunk y mejoras en favoritos."
        )
    ),
    ChangelogRelease(
        version = "v1.0.0",
        tag = "Inicial",
        items = listOf(
            "Reproductor local offline MP3, FLAC, WAV, AAC y OGG basado en ExoPlayer.",
            "Ecualizador de 5 bandas, Bass Boost, Virtualizador 3D, ReplayGain y reproducción sin pausas.",
            "Editor de etiquetas, listas inteligentes y exportación/importación .m3u."
        )
    )
)

@Composable
fun CreditsBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val contentScrollState = rememberScrollState()
    var selectedSection by remember { mutableStateOf(AboutSheetSection.ABOUT) }
    var supportFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedSection) {
        contentScrollState.scrollTo(0)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 12.dp)
                    .testTag("credits_bottom_sheet"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_credits_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Cerrar créditos y legal",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Album,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                    ) {
                        Text(
                            text = "Davoo Player",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Versión ${BuildConfig.VERSION_NAME} (Comp. ${BuildConfig.VERSION_CODE}) · Audio local",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(
                        items = AboutSheetSection.entries,
                        key = { it.name },
                        contentType = { "about_tab" }
                    ) { section ->
                        val selected = selectedSection == section
                        FilterChip(
                            selected = selected,
                            onClick = { selectedSection = section },
                            label = { Text(section.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag(section.tag)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(contentScrollState)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedSection) {
                        AboutSheetSection.ABOUT -> {
                            AboutOverviewSection(
                                supportFeedback = supportFeedback,
                                onSendSupportEmail = {
                                    val launched = openSupportEmail(context)
                                    supportFeedback = if (launched) {
                                        "Abriendo cliente de correo…"
                                    } else {
                                        copyEmailToClipboard(context)
                                        "No hay app de correo instalada. Dirección copiada al portapapeles."
                                    }
                                },
                                onCopySupportEmail = {
                                    copyEmailToClipboard(context)
                                    supportFeedback = "Correo copiado: $SUPPORT_EMAIL"
                                },
                                onNavigateSection = { selectedSection = it }
                            )
                        }
                        AboutSheetSection.LEGAL -> LegalDocumentsSection()
                        AboutSheetSection.LICENSES -> IntellectualPropertySection()
                        AboutSheetSection.CHANGELOG -> ChangelogSection()
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutOverviewSection(
    supportFeedback: String?,
    onSendSupportEmail: () -> Unit,
    onCopySupportEmail: () -> Unit,
    onNavigateSection: (AboutSheetSection) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("credits_davoopunk")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(11.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "DESARROLLADO POR",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "DavooPunk",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Idea, diseño e ingeniería de Davoo Player. Reproductor local sin publicidad ni rastreo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Email, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Text("Canal de Contacto y Soporte", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                text = "Para reportar fallos, enviar sugerencias o realizar consultas legales / DMCA:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = SUPPORT_EMAIL,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSendSupportEmail,
                    modifier = Modifier.weight(1f).testTag("support_email_button")
                ) {
                    Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Escribir correo")
                }
                OutlinedButton(
                    onClick = onCopySupportEmail,
                    modifier = Modifier.testTag("copy_support_email_button")
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copiar correo", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copiar")
                }
            }
            if (supportFeedback != null) {
                Text(
                    text = supportFeedback,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("Accesos directos legales y transparencia", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                text = "Versión instalada: ${BuildConfig.VERSION_NAME} · Software de Código Abierto (Licencia MIT). Motor offline sin recopilación de datos personales.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onNavigateSection(AboutSheetSection.LEGAL) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Privacidad y DMCA")
                }
                OutlinedButton(
                    onClick = { onNavigateSection(AboutSheetSection.LICENSES) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Licencias OSS")
                }
            }
        }
    }
}

@Composable
private fun LegalDocumentsSection() {
    ExpandableLegalCard(
        icon = Icons.Rounded.Lock,
        title = "Política de Privacidad",
        summary = "Offline por defecto · Integraciones opcionales (Last.fm, Deezer, letras)",
        testTag = "legal_privacy_card",
        initiallyExpanded = true,
        body = """
            Davoo Player funciona de forma local y privada por defecto. Cualquier conexión a servicios externos es opcional o se activa únicamente bajo tu solicitud.

            1. Almacenamiento local y procesamiento en el dispositivo:
            Tus canciones, carátulas, listas de reproducción (.m3u), letras guardadas (.lrc), historial local y ajustes de ecualización permanecen únicamente en la memoria privada de tu teléfono o en las carpetas que tú autorices.

            2. Sin servidores propios ni telemetría (GDPR / CCPA):
            DavooPunk no opera servidores propios, rastreadores publicitarios ni telemetría analítica; no vendemos, alquilamos ni transferimos información personal a terceros.

            3. Integración opcional con Last.fm (OAuth):
            Solo si decides conectar tu cuenta en Opciones o Perfil Last.fm, se abre la autorización oficial de Last.fm y se guardan localmente tu nombre de usuario y session_key para enviar Now Playing, scrobbles, gestionar Favoritas (track.love/unlove) y consultar tus estadísticas (topTracks, lovedTracks, recentTracks e info de perfil). Puedes cerrar sesión en cualquier momento para borrar esos datos locales y revocar el acceso.

            4. Enriquecimiento anónimo con Deezer API:
            Al abrir la pestaña Perfil Last.fm, la app consulta de forma anónima la API pública de Deezer enviando únicamente título y artista para obtener carátulas HD y vistas previas de 30 segundos, sin enviar identificadores personales ni del dispositivo.

            5. Letras, traducción opcional y comprobación de actualizaciones:
            Solo cuando pulsas manualmente buscar o traducir letras, se envía el título, artista o texto a LRCLIB, DeepL o Google Traductor. Si activas o pulsas comprobar actualizaciones, la app consulta la API pública de GitHub Releases.

            6. Permisos del sistema:
            Los permisos de lectura de audio, Selector del Sistema (SAF / Tarjeta SD) y confirmaciones del sistema (MediaStore) se utilizan exclusivamente para reproducir, editar etiquetas, renombrar o eliminar archivos locales bajo tu autorización.
        """.trimIndent()
    )

    ExpandableLegalCard(
        icon = Icons.Rounded.Gavel,
        title = "Términos y Condiciones de Uso",
        summary = "Software libre (MIT), APIs de terceros y uso responsable",
        testTag = "legal_terms_card",
        initiallyExpanded = false,
        body = """
            Al instalar, compilar o utilizar Davoo Player aceptas los siguientes términos:

            1. Licencia de Código Abierto (MIT):
            Davoo Player es software de código abierto publicado bajo la Licencia MIT. Puedes usar, estudiar, modificar y redistribuir el código fuente conservando el aviso de derechos de autor original y el texto de la licencia MIT.

            2. Uso lícito:
            Eres responsable de utilizar la aplicación respetando la legislación vigente y los derechos de propiedad intelectual de los archivos de audio que reproduzcas, edites o administres.

            3. Servicios y APIs de terceros (no afiliación):
            Las funciones opcionales conectadas a Last.fm, Deezer API, LRCLIB, DeepL, Google Traductor y GitHub dependen de servicios externos sujetos a sus propios términos y disponibilidad. Davoo Player es un proyecto independiente sin afiliación, patrocinio ni respaldo oficial por parte de dichas plataformas.

            4. Disponibilidad y garantía:
            El software se entrega "tal cual" (as-is), sin garantías expresas ni implícitas. El autor y colaboradores pueden actualizar funciones o publicar nuevas versiones en cualquier momento.
        """.trimIndent()
    )

    ExpandableLegalCard(
        icon = Icons.Rounded.Shield,
        title = "Descargo de Responsabilidad (Copyright / DMCA)",
        summary = "Reproductor local · Vistas previas transmitidas desde API pública",
        testTag = "legal_dmca_card",
        initiallyExpanded = false,
        body = """
            Davoo Player es exclusivamente una herramienta técnica para reproducir y organizar archivos de audio almacenados localmente por el usuario.

            1. Cero contenido alojado o distribuido:
            La aplicación no incluye, aloja ni distribuye música protegida, y no promueve ni facilita la piratería ni la descarga ilegal de material con derechos de autor.

            2. Vistas previas y carátulas de Deezer API:
            Las carátulas y fragmentos de vista previa de 30 segundos disponibles en la pestaña Perfil Last.fm se transmiten directamente desde la API pública oficial de Deezer bajo demanda del usuario, sin almacenar ni redistribuir obras completas dentro de la app.

            3. Responsabilidad exclusiva del usuario:
            El usuario es el único responsable de contar con los derechos legales, licencias o autorizaciones correspondientes sobre cualquier archivo MP3, FLAC, WAV, AAC u OGG que reproduzca o administre en la aplicación.

            4. Consultas DMCA o propiedad intelectual:
            Para cualquier notificación relacionada con derechos de autor, escribe a $SUPPORT_EMAIL.
        """.trimIndent()
    )
}

@Composable
private fun IntellectualPropertySection() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().testTag("license_proprietary_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Copyright, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("Licencia de Código Abierto (MIT)", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                text = "© 2025–2026 DavooPunk ($SUPPORT_EMAIL) · MIT License (Open Source).",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Davoo Player es un proyecto de código abierto bajo la Licencia MIT. Se permite su uso, copia, modificación, fusión, publicación y distribución manteniendo el aviso de copyright y esta licencia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().testTag("license_visual_assets_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Text("Derechos de Recursos Visuales y Tipografía", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                text = "El logotipo adaptativo y las ilustraciones integradas fueron generados específicamente para Davoo Player. Los iconos vectoriales proceden de Material Symbols (Google, licencia Apache 2.0, apta para uso comercial).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Icon(Icons.Rounded.Code, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
        Text(
            text = "Atribuciones de Terceros (Open Source Notices)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().testTag("open_source_notices_list")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            openSourceNotices.forEachIndexed { index, notice ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = notice.library,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = notice.license,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = notice.author,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = notice.purpose,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangelogSection() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text("Registro de Cambios (Changelog)", style = MaterialTheme.typography.titleSmall)
    }

    changelogReleases.forEach { release ->
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = if (release.tag == "Actual") {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            } else null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = release.version,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (release.tag == "Actual") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = release.tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (release.tag == "Actual") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                release.items.forEach { line ->
                    Text(
                        text = "• $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandableLegalCard(
    icon: ImageVector,
    title: String,
    summary: String,
    testTag: String,
    initiallyExpanded: Boolean,
    body: String
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Contraer" else "Expandir"
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                        Text("Documento completo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun openSupportEmail(context: Context): Boolean {
    return runCatching {
        val uri = Uri.parse(
            "mailto:$SUPPORT_EMAIL?subject=${Uri.encode("Soporte / Consulta - Davoo Player v${BuildConfig.VERSION_NAME}")}"
        )
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

private fun copyEmailToClipboard(context: Context) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Soporte Davoo Player", SUPPORT_EMAIL))
    }
}
