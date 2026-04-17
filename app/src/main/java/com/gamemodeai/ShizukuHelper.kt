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

    // ══════════════════════════════════════════════════════════════════════════
    // MODO JUEGO — Fase 1 (al activar)
    // ══════════════════════════════════════════════════════════════════════════
    suspend fun enableGameMode(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(

            // BLOQUE 1 · SAMSUNG GOS — parar el throttling de software Samsung
            "am force-stop com.samsung.android.game.gos",
            "am force-stop com.samsung.android.game.gamehome",
            "am force-stop com.samsung.android.game.gametools",
            "settings put global game_home_enable 0",
            "settings put global game_tools_enable 0",
            "settings put global game_automatic_fps 0",

            // BLOQUE 2 · SERVICIOS SAMSUNG — parar los que consumen CPU/RAM en idle
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
            "am force-stop com.samsung.android.app.samsungbluetoothdevicemanager",
            "am force-stop com.samsung.android.app.galaxyfinder",
            // Digital Wellbeing — consume CPU monitoreando el uso del teléfono
            "am force-stop com.samsung.android.forest",
            "am force-stop com.samsung.android.wellbeing",
            // Wi-Fi Aware — protocolo P2P de Samsung, inútil durante gaming
            "am force-stop com.samsung.android.aware.service",
            // Samsung Pay Frame — no se usa durante el juego
            "am force-stop com.samsung.android.spayfw",
            // Samsung Location sensor DB — actualiza DB de localización en background
            "am force-stop com.samsung.android.location.sensordatabaseprovider",

            // BLOQUE 3 · ANTI-CALENTAMIENTO FASE 1
            "settings put global nfc_on 0",
            "settings put system screen_brightness 115",
            "settings put system screen_brightness_mode 0",
            "settings put global wifi_scan_always_enabled 0",
            "settings put global wifi_scan_interval_ms 0",
            "settings put secure location_mode 0",
            "settings put global bluetooth_scan_mode 0",
            "settings put global always_on_display_enabled 0",
            "settings put system accelerometer_rotation 0",
            "settings put global sync_disabled 1",

            // BLOQUE 4 · RENDIMIENTO CPU (Exynos 850)
            "cmd power set-mode 2",
            "settings put global sem_enhanced_cpu_responsiveness 1",
            "settings put global adaptive_battery_management_enabled 0",
            "settings put global automatic_power_save_mode 0",
            "settings put global low_power 0",
            "settings put global extreme_power_save_mode 0",
            "settings put global app_standby_enabled 0",
            "settings put global enable_freeform_support 0",

            // BLOQUE 5 · PANTALLA — 60 Hz fijo (LCD A06 no tiene AMOLED)
            "settings put global window_animation_scale 0",
            "settings put global transition_animation_scale 0",
            "settings put global animator_duration_scale 0",
            "settings put system peak_refresh_rate 60",
            "settings put system min_refresh_rate 60",
            "settings put secure Vision_Booster 0",
            "settings put secure accessibility_display_daltonizer_enabled 0",
            "settings put system screen_off_timeout 1800000",

            // BLOQUE 6 · GPU — Vulkan optimizado para Exynos 850
            "settings put global gpu_debug_layers_enable 0",
            "settings put global enable_gpu_debug_layers 0",
            "settings put global skia_use_vulkan_for_android 1",
            "settings put global enable_vulkan_validation_layers 0",

            // BLOQUE 7 · AIM / TOUCH — precisión máxima del A06
            // Táctil sin filtros: desactiva todo suavizado y predicción para respuesta raw
            "settings put system haptic_feedback_enabled 0",
            "settings put system sound_effects_enabled 0",
            "settings put system pointer_speed 1",
            "settings put system pointer_location 0",
            "settings put system show_touches 0",
            "settings put system touch_sensitivity_mode 1",  // alta sensibilidad: LCD A06 sin protector
            "settings put system touch_blocking_period 0",   // sin bloqueo entre eventos táctiles
            "settings put system touch_debounce_period 0",   // sin debounce → cada toque cuenta
            "settings put system touch_event_blocking_period 0",
            "settings put system touch_smooth_mode 0",       // sin suavizado: posición exacta del dedo
            "settings put system touch_sensitivity_auto_adjust 0",
            "settings put secure touch_exploration_enabled 0",
            "settings put secure accessibility_display_magnification_enabled 0",
            "settings put secure spell_checker_enabled 0",
            // Tiempo de respuesta táctil: long-press y doble toque más rápidos
            "settings put system long_press_timeout 300",    // 400ms→300ms: gestos más reactivos
            "settings put system multi_press_timeout 300",   // doble toque reconocido 100ms antes
            // Asistente virtual: desactivar completamente para que no robe toques
            "settings put secure assist_gesture_enabled 0",
            "settings put secure assist_gesture_wake 0",
            "settings put secure double_tap_to_wake 0",      // evita despertar accidental al rozar pantalla

            // BLOQUE 8 · GESTOS SAMSUNG — eliminar toda interferencia durante el juego
            "settings put system edge_panels_enabled 0",
            "settings put system edge_typing_enabled 0",
            "settings put system edge_lighting_enabled 0",
            "settings put system one_handed_mode_enabled 0",
            "settings put system one_handed_mode_trigger 0",
            "settings put system swipe_up_to_switch_apps_enabled 0", // sin cambio accidental de app
            "settings put secure camera_double_tap_power_key_gesture_disabled 1",
            "settings put secure assistant_gesture_triggered 1",
            "settings put secure navigation_mode 0",
            "settings put global policy_control immersive.full=com.dts.freefireth,com.dts.freefiremaxob",
            "settings put secure immersive_mode_confirmations confirmed",
            "settings put system prevent_accidental_touch 0",
            "settings put system accidental_touch_protection 0",
            "settings put global device_idle_constants min_time_to_alarm=3600000",
            "settings put global wifi_watchdog_on 0",

            // BLOQUE 9 · SAMSUNG SECURITY ENGINE — reduce picos de CPU inesperados
            // El motor de seguridad de Samsung hace análisis en background y causa microfreezes
            "settings put global sem_mobile_security_engine 0",
            "settings put global game_mode_intervention 0",  // Samsung GOS no puede intervenir
            "settings put secure screensaver_enabled 0",     // sin Daydream que despierte la GPU
            "settings put global network_scoring_ui_enabled 0", // scoring de red: análisis innecesario

            // BLOQUE 10 · PROCESO — Free Fire con máxima prioridad
            "cmd activity set-standby-bucket com.dts.freefireth active",
            "cmd activity set-standby-bucket com.dts.freefiremaxob active",

            // BLOQUE 10 · MEMORIA — límite de fondo + freeze de apps inactivas
            "am kill-all",
            "settings put global always_finish_activities 0",
            "settings put global background_process_limit 2",
            "settings put global cached_apps_freezer enabled",

            // BLOQUE 11 · RED — WiFi estable sin interrupciones durante la partida
            "settings put global wifi_sleep_policy 2",
            "settings put global mobile_data_always_on 1",
            "settings put global captive_portal_detection_enabled 0",
            "settings put global nsd_on 0",
            "settings put global aggressive_wifi_to_mobile_handover 1",
            "settings put global wifi_connected_mac_randomization_enabled 0",
            "settings put global network_recommendations_enabled 0",
            "settings put global wifi_enhanced_auto_join 0",

            // BLOQUE 12 · LIMPIEZA FINAL
            "settings put global auto_time 0",
            "settings put global heads_up_notifications_enabled 0",
            "settings put system notification_bubbles 0",
            "settings put global fstrim_mandatory_interval 86400000",
            "am kill-all"
        ))
    }

    suspend fun applyMaintenanceMode(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(
            // Reiniciar servicios que se auto-levantan
            "am force-stop com.samsung.android.game.gos",
            "am force-stop com.samsung.android.game.gamehome",
            "am force-stop com.samsung.android.bixby.agent",
            "am force-stop com.samsung.android.bixby.service",
            "am force-stop com.samsung.android.forest",
            "am force-stop com.samsung.android.wellbeing",
            // Prioridad Free Fire
            "cmd activity set-standby-bucket com.dts.freefireth active",
            "cmd activity set-standby-bucket com.dts.freefiremaxob active",
            // Animaciones y red
            "settings put global window_animation_scale 0",
            "settings put global transition_animation_scale 0",
            "settings put global animator_duration_scale 0",
            "settings put global wifi_scan_always_enabled 0",
            "settings put global sync_disabled 1",
            "settings put global nfc_on 0",
            // AIM — re-confirmar cada 5 min (algunos servicios Samsung los revierten)
            "settings put system haptic_feedback_enabled 0",
            "settings put system sound_effects_enabled 0",
            "settings put system touch_sensitivity_mode 1",
            "settings put system touch_blocking_period 0",
            "settings put system touch_debounce_period 0",
            "settings put system touch_smooth_mode 0",
            "settings put system long_press_timeout 300",
            "settings put secure assist_gesture_enabled 0",
            "settings put global sem_mobile_security_engine 0",
            "settings put global game_mode_intervention 0",
            // Notificaciones y memoria
            "settings put global heads_up_notifications_enabled 0",
            "settings put global background_process_limit 2",
            "settings put global wifi_connected_mac_randomization_enabled 0",
            "settings put global network_recommendations_enabled 0"
        ))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FASE 2 — Partida larga (20 minutos después de activar)
    // Reducción térmica adicional para partidas largas en el A06.
    // ══════════════════════════════════════════════════════════════════════════
    suspend fun applyLongGameMode(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(

            // Brillo baja de 115 → 75 (29 %) — el panel LCD es la mayor fuente de calor
            "settings put system screen_brightness 75",

            // Matar todo lo que haya vuelto a levantarse en 20 min
            "am force-stop com.samsung.android.game.gos",
            "am force-stop com.samsung.android.bixby.agent",
            "am force-stop com.samsung.android.bixby.service",
            "am force-stop com.samsung.android.app.camera",
            "am force-stop com.samsung.android.galaxyapps",
            "am force-stop com.sec.android.daemonapp",
            "am force-stop com.samsung.android.sm",
            "am force-stop com.samsung.android.lool",
            "am force-stop com.samsung.android.app.galaxyfinder",

            // Reducir al mínimo estable los procesos de fondo
            "settings put global background_process_limit 1",

            // Segunda limpieza de RAM
            "am kill-all",

            // Confirmar configuraciones críticas aún activas
            "settings put global wifi_scan_always_enabled 0",
            "settings put global sync_disabled 1",
            "settings put secure location_mode 0",
            "settings put global nfc_on 0",
            "settings put global wifi_connected_mac_randomization_enabled 0",
            "settings put global network_recommendations_enabled 0",

            // Prioridad máxima de proceso para Free Fire
            "cmd activity set-standby-bucket com.dts.freefireth active",
            "cmd activity set-standby-bucket com.dts.freefiremaxob active",

            // Tercer kill-all para asegurar que nada compite con Free Fire
            "am kill-all"
        ))
    }

    suspend fun applyLongGameMaintenance(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(
            "settings put system screen_brightness 75",
            "am force-stop com.samsung.android.game.gos",
            "am force-stop com.samsung.android.bixby.agent",
            "am force-stop com.samsung.android.bixby.service",
            "cmd activity set-standby-bucket com.dts.freefireth active",
            "cmd activity set-standby-bucket com.dts.freefiremaxob active",
            "settings put global background_process_limit 1",
            "settings put global wifi_scan_always_enabled 0",
            "settings put global sync_disabled 1",
            "settings put secure location_mode 0",
            "settings put global nfc_on 0",
            "settings put system haptic_feedback_enabled 0",
            "settings put system sound_effects_enabled 0",
            "settings put global heads_up_notifications_enabled 0",
            "settings put global wifi_connected_mac_randomization_enabled 0",
            "settings put global network_recommendations_enabled 0"
        ))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DESACTIVAR MODO JUEGO — restaurar TODOS los ajustes modificados
    // ══════════════════════════════════════════════════════════════════════════
    suspend fun disableGameMode(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(
            // Animaciones
            "settings put global window_animation_scale 1",
            "settings put global transition_animation_scale 1",
            "settings put global animator_duration_scale 1",

            // CPU / batería
            "cmd power set-mode 0",
            "settings put global sem_enhanced_cpu_responsiveness 0",
            "settings put global adaptive_battery_management_enabled 1",
            "settings put global automatic_power_save_mode 1",
            "settings put global app_standby_enabled 1",
            "settings put global enable_freeform_support 0",

            // Pantalla
            "settings put global nfc_on 1",
            "settings put system screen_brightness_mode 1",
            "settings put global always_on_display_enabled 1",
            "settings put system accelerometer_rotation 1",
            "settings put system screen_off_timeout 300000",    // 5 min (era 30 min)
            "settings put global bluetooth_scan_mode 23",

            // GPU
            "settings put global gpu_debug_layers_enable 0",
            "settings put global skia_use_vulkan_for_android 0",

            // Touch / puntero — restaurar los que se cambiaron
            "settings put system haptic_feedback_enabled 1",
            "settings put system sound_effects_enabled 1",
            "settings put system pointer_speed 0",              // restaurar velocidad por defecto
            "settings put system touch_sensitivity_mode 0",
            "settings put system touch_blocking_period 100",
            "settings put system touch_debounce_period 50",
            "settings put system touch_event_blocking_period 100",
            "settings put system touch_smooth_mode 1",
            "settings put system touch_sensitivity_auto_adjust 1",
            "settings put system accidental_touch_protection 1",
            "settings put secure touch_exploration_enabled 0",
            "settings put secure spell_checker_enabled 1",
            // Restaurar tiempos de toque
            "settings put system long_press_timeout 400",
            "settings put system multi_press_timeout 400",
            // Restaurar asistente virtual
            "settings put secure assist_gesture_enabled 1",
            "settings put secure assist_gesture_wake 1",
            "settings put secure double_tap_to_wake 1",

            // Paneles Samsung — restaurar panel lateral y tipeo lateral
            "settings put system edge_panels_enabled 1",
            "settings put system edge_lighting_enabled 1",
            "settings put system edge_typing_enabled 1",
            "settings put system one_handed_mode_enabled 0",
            "settings put system swipe_up_to_switch_apps_enabled 1",
            "settings put secure camera_double_tap_power_key_gesture_disabled 0",
            "settings put secure navigation_mode 2",
            "settings put global policy_control null",
            "settings put global game_home_enable 1",
            "settings put global game_tools_enable 1",
            "settings put global game_automatic_fps 1",
            "settings put system prevent_accidental_touch 1",
            "settings put global wifi_watchdog_on 1",
            "settings put global device_idle_constants \"\"",
            // Restaurar Samsung Security Engine
            "settings put global sem_mobile_security_engine 1",
            "settings put global game_mode_intervention 1",
            "settings put global network_scoring_ui_enabled 1",

            // Notificaciones
            "settings put system notification_bubbles 1",
            "settings put global heads_up_notifications_enabled 1",

            // Proceso — Free Fire vuelve a prioridad normal
            "cmd activity set-standby-bucket com.dts.freefireth working_set",
            "cmd activity set-standby-bucket com.dts.freefiremaxob working_set",

            // Memoria
            "settings put global always_finish_activities 0",
            "settings put global background_process_limit -1",
            "settings put global cached_apps_freezer disabled",

            // Red — restaurar WiFi y datos normales
            "settings put global wifi_scan_always_enabled 1",
            "settings put global captive_portal_detection_enabled 1",
            "settings put global nsd_on 1",
            "settings put global aggressive_wifi_to_mobile_handover 0",
            "settings put global mobile_data_always_on 0",
            "settings put global wifi_connected_mac_randomization_enabled 1",    // faltaba restaurar
            "settings put global network_recommendations_enabled 1",              // faltaba restaurar
            "settings put global wifi_enhanced_auto_join 1",                     // faltaba restaurar
            "settings put global sync_disabled 0",
            "settings put global auto_time 1",
            "settings put secure location_mode 3",
            "settings put global fstrim_mandatory_interval 3600000"
        ))
    }

    /** Ejecuta un único comando shell via Shizuku. Para uso externo (ej. botón Limpiar RAM). */
    suspend fun run(cmd: String): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(cmd))
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
