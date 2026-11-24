package io.github.bluehorn.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AudioDspProcessorTest {

    @Test
    fun mutedWhenNotTransmitting() {
        val dsp = AudioDspProcessor(sampleRate = 48000)
        val buffer = ShortArray(480) { 12000 }

        // Run a few buffers to ensure envelope settles to zero
        repeat(5) {
            dsp.processBuffer(
                buffer = buffer,
                length = buffer.size,
                gain = 2.0f,
                noiseGateThreshold = 0.0f,
                isTransmitting = false
            )
        }

        assertEquals(0, buffer.last().toInt())
    }

    @Test
    fun softLimiterPreventsOverflowAtHighGain() {
        val dsp = AudioDspProcessor(sampleRate = 48000)
        val buffer = ShortArray(480) { 20000 }

        var lastMeter = AudioMeterState()
        repeat(10) {
            for (i in buffer.indices) buffer[i] = 20000
            lastMeter = dsp.processBuffer(
                buffer = buffer,
                length = buffer.size,
                gain = 4.0f,
                noiseGateThreshold = 0.0f,
                isTransmitting = true
            )
        }

        assertTrue(lastMeter.isClipping)
        assertTrue(buffer.all { abs(it.toInt()) <= Short.MAX_VALUE })
    }

    @Test
    fun noiseGateMarksQuietSignalAsGated() {
        val dsp = AudioDspProcessor(sampleRate = 48000)
        val quietBuffer = ShortArray(480) { 20 }

        val meter = dsp.processBuffer(
            buffer = quietBuffer,
            length = quietBuffer.size,
            gain = 1.5f,
            noiseGateThreshold = 0.05f,
            isTransmitting = true
        )

        assertTrue(meter.isGated)
        assertFalse(meter.isClipping)
    }
}
