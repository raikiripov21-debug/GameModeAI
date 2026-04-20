package com.gamemode.a26

import android.content.Context
import android.util.Log
import java.util.LinkedList

/**
 * AdaptiveEngine — Samsung Galaxy A26 5G
 * Exynos 1380 | 6 GB RAM | 90Hz AMOLED | Android 14
 *
 * PROTEGIDO: solo corregir si el build falla.
 * Modelo ligero: promedios deslizantes + scoring + memoria de sesion.
 */
object AdaptiveEngine {

    private const val TAG = "AdaptiveEngine_A26"
    private const val WINDOW = 20

    private val cpuW  = LinkedList<Int>()
    private val tempW = LinkedList<Float>()
    private val ramW  = LinkedList<Int>()

    private var spikes = 0; private var stable = 0; private var cycles = 0
    private var score  = 55  // A26 arranca más estable (más RAM)
    private var delayMs = 6000L  // A26 puede ir más rápido (8 núcleos potentes)

    enum class State { OBSERVING, ADAPTING, STABILIZED, THERMAL_CAUTION }

    @Volatile var state: State = State.OBSERVING; private set

    data class Decision(
        val delayMs   : Long  = 6000L,
        val runMaint  : Boolean = true,
        val maintFreq : Int   = 3,
        val state     : State = State.OBSERVING,
        val score     : Int   = 55,
        val advice    : String = ""
    )

    fun process(cpuPct: Int, tempC: Float, ramFreeMb: Long, ramTotalMb: Long): Decision {
        cycles++
        val ramPct = if (ramTotalMb > 0) ((1f - ramFreeMb.toFloat() / ramTotalMb) * 100).toInt() else 0
        fun <T> add(w: LinkedList<T>, v: T) { w.addLast(v); if (w.size > WINDOW) w.removeFirst() }
        add(cpuW, cpuPct); add(tempW, tempC); add(ramW, ramPct)

        val isSpikey = cpuPct > 78
        if (isSpikey) spikes++ else stable++

        val d = (if (cpuPct > 85) -8 else if (cpuPct < 50) 5 else 0) +
                (if (tempC > 39f) -12 else if (tempC > 36f) -5 else if (tempC < 32f) 5 else 0) +
                (if (ramPct > 88) -6 else if (ramPct < 55) 3 else 0)
        score = (score + d).coerceIn(0, 100)

        when {
            tempC >= 39f -> delayMs = 7500L
            isSpikey     -> delayMs = (delayMs + 200).coerceAtMost(7500L)
            !isSpikey && stable > 8 -> delayMs = (delayMs - 200).coerceAtLeast(5500L)
        }

        state = when {
            tempC >= 39f          -> State.THERMAL_CAUTION
            cycles < WINDOW       -> State.OBSERVING
            score > 72            -> State.STABILIZED
            spikes > stable       -> State.ADAPTING
            else                  -> State.STABILIZED
        }

        val freq = when {
            state == State.THERMAL_CAUTION -> 12
            spikes > stable                -> 2
            score > 72                     -> 7
            else                           -> 4
        }

        val advice = when {
            tempC >= 39f -> "Temperatura critica — proteccion termica activa"
            cpuPct > 82  -> "CPU alta — delay ajustado a ${delayMs}ms"
            ramPct > 88  -> "RAM alta — mantenimiento prioritario"
            score > 72   -> "Sistema estabilizado (A26)"
            else         -> "Aprendiendo — ciclo $cycles"
        }

        AimStabilizer.updateFromScore(score)
        return Decision(delayMs, state != State.THERMAL_CAUTION, freq, state, score, advice)
    }

    fun saveState(ctx: Context) = ctx.getSharedPreferences("adaptive_a26", 0).edit()
        .putLong("delay", delayMs).putInt("score", score).apply()

    fun restoreState(ctx: Context) {
        val p = ctx.getSharedPreferences("adaptive_a26", 0)
        delayMs = p.getLong("delay", 6000L).coerceIn(5500L, 7500L)
        score   = p.getInt("score", 55)
        Log.d(TAG, "Restaurado delay=$delayMs score=$score")
    }

    fun reset() {
        cpuW.clear(); tempW.clear(); ramW.clear()
        spikes = 0; stable = 0; cycles = 0; state = State.OBSERVING
    }

    fun status() = "$state | Score:$score | Delay:${delayMs}ms"
}
