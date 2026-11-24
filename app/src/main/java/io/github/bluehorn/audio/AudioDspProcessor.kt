package io.github.bluehorn.audio

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Pure Kotlin real-time PCM16 DSP processor:
 * 1. Computes RMS input level for the VU meter.
 * 2. Applies a smooth-envelope noise gate to reduce background hiss and feedback.
 * 3. Applies software gain with a tanh soft-clipping limiter to prevent harsh distortion.
 * 4. Smoothly ramps mute/unmute transitions for click-free Push-to-Talk.
 */
class AudioDspProcessor(
    sampleRate: Int = 48000
) {
    private var gateEnvelope: Float = 0f
    private var transmitEnvelope: Float = 0f

    // Fast attack (~5 ms), smooth release (~120 ms)
    private val gateAttackCoeff: Float = (1f / (0.005f * sampleRate)).coerceIn(0.0005f, 1f)
    private val gateReleaseCoeff: Float = (1f / (0.120f * sampleRate)).coerceIn(0.0001f, 1f)

    // Smooth PTT mute/unmute ramp (~10 ms)
    private val transmitRampCoeff: Float = (1f / (0.010f * sampleRate)).coerceIn(0.0005f, 1f)

    fun reset() {
        gateEnvelope = 0f
        transmitEnvelope = 0f
    }

    /**
     * Processes PCM16 samples in place and returns live metering information.
     */
    fun processBuffer(
        buffer: ShortArray,
        length: Int,
        gain: Float,
        noiseGateThreshold: Float,
        isTransmitting: Boolean
    ): AudioMeterState {
        if (length <= 0) {
            return AudioMeterState()
        }

        var sumSquares = 0.0
        for (i in 0 until length) {
            val normalized = buffer[i] / 32768.0
            sumSquares += normalized * normalized
        }
        val rms = sqrt(sumSquares / length).toFloat()
        val gateOpen = noiseGateThreshold <= 0.0001f || rms >= noiseGateThreshold

        var clipping = false
        val clampedGain = gain.coerceIn(0.1f, 5.0f)

        for (i in 0 until length) {
            // Smooth noise gate envelope
            gateEnvelope = if (gateOpen) {
                (gateEnvelope + gateAttackCoeff).coerceAtMost(1f)
            } else {
                (gateEnvelope - gateReleaseCoeff).coerceAtLeast(0f)
            }

            // Smooth Push-to-Talk transmit envelope
            transmitEnvelope = if (isTransmitting) {
                (transmitEnvelope + transmitRampCoeff).coerceAtMost(1f)
            } else {
                (transmitEnvelope - transmitRampCoeff).coerceAtLeast(0f)
            }

            val totalMultiplier = clampedGain * gateEnvelope * transmitEnvelope
            if (totalMultiplier <= 0.0001f) {
                buffer[i] = 0
                continue
            }

            val raw = (buffer[i] / 32768f) * totalMultiplier
            if (abs(raw) > 0.95f) {
                clipping = true
            }

            // Soft-clipping limiter: transparent below ~0.6, smooth saturation near +/-1.0
            val limited = if (abs(raw) <= 0.6f) {
                raw
            } else {
                tanh(raw.toDouble()).toFloat()
            }

            val outSample = (limited * 32767f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[i] = outSample.toShort()
        }

        // Visual level boosted slightly for human-readable VU meter response
        val visualLevel = (rms * 3.2f).coerceIn(0f, 1f)
        return AudioMeterState(
            inputLevel = visualLevel,
            isGated = !gateOpen && isTransmitting,
            isClipping = clipping && isTransmitting
        )
    }
}
