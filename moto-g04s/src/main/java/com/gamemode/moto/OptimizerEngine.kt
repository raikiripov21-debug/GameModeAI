package com.gamemode.moto

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * OptimizerEngine — Motorola Moto G04s
 * SoC: Unisoc T606 | 8 cores (A75+A55) | 4 GB RAM | 90Hz IPS LCD
 * TEMPERATURA MAXIMA: 36°C — umbral mas bajo por menor disipacion de calor.
 * PARAMETROS INDEPENDIENTES — sin compartir con A06 ni A26.
 */
object OptimizerEngine {

    private const val TAG = "OptimizerEngine_Moto"

    const val THERMAL_STOP_C = 36f  // Moto G04s se calienta mas rapido

    sealed class Health {
        object Stable      : Health()
        object MediumLoad  : Health()
        object HighLoad    : Health()
        object ThermalRisk : Health()
    }

    data class Snapshot(
        val cpuPct    : Int   = 0,
        val cpuTempC  : Float = 0f,
        val battTempC : Float = 0f,
        val ramFreeMb : Long  = 0L,
        val ramTotalMb: Long  = 0L,
        val cpuMhz    : Int   = 0,
        val health    : Health = Health.Stable
    ) {
        val ramUsedPct: Int get() =
            if (ramTotalMb > 0) ((1f - ramFreeMb.toFloat() / ramTotalMb) * 100).toInt() else 0
        val healthLabel: String get() = when (health) {
            Health.Stable      -> "Estable"
            Health.MediumLoad  -> "Carga media"
            Health.HighLoad    -> "Alta carga"
            Health.ThermalRisk -> "Riesgo termico"
        }
    }

    private var prevIdle = 0L; private var prevTotal = 0L

    private fun readCpuPct(): Int = try {
        val l = File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") } ?: return 0
        val p = l.trim().split("\\s+".toRegex())
        if (p.size < 8) return 0
        val idle = p[4].toLong() + p[5].toLong()
        val total = p.drop(1).take(7).sumOf { it.toLong() }
        val di = idle - prevIdle; val dt = total - prevTotal
        prevIdle = idle; prevTotal = total
        if (dt <= 0) 0 else ((1f - di.toFloat() / dt) * 100).toInt().coerceIn(0, 100)
    } catch (e: Exception) { Log.w(TAG, e.message.toString()); 0 }

    fun readCpuTempC(): Float {
        val paths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/devices/platform/soc/soc:ap-apb@20000000/2500000.i2c/i2c-5/5-0068/power_supply/battery/temp"
        )
        for (p in paths) try {
            val raw = File(p).readText().trim().toLongOrNull() ?: continue
            val t = if (raw > 1000) raw / 1000f else raw.toFloat()
            if (t in 20f..80f) return t
        } catch (_: Exception) {}
        return 0f
    }

    fun readBatteryTempC(): Float = try {
        val raw = File("/sys/class/power_supply/battery/temp").readText().trim().toIntOrNull() ?: return 0f
        raw / 10f
    } catch (_: Exception) { 0f }

    fun readCpuMhz(): Int = try {
        File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq").readText().trim().toIntOrNull()?.div(1000) ?: 0
    } catch (_: Exception) { 0 }

    suspend fun getSnapshot(ramFreeMb: Long, ramTotalMb: Long): Snapshot = withContext(Dispatchers.IO) {
        val cpu = readCpuPct(); val temp = readCpuTempC(); val batt = readBatteryTempC()
        val mhz = readCpuMhz()
        val health = when {
            temp >= THERMAL_STOP_C || batt >= THERMAL_STOP_C -> Health.ThermalRisk
            cpu > 75 -> Health.HighLoad                       // Unisoc T606 mas sensible
            temp >= 32f || cpu > 55 -> Health.MediumLoad
            else -> Health.Stable
        }
        Snapshot(cpu, temp, batt, ramFreeMb, ramTotalMb, mhz, health)
    }

    fun shouldPause(snap: Snapshot) = snap.cpuTempC > THERMAL_STOP_C || snap.battTempC > THERMAL_STOP_C
}
