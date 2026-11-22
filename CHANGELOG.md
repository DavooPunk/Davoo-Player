# Registro de Cambios — Davoo Player

Historial completo de versiones, nuevas funciones, optimizaciones y correcciones de **Davoo Player**, reproductor de música local offline para Android creado por **DavooPunk**.

---

## v2.0.4 (Actual — Compilación 9)

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
