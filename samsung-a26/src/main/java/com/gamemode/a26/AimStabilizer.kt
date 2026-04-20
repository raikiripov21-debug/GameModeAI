package com.gamemode.a26

import kotlin.math.abs
import kotlin.math.sign

/**
 * AimStabilizer — Samsung Galaxy A26 5G (Exynos 1380)
 *
 * Elimina la "mira bipolar": oscilaciones bruscas del delta táctil/giroscopio.
 * Parámetros tuneados para Exynos 1380 a 90 Hz.
 *
 *  • EMA (Exponential Moving Average): suaviza sin lag perceptible.
 *  • Zona muerta dinámica: descarta micro-jitter por debajo del umbral.
 *  • Detector de ráfaga: si el signo del delta alterna rápido → más suavizado temporal.
 *  • Adaptación por score: score alto = sistema libre = mira responsiva.
 */
object AimStabilizer {

    // ── Parámetros Exynos 1380 / 90 Hz ───────────────────────────────────
    private const val DEAD_ZONE_DP     = 0.4f   // zona muerta un poco mayor (90 Hz, más datos)
    private const val ALPHA_MIN        = 0.18f  // suavizado máximo bajo carga
    private const val ALPHA_MAX        = 0.75f  // responsividad máxima sistema libre
    private const val JITTER_THRESHOLD = 3
    private const val JITTER_BOOST_DEC = 0.10f
    private const val JITTER_TICKS     = 10

    // ── Estado ────────────────────────────────────────────────────────────
    private var smoothedX = 0f
    private var smoothedY = 0f
    private var alpha = 0.50f

    private var prevSignX = 0f
    private var prevSignY = 0f
    private var jitterCountX = 0
    private var jitterCountY = 0
    private var jitterBoostActive = false
    private var jitterBoostTicks  = 0

    // ── API ───────────────────────────────────────────────────────────────

    /**
     * Suaviza el delta crudo de cada frame.
     * @return Pair(dx_estabilizado, dy_estabilizado)
     */
    fun smooth(rawDx: Float, rawDy: Float): Pair<Float, Float> {
        val eff = effectiveAlpha(rawDx, rawDy)
        smoothedX = eff * deadZone(rawDx) + (1f - eff) * smoothedX
        smoothedY = eff * deadZone(rawDy) + (1f - eff) * smoothedY
        return Pair(smoothedX, smoothedY)
    }

    /** Adapta la sensibilidad según puntuación del AdaptiveEngine (0-100). */
    fun updateFromScore(score: Int) {
        val t = score.coerceIn(0, 100) / 100f
        alpha = ALPHA_MIN + t * (ALPHA_MAX - ALPHA_MIN)
    }

    /** Reinicia al iniciar/cerrar sesión de juego. */
    fun reset() {
        smoothedX = 0f; smoothedY = 0f
        prevSignX = 0f; prevSignY = 0f
        jitterCountX = 0; jitterCountY = 0
        jitterBoostActive = false; jitterBoostTicks = 0
    }

    // ── Privados ──────────────────────────────────────────────────────────

    private fun deadZone(v: Float) = if (abs(v) < DEAD_ZONE_DP) 0f else v

    private fun effectiveAlpha(dx: Float, dy: Float): Float {
        detectJitter(dx, dy)
        return if (jitterBoostActive) {
            jitterBoostTicks--
            if (jitterBoostTicks <= 0) jitterBoostActive = false
            (alpha - JITTER_BOOST_DEC).coerceAtLeast(ALPHA_MIN)
        } else alpha
    }

    private fun detectJitter(dx: Float, dy: Float) {
        val sx = sign(dx); val sy = sign(dy)
        if (sx != 0f && sx != prevSignX) jitterCountX++ else jitterCountX = 0
        if (sy != 0f && sy != prevSignY) jitterCountY++ else jitterCountY = 0
        if (sx != 0f) prevSignX = sx
        if (sy != 0f) prevSignY = sy
        if (jitterCountX >= JITTER_THRESHOLD || jitterCountY >= JITTER_THRESHOLD) {
            jitterBoostActive = true
            jitterBoostTicks  = JITTER_TICKS
            jitterCountX = 0; jitterCountY = 0
        }
    }
}
