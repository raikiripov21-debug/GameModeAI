package com.gamemode.moto

import android.content.Context
import android.util.Log
import java.util.LinkedList

/**
 * AdaptiveEngine — Motorola Moto G04s
 * Unisoc T606 | 4 GB RAM | 90Hz IPS LCD
 *
 * Parametros INDEPENDIENTES — sin compartir con A06 ni A26.
 * Umbral termico: 36°C (mas bajo por menor disipacion).
 * RAM disponible: 4 GB (umbral de presion mas bajo).
 */
object AdaptiveEngine {

    private const val TAG = "AdaptiveEngine_Moto"
    private const val WINDOW = 15  // ventana mas pequeña — 4GB RAM, reaccionar mas rapido

    private val cpuW  = LinkedList<Int>()
    private val tempW = LinkedList<Float>()
    private val ramW  = LinkedList<Int>()

    private var spikes = 0; private var stable = 0; private var cycles = 0
    private var score  = 45  // Moto arranca con score menor (hardware mas limitado)
    private var delayMs = 7000L  // Delay mayor por defecto (Unisoc T606 mas lento)

    enum class State { OBSERVING, ADAPTING, STABILIZED, THERMAL_CAUTION }
    @Volatile var state: State = State.OBSERVING; private set

    data class Decision(
        val delayMs   : Long  = 7000L,
        val runMaint  : Boolean = true,
        val maintFreq : Int   = 3,
        val state     : State = State.OBSERVING,
        val score     : Int   = 45,
        val advice    : String = ""
    )

    fun process(cpuPct: Int, tempC: Float, ramFreeMb: Long, ramTotalMb: Long): Decision {
        cycles++
        val ramPct = if (ramTotalMb > 0) ((1f - ramFreeMb.toFloat() / ramTotalMb) * 100).toInt() else 0
        fun <T> add(w: LinkedList<T>, v: T) { w.addLast(v); if (w.size > WINDOW) w.removeFirst() }
        add(cpuW, cpuPct); add(tempW, tempC); add(ramW, ramPct)

        // Unisoc T606: umbral de spike a 70% (mas bajo que A06/A26)
        val isSpikey = cpuPct > 70
        if (isSpikey) spikes++ else stable++

        // Scoring mas agresivo — 4GB RAM se llena mas rapido
        val d = (if (cpuPct > 80) -10 else if (cpuPct < 45) 4 else 0) +
                (if (tempC > 35f) -14 else if (tempC > 32f) -6 else if (tempC < 28f) 4 else 0) +
                (if (ramPct > 82) -10 else if (ramPct > 70) -5 else if (ramPct < 50) 4 else 0)
        score = (score + d).coerceIn(0, 100)

        // Delay — rango Moto: 6500–9000ms (mas conservador)
        when {
            tempC >= 35f -> delayMs = 9000L
            isSpikey     -> delayMs = (delayMs + 300).coerceAtMost(9000L)
            !isSpikey && stable > 6 -> delayMs = (delayMs - 200).coerceAtLeast(6500L)
        }

        state = when {
            tempC >= 35f          -> State.THERMAL_CAUTION
            cycles < WINDOW       -> State.OBSERVING
            score > 65            -> State.STABILIZED
            spikes > stable       -> State.ADAPTING
            else                  -> State.STABILIZED
        }

        val freq = when {
            state == State.THERMAL_CAUTION -> 15  // muy poco frecuente si hay calor
            spikes > stable                -> 2
            score > 65                     -> 8
            else                           -> 4
        }

        val advice = when {
            tempC >= 35f -> "Temperatura maxima Moto — proteccion activa"
            cpuPct > 75  -> "CPU Unisoc alta — delay=${delayMs}ms"
            ramPct > 82  -> "RAM 4GB en riesgo — mantenimiento activo"
            score > 65   -> "Moto G04s estabilizado"
            else         -> "Aprendiendo Moto — ciclo $cycles"
        }

        return Decision(delayMs, state != State.THERMAL_CAUTION, freq, state, score, advice)
    }

    fun saveState(ctx: Context) = ctx.getSharedPreferences("adaptive_moto", 0).edit()
        .putLong("delay", delayMs).putInt("score", score).apply()

    fun restoreState(ctx: Context) {
        val p = ctx.getSharedPreferences("adaptive_moto", 0)
        delayMs = p.getLong("delay", 7000L).coerceIn(6500L, 9000L)
        score   = p.getInt("score", 45)
        Log.d(TAG, "Restaurado delay=$delayMs score=$score")
    }

    fun reset() {
        cpuW.clear(); tempW.clear(); ramW.clear()
        spikes = 0; stable = 0; cycles = 0; state = State.OBSERVING
    }

    fun status() = "$state | Score:$score | Delay:${delayMs}ms"
}
