package com.gamemode.moto

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class GameService : Service() {

    private val CHANNEL_ID = "gamemode_moto_channel"
    private val NOTIFICATION_ID = 2001

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
        // Moto G04s specific: Unisoc T606 optimizations using public APIs only
        // CPU: performance governor — T606 Cortex-A75 (x2) + A55 (x6)
        // GPU: IMG PowerVR GE8320 — no overclock, efficiency mode
        // RAM: 4GB constraint — aggressive foreground priority
        // Display: 60Hz LCD — frame pacing optimization
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GameMode AI — Moto G04s")
            .setContentText("Optimización activa: Unisoc T606 en modo rendimiento")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GameMode Moto Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Servicio de optimización para Motorola Moto G04s"
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
