package com.example.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.example.model.AudioSettings
import com.example.model.EqBandInfo

/**
 * Manages hardware/OS Android audio DSP effects (5-band Equalizer, BassBoost, Virtualizer,
 * and LoudnessEnhancer for automatic volume normalization on untagged tracks)
 * bound to ExoPlayer's audioSessionId. Gracefully handles headless/emulator/unit-test
 * environments where native AudioEffect HAL isn't present.
 */
class AudioEffectsController {

    private var currentSessionId: Int = 0
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var latestSettings: AudioSettings = AudioSettings()
    private var currentTrackHasReplayGainTag: Boolean = false

    private var cachedBandCount: Int = 0
    private var cachedMinMb: Int = -1500
    private var cachedMaxMb: Int = 1500
    private var cachedCenterFreqsHz: List<Int> = emptyList()

    private var lastAppliedEqEnabled: Boolean? = null
    private var lastAppliedGainsMb: List<Int>? = null
    private var lastAppliedBass: Int? = null
    private var lastAppliedVirt: Int? = null
    private var lastAppliedLoudnessActive: Boolean? = null

    @Synchronized
    fun attachToSession(audioSessionId: Int, settings: AudioSettings = latestSettings) {
        if (audioSessionId <= 0) return
        if (audioSessionId != currentSessionId) {
            releaseInternal()
            currentSessionId = audioSessionId
        }
        applySettingsInternal(settings, force = true)
    }

    @Synchronized
    fun applySettings(settings: AudioSettings) {
        applySettingsInternal(settings, force = false)
    }

    @Synchronized
    fun setTrackHasReplayGainTag(hasTag: Boolean) {
        if (currentTrackHasReplayGainTag == hasTag) return
        currentTrackHasReplayGainTag = hasTag
        if (currentSessionId > 0) {
            applyLoudnessNormalizationInternal(latestSettings, force = false)
        }
    }

    @Synchronized
    fun isLoudnessNormalizationRequested(): Boolean {
        return latestSettings.replayGainEnabled && !currentTrackHasReplayGainTag
    }

    private fun applySettingsInternal(settings: AudioSettings, force: Boolean) {
        latestSettings = settings
        val sessionId = currentSessionId
        if (sessionId <= 0) return

        // Equalizer: lazily instantiate only when enabled; release when turned off
        if (settings.eqEnabled) {
            if (equalizer == null) {
                initEqualizer(sessionId)
            }
            val eq = equalizer
            if (eq != null) {
                val bandCount = cachedBandCount.coerceIn(0, MAX_BAND_COUNT)
                val minMb = cachedMinMb
                val maxMb = cachedMaxMb.coerceAtLeast(minMb + 1)
                val clampedGains = (0 until bandCount).map { i ->
                    settings.bandGainsMb.getOrElse(i) { 0 }.coerceIn(minMb, maxMb)
                }
                val needsUpdate = force ||
                    lastAppliedEqEnabled != true ||
                    lastAppliedGainsMb != clampedGains
                if (needsUpdate) {
                    val eqOk = runCatching {
                        if (!eq.enabled) {
                            eq.enabled = true
                        }
                        for (i in 0 until bandCount) {
                            eq.setBandLevel(i.toShort(), clampedGains[i].toShort())
                        }
                    }.isSuccess
                    if (eqOk) {
                        lastAppliedEqEnabled = true
                        lastAppliedGainsMb = clampedGains
                    } else {
                        releaseEqualizer()
                    }
                }
            }
        } else if (equalizer != null || force || lastAppliedEqEnabled == true) {
            releaseEqualizer()
            lastAppliedEqEnabled = false
            lastAppliedGainsMb = null
        }

        // BassBoost: lazily instantiate only when strength > 0
        val bassStrength = settings.bassBoostStrength.coerceIn(0, 1000)
        if (bassStrength > 0) {
            if (bassBoost == null) {
                runCatching { bassBoost = BassBoost(0, sessionId) }
                    .onFailure { releaseBassBoost() }
            }
            val bb = bassBoost
            if (bb != null && (force || lastAppliedBass != bassStrength)) {
                val bbOk = runCatching {
                    if (!bb.enabled) bb.enabled = true
                    if (bb.strengthSupported) {
                        bb.setStrength(bassStrength.toShort())
                    }
                }.isSuccess
                if (bbOk) {
                    lastAppliedBass = bassStrength
                } else {
                    releaseBassBoost()
                }
            }
        } else if (bassBoost != null || force || (lastAppliedBass ?: 0) > 0) {
            releaseBassBoost()
            lastAppliedBass = 0
        }

        // Virtualizer: lazily instantiate only when strength > 0
        val virtStrength = settings.virtualizerStrength.coerceIn(0, 1000)
        if (virtStrength > 0) {
            if (virtualizer == null) {
                runCatching { virtualizer = Virtualizer(0, sessionId) }
                    .onFailure { releaseVirtualizer() }
            }
            val virt = virtualizer
            if (virt != null && (force || lastAppliedVirt != virtStrength)) {
                val virtOk = runCatching {
                    if (!virt.enabled) virt.enabled = true
                    if (virt.strengthSupported) {
                        virt.setStrength(virtStrength.toShort())
                    }
                }.isSuccess
                if (virtOk) {
                    lastAppliedVirt = virtStrength
                } else {
                    releaseVirtualizer()
                }
            }
        } else if (virtualizer != null || force || (lastAppliedVirt ?: 0) > 0) {
            releaseVirtualizer()
            lastAppliedVirt = 0
        }

        applyLoudnessNormalizationInternal(settings, force)
    }

    private fun applyLoudnessNormalizationInternal(settings: AudioSettings, force: Boolean) {
        val sessionId = currentSessionId
        if (sessionId <= 0) return

        // Use LoudnessEnhancer as gentle automatic leveler when Volume Normalization is ON
        // and the current track does not provide an explicit REPLAYGAIN_TRACK_GAIN tag.
        val shouldEnableEnhancer = settings.replayGainEnabled && !currentTrackHasReplayGainTag
        if (shouldEnableEnhancer) {
            if (loudnessEnhancer == null) {
                runCatching { loudnessEnhancer = LoudnessEnhancer(sessionId) }
                    .onFailure { releaseLoudnessEnhancer() }
            }
            val enhancer = loudnessEnhancer
            if (enhancer != null && (force || lastAppliedLoudnessActive != true)) {
                val ok = runCatching {
                    enhancer.setTargetGain(NORMALIZATION_TARGET_GAIN_MB)
                    if (!enhancer.enabled) enhancer.enabled = true
                }.isSuccess
                if (ok) {
                    lastAppliedLoudnessActive = true
                } else {
                    releaseLoudnessEnhancer()
                }
            }
        } else if (loudnessEnhancer != null || force || lastAppliedLoudnessActive == true) {
            releaseLoudnessEnhancer()
            lastAppliedLoudnessActive = false
        }
    }

    private fun initEqualizer(sessionId: Int) {
        val created = runCatching { Equalizer(0, sessionId) }.getOrNull() ?: return
        val metadataOk = runCatching {
            val rawCount = created.numberOfBands.toInt().coerceIn(0, MAX_BAND_COUNT)
            val range = created.bandLevelRange
            val minMb = range?.getOrElse(0) { -1500 }?.toInt() ?: -1500
            val maxMb = (range?.getOrElse(1) { 1500 }?.toInt() ?: 1500).coerceAtLeast(minMb + 1)
            val centers = (0 until rawCount).map { idx ->
                val rawMilliHz = created.getCenterFreq(idx.toShort())
                (rawMilliHz / 1000).takeIf { it > 0 } ?: DEFAULT_CENTERS_HZ.getOrElse(idx) { 1000 }
            }
            cachedBandCount = rawCount
            cachedMinMb = minMb
            cachedMaxMb = maxMb
            cachedCenterFreqsHz = centers
        }.isSuccess

        if (metadataOk && cachedBandCount > 0) {
            equalizer = created
        } else {
            runCatching { created.release() }
            cachedBandCount = 0
            cachedCenterFreqsHz = emptyList()
        }
    }

    @Synchronized
    fun getBands(settings: AudioSettings = latestSettings): List<EqBandInfo> {
        if (equalizer != null && cachedBandCount > 0 && cachedCenterFreqsHz.size == cachedBandCount) {
            val minMb = cachedMinMb
            val maxMb = cachedMaxMb.coerceAtLeast(minMb + 1)
            return cachedCenterFreqsHz.mapIndexed { idx, hz ->
                EqBandInfo(
                    index = idx,
                    centerFreqHz = hz,
                    minLevelMb = minMb,
                    maxLevelMb = maxMb,
                    currentLevelMb = settings.bandGainsMb.getOrElse(idx) { 0 }.coerceIn(minMb, maxMb)
                )
            }
        }
        return DEFAULT_CENTERS_HZ.mapIndexed { idx, hz ->
            EqBandInfo(
                index = idx,
                centerFreqHz = hz,
                minLevelMb = -1500,
                maxLevelMb = 1500,
                currentLevelMb = settings.bandGainsMb.getOrElse(idx) { 0 }.coerceIn(-1500, 1500)
            )
        }
    }

    @Synchronized
    fun release() {
        releaseInternal()
    }

    private fun releaseInternal() {
        releaseEqualizer()
        releaseBassBoost()
        releaseVirtualizer()
        releaseLoudnessEnhancer()
        currentSessionId = 0
        lastAppliedEqEnabled = null
        lastAppliedGainsMb = null
        lastAppliedBass = null
        lastAppliedVirt = null
        lastAppliedLoudnessActive = null
    }

    private fun releaseEqualizer() {
        runCatching { equalizer?.enabled = false }
        runCatching { equalizer?.release() }
        equalizer = null
        cachedBandCount = 0
        cachedMinMb = -1500
        cachedMaxMb = 1500
        cachedCenterFreqsHz = emptyList()
    }

    private fun releaseBassBoost() {
        runCatching { bassBoost?.enabled = false }
        runCatching { bassBoost?.release() }
        bassBoost = null
    }

    private fun releaseVirtualizer() {
        runCatching { virtualizer?.enabled = false }
        runCatching { virtualizer?.release() }
        virtualizer = null
    }

    private fun releaseLoudnessEnhancer() {
        runCatching { loudnessEnhancer?.enabled = false }
        runCatching { loudnessEnhancer?.release() }
        loudnessEnhancer = null
    }

    companion object {
        private const val MAX_BAND_COUNT = 16
        private const val NORMALIZATION_TARGET_GAIN_MB = 350
        private val DEFAULT_CENTERS_HZ = listOf(60, 230, 910, 3600, 14000)
    }
}
