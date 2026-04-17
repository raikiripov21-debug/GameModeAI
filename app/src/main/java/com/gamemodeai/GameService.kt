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
import android.os.PowerManager
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
        const val PHASE2_MINUTES  = 20                  // Exynos 850 — menos eficiente que 1380
        const val PHASE2_DELAY_MS = PHASE2_MINUTES * 60 * 1000L

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
    // WakeLock parcial: evita que el Exynos 850 entre en deep sleep durante mantenimiento
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(phase2 = false, minLeft = PHASE2_MINUTES))
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GameModeAI:SessionLock")
        wakeLock?.acquire(6 * 60 * 60 * 1000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        monitorJob?.cancel()

        val alreadyRunning = Prefs.getLongGameStartMs(this) > 0L
        if (!alreadyRunning) {
            Prefs.startLongGame(this)
        }

        monitorJob = serviceScope.launch {

            val startMs     = Prefs.getLongGameStartMs(this@GameService)
            val elapsedMs   = System.currentTimeMillis() - startMs
            val remainingMs = (PHASE2_DELAY_MS - elapsedMs).coerceAtLeast(0L)
            val elapsedMin  = (elapsedMs / 60_000L).coerceAtMost(PHASE2_MINUTES.toLong()).toInt()

            if (Prefs.isPhase2Active(this@GameService)) {
                // Fase 2 ya activa — ir directo al bucle de mantenimiento
            } else if (remainingMs > 0L) {
                var elapsed = elapsedMin
                while (isActive && elapsed < PHASE2_MINUTES) {
                    delay(60_000L)
                    elapsed++
                    if (elapsed % 5 == 0) {
                        ShizukuHelper.applyMaintenanceMode()
                    }
                    val minLeft = PHASE2_MINUTES - elapsed
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        buildNotification(phase2 = false, minLeft = minLeft)
                    )
                }
                if (isActive) applyPhase2()
            } else {
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
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

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

    private fun buildNotification(phase2: Boolean, minLeft: Int): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (phase2) "GameModeAI A06 — Fase 2 activa" else "GameModeAI A06 activo"
        val text = when {
            phase2         -> "Brillo y CPU reducidos · temp bajo control"
            minLeft > 1    -> "Fase 2 térmica en $minLeft min · optimizado para Exynos 850"
            else           -> "Fase 2 térmica en $minLeft min · casi lista!"
        }

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
