package com.gamemode.a26

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign

/**
 * AimStabilizer v2 — Samsung Galaxy A26 5G (Exynos 1380 / 90 Hz)
 *
 *  1. Zona muerta adaptativa — sube bajo carga para absorber jitter de frame drops.
 *  2. EMA con alpha ligado al score del sistema.
 *  3. Predicción de velocidad — anticipa la dirección para evitar saltos al girar.
 *  4. Detector de ráfaga — neutraliza oscilaciones de signo rápidas.
 *  5. Aim lock suave — congela y aplica fricción cuando el jugador está quieto
 *     (≥7 frames bajo umbral a 90 Hz), sin latencia al reanudar movimiento.
 */
object AimStabilizer {

    // ── Parámetros Exynos 1380 / 90 Hz ───────────────────────────────────
    private const val DEAD_ZONE_BASE    = 0.35f
    private const val DEAD_ZONE_LOADED  = 0.60f
    private const val ALPHA_MIN         = 0.18f
    private const val ALPHA_MAX         = 0.75f
    private const val VELOCITY_MIX      = 0.28f
    private const val JITTER_THRESHOLD  = 3
    private const val JITTER_ALPHA_DEC  = 0.10f
    private const val JITTER_TICKS      = 10    // más ciclos a 90 Hz
    private const val STILL_THRESHOLD   = 0.20f
    private const val STILL_FRAMES_LOCK = 7     // 7 frames @ 90 Hz ≈ 78 ms
    private const val LOCK_FRICTION     = 0.82f

    // ── Estado ────────────────────────────────────────────────────────────
    private var smoothedX   = 0f
    private var smoothedY   = 0f
    private var velocityX   = 0f
    private var velocityY   = 0f
    private var alpha       = 0.50f
    private var systemScore = 55

    private var prevSignX    = 0f
    private var prevSignY    = 0f
    private var jitterCntX   = 0
    private var jitterCntY   = 0
    private var jitterActive = false
    private var jitterTicks  = 0

    private var stillFrames = 0
    private var lockActive  = false

    // ── API ───────────────────────────────────────────────────────────────

    fun smooth(rawDx: Float, rawDy: Float): Pair<Float, Float> {
        val dz    = dynamicDeadZone()
        val filtX = applyDeadZone(rawDx, dz)
        val filtY = applyDeadZone(rawDy, dz)
        val mag   = hypot(filtX, filtY)

        // Aim lock suave
        if (mag < STILL_THRESHOLD) {
            stillFrames++
            if (stillFrames >= STILL_FRAMES_LOCK) {
                lockActive = true
                smoothedX *= LOCK_FRICTION
                smoothedY *= LOCK_FRICTION
                velocityX *= LOCK_FRICTION
                velocityY *= LOCK_FRICTION
                return Pair(smoothedX, smoothedY)
            }
        } else {
            stillFrames = 0
            lockActive  = false
        }

        // Predicción de velocidad
        val predX = filtX + VELOCITY_MIX * velocityX
        val predY = filtY + VELOCITY_MIX * velocityY

        // EMA adaptativo
        val eff = effectiveAlpha(rawDx, rawDy)
        smoothedX = eff * predX + (1f - eff) * smoothedX
        smoothedY = eff * predY + (1f - eff) * smoothedY
        velocityX = smoothedX - predX * (1f - eff)
        velocityY = smoothedY - predY * (1f - eff)

        return Pair(smoothedX, smoothedY)
    }

    fun updateFromScore(score: Int) {
        systemScore = score.coerceIn(0, 100)
        val t = systemScore / 100f
        alpha = ALPHA_MIN + t * (ALPHA_MAX - ALPHA_MIN)
    }

    fun reset() {
        smoothedX = 0f; smoothedY = 0f
        velocityX = 0f; velocityY = 0f
        prevSignX = 0f; prevSignY = 0f
        jitterCntX = 0; jitterCntY = 0
        jitterActive = false; jitterTicks = 0
        stillFrames = 0; lockActive = false
    }

    fun isLocked(): Boolean = lockActive

    // ── Privados ──────────────────────────────────────────────────────────

    private fun dynamicDeadZone(): Float {
        val t = systemScore / 100f
        return DEAD_ZONE_LOADED + t * (DEAD_ZONE_BASE - DEAD_ZONE_LOADED)
    }

    private fun applyDeadZone(v: Float, dz: Float) = if (abs(v) < dz) 0f else v

    private fun effectiveAlpha(dx: Float, dy: Float): Float {
        detectJitter(dx, dy)
        return if (jitterActive) {
            jitterTicks--
            if (jitterTicks <= 0) jitterActive = false
            (alpha - JITTER_ALPHA_DEC).coerceAtLeast(ALPHA_MIN)
        } else alpha
    }

    private fun detectJitter(dx: Float, dy: Float) {
        val sx = sign(dx); val sy = sign(dy)
        if (sx != 0f && sx != prevSignX) jitterCntX++ else jitterCntX = 0
        if (sy != 0f && sy != prevSignY) jitterCntY++ else jitterCntY = 0
        if (sx != 0f) prevSignX = sx
        if (sy != 0f) prevSignY = sy
        if (jitterCntX >= JITTER_THRESHOLD || jitterCntY >= JITTER_THRESHOLD) {
            jitterActive = true
            jitterTicks  = JITTER_TICKS
            jitterCntX   = 0; jitterCntY = 0
        }
    }
}
