package com.gamemode.moto

import android.content.Context

object Prefs {
    private const val NAME = "gm_moto_prefs"
    fun isActive(ctx: Context) = ctx.getSharedPreferences(NAME, 0).getBoolean("active", false)
    fun setActive(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(NAME, 0).edit().putBoolean("active", v).apply()
    }
    fun startSession(ctx: Context) {
        ctx.getSharedPreferences(NAME, 0).edit()
            .putLong("session_ms", System.currentTimeMillis()).apply()
    }
    fun getSessionStartMs(ctx: Context) = ctx.getSharedPreferences(NAME, 0).getLong("session_ms", 0L)
}
