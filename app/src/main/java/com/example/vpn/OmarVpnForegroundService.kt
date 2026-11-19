package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.OmarVpnApplication
import com.example.R
import com.example.core.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OmarVpnForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel(this)
        when (intent?.action) {
            ACTION_START -> {
                val serverName = intent.getStringExtra(EXTRA_SERVER_NAME).orEmpty()
                val notification = buildNotification(this, serverName, 0L, 0L)
                startForeground(NOTIFICATION_ID, notification)
            }
            ACTION_DISCONNECT -> {
                val app = applicationContext as? OmarVpnApplication
                serviceScope.launch {
                    app?.container?.vpnManager?.disconnect()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_START = "com.omarvpn.action.START_FGS"
        const val ACTION_STOP = "com.omarvpn.action.STOP_FGS"
        const val ACTION_DISCONNECT = "com.omarvpn.action.DISCONNECT_VPN"
        const val EXTRA_SERVER_NAME = "extra_server_name"
        private const val CHANNEL_ID = "omar_vpn_tunnel_channel"
        private const val NOTIFICATION_ID = 7001

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java) ?: return
                val existing = manager.getNotificationChannel(CHANNEL_ID)
                if (existing == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notification_channel_vpn_name),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = context.getString(R.string.notification_channel_vpn_desc)
                        setShowBadge(false)
                    }
                    manager.createNotificationChannel(channel)
                }
            }
        }

        fun updateNotification(
            context: Context,
            serverName: String,
            rxBytes: Long,
            txBytes: Long
        ) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            ensureNotificationChannel(context)
            manager.notify(NOTIFICATION_ID, buildNotification(context, serverName, rxBytes, txBytes))
        }

        private fun buildNotification(
            context: Context,
            serverName: String,
            rxBytes: Long,
            txBytes: Long
        ): Notification {
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openAppPending = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val disconnectIntent = Intent(context, OmarVpnForegroundService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            val disconnectPending = PendingIntent.getService(
                context,
                1,
                disconnectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val trafficSummary = "↓ ${Formatters.formatBytes(rxBytes)}  •  ↑ ${Formatters.formatBytes(txBytes)}"
            val contentText = if (serverName.isNotBlank()) {
                "$serverName  •  $trafficSummary"
            } else {
                trafficSummary
            }

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_qs_tile)
                .setContentTitle(context.getString(R.string.vpn_notification_title_connected))
                .setContentText(contentText)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openAppPending)
                .addAction(
                    0,
                    context.getString(R.string.vpn_notification_action_disconnect),
                    disconnectPending
                )
                .build()
        }
    }
}
