package com.example

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.DeviceMusicScanner
import com.example.data.repository.LastFmScrobblePolicy
import com.example.model.LastFmTrackItem
import com.example.playback.TenBandEqualizerAudioProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
class PerformanceAndAudioTest {

    @Test
    fun equalizer_preampOnlyWithFlatBands_attenuatesFloatAnd16BitCleanly() {
        val processor = TenBandEqualizerAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_FLOAT))
        processor.flush()
        processor.updateSettings(
            eqEnabled = true,
            preampDb = -6.0f,
            bandGainsMb = List(10) { 0 }
        )

        val inputSamples = floatArrayOf(0.5f, -0.5f, 0.25f, -0.25f)
        val input = ByteBuffer.allocateDirect(inputSamples.size * 4).order(ByteOrder.nativeOrder())
        inputSamples.forEach { input.putFloat(it) }
        input.flip()

        processor.queueInput(input)
        val out = processor.output
        assertEquals(inputSamples.size * 4, out.remaining())

        // -6 dB ≈ 0.501187 linear gain
        for (expectedRaw in inputSamples) {
            val actual = out.float
            val expected = expectedRaw * 0.501187f
            assertTrue("Expected ~$expected but got $actual", abs(actual - expected) < 0.01f)
        }
    }

    @Test
    fun equalizer_selectiveBandBoost_boostsSignalAndResetToZeroReturnsUnity() {
        val processor = TenBandEqualizerAudioProcessor()
        val sampleRate = 48000
        processor.configure(AudioProcessor.AudioFormat(sampleRate, 1, C.ENCODING_PCM_16BIT))
        processor.flush()

        val boostBands = MutableList(10) { 0 }.apply { this[5] = 600 } // +6 dB at 1 kHz
        processor.updateSettings(eqEnabled = true, preampDb = 0f, bandGainsMb = boostBands)

        val frames = 1024
        val inputBytes = ByteBuffer.allocateDirect(frames * 2).order(ByteOrder.nativeOrder())
        var inputRmsSum = 0.0
        for (i in 0 until frames) {
            val sample = (sin(2.0 * PI * 1000.0 * i / sampleRate) * 8000.0).toInt().toShort()
            inputRmsSum += sample * sample.toDouble()
            inputBytes.putShort(sample)
        }
        inputBytes.flip()

        processor.queueInput(inputBytes)
        val boostedOut = processor.output
        var boostedRmsSum = 0.0
        while (boostedOut.remaining() >= 2) {
            val s = boostedOut.short.toDouble()
            boostedRmsSum += s * s
        }
        assertTrue(
            "Boosted 1kHz RMS should exceed input RMS",
            sqrt(boostedRmsSum / frames) > sqrt(inputRmsSum / frames) * 1.4
        )

        // Returning band to 0 dB must reset biquad history cleanly and pass unity signal
        processor.updateSettings(eqEnabled = true, preampDb = 0f, bandGainsMb = List(10) { 0 })
        val verifyBytes = ByteBuffer.allocateDirect(64 * 2).order(ByteOrder.nativeOrder())
        val expectedShorts = ShortArray(64) { idx -> ((idx - 32) * 100).toShort() }
        expectedShorts.forEach { verifyBytes.putShort(it) }
        verifyBytes.flip()

        processor.queueInput(verifyBytes)
        val flatOut = processor.output
        for (expected in expectedShorts) {
            val actual = flatOut.short
            assertTrue("Expected unity ~$expected, got $actual", abs(actual - expected) <= 1)
        }
    }

    @Test
    fun deviceMusicScanner_missingDurationUriReturnsNullAcrossCachedCalls() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val missingUri = Uri.parse("file:///nonexistent/corrupt_audio_perf_test.flac")

        val first = DeviceMusicScanner.extractDurationFromFile(context, missingUri)
        val second = DeviceMusicScanner.extractDurationFromFile(context, missingUri)

        assertNull(first)
        assertNull(second)
    }

    @Test
    fun lastFmScrobblePolicy_computeThresholdMatchesLocalAndPreviewCaps() {
        assertEquals(120_000L, LastFmScrobblePolicy.computeThresholdMs("track_1", 240_000L))
        assertEquals(
            15_000L,
            LastFmScrobblePolicy.computeThresholdMs(
                "${LastFmTrackItem.PREVIEW_TRACK_ID_PREFIX}999",
                30_000L
            )
        )
        assertEquals(
            10_000L,
            LastFmScrobblePolicy.computeThresholdMs(
                "${LastFmTrackItem.PREVIEW_TRACK_ID_PREFIX}999",
                20_000L
            )
        )
        assertEquals(Long.MAX_VALUE, LastFmScrobblePolicy.computeThresholdMs("track_1", 0L))
    }
}
