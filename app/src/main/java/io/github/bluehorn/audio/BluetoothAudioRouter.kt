package io.github.bluehorn.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BluetoothOutputState(
    val isBluetoothConnected: Boolean = false,
    val deviceName: String = "未连接蓝牙音响",
    val deviceTypeLabel: String = "",
    val bluetoothDevice: AudioDeviceInfo? = null,
    val fallbackExternalDevice: AudioDeviceInfo? = null,
    val builtInMic: AudioDeviceInfo? = null
)

/**
 * Monitors connected Bluetooth A2DP / BLE Audio speakers and locates the phone's
 * built-in microphone so recording never hijacks Bluetooth HFP/SCO hands-free mode.
 */
class BluetoothAudioRouter(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(BluetoothOutputState())
    val state: StateFlow<BluetoothOutputState> = _state.asStateFlow()

    private var isRegistered = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refreshDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refreshDevices()
        }
    }

    fun startMonitoring() {
        refreshDevices()
        if (!isRegistered) {
            audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
            isRegistered = true
        }
    }

    fun stopMonitoring() {
        if (isRegistered) {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
            isRegistered = false
        }
    }

    fun refreshDevices() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)

        val btSpeaker = outputs.firstOrNull { isBluetoothSpeakerDevice(it) }
        val externalOther = outputs.firstOrNull { isOtherExternalOutput(it) }
        val phoneMic = inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }

        if (btSpeaker != null) {
            val rawName = btSpeaker.productName?.toString()?.trim()
            val friendlyName = if (!rawName.isNullOrEmpty()) rawName else "蓝牙音响"
            _state.value = BluetoothOutputState(
                isBluetoothConnected = true,
                deviceName = friendlyName,
                deviceTypeLabel = getDeviceTypeLabel(btSpeaker.type),
                bluetoothDevice = btSpeaker,
                fallbackExternalDevice = externalOther,
                builtInMic = phoneMic
            )
        } else {
            _state.value = BluetoothOutputState(
                isBluetoothConnected = false,
                deviceName = "未检测到蓝牙音响",
                deviceTypeLabel = "",
                bluetoothDevice = null,
                fallbackExternalDevice = externalOther,
                builtInMic = phoneMic
            )
        }
    }

    private fun isBluetoothSpeakerDevice(device: AudioDeviceInfo): Boolean {
        if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            ) {
                return true
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (device.type == AudioDeviceInfo.TYPE_BLE_BROADCAST) {
                return true
            }
        }
        return false
    }

    private fun isOtherExternalOutput(device: AudioDeviceInfo): Boolean {
        return when (device.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_AUX_LINE -> true
            else -> false
        }
    }

    private fun getDeviceTypeLabel(type: Int): String {
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) return "蓝牙 A2DP 媒体通道"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (type == AudioDeviceInfo.TYPE_BLE_SPEAKER) return "LE Audio 蓝牙音响"
            if (type == AudioDeviceInfo.TYPE_BLE_HEADSET) return "LE Audio 蓝牙设备"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (type == AudioDeviceInfo.TYPE_BLE_BROADCAST) return "LE Audio 广播"
        }
        return "蓝牙音频"
    }
}
