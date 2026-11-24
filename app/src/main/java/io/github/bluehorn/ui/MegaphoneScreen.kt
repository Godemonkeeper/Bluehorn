package io.github.bluehorn.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsBluetooth
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bluehorn.audio.AudioConfig
import io.github.bluehorn.audio.AudioMeterState
import io.github.bluehorn.audio.BluetoothOutputState
import io.github.bluehorn.audio.BroadcastMode
import io.github.bluehorn.audio.EngineStatus
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MegaphoneScreen(
    bluetoothState: BluetoothOutputState,
    config: AudioConfig,
    status: EngineStatus,
    meter: AudioMeterState,
    hasMicPermission: Boolean,
    onRequestPermission: () -> Unit,
    onToggleLive: () -> Unit,
    onPushToTalkChanged: (Boolean) -> Unit,
    onModeChange: (BroadcastMode) -> Unit,
    onGainChange: (Float) -> Unit,
    onNoiseGateChange: (Float) -> Unit,
    onEchoCancelerChange: (Boolean) -> Unit,
    onNoiseSuppressorChange: (Boolean) -> Unit,
    onBluetoothOnlyLockChange: (Boolean) -> Unit,
    onDismissWarning: () -> Unit
) {
    val context = LocalContext.current
    var showInfoDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BlueHorn 蓝牙扩音",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = "使用提示与开源信息"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Bluetooth Speaker Status Card
            BluetoothStatusCard(
                bluetoothState = bluetoothState,
                onOpenBluetoothSettings = {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    context.startActivity(intent)
                }
            )

            // Warning Banner (if any)
            if (!status.lastWarning.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF451A03)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = status.lastWarning,
                            color = Color(0xFFFDE68A),
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissWarning) {
                            Text("知道了", color = Color(0xFFFBBF24))
                        }
                    }
                }
            }

            // 2. Mode Switcher (Continuous vs Push-to-Talk)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = config.mode == BroadcastMode.CONTINUOUS,
                    onClick = { onModeChange(BroadcastMode.CONTINUOUS) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("持续扩音")
                }
                SegmentedButton(
                    selected = config.mode == BroadcastMode.PUSH_TO_TALK,
                    onClick = { onModeChange(BroadcastMode.PUSH_TO_TALK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("按住说话（防啸叫）")
                }
            }

            // 3. Hero Megaphone Button & Live VU Meter
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    HeroMicButton(
                        mode = config.mode,
                        isServiceRunning = status.isServiceRunning,
                        isTransmitting = status.isTransmitting,
                        hasMicPermission = hasMicPermission,
                        onRequestPermission = onRequestPermission,
                        onToggleLive = onToggleLive,
                        onPushToTalkChanged = onPushToTalkChanged
                    )

                    Text(
                        text = when {
                            !hasMicPermission -> "点击授予麦克风权限"
                            config.mode == BroadcastMode.CONTINUOUS && status.isServiceRunning ->
                                "正在实时扩音中（支持息屏后台运行）"
                            config.mode == BroadcastMode.CONTINUOUS ->
                                "点击开始实时扩音到蓝牙音响"
                            status.isTransmitting ->
                                "正在对讲扩音，松开即静音"
                            else ->
                                "按住按钮说话，松开自动静音"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )

                    LiveLevelMeter(
                        meter = meter,
                        isActive = status.isServiceRunning
                    )

                    if (config.mode == BroadcastMode.PUSH_TO_TALK && status.isServiceRunning && !status.isTransmitting) {
                        TextButton(onClick = onToggleLive) {
                            Text("退出对讲待机（关闭前台服务）", fontSize = 13.sp)
                        }
                    }
                }
            }

            // 4. Audio DSP & Safety Controls
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "实时音效与防啸叫调节",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }

                    // Gain Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("扩音增益（带防破音限幅）", fontSize = 14.sp)
                            }
                            Text(
                                text = "${(config.gain * 100).roundToInt()}%",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Slider(
                            value = config.gain,
                            onValueChange = onGainChange,
                            valueRange = 0.5f..4.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    // Noise Gate Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("防底噪门限（安静时自动静音）", fontSize = 14.sp)
                            Text(
                                text = "${((config.noiseGateThreshold / 0.15f) * 100).roundToInt()}%",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Slider(
                            value = config.noiseGateThreshold,
                            onValueChange = onNoiseGateChange,
                            valueRange = 0f..0.15f
                        )
                    }

                    ToggleRow(
                        title = "硬件回声消除 (AEC)",
                        subtitle = if (status.echoCancelerAvailable) "减少音响回声进入麦克风" else "当前设备不支持硬件 AEC",
                        checked = config.enableEchoCanceler && status.echoCancelerAvailable,
                        enabled = status.echoCancelerAvailable,
                        onCheckedChange = onEchoCancelerChange
                    )

                    ToggleRow(
                        title = "硬件环境降噪 (NS)",
                        subtitle = if (status.noiseSuppressorAvailable) "抑制环境风噪与电流底噪" else "当前设备不支持硬件降噪",
                        checked = config.enableNoiseSuppressor && status.noiseSuppressorAvailable,
                        enabled = status.noiseSuppressorAvailable,
                        onCheckedChange = onNoiseSuppressorChange
                    )

                    ToggleRow(
                        title = "仅允许蓝牙输出（防啸叫安全锁）",
                        subtitle = "蓝牙断开时立即停止扩音，避免手机自身扬声器尖啸",
                        checked = config.bluetoothOnlyLock,
                        enabled = true,
                        onCheckedChange = onBluetoothOnlyLockChange,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("关于 BlueHorn & 防啸叫提示") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• 开源协议：MIT License，无广告、无联网、纯本地实时处理。")
                    Text("• 避免啸叫：尽量让手机麦克风远离蓝牙音响（建议 2 米以上或隔开方向），并开启「按住说话」模式。")
                    Text("• 蓝牙延迟说明：蓝牙 A2DP 音频协议本身存在约 100~250ms 传输延迟，属蓝牙标准物理特性。")
                    Text("• 音频路由：已固定使用手机内置麦克风录音 + 蓝牙 A2DP 媒体通道播放，避免触发低音质通话模式。")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("好的")
                }
            }
        )
    }
}

@Composable
private fun BluetoothStatusCard(
    bluetoothState: BluetoothOutputState,
    onOpenBluetoothSettings: () -> Unit
) {
    val accentColor = if (bluetoothState.isBluetoothConnected) {
        Color(0xFF10B981)
    } else {
        Color(0xFFF59E0B)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (bluetoothState.isBluetoothConnected) {
                            Icons.Rounded.BluetoothAudio
                        } else {
                            Icons.Rounded.BluetoothDisabled
                        },
                        contentDescription = null,
                        tint = accentColor
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = bluetoothState.deviceName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (bluetoothState.isBluetoothConnected) {
                            bluetoothState.deviceTypeLabel
                        } else {
                            "请先在系统蓝牙中连接音响"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(onClick = onOpenBluetoothSettings) {
                Icon(
                    imageVector = Icons.Rounded.SettingsBluetooth,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("蓝牙", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun HeroMicButton(
    mode: BroadcastMode,
    isServiceRunning: Boolean,
    isTransmitting: Boolean,
    hasMicPermission: Boolean,
    onRequestPermission: () -> Unit,
    onToggleLive: () -> Unit,
    onPushToTalkChanged: (Boolean) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isTransmitting) 1.14f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val buttonColor by animateColorAsState(
        targetValue = when {
            !hasMicPermission -> Color(0xFF334155)
            isTransmitting -> Color(0xFFEA580C)
            isServiceRunning -> Color(0xFF0284C7)
            else -> Color(0xFF1E293B)
        },
        label = "btnColor"
    )

    val ringColor = if (isTransmitting) {
        Color(0xFFF97316).copy(alpha = 0.28f)
    } else {
        Color(0xFF38BDF8).copy(alpha = 0.16f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(196.dp)
    ) {
        // Outer animated ring
        Box(
            modifier = Modifier
                .size(176.dp)
                .scale(if (isTransmitting) pulseScale else 1f)
                .clip(CircleShape)
                .background(ringColor)
        )

        // Core interactive button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(148.dp)
                .clip(CircleShape)
                .background(buttonColor)
                .border(
                    width = 3.dp,
                    color = if (isTransmitting) Color(0xFFFDBA74) else Color(0xFF38BDF8),
                    shape = CircleShape
                )
                .pointerInput(mode, hasMicPermission) {
                    detectTapGestures(
                        onPress = {
                            if (!hasMicPermission) {
                                onRequestPermission()
                                return@detectTapGestures
                            }
                            if (mode == BroadcastMode.PUSH_TO_TALK) {
                                onPushToTalkChanged(true)
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    onPushToTalkChanged(false)
                                }
                            }
                        },
                        onTap = {
                            if (!hasMicPermission) {
                                onRequestPermission()
                            } else if (mode == BroadcastMode.CONTINUOUS) {
                                onToggleLive()
                            }
                        }
                    )
                }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isTransmitting) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                    contentDescription = "扩音开关",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when {
                        !hasMicPermission -> "授权录音"
                        mode == BroadcastMode.PUSH_TO_TALK -> if (isTransmitting) "松开静音" else "按住说话"
                        isServiceRunning -> "停止扩音"
                        else -> "开启扩音"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun LiveLevelMeter(
    meter: AudioMeterState,
    isActive: Boolean
) {
    val segments = 20
    val filledCount = if (isActive) (meter.inputLevel * segments).roundToInt().coerceIn(0, segments) else 0

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "实时音量电平",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = when {
                    !isActive -> "待机"
                    meter.isClipping -> "触发防破音限幅"
                    meter.isGated -> "降噪门限静音中"
                    else -> "正常传输"
                },
                fontSize = 12.sp,
                color = when {
                    meter.isClipping -> Color(0xFFEF4444)
                    meter.isGated -> Color(0xFF94A3B8)
                    isActive -> Color(0xFF10B981)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (i in 0 until segments) {
                val activeColor = when {
                    i >= 16 -> Color(0xFFEF4444)
                    i >= 12 -> Color(0xFFF59E0B)
                    else -> Color(0xFF10B981)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (i < filledCount) activeColor else Color(0xFF1E293B)
                        )
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = title,
                    fontSize = 14.sp,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}
