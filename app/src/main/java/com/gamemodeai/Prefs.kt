package com.gamemodeai

import android.content.Context
import android.content.SharedPreferences

/**
 * Almacenamiento ligero del estado de GameModeAI.
 * Usa apply() en lugar de commit() para escrituras asíncronas (no bloquea el hilo UI).
 */
object Prefs {
    private const val PREFS_NAME = "game_mode_prefs"
    private const val KEY_ACTIVE        = "is_active"
    private const val KEY_LONG_START_MS = "long_game_start_ms"
    private const val KEY_PHASE2_ACTIVE = "phase2_active"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ACTIVE, false)

    fun setActive(context: Context, active: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, active)
            .apply()
        if (!active) clearLongGame(context)
    }

    fun startLongGame(context: Context) {
        prefs(context).edit()
            .putLong(KEY_LONG_START_MS, System.currentTimeMillis())
            .putBoolean(KEY_PHASE2_ACTIVE, false)
            .apply()
    }

    fun getLongGameStartMs(context: Context): Long =
        prefs(context).getLong(KEY_LONG_START_MS, 0L)

    fun setPhase2Active(context: Context) =
        prefs(context).edit()
            .putBoolean(KEY_PHASE2_ACTIVE, true)
            .apply()

    fun isPhase2Active(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PHASE2_ACTIVE, false)

    fun clearLongGame(context: Context) =
        prefs(context).edit()
            .remove(KEY_LONG_START_MS)
            .remove(KEY_PHASE2_ACTIVE)
            .apply()
}
