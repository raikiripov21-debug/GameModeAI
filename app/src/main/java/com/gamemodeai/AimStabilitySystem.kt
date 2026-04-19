package com.gamemodeai

import android.util.Log
import java.util.LinkedList

/**
 * AimStabilitySystem — Sistema de estabilidade de mira para Samsung Galaxy A06
 *
 * Analisa padrões de uso da CPU para detectar condições que causam micro-lag
 * e instabilidade na mira durante o Free Fire.
 *
 * NÃO interfere na consistência do toque — apenas monitora e reporta.
 * NÃO executa comandos no sistema — apenas análise passiva.
 */
object AimStabilitySystem {

    private const val TAG = "AimStabilitySystem"

    // Janela deslizante de amostras de CPU para suavização
    private const val WINDOW_SIZE = 10
    private val cpuWindow = LinkedList<Int>()

    // Detecção de spikes
    private const val SPIKE_THRESHOLD_PCT = 25  // salto > 25% em 1 ciclo = spike
    private var prevCpuPct = 0
    private var spikeCount = 0
    private var lastSpikeMs = 0L

    // Detecção de micro-lag (sequência de CPU alta)
    private const val MICROLAG_THRESHOLD_PCT = 75   // CPU > 75% por vários ciclos
    private const val MICROLAG_CONSECUTIVE   = 3    // ciclos consecutivos
    private var consecutiveHighCount = 0

    // Modo de rastreamento — ativo quando Free Fire está em foco
    @Volatile var trackingMode: Boolean = false
        private set

    // Detecção de snap — CPU cai subitamente após pico (pode causar frame drop)
    private const val SNAP_THRESHOLD = -30  // queda > 30% num ciclo = snap
    private var snapCount = 0

    // ── Relatório de estado ──────────────────────────────────────────────────
    data class AimReport(
        val smoothedCpuPct  : Int     = 0,
        val spikeDetected   : Boolean = false,
        val microLagRisk    : Boolean = false,
        val snapDetected    : Boolean = false,
        val trackingActive  : Boolean = false,
        val recommendation  : String  = ""
    )

    /**
     * Processa nova amostra de CPU e retorna relatório de estabilidade.
     * Chamar a cada ciclo de monitoramento do GameService.
     */
    fun processSample(cpuPct: Int, freeFireRunning: Boolean): AimReport {
        trackingMode = freeFireRunning

        // ── Suavização por média deslizante ────────────────────────────────
        cpuWindow.addLast(cpuPct)
        if (cpuWindow.size > WINDOW_SIZE) cpuWindow.removeFirst()
        val smoothed = if (cpuWindow.isNotEmpty()) cpuWindow.average().toInt() else cpuPct

        // ── Detecção de spike ───────────────────────────────────────────────
        val delta = cpuPct - prevCpuPct
        val spikeDetected = delta > SPIKE_THRESHOLD_PCT
        if (spikeDetected) {
            spikeCount++
            lastSpikeMs = System.currentTimeMillis()
            Log.d(TAG, "CPU spike: +${delta}% (total: $spikeCount)")
        }

        // ── Detecção de snap ────────────────────────────────────────────────
        val snapDetected = delta < SNAP_THRESHOLD
        if (snapDetected) {
            snapCount++
            Log.d(TAG, "CPU snap: ${delta}% (total: $snapCount)")
        }

        prevCpuPct = cpuPct

        // ── Detecção de micro-lag ───────────────────────────────────────────
        if (cpuPct > MICROLAG_THRESHOLD_PCT) {
            consecutiveHighCount++
        } else {
            consecutiveHighCount = 0
        }
        val microLagRisk = consecutiveHighCount >= MICROLAG_CONSECUTIVE

        if (microLagRisk) {
            Log.w(TAG, "Micro-lag risk: CPU>${MICROLAG_THRESHOLD_PCT}% por ${consecutiveHighCount} ciclos")
        }

        // ── Recomendação ────────────────────────────────────────────────────
        val rec = buildRecommendation(smoothed, spikeDetected, microLagRisk, snapDetected)

        return AimReport(
            smoothedCpuPct  = smoothed,
            spikeDetected   = spikeDetected,
            microLagRisk    = microLagRisk,
            snapDetected    = snapDetected,
            trackingActive  = freeFireRunning,
            recommendation  = rec
        )
    }

    private fun buildRecommendation(
        smoothed: Int,
        spike: Boolean,
        microLag: Boolean,
        snap: Boolean
    ): String {
        return when {
            microLag && spike -> "CPU instável — mira pode ter saltos"
            microLag          -> "CPU alta por vários ciclos — reduzindo brilho ajuda"
            spike             -> "Pico de CPU detectado — JIT ou serviço Samsung"
            snap              -> "Queda brusca de CPU — frame drop possível"
            smoothed > 70     -> "CPU acima de 70% — temperatura monitorada"
            else              -> "CPU estável — mira estável"
        }
    }

    /** Reseta contadores ao iniciar nova sessão. */
    fun resetSession() {
        cpuWindow.clear()
        prevCpuPct    = 0
        spikeCount    = 0
        lastSpikeMs   = 0L
        snapCount     = 0
        consecutiveHighCount = 0
        trackingMode  = false
        Log.d(TAG, "Session reset")
    }

    /** Estatísticas da sessão para debug. */
    fun getSessionStats(): String =
        "Spikes: $spikeCount | Snaps: $snapCount | Tracking: $trackingMode"
}
