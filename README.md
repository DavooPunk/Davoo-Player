<p align="center">
  <img src="banner.png?v=2" alt="Davoo Player Banner" width="100%">
</p>

# 🎵 Davoo Player

**Davoo Player** es un reproductor de música local nativo para Android diseñado para gestionar bibliotecas extensas de audio de alta fidelidad (**FLAC** y formatos sin pérdida), con una interfaz altamente personalizable, visor de letras inmersivo, integración con Last.fm, acceso directo a metadatos y un motor de ecualización avanzado.

---

## ✨ Características Principales

- **Soporte de Audio Lossless & Metadatos:** Reproducción fluida de archivos **FLAC**, MP3, WAV, OGG y M4A con indicadores visuales de formato, normalización de volumen, crossfade y visualización detallada de metadatos.
- **Ecualizador Gráfico de 10 Bandas + Preamp:** Ajuste fino desde 31 Hz hasta 16 kHz (-12 dB a +12 dB) acompañado de control de preamplificador (Preamp) automático para evitar distorsión (*clipping*), balance estéreo y presets calibrados como **Hip-Hop**, Rock, Vocal, Clásica, Electrónica y Jazz.
- **Perfiles de Audífonos (Estilo AutoEq / Poweramp):** Incluye curvas de ecualización calibradas para modelos populares de audífonos e IEMs (Sony, Sennheiser, Audio-Technica, Apple, Samsung, KZ, entre otros) y presets personalizables.
- **Visor de Letras Inmersivo y Traducción:** Vista a pantalla completa estilo Spotify sobre el color dinámico de la carátula con letras sincronizadas (`.lrc`) desde LRCLIB o archivos locales, pausa inteligente de auto-scroll al explorar manualmente, indicador musical (`♪`) en pausas instrumentales, calibración de desfase (`±0.5s`), exportación `.lrc`, traducción (DeepL / Google) y vista bilingüe.
- **Compartir Clips de Letra con Audio (Estados / Historias):** Generador nativo de tarjetas en video vertical (`9:16` `.mp4`) o imagen (`.png`) con los versos seleccionados sincronizados en tiempo real y el fragmento de audio real recortado (100% compatible con archivos FLAC Hi-Res), listo para compartir en Estados de WhatsApp, Telegram o Instagram.
- **Personalización Total de Apariencia y Temas:** Soporte para **Modo Oscuro, Modo Claro y Automático del sistema**, temas predefinidos y un **Color Picker manual (Hex y espectro)** para personalizar acentos, fondos y tarjetas, con opción de alternar entre colores dinámicos de carátula o paleta fija.
- **Reanudación Exacta y Gestos Rápidos:** Restauración silenciosa en pausa de la última pista, cola completa, posición exacta en milisegundos, modo aleatorio y repetición al abrir la app, junto con gestos de deslizamiento en el mini reproductor y accesos directos a Artista o Álbum.
- **Integración con Last.fm:**
  - **Scrobbling en Vivo:** Envío automático de *Now Playing*, registro de *scrobbles* en segundo plano y sincronización bidireccional de pistas favoritas (*Loved Tracks*).
  - **Perfil y Estadísticas:** Consulta de Top Canciones (por período), canciones favoritas e historial reciente directamente desde tu cuenta conectada.
- **Navegación Rápida, Carpetas y Copias de Seguridad:** Organización por **Álbumes, Artistas, Géneros y Carpetas** con buscador global instantáneo, escaneo por lotes (*batch processing*) y exportación/importación completa de listas, letras, perfiles EQ y temas en formato `.json`.
- **Actualizaciones Integradas:** Comprobador conectado a GitHub Releases con alertas activas por defecto y redirección directa para descargar nuevas versiones.

---

## 🛠️ Stack Tecnológico

- **Lenguaje & UI:** Kotlin + Jetpack Compose (Material 3)
- **Motor Multimedia y Códecs:** AndroidX Media3 (ExoPlayer) + `MediaCodec` / `MediaMuxer` nativo
- **Persistencia:** Room Database + DataStore / SharedPreferences
- **APIs Externas:** Last.fm Web Services (OAuth + Scrobbling), LRCLIB, GitHub Releases

---

## 🚀 Compilación desde el Código Fuente

Si deseas compilar el proyecto manualmente desde la terminal:

1. **Clona el repositorio:**
   ```bash
   git clone [https://github.com/DavooPunk/Davoo-Player.git](https://github.com/DavooPunk/Davoo-Player.git)
   cd Davoo-Player
