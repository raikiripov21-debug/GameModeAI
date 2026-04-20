package com.gamemode.a26

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/**
 * GameService — Samsung Galaxy A26 5G
 * Servicio en primer plano con AdaptiveEngine integrado.
 * Sin Shizuku — usa APIs públicas únicamente.
 */
class GameService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val CHANNEL_ID = "gm_a26_channel"
        private const val NOTIF_ID   = 2001
        private const val TAG        = "GameService_A26"
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GameModeAI_A26::WakeLock")
        wakeLock?.acquire(20 * 60 * 1000L)
        AdaptiveEngine.restoreState(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotif("Iniciando…", "Motor A26 arrancando"))
        Prefs.startSession(this)
        AdaptiveEngine.reset()
        job = scope.launch { runLoop() }
        return START_STICKY
    }

    private suspend fun runLoop() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        while (isActive) {
            val mi = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val snap = OptimizerEngine.getSnapshot(
                mi.availMem / (1024L * 1024L), mi.totalMem / (1024L * 1024L))

            val sessionMs = System.currentTimeMillis() - Prefs.getSessionStartMs(this@GameService)
            val dec = AdaptiveEngine.process(
                snap.cpuPct, snap.cpuTempC, snap.ramFreeMb, snap.ramTotalMb)

            updateNotif(snap, dec)
            AdaptiveEngine.saveState(this@GameService)

            delay(dec.delayMs)
        }
    }

    private fun updateNotif(snap: OptimizerEngine.Snapshot, dec: AdaptiveEngine.Decision) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotif(
            "GameModeAI A26 — ${snap.healthLabel}",
            "CPU:${snap.cpuPct}% T:${snap.cpuTempC.toInt()}°C RAM:${snap.ramUsedPct}% | ${dec.advice}"
        ))
    }

    private fun buildNotif(title: String, text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title).setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN).setSilent(true).build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Game Mode AI A26", NotificationManager.IMPORTANCE_MIN)
                .apply { setShowBadge(false); setSound(null, null) }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel(); scope.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        Prefs.setActive(this, false)
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
