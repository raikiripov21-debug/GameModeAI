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
import kotlinx.coroutines.*

class GameService : Service() {

    companion object {
        const val CHANNEL_ID      = "game_mode_channel"
        const val NOTIFICATION_ID = 1001
        const val PHASE2_DELAY_MS = 20 * 60 * 1000L

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

    // SupervisorJob: si un hijo falla, los demás continúan
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var notificationManager: NotificationManager
    private var monitorJob: Job? = null
    private var aotJob: Job? = null

    // WakeLock mínimo: solo para mantener el CPU activo durante el mantenimiento
    // cada 5 min. Duración máxima 3h (A06 raramente juega más de 3h seguidas)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(phase2 = false, minLeft = 20))
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Cancelar trabajos previos de forma segura antes de reiniciar
        monitorJob?.cancel()
        aotJob?.cancel()

        // Solo registrar inicio si no hay sesión activa (evita reset tras reinicio del servicio)
        if (Prefs.getLongGameStartMs(this) <= 0L) {
            Prefs.startLongGame(this)
        }

        // Mantenimiento inmediato a los 4 segundos (esperar que Shizuku esté listo)
        serviceScope.launch {
            delay(4_000L)
            runCatching { ShizukuHelper.applyMaintenanceMode() }
        }

        // AOT de Free Fire en background (no bloquea activación)
        aotJob = serviceScope.launch {
            runCatching { ShizukuHelper.optimizeFreeFireAOT() }
        }

        monitorJob = serviceScope.launch {
            val startMs     = Prefs.getLongGameStartMs(this@GameService)
            val elapsedMs   = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)
            val remainingMs = (PHASE2_DELAY_MS - elapsedMs).coerceAtLeast(0L)

            if (!Prefs.isPhase2Active(this@GameService) && remainingMs > 0L) {
                // Mantenimiento cada 5 minutos hasta llegar a Fase 2
                var elapsedMin = (elapsedMs / 60_000L).toInt().coerceIn(0, 20)
                while (isActive && elapsedMin < 20) {
                    delay(60_000L)
                    elapsedMin++
                    if (isActive && elapsedMin % 5 == 0) {
                        runCatching { ShizukuHelper.applyMaintenanceMode() }
                    }
                    val minLeft = (20 - elapsedMin).coerceAtLeast(0)
                    safeNotify(buildNotification(phase2 = false, minLeft = minLeft))
                }
                if (isActive) applyPhase2()
            } else if (!Prefs.isPhase2Active(this@GameService)) {
                // Más de 20 minutos, Fase 2 no aplicada aún
                applyPhase2()
            }
            // Bucle de mantenimiento Fase 2 (cada 5 minutos)
            var ticks = 0
            while (isActive) {
                delay(60_000L)
                ticks++
                if (isActive && ticks % 5 == 0) {
                    runCatching { ShizukuHelper.applyLongGameMaintenance() }
                }
                safeNotify(buildNotification(phase2 = true, minLeft = 0))
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        aotJob?.cancel()
        monitorJob?.cancel()
        serviceScope.cancel()
        Prefs.clearLongGame(this)
        releaseWakeLock()
        super.onDestroy()
    }

    private suspend fun applyPhase2() {
        val ok = runCatching { ShizukuHelper.applyLongGameMode() }.getOrDefault(false)
        if (ok) {
            Prefs.setPhase2Active(this)
            safeNotify(buildNotification(phase2 = true, minLeft = 0))
        }
    }

    private fun safeNotify(notification: Notification) {
        try { notificationManager.notify(NOTIFICATION_ID, notification) }
        catch (_: Exception) { }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "GameModeAI:SessionLock"
            ).apply { acquire(3 * 60 * 60 * 1000L) } // 3 horas máximo
        } catch (_: Exception) { }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) { }
        wakeLock = null
    }

    private fun buildNotification(phase2: Boolean, minLeft: Int): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (phase2) "GameModeAI — Fase 2 activa" else "GameModeAI activo"
        val text = when {
            phase2      -> "Temperatura controlada · rendimiento máximo"
            minLeft > 1 -> "Fase 2 en $minLeft min · aim y rendimiento optimizados"
            else        -> "Modo juego activo · rendimiento optimizado"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
