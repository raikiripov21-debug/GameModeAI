package com.gamemodeai

import android.util.Log

/**
 * ActionExecutor — Despachador seguro de ações para Samsung Galaxy A06
 *
 * Garante que NENHUM comando proibido seja executado.
 * Camada de segurança adicional sobre o ShizukuHelper.
 *
 * PROIBIDO (bloqueado aqui):
 * - am kill-all
 * - am force-stop
 * - qualquer comando que force-stop aplicativos
 *
 * PERMITIDO:
 * - settings put (system/global/secure)
 * - cmd activity set-standby-bucket
 * - cmd package compile
 * - cmd power set-mode
 */
object ActionExecutor {

    private const val TAG = "ActionExecutor"

    // Lista de termos proibidos — qualquer comando contendo estes é bloqueado
    private val FORBIDDEN_PATTERNS = listOf(
        "am kill-all",
        "am force-stop",
        "am kill ",
        "killall",
        "pkill"
    )

    /**
     * Executa uma ação segura — verifica contra lista de proibições.
     * Retorna false se o comando for proibido ou se Shizuku não estiver disponível.
     */
    suspend fun safeRun(command: String): Boolean {
        if (isForbidden(command)) {
            Log.e(TAG, "BLOQUEADO: comando proibido detectado: '$command'")
            return false
        }
        return ShizukuHelper.run(command)
    }

    /** Verifica se o comando contém termos proibidos. */
    fun isForbidden(command: String): Boolean {
        val lower = command.lowercase()
        return FORBIDDEN_PATTERNS.any { pattern -> lower.contains(pattern) }
    }

    /**
     * Executa manutenção leve segura — reconfirma settings críticos.
     * Anti-spam herdado do ShizukuHelper (11s mínimo).
     */
    suspend fun runLightMaintenance(): Boolean {
        return ShizukuHelper.applyMaintenanceMode()
    }

    /**
     * Executa manutenção de Fase 2 — apenas configurações seguras.
     */
    suspend fun runPhase2Maintenance(): Boolean {
        return ShizukuHelper.applyLongGameMaintenance()
    }

    /**
     * Reduz brilho para controle de temperatura — ação de emergência térmica.
     * Único comando permitido quando temperatura > 40°C.
     */
    suspend fun reduceBrightnessForThermal(brightness: Int = 50): Boolean {
        val cmd = "settings put system screen_brightness ${brightness.coerceIn(30, 100)}"
        return ShizukuHelper.run(cmd)
    }

    /**
     * Prioriza o Free Fire no agendador de processos (seguro).
     */
    suspend fun prioritizeFreeFire(): Boolean {
        return ShizukuHelper.run(
            "cmd activity set-standby-bucket com.dts.freefireth active ; " +
            "cmd activity set-standby-bucket com.dts.freefiremaxob active"
        )
    }
}
