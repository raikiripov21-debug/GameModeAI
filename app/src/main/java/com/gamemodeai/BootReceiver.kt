package com.gamemodeai

  import android.content.BroadcastReceiver
  import android.content.Context
  import android.content.Intent

  /**
   * Se activa tras reiniciar el teléfono.
   * Si el modo juego estaba activo antes del reinicio, lo reactiva automáticamente.
   *
   * IMPORTANTE: limpia el estado de sesión anterior (start_ms, phase2) para que
   * el contador de Fase 2 empiece desde cero y no herede valores corruptos del
   * reinicio anterior.
   */
  class BootReceiver : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
          if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
              intent.action == "android.intent.action.QUICKBOOT_POWERON"
          ) {
              if (Prefs.isActive(context)) {
                  // Limpiar estado de sesión stale del reinicio anterior
                  // (evita que Phase 2 se active de inmediato o que el countdown sea incorrecto)
                  Prefs.clearLongGame(context)
                  GameService.start(context)
              }
          }
      }
  }
  