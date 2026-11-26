package com.example.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Immutable
data class AudioOutputDeviceInfo(
    val deviceName: String = "Altavoz del dispositivo",
    val routeLabel: String = "Altavoz",
    val isExternal: Boolean = false,
    val isUsbDac: Boolean = false,
    val isBluetooth: Boolean = false,
    val isWired: Boolean = false,
    val maxSampleRateHz: Int? = null,
    val supportedSampleRatesHz: List<Int> = emptyList(),
    val maxBitDepthLabel: String? = null
) {
    val shortBadgeLabel: String
        get() = buildString {
            append(routeLabel)
            if (isExternal && deviceName.isNotBlank() && !deviceName.equals(routeLabel, ignoreCase = true)) {
                append(" · ")
                append(deviceName.take(22))
            }
            maxSampleRateHz?.takeIf { it > 0 }?.let { hz ->
                val khz = if (hz % 1000 == 0) "${hz / 1000}" else String.format(java.util.Locale.US, "%.1f", hz / 1000.0)
                append(" · $khz kHz")
            }
            maxBitDepthLabel?.let { depth ->
                append(" · $depth")
            }
        }
}

@Immutable
data class RawOutputDeviceCandidate(
    val type: Int,
    val productName: String,
    val sampleRates: List<Int>,
    val encodings: List<Int>
)

object AudioOutputMonitor {

    fun queryActiveOutputDevice(context: Context): AudioOutputDeviceInfo {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return AudioOutputDeviceInfo()
        val rawCandidates = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.isSink }
                .map { info ->
                    RawOutputDeviceCandidate(
                        type = info.type,
                        productName = info.productName?.toString()?.trim().orEmpty(),
                        sampleRates = info.sampleRates.toList(),
                        encodings = info.encodings.toList()
                    )
                }
        }.getOrDefault(emptyList())
        return selectPrimaryAudioOutput(rawCandidates)
    }

    fun selectPrimaryAudioOutput(candidates: List<RawOutputDeviceCandidate>): AudioOutputDeviceInfo {
        if (candidates.isEmpty()) return AudioOutputDeviceInfo()

        val sorted = candidates.sortedBy { priorityForDeviceType(it.type) }
        val chosen = sorted.firstOrNull { priorityForDeviceType(it.type) < Int.MAX_VALUE }
            ?: return AudioOutputDeviceInfo()

        val routeLabel = routeLabelForType(chosen.type)
        val isUsb = chosen.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            chosen.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            chosen.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        val isBt = chosen.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            chosen.type == AudioDeviceInfo.TYPE_HEARING_AID ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (chosen.type == AudioDeviceInfo.TYPE_BLE_HEADSET || chosen.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                chosen.type == AudioDeviceInfo.TYPE_BLE_BROADCAST)
        val isWired = chosen.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            chosen.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            chosen.type == AudioDeviceInfo.TYPE_LINE_ANALOG ||
            chosen.type == AudioDeviceInfo.TYPE_LINE_DIGITAL ||
            chosen.type == AudioDeviceInfo.TYPE_AUX_LINE
        val isExternal = chosen.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER &&
            chosen.type != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE

        val distinctRates = chosen.sampleRates.filter { it > 0 }.distinct().sorted()
        val maxRate = distinctRates.maxOrNull()
        val bitDepth = resolveMaxBitDepthLabel(chosen.encodings)
        val cleanName = chosen.productName
            .takeIf { it.isNotBlank() && !it.equals(Build.MODEL, ignoreCase = true) }
            ?: routeLabel

        return AudioOutputDeviceInfo(
            deviceName = cleanName,
            routeLabel = routeLabel,
            isExternal = isExternal,
            isUsbDac = isUsb,
            isBluetooth = isBt,
            isWired = isWired,
            maxSampleRateHz = maxRate,
            supportedSampleRatesHz = distinctRates,
            maxBitDepthLabel = bitDepth
        )
    }

    private fun priorityForDeviceType(type: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
        ) {
            return 2
        }
        return when (type) {
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> 0
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_AUX_LINE -> 1
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_HEARING_AID -> 2
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC -> 3
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 4
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> 5
            else -> Int.MAX_VALUE
        }
    }

    private fun routeLabelForType(type: Int): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
        ) {
            return "Bluetooth LE"
        }
        return when (type) {
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "DAC USB"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Audífonos"
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_AUX_LINE -> "Salida de línea"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_HEARING_AID -> "Bluetooth"
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Auricular"
            else -> "Altavoz"
        }
    }

    private fun resolveMaxBitDepthLabel(encodings: List<Int>): String? {
        if (encodings.isEmpty()) return null
        return when {
            AudioFormat.ENCODING_PCM_FLOAT in encodings -> "32-bit Float"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                AudioFormat.ENCODING_PCM_32BIT in encodings -> "32-bit"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                AudioFormat.ENCODING_PCM_24BIT_PACKED in encodings -> "24-bit"
            AudioFormat.ENCODING_PCM_16BIT in encodings -> "16-bit"
            else -> null
        }
    }
}

@Composable
fun rememberAudioOutputDeviceInfo(): AudioOutputDeviceInfo {
    val context = LocalContext.current
    var outputInfo by remember(context) {
        mutableStateOf(AudioOutputMonitor.queryActiveOutputDevice(context))
    }

    DisposableEffect(context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val callback = if (audioManager != null) {
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    outputInfo = AudioOutputMonitor.queryActiveOutputDevice(context)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    outputInfo = AudioOutputMonitor.queryActiveOutputDevice(context)
                }
            }
        } else null

        if (audioManager != null && callback != null) {
            runCatching { audioManager.registerAudioDeviceCallback(callback, null) }
            outputInfo = AudioOutputMonitor.queryActiveOutputDevice(context)
        }

        onDispose {
            if (audioManager != null && callback != null) {
                runCatching { audioManager.unregisterAudioDeviceCallback(callback) }
            }
        }
    }

    return outputInfo
}
