package io.github.bluehorn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.bluehorn.MainActivity
import io.github.bluehorn.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MegaphoneService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        MegaphoneRepository.ensureRouterStarted(this)

        // Keep repository Bluetooth state synced while service is alive
        serviceScope.launch {
            MegaphoneRepository.getRouter()?.state?.collectLatest { btState ->
                MegaphoneRepository.updateBluetoothStateFromCallback(btState)
                if (!MegaphoneRepository.engine.isRunning() && MegaphoneRepository.status.value.lastWarning != null) {
                    stopForegroundAndSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                MegaphoneRepository.engine.stop()
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                MegaphoneRepository.refreshBluetoothState()
                val bt = MegaphoneRepository.bluetoothState.value
                val cfg = MegaphoneRepository.config.value
                val targetOutput = MegaphoneRepository.resolveTargetOutput(bt, cfg)

                val notification = buildNotification(bt.deviceName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                val started = MegaphoneRepository.engine.start(
                    config = cfg,
                    outputDevice = targetOutput,
                    builtInMic = bt.builtInMic
                )
                if (!started) {
                    stopForegroundAndSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        MegaphoneRepository.engine.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopForegroundAndSelf() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(speakerName: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MegaphoneService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_megaphone)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("输出目标：$speakerName")
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                R.drawable.ic_notification_megaphone,
                getString(R.string.notification_stop_action),
                stopPendingIntent
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "bluehorn_live_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "io.github.bluehorn.action.START"
        const val ACTION_STOP = "io.github.bluehorn.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, MegaphoneService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MegaphoneService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
