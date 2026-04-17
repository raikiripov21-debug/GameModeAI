package com.gamemodeai

import android.content.Context

object Prefs {
    private const val PREFS_NAME = "game_mode_prefs"
    private const val KEY_ACTIVE = "is_active"

    fun isActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ACTIVE, false)
    }

    fun setActive(context: Context, active: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ACTIVE, active).apply()
    }
}
