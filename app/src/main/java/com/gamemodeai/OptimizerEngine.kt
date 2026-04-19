package com.gamemodeai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * OptimizerEngine — Motor de optimización inteligente para Samsung A06
 *
 * Analiza el estado del sistema en tiempo real y clasifica la salud del dispositivo
 * en 4 estados, tomando decisiones automáticas de mantenimiento según el contexto.
 *
 * NO usa root ni APIs prohibidas. Solo lectura de /proc y /sys (públicas).
 */
object OptimizerEngine {

    private const val TAG = "OptimizerEngine"

    // ── Estado de salud del sistema ──────────────────────────────────────────
    sealed class SystemHealth {
        object Stable      : SystemHealth()   // CPU <60%, temp <38°C
        object MediumLoad  : SystemHealth()   // CPU 60-80% o temp 38-43°C
        object HighLoad    : SystemHealth()   // CPU >80% o temp 44-47°C
        object ThermalRisk : SystemHealth()   // temp ≥48°C — acción inmediata
    }

    data class SystemSnapshot(
        val cpuUsagePct  : Int   = 0,     // 0-100%
        val cpuTempC     : Float = 0f,
        val battTempC    : Float = 0f,
        val ramFreeMb    : Long  = 0L,
        val ramTotalMb   : Long  = 0L,
        val cpuMhz       : Int   = 0,
        val health       : SystemHealth = SystemHealth.Stable
    ) {
        val ramUsedPct: Int get() =
            if (ramTotalMb > 0) ((1f - ramFreeMb.toFloat() / ramTotalMb.toFloat()) * 100).toInt()
            else 0
        val healthLabel: String get() = when (health) {
            SystemHealth.Stable      -> "Estable"
            SystemHealth.MediumLoad  -> "Carga media"
            SystemHealth.HighLoad    -> "Alta carga"
            SystemHealth.ThermalRisk -> "Riesgo térmico"
        }
    }

    // ── Estado anterior de /proc/stat para cálculo de CPU usage ─────────────
    private var prevIdle  : Long = 0L
    private var prevTotal : Long = 0L

    // ── Lectura de CPU usage desde /proc/stat ─────────────────────────────
    private fun readCpuUsagePct(): Int {
        return try {
            val line = File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") }
                ?: return 0
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size < 8) return 0
            // user, nice, system, idle, iowait, irq, softirq, steal
            val user   = parts[1].toLong()
            val nice   = parts[2].toLong()
            val system = parts[3].toLong()
            val idle   = parts[4].toLong()
            val iowait = parts[5].toLong()
            val irq    = parts[6].toLong()
            val soft   = parts[7].toLong()
            val steal  = if (parts.size > 8) parts[8].toLong() else 0L
            val idleAll  = idle + iowait
            val totalAll = user + nice + system + idleAll + irq + soft + steal
            val diffIdle  = idleAll  - prevIdle
            val diffTotal = totalAll - prevTotal
            prevIdle  = idleAll
            prevTotal = totalAll
            if (diffTotal <= 0L) return 0
            ((1f - diffIdle.toFloat() / diffTotal.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } catch (e: Exception) {
            Log.w(TAG, "readCpuUsage: ${e.message}")
            0
        }
    }

    // ── Snapshot completo del sistema ─────────────────────────────────────
    suspend fun getSnapshot(ramFreeMb: Long, ramTotalMb: Long): SystemSnapshot =
        withContext(Dispatchers.IO) {
            val cpuUsage  = readCpuUsagePct()
            val cpuTemp   = readCpuTempC()
            val battTemp  = readBatteryTempC()
            val cpuMhz    = readCpuFreqMhz()
            val health    = classify(cpuUsage, cpuTemp)
            SystemSnapshot(
                cpuUsagePct = cpuUsage,
                cpuTempC    = cpuTemp,
                battTempC   = battTemp,
                ramFreeMb   = ramFreeMb,
                ramTotalMb  = ramTotalMb,
                cpuMhz      = cpuMhz,
                health      = health
            )
        }

    // ── Clasificación inteligente del estado ─────────────────────────────
    private fun classify(cpuPct: Int, tempC: Float): SystemHealth = when {
        tempC >= 48f                         -> SystemHealth.ThermalRisk
        tempC >= 44f || cpuPct > 80         -> SystemHealth.HighLoad
        tempC >= 38f || cpuPct > 60         -> SystemHealth.MediumLoad
        else                                 -> SystemHealth.Stable
    }

    // ── Decisión de mantenimiento adaptativo ─────────────────────────────
    /**
     * Decide si se debe ejecutar mantenimiento basado en el estado actual.
     * Devuelve true si se ejecutó alguna acción.
     * Llamar desde GameService cada 60 s (en lugar de cada 5 min fijo).
     */
    suspend fun runAdaptiveMaintenance(
        snapshot: SystemSnapshot,
        minutesSinceLastFull: Int
    ): Boolean {
        return when (snapshot.health) {
            SystemHealth.ThermalRisk -> {
                Log.w(TAG, "THERMAL RISK — acción de emergencia")
                // Emergencia: ejecutar limpieza máxima inmediatamente
                ShizukuHelper.run(
                    "settings put system screen_brightness 50 ; " +
                    "settings put global background_process_limit 0 ; " +
                    "am kill-all ; " +
                    "am force-stop com.samsung.android.game.gos ; " +
                    "am force-stop com.samsung.android.bixby.agent ; " +
                    "am force-stop com.samsung.android.bixby.service"
                )
                true
            }
            SystemHealth.HighLoad -> {
                Log.d(TAG, "HIGH LOAD — mantenimiento cada 2 min")
                // Alta carga: mantenimiento acelerado cada 2 minutos
                if (minutesSinceLastFull % 2 == 0) {
                    ShizukuHelper.applyMaintenanceMode()
                    true
                } else false
            }
            SystemHealth.MediumLoad -> {
                // Carga media: mantenimiento cada 3 minutos
                if (minutesSinceLastFull % 3 == 0) {
                    ShizukuHelper.applyMaintenanceMode()
                    true
                } else false
            }
            SystemHealth.Stable -> {
                // Estable: mantenimiento cada 5 minutos (original v53)
                if (minutesSinceLastFull % 5 == 0) {
                    ShizukuHelper.applyMaintenanceMode()
                    true
                } else false
            }
        }
    }

    // ── Análisis de sesión para sugerencias de timing ────────────────────
    fun getSessionAdvice(sessionMs: Long, snapshot: SystemSnapshot): String? {
        val minActive = sessionMs / 60_000L
        return when {
            snapshot.health == SystemHealth.ThermalRisk ->
                "Pausa recomendada — temp crítica ${snapshot.cpuTempC.toInt()}°C"
            snapshot.health == SystemHealth.HighLoad && minActive > 30 ->
                "Sesión larga + alta carga — considera pausa breve"
            snapshot.battTempC > 40f ->
                "Batería caliente (${snapshot.battTempC.toInt()}°C) — descarga el cargador"
            snapshot.ramUsedPct > 85 ->
                "RAM al ${snapshot.ramUsedPct}% — ejecuta limpieza"
            else -> null
        }
    }
}
