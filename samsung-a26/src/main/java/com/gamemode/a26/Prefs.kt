package com.gamemode.a26

import android.content.Context

object Prefs {
    private const val NAME = "gm_a26_prefs"
    private const val KEY_ACTIVE = "active"
    private const val KEY_SESSION_START = "session_start_ms"

    fun isActive(ctx: Context) = ctx.getSharedPreferences(NAME, 0).getBoolean(KEY_ACTIVE, false)
    fun setActive(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(NAME, 0).edit().putBoolean(KEY_ACTIVE, v).apply()
        if (!v) ctx.getSharedPreferences(NAME, 0).edit().remove(KEY_SESSION_START).apply()
    }
    fun startSession(ctx: Context) {
        ctx.getSharedPreferences(NAME, 0).edit()
            .putLong(KEY_SESSION_START, System.currentTimeMillis()).apply()
    }
    fun getSessionStartMs(ctx: Context) = ctx.getSharedPreferences(NAME, 0).getLong(KEY_SESSION_START, 0L)
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(NAME, 0)
}
