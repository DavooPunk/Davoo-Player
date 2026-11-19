# Registro de Cambios — Davoo Player

Historial completo de versiones, nuevas funciones, optimizaciones y correcciones de **Davoo Player**, reproductor de música local offline para Android creado por **DavooPunk**.

---

## v2.0.0 (Actual — Compilación 5)

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
