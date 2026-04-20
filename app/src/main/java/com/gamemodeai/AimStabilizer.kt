package com.gamemodeai

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign

/**
 * AimStabilizer v2 — Samsung Galaxy A06
 *
 * Capas de estabilización (de más rápida a más lenta):
 *
 *  1. ZONA MUERTA ADAPTATIVA
 *     Ignora movimientos por debajo del umbral. El umbral sube
 *     automáticamente cuando el sistema está bajo carga para compensar
 *     el jitter causado por frame drops.
 *
 *  2. EMA (Exponential Moving Average)
 *     Suaviza el delta frame a frame sin agregar latencia perceptible.
 *     Alpha se adapta al score del sistema: sistema libre → más responsivo;
 *     sistema cargado → más suavizado.
 *
 *  3. PREDICCIÓN DE VELOCIDAD
 *     Mezcla el delta crudo con la velocidad estimada del frame anterior
 *     para anticipar la dirección y evitar saltos bruscos al cambiar de
 *     dirección.
 *
 *  4. DETECTOR DE RÁFAGA DE JITTER
 *     Si el signo del delta alterna ≥3 veces consecutivas en cualquier
 *     eje, activa "modo calma": reduce el alpha temporalmente para amortiguar
 *     la oscilación.
 *
 *  5. AIM LOCK SUAVE (anti-drift)
 *     Si la magnitud del delta lleva ≥5 frames por debajo del umbral de
 *     quietud, el estabilizador congela la salida en (0,0) y aplica
 *     fricción creciente para frenar la deriva residual.
 *     Se desactiva inmediatamente al detectar movimiento intencional.
 */
object AimStabilizer {

    // ────────────────────────────────────────────────────────────────────
    // Parámetros base — A06
    // ────────────────────────────────────────────────────────────────────
    private const val DEAD_ZONE_BASE    = 0.30f  // dp de zona muerta en reposo
    private const val DEAD_ZONE_LOADED  = 0.55f  // dp bajo carga alta (score < 40)
    private const val ALPHA_MIN         = 0.20f
    private const val ALPHA_MAX         = 0.80f
    private const val VELOCITY_MIX      = 0.30f  // peso de la velocidad previa en la predicción
    private const val JITTER_THRESHOLD  = 3      // alternaciones de signo para activar calma
    private const val JITTER_ALPHA_DEC  = 0.08f  // reducción de alpha en modo calma
    private const val JITTER_TICKS      = 8      // ciclos de modo calma
    private const val STILL_THRESHOLD   = 0.18f  // magnitud por debajo de la cual = quieto
    private const val STILL_FRAMES_LOCK = 5      // frames quieto antes de aim lock
    private const val LOCK_FRICTION     = 0.85f  // coeficiente de fricción durante lock

    // ────────────────────────────────────────────────────────────────────
    // Estado interno
    // ────────────────────────────────────────────────────────────────────
    private var smoothedX   = 0f
    private var smoothedY   = 0f
    private var velocityX   = 0f
    private var velocityY   = 0f
    private var alpha       = 0.55f
    private var systemScore = 55

    // Jitter
    private var prevSignX    = 0f
    private var prevSignY    = 0f
    private var jitterCntX   = 0
    private var jitterCntY   = 0
    private var jitterActive = false
    private var jitterTicks  = 0

    // Aim lock suave
    private var stillFrames  = 0
    private var lockActive   = false

    // ────────────────────────────────────────────────────────────────────
    // API pública
    // ────────────────────────────────────────────────────────────────────

    /**
     * Procesa el delta crudo de cada frame y devuelve el delta estabilizado.
     * Llamar desde el InputDispatcher / GestureListener / GyroHandler.
     */
    fun smooth(rawDx: Float, rawDy: Float): Pair<Float, Float> {
        val dz       = dynamicDeadZone()
        val filtX    = applyDeadZone(rawDx, dz)
        val filtY    = applyDeadZone(rawDy, dz)
        val mag      = hypot(filtX, filtY)

        // ── Aim lock suave ──────────────────────────────────────────────
        if (mag < STILL_THRESHOLD) {
            stillFrames++
            if (stillFrames >= STILL_FRAMES_LOCK) {
                lockActive  = true
                smoothedX  *= LOCK_FRICTION
                smoothedY  *= LOCK_FRICTION
                velocityX  *= LOCK_FRICTION
                velocityY  *= LOCK_FRICTION
                return Pair(smoothedX, smoothedY)
            }
        } else {
            stillFrames = 0
            lockActive  = false
        }

        // ── Predicción de velocidad ─────────────────────────────────────
        val predX = filtX + VELOCITY_MIX * velocityX
        val predY = filtY + VELOCITY_MIX * velocityY

        // ── EMA con alpha adaptativo ────────────────────────────────────
        val eff = effectiveAlpha(rawDx, rawDy)
        smoothedX = eff * predX + (1f - eff) * smoothedX
        smoothedY = eff * predY + (1f - eff) * smoothedY

        // Actualizar velocidad estimada
        velocityX = smoothedX - predX * (1f - eff)
        velocityY = smoothedY - predY * (1f - eff)

        return Pair(smoothedX, smoothedY)
    }

    /**
     * Recibe el score del AdaptiveEngine (0-100).
     * Ajusta alpha y el umbral de zona muerta en tiempo real.
     */
    fun updateFromScore(score: Int) {
        systemScore = score.coerceIn(0, 100)
        val t = systemScore / 100f
        alpha = ALPHA_MIN + t * (ALPHA_MAX - ALPHA_MIN)
    }

    /** Reinicia todo el estado al iniciar/cerrar una sesión de juego. */
    fun reset() {
        smoothedX = 0f;  smoothedY = 0f
        velocityX = 0f;  velocityY = 0f
        prevSignX = 0f;  prevSignY = 0f
        jitterCntX = 0;  jitterCntY = 0
        jitterActive = false; jitterTicks = 0
        stillFrames = 0; lockActive = false
    }

    /** True si el aim lock suave está activo en este momento. */
    fun isLocked(): Boolean = lockActive

    // ────────────────────────────────────────────────────────────────────
    // Privados
    // ────────────────────────────────────────────────────────────────────

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
