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

            // ── 3. AIM / TÁCTIL: máxima precisión para la mira ───────────────
            // Eliminar vibración que consume CPU y distrae el dedo
            "settings put system haptic_feedback_enabled 0",
            "settings put system sound_effects_enabled 0",
            // Velocidad del puntero neutra (no hay aceleración artificial)
            "settings put system pointer_speed 0",
            // Desactivar overlays que interfieren con el touch
            "settings put system pointer_location 0",
            "settings put system show_touches 0",
            // Sensibilidad táctil alta Samsung → menor lag entre dedo y pantalla
            "settings put system touch_sensitivity_mode 1",
            // Bloqueo de eventos parásitos (rebotes táctiles = micro-saltos)
            "settings put system touch_blocking_period 0",
            "settings put system touch_debounce_period 0",
            "settings put system touch_event_blocking_period 0",
            // Desactivar zoom accesibilidad (interfiere con coordenadas touch)
            "settings put secure accessibility_display_magnification_enabled 0",
            // Desactivar corrección de escritura que puede robar frames de touch
            "settings put secure spell_checker_enabled 0",

            // ── 4. PANTALLA: frecuencia fija → cero micro-saltos por Hz ──────
            // Forzar 60 Hz estable en lugar de adaptativo (60↔90 causa jitter)
            "settings put system peak_refresh_rate 60",
            "settings put system min_refresh_rate 60",
            // Desactivar brillo adaptativo (no roba CPU durante el juego)
            "settings put system screen_brightness_mode 0",
            // Tiempo de pantalla apagada largo para que no interrumpa
            "settings put system screen_off_timeout 1800000",

            // ── 5. GPU: renderizado directo y sin overhead de debug ───────────
            "settings put global gpu_debug_layers_enable 0",
            "settings put global enable_gpu_debug_layers 0",

            // ── 6. PRIORIDAD DE PROCESO: Free Fire al frente ──────────────────
            // Marcar Free Fire como app activa en el scheduler
            "cmd activity set-standby-bucket com.dts.freefireth active",
            "cmd activity set-standby-bucket com.dts.freefiremaxob active",

            // ── 7. MEMORIA: liberar y congelar todo lo innecesario ────────────
            "am kill-all",
            "settings put global always_finish_activities 1",
            "settings put global background_process_limit 1",
            "settings put global cached_apps_freezer enabled",

            // ── 8. RED: conexión estable sin interrupciones ───────────────────
            "settings put global wifi_sleep_policy 2",
            "settings put global mobile_data_always_on 1",
            "settings put global wifi_scan_always_enabled 0",
            "settings put global captive_portal_detection_enabled 0",
            "settings put global nsd_on 0",
            "settings put global aggressive_wifi_to_mobile_handover 1",

            // ── 9. SINCRONIZACIÓN: pausar todo proceso en segundo plano ───────
            "settings put global sync_disabled 1",
            "settings put global auto_time 0",

            // ── 10. GPS / LOCALIZACIÓN: apagar para liberar CPU ───────────────
            "settings put secure location_mode 0",

            // ── 11. NOTIFICACIONES: cero interrupciones ───────────────────────
            "settings put global heads_up_notifications_enabled 0",

            // ── 12. ALMACENAMIENTO: aplazar mantenimiento del sistema ─────────
            "settings put global fstrim_mandatory_interval 86400000",

            // ── 13. SEGUNDA LIMPIEZA DE RAM tras aplicar todo ─────────────────
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

            // Restaurar táctil / aim
            "settings put system haptic_feedback_enabled 1",
            "settings put system touch_sensitivity_mode 0",
            "settings put system touch_blocking_period 100",
            "settings put system touch_debounce_period 50",
            "settings put system touch_event_blocking_period 100",
            "settings put secure spell_checker_enabled 1",

            // Restaurar pantalla
            "settings put system peak_refresh_rate 90",
            "settings put system min_refresh_rate 60",
            "settings put system screen_brightness_mode 1",
            "settings put system screen_off_timeout 60000",

            // Restaurar GPU
            "settings put global gpu_debug_layers_enable 0",

            // Restaurar prioridad de proceso
            "cmd activity set-standby-bucket com.dts.freefireth working_set",
            "cmd activity set-standby-bucket com.dts.freefiremaxob working_set",

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
