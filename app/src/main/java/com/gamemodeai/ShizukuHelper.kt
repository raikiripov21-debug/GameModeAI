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
        return try { Shizuku.pingBinder() }
        catch (e: Exception) { Log.w(TAG, "Shizuku not available: ${e.message}"); false }
    }

    fun hasPermission(): Boolean {
        return try {
            if (Shizuku.isPreV11()) false
            else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { Log.w(TAG, "Error checking permission: ${e.message}"); false }
    }

    fun requestPermission() {
        try { if (!Shizuku.isPreV11()) Shizuku.requestPermission(REQUEST_CODE) }
        catch (e: Exception) { Log.e(TAG, "Error requesting permission: ${e.message}") }
    }

    suspend fun enableGameMode(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(

            // ══════════════════════════════════════════════════════════════════
            // BLOQUE 1 · SAMSUNG GOS — La causa #1 de lag de mira en Samsung
            // Game Optimizing Service throttlea CPU/GPU sin aviso durante el juego.
            // Al forzar su parada la CPU/GPU trabaja al 100 % todo el tiempo.
            // NO toca archivos del juego — solo para un servicio del sistema.
            // ══════════════════════════════════════════════════════════════════
            "am force-stop com.samsung.android.game.gos",
            "am force-stop com.samsung.android.game.gamehome",
            "am force-stop com.samsung.android.game.gametools",
            "settings put global game_home_enable 0",
            "settings put global game_tools_enable 0",
            "settings put global game_automatic_fps 0",

            // ══════════════════════════════════════════════════════════════════
            // BLOQUE 2 · SERVICIOS SAMSUNG que compiten con el juego
            // Cámara, Bixby, Galaxy Store, DeX — consumen CPU/RAM en segundo plano
            // ══════════════════════════════════════════════════════════════════
            "am force-stop com.samsung.android.bixby.agent",
            "am force-stop com.samsung.android.bixby.service",
            "am force-stop com.samsung.android.app.camera",
            "am force-stop com.samsung.android.galaxyapps",
            "am force-stop com.sec.android.daemonapp",
            "am force-stop com.samsung.android.sm.policy",
            "am force-stop com.samsung.android.sm",
            "am force-stop com.samsung.android.lool",
            "am force-stop com.samsung.android.sdhms",
            "am force-stop com.samsung.android.app.updatecenter",

            // ══════════════════════════════════════════════════════════════════
            // BLOQUE 3 · RENDIMIENTO CPU — sin throttling ni ahorro de energía
            // ══════════════════════════════════════════════════════════════════
            "cmd power set-mode 2",
            "settings put global sem_enhanced_cpu_responsiveness 1",
            // Batería adaptativa OFF → no limita la CPU por "hábitos de uso"
            "settings put global adaptive_battery_management_enabled 0",
            // Ahorro de energía automático OFF
            "settings put global automatic_power_save_mode 0",
            "settings put global low_power 0",
            // Modo ahorro extremo OFF
            "settings put global extreme_power_save_mode 0",
            // Temperatura de CPU — aplazar gestión térmica agresiva
            "settings put global thermal_service_enable 0",

            // ══════════════════════════════════════════════════════════════════
            // BLOQUE 4 · PANTALLA — sin latencia visual ni cambios de Hz
            // ══════════════════════════════════════════════════════════════════
            "settings put global window_animation_scale 0",
            "settings put global transition_animation_scale 0",
            "settings put global animator_duration_scale 0",
            // Frecuencia fija: sin que el panel cambie de Hz durante el aim
            "settings put system peak_refresh_rate 60",
            "settings put system min_refresh_rate 60",
            // Vision Booster Samsung (procesa colores con GPU extra → latencia)
            "settings put secure Vision_Booster 0",
            // Modo Eye Comfort / filtro de luz azul → toca la GPU
            "settings put secure accessibility_display_daltonizer_enabled 0",
            // Brillo manual — el sensor de luz roba CPU micropulsos
            "settings put system screen_brightness_mode 0",
            // Pantalla siempre encendida OFF (roba GPU/RAM)
            "settings put global always_on_display_enabled 0",
            // Rotación auto OFF — el acelerómetro roba interrupciones del sistema
            "settings put system accelerometer_rotation 0",
            // Timeout largo para no perder el juego
            "settings put system screen_off_timeout 1800000",

            // ══════════════════════════════════════════════════════════════════
            // BLOQUE 5 · GPU — renderizado limpio y directo
            // ══════════════════════════════════════════════════════════════════
            "settings put global gpu_debug_layers_enable 0",
            "settings put global enable_gpu_debug_layers 0",
            // Skia renderer (más eficiente en Exynos/Helio que HWUI por defecto)
            "settings put global skia_use_vulkan_for_android 1",
            // Reducir overhead de Vulkan validation layers en release
            "settings put global enable_vulkan_validation_layers 0",

            // ══════════════════════════════════════════════════════════════════
            // BLOQUE 6 · AIM / TOUCH — cero rebotes, cero latencia, mira suave
            // ══════════════════════════════════════════════════════════════════
            "settings put system haptic_feedback_enabled 0",
            "settings put system sound_effects_enabled 0",
            "settings put system pointer_speed 0",
            "settings put system pointer_location 0",
            "settings put system show_touches 0",
            // Sensibilidad táctil alta Samsung → menor lag entre dedo y pantalla
            "settings put system touch_sensitivity_mode 1",
            // Eliminar TODOS los filtros de rebote táctil (causa #1 de micro-saltos)
            "settings put system touch_blocking_period 0",
            "settings put system touch_debounce_period 0",
            "settings put system touch_event_blocking_period 0",
            // Touch smoothing Samsung OFF → el sistema no interpola/suaviza el dedo
            // (la interpolación CAUSA micro-saltos, no los elimina)
            "settings put system touch_smooth_mode 0",
            // Ajuste automático de sensibilidad OFF (Samsung adapta sensibilidad al vuelo)
            "settings put system touch_sensitivity_auto_adjust 0",
            // Exploración táctil de accesibilidad → OFF
            "settings put secure touch_exploration_enabled 0",
            "settings put secure accessibility_display_magnification_enabled 0",
            // Corrector de escritura y autocorrección → OFF
            "settings put secure spell_checker_enabled 0",

            // ══════════════════════════════════════════════════════════════════
            // BLOQUE 7 · FALLOS SAMSUNG A06 — interrupciones durante la partida
            // ══════════════════════════════════════════════════════════════════
            "settings put system edge_panels_enabled 0",
            "settings put system edge_typing_enabled 0",
            "settings put system edge_lighting_enabled 0",
            "settings put system one_handed_mode_enabled 0",
            "settings put system one_handed_mode_trigger 0",
            "settings put secure camera_double_tap_power_key_gesture_disabled 1",
            "settings put secure assistant_gesture_triggered 1",
            "settings put secure navigation_mode 0",
            "settings put global policy_control immersive.full=com.dts.freefireth,com.dts.freefiremaxob",
            "settings put secure immersive_mode_confirmations confirmed",
            "settings put system prevent_accidental_touch 0",
            "settings put global device_idle_constants min_time_to_alarm=3600000",
            "settings put global wifi_watchdog_on 0",

            // ══════════════════════════════════════════════════════════════════
            // BLOQUE 8 · PROCESO — Free Fire al frente de TODO el scheduler
            // ══════════════════════════════════════════════════════════════════
            "cmd activity set-standby-bucket com.dts.freefireth active",
            "cmd activity set-standby-bucket com.dts.freefiremaxob active",

            // ══════════════════════════════════════════════════════════════════
            // BLOQUE 9 · MEMORIA — liberar y congelar todo lo innecesario
            // ══════════════════════════════════════════════════════════════════
            "am kill-all",
            "settings put global always_finish_activities 1",
            "settings put global background_process_limit 1",
            "settings put global cached_apps_freezer enabled",

            // ══════════════════════════════════════════════════════════════════
            // BLOQUE 10 · RED — conexión estable y sin interrupciones
            // ══════════════════════════════════════════════════════════════════
            "settings put global wifi_sleep_policy 2",
            "settings put global mobile_data_always_on 1",
            "settings put global wifi_scan_always_enabled 0",
            "settings put global wifi_scan_interval_ms 0",
            "settings put global captive_portal_detection_enabled 0",
            "settings put global nsd_on 0",
            "settings put global aggressive_wifi_to_mobile_handover 1",

            // ══════════════════════════════════════════════════════════════════
            // BLOQUE 11 · SINCRONIZACIÓN / GPS / NOTIFICACIONES
            // ══════════════════════════════════════════════════════════════════
            "settings put global sync_disabled 1",
            "settings put global auto_time 0",
            "settings put secure location_mode 0",
            "settings put global heads_up_notifications_enabled 0",
            "settings put system notification_bubbles 0",

            // ══════════════════════════════════════════════════════════════════
            // BLOQUE 12 · ALMACENAMIENTO + SEGUNDA LIMPIEZA DE RAM
            // ══════════════════════════════════════════════════════════════════
            "settings put global fstrim_mandatory_interval 86400000",
            "am kill-all"
        ))
    }

    suspend fun disableGameMode(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(
            // Animaciones
            "settings put global window_animation_scale 1",
            "settings put global transition_animation_scale 1",
            "settings put global animator_duration_scale 1",
            // Energía
            "cmd power set-mode 0",
            "settings put global sem_enhanced_cpu_responsiveness 0",
            "settings put global adaptive_battery_management_enabled 1",
            "settings put global automatic_power_save_mode 1",
            "settings put global thermal_service_enable 1",
            // Pantalla
            "settings put system peak_refresh_rate 60",
            "settings put system min_refresh_rate 60",
            "settings put secure Vision_Booster 1",
            "settings put system screen_brightness_mode 1",
            "settings put global always_on_display_enabled 1",
            "settings put system accelerometer_rotation 1",
            "settings put system screen_off_timeout 60000",
            // GPU
            "settings put global gpu_debug_layers_enable 0",
            "settings put global skia_use_vulkan_for_android 0",
            // Touch / Aim
            "settings put system haptic_feedback_enabled 1",
            "settings put system sound_effects_enabled 1",
            "settings put system touch_sensitivity_mode 0",
            "settings put system touch_blocking_period 100",
            "settings put system touch_debounce_period 50",
            "settings put system touch_event_blocking_period 100",
            "settings put system touch_smooth_mode 1",
            "settings put system touch_sensitivity_auto_adjust 1",
            "settings put secure touch_exploration_enabled 0",
            "settings put secure spell_checker_enabled 1",
            // Fallos A06
            "settings put system edge_panels_enabled 1",
            "settings put system one_handed_mode_enabled 0",
            "settings put secure camera_double_tap_power_key_gesture_disabled 0",
            "settings put secure navigation_mode 2",
            "settings put global policy_control null",
            "settings put global game_home_enable 1",
            "settings put global game_tools_enable 1",
            "settings put system prevent_accidental_touch 1",
            "settings put global wifi_watchdog_on 1",
            "settings put system notification_bubbles 1",
            // Proceso
            "cmd activity set-standby-bucket com.dts.freefireth working_set",
            "cmd activity set-standby-bucket com.dts.freefiremaxob working_set",
            // Memoria
            "settings put global always_finish_activities 0",
            "settings put global background_process_limit -1",
            "settings put global cached_apps_freezer disabled",
            // Red
            "settings put global wifi_scan_always_enabled 1",
            "settings put global captive_portal_detection_enabled 1",
            "settings put global nsd_on 1",
            "settings put global aggressive_wifi_to_mobile_handover 0",
            "settings put global mobile_data_always_on 0",
            // Sync / GPS
            "settings put global sync_disabled 0",
            "settings put global auto_time 1",
            "settings put secure location_mode 3",
            // Notificaciones
            "settings put global heads_up_notifications_enabled 1",
            // Almacenamiento
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
