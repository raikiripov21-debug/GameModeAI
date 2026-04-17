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
import kotlinx.coroutines.Job

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
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(phase2 = false, minLeft = 20))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        monitorJob?.cancel()

        // Solo registrar tiempo de inicio si no hay uno ya (evita resetear el contador
        // cuando Android reinicia el servicio con START_STICKY después de matarlo).
        val alreadyRunning = Prefs.getLongGameStartMs(this) > 0L
        if (!alreadyRunning) {
            Prefs.startLongGame(this)
        }

        monitorJob = serviceScope.launch {

            // Calcular minutos restantes basándose en el tiempo de inicio real
            // (importante para recuperarse correctamente tras un reinicio de servicio)
            val startMs     = Prefs.getLongGameStartMs(this@GameService)
            val elapsedMs   = System.currentTimeMillis() - startMs
            val remainingMs = (PHASE2_DELAY_MS - elapsedMs).coerceAtLeast(0L)
            val elapsedMin  = (elapsedMs / 60_000L).coerceAtMost(20L).toInt()

            if (Prefs.isPhase2Active(this@GameService)) {
                // La Fase 2 ya estaba activa — saltar directamente al bucle de mantenimiento
            } else if (remainingMs > 0L) {
                // Contar desde los minutos ya transcurridos hacia los 20
                var elapsed = elapsedMin
                while (isActive && elapsed < 20) {
                    delay(60_000L)   // 1 minuto
                    elapsed++
                    if (elapsed % 5 == 0) {
                        ShizukuHelper.applyMaintenanceMode()
                    }
                    val minLeft = 20 - elapsed
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        buildNotification(phase2 = false, minLeft = minLeft)
                    )
                }
                if (isActive) {
                    applyPhase2()
                }
            } else {
                // Han pasado más de 20 minutos y Fase 2 aún no se aplicó
                applyPhase2()
            }

            // ── Bucle de mantenimiento Fase 2 ────────────────────────────────
            var phase2Elapsed = 0
            while (isActive) {
                delay(60_000L)
                phase2Elapsed++
                if (phase2Elapsed % 5 == 0) {
                    ShizukuHelper.applyLongGameMaintenance()
                }
                notificationManager.notify(
                    NOTIFICATION_ID,
                    buildNotification(phase2 = true, minLeft = 0)
                )
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        monitorJob?.cancel()
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
            "GameModeAI — Fase 2 activa"
        else
            "GameModeAI activo"

        val text = if (phase2)
            "Brillo y CPU reducidos para mantener temp baja"
        else if (minLeft > 1)
            "Fase 2 térmica en $minLeft min · aim y rendimiento optimizados"
        else
            "Fase 2 térmica en $minLeft min · casi lista!"

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
