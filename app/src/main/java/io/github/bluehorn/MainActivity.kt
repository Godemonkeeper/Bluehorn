package io.github.bluehorn

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bluehorn.service.MegaphoneRepository
import io.github.bluehorn.service.MegaphoneService
import io.github.bluehorn.ui.MegaphoneScreen
import io.github.bluehorn.ui.theme.BlueHornTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private var hasMicPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasMicPermission = result[Manifest.permission.RECORD_AUDIO] == true || checkMicPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MegaphoneRepository.ensureRouterStarted(this)
        hasMicPermission = checkMicPermission()

        setContent {
            BlueHornTheme {
                val btRouter = MegaphoneRepository.getRouter()
                LaunchedEffect(btRouter) {
                    btRouter?.state?.collectLatest { state ->
                        MegaphoneRepository.updateBluetoothStateFromCallback(state)
                    }
                }

                val bluetoothState by MegaphoneRepository.bluetoothState.collectAsStateWithLifecycle()
                val config by MegaphoneRepository.config.collectAsStateWithLifecycle()
                val status by MegaphoneRepository.status.collectAsStateWithLifecycle()
                val meter by MegaphoneRepository.meter.collectAsStateWithLifecycle()

                MegaphoneScreen(
                    bluetoothState = bluetoothState,
                    config = config,
                    status = status,
                    meter = meter,
                    hasMicPermission = hasMicPermission,
                    onRequestPermission = { requestRequiredPermissions() },
                    onToggleLive = {
                        if (!checkMicPermission()) {
                            requestRequiredPermissions()
                        } else if (status.isServiceRunning) {
                            MegaphoneService.stop(this)
                        } else {
                            MegaphoneService.start(this)
                        }
                    },
                    onPushToTalkChanged = { pressed ->
                        if (!checkMicPermission()) {
                            requestRequiredPermissions()
                            return@MegaphoneScreen
                        }
                        if (pressed) {
                            MegaphoneRepository.setPushToTalkPressed(true)
                            if (!status.isServiceRunning) {
                                MegaphoneService.start(this)
                            }
                        } else {
                            MegaphoneRepository.setPushToTalkPressed(false)
                        }
                    },
                    onModeChange = { newMode ->
                        MegaphoneRepository.updateMode(newMode)
                    },
                    onGainChange = { MegaphoneRepository.updateGain(it) },
                    onNoiseGateChange = { MegaphoneRepository.updateNoiseGate(it) },
                    onEchoCancelerChange = { MegaphoneRepository.updateEchoCanceler(it) },
                    onNoiseSuppressorChange = { MegaphoneRepository.updateNoiseSuppressor(it) },
                    onBluetoothOnlyLockChange = { MegaphoneRepository.updateBluetoothOnlyLock(it) },
                    onDismissWarning = { MegaphoneRepository.clearWarning() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasMicPermission = checkMicPermission()
        MegaphoneRepository.refreshBluetoothState()
    }

    private fun checkMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}
