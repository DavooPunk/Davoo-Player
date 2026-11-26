<p align="center">
  <img src="banner.png?v=2" alt="Davoo Player Banner" width="100%">
</p>

# 🎵 Davoo Player

**Davoo Player** es un reproductor de música local nativo para Android diseñado para gestionar bibliotecas extensas de audio de alta fidelidad (**FLAC** y formatos sin pérdida), con una interfaz inmersiva y libre de fricciones, visor de letras sincronizadas con traducción por IA, integración con Last.fm, telemetría de audio y un motor de ecualización avanzado.

---

## ✨ Características Principales

- **Interfaz Inmersiva de Alta Fidelidad (Sin Modales Deslizables):**
  - **Reproduciendo Ahora de Gran Formato:** Carátula protagonista de borde a borde, badges audiófilos en tiempo real (`FLAC`, sample rate, bit depth y ruta de salida DAC/Audífonos), títulos largos con desplazamiento horizontal continuo (*marquee*) y menú superior rápido (`⋮`).
  - **Navegación Fija Estilo YT Music:** Barra inferior anclada con 3 pestañas (**Cola**, **Letras** e **Info**) que despliega una vista fija a pantalla completa con mini-reproductor superior y scroll 100% libre, erradicando por completo los paneles arrastrables (`ModalBottomSheet`) para evitar cierres accidentales.
- **Soporte de Audio Lossless, ReplayGain y Telemetría:** Reproducción fluida de **FLAC**, MP3, WAV, OGG y M4A con escáner offline de sonoridad **ReplayGain**, controles dedicados de **Preamp para salidas analógicas 3.5 mm / DAC** (`-6` a `+12 dB` y fallback para pistas sin analizar), crossfade y visualización detallada de metadatos.
- **Ecualizador Gráfico de 10 Bandas + Catálogo AutoEq e Importador `.txt`:**
  - Ajuste fino desde `31 Hz` hasta `16 kHz` (`-12 dB` a `+12 dB`) con control de preamp automático anti-*clipping* y balance estéreo.
  - **Más de 30 Perfiles AutoEq Integrados:** Curvas calibradas para audífonos e IEMs populares como **KZ** (*EDX, EDX Pro, EDX Pro X, EDX Pro 2, Castor Harman/Bass, ZSN Pro/X, ZS10 Pro/X, D-Fi, PR2*), **Moondrop** (*Chu, Chu II, Aria*), **Tangzu** (*Wan'er S.G*), **7Hz** (*Zero, Zero:2*), **Truthear** (*Zero, Zero:RED, Hola*), Sennheiser HD 650, Philips SHP9500 y más, con buscador rápido.
  - **Importador GraphicEQ (`.txt`):** Carga perfiles descargados desde `autoeq.app` o `squig.link` con interpolación logarítmica automática a 10 bandas.
- **Visor de Letras Sincronizadas y Traductor DeepSeek AI Multiidioma:**
  - Descarga e integración de letras `.lrc` desde LRCLIB o almacenamiento local, calibración de desfase (`±0.5s`) y exportación `.lrc`.
  - **Traducción por IA (DeepSeek AI / DeepL / Google):** Traducción completa y estricta de letras en uno o múltiples idiomas mezclados (ej. español/inglés, japonés/inglés, spanglish) con caché local regenerable y vista bilingüe.
- **Generador de Clips de Letra Estilo Spotify y Guardado Directo:**
  - Exporta tarjetas en video vertical (`9:16` `.mp4` en 720p/1080p) o imagen (`.png`) con el fragmento real de audio recortado (compatible con FLAC Hi-Res) y selección libre de versos hasta **1:30 (90 segundos)** sin límite artificial de líneas.
  - **Animación de Scroll Continuo:** Desplazamiento vertical suavizado que centra y resalta el verso activo (junto a su traducción en modo bilingüe) mientras atenúa los versos anteriores y siguientes.
  - **Guardado Directo en Galería + Compartir:** Almacena tus clips directamente en `Películas/Davoo Player` o `Imágenes/Davoo Player` mediante `MediaStore` (Scoped Storage) o compártelos al instante en WhatsApp, Telegram o Instagram.
- **Personalización Total de Apariencia y Temas:** Soporte para **Modo Oscuro, Modo Claro y Automático del sistema**, temas predefinidos y un **Color Picker manual (Hex y espectro)** para acentos, fondos y tarjetas, con opción de alternar entre colores dinámicos de carátula o paleta fija.
- **Reanudación Exacta y Auto-Etiquetado Deezer:** Restauración silenciosa en pausa de la última pista, cola completa y posición exacta en milisegundos al abrir la app, además de autocompletado legal de metadatos y carátulas HD (`1000x1000`) mediante la API pública de Deezer.
- **Integración con Last.fm:**
  - **Scrobbling en Vivo:** Envío automático de *Now Playing*, registro de *scrobbles* en segundo plano y sincronización bidireccional de pistas favoritas (*Loved Tracks*).
  - **Perfil y Estadísticas:** Consulta de Top Canciones, favoritas e historial reciente directamente desde tu cuenta conectada.
- **Explorador de Carpetas, Bibliotecas y Respaldos `.json`:** Navegación jerárquica por **Álbumes, Artistas, Géneros y Carpetas** con barra de ruta (*breadcrumbs*), buscador global instantáneo y exportación/importación completa de listas, letras, perfiles AutoEq, configuración ReplayGain y temas en formato `.json`.
- **Actualizaciones Integradas:** Comprobador conectado a GitHub Releases con alertas activas y descarga directa de nuevas compilaciones.

---

## 🛠️ Stack Tecnológico

- **Lenguaje & UI:** Kotlin + Jetpack Compose (Material 3)
- **Motor Multimedia y Códecs:** AndroidX Media3 (ExoPlayer) + `MediaCodec` / `MediaMuxer` nativo
- **Persistencia:** Room Database + DataStore / SharedPreferences + MediaStore (Scoped Storage)
- **APIs Externas:** DeepSeek AI (`deepseek-chat`), Deezer Public API, Last.fm Web Services (OAuth + Scrobbling), LRCLIB, GitHub Releases

---

## 🚀 Compilación desde el Código Fuente

Si deseas compilar el proyecto manualmente desde la terminal:

1. **Clona el repositorio:**
   ```bash
   git clone [https://github.com/DavooPunk/Davoo-Player.git](https://github.com/DavooPunk/Davoo-Player.git)
   cd Davoo-Player
