package com.gamemodeai

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GameService : Service() {

    companion object {
        const val CHANNEL_ID      = "game_mode_channel"
        const val NOTIFICATION_ID = 1001
        const val PHASE2_DELAY_MS = 20 * 60 * 1000L   // 20 minutos

        fun start(context: Context) {
            val intent = Intent(context, GameService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GameService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(phase2 = false, minLeft = 20))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // Registrar el tiempo de inicio de la sesión
        Prefs.startLongGame(this)

        serviceScope.launch {

            // ── Contador de minutos para la notificación ──────────────────────
            var elapsed = 0
            while (isActive && elapsed < 20) {
                delay(60_000L)   // 1 minuto
                elapsed++
                val minLeft = 20 - elapsed
                notificationManager.notify(
                    NOTIFICATION_ID,
                    buildNotification(phase2 = false, minLeft = minLeft)
                )
            }

            // ── FASE 2: 20 minutos cumplidos ──────────────────────────────────
            if (isActive) {
                applyPhase2()
            }

            // ── Seguir actualizando notificación en fase 2 ────────────────────
            while (isActive) {
                delay(60_000L)
                if (isActive) {
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        buildNotification(phase2 = true, minLeft = 0)
                    )
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Prefs.clearLongGame(this)
    }

    // ── Fase 2 — reducción térmica para partidas largas ───────────────────────
    private suspend fun applyPhase2() {
        val ok = ShizukuHelper.applyLongGameMode()
        if (ok) {
            Prefs.setPhase2Active(this)
            notificationManager.notify(
                NOTIFICATION_ID,
                buildNotification(phase2 = true, minLeft = 0)
            )
        }
    }

    // ── Notificación ──────────────────────────────────────────────────────────
    private fun buildNotification(phase2: Boolean, minLeft: Int): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (phase2)
            "🌡 Fase 2 activa — Partida larga"
        else
            "GameModeAI activo"

        val text = if (phase2)
            "Brillo y CPU reducidos para mantener temp baja"
        else if (minLeft > 1)
            "Fase 2 térmica en $minLeft min · aim y rendimiento optimizados"
        else
            "Fase 2 térmica en $minLeft min · ¡casi lista!"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()
    }

    // ── Canal de notificación ─────────────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Game Mode AI", NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Game Mode AI activo"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
