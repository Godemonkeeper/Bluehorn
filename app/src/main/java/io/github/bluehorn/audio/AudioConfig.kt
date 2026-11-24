package io.github.bluehorn.audio

enum class BroadcastMode {
    CONTINUOUS,
    PUSH_TO_TALK
}

data class AudioConfig(
    val gain: Float = 1.5f,
    val noiseGateThreshold: Float = 0.015f,
    val enableEchoCanceler: Boolean = true,
    val enableNoiseSuppressor: Boolean = true,
    val bluetoothOnlyLock: Boolean = true,
    val mode: BroadcastMode = BroadcastMode.CONTINUOUS
)

data class AudioMeterState(
    val inputLevel: Float = 0f,
    val isGated: Boolean = false,
    val isClipping: Boolean = false
)

data class EngineStatus(
    val isServiceRunning: Boolean = false,
    val isTransmitting: Boolean = false,
    val sampleRate: Int = 48000,
    val echoCancelerAvailable: Boolean = false,
    val noiseSuppressorAvailable: Boolean = false,
    val lastWarning: String? = null
)
