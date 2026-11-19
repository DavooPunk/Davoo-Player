package com.example.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Real-time L/R stereo balance processor for 16-bit and float stereo PCM audio streams.
 * Cleanly bypasses mono, multichannel, or other PCM encodings by returning [AudioProcessor.AudioFormat.NOT_SET].
 */
@OptIn(UnstableApi::class)
class StereoBalanceAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var balance: Float = 0f

    fun setBalance(value: Float) {
        balance = value.coerceIn(-1f, 1f)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val isSupportedEncoding = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        return if (isSupportedEncoding && inputAudioFormat.channelCount == 2) {
            inputAudioFormat
        } else {
            // Returning NOT_SET marks this processor inactive in BaseAudioProcessor
            // so ExoPlayer's AudioProcessingPipeline bypasses it without failing playback.
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val currentBalance = balance
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        if (currentBalance == 0f) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val leftGain = if (currentBalance > 0f) (1f - currentBalance).coerceIn(0f, 1f) else 1f
        val rightGain = if (currentBalance < 0f) (1f + currentBalance).coerceIn(0f, 1f) else 1f

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                while (inputBuffer.remaining() >= 4) {
                    val left = inputBuffer.short
                    val right = inputBuffer.short
                    val scaledLeft = (left * leftGain).roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                    val scaledRight = (right * rightGain).roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                    outputBuffer.putShort(scaledLeft)
                    outputBuffer.putShort(scaledRight)
                }
            }

            C.ENCODING_PCM_FLOAT -> {
                while (inputBuffer.remaining() >= 8) {
                    val left = inputBuffer.float
                    val right = inputBuffer.float
                    outputBuffer.putFloat((left * leftGain).coerceIn(-1f, 1f))
                    outputBuffer.putFloat((right * rightGain).coerceIn(-1f, 1f))
                }
            }
        }

        while (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer.get())
        }
        outputBuffer.flip()
    }
}
