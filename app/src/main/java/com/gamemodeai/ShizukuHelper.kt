package com.gamemodeai

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    private const val REQUEST_CODE = 1001

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku not available: ${e.message}")
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            if (Shizuku.isPreV11()) {
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Shizuku permission: ${e.message}")
            false
        }
    }

    fun requestPermission() {
        try {
            if (!Shizuku.isPreV11()) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting Shizuku permission: ${e.message}")
        }
    }

    suspend fun enableGameMode(): Boolean = withContext(Dispatchers.IO) {
        runCommands(
            listOf(
                // ── Animaciones ──────────────────────────────────────────────
                "settings put global window_animation_scale 0",
                "settings put global transition_animation_scale 0",
                "settings put global animator_duration_scale 0",

                // ── Respuesta táctil (aim más estable y preciso) ──────────────
                "settings put system haptic_feedback_enabled 0",
                "settings put system pointer_speed 0",
                "settings put system pointer_location 0",

                // ── Sin interrupciones durante el juego ───────────────────────
                "settings put global heads_up_notifications_enabled 0",
                "settings put system sound_effects_enabled 0",

                // ── Red estable (menos lag de conexión) ───────────────────────
                "settings put global wifi_sleep_policy 2",
                "settings put global mobile_data_always_on 1",

                // ── Liberar RAM: matar procesos en caché ──────────────────────
                "am kill-all",

                // ── Liberar RAM extra: destruir actividades inactivas ─────────
                "settings put global always_finish_activities 1",

                // ── Detener sincronización en segundo plano ───────────────────
                "settings put global sync_disabled 1",

                // ── Limitar procesos en segundo plano a 1 (solo Free Fire) ────
                "settings put global background_process_limit 1",

                // ── Renderizado por GPU ───────────────────────────────────────
                "settings put global gpu_debug_layers_gles \"\"",

                // ── Segunda limpieza de RAM tras aplicar todo ─────────────────
                "am kill-all"
            )
        )
    }

    suspend fun disableGameMode(): Boolean = withContext(Dispatchers.IO) {
        runCommands(
            listOf(
                // Restaurar animaciones
                "settings put global window_animation_scale 1",
                "settings put global transition_animation_scale 1",
                "settings put global animator_duration_scale 1",

                // Restaurar táctil
                "settings put system haptic_feedback_enabled 1",

                // Restaurar notificaciones
                "settings put global heads_up_notifications_enabled 1",
                "settings put system sound_effects_enabled 1",

                // Restaurar actividades
                "settings put global always_finish_activities 0",

                // Restaurar sincronización
                "settings put global sync_disabled 0",

                // Restaurar procesos en segundo plano (por defecto Android = -1)
                "settings put global background_process_limit -1",

                // Restaurar red
                "settings put global mobile_data_always_on 0"
            )
        )
    }

    private fun runCommands(commands: List<String>): Boolean {
        if (!isShizukuAvailable()) {
            Log.w(TAG, "Shizuku is not available")
            return false
        }
        if (!hasPermission()) {
            Log.w(TAG, "No Shizuku permission")
            requestPermission()
            return false
        }
        var allSuccess = true
        for (cmd in commands) {
            try {
                val process = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
                val exitCode = process.waitFor()
                process.destroy()
                if (exitCode != 0) {
                    Log.w(TAG, "Command returned $exitCode: $cmd")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error running command [$cmd]: ${e.message}")
                allSuccess = false
            }
        }
        return allSuccess
    }
}
