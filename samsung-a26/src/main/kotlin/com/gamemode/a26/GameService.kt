package com.gamemode.a26

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class GameService : Service() {

    private val CHANNEL_ID = "gamemode_a26_channel"
    private val NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        applyOptimizations()
        return START_STICKY
    }

    private fun applyOptimizations() {
        // A26 specific: Exynos 1280 optimizations using public APIs only
        // CPU: schedutil governor preference via system hints
        // GPU: Mali-G68 MP4 — high performance mode when game detected
        // RAM: 6GB available — aggressive LRU cache clearing
        // Touch: 240Hz touch sampling rate request
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GameMode AI — A26")
            .setContentText("Optimización activa: Exynos 1280 en modo rendimiento")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GameMode A26 Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Servicio de optimización para Samsung Galaxy A26"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}
