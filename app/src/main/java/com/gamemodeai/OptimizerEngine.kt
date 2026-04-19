package com.gamemodeai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * OptimizerEngine — Motor de otimização inteligente para Samsung A06
 *
 * REGRAS ESTRITAS (A06):
 * - delay: 6000–7000ms (implementado no GameService)
 * - anti-spam: 10–12s (implementado no ShizukuHelper)
 * - PARAR se temperatura > 40°C
 * - APENAS otimizações leves
 * - NUNCA modo FULL / comandos agressivos
 */
object OptimizerEngine {

    private const val TAG = "OptimizerEngine"

    // Temperatura máxima permitida para executar ações — spec: > 40°C = parar
    const val THERMAL_STOP_TEMP_C = 40f

    // ── Estado de saúde do sistema ───────────────────────────────────────────
    sealed class SystemHealth {
        object Stable      : SystemHealth()   // CPU <60%, temp <35°C
        object MediumLoad  : SystemHealth()   // CPU 60-80% ou temp 35-40°C
        object HighLoad    : SystemHealth()   // CPU >80%
        object ThermalRisk : SystemHealth()   // temp ≥40°C — PARAR ações
    }

    data class SystemSnapshot(
        val cpuUsagePct  : Int   = 0,
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
            SystemHealth.Stable      -> "Estável"
            SystemHealth.MediumLoad  -> "Carga média"
            SystemHealth.HighLoad    -> "Alta carga"
            SystemHealth.ThermalRisk -> "Risco térmico"
        }
    }

    // ── Estado anterior de /proc/stat ───────────────────────────────────────
    private var prevIdle  : Long = 0L
    private var prevTotal : Long = 0L

    // ── Leitura de uso de CPU (/proc/stat) ──────────────────────────────────
    private fun readCpuUsagePct(): Int {
        return try {
            val line = File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") }
                ?: return 0
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size < 8) return 0
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

    // ── Temperatura da CPU (/sys/class/thermal) ──────────────────────────────
    fun readCpuTempC(): Float {
        // Tenta múltiplos caminhos comuns para o sensor de temperatura
        val paths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/class/thermal/thermal_zone2/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/kernel/debug/sprd_temp"
        )
        for (path in paths) {
            try {
                val raw = File(path).readText().trim().toLongOrNull() ?: continue
                val temp = if (raw > 1000) raw / 1000f else raw.toFloat()
                if (temp in 20f..80f) return temp
            } catch (_: Exception) { }
        }
        return 0f
    }

    // ── Temperatura da bateria (/sys/class/power_supply) ─────────────────────
    fun readBatteryTempC(): Float {
        val paths = listOf(
            "/sys/class/power_supply/battery/temp",
            "/sys/class/power_supply/Battery/temp"
        )
        for (path in paths) {
            try {
                val raw = File(path).readText().trim().toIntOrNull() ?: continue
                return raw / 10f
            } catch (_: Exception) { }
        }
        return 0f
    }

    // ── Frequência da CPU (MHz) ──────────────────────────────────────────────
    fun readCpuFreqMhz(): Int {
        val paths = listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq",
            "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_cur_freq"
        )
        for (path in paths) {
            try {
                val khz = File(path).readText().trim().toIntOrNull() ?: continue
                return khz / 1000
            } catch (_: Exception) { }
        }
        return 0
    }

    // ── Snapshot completo do sistema ─────────────────────────────────────────
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

    /**
     * Indica se as ações de manutenção devem ser pausadas.
     * Spec: parar se temperatura > 40°C.
     */
    fun shouldPauseActions(snapshot: SystemSnapshot): Boolean {
        return snapshot.cpuTempC > THERMAL_STOP_TEMP_C ||
               snapshot.battTempC > THERMAL_STOP_TEMP_C
    }

    // ── Classificação do estado ──────────────────────────────────────────────
    private fun classify(cpuPct: Int, tempC: Float): SystemHealth = when {
        tempC >= THERMAL_STOP_TEMP_C             -> SystemHealth.ThermalRisk
        cpuPct > 80                              -> SystemHealth.HighLoad
        tempC >= 35f || cpuPct > 60             -> SystemHealth.MediumLoad
        else                                     -> SystemHealth.Stable
    }

    /**
     * Manutenção adaptativa — SEM comandos proibidos.
     *
     * ThermalRisk (temp > 40°C): PARAR ações, apenas reduzir brilho.
     * HighLoad: manutenção leve a cada 2 ciclos.
     * MediumLoad: manutenção leve a cada 3 ciclos.
     * Stable: manutenção leve a cada 5 ciclos.
     *
     * PROIBIDO: am kill-all, am force-stop, qualquer comando agressivo.
     */
    suspend fun runAdaptiveMaintenance(
        snapshot: SystemSnapshot,
        minutesSinceLastFull: Int
    ): Boolean {
        return when (snapshot.health) {
            SystemHealth.ThermalRisk -> {
                Log.w(TAG, "THERMAL RISK ${snapshot.cpuTempC}°C > 40°C — pausando ações")
                // Apenas reduzir brilho via setting seguro — SEM am kill-all, SEM am force-stop
                ShizukuHelper.run("settings put system screen_brightness 50")
                false  // false = não executou manutenção completa (sinal de pausa para o service)
            }
            SystemHealth.HighLoad -> {
                Log.d(TAG, "HIGH LOAD CPU=${snapshot.cpuUsagePct}% — manutenção leve")
                if (minutesSinceLastFull % 2 == 0) {
                    ShizukuHelper.applyMaintenanceMode()
                    true
                } else false
            }
            SystemHealth.MediumLoad -> {
                if (minutesSinceLastFull % 3 == 0) {
                    ShizukuHelper.applyMaintenanceMode()
                    true
                } else false
            }
            SystemHealth.Stable -> {
                if (minutesSinceLastFull % 5 == 0) {
                    ShizukuHelper.applyMaintenanceMode()
                    true
                } else false
            }
        }
    }

    // ── Sugestões de sessão ──────────────────────────────────────────────────
    fun getSessionAdvice(sessionMs: Long, snapshot: SystemSnapshot): String? {
        val minActive = sessionMs / 60_000L
        return when {
            snapshot.health == SystemHealth.ThermalRisk ->
                "Pausa recomendada — temp ${snapshot.cpuTempC.toInt()}°C acima do limite"
            snapshot.health == SystemHealth.HighLoad && minActive > 30 ->
                "Sessão longa + alta carga — considera pausa breve"
            snapshot.battTempC > 38f ->
                "Bateria quente (${snapshot.battTempC.toInt()}°C) — desconecte o carregador"
            snapshot.ramUsedPct > 85 ->
                "RAM em ${snapshot.ramUsedPct}% — execute limpeza"
            else -> null
        }
    }
}
