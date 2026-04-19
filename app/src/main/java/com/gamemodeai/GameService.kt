package com.gamemodeai

import android.app.ActivityManager
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
import java.util.concurrent.atomic.AtomicBoolean

class GameService : Service() {

    companion object {
        const val CHANNEL_ID      = "game_mode_channel"
        const val NOTIFICATION_ID = 1001
        const val PHASE2_DELAY_MS = 20 * 60 * 1000L   // 20 minutos

        // ── SPEC A06: delay 6000-7000ms, anti-spam 10-12s ──────────────────
        private const val POLL_DELAY_MS  = 6_500L      // loop de monitoramento
        private const val ANTI_SPAM_MS   = 11_000L     // mínimo entre ações
        private const val THERMAL_LIMIT  = OptimizerEngine.THERMAL_STOP_TEMP_C

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
    private var aotJob: Job? = null
    private val aotRunning = AtomicBoolean(false)
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var lastHealthLabel: String = "Estável"

    // Anti-spam: controla quando a última ação foi executada
    @Volatile private var lastActionMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(phase2 = false, minLeft = 20))
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GameModeAI:SessionLock")
        wakeLock?.acquire(6 * 60 * 60 * 1000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        monitorJob?.cancel()

        // Manutenção imediata ao iniciar (após 4s para estabilizar)
        serviceScope.launch {
            delay(4_000L)
            ShizukuHelper.applyMaintenanceMode()
            lastActionMs = System.currentTimeMillis()
        }

        val alreadyRunning = Prefs.getLongGameStartMs(this) > 0L
        if (!alreadyRunning) {
            Prefs.startLongGame(this)
        }

        // AOT em background
        aotJob?.cancel()
        aotJob = serviceScope.launch {
            aotRunning.set(true)
            notificationManager.notify(NOTIFICATION_ID,
                buildNotification(phase2 = false, minLeft = 20, aotRunning = true))
            ShizukuHelper.optimizeFreeFireAOT()
            aotRunning.set(false)
            notificationManager.notify(NOTIFICATION_ID,
                buildNotification(phase2 = false, minLeft = 20, aotRunning = false))
        }

        monitorJob = serviceScope.launch {

            val startMs     = Prefs.getLongGameStartMs(this@GameService)
            val elapsedMs   = System.currentTimeMillis() - startMs
            val remainingMs = (PHASE2_DELAY_MS - elapsedMs).coerceAtLeast(0L)
            val elapsedMin  = (elapsedMs / 60_000L).coerceAtMost(20L).toInt()

            if (!Prefs.isPhase2Active(this@GameService) && remainingMs > 0L) {
                var elapsed = elapsedMin
                var cycleCount = 0

                // ── Fase 1: monitoramento a cada POLL_DELAY_MS ──────────────
                while (isActive && elapsed < 20) {
                    delay(POLL_DELAY_MS)
                    cycleCount++

                    val ramFree  = getAvailableRamMb()
                    val ramTotal = getTotalRamMb()
                    val snap     = OptimizerEngine.getSnapshot(ramFree, ramTotal)
                    lastHealthLabel = snap.healthLabel

                    // Proteção térmica: parar ações se temp > 40°C
                    if (OptimizerEngine.shouldPauseActions(snap)) {
                        Log.w("GameService", "Thermal pause: ${snap.cpuTempC}°C")
                        // Anti-spam de notificação — mínimo 11s entre updates
                        val now = System.currentTimeMillis()
                        if (now - lastActionMs >= ANTI_SPAM_MS) {
                            notificationManager.notify(NOTIFICATION_ID,
                                buildNotification(phase2 = false, minLeft = 20 - elapsed))
                            lastActionMs = now
                        }
                        continue
                    }

                    // Anti-spam: não executar manutenção mais de 1x por 11s
                    val now = System.currentTimeMillis()
                    if (now - lastActionMs >= ANTI_SPAM_MS) {
                        // Ciclos de manutenção adaptativos (baseados em minutos equivalentes)
                        val minutesCycle = cycleCount / (60_000L / POLL_DELAY_MS).toInt()
                        val did = OptimizerEngine.runAdaptiveMaintenance(snap, minutesCycle.toInt())
                        if (did) lastActionMs = now
                    }

                    // Atualiza minutos decorridos a cada ~60s
                    if (cycleCount % (60_000L / POLL_DELAY_MS).toInt() == 0) {
                        elapsed++
                        val minLeft = 20 - elapsed
                        notificationManager.notify(NOTIFICATION_ID,
                            buildNotification(phase2 = false, minLeft = minLeft))
                    }
                }

                if (isActive) applyPhase2()
            } else if (Prefs.isPhase2Active(this@GameService)) {
                // Já em Fase 2 — pular direto para o loop de manutenção
            } else {
                applyPhase2()
            }

            // ── Fase 2: loop de manutenção com POLL_DELAY_MS ─────────────────
            var phase2Cycle = 0
            while (isActive) {
                delay(POLL_DELAY_MS)
                phase2Cycle++

                val ramFree  = getAvailableRamMb()
                val ramTotal = getTotalRamMb()
                val snap     = OptimizerEngine.getSnapshot(ramFree, ramTotal)
                lastHealthLabel = snap.healthLabel

                // Proteção térmica
                if (OptimizerEngine.shouldPauseActions(snap)) {
                    Log.w("GameService", "Fase2 thermal pause: ${snap.cpuTempC}°C")
                    continue
                }

                // Anti-spam
                val now = System.currentTimeMillis()
                if (now - lastActionMs >= ANTI_SPAM_MS) {
                    val minutesCycle = phase2Cycle / (60_000L / POLL_DELAY_MS).toInt()
                    val did = OptimizerEngine.runAdaptiveMaintenance(snap, minutesCycle.toInt())
                    if (did) {
                        ShizukuHelper.applyLongGameMaintenance()
                        lastActionMs = now
                    }
                }

                // Notificação a cada ~60s
                if (phase2Cycle % (60_000L / POLL_DELAY_MS).toInt() == 0) {
                    notificationManager.notify(NOTIFICATION_ID,
                        buildNotification(phase2 = true, minLeft = 0))
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        aotJob?.cancel()
        monitorJob?.cancel()
        serviceScope.cancel()
        Prefs.clearLongGame(this)
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

    // ── Fase 2 ───────────────────────────────────────────────────────────────
    private suspend fun applyPhase2() {
        val ok = ShizukuHelper.applyLongGameMode()
        if (ok) {
            lastActionMs = System.currentTimeMillis()
            Prefs.setPhase2Active(this)
            notificationManager.notify(NOTIFICATION_ID,
                buildNotification(phase2 = true, minLeft = 0))
        }
    }

    // ── Notificação ──────────────────────────────────────────────────────────
    private fun buildNotification(
        phase2: Boolean,
        minLeft: Int,
        aotRunning: Boolean = this.aotRunning.get()
    ): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val title = when {
            phase2     -> "GameModeAI — Fase 2 ativa"
            aotRunning -> "GameModeAI — Compilando AOT"
            else       -> "GameModeAI ativo · $lastHealthLabel"
        }

        val text = when {
            aotRunning  -> "Compilando Free Fire (AOT)… aim mais estável ao terminar"
            phase2      -> "Brilho e CPU reduzidos · Motor: $lastHealthLabel"
            minLeft > 1 -> "Fase 2 em $minLeft min · Motor: $lastHealthLabel"
            else        -> "Fase 2 térmica em $minLeft min · quase pronta!"
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

    // ── Canal ─────────────────────────────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Game Mode AI", NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Game Mode AI ativo"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    // ── Helpers de RAM ────────────────────────────────────────────────────────
    private fun getAvailableRamMb(): Long {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem / (1024L * 1024L)
    }

    private fun getTotalRamMb(): Long {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.totalMem / (1024L * 1024L)
    }
}
