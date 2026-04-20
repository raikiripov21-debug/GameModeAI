package com.gamemode.moto

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

class GameService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val nm by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    companion object {
        private const val CHANNEL_ID = "gm_moto_channel"
        private const val NOTIF_ID   = 3001
        private const val TAG        = "GameService_Moto"
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GameModeAI_Moto::WakeLock")
        wakeLock?.acquire(20 * 60 * 1000L)
        AdaptiveEngine.restoreState(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotif("GameModeAI Moto", "Motor Moto iniciando..."))
        Prefs.startSession(this)
        AdaptiveEngine.reset()

        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        job = scope.launch {
            while (isActive) {
                val mi = ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                val ramFree  = mi.availMem / (1024L * 1024L)
                val ramTotal = mi.totalMem / (1024L * 1024L)

                val snap = OptimizerEngine.getSnapshot(ramFree, ramTotal)
                val dec  = AdaptiveEngine.process(
                    snap.cpuPct, snap.cpuTempC, snap.ramFreeMb, snap.ramTotalMb)

                nm.notify(NOTIF_ID, buildNotif(
                    "GameModeAI Moto — ${snap.healthLabel}",
                    "CPU:${snap.cpuPct}% T:${snap.cpuTempC.toInt()}C RAM:${snap.ramUsedPct}% | ${dec.advice}"
                ))
                AdaptiveEngine.saveState(this@GameService)
                delay(dec.delayMs)
            }
        }
        return START_STICKY
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
                CHANNEL_ID, "Game Mode AI Moto", NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false); setSound(null, null) }
            nm.createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        Prefs.setActive(this, false)
        Log.d(TAG, "Moto service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
