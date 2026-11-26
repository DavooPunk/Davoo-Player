package com.example.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.model.TEN_BAND_CENTERS_HZ
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Real-time 10-band biquad graphic equalizer + dedicated Preamp processor for ExoPlayer's AudioSink.
 * Supports 16-bit and float PCM across 1..8 channels and cleanly bypasses unsupported formats or
 * disabled EQ states without interrupting active playback.
 */
@OptIn(UnstableApi::class)
class TenBandEqualizerAudioProcessor : BaseAudioProcessor() {

    private data class FilterCoeffs(
        val b0: Double,
        val b1: Double,
        val b2: Double,
        val a1: Double,
        val a2: Double
    )

    private class EqSnapshot(
        val enabled: Boolean,
        val isBypass: Boolean,
        val preampLinear: Double,
        val bandGainsMb: List<Int>
    )

    private class ChannelBiquadState(bandCount: Int) {
        val x1 = DoubleArray(bandCount)
        val x2 = DoubleArray(bandCount)
        val y1 = DoubleArray(bandCount)
        val y2 = DoubleArray(bandCount)

        fun reset() {
            x1.fill(0.0)
            x2.fill(0.0)
            y1.fill(0.0)
            y2.fill(0.0)
        }

        fun resetBand(bandIdx: Int) {
            x1[bandIdx] = 0.0
            x2[bandIdx] = 0.0
            y1[bandIdx] = 0.0
            y2[bandIdx] = 0.0
        }
    }

    @Volatile
    private var targetSnapshot = EqSnapshot(
        enabled = false,
        isBypass = true,
        preampLinear = 1.0,
        bandGainsMb = List(BAND_COUNT) { 0 }
    )

    private var appliedSampleRateHz: Int = 0
    private var appliedGainsMb: List<Int> = emptyList()
    private var coeffsBuilt: Boolean = false
    private var activeCount: Int = 0
    private val activeBandIndices = IntArray(BAND_COUNT)
    private val activeB0 = DoubleArray(BAND_COUNT)
    private val activeB1 = DoubleArray(BAND_COUNT)
    private val activeB2 = DoubleArray(BAND_COUNT)
    private val activeA1 = DoubleArray(BAND_COUNT)
    private val activeA2 = DoubleArray(BAND_COUNT)
    private var channelStates: Array<ChannelBiquadState> = emptyArray()

    fun updateSettings(
        eqEnabled: Boolean,
        preampDb: Float,
        bandGainsMb: List<Int>
    ) {
        val safePreamp = preampDb.takeIf { it.isFinite() }?.coerceIn(MIN_PREAMP_DB, MAX_PREAMP_DB) ?: 0f
        val normalizedGains = List(BAND_COUNT) { idx ->
            bandGainsMb.getOrElse(idx) { 0 }.coerceIn(MIN_BAND_MB, MAX_BAND_MB)
        }
        val linearPreamp = 10.0.pow(safePreamp.toDouble() / 20.0)
        val bypass = !eqEnabled || (
            normalizedGains.all { it == 0 } &&
                kotlin.math.abs(linearPreamp - 1.0) < 1e-4
            )
        targetSnapshot = EqSnapshot(
            enabled = eqEnabled,
            isBypass = bypass,
            preampLinear = linearPreamp,
            bandGainsMb = normalizedGains
        )
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val validEncoding = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val validChannels = inputAudioFormat.channelCount in 1..8
        val validRate = inputAudioFormat.sampleRate > 0
        return if (validEncoding && validChannels && validRate) {
            channelStates = Array(inputAudioFormat.channelCount) { ChannelBiquadState(BAND_COUNT) }
            rebuildCoeffsIfNeeded(inputAudioFormat.sampleRate, targetSnapshot.bandGainsMb, force = true)
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun onFlush() {
        channelStates.forEach { it.reset() }
    }

    override fun onReset() {
        channelStates = emptyArray()
        coeffsBuilt = false
        activeCount = 0
        appliedSampleRateHz = 0
        appliedGainsMb = emptyList()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val snap = targetSnapshot
        val outputBuffer = replaceOutputBuffer(remaining)

        if (snap.isBypass) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val sampleRate = inputAudioFormat.sampleRate
        val channels = inputAudioFormat.channelCount
        if (channels <= 0 || channelStates.size != channels) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        rebuildCoeffsIfNeeded(sampleRate, snap.bandGainsMb, force = false)
        val preamp = snap.preampLinear
        val count = activeCount
        val states = channelStates

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                val frameBytes = channels * 2
                while (inputBuffer.remaining() >= frameBytes) {
                    for (ch in 0 until channels) {
                        val rawSample = inputBuffer.short.toDouble() / 32768.0
                        val scaled = rawSample * preamp
                        val processed = if (count == 0) scaled else processSample(scaled, states[ch], count)
                        val clamped = softLimit(processed)
                        val outShort = (clamped * 32767.0).roundToInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            .toShort()
                        outputBuffer.putShort(outShort)
                    }
                }
            }

            C.ENCODING_PCM_FLOAT -> {
                val frameBytes = channels * 4
                while (inputBuffer.remaining() >= frameBytes) {
                    for (ch in 0 until channels) {
                        val rawSample = inputBuffer.float.toDouble()
                        val scaled = rawSample * preamp
                        val processed = if (count == 0) scaled else processSample(scaled, states[ch], count)
                        val clamped = softLimit(processed).toFloat()
                        outputBuffer.putFloat(clamped)
                    }
                }
            }
        }

        while (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer.get())
        }
        outputBuffer.flip()
    }

    private fun processSample(
        input: Double,
        state: ChannelBiquadState,
        count: Int
    ): Double {
        var x = input
        val x1Arr = state.x1
        val x2Arr = state.x2
        val y1Arr = state.y1
        val y2Arr = state.y2
        for (i in 0 until count) {
            val b = activeBandIndices[i]
            val sx1 = x1Arr[b]
            val sx2 = x2Arr[b]
            val sy1 = y1Arr[b]
            val sy2 = y2Arr[b]
            val y = activeB0[i] * x + activeB1[i] * sx1 + activeB2[i] * sx2 -
                activeA1[i] * sy1 - activeA2[i] * sy2
            x2Arr[b] = sx1
            x1Arr[b] = x
            y2Arr[b] = sy1
            val safeY = if (y.isFinite()) y else 0.0
            y1Arr[b] = safeY
            x = safeY
        }
        return x
    }

    private fun softLimit(sample: Double): Double {
        return if (sample > 0.98) {
            (0.98 + 0.02 * tanh((sample - 0.98) * 10.0)).coerceAtMost(1.0)
        } else if (sample < -0.98) {
            (-0.98 + 0.02 * tanh((sample + 0.98) * 10.0)).coerceAtLeast(-1.0)
        } else {
            sample
        }
    }

    private fun rebuildCoeffsIfNeeded(
        sampleRateHz: Int,
        gainsMb: List<Int>,
        force: Boolean
    ) {
        if (!force && sampleRateHz == appliedSampleRateHz && gainsMb == appliedGainsMb && coeffsBuilt) {
            return
        }
        appliedSampleRateHz = sampleRateHz
        appliedGainsMb = gainsMb
        var nextActiveCount = 0
        for (idx in 0 until BAND_COUNT) {
            val gainDb = gainsMb.getOrElse(idx) { 0 } / 100.0
            if (kotlin.math.abs(gainDb) < 0.01) {
                for (chState in channelStates) {
                    chState.resetBand(idx)
                }
                continue
            }
            val fc = TEN_BAND_CENTERS_HZ[idx].toDouble().coerceAtMost(sampleRateHz * 0.45)
            val c = when (idx) {
                0 -> computeLowShelfCoeffs(sampleRateHz.toDouble(), fc, gainDb)
                BAND_COUNT - 1 -> computeHighShelfCoeffs(sampleRateHz.toDouble(), fc, gainDb)
                else -> computePeakingCoeffs(sampleRateHz.toDouble(), fc, Q_PEAK, gainDb)
            }
            activeBandIndices[nextActiveCount] = idx
            activeB0[nextActiveCount] = c.b0
            activeB1[nextActiveCount] = c.b1
            activeB2[nextActiveCount] = c.b2
            activeA1[nextActiveCount] = c.a1
            activeA2[nextActiveCount] = c.a2
            nextActiveCount++
        }
        activeCount = nextActiveCount
        coeffsBuilt = true
    }

    private fun computePeakingCoeffs(
        fs: Double,
        fc: Double,
        q: Double,
        gainDb: Double
    ): FilterCoeffs {
        if (kotlin.math.abs(gainDb) < 0.01) return IDENTITY_COEFFS
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * fc / fs
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val b0 = 1.0 + alpha * a
        val b1 = -2.0 * cosW0
        val b2 = 1.0 - alpha * a
        val a0 = 1.0 + alpha / a
        val a1 = -2.0 * cosW0
        val a2 = 1.0 - alpha / a
        return FilterCoeffs(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    private fun computeLowShelfCoeffs(
        fs: Double,
        fc: Double,
        gainDb: Double
    ): FilterCoeffs {
        if (kotlin.math.abs(gainDb) < 0.01) return IDENTITY_COEFFS
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * fc / fs
        val cosW0 = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt((a + 1.0 / a) * (1.0 / SHELF_SLOPE - 1.0) + 2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
        val b0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha)
        val b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
        val b2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha)
        val a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
        val a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
        val a2 = (a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha
        return FilterCoeffs(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    private fun computeHighShelfCoeffs(
        fs: Double,
        fc: Double,
        gainDb: Double
    ): FilterCoeffs {
        if (kotlin.math.abs(gainDb) < 0.01) return IDENTITY_COEFFS
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * fc / fs
        val cosW0 = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt((a + 1.0 / a) * (1.0 / SHELF_SLOPE - 1.0) + 2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
        val b0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha)
        val b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
        val b2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha)
        val a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
        val a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
        val a2 = (a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha
        return FilterCoeffs(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    companion object {
        const val BAND_COUNT = 10
        const val MIN_BAND_MB = -1200
        const val MAX_BAND_MB = 1200
        const val MIN_PREAMP_DB = -12.0f
        const val MAX_PREAMP_DB = 12.0f
        private const val Q_PEAK = 1.41421356
        private const val SHELF_SLOPE = 0.9
        private val IDENTITY_COEFFS = FilterCoeffs(1.0, 0.0, 0.0, 0.0, 0.0)
    }
}
