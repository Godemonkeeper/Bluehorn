package io.github.bluehorn.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Low-latency real-time audio loopback engine:
 * Phone Built-in Mic (AudioRecord) -> DSP (Noise Gate + Gain + Soft Limiter) -> Bluetooth A2DP Speaker (AudioTrack)
 */
class MegaphoneEngine(
    private val onMeterUpdate: (AudioMeterState) -> Unit,
    private val onStatusChange: (EngineStatus) -> Unit
) {
    private val running = AtomicBoolean(false)
    private val pttPressed = AtomicBoolean(false)
    private val configRef = AtomicReference(AudioConfig())

    private var workerThread: Thread? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    @Volatile
    private var targetOutputDevice: AudioDeviceInfo? = null

    @Volatile
    private var targetInputMic: AudioDeviceInfo? = null

    fun updateConfig(newConfig: AudioConfig) {
        configRef.set(newConfig)
        try {
            echoCanceler?.enabled = newConfig.enableEchoCanceler
            noiseSuppressor?.enabled = newConfig.enableNoiseSuppressor
        } catch (_: IllegalStateException) {
            // Ignored if effect is being released
        }
        publishStatus()
    }

    fun setPushToTalkPressed(pressed: Boolean) {
        pttPressed.set(pressed)
        publishStatus()
    }

    fun updateRouting(outputDevice: AudioDeviceInfo?, builtInMic: AudioDeviceInfo?) {
        targetOutputDevice = outputDevice
        targetInputMic = builtInMic

        if (running.get()) {
            val cfg = configRef.get()
            if (cfg.bluetoothOnlyLock && outputDevice == null) {
                stop(warning = "蓝牙音响已断开，已自动停止扩音以防止手机扬声器啸叫")
                return
            }
            try {
                audioTrack?.preferredDevice = outputDevice
                if (builtInMic != null) {
                    audioRecord?.preferredDevice = builtInMic
                }
            } catch (_: Exception) {
                // Safe fallback if device changes mid-release
            }
        }
    }

    fun isRunning(): Boolean = running.get()

    @SuppressLint("MissingPermission")
    fun start(
        config: AudioConfig,
        outputDevice: AudioDeviceInfo?,
        builtInMic: AudioDeviceInfo?
    ): Boolean {
        if (running.get()) return true

        configRef.set(config)
        targetOutputDevice = outputDevice
        targetInputMic = builtInMic

        if (config.bluetoothOnlyLock && outputDevice == null) {
            onStatusChange(
                EngineStatus(
                    isServiceRunning = false,
                    isTransmitting = false,
                    lastWarning = "未检测到蓝牙音响，请先连接蓝牙音响（或关闭「仅蓝牙输出安全锁」）"
                )
            )
            return false
        }

        val sampleRate = selectSupportedSampleRate()
        val inBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(1024)

        val outBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(1024)

        // Frame chunk size ~10ms for low latency and responsive VU metering
        val frameShorts = (sampleRate / 100).coerceAtLeast(256)

        val record = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(inBufferSize * 2)
                .build()
        } catch (e: Exception) {
            onStatusChange(
                EngineStatus(
                    isServiceRunning = false,
                    isTransmitting = false,
                    lastWarning = "无法初始化麦克风：${e.localizedMessage ?: "未知错误"}"
                )
            )
            return false
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            onStatusChange(
                EngineStatus(
                    isServiceRunning = false,
                    isTransmitting = false,
                    lastWarning = "麦克风初始化失败，请检查录音权限或是否有其他应用占用麦克风"
                )
            )
            return false
        }

        // Pin input strictly to phone's built-in microphone so Bluetooth stays in high-quality A2DP
        if (builtInMic != null) {
            record.preferredDevice = builtInMic
        }

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(outBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (e: Exception) {
            record.release()
            onStatusChange(
                EngineStatus(
                    isServiceRunning = false,
                    isTransmitting = false,
                    lastWarning = "无法初始化音频输出：${e.localizedMessage ?: "未知错误"}"
                )
            )
            return false
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            record.release()
            track.release()
            onStatusChange(
                EngineStatus(
                    isServiceRunning = false,
                    isTransmitting = false,
                    lastWarning = "音频输出初始化失败，请重试"
                )
            )
            return false
        }

        if (outputDevice != null) {
            track.preferredDevice = outputDevice
        }

        // Attach hardware audio effects if supported by device
        val aecAvail = AcousticEchoCanceler.isAvailable()
        if (aecAvail) {
            echoCanceler = AcousticEchoCanceler.create(record.audioSessionId)?.apply {
                enabled = config.enableEchoCanceler
            }
        }

        val nsAvail = NoiseSuppressor.isAvailable()
        if (nsAvail) {
            noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)?.apply {
                enabled = config.enableNoiseSuppressor
            }
        }

        audioRecord = record
        audioTrack = track
        running.set(true)

        val dsp = AudioDspProcessor(sampleRate = sampleRate)

        onStatusChange(
            EngineStatus(
                isServiceRunning = true,
                isTransmitting = isCurrentlyTransmitting(config),
                sampleRate = sampleRate,
                echoCancelerAvailable = aecAvail,
                noiseSuppressorAvailable = nsAvail,
                lastWarning = null
            )
        )

        workerThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val pcmBuffer = ShortArray(frameShorts)
            var lastMeterTimestamp = 0L

            try {
                record.startRecording()
                track.play()

                while (running.get()) {
                    val readCount = record.read(pcmBuffer, 0, pcmBuffer.size, AudioRecord.READ_BLOCKING)
                    if (readCount <= 0) continue

                    val currentCfg = configRef.get()
                    val transmitting = isCurrentlyTransmitting(currentCfg)

                    val meter = dsp.processBuffer(
                        buffer = pcmBuffer,
                        length = readCount,
                        gain = currentCfg.gain,
                        noiseGateThreshold = currentCfg.noiseGateThreshold,
                        isTransmitting = transmitting
                    )

                    track.write(pcmBuffer, 0, readCount, AudioTrack.WRITE_BLOCKING)

                    val now = System.currentTimeMillis()
                    if (now - lastMeterTimestamp >= 45L) {
                        lastMeterTimestamp = now
                        onMeterUpdate(meter)
                    }
                }
            } catch (_: Exception) {
                // Handled during stop cleanup
            } finally {
                cleanupAudioResources()
                onMeterUpdate(AudioMeterState())
            }
        }, "BlueHorn-AudioThread").apply {
            start()
        }

        return true
    }

    fun stop(warning: String? = null) {
        val wasRunning = running.getAndSet(false)
        pttPressed.set(false)

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
        } catch (_: Exception) {
        }

        workerThread?.let { thread ->
            if (thread !== Thread.currentThread()) {
                try {
                    thread.join(400)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        workerThread = null

        if (!wasRunning) {
            cleanupAudioResources()
        }

        onMeterUpdate(AudioMeterState())
        onStatusChange(
            EngineStatus(
                isServiceRunning = false,
                isTransmitting = false,
                echoCancelerAvailable = AcousticEchoCanceler.isAvailable(),
                noiseSuppressorAvailable = NoiseSuppressor.isAvailable(),
                lastWarning = warning
            )
        )
    }

    private fun cleanupAudioResources() {
        try {
            echoCanceler?.release()
        } catch (_: Exception) {
        }
        echoCanceler = null

        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {
        }
        noiseSuppressor = null

        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null

        try {
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
    }

    private fun isCurrentlyTransmitting(config: AudioConfig): Boolean {
        return when (config.mode) {
            BroadcastMode.CONTINUOUS -> true
            BroadcastMode.PUSH_TO_TALK -> pttPressed.get()
        }
    }

    private fun publishStatus() {
        if (!running.get()) return
        val cfg = configRef.get()
        onStatusChange(
            EngineStatus(
                isServiceRunning = true,
                isTransmitting = isCurrentlyTransmitting(cfg),
                echoCancelerAvailable = AcousticEchoCanceler.isAvailable(),
                noiseSuppressorAvailable = NoiseSuppressor.isAvailable(),
                lastWarning = null
            )
        )
    }

    private fun selectSupportedSampleRate(): Int {
        val candidates = intArrayOf(48000, 44100, 16000)
        for (rate in candidates) {
            val size = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (size > 0) return rate
        }
        return 44100
    }
}
