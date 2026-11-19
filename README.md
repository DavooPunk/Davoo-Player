# 🎵 Davoo Player

**Davoo Player** es un reproductor de música local nativo para Android diseñado para gestionar bibliotecas extensas de audio de alta fidelidad (FLAC y formatos sin pérdida), con una interfaz oscura minimalista, acceso directo a metadatos y un motor de ecualización avanzado.

---

## ✨ Características Principales

* **Soporte de Audio Lossless & Metadatos:** Reproducción fluida de archivos **FLAC**, MP3, WAV, OGG y M4A con indicadores visuales de formato y visualización detallada de metadatos a la mano[cite: 4, 5].
* **Ecualizador Gráfico de 10 Bandas + Preamp:** Ajuste fino desde 31 Hz hasta 16 kHz (-12 dB a +12 dB) acompañado de control de preamplificador (Preamp) para evitar distorsión (*clipping*)[cite: 5].
* **Perfiles de Audífonos (Estilo AutoEq / Poweramp):** Incluye curvas de ecualización calibradas para modelos populares de audífonos e IEMs (Sony, Sennheiser, Audio-Technica, Apple, Samsung, KZ, entre otros) y presets clásicos personalizables[cite: 5].
* **Navegación Rápida y Búsqueda Global:** Organización por **Álbumes, Artistas, Géneros y Carpetas**, con buscador global fijo siempre accesible en la cabecera[cite: 4].
* **Gestión Optimizada de Carpetas:** Escaneo y eliminación por lotes (*batch processing*) que permite agregar o quitar carpetas con cientos de álbumes de forma instantánea sin bloquear la interfaz.
* **Interfaz Dark Mode:** Diseño limpio con paleta oscura y acentos cálidos pensado para uso prolongado y bajo consumo.

---

## 🛠️ Stack Tecnológico

* **Lenguaje:** Kotlin
* **Sistema de Construcción:** Gradle (Kotlin DSL) + Android SDK
* **Procesamiento de Audio:** Android Audio Effects / Equalizer API

---

## 🚀 Compilación desde el Código Fuente

Si deseas compilar el proyecto manualmente desde la terminal:

1. **Clona el repositorio:**
   ```bash
   git clone [https://github.com/DavooPunk/Davoo-Player.git](https://github.com/DavooPunk/Davoo-Player.git)
   cd Davoo-Player
