package com.gamemode.moto

import kotlin.math.abs
import kotlin.math.sign

/**
 * AimStabilizer — Motorola Moto G04s (Unisoc T606)
 *
 * Elimina la "mira bipolar" en el Moto G04s.
 * Parámetros independientes para Unisoc T606 @ 60 Hz.
 *
 * El T606 tiene menor throughput de GPU que Exynos/Snapdragon, por lo que
 * los frames caen más en momentos de carga. El estabilizador compensa
 * aumentando el suavizado cuando AdaptiveEngine detecta estrés del SoC.
 *
 *  • EMA: suaviza el delta de mira frame a frame.
 *  • Zona muerta 0.5 dp: más conservadora (60 Hz = menos frames = más jitter visible).
 *  • Anti-ráfaga: detecta cambios de signo consecutivos → activa modo calma.
 *  • Score binding: score bajo → alpha bajo (más suavizado automático).
 */
object AimStabilizer {

    // ── Parámetros Unisoc T606 / 60 Hz ───────────────────────────────────
    private const val DEAD_ZONE_DP     = 0.5f   // zona muerta mayor (60 Hz, señal más ruidosa)
    private const val ALPHA_MIN        = 0.15f  // bajo carga extrema: suavizado agresivo
    private const val ALPHA_MAX        = 0.70f  // sistema libre: responsivo pero controlado
    private const val JITTER_THRESHOLD = 3
    private const val JITTER_BOOST_DEC = 0.12f  // reducción extra de alpha (SoC más débil)
    private const val JITTER_TICKS     = 12     // más ciclos de calma (60 fps → más tiempo)

    // ── Estado ────────────────────────────────────────────────────────────
    private var smoothedX = 0f
    private var smoothedY = 0f
    private var alpha = 0.45f          // conservador por defecto en Moto

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

    /**
     * Adapta la sensibilidad según puntuación del AdaptiveEngine (0-100).
     * Score alto = sistema libre = mira más responsiva.
     * Score bajo = SoC cargado = suavizado automático para compensar frame drops.
     */
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
