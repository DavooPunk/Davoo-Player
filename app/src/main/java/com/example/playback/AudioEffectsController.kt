package com.example.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import com.example.model.AudioSettings
import com.example.model.EqBandInfo
import com.example.model.EqPreset
import com.example.model.TEN_BAND_CENTERS_HZ

import kotlin.math.roundToInt

/**
 * Manages OS Android audio DSP effects (BassBoost, Virtualizer, and LoudnessEnhancer for
 * ReplayGain positive preamp headroom & volume normalization) bound to ExoPlayer's audioSessionId,
 * while providing unified 10-band EQ metadata aligned with [TenBandEqualizerAudioProcessor].
 */
class AudioEffectsController {

    private var currentSessionId: Int = 0
    private var bassBoost: BassBoost? = null
    @Suppress("DEPRECATION")
    private var virtualizer: android.media.audiofx.Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var latestSettings: AudioSettings = AudioSettings()
    private var currentTrackHasReplayGainTag: Boolean = false
    private var replayGainBoostDb: Float = 0f

    private var lastAppliedBass: Int? = null
    private var lastAppliedVirt: Int? = null
    private var lastAppliedLoudnessActive: Boolean? = null
    private var lastAppliedLoudnessGainMb: Int? = null

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
    fun setReplayGainBoostDb(boostDb: Float) {
        val safeBoost = if (boostDb.isFinite()) boostDb.coerceIn(0f, 12f) else 0f
        if (kotlin.math.abs(replayGainBoostDb - safeBoost) < 0.01f) return
        replayGainBoostDb = safeBoost
        if (currentSessionId > 0) {
            applyLoudnessNormalizationInternal(latestSettings, force = false)
        }
    }

    @Synchronized
    fun currentReplayGainBoostMb(): Int {
        return if (latestSettings.replayGainEnabled && replayGainBoostDb > 0.05f) {
            (replayGainBoostDb * 100f).roundToInt().coerceIn(0, 1200)
        } else {
            0
        }
    }

    @Synchronized
    fun isLoudnessNormalizationRequested(): Boolean {
        return latestSettings.replayGainEnabled &&
            (replayGainBoostDb > 0.05f || !currentTrackHasReplayGainTag)
    }

    @Suppress("DEPRECATION")
    private fun applySettingsInternal(settings: AudioSettings, force: Boolean) {
        latestSettings = settings
        val sessionId = currentSessionId
        if (sessionId <= 0) return

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
                runCatching { virtualizer = android.media.audiofx.Virtualizer(0, sessionId) }
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

        val explicitBoostMb = if (settings.replayGainEnabled && replayGainBoostDb > 0.05f) {
            (replayGainBoostDb * 100f).roundToInt().coerceIn(0, 1200)
        } else {
            0
        }
        val shouldEnableEnhancer = explicitBoostMb > 0
        if (shouldEnableEnhancer) {
            if (loudnessEnhancer == null) {
                runCatching { loudnessEnhancer = LoudnessEnhancer(sessionId) }
                    .onFailure { releaseLoudnessEnhancer() }
            }
            val enhancer = loudnessEnhancer
            if (enhancer != null && (force || lastAppliedLoudnessActive != true || lastAppliedLoudnessGainMb != explicitBoostMb)) {
                val ok = runCatching {
                    enhancer.setTargetGain(explicitBoostMb)
                    if (!enhancer.enabled) enhancer.enabled = true
                }.isSuccess
                if (ok) {
                    lastAppliedLoudnessActive = true
                    lastAppliedLoudnessGainMb = explicitBoostMb
                } else {
                    releaseLoudnessEnhancer()
                }
            }
        } else if (loudnessEnhancer != null || force || lastAppliedLoudnessActive == true) {
            releaseLoudnessEnhancer()
            lastAppliedLoudnessActive = false
            lastAppliedLoudnessGainMb = 0
        }
    }

    @Synchronized
    fun getBands(settings: AudioSettings = latestSettings): List<EqBandInfo> {
        val normalized = EqPreset.normalizeToTenBands(settings.bandGainsMb)
        return TEN_BAND_CENTERS_HZ.mapIndexed { idx, hz ->
            EqBandInfo(
                index = idx,
                centerFreqHz = hz,
                minLevelMb = TenBandEqualizerAudioProcessor.MIN_BAND_MB,
                maxLevelMb = TenBandEqualizerAudioProcessor.MAX_BAND_MB,
                currentLevelMb = normalized.getOrElse(idx) { 0 }
                    .coerceIn(TenBandEqualizerAudioProcessor.MIN_BAND_MB, TenBandEqualizerAudioProcessor.MAX_BAND_MB)
            )
        }
    }

    @Synchronized
    fun release() {
        releaseInternal()
    }

    private fun releaseInternal() {
        releaseBassBoost()
        releaseVirtualizer()
        releaseLoudnessEnhancer()
        currentSessionId = 0
        lastAppliedBass = null
        lastAppliedVirt = null
        lastAppliedLoudnessActive = null
        lastAppliedLoudnessGainMb = null
    }

    private fun releaseBassBoost() {
        runCatching { bassBoost?.enabled = false }
        runCatching { bassBoost?.release() }
        bassBoost = null
    }

    @Suppress("DEPRECATION")
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
}
