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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.BuildConfig
import com.example.ui.theme.CopperAmber
import com.example.ui.theme.LosslessCyan

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
        purpose = "Cliente HTTP ligero para descarga opcional de letras (LRCLIB) y traducción con DeepL / Google Traductor"
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
        version = "v2.0.5",
        tag = "Actual",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val contentScrollState = rememberScrollState()
    var selectedSection by remember { mutableStateOf(AboutSheetSection.ABOUT) }
    var supportFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedSection) {
        contentScrollState.scrollTo(0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .testTag("credits_bottom_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Album,
                        contentDescription = null,
                        tint = CopperAmber
                    )
                    Column {
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
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_credits_button")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cerrar créditos y legal")
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(AboutSheetSection.entries) { section ->
                    val selected = selectedSection == section
                    FilterChip(
                        selected = selected,
                        onClick = { selectedSection = section },
                        label = { Text(section.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CopperAmber.copy(alpha = 0.2f),
                            selectedLabelColor = CopperAmber
                        ),
                        modifier = Modifier.testTag(section.tag)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(contentScrollState),
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
        border = BorderStroke(1.dp, CopperAmber.copy(alpha = 0.45f)),
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
                color = CopperAmber.copy(alpha = 0.16f),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = CopperAmber,
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
                    color = CopperAmber,
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
                Icon(Icons.Rounded.Email, contentDescription = null, tint = LosslessCyan, modifier = Modifier.size(18.dp))
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
                color = LosslessCyan
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
                    color = CopperAmber
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
                Icon(Icons.Rounded.Info, contentDescription = null, tint = CopperAmber, modifier = Modifier.size(18.dp))
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
        border = BorderStroke(1.dp, CopperAmber.copy(alpha = 0.35f)),
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
                Icon(Icons.Rounded.Copyright, contentDescription = null, tint = CopperAmber, modifier = Modifier.size(18.dp))
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
                Icon(Icons.Rounded.Palette, contentDescription = null, tint = LosslessCyan, modifier = Modifier.size(18.dp))
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
        Icon(Icons.Rounded.Code, contentDescription = null, tint = LosslessCyan, modifier = Modifier.size(18.dp))
        Text(
            text = "Atribuciones de Terceros (Open Source Notices)",
            style = MaterialTheme.typography.titleSmall,
            color = LosslessCyan
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
                            color = CopperAmber
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
        Icon(Icons.Rounded.History, contentDescription = null, tint = CopperAmber, modifier = Modifier.size(18.dp))
        Text("Registro de Cambios (Changelog)", style = MaterialTheme.typography.titleSmall)
    }

    changelogReleases.forEach { release ->
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = if (release.tag == "Actual") {
                BorderStroke(1.dp, CopperAmber.copy(alpha = 0.45f))
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
                        color = if (release.tag == "Actual") CopperAmber else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = release.tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (release.tag == "Actual") LosslessCyan else MaterialTheme.colorScheme.onSurfaceVariant
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
                Icon(icon, contentDescription = null, tint = CopperAmber, modifier = Modifier.size(20.dp))
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
                        Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(14.dp), tint = LosslessCyan)
                        Text("Documento completo", style = MaterialTheme.typography.labelSmall, color = LosslessCyan)
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
