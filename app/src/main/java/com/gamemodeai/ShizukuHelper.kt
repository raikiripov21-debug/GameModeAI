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

            // ══════════════════════════════════════════════════════════════════
            // 1. ANIMACIONES — eliminar toda latencia visual
            // ══════════════════════════════════════════════════════════════════
            "settings put global window_animation_scale 0",
            "settings put global transition_animation_scale 0",
            "settings put global animator_duration_scale 0",

            // ══════════════════════════════════════════════════════════════════
            // 2. RENDIMIENTO — CPU al máximo
            // ══════════════════════════════════════════════════════════════════
            "cmd power set-mode 2",
            // Samsung: respuesta de CPU mejorada (OneUI)
            "settings put global sem_enhanced_cpu_responsiveness 1",

            // ══════════════════════════════════════════════════════════════════
            // 3. AIM / TÁCTIL — mira suave sin micro-saltos
            // ══════════════════════════════════════════════════════════════════
            "settings put system haptic_feedback_enabled 0",
            "settings put system sound_effects_enabled 0",
            "settings put system pointer_speed 0",
            "settings put system pointer_location 0",
            "settings put system show_touches 0",
            // Sensibilidad táctil alta Samsung → menor lag dedo-pantalla
            "settings put system touch_sensitivity_mode 1",
            // Cero rebotes y eventos parásitos (causa #1 de micro-saltos)
            "settings put system touch_blocking_period 0",
            "settings put system touch_debounce_period 0",
            "settings put system touch_event_blocking_period 0",
            // Exploración táctil de accesibilidad → OFF (roba eventos de touch)
            "settings put secure touch_exploration_enabled 0",
            "settings put secure accessibility_display_magnification_enabled 0",
            // Corrección de escritura → OFF (roba frames de input)
            "settings put secure spell_checker_enabled 0",

            // ══════════════════════════════════════════════════════════════════
            // 4. PANTALLA — frecuencia FIJA 60 Hz (sin jitter por cambio de Hz)
            // ══════════════════════════════════════════════════════════════════
            "settings put system peak_refresh_rate 60",
            "settings put system min_refresh_rate 60",
            // Bloquear rotación automática (no interrumpe con sensores)
            "settings put system accelerometer_rotation 0",
            // Brillo manual (sin que el sensor robe CPU)
            "settings put system screen_brightness_mode 0",
            // Pantalla encendida por 30 min
            "settings put system screen_off_timeout 1800000",
            // Always-On Display → OFF (roba RAM y GPU en algunos momentos)
            "settings put global always_on_display_enabled 0",

            // ══════════════════════════════════════════════════════════════════
            // 5. FALLOS SAMSUNG A06 — interrupciones durante el juego
            // ══════════════════════════════════════════════════════════════════

            // Panel lateral Samsung (se activa al deslizar el borde → pierde control)
            "settings put system edge_panels_enabled 0",
            "settings put system edge_typing_enabled 0",
            "settings put system edge_lighting_enabled 0",

            // Modo de una mano (se activa solo con deslizamiento desde el borde inferior)
            "settings put system one_handed_mode_enabled 0",
            "settings put system one_handed_mode_trigger 0",

            // Doble toque al botón lateral → abre cámara (interrumpe mientras juegas)
            "settings put secure camera_double_tap_power_key_gesture_disabled 1",

            // Mantener pulsado → asistente de voz (desactivar atajo)
            "settings put secure assistant_gesture_triggered 1",
            "settings put secure voice_interaction_service disabled",

            // Navegación por gestos → cambiar a 3 botones clásicos
            // (los gestos de deslizar desde los bordes chocan con los controles de FF)
            "settings put secure navigation_mode 0",

            // Modo inmersivo para Free Fire (oculta barra de nav permanentemente)
            "settings put global policy_control immersive.full=com.dts.freefireth,com.dts.freefiremaxob",

            // Confirmación de inmersivo → siempre aceptada (sin el cartel de "desliza para mostrar")
            "settings put secure immersive_mode_confirmations confirmed",

            // Game Launcher de Samsung → desactivar (puede pausar o capturar el juego)
            "settings put global game_home_enable 0",
            "settings put global game_tools_enable 0",
            "settings put global game_automatic_fps 0",

            // Prevención de toque accidental Samsung (bloquea inputs legítimos dentro del juego)
            "settings put system prevent_accidental_touch 0",

            // Teclado emergente / autocompletado (roba CPU cuando hay campo de texto cerca)
            "settings put secure show_ime_with_hard_keyboard 0",

            // Doze/modo reposo del sistema durante el juego → OFF
            "settings put global device_idle_constants min_time_to_alarm=3600000",

            // Watchdog de WiFi (puede cortar la red 1-2 s para "verificar" la señal)
            "settings put global wifi_watchdog_on 0",

            // Escaneo de redes en segundo plano
            "settings put global wifi_scan_always_enabled 0",
            "settings put global wifi_scan_interval_ms 0",

            // ══════════════════════════════════════════════════════════════════
            // 6. GPU — renderizado directo
            // ══════════════════════════════════════════════════════════════════
            "settings put global gpu_debug_layers_enable 0",
            "settings put global enable_gpu_debug_layers 0",

            // ══════════════════════════════════════════════════════════════════
            // 7. PRIORIDAD DE PROCESO — Free Fire al frente del scheduler
            // ══════════════════════════════════════════════════════════════════
            "cmd activity set-standby-bucket com.dts.freefireth active",
            "cmd activity set-standby-bucket com.dts.freefiremaxob active",

            // ══════════════════════════════════════════════════════════════════
            // 8. MEMORIA — limpiar y congelar procesos de fondo
            // ══════════════════════════════════════════════════════════════════
            "am kill-all",
            "settings put global always_finish_activities 1",
            "settings put global background_process_limit 1",
            "settings put global cached_apps_freezer enabled",

            // ══════════════════════════════════════════════════════════════════
            // 9. RED — estable sin cortes
            // ══════════════════════════════════════════════════════════════════
            "settings put global wifi_sleep_policy 2",
            "settings put global mobile_data_always_on 1",
            "settings put global captive_portal_detection_enabled 0",
            "settings put global nsd_on 0",
            "settings put global aggressive_wifi_to_mobile_handover 1",

            // ══════════════════════════════════════════════════════════════════
            // 10. SINCRONIZACIÓN / GPS / NOTIFICACIONES — cero interrupciones
            // ══════════════════════════════════════════════════════════════════
            "settings put global sync_disabled 1",
            "settings put global auto_time 0",
            "settings put secure location_mode 0",
            "settings put global heads_up_notifications_enabled 0",
            "settings put system notification_bubbles 0",

            // ══════════════════════════════════════════════════════════════════
            // 11. ALMACENAMIENTO y SEGUNDA LIMPIEZA DE RAM
            // ══════════════════════════════════════════════════════════════════
            "settings put global fstrim_mandatory_interval 86400000",
            "am kill-all"
        ))
    }

    suspend fun disableGameMode(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(

            // Restaurar animaciones
            "settings put global window_animation_scale 1",
            "settings put global transition_animation_scale 1",
            "settings put global animator_duration_scale 1",

            // Restaurar energía
            "cmd power set-mode 0",
            "settings put global sem_enhanced_cpu_responsiveness 0",

            // Restaurar táctil
            "settings put system haptic_feedback_enabled 1",
            "settings put system sound_effects_enabled 1",
            "settings put system touch_sensitivity_mode 0",
            "settings put system touch_blocking_period 100",
            "settings put system touch_debounce_period 50",
            "settings put system touch_event_blocking_period 100",
            "settings put secure touch_exploration_enabled 0",
            "settings put secure spell_checker_enabled 1",

            // Restaurar pantalla
            "settings put system peak_refresh_rate 90",
            "settings put system min_refresh_rate 60",
            "settings put system accelerometer_rotation 1",
            "settings put system screen_brightness_mode 1",
            "settings put system screen_off_timeout 60000",
            "settings put global always_on_display_enabled 1",

            // Restaurar fallos Samsung
            "settings put system edge_panels_enabled 1",
            "settings put system one_handed_mode_enabled 0",
            "settings put secure camera_double_tap_power_key_gesture_disabled 0",
            "settings put secure navigation_mode 2",
            "settings put global policy_control null",
            "settings put global game_home_enable 1",
            "settings put global game_tools_enable 1",
            "settings put system prevent_accidental_touch 1",
            "settings put global wifi_watchdog_on 1",
            "settings put global wifi_scan_always_enabled 1",
            "settings put system notification_bubbles 1",

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
            "settings put global captive_portal_detection_enabled 1",
            "settings put global nsd_on 1",
            "settings put global aggressive_wifi_to_mobile_handover 0",
            "settings put global mobile_data_always_on 0",

            // Restaurar sincronización / GPS
            "settings put global sync_disabled 0",
            "settings put global auto_time 1",
            "settings put secure location_mode 3",

            // Restaurar notificaciones
            "settings put global heads_up_notifications_enabled 1",

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
