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
            if (Shizuku.isPreV11()) false
            else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.w(TAG, "Error checking permission: ${e.message}")
            false
        }
    }

    fun requestPermission() {
        try {
            if (!Shizuku.isPreV11()) Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting permission: ${e.message}")
        }
    }

    suspend fun enableGameMode(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(

            // ── 1. ANIMACIONES: eliminar toda latencia visual ─────────────────
            "settings put global window_animation_scale 0",
            "settings put global transition_animation_scale 0",
            "settings put global animator_duration_scale 0",

            // ── 2. MODO RENDIMIENTO: forzar CPU en máximo ─────────────────────
            "cmd power set-mode 2",

            // ── 3. TÁCTIL: máxima precisión y respuesta del aim ───────────────
            "settings put system haptic_feedback_enabled 0",
            "settings put system pointer_speed 0",
            "settings put system pointer_location 0",
            "settings put system show_touches 0",
            // Sensibilidad táctil alta (Samsung) — menor lag entre dedo y pantalla
            "settings put system touch_sensitivity_mode 1",
            // Desactivar zoom de accesibilidad que puede interferir con el touch
            "settings put secure accessibility_display_magnification_enabled 0",

            // ── 4. MEMORIA: liberar y congelar todo lo innecesario ────────────
            "am kill-all",
            "settings put global always_finish_activities 1",
            "settings put global background_process_limit 1",
            "settings put global cached_apps_freezer enabled",

            // ── 5. RED: conexión estable sin interrupciones ───────────────────
            "settings put global wifi_sleep_policy 2",
            "settings put global mobile_data_always_on 1",
            "settings put global wifi_scan_always_enabled 0",
            "settings put global captive_portal_detection_enabled 0",
            "settings put global nsd_on 0",
            "settings put global aggressive_wifi_to_mobile_handover 1",

            // ── 6. SINCRONIZACIÓN: pausar todo proceso en segundo plano ───────
            "settings put global sync_disabled 1",
            "settings put global auto_time 0",

            // ── 7. GPS / LOCALIZACIÓN: apagar para liberar CPU ────────────────
            "settings put secure location_mode 0",

            // ── 8. NOTIFICACIONES: cero interrupciones ────────────────────────
            "settings put global heads_up_notifications_enabled 0",
            "settings put system sound_effects_enabled 0",

            // ── 9. ALMACENAMIENTO: aplazar mantenimiento del sistema ──────────
            "settings put global fstrim_mandatory_interval 86400000",

            // ── 10. SEGUNDA LIMPIEZA DE RAM tras aplicar todo ─────────────────
            "am kill-all"
        ))
    }

    suspend fun disableGameMode(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(

            // Restaurar animaciones
            "settings put global window_animation_scale 1",
            "settings put global transition_animation_scale 1",
            "settings put global animator_duration_scale 1",

            // Restaurar modo de energía normal
            "cmd power set-mode 0",

            // Restaurar táctil
            "settings put system haptic_feedback_enabled 1",
            "settings put system touch_sensitivity_mode 0",

            // Restaurar memoria
            "settings put global always_finish_activities 0",
            "settings put global background_process_limit -1",
            "settings put global cached_apps_freezer disabled",

            // Restaurar red
            "settings put global wifi_scan_always_enabled 1",
            "settings put global captive_portal_detection_enabled 1",
            "settings put global nsd_on 1",
            "settings put global aggressive_wifi_to_mobile_handover 0",
            "settings put global mobile_data_always_on 0",

            // Restaurar sincronización
            "settings put global sync_disabled 0",
            "settings put global auto_time 1",

            // Restaurar GPS
            "settings put secure location_mode 3",

            // Restaurar notificaciones
            "settings put global heads_up_notifications_enabled 1",
            "settings put system sound_effects_enabled 1",

            // Restaurar almacenamiento
            "settings put global fstrim_mandatory_interval 3600000"
        ))
    }

    private fun runCommands(commands: List<String>): Boolean {
        if (!isShizukuAvailable()) { Log.w(TAG, "Shizuku not available"); return false }
        if (!hasPermission()) { requestPermission(); return false }

        var ok = true
        for (cmd in commands) {
            try {
                val p = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
                val exit = p.waitFor()
                p.destroy()
                if (exit != 0) Log.w(TAG, "exit $exit: $cmd")
            } catch (e: Exception) {
                Log.e(TAG, "Error [$cmd]: ${e.message}")
                ok = false
            }
        }
        return ok
    }
}
