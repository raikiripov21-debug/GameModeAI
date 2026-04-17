package com.gamemodeai

import android.content.Context

object Prefs {
    private const val PREFS_NAME = "game_mode_prefs"
    private const val KEY_ACTIVE       = "is_active"
    private const val KEY_SENS_GENERAL = "sens_general"
    private const val KEY_SENS_RED_DOT = "sens_red_dot"
    private const val KEY_SENS_2X      = "sens_2x"
    private const val KEY_SENS_4X      = "sens_4x"
    private const val KEY_SENS_SNIPER  = "sens_sniper"

    fun isActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ACTIVE, false)

    fun setActive(context: Context, active: Boolean) =
        prefs(context).edit().putBoolean(KEY_ACTIVE, active).apply()

    fun getSensGeneral(context: Context): Int  = prefs(context).getInt(KEY_SENS_GENERAL, 90)
    fun getSensRedDot(context: Context): Int   = prefs(context).getInt(KEY_SENS_RED_DOT, 95)
    fun getSens2x(context: Context): Int       = prefs(context).getInt(KEY_SENS_2X, 70)
    fun getSens4x(context: Context): Int       = prefs(context).getInt(KEY_SENS_4X, 50)
    fun getSensSniper(context: Context): Int   = prefs(context).getInt(KEY_SENS_SNIPER, 25)

    fun setSensGeneral(context: Context, v: Int)  = prefs(context).edit().putInt(KEY_SENS_GENERAL, v).apply()
    fun setSensRedDot(context: Context, v: Int)   = prefs(context).edit().putInt(KEY_SENS_RED_DOT, v).apply()
    fun setSens2x(context: Context, v: Int)       = prefs(context).edit().putInt(KEY_SENS_2X, v).apply()
    fun setSens4x(context: Context, v: Int)       = prefs(context).edit().putInt(KEY_SENS_4X, v).apply()
    fun setSensSniper(context: Context, v: Int)   = prefs(context).edit().putInt(KEY_SENS_SNIPER, v).apply()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
