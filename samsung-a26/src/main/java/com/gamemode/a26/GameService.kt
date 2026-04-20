package com.gamemode.a26

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
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * GameService — Samsung Galaxy A26 5G
 * Servicio en primer plano con AdaptiveEngine integrado.
 * Sin Shizuku — usa APIs publicas unicamente.
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
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GameModeAI_A26::WakeLock")
        wakeLock?.acquire(20 * 60 * 1000L)
        AdaptiveEngine.restoreState(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotif("Iniciando A26", "Motor A26 arrancando"))
        Prefs.startSession(this)
        AdaptiveEngine.reset()
        job = scope.launch { runLoop() }
        return START_STICKY
    }

    private fun CoroutineScope.runLoop() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        while (isActive) {
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val ramFree  = mi.availMem / (1024L * 1024L)
            val ramTotal = mi.totalMem / (1024L * 1024L)

            val snap = kotlinx.coroutines.runBlocking {
                OptimizerEngine.getSnapshot(ramFree, ramTotal)
            }
            val dec = AdaptiveEngine.process(
                snap.cpuPct, snap.cpuTempC, snap.ramFreeMb, snap.ramTotalMb)

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotif(
                "GameModeAI A26 — ${snap.healthLabel}",
                "CPU:${snap.cpuPct}% T:${snap.cpuTempC.toInt()}°C | ${dec.advice}"
            ))
            AdaptiveEngine.saveState(this@GameService)
            Thread.sleep(dec.delayMs)
        }
    }

    private fun buildNotif(title: String, text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title).setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN).setSilent(true).build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Game Mode AI A26", NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false); setSound(null, null) }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel(); scope.let { }
        if (wakeLock?.isHeld == true) wakeLock?.release()
        Prefs.setActive(this, false)
        Log.d(TAG, "A26 service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
