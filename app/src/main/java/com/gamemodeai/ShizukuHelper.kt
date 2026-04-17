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
        catch (e: Exception) { Log.w(TAG, "Shizuku no disponible: ${e.message}"); false }
    }

    fun hasPermission(): Boolean {
        return try {
            if (Shizuku.isPreV11()) false
            else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { Log.w(TAG, "Error comprobando permiso: ${e.message}"); false }
    }

    fun requestPermission() {
        try { if (!Shizuku.isPreV11()) Shizuku.requestPermission(REQUEST_CODE) }
        catch (e: Exception) { Log.e(TAG, "Error solicitando permiso: ${e.message}") }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MODO JUEGO — Fase 1 (al activar)
    // Samsung Galaxy A06 · Exynos 850 · 60 Hz LCD
    // ══════════════════════════════════════════════════════════════════════════
    suspend fun enableGameMode(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(

            // BLOQUE 1 · SAMSUNG GOS — detener throttling de software Samsung
            "am force-stop com.samsung.android.game.gos",
            "am force-stop com.samsung.android.game.gamehome",
            "am force-stop com.samsung.android.game.gametools",
            "settings put global game_home_enable 0",
            "settings put global game_tools_enable 0",
            "settings put global game_automatic_fps 0",
            "settings put global sem_game_manager_booster 0",

            // BLOQUE 2 · SERVICIOS SAMSUNG — detener consumidores de CPU/RAM en idle
            "am force-stop com.samsung.android.bixby.agent",
            "am force-stop com.samsung.android.bixby.service",
            "am force-stop com.samsung.android.bixby.wakeup",
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
            "am force-stop com.samsung.android.forest",
            "am force-stop com.samsung.android.wellbeing",
            "am force-stop com.samsung.android.aware.service",
            "am force-stop com.samsung.android.spayfw",
            "am force-stop com.samsung.android.location.sensordatabaseprovider",
            "am force-stop com.samsung.android.app.cocktailbarservice",
            "am force-stop com.samsung.android.rubin.app",

            // BLOQUE 3 · ANTI-CALENTAMIENTO FASE 1
            "settings put global nfc_on 0",
            "settings put system screen_brightness 110",
            "settings put system screen_brightness_mode 0",
            "settings put global wifi_scan_always_enabled 0",
            "settings put global wifi_scan_interval_ms 0",
            "settings put secure location_mode 0",
            "settings put global bluetooth_scan_mode 0",
            "settings put global always_on_display_enabled 0",
            "settings put system accelerometer_rotation 0",
            "settings put global sync_disabled 1",
            "settings put global auto_time_zone 0",

            // BLOQUE 4 · RENDIMIENTO CPU (Exynos 850 — 8 cores Cortex-A55)
            "cmd power set-mode 2",
            "settings put global sem_enhanced_cpu_responsiveness 1",
            "settings put global adaptive_battery_management_enabled 0",
            "settings put global automatic_power_save_mode 0",
            "settings put global low_power 0",
            "settings put global extreme_power_save_mode 0",
            "settings put global app_standby_enabled 0",
            "settings put global enable_freeform_support 0",
            "settings put global restricted_device_performance 0",
            "settings put global power_saving_mode 0",

            // BLOQUE 5 · PANTALLA — 60 Hz fijo (A06 tiene LCD, no AMOLED)
            "settings put global window_animation_scale 0",
            "settings put global transition_animation_scale 0",
            "settings put global animator_duration_scale 0",
            "settings put system peak_refresh_rate 60",
            "settings put system min_refresh_rate 60",
            "settings put secure Vision_Booster 0",
            "settings put secure accessibility_display_daltonizer_enabled 0",
            "settings put system screen_off_timeout 1800000",
            "settings put secure show_ime_with_hard_keyboard 0",

            // BLOQUE 6 · GPU — Vulkan optimizado para Mali-G52 (Exynos 850)
            "settings put global gpu_debug_layers_enable 0",
            "settings put global enable_gpu_debug_layers 0",
            "settings put global skia_use_vulkan_for_android 1",
            "settings put global enable_vulkan_validation_layers 0",
            "settings put global gpu_work_period_timing_enabled 0",

            // BLOQUE 7 · AIM / TOUCH — máxima precisión táctil LCD A06
            "settings put system haptic_feedback_enabled 0",
            "settings put system sound_effects_enabled 0",
            "settings put system pointer_speed 1",
            "settings put system pointer_location 0",
            "settings put system show_touches 0",
            "settings put system touch_sensitivity_mode 1",
            "settings put system touch_blocking_period 0",
            "settings put system touch_debounce_period 0",
            "settings put system touch_event_blocking_period 0",
            "settings put system touch_smooth_mode 0",
            "settings put system touch_sensitivity_auto_adjust 0",
            "settings put secure touch_exploration_enabled 0",
            "settings put secure accessibility_display_magnification_enabled 0",
            "settings put secure spell_checker_enabled 0",
            "settings put system long_press_timeout 280",
            "settings put system multi_press_timeout 280",
            "settings put secure assist_gesture_enabled 0",
            "settings put secure assist_gesture_wake 0",
            "settings put secure double_tap_to_wake 0",

            // BLOQUE 8 · GESTOS SAMSUNG — eliminar interferencias durante partida
            "settings put system edge_panels_enabled 0",
            "settings put system edge_typing_enabled 0",
            "settings put system edge_lighting_enabled 0",
            "settings put system one_handed_mode_enabled 0",
            "settings put system one_handed_mode_trigger 0",
            "settings put system swipe_up_to_switch_apps_enabled 0",
            "settings put secure camera_double_tap_power_key_gesture_disabled 1",
            "settings put secure assistant_gesture_triggered 1",
            "settings put secure navigation_mode 0",
            "settings put global policy_control immersive.full=com.dts.freefireth,com.dts.freefiremaxob",
            "settings put secure immersive_mode_confirmations confirmed",
            "settings put system prevent_accidental_touch 0",
            "settings put system accidental_touch_protection 0",
            "settings put global device_idle_constants min_time_to_alarm=3600000",
            "settings put global wifi_watchdog_on 0",

            // BLOQUE 9 · SAMSUNG SECURITY ENGINE — evitar microfreezes
            "settings put global sem_mobile_security_engine 0",
            "settings put global game_mode_intervention 0",
            "settings put secure screensaver_enabled 0",
            "settings put global network_scoring_ui_enabled 0",
            "settings put global auto_time 0",

            // BLOQUE 10 · FREE FIRE — máxima prioridad de proceso
            "cmd activity set-standby-bucket com.dts.freefireth active",
            "cmd activity set-standby-bucket com.dts.freefiremaxob active",

            // BLOQUE 11 · MEMORIA — liberar y limitar procesos de fondo
            "am kill-all",
            "settings put global always_finish_activities 0",
            "settings put global background_process_limit 2",
            "settings put global cached_apps_freezer enabled",

            // BLOQUE 12 · RED — WiFi estable y sin cortes
            "settings put global wifi_sleep_policy 2",
            "settings put global mobile_data_always_on 1",
            "settings put global captive_portal_detection_enabled 0",
            "settings put global nsd_on 0",
            "settings put global aggressive_wifi_to_mobile_handover 1",
            "settings put global wifi_connected_mac_randomization_enabled 0",
            "settings put global network_recommendations_enabled 0",
            "settings put global wifi_enhanced_auto_join 0",

            // BLOQUE 13 · LIMPIEZA FINAL
            "settings put global heads_up_notifications_enabled 0",
            "settings put system notification_bubbles 0",
            "settings put global fstrim_mandatory_interval 86400000",
            "am kill-all"
        ))
    }

    suspend fun applyMaintenanceMode(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(
            "am force-stop com.samsung.android.game.gos",
            "am force-stop com.samsung.android.game.gamehome",
            "am force-stop com.samsung.android.bixby.agent",
            "am force-stop com.samsung.android.bixby.service",
            "am force-stop com.samsung.android.forest",
            "am force-stop com.samsung.android.wellbeing",
            "am force-stop com.samsung.android.rubin.app",
            "cmd activity set-standby-bucket com.dts.freefireth active",
            "cmd activity set-standby-bucket com.dts.freefiremaxob active",
            "settings put global window_animation_scale 0",
            "settings put global transition_animation_scale 0",
            "settings put global animator_duration_scale 0",
            "settings put global wifi_scan_always_enabled 0",
            "settings put global sync_disabled 1",
            "settings put global nfc_on 0",
            "settings put system haptic_feedback_enabled 0",
            "settings put system sound_effects_enabled 0",
            "settings put system touch_sensitivity_mode 1",
            "settings put system touch_blocking_period 0",
            "settings put system touch_debounce_period 0",
            "settings put system touch_smooth_mode 0",
            "settings put system long_press_timeout 280",
            "settings put secure assist_gesture_enabled 0",
            "settings put global sem_mobile_security_engine 0",
            "settings put global game_mode_intervention 0",
            "settings put global heads_up_notifications_enabled 0",
            "settings put global background_process_limit 2",
            "settings put global wifi_connected_mac_randomization_enabled 0",
            "settings put global network_recommendations_enabled 0",
            "cmd power set-mode 2"
        ))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FASE 2 — Partida larga (20 minutos después de activar)
    // Reducción térmica adicional para el Exynos 850 del A06.
    // ══════════════════════════════════════════════════════════════════════════
    suspend fun applyLongGameMode(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(
            "settings put system screen_brightness 70",
            "am force-stop com.samsung.android.game.gos",
            "am force-stop com.samsung.android.bixby.agent",
            "am force-stop com.samsung.android.bixby.service",
            "am force-stop com.samsung.android.app.camera",
            "am force-stop com.samsung.android.galaxyapps",
            "am force-stop com.sec.android.daemonapp",
            "am force-stop com.samsung.android.sm",
            "am force-stop com.samsung.android.lool",
            "am force-stop com.samsung.android.app.galaxyfinder",
            "am force-stop com.samsung.android.rubin.app",
            "settings put global background_process_limit 1",
            "am kill-all",
            "settings put global wifi_scan_always_enabled 0",
            "settings put global wifi_enhanced_auto_join 0",
            "settings put global nfc_on 0",
            "settings put global sync_disabled 1",
            "settings put global network_recommendations_enabled 0",
            "cmd power set-mode 2",
            "cmd activity set-standby-bucket com.dts.freefireth active",
            "cmd activity set-standby-bucket com.dts.freefiremaxob active"
        ))
    }

    suspend fun applyLongGameMaintenance(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(
            "am force-stop com.samsung.android.game.gos",
            "am force-stop com.samsung.android.bixby.agent",
            "am force-stop com.samsung.android.bixby.service",
            "am force-stop com.samsung.android.forest",
            "am force-stop com.samsung.android.wellbeing",
            "am force-stop com.samsung.android.rubin.app",
            "cmd activity set-standby-bucket com.dts.freefireth active",
            "cmd activity set-standby-bucket com.dts.freefiremaxob active",
            "settings put global window_animation_scale 0",
            "settings put global transition_animation_scale 0",
            "settings put global animator_duration_scale 0",
            "settings put global wifi_scan_always_enabled 0",
            "settings put global sync_disabled 1",
            "settings put global nfc_on 0",
            "settings put system touch_sensitivity_mode 1",
            "settings put system touch_blocking_period 0",
            "settings put system touch_debounce_period 0",
            "settings put system touch_smooth_mode 0",
            "settings put global background_process_limit 1",
            "settings put global sem_mobile_security_engine 0",
            "settings put global game_mode_intervention 0",
            "settings put global heads_up_notifications_enabled 0",
            "cmd power set-mode 2"
        ))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EMERGENCIA TÉRMICA — temp >= THERMAL_EMERGENCY_C
    // ══════════════════════════════════════════════════════════════════════════
    suspend fun applyThermalEmergency(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(
            "settings put system screen_brightness 45",
            "settings put global background_process_limit 0",
            "am kill-all",
            "am force-stop com.samsung.android.game.gos",
            "am force-stop com.samsung.android.game.gamehome",
            "am force-stop com.samsung.android.bixby.agent",
            "am force-stop com.samsung.android.bixby.service",
            "am force-stop com.samsung.android.forest",
            "am force-stop com.samsung.android.wellbeing",
            "am force-stop com.samsung.android.sm",
            "am force-stop com.sec.android.daemonapp",
            "am force-stop com.samsung.android.rubin.app",
            "settings put global nfc_on 0",
            "settings put global wifi_scan_always_enabled 0",
            "settings put global sync_disabled 1",
            "cmd activity set-standby-bucket com.dts.freefireth active",
            "cmd activity set-standby-bucket com.dts.freefiremaxob active"
        ))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DESACTIVAR MODO JUEGO
    // ══════════════════════════════════════════════════════════════════════════
    suspend fun disableGameMode(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(
            "settings put system screen_brightness_mode 1",
            "settings put global window_animation_scale 1",
            "settings put global transition_animation_scale 1",
            "settings put global animator_duration_scale 1",
            "settings put system peak_refresh_rate 60",
            "settings put system min_refresh_rate 60",
            "settings put global sync_disabled 0",
            "settings put global nfc_on 1",
            "settings put global wifi_scan_always_enabled 1",
            "settings put global wifi_scan_interval_ms 15000",
            "settings put global wifi_connected_mac_randomization_enabled 1",
            "settings put global network_recommendations_enabled 1",
            "settings put global wifi_enhanced_auto_join 1",
            "settings put secure location_mode 3",
            "settings put global bluetooth_scan_mode 1",
            "settings put global always_on_display_enabled 0",
            "settings put system accelerometer_rotation 1",
            "settings put global heads_up_notifications_enabled 1",
            "settings put system notification_bubbles 1",
            "settings put global background_process_limit -1",
            "settings put global cached_apps_freezer disabled",
            "settings put global always_finish_activities 0",
            "settings put global auto_time 1",
            "settings put global auto_time_zone 1",
            "settings put global game_home_enable 1",
            "settings put global game_tools_enable 1",
            "settings put system haptic_feedback_enabled 1",
            "settings put system sound_effects_enabled 1",
            "settings put system long_press_timeout 400",
            "settings put system multi_press_timeout 400",
            "settings put system screen_off_timeout 60000",
            "settings put global adaptive_battery_management_enabled 1",
            "settings put global automatic_power_save_mode 1",
            "settings put global app_standby_enabled 1",
            "settings put global policy_control null",
            "settings put global mobile_data_always_on 0",
            "settings put global device_idle_constants \"\"",
            "settings put global sem_mobile_security_engine 1",
            "settings put secure double_tap_to_wake 1",
            "cmd power set-mode 0",
            "settings put global sem_enhanced_cpu_responsiveness 0",
            "settings put global touch_sensitivity_mode 0"
        ))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // COMANDO ÚNICO (para la emergencia térmica inline)
    // ══════════════════════════════════════════════════════════════════════════
    suspend fun run(cmd: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
                .also { it.waitFor() }
            true
        }.getOrElse { e -> Log.e(TAG, "run error: ${e.message}"); false }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EJECUCIÓN DE LISTA DE COMANDOS
    // ══════════════════════════════════════════════════════════════════════════
    private fun runCommands(commands: List<String>): Boolean {
        if (!isShizukuAvailable() || !hasPermission()) {
            Log.e(TAG, "Shizuku no disponible o sin permiso")
            return false
        }
        var allOk = true
        commands.forEach { cmd ->
            runCatching {
                Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
                    .also { it.waitFor() }
            }.onFailure { e ->
                Log.w(TAG, "Error en [$cmd]: ${e.message}")
                allOk = false
            }
        }
        return allOk
    }
}
