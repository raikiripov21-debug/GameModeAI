package com.gamemodeai

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import java.io.File

/**
 * SystemMonitor — Leitura de RAM, CPU e temperatura para Samsung Galaxy A06
 *
 * Leitura apenas de /proc e /sys (APIs públicas) — sem root.
 * Complementa o OptimizerEngine com helpers de contexto Android.
 */
object SystemMonitor {

    private const val TAG = "SystemMonitor"

    // ── RAM ──────────────────────────────────────────────────────────────────

    fun getAvailableRamMb(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem / (1024L * 1024L)
    }

    fun getTotalRamMb(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.totalMem / (1024L * 1024L)
    }

    fun isMemoryLow(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.lowMemory
    }

    // ── CPU ──────────────────────────────────────────────────────────────────

    /** Frequência atual da CPU em MHz — lê de /sys/devices/system/cpu */
    fun readCpuFreqMhz(): Int = OptimizerEngine.readCpuFreqMhz()

    /** Frequência máxima do cpu0 em MHz */
    fun readMaxCpuFreqMhz(): Int {
        return try {
            val raw = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
                .readText().trim().toIntOrNull() ?: return 0
            raw / 1000
        } catch (e: Exception) {
            Log.w(TAG, "readMaxCpuFreq: ${e.message}")
            0
        }
    }

    // ── Temperatura ──────────────────────────────────────────────────────────

    /** Temperatura da CPU em °C */
    fun readCpuTempC(): Float = OptimizerEngine.readCpuTempC()

    /** Temperatura da bateria em °C */
    fun readBatteryTempC(): Float = OptimizerEngine.readBatteryTempC()

    /** True se algum sensor estiver acima do limite térmico do A06 (40°C) */
    fun isThermalRisk(): Boolean {
        val cpuTemp  = readCpuTempC()
        val battTemp = readBatteryTempC()
        return cpuTemp  > OptimizerEngine.THERMAL_STOP_TEMP_C ||
               battTemp > OptimizerEngine.THERMAL_STOP_TEMP_C
    }

    // ── Resumo ───────────────────────────────────────────────────────────────

    data class SystemStatus(
        val ramFreeMb    : Long  = 0L,
        val ramTotalMb   : Long  = 0L,
        val cpuMhz       : Int   = 0,
        val maxCpuMhz    : Int   = 0,
        val cpuTempC     : Float = 0f,
        val battTempC    : Float = 0f,
        val thermalRisk  : Boolean = false
    ) {
        val freqPct: Float get() =
            if (maxCpuMhz > 0) cpuMhz.toFloat() / maxCpuMhz.toFloat() else 0f
        val ramPct: Float get() =
            if (ramTotalMb > 0) ramFreeMb.toFloat() / ramTotalMb.toFloat() else 0f
    }

    fun getStatus(context: Context): SystemStatus {
        val ramFree  = getAvailableRamMb(context)
        val ramTotal = getTotalRamMb(context)
        val cpuMhz   = readCpuFreqMhz()
        val maxMhz   = readMaxCpuFreqMhz()
        val cpuTemp  = readCpuTempC()
        val battTemp = readBatteryTempC()
        return SystemStatus(
            ramFreeMb   = ramFree,
            ramTotalMb  = ramTotal,
            cpuMhz      = cpuMhz,
            maxCpuMhz   = maxMhz,
            cpuTempC    = cpuTemp,
            battTempC   = battTemp,
            thermalRisk = isThermalRisk()
        )
    }
}
