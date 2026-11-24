package io.github.bluehorn.service

import android.content.Context
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import io.github.bluehorn.audio.AudioConfig
import io.github.bluehorn.audio.AudioMeterState
import io.github.bluehorn.audio.BluetoothAudioRouter
import io.github.bluehorn.audio.BluetoothOutputState
import io.github.bluehorn.audio.BroadcastMode
import io.github.bluehorn.audio.EngineStatus
import io.github.bluehorn.audio.MegaphoneEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared singleton state holder bridging the Compose UI and Foreground Service.
 */
object MegaphoneRepository {

    private val _config = MutableStateFlow(AudioConfig())
    val config: StateFlow<AudioConfig> = _config.asStateFlow()

    private val _meter = MutableStateFlow(AudioMeterState())
    val meter: StateFlow<AudioMeterState> = _meter.asStateFlow()

    private val _status = MutableStateFlow(
        EngineStatus(
            echoCancelerAvailable = AcousticEchoCanceler.isAvailable(),
            noiseSuppressorAvailable = NoiseSuppressor.isAvailable()
        )
    )
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private val _bluetoothState = MutableStateFlow(BluetoothOutputState())
    val bluetoothState: StateFlow<BluetoothOutputState> = _bluetoothState.asStateFlow()

    private var router: BluetoothAudioRouter? = null

    val engine: MegaphoneEngine = MegaphoneEngine(
        onMeterUpdate = { _meter.value = it },
        onStatusChange = { _status.value = it }
    )

    fun ensureRouterStarted(context: Context) {
        if (router == null) {
            val appCtx = context.applicationContext
            router = BluetoothAudioRouter(appCtx).also { btRouter ->
                btRouter.startMonitoring()
                _bluetoothState.value = btRouter.state.value
            }
        }
        refreshBluetoothState()
    }

    fun refreshBluetoothState() {
        val btRouter = router ?: return
        btRouter.refreshDevices()
        val currentBt = btRouter.state.value
        _bluetoothState.value = currentBt
        engine.updateRouting(
            outputDevice = resolveTargetOutput(currentBt, _config.value),
            builtInMic = currentBt.builtInMic
        )
    }

    fun updateBluetoothStateFromCallback(state: BluetoothOutputState) {
        _bluetoothState.value = state
        engine.updateRouting(
            outputDevice = resolveTargetOutput(state, _config.value),
            builtInMic = state.builtInMic
        )
    }

    fun getRouter(): BluetoothAudioRouter? = router

    fun updateGain(gain: Float) {
        val updated = _config.value.copy(gain = gain.coerceIn(0.5f, 4.0f))
        _config.value = updated
        engine.updateConfig(updated)
    }

    fun updateNoiseGate(threshold: Float) {
        val updated = _config.value.copy(noiseGateThreshold = threshold.coerceIn(0f, 0.15f))
        _config.value = updated
        engine.updateConfig(updated)
    }

    fun updateEchoCanceler(enabled: Boolean) {
        val updated = _config.value.copy(enableEchoCanceler = enabled)
        _config.value = updated
        engine.updateConfig(updated)
    }

    fun updateNoiseSuppressor(enabled: Boolean) {
        val updated = _config.value.copy(enableNoiseSuppressor = enabled)
        _config.value = updated
        engine.updateConfig(updated)
    }

    fun updateBluetoothOnlyLock(enabled: Boolean) {
        val updated = _config.value.copy(bluetoothOnlyLock = enabled)
        _config.value = updated
        engine.updateConfig(updated)
        val bt = _bluetoothState.value
        engine.updateRouting(
            outputDevice = resolveTargetOutput(bt, updated),
            builtInMic = bt.builtInMic
        )
    }

    fun updateMode(mode: BroadcastMode) {
        val updated = _config.value.copy(mode = mode)
        _config.value = updated
        engine.updateConfig(updated)
    }

    fun setPushToTalkPressed(pressed: Boolean) {
        engine.setPushToTalkPressed(pressed)
    }

    fun clearWarning() {
        _status.value = _status.value.copy(lastWarning = null)
    }

    fun resolveTargetOutput(
        bt: BluetoothOutputState,
        cfg: AudioConfig
    ) = if (cfg.bluetoothOnlyLock) {
        bt.bluetoothDevice
    } else {
        bt.bluetoothDevice ?: bt.fallbackExternalDevice
    }
}
