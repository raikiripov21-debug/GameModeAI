package com.gamemodeai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Se activa tras reiniciar el teléfono.
 * Si el modo juego estaba activo antes del reinicio, lo reactiva automáticamente.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            if (Prefs.isActive(context)) {
                GameService.start(context)
            }
        }
    }
}
