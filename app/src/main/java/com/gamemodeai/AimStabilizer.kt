package com.gamemodeai

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * AimStabilizer — Samsung Galaxy A06
 *
 * Elimina la "mira bipolar": oscilaciones bruscas en el delta de toque/giroscopio.
 *
 * Técnicas usadas:
 *  1. EMA (Exponential Moving Average) — suaviza la señal sin agregar lag perceptible.
 *  2. Zona muerta dinámica — descarta micro-movimientos por debajo del umbral.
 *  3. Detector de ráfaga de jitter — si el delta alterna signo rápidamente, incrementa
 *     temporalmente el suavizado para calmar la señal.
 *  4. Adaptación desde AdaptiveEngine — cuando el sistema está bajo carga, aplica más
 *     suavizado para compensar los frames perdidos.
 */
object AimStabilizer {

    // ── Parámetros base ───────────────────────────────────────────────────
    private const val DEAD_ZONE_DP     = 0.3f   // movimientos < esto se ignoran
    private const val ALPHA_MIN        = 0.20f  // alpha bajo = muy suave (sistema cargado)
    private const val ALPHA_MAX        = 0.80f  // alpha alto = muy responsivo (sistema libre)
    private const val JITTER_THRESHOLD = 3      // ráfagas antes de activar modo anti-jitter
    private const val JITTER_BOOST_DEC = 0.08f  // reducción de alpha por ráfaga

    // ── Estado interno ────────────────────────────────────────────────────
    private var smoothedX = 0f
    private var smoothedY = 0f
    private var alpha = 0.55f          // valor inicial equilibrado

    private var prevSignX = 0f
    private var prevSignY = 0f
    private var jitterCountX = 0
    private var jitterCountY = 0
    private var jitterBoostActive = false
    private var jitterBoostTicks = 0

    // ── API pública ───────────────────────────────────────────────────────

    /**
     * Llama esto desde tu InputDispatcher o GestureListener con el delta
     * crudo de cada frame. Devuelve el delta estabilizado listo para aplicar.
     *
     * @param rawDx delta crudo en X (píxeles o unidades de giroscopio)
     * @param rawDy delta crudo en Y
     * @return Pair(dx_estabilizado, dy_estabilizado)
     */
    fun smooth(rawDx: Float, rawDy: Float): Pair<Float, Float> {
        val effectiveAlpha = computeEffectiveAlpha(rawDx, rawDy)

        val filteredX = applyDeadZone(rawDx)
        val filteredY = applyDeadZone(rawDy)

        smoothedX = effectiveAlpha * filteredX + (1f - effectiveAlpha) * smoothedX
        smoothedY = effectiveAlpha * filteredY + (1f - effectiveAlpha) * smoothedY

        return Pair(smoothedX, smoothedY)
    }

    /**
     * Llama esto cuando AdaptiveEngine produce una nueva decision.
     * Ajusta el alpha del filtro según la puntuación del sistema (0-100).
     * Score alto = sistema libre = alpha alto (responsivo).
     * Score bajo = sistema cargado = alpha bajo (más suavizado).
     */
    fun updateFromScore(score: Int) {
        val t = score.coerceIn(0, 100) / 100f
        alpha = ALPHA_MIN + t * (ALPHA_MAX - ALPHA_MIN)
    }

    /** Reinicia el estado (al iniciar/parar una sesión de juego). */
    fun reset() {
        smoothedX = 0f; smoothedY = 0f
        prevSignX = 0f; prevSignY = 0f
        jitterCountX = 0; jitterCountY = 0
        jitterBoostActive = false; jitterBoostTicks = 0
    }

    // ── Privados ──────────────────────────────────────────────────────────

    private fun applyDeadZone(v: Float): Float {
        return if (abs(v) < DEAD_ZONE_DP) 0f else v
    }

    private fun computeEffectiveAlpha(dx: Float, dy: Float): Float {
        detectJitter(dx, dy)
        return if (jitterBoostActive) {
            jitterBoostTicks--
            if (jitterBoostTicks <= 0) { jitterBoostActive = false }
            (alpha - JITTER_BOOST_DEC).coerceAtLeast(ALPHA_MIN)
        } else {
            alpha
        }
    }

    private fun detectJitter(dx: Float, dy: Float) {
        val sx = sign(dx)
        val sy = sign(dy)
        if (sx != 0f && sx != prevSignX) { jitterCountX++ } else { jitterCountX = 0 }
        if (sy != 0f && sy != prevSignY) { jitterCountY++ } else { jitterCountY = 0 }
        prevSignX = if (sx != 0f) sx else prevSignX
        prevSignY = if (sy != 0f) sy else prevSignY
        if (jitterCountX >= JITTER_THRESHOLD || jitterCountY >= JITTER_THRESHOLD) {
            jitterBoostActive = true
            jitterBoostTicks = 8
            jitterCountX = 0; jitterCountY = 0
        }
    }
}
