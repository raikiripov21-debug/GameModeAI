package com.gamemodeai

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Wrapper de Shizuku completamente defensivo.
 * REGLAS:
 * - Shizuku es OPCIONAL. Si no está disponible, todos los métodos retornan false sin crash.
 * - runCommands() ejecuta N comandos en UN proceso sh (mínimo overhead).
 * - Todos los métodos son suspend y usan Dispatchers.IO.
 */
object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    private const val PERMISSION_CODE = 1001

    // ── Estado de Shizuku ─────────────────────────────────────────────────────

    fun isShizukuAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Exception) { false }

    fun hasPermission(): Boolean = try {
        if (Shizuku.isPreV11()) false
        else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    fun requestPermission() {
        try { if (!Shizuku.isPreV11()) Shizuku.requestPermission(PERMISSION_CODE) }
        catch (_: Exception) { }
    }

    // ── FASE 1 — Al activar el modo juego ─────────────────────────────────────
    // Optimizado para Samsung Galaxy A06 (Exynos 850, 3GB RAM, LCD 60Hz)
    suspend fun enableGameMode(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(
            // SAMSUNG GOS — parar el throttling de rendimiento de Samsung
            "am force-stop com.samsung.android.game.gos",
            "am force-stop com.samsung.android.game.gamehome",
            "am force-stop com.samsung.android.game.gametools",
            "settings put global game_home_enable 0",
            "settings put global game_tools_enable 0",
            "settings put global game_automatic_fps 0",
            "settings put global game_mode_intervention 0",
            "settings put global sem_mobile_security_engine 0",

            // SERVICIOS SAMSUNG — pausar los que consumen CPU/RAM en segundo plano
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
            "am force-stop com.samsung.android.app.galaxyfinder",
            "am force-stop com.samsung.android.forest",
            "am force-stop com.samsung.android.wellbeing",
            "am force-stop com.samsung.android.aware.service",
            "am force-stop com.samsung.android.spayfw",
            "am force-stop com.samsung.android.location.sensordatabaseprovider",

            // ANTI-CALENTAMIENTO — A06 se calienta con la pantalla brillante
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

            // CPU — Exynos 850: máximo rendimiento, sin ahorro de batería
            "cmd power set-mode 2",
            "settings put global sem_enhanced_cpu_responsiveness 1",
            "settings put global adaptive_battery_management_enabled 0",
            "settings put global automatic_power_save_mode 0",
            "settings put global low_power 0",
            "settings put global extreme_power_save_mode 0",
            "settings put global app_standby_enabled 0",

            // PANTALLA — 60 Hz fijo (LCD del A06, sin soporte para variable refresh)
            "settings put global window_animation_scale 0",
            "settings put global transition_animation_scale 0",
            "settings put global animator_duration_scale 0",
            "settings put system peak_refresh_rate 60",
            "settings put system min_refresh_rate 60",
            "settings put secure Vision_Booster 0",
            "settings put system screen_off_timeout 1800000",

            // GPU — Vulkan para Exynos 850
            "settings put global gpu_debug_layers_enable 0",
            "settings put global enable_gpu_debug_layers 0",
            "settings put global skia_use_vulkan_for_android 1",
            "settings put global enable_vulkan_validation_layers 0",
            "settings put secure screensaver_enabled 0",

            // AIM / TÁCTIL — respuesta raw, sin filtros, sin predicción
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
            "settings put system long_press_timeout 300",
            "settings put system multi_press_timeout 300",
            "settings put secure assist_gesture_enabled 0",
            "settings put secure assist_gesture_wake 0",
            "settings put secure double_tap_to_wake 0",

            // GESTOS SAMSUNG — eliminar interferencias durante el juego
            "settings put system edge_panels_enabled 0",
            "settings put system edge_typing_enabled 0",
            "settings put system edge_lighting_enabled 0",
            "settings put system one_handed_mode_enabled 0",
            "settings put system swipe_up_to_switch_apps_enabled 0",
            "settings put secure camera_double_tap_power_key_gesture_disabled 1",
            "settings put secure navigation_mode 0",
            "settings put global policy_control immersive.full=com.dts.freefireth,com.dts.freefiremaxob",
            "settings put secure immersive_mode_confirmations confirmed",
            "settings put system prevent_accidental_touch 0",
            "settings put system accidental_touch_protection 0",
            "settings put global wifi_watchdog_on 0",

            // RED — WiFi estable, sin power-save, sin saltos de ping
            "settings put global wifi_sleep_policy 2",
            "settings put global mobile_data_always_on 1",
            "settings put global captive_portal_detection_enabled 0",
            "settings put global nsd_on 0",
            "settings put global wifi_connected_mac_randomization_enabled 0",
            "settings put global network_recommendations_enabled 0",
            "settings put global wifi_enhanced_auto_join 0",
            "settings put global wifi_suspend_optimizations_enabled 0",
            "settings put global vsync_for_cpu_throttle 0",
            "settings put global network_scoring_ui_enabled 0",
            "settings put global aggressive_wifi_to_mobile_handover 1",

            // PROCESO — Free Fire con máxima prioridad
            "cmd activity set-standby-bucket com.dts.freefireth active",
            "cmd activity set-standby-bucket com.dts.freefiremaxob active",

            // MEMORIA — limitar procesos en fondo, congelar apps inactivas
            "am kill-all",
            "settings put global background_process_limit 2",
            "settings put global cached_apps_freezer enabled",
            "settings put global device_idle_constants min_time_to_alarm=3600000",

            // NOTIFICACIONES — sin interrupciones durante el juego
            "settings put global auto_time 0",
            "settings put global heads_up_notifications_enabled 0",
            "settings put system notification_bubbles 0",
            "settings put global fstrim_mandatory_interval 86400000",

            // Limpieza final de procesos en fondo
            "am kill-all"
        ))
    }

    // ── Mantenimiento cada 5 minutos ──────────────────────────────────────────
    suspend fun applyMaintenanceMode(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(
            "am force-stop com.samsung.android.game.gos",
            "am force-stop com.samsung.android.game.gamehome",
            "am force-stop com.samsung.android.bixby.agent",
            "am force-stop com.samsung.android.bixby.service",
            "am force-stop com.samsung.android.forest",
            "am force-stop com.samsung.android.wellbeing",
            "cmd activity set-standby-bucket com.dts.freefireth active",
            "cmd activity set-standby-bucket com.dts.freefiremaxob active",
            "settings put global window_animation_scale 0",
            "settings put global transition_animation_scale 0",
            "settings put global animator_duration_scale 0",
            "settings put global wifi_scan_always_enabled 0",
            "settings put global sync_disabled 1",
            "settings put global nfc_on 0",
            "settings put system haptic_feedback_enabled 0",
            "settings put system touch_sensitivity_mode 1",
            "settings put system touch_blocking_period 0",
            "settings put system touch_debounce_period 0",
            "settings put system touch_smooth_mode 0",
            "settings put system long_press_timeout 300",
            "settings put secure assist_gesture_enabled 0",
            "settings put global sem_mobile_security_engine 0",
            "settings put global game_mode_intervention 0",
            "settings put global heads_up_notifications_enabled 0",
            "settings put global background_process_limit 2",
            "settings put global wifi_connected_mac_randomization_enabled 0",
            "settings put global network_recommendations_enabled 0"
        ))
    }

    // ── FASE 2 — Partida larga (después de 20 minutos) ────────────────────────
    // Reducción térmica adicional para el A06 (panel LCD = mayor fuente de calor)
    suspend fun applyLongGameMode(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(
            // Brillo 115→75 (29%): el LCD del A06 genera ~30% del calor total
            "settings put system screen_brightness 75",
            // Limpieza completa de lo que se re-levantó en 20 min
            "am force-stop com.samsung.android.game.gos",
            "am force-stop com.samsung.android.bixby.agent",
            "am force-stop com.samsung.android.bixby.service",
            "am force-stop com.samsung.android.app.camera",
            "am force-stop com.samsung.android.galaxyapps",
            "am force-stop com.sec.android.daemonapp",
            "am force-stop com.samsung.android.sm",
            "am force-stop com.samsung.android.lool",
            "am force-stop com.samsung.android.app.galaxyfinder",
            // Reducir al mínimo posible los procesos en fondo
            "settings put global background_process_limit 1",
            "am kill-all",
            // Reconfirmar configuraciones críticas
            "settings put global wifi_scan_always_enabled 0",
            "settings put global sync_disabled 1",
            "settings put secure location_mode 0",
            "settings put global nfc_on 0",
            "settings put global wifi_connected_mac_randomization_enabled 0",
            "settings put global network_recommendations_enabled 0",
            // Máxima prioridad para Free Fire
            "cmd activity set-standby-bucket com.dts.freefireth active",
            "cmd activity set-standby-bucket com.dts.freefiremaxob active",
            // Tercera limpieza para asegurar que nada compite
            "am kill-all"
        ))
    }

    // ── Mantenimiento Fase 2 (cada 5 minutos) ─────────────────────────────────
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
            "settings put global heads_up_notifications_enabled 0",
            "settings put global wifi_connected_mac_randomization_enabled 0"
        ))
    }

    // ── Desactivar modo juego — restaurar TODOS los ajustes ───────────────────
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
            // Pantalla y conectividad
            "settings put global nfc_on 1",
            "settings put system screen_brightness_mode 1",
            "settings put global always_on_display_enabled 1",
            "settings put system accelerometer_rotation 1",
            "settings put system screen_off_timeout 300000",
            "settings put global bluetooth_scan_mode 23",
            // GPU
            "settings put global gpu_debug_layers_enable 0",
            "settings put global skia_use_vulkan_for_android 0",
            // Táctil
            "settings put system haptic_feedback_enabled 1",
            "settings put system sound_effects_enabled 1",
            "settings put system pointer_speed 0",
            "settings put system touch_sensitivity_mode 0",
            "settings put system touch_blocking_period 100",
            "settings put system touch_debounce_period 50",
            "settings put system touch_event_blocking_period 100",
            "settings put system touch_smooth_mode 1",
            "settings put system touch_sensitivity_auto_adjust 1",
            "settings put system accidental_touch_protection 1",
            "settings put secure spell_checker_enabled 1",
            "settings put system long_press_timeout 400",
            "settings put system multi_press_timeout 400",
            "settings put secure assist_gesture_enabled 1",
            "settings put secure assist_gesture_wake 1",
            "settings put secure double_tap_to_wake 1",
            // Gestos Samsung
            "settings put system edge_panels_enabled 1",
            "settings put system edge_lighting_enabled 1",
            "settings put system edge_typing_enabled 1",
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
            "settings put global sem_mobile_security_engine 1",
            "settings put global game_mode_intervention 1",
            "settings put global network_scoring_ui_enabled 1",
            // Notificaciones
            "settings put system notification_bubbles 1",
            "settings put global heads_up_notifications_enabled 1",
            // Proceso
            "cmd activity set-standby-bucket com.dts.freefireth working_set",
            "cmd activity set-standby-bucket com.dts.freefiremaxob working_set",
            // Memoria
            "settings put global background_process_limit -1",
            "settings put global cached_apps_freezer disabled",
            // Red
            "settings put global wifi_scan_always_enabled 1",
            "settings put global captive_portal_detection_enabled 1",
            "settings put global nsd_on 1",
            "settings put global aggressive_wifi_to_mobile_handover 0",
            "settings put global mobile_data_always_on 0",
            "settings put global wifi_connected_mac_randomization_enabled 1",
            "settings put global network_recommendations_enabled 1",
            "settings put global wifi_enhanced_auto_join 1",
            "settings put global wifi_suspend_optimizations_enabled 1",
            "settings put global vsync_for_cpu_throttle 1",
            "settings put global sync_disabled 0",
            "settings put global auto_time 1",
            "settings put secure location_mode 3",
            "settings put global fstrim_mandatory_interval 3600000",
            "settings put secure screensaver_enabled 0"
        ))
    }

    // ── AOT — Compilación Ahead-of-Time de Free Fire ───────────────────────────
    suspend fun optimizeFreeFireAOT(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "AOT: compilando Free Fire en modo speed…")
        val ok = runCommands(listOf(
            "cmd package compile -m speed com.dts.freefireth",
            "cmd package compile -m speed com.dts.freefiremaxob"
        ))
        Log.d(TAG, if (ok) "AOT: completado" else "AOT: falló o Shizuku no disponible")
        ok
    }

    /** Detecta si Free Fire está en ejecución (requiere Shizuku) */
    suspend fun isFreeFireRunning(): Boolean = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) return@withContext false
        return@withContext try {
            val p = Shizuku.newProcess(
                arrayOf("sh", "-c",
                    "dumpsys activity processes | grep -Ec 'freefireth|freefiremaxob'"),
                null, null
            )
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            p.destroy()
            (out.toIntOrNull() ?: 0) > 0
        } catch (_: Exception) { false }
    }

    // ── Motor de ejecución — UN proceso por lote de comandos ──────────────────
    private fun runCommands(commands: List<String>): Boolean {
        if (commands.isEmpty()) return true
        if (!isShizukuAvailable()) {
            Log.w(TAG, "Shizuku no disponible — saltando ${commands.size} comandos")
            return false
        }
        if (!hasPermission()) {
            requestPermission()
            Log.w(TAG, "Shizuku sin permiso — saltando ${commands.size} comandos")
            return false
        }
        // Todos los comandos en un solo proceso sh (evita 50+ fork/exec)
        val script = commands.joinToString(" ; ")
        return try {
            val p = Shizuku.newProcess(arrayOf("sh", "-c", script), null, null)
            // Drenar stdout para evitar que el proceso se bloquee por buffer lleno
            val stdout = p.inputStream.bufferedReader().readText()
            val exitCode = p.waitFor()
            p.destroy()
            if (exitCode != 0) Log.d(TAG, "Script exit=$exitCode (${commands.size} cmds)")
            true // true aunque algún comando individual falle; no son críticos
        } catch (e: Exception) {
            Log.e(TAG, "runCommands excepción: ${e.message}")
            false
        }
    }
}
