<p align="center">
  <img src="banner.png?v=2" alt="Davoo Player Banner" width="100%">
</p>

# 🎵 Davoo Player

**Davoo Player** es un reproductor de música local nativo para Android diseñado para gestionar bibliotecas extensas de audio de alta fidelidad (FLAC y formatos sin pérdida), con una interfaz oscura minimalista, acceso directo a metadatos y un motor de ecualización avanzado.

---

## ✨ Características Principales

- **Soporte de Audio Lossless & Metadatos:** Reproducción fluida de archivos **FLAC**, MP3, WAV, OGG y M4A con indicadores visuales de formato y visualización detallada de metadatos a la mano.
- **Ecualizador Gráfico de 10 Bandas + Preamp:** Ajuste fino desde 31 Hz hasta 16 kHz (-12 dB a +12 dB) acompañado de control de preamplificador (Preamp) automático para evitar distorsión (*clipping*), incluyendo presets calibrados como **Hip-Hop**, Rock, Vocal, Clásica, Electrónica y Jazz.
- **Perfiles de Audífonos (Estilo AutoEq / Poweramp):** Incluye curvas de ecualización calibradas para modelos populares de audífonos e IEMs (Sony, Sennheiser, Audio-Technica, Apple, Samsung, KZ, entre otros) y presets clásicos personalizables.
- **Visor de Letras Minimalista y Traducción:** Interfaz limpia estilo Spotify con letras sincronizadas (`.lrc`) desde LRCLIB o archivos locales, menú rápido de traducción (DeepL / Google) y vista bilingüe.
- **Navegación Rápida y Búsqueda Global:** Organización por **Álbumes, Artistas, Géneros y Carpetas**, con buscador global siempre accesible en la cabecera principal.
- **Gestión Optimizada de Carpetas:** Escaneo y eliminación por lotes (*batch processing*) que permite agregar o quitar carpetas con cientos de álbumes de forma instantánea sin bloquear la interfaz.
- **Actualizaciones Integradas:** Comprobador opcional desde los ajustes conectado a GitHub Releases con notificaciones nativas y redirección directa para descargar nuevas versiones.
- **Interfaz Dark Mode:** Diseño limpio con paleta oscura y acentos cálidos pensado para uso prolongado, renderizado a 60 fps por GPU y bajo consumo de batería.

---

## 🛠️ Stack Tecnológico

- **Lenguaje:** Kotlin
- **Sistema de Construcción:** Gradle (Kotlin DSL) + Android SDK
- **Procesamiento de Audio:** Android Audio Effects / Equalizer API

---

## 🚀 Compilación desde el Código Fuente

Si deseas compilar el proyecto manualmente desde la terminal:

1. **Clona el repositorio:**
   ```bash
   git clone [https://github.com/DavooPunk/Davoo-Player.git](https://github.com/DavooPunk/Davoo-Player.git)
   cd Davoo-Player
