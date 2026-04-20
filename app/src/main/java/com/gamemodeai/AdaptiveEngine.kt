package com.gamemodeai

import android.content.Context
import android.util.Log
import java.util.LinkedList

/**
 * AdaptiveEngine — Motor de IA adaptativa para Samsung Galaxy A06
 *
 * Aprende de: CPU spikes, presión de RAM, temperatura, duración de sesión.
 * Almacena: último estado estable, delay preferido, umbrales seguros.
 * Modelo: promedios deslizantes + memoria de sesión + sistema de puntuación.
 * NO usa ML pesado — solo lógica ligera.
 */
object AdaptiveEngine {

    private const val TAG = "AdaptiveEngine"

    // ── Ventanas deslizantes (20 muestras) ───────────────────────────────────
    private const val WINDOW_SIZE = 20
    private val cpuWindow  = LinkedList<Int>()
    private val tempWindow = LinkedList<Float>()
    private val ramWindow  = LinkedList<Int>()

    // ── Contadores de sesión ─────────────────────────────────────────────────
    private var spikeCount     = 0
    private var stableCount    = 0
    private var totalCycles    = 0
    private var sessionStartMs = 0L

    // ── Delay adaptativo (ms) — rango A06: 6000–8000 ────────────────────────
    private var currentDelayMs   = 6500L
    private const val DELAY_MIN  = 6000L
    private const val DELAY_MAX  = 8000L
    private const val DELAY_STEP = 250L

    // ── Puntuación de estabilidad (0–100) ────────────────────────────────────
    private var stabilityScore = 50

    // ── Estado de aprendizaje ────────────────────────────────────────────────
    enum class LearningState { OBSERVING, ADAPTING, STABILIZED, THERMAL_CAUTION }

    @Volatile var learningState: LearningState = LearningState.OBSERVING
        private set

    // ── Umbrales seguros adaptativos ─────────────────────────────────────────
    private var safeCpuPct  = 75
    private var safeTempC   = 38f

    // ── Último estado estable ────────────────────────────────────────────────
    data class StableState(
        val avgCpuPct        : Int   = 0,
        val avgTempC         : Float = 0f,
        val avgRamPct        : Int   = 0,
        val preferredDelayMs : Long  = 6500L
    )
    @Volatile var lastStableState: StableState? = null
        private set

    // ── Decisión del motor ───────────────────────────────────────────────────
    data class AdaptiveDecision(
        val recommendedDelayMs   : Long         = 6500L,
        val shouldRunMaintenance : Boolean      = true,
        val maintenanceFrequency : Int          = 3,
        val learningState        : LearningState = LearningState.OBSERVING,
        val stabilityScore       : Int          = 50,
        val advice               : String       = ""
    )

    /**
     * Procesa una muestra del sistema y devuelve la decisión adaptativa.
     * Llamar en cada ciclo del GameService.
     */
    fun processSample(
        cpuPct     : Int,
        tempC      : Float,
        ramFreeMb  : Long,
        ramTotalMb : Long,
        sessionMs  : Long
    ): AdaptiveDecision {
        if (sessionStartMs == 0L) sessionStartMs = System.currentTimeMillis()
        totalCycles++

        val ramPct = if (ramTotalMb > 0)
            ((1f - ramFreeMb.toFloat() / ramTotalMb.toFloat()) * 100f).toInt() else 0

        updateWindow(cpuWindow, cpuPct)
        updateWindow(tempWindow, tempC)
        updateWindow(ramWindow, ramPct)

        val avgCpu  = cpuWindow.average().toInt()
        val avgTemp = tempWindow.average().toFloat()
        val avgRam  = ramWindow.average().toInt()

        // ── Detección de spike ───────────────────────────────────────────────
        val isSpikey = cpuPct > safeCpuPct
        if (isSpikey) spikeCount++ else stableCount++

        // ── Actualizar puntuación ────────────────────────────────────────────
        updateScore(cpuPct, tempC, ramPct)

        // ── Adaptar delay ────────────────────────────────────────────────────
        when {
            tempC >= 39f -> currentDelayMs = DELAY_MAX
            isSpikey     -> currentDelayMs = (currentDelayMs + DELAY_STEP).coerceAtMost(DELAY_MAX)
            !isSpikey && stableCount > 10 ->
                currentDelayMs = (currentDelayMs - DELAY_STEP).coerceAtLeast(DELAY_MIN)
        }

        // ── Adaptar umbrales ─────────────────────────────────────────────────
        if (totalCycles >= WINDOW_SIZE && avgCpu < safeCpuPct - 10)
            safeCpuPct = (safeCpuPct - 1).coerceAtLeast(60)
        if (totalCycles >= WINDOW_SIZE && avgTemp < safeTempC - 3f)
            safeTempC = (safeTempC - 0.5f).coerceAtLeast(35f)

        // ── Determinar estado de aprendizaje ─────────────────────────────────
        learningState = when {
            tempC >= 39f              -> LearningState.THERMAL_CAUTION
            totalCycles < WINDOW_SIZE -> LearningState.OBSERVING
            stabilityScore > 75       -> LearningState.STABILIZED
            spikeCount > stableCount  -> LearningState.ADAPTING
            else                      -> LearningState.STABILIZED
        }

        // ── Guardar último estado estable ────────────────────────────────────
        if (learningState == LearningState.STABILIZED) {
            lastStableState = StableState(avgCpu, avgTemp, avgRam, currentDelayMs)
        }

        // ── Frecuencia de mantenimiento adaptativa ───────────────────────────
        val mainFreq = when {
            learningState == LearningState.THERMAL_CAUTION -> 10
            spikeCount > stableCount                       -> 2
            stabilityScore > 75                            -> 6
            else                                           -> 3
        }

        return AdaptiveDecision(
            recommendedDelayMs   = currentDelayMs,
            shouldRunMaintenance = learningState != LearningState.THERMAL_CAUTION,
            maintenanceFrequency = mainFreq,
            learningState        = learningState,
            stabilityScore       = stabilityScore,
            advice               = buildAdvice(avgCpu, avgTemp, avgRam, sessionMs)
        )
    }

    private fun updateScore(cpuPct: Int, tempC: Float, ramPct: Int) {
        val d = (if (cpuPct > 85) -10 else if (cpuPct > 70) -5 else if (cpuPct < 50) 5 else 0) +
                (if (tempC > 39f) -15 else if (tempC > 36f) -7 else if (tempC < 33f) 5 else 0) +
                (if (ramPct > 85) -8  else if (ramPct > 75) -3 else if (ramPct < 60) 3 else 0)
        stabilityScore = (stabilityScore + d).coerceIn(0, 100)
    }

    private fun buildAdvice(avgCpu: Int, avgTemp: Float, avgRam: Int, sessionMs: Long): String {
        val min = sessionMs / 60_000L
        return when {
            avgTemp >= 39f  -> "Temperatura critica — enfriamiento activo"
            avgTemp >= 36f  -> "Temperatura elevada — reduciendo frecuencia"
            avgCpu > 80     -> "CPU alta — delay aumentado a ${currentDelayMs}ms"
            avgRam > 85     -> "RAM casi llena — mantenimiento priorizado"
            min > 45        -> "Sesion larga ($min min) — vigilando temperatura"
            stabilityScore > 75 -> "Sistema estabilizado — optimizacion minima"
            else            -> "Aprendiendo patrones — ciclo $totalCycles"
        }
    }

    private fun <T> updateWindow(w: LinkedList<T>, v: T) {
        w.addLast(v); if (w.size > WINDOW_SIZE) w.removeFirst()
    }

    // ── Persistencia ─────────────────────────────────────────────────────────

    fun saveState(context: Context) {
        context.getSharedPreferences("adaptive_engine", Context.MODE_PRIVATE).edit()
            .putLong("delay_ms",         currentDelayMs)
            .putInt("stability_score",   stabilityScore)
            .putInt("safe_cpu_pct",      safeCpuPct)
            .putFloat("safe_temp_c",     safeTempC)
            .putInt("total_cycles",      totalCycles)
            .apply()
        Log.d(TAG, "Estado guardado — delay=$currentDelayMs score=$stabilityScore")
    }

    fun restoreState(context: Context) {
        val p = context.getSharedPreferences("adaptive_engine", Context.MODE_PRIVATE)
        currentDelayMs = p.getLong("delay_ms", 6500L).coerceIn(DELAY_MIN, DELAY_MAX)
        stabilityScore = p.getInt("stability_score", 50)
        safeCpuPct     = p.getInt("safe_cpu_pct", 75)
        safeTempC      = p.getFloat("safe_temp_c", 38f)
        Log.d(TAG, "Estado restaurado — delay=$currentDelayMs score=$stabilityScore")
    }

    fun resetSession() {
        cpuWindow.clear(); tempWindow.clear(); ramWindow.clear()
        spikeCount = 0; stableCount = 0; totalCycles = 0; sessionStartMs = 0L
        learningState = LearningState.OBSERVING
        Log.d(TAG, "Sesion reseteada")
    }

    fun getCurrentDelay()    : Long   = currentDelayMs
    fun getStabilityScore()  : Int    = stabilityScore
    fun getLearningStatus()  : String =
        "$learningState | Score:$stabilityScore | Delay:${currentDelayMs}ms"
}