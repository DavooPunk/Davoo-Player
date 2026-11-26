# Registro de Cambios — Davoo Player

Historial completo de versiones, nuevas funciones, optimizaciones y correcciones de **Davoo Player**, reproductor de música local offline para Android creado por **DavooPunk**.

---

## v2.1.1 (Actual — Compilación 13)

### Reproduciendo ahora + Vista Integrada Estilo YT Music y Cero Modales Deslizables
- **Carátula protagonista `1:1` de gran formato**: ocupa casi todo el ancho disponible con márgenes compactos (`16–18 dp`), esquinas redondeadas de `12 dp`, badges audiófilos limpios y menú superior (`⋮`) para acceder rápido a cola, ecualizador, temporizador, listas, etiquetas y metadatos.
- **Barra inferior fija con 3 pestañas (*Cola*, *Letras*, *Info*)**: anclada en la base de *Reproduciendo ahora*, abre una vista fija a pantalla completa con cabecera superior (`44 dp`, minicarátula, título con *marquee*, artista, play/pausa y colapsar `⌄`), pestañas fijas y desplazamiento 100% libre sin cierres accidentales.
- **Títulos deslizantes (`marquee`) y vistas fijas en toda la app**: eliminación total de paneles `ModalBottomSheet` reemplazados por vistas fijas a pantalla completa o diálogos centrados con botón explícito de cierre y `BackHandler`, junto a animación horizontal continua en títulos largos.

### Generador de Clips de Letra Estilo Spotify y Guardado Directo
- **Sin tope artificial de líneas (hasta 1:30)**: selecciona tantos versos consecutivos como entren en el límite máximo de **90 segundos (`1:30`)**, manteniendo el texto grande y legible.
- **Scroll vertical animado en vivo y en video `.mp4`**: desplazamiento continuo suavizado (*FastOutSlowIn*) que centra el verso activo en negrita al `100%` de opacidad (junto a su traducción en modo bilingüe) mientras atenúa los versos pasados y futuros.
- **Guardar en el dispositivo + Compartir**: tras renderizar un `.mp4` o `.png`, elige entre **Guardar** directamente en *Películas/Davoo Player* o *Imágenes/Davoo Player* (Scoped Storage `MediaStore` sin permisos invasivos) y **Compartir**, sin cerrar ni borrar el archivo generado.

### Traductor DeepSeek AI Multiidioma Estricto
- **Traducción completa de letras mixtas**: regla estricta en `deepseek-chat` para traducir el 100% de versos y frases extranjeras mezcladas (ej. español/inglés, japonés/inglés, spanglish) al idioma destino, con regeneración capaz de sobrescribir la caché local y renderizado bilingüe sin filtros falsos.

### Catálogo AutoEq Masivo, Importador `.txt` y Preamp ReplayGain
- **Más de 30 perfiles AutoEq integrados + Importador GraphicEQ `.txt`**: añade la línea KZ (*EDX, EDX Pro, EDX Pro X, EDX Pro 2, Castor Harman/Bass, ZSN Pro/X, ZS10 Pro/X, D-Fi, PR2*), Moondrop (*Chu, Chu II, Aria*), Tangzu (*Wan'er S.G*), 7Hz (*Zero, Zero:2*), Truthear (*Zero, Zero:RED, Hola*), Sennheiser HD 650, Philips SHP9500 y más, además del botón **«Importar .txt»** con interpolación logarítmica de 10 bandas.
- **Preamp dedicado para ReplayGain (3.5 mm / DAC) en *Opciones***: nuevos controles exclusivos en *Opciones > Audio y ecualización* para ajustar la ganancia objetivo de ReplayGain (`-6` a `+12 dB`, `+3.0 dB` por defecto) y pistas sin etiquetas (`-12` a `0 dB`, `-4.0 dB` por defecto), incluidos en los respaldos `.json`.

---

## v2.1.0 (Compilación 12)

### Traductor de Letras con DeepSeek AI
- **Integración de DeepSeek AI (`deepseek-chat`)**: disponible como tercera opción junto a **DeepL** y **Google Traductor** en *Opciones > Traductor de letras* y en el menú rápido del visor inmersivo de letras. Envía todos los versos en una sola petición JSON estructurada para conservar contexto poético, modismos y exactitud en los timestamps `.lrc`, guardando el resultado en caché offline e incluyendo campo enmascarado y prueba de conexión directa (excluido de respaldos `.json`).

### Auto-etiquetado Deezer y Descarga de Letras (`.lrc`) por Lotes
- **Auto-etiquetado desde la API pública de Deezer (sin ARL ni descargas de audio)**: autocompleta carátulas HD (`1000×1000`), álbum, artista, año y número de pista directamente en el editor de etiquetas o por lotes desde *Opciones > Etiquetas y letras (.lrc)* para tus archivos MP3 y FLAC locales.
- **Descarga de letras sincronizadas (`.lrc`) por lotes desde LRCLIB**: busca y guarda offline letras con marcas de tiempo para toda tu biblioteca existente, con opción de exportarlas a una carpeta del sistema.

### Compartir Letras Mejorado: Hasta 10 Líneas, Límite 1:30, Texto Más Grande y Video 1080p
- **Selección ampliada de 1 a 10 líneas y hasta 1:30 (90 segundos)**: permite seleccionar hasta 10 versos consecutivos en letras `.lrc` (o un fragmento libre de `15s` a `1:30`) con el límite ampliado de hasta 1:30 (90 segundos) y compatibilidad con modo Original o Bilingüe.
- **Tamaño de letra seleccionable**: nuevo selector de tamaño (*Compacto*, *Mediano*, *Grande* por defecto y *Muy grande*) aplicado en vivo tanto en la vista previa como en las exportaciones `.png` y `.mp4`, con reajuste automático inteligente para evitar cortes.
- **Exportación de video en 1080p Full HD (`1080×1920`)**: nuevo selector de resolución (**1080p Full HD** por defecto y **720p Ligero**) con respaldo automático a `720p` y liberación limpia del codificador en hardware de gama baja.

### Experiencia Audiófila, Organización y Opciones Centralizadas
- **Orden de biblioteca persistente**: Davoo Player recuerda automáticamente el criterio de ordenamiento elegido (*Más recientes*, *Título*, *Artista*, *Álbum* o *Duración*) entre sesiones e incluye esta preferencia en el respaldo `.json`.
- **Escáner de sonoridad ReplayGain (`-18 dBFS`) bajo demanda**: accesible exclusivamente desde *Opciones > Audio y ecualización*, analiza pistas MP3/FLAC locales en streaming (~64 KB de RAM) y guarda las ganancias calculadas en caché local sin modificar tus archivos de audio.
- **Fade suave al pausar y reanudar (~180 ms)**: nuevo interruptor exclusivo en *Opciones > Audio y ecualización* (también incluido en los respaldos `.json`) para atenuar suavemente el volumen en pausas y reanudaciones manuales.
- **Telemetría de salida de audio y DAC USB en vivo**: detección sin permisos adicionales vía `AudioDeviceCallback` que muestra la ruta activa (*DAC USB*, *Audífonos*, *Bluetooth*, *HDMI* o *Altavoz*), frecuencias soportadas y profundidad de bits en *Reproduciendo ahora* y *Metadatos*.
- **Explorador jerárquico de carpetas físicas**: navega por subcarpetas reales dentro de *Colección > Carpetas* con barra de ruta interactiva (*breadcrumbs*), reproducción o aleatorio por subcarpeta, botón *Atrás* y conservación de interruptores de exclusión.

---

## v2.0.6 (Compilación 11)

### Compartir Letras y Fragmentos en Video 9:16 (`.mp4`) e Imagen (`.png`)
- **Exportación nativa de video vertical (`720x1280`, 30 fps) con audio**: crea clips listos para Estados de WhatsApp, Telegram e Historias de Instagram directamente desde el botón de compartir integrado en la esquina superior derecha del visor inmersivo de letras (con soporte tanto para letras `.lrc` como para pistas sin letra sincronizada).
- **Selección inteligente de versos y rango de audio**: elige de 1 a 6 líneas consecutivas en letras `.lrc` (aplicando el offset calibrado por canción, límite automático de 30 segundos y opción **Original** o **Bilingüe** si hay traducción) o selecciona un fragmento de 15s a 30s con visualizador de espectro animado para temas sin letra sincronizada.
- **Vista previa en vivo y 3 estilos visuales**: escucha en bucle el fragmento seleccionado con animación en tiempo real (pausando y restaurando automáticamente el reproductor principal) y elige entre *Color dinámico de carátula*, *Oscuro ámbar (`CopperAmber`)* o *Carátula difuminada*, en formato **Video con Audio (`.mp4`)** o **Tarjeta Estática (`.png`)**.
- **Motor nativo compatible con FLAC Hi-Res**: transcodificación directa con `MediaExtractor`, `MediaCodec` (`H.264` + `AAC 192 kbps`) y `MediaMuxer` sin librerías pesadas externas; convierte automáticamente audio de 24-bit/32-bit/float y frecuencias Hi-Res (`88.2 kHz`, `96 kHz`) a 16-bit y `44.1/48 kHz`, aplica `fade-in` (300 ms) y `fade-out` (500 ms), muestra progreso real cancelable y limpia la caché `shared_clips` antes de cada exportación.

### Interludios Instrumentales, Pantalla Completa y Optimización
- **Icono de nota musical en interludios instrumentales**: las introducciones largas (> 5s), pausas entre versos mayores a 8s y líneas vacías o de puntos (`...`) en letras sincronizadas muestran un icono limpio de nota musical (`♪`) interactivo con pulso sutil por GPU mientras suena.
- **Menú *Acerca de, Legal y Cambios* a pantalla completa**: migrado a una vista inmersiva de pantalla completa con flecha de cierre en la esquina superior izquierda, evitando cierres accidentales al desplazarse.
- **Avisos de actualización activos por defecto**: la comprobación ligera diaria de nuevas versiones en GitHub viene habilitada de fábrica (y sigue siendo configurable en *Opciones*).
- **Rendimiento, estabilidad y menor uso de memoria**: modelos principales anotados como `@Immutable` para reducir recomposiciones en desplazamiento rápido, animaciones libres de relayouts y limpieza determinista de caché y recursos nativos.

### Apariencia, Colores Manuales y Copias de Seguridad (`.json`)
- **Tema Oscuro, Claro y Automático del sistema**: selector de 3 modos en *Opciones > Apariencia* (Oscuro por defecto, Claro de alto contraste y Automático según el sistema), con adaptación dinámica de los iconos de la barra de estado y navegación.
- **Personalización total de colores**: 6 paletas predefinidas (*Ámbar Cobre*, *Cian Lossless*, *Esmeralda*, *Violeta Neón*, *Carmesí* y *Monocromo*) más editor manual interactivo (cuadro Saturación/Brillo, barra de Tono, muestras rápidas y entrada hexadecimal `#RRGGBB`) para personalizar primario, secundario, fondo, tarjetas y texto con verificación de contraste WCAG y restauración a valores por defecto.
- **Control unificado de colores dinámicos de carátula**: al desactivar *"Usar colores dinámicos de la carátula"*, el reproductor, el mini reproductor flotante, el visor de letras y las tarjetas de compartir respetan estrictamente tu paleta personalizada.
- **Copia de seguridad `.json` exportable e importable**: guarda y restaura listas, estadísticas de reproducción, historial, traducciones offline, ecualizador y preferencias de apariencia/tema desde *Opciones*.
- **Menús más limpios y consistentes**: controles de Crossfade, Normalizar volumen y Gapless centralizados exclusivamente dentro de *Ecualizador y DSP*, y personalización visual centralizada en *Opciones > Apariencia*.

---

## v2.0.5 (Compilación 10)

### Visor de Letras Inmersivo y Navegación Libre (Estilo Spotify)
- **Pantalla completa sin cierres accidentales**: reemplaza el contenedor deslizable por una vista inmersiva a pantalla completa con transición vertical suave, botón superior izquierdo de cierre explícito (`KeyboardArrowDown`) y título/artista centrados sobre el color dinámico de la carátula.
- **Pausa inteligente del auto-scroll**: al desplazarte manualmente por la letra se pausa el seguimiento automático durante ~4.5 segundos manteniendo iluminada la frase activa y mostrando la píldora flotante **«Sincronizar»** (también puedes reanudar tocando cualquier línea).

### Calibración de Sincronización (`±0.5s`) y Exportación `.lrc`
- **Offset por canción (`-0.5s` / `0.0s` / `+0.5s`)**: ajusta en vivo el adelanto o retraso de letras desincronizadas con persistencia por pista en la base de datos local (Room `v5`).
- **Exportar y compartir `.lrc`**: guarda el archivo `.lrc` en tu almacenamiento con el selector del sistema (SAF) o compártelo directamente vía `FileProvider`, eligiendo formato **Original** o **Bilingüe** con los tiempos ya corregidos por el offset.

### Reanudación Exacta y Reproductor Refinado
- **Reanudación exacta de sesión al iniciar**: guarda la última pista, toda la cola en orden, posición exacta en milisegundos, modo aleatorio y repetición para restaurarlos en pausa al volver a abrir la app (sin auto-play indeseado).
- **Interfaz de reproducción ajustada y sin botones de 10 segundos**: se eliminaron los botones de retroceder/adelantar 10 segundos y se adaptó automáticamente el tamaño de la carátula y los espaciados para que todos los controles encajen sin cortes en pantalla.
- **Gestos en mini reproductor y accesos en Reproduciendo ahora**: desliza horizontalmente el mini reproductor inferior para cambiar a la pista siguiente o anterior, y toca los accesos de **Artista** o **Álbum** en la pantalla *Reproduciendo ahora* para saltar directo a su colección.

### Estabilidad, Fluidez y Cero Tirones
- **Ticker de progreso desacoplado (`PlaybackPositionState`)**: la barra de tiempo actualiza únicamente los controles de tiempo y el deslizador cada 350 ms, evitando recomponer toda la biblioteca, colas y menús durante la reproducción.
- **Caché de sesión e IPC en segundo plano**: caché en memoria de la cola y la sesión guardada con actualización ligera de posición (`updateExactPosition`), evitando lecturas Binder repetidas en el hilo principal y re-serializaciones de hasta 2,500 pistas.
- **Renderizado y concurrencia optimizados**: barras de ecualizador animadas con `graphicsLayer.scaleY` sin relayouts, semáforos de concurrencia (`Semaphore`) al extraer duraciones y colores de carátula, e hints `contentType` en listas para un scroll uniforme sin tirones.

---

## v2.0.4 (Compilación 9)

### Crossfade Real y Colores Adaptativos en Letras
- **Crossfade configurable entre canciones (0 a 12 s)**: reproducción solapada de doble canal mediante curva *equal-power* para mantener el volumen constante, ecualizador/balance DSP aplicados en la mezcla, respeto por etiquetas ReplayGain y control continuo desde *Opciones* y *Ecualizador*.
- **Colores adaptativos en Letras y Traducción**: el visor de letras, el degradado de fondo, la línea traducida activa, la barra de progreso y los menús flotantes rápidos de traducción (*DeepL / Google*, selector de idioma) y gestión `.lrc` ahora adoptan automáticamente el color dinámico extraído de la carátula de la canción en reproducción.

### Enriquecimiento Deezer y Avatar Last.fm
- **Búsqueda flexible en Deezer (`buscarEnDeezer`)**: tolerante a múltiples artistas y colaboraciones (comas `,`, `&`, `feat.` / `ft.`) con respaldo automático mediante búsqueda abierta, conversión forzada a `https://`, actualización reactiva inmediata en lista y reproducción de vistas previas de 30 segundos.
- **Avatar real en Perfil Last.fm**: consulta adicional a `user.getInfo` para mostrar la foto de perfil del usuario en el círculo superior con respaldo del icono de disco si no tiene avatar.

### Navegación en Biblioteca y Cola Reorganizada
- **Botón *"Ir a la canción actual"* en Tu Música**: acceso compacto junto al botón de selección múltiple (visible cuando la pista activa está en la lista filtrada) para desplazarse con animación directamente hasta la canción que está sonando.
- **Cola de reproducción centrada en lo actual y siguiente**: fija siempre en primera posición la pista en reproducción (*«SONANDO AHORA»*), muestra únicamente las pistas siguientes con reordenamiento por arrastre (`drag & drop`), ajusta automáticamente los índices al seleccionar o quitar canciones y añade un botón rápido en la cabecera para volver suavemente arriba.

### Gestión Física de Archivos y Etiquetas en Disco
- **Eliminar canciones físicamente del dispositivo (individual y por lotes)**: disponible tanto desde el menú contextual de cada pista como desde la selección múltiple con diálogo de confirmación. Implementado con Scoped Storage para Android 10–15+ (`MediaStore.createDeleteRequest`, `RecoverableSecurityException` y `DocumentsContract`), sincronizando al instante la cola y limpiando biblioteca, estadísticas, playlists, overrides y letras sin re-escaneo completo.
- **Renombrado físico y escritura real de etiquetas**: nuevo campo de nombre de archivo dentro del editor de etiquetas (conservando la extensión original y bloqueando caracteres inválidos), escritura binaria directa en disco de etiquetas ID3v2.3 (MP3, preservando letras `USLT` y portadas `APIC`) y Vorbis Comment / `PICTURE` (FLAC, preservando `STREAMINFO` y `LYRICS`), actualización de columnas en `MediaStore`, flujo de permiso de escritura del sistema, indexación mediante `MediaScannerConnection` y refresco inmediato en la app.

---

## v2.0.3 (Compilación 8)

### Integración con Last.fm y Scrobbling en Vivo
- **Autenticación OAuth nativa**: botón *"Conectar con Last.fm"* en *Opciones* y en la nueva pestaña *Last.fm*, con apertura en navegador externo, callback `davooplayer://lastfm-callback`, firma MD5 nativa (`MessageDigest`) sin librerías externas y persistencia segura de sesión (`lfm_session_key`, `lfm_username`).
- **Configuración flexible de credenciales**: soporte para constantes/Secrets (`LASTFM_API_KEY`, `LASTFM_SHARED_SECRET`) y diálogo modal editable dentro de la app.
- **Scrobbling automático en tiempo real**: envía `track.updateNowPlaying` al comenzar una pista y ejecuta `track.scrobble` una única vez al superar el 50% de duración (o 15 segundos en previews) con indicador discreto `"Scrobble enviado"`.
- **Sincronización de Favoritas (`track.love` / `track.unlove`)**: el botón de corazón en el reproductor y en el perfil Last.fm sincroniza tus gustos al instante.

### Perfil Last.fm y Enriquecimiento con Deezer
- **Nueva pestaña *Last.fm***: muestra **Top Canciones** (con filtros `7 días`, `1 mes`, `12 meses` y `Siempre`), **Favoritas** e **Historial Reciente** destacando las pistas en reproducción actual (`@attr.nowplaying`).
- **API pública de Deezer (`buscarEnDeezer`)**: obtiene carátulas en alta resolución (`cover_xl`) y permite escuchar el fragmento de audio (`preview` MP3 de 30s) directamente en Davoo Player con caché LRU limitada en memoria y control de concurrencia para ahorrar batería y datos.

### Correcciones Críticas y Rendimiento (Buscador Global)
- **Escritura fluida y cursor estable con Gboard**: desacoplamiento total entre el estado síncrono local del `OutlinedTextField` (`localQuery`) y el `StateFlow` del `ViewModel`, eliminando saltos de cursor, inversión de caracteres y conflictos con el autocompletado al escribir rápido.
- **Filtrado asíncrono con debounce (`150 ms`)**: el filtrado de bibliotecas extensas (más de 5,400 pistas, álbumes, artistas y listas) ahora se ejecuta en `Dispatchers.Default` mediante `collectLatest` y sin asignaciones innecesarias de cadenas, cancelando búsquedas intermedias para no bloquear el hilo principal.

---

## v2.0.2 (Compilación 7)

### Hub Principal y Ecualizador
- **Cabecera principal mejorada**: intercambio de los botones circulares superiores en *Tu Música* para ubicar la lupa de búsqueda a la izquierda y la tuerca de *Opciones* en el extremo derecho.
- **Nuevo preset Hip-Hop**: curva de 10 bandas orientada a subgraves y punch (`31 Hz`, `62 Hz`, `125 Hz`) con brillo definido en agudos y Preamp automático (`-4.8 dB`) para evitar clipping.

### Visor de Letras Minimalista (Estilo Spotify)
- **Cabecera sin distracciones**: al cargar una letra se ocultan automáticamente los botones superiores y las filas grandes del traductor, dejando las letras sincronizadas como protagonistas en alto contraste blanco sobre fondo oscuro y scroll fluido.
- **Barra inferior con dos iconos**: abajo a la izquierda un botón compacto de traducción (*DeepL / Google*, idioma y modos *Bilingüe / Traducida / Original*) en menú flotante; abajo a la derecha un botón compacto de gestión para descargar desde LRCLIB, cargar `.lrc` local, abrir Lyricsify o eliminar la letra guardada, junto a una barra de progreso de reproducción integrada debajo.

### Actualizaciones Opcionales vía GitHub y Limpieza de Ajustes
- **Comprobador opcional de actualizaciones**: nueva opción discreta en *Opciones* (manual o diaria ligera si se activa) que consulta las versiones en tu repositorio de GitHub, muestra aviso en la app, envía notificación del sistema opcional y abre el enlace directamente en el navegador externo.
- **Limpieza en Opciones**: se eliminó el botón redundante de *"Código abierto (Licencia MIT) y legal"*, conservando *"Acerca de Davoo Player"* como acceso unificado.

### Estabilidad y Bajo Consumo
- **Menor consumo de CPU, RAM y batería**: visualizador de espectro optimizado por GPU (`graphicsLayer.scaleY`) sin relayouts a 60 fps, limpieza de caché de colores de carátula en `onTrimMemory`/`onLowMemory`, bypass rápido de matriz en el procesador de audio del ecualizador y liberación de recursos al cerrar pantallas y ViewModel.

---

## v2.0.1 (Compilación 6)

### Buscador Global en Cabecera Fija
- **Botón fijo en todas las pestañas**: añadido el botón de búsqueda (lupa) en la esquina superior derecha de la cabecera de *Biblioteca*, *Álbumes* y *Playlists*.
- **Búsqueda global unificada en tiempo real**: al pulsarlo se despliega un panel para filtrar simultáneamente canciones, álbumes, artistas y playlists, eliminando los buscadores duplicados del cuerpo y la barra flotante anterior.

### Ecualizador Avanzado de 10 Bandas y Perfiles AutoEq
- **Ecualizador gráfico de 10 bandas + Preamp**: ampliado a **31Hz, 62Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz y 16kHz** (-12 dB a +12 dB) con deslizador dedicado de **Preamp** y limitación suave en tiempo real sobre PCM bicuádrico para evitar clipping.
- **Perfiles calibrados para audífonos (AutoEq)**: selector con buscador rápido para modelos populares (*Sony WH-1000XM4/XM5, Apple AirPods Pro 2, Samsung Galaxy Buds2 Pro, Sennheiser HD 600, Audio-Technica ATH-M50x, KZ ZSN Pro, JBL Tune 720BT, Bose QuietComfort Ultra, Harman IEM Neutro*) junto a presets clásicos (*Plano, Graves+, Vocal, Rock, Jazz, Clásica, Electrónica, V-Shape*).
- **Perfiles personalizados del usuario**: guarda y elimina tus propios perfiles de ecualización y preamp con persistencia local.

### Optimización de Rendimiento al Quitar Carpetas
- **Actualización optimista instantánea**: al quitar una carpeta se refleja visualmente al instante con un indicador de carga sutil solo en ese elemento, sin bloquear el resto de la interfaz.
- **Borrado por lotes y sin re-escaneo completo**: los registros asociados se eliminan por lotes en Room y se filtran en memoria directamente, evitando volver a escanear el resto de las carpetas vinculadas.
- **Limpieza de memoria en segundo plano**: liberación asíncrona de caché de carátulas y duraciones en hilos secundarios.

---

## v2.0.0 (Compilación 5)

### Interfaz Limpia y Nuevo Menú de Opciones
- **Hub principal limpio y unificado**: se eliminaron las opciones dispersas del encabezado y los menús/filtros de MP3, FLAC y WAV para mostrar toda tu música unificada en una interfaz minimalista y clara.
- **Nuevo submenú de Opciones (icono de tuerca)**: acceso directo desde la esquina superior derecha de *Tu Música* que centraliza:
  - **Memoria y biblioteca**: actualización/escaneo de canciones, importación de archivos, gestión de carpetas excluidas y vinculación de carpetas en tarjeta SD.
  - **Audio y ecualización**: Ecualizador y DSP, Temporizador de apagado, Normalizar volumen y Reproducción sin pausas (*Gapless*).
  - **Traductor de letras**: selector persistente entre **DeepL** y **Google Traductor**.
  - **Personalización**: interruptor de colores dinámicos de carátula.
  - **Créditos, código abierto y soporte**: acceso a licencia MIT, documentos legales, changelog y contacto oficial.

### Traducción Dual Seleccionable (DeepL + Google)
- **Elige tu traductor**: selecciona libremente entre **DeepL API v2** (alta precisión con contexto de canción y artista, compatible con claves Free `:fx` y Pro) y **Google Traductor** (rápido, sin clave API, con normalizador de contracciones y modismos musicales) tanto en *Opciones* como directamente en el visor de letras.
- **Persistencia offline y edición humana**: todas las traducciones se guardan en la base de datos local (Room) y se mantiene la detección nativa de archivos `.lrc` bilingües y el editor manual con enlaces a **Letras.com** y **LyricsTranslate**.

### Rendimiento Ultraligero y Estabilidad en Teléfonos de Bajos Recursos
- **Barra de desplazamiento lateral sin lag**: reescrita usando traducción directa en GPU (`graphicsLayer`), canal único conflacionado para evitar acumulación de corrutinas y geometría ligera sin recálculo de sombras por fotograma.
- **Menor consumo de RAM y CPU al desplazarse**: límite de concurrencia en la extracción de carátulas embebidas, cancelación inmediata de filas fuera de pantalla, eliminación de corrutinas innecesarias por fila y remoción de dependencias pesadas de ML Kit para mantener un APK más ligero.

### Código Abierto y Contacto Oficial
- **Licencia MIT (Open Source)**: Davoo Player pasa a ser software de código abierto con archivo `LICENSE` en el repositorio y términos actualizados dentro de la app.
- **Contacto y soporte oficial**: actualizado a `davidmarin2704@gmail.com`.

---

## v1.3.0 (Compilación 4)

### Audio y Ecualización (DSP)
- **Normalización de volumen integrada en Ecualizador y DSP**: nuevo interruptor activable/desactivable ubicado directamente en la parte superior del menú de Ecualización.
  - Aplica el nivel exacto cuando el archivo incluye etiquetas **ReplayGain** (`REPLAYGAIN_TRACK_GAIN`).
  - Activa automáticamente el nivelador nativo de Android (`LoudnessEnhancer`) en pistas sin etiqueta ReplayGain y libera el efecto por completo al desactivarlo.
- **Blindaje del ecualizador y controles DSP**: sincronización segura de hilos, validación de rangos y limitación de actualizaciones rápidas en sliders para evitar cierres inesperados al manipular intensamente el ecualizador, Bass Boost, Virtualizador 3D o cambiar de pista.

### Almacenamiento y Biblioteca
- **Soporte completo para tarjetas SD**:
  - Detección automática multi-volumen en almacenamiento interno y externo.
  - Vinculación directa de carpetas en tarjeta SD desde el gestor de carpetas (mediante selector de carpetas del sistema sin permisos invasivos) con indexación persistente y opción de desvincular.
- **Refresco seguro de la biblioteca**: serialización de escaneos concurrentes y protección contra archivos corruptos o URIs inaccesibles para prevenir crasheos al actualizar las canciones.

### Letras y Traducción
- **Traducción local en el dispositivo (sin claves API ni IA generativa)**:
  - Se eliminaron completamente las integraciones anteriores (IA, Google Traductor y DeepL) para mantener el código limpio y funcionar sin claves ni cuentas.
  - Traducción 100% en el dispositivo mediante **ML Kit Translate & Language ID** (descarga única del paquete de idioma ~30 MB y luego funciona sin internet) con alineación exacta línea por línea para respetar cada tiempo `.lrc`.
  - **Normalizador previo de letras**: expande contracciones y modismos frecuentes en canciones en inglés (`wanna`, `gonna`, `ain't`, `'cause`, `feelin'`, etc.) antes de traducir para mejorar la naturalidad de los versos.
- **Soporte nativo para archivos `.lrc` bilingües**:
  - Detección automática de marcas de tiempo duplicadas en archivos `.lrc` bilingües, mostrando al instante los modos *Bilingüe*, *Traducida* y *Original* 100% sin conexión.
- **Traducciones humanas guardadas offline (Room)**:
  - Persistencia local en base de datos para todas las traducciones (locales automáticas o editadas manualmente) sin volver a depender de internet.
  - Editor integrado para pegar o corregir traducciones manualmente, con accesos directos a **Letras.com** y **LyricsTranslate**.

### Navegación y Usabilidad
- **Barra de desplazamiento rápido mejorada**: mayor visibilidad, contraste y área táctil más amplia en el borde derecho.
- **Cobertura extendida**: barra rápida disponible ahora también en listas de Artistas, detalle de Álbumes y vistas filtradas por Artista, Género y Carpeta.

### Información Legal, Confianza y Soporte
- **Sección unificada "Acerca de" y Legal** con pestañas organizadas:
  - **Acerca de**: versión y número de compilación, créditos de autoría para **DavooPunk** y canal de soporte por correo electrónico con acceso directo y copiado rápido.
  - **Documentos Legales**: Política de Privacidad (declaración explícita de uso offline y cero recolección de datos personales), Términos y Condiciones de Uso, y Descargo de Responsabilidad sobre Derechos de Autor (Copyright / DMCA).
  - **Propiedad Intelectual y Licencias**: licencia propietaria del código, derechos sobre recursos visuales y avisos de licencias de terceros (*Open Source Notices*: AndroidX Media3, Jetpack Compose, Room, Material Icons, Coil, OkHttp, Google ML Kit y Kotlin).
  - **Registro de Cambios (Changelog)**: historial interactivo de versiones dentro de la app.

---

## v1.2.0

### Navegación y Gestión de Biblioteca
- **Botón flotante de búsqueda rápida (lupa)**: al desplazarse hacia abajo en bibliotecas extensas y comenzar a subir, aparece un botón rápido para saltar directamente al buscador sin tener que regresar manualmente al inicio.
- **Barra lateral de desplazamiento rápido**: indicador deslizable con burbuja flotante de letra y posición para recorrer listas largas de canciones y playlists con agilidad.
- **Selección múltiple de canciones**: modo de selección por lotes en la biblioteca y en las playlists para añadir varias pistas a una lista, enviarlas a la cola o eliminarlas en conjunto con confirmación segura.

---

## v1.1.0

### Identidad y Experiencia de Usuario
- **Identidad Davoo Player**: actualización del nombre de la app a **Davoo Player**, cambio del encabezado principal a **"Tu Música"** e inclusión de un apartado discreto de créditos dedicado a **DavooPunk**.
- **Corrección en Favoritos**: persistencia explícita y sincronizada del estado de favorito (icono de corazón) para evitar desincronizaciones al marcar o desmarcar canciones desde cualquier pantalla.

### Letras Online y Offline
- **Descarga de letras dentro de la app**: búsqueda e integración de letras sincronizadas (`.lrc`) y planas desde LRCLIB, almacenamiento offline por canción y acceso directo para buscar en **Lyricsify**.

### Rendimiento, Batería y Memoria
- **Optimización de batería y RAM**: ajustes en caché de carátulas, liberación de memoria ante eventos del sistema (`onTrimMemory` / `onLowMemory`), eliminación de trabajo innecesario en reposo y liberación de efectos DSP cuando están inactivos.
- **Cierre limpio desde multitarea**: al cerrar la aplicación completamente desde el selector de tareas recientes, el servicio de reproducción y sus recursos se detienen y liberan adecuadamente para evitar consumo en segundo plano.

---

## v1.0.0 (Lanzamiento Inicial)

### Motor de Reproducción Local Offline
- **Reproductor de audio Hi-Res 100% offline** basado en AndroidX Media3 (ExoPlayer) compatible con **MP3, FLAC, WAV, AAC y OGG**.
- **Controles de reproducción completos**: pantalla *Now Playing*, barra flotante *Mini Player*, cola de reproducción reordenable, modo aleatorio, repetición, controles en notificación multimedia, widget de pantalla de inicio y compatibilidad con Android Auto.

### Procesamiento de Audio (DSP)
- **Ecualizador de 5 bandas** con perfiles preestablecidos (*Plano, Rock, Pop, Jazz, Clásica, Electrónica, Graves, Vocal* y *Personalizado*).
- **Efectos de audio**: refuerzo de graves (*Bass Boost*), virtualizador envolvente 3D, *crossfade* configurable entre pistas, control de velocidad de reproducción (`0.75x` a `2.0x`), balance estéreo (L/R), reproducción sin pausas (*Gapless*) y soporte inicial para etiquetas ReplayGain.
- **Temporizador de apagado (*Sleep Timer*)** por minutos o al finalizar la pista actual.

### Organización, Etiquetas y Personalización
- **Exploración avanzada**: pestañas de *Tu Música*, *Colección* (Álbumes, Artistas, Géneros y Carpetas con exclusión de carpetas) y *Playlists*.
- **Playlists manuales e inteligentes**: creación, edición, estadísticas de reproducción (más escuchadas, recientes, favoritas) e importación/exportación de listas `.m3u`.
- **Editor integrado de etiquetas y carátulas** (título, artista, álbum, género, año, número de pista e imagen mediante Android Photo Picker sin permisos de almacenamiento) y visor de metadatos técnicos del archivo.
- **Visor de letras** embebidas y soporte para cargar archivos `.lrc` locales.
- **Tema dinámico por carátula** en interfaz Material Design 3 adaptativa.
