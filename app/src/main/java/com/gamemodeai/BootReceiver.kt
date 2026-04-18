package com.gamemodeai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Se activa tras reiniciar el teléfono o tras actualizar la app.
 * Si el modo juego estaba activo antes del reinicio, lo reactiva automáticamente.
 * Limpia el estado de sesión anterior para evitar valores corruptos.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // Doble null-check: contexto e intent nunca deben ser nulos aquí
        if (context == null || intent == null) return

        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED
        )

        if (intent.action !in validActions) return

        // Solo reactivar si el usuario lo había activado previamente
        if (!Prefs.isActive(context)) return

        // Limpiar estado de sesión anterior para evitar Fase 2 inmediata
        // o countdown incorrecto tras el reinicio
        Prefs.clearLongGame(context)

        // Pequeño delay via goAsync para dar tiempo al sistema de arrancar
        val pendingResult = goAsync()
        try {
            GameService.start(context)
        } catch (_: Exception) {
            // Si el servicio no puede arrancar, no crash
        } finally {
            pendingResult.finish()
        }
    }
}
