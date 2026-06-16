package app.streammog.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.streammog.android.MainActivity
import app.streammog.android.R
import app.streammog.android.app.AppBrand

class StreamingForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "avalens_streaming"
        const val NOTIFICATION_ID = 1001
        private const val EXTRA_STATUS = "status"

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "${AppBrand.DISPLAY_NAME} Streaming",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows when ${AppBrand.DISPLAY_NAME} is actively streaming or recording"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun start(context: Context, statusText: String) {
            val intent = Intent(context, StreamingForegroundService::class.java).apply {
                putExtra(EXTRA_STATUS, statusText)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StreamingForegroundService::class.java))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val statusText = intent?.getStringExtra(EXTRA_STATUS) ?: "Active"
        startForeground(NOTIFICATION_ID, buildNotification(statusText), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(statusText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(AppBrand.DISPLAY_NAME)
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
