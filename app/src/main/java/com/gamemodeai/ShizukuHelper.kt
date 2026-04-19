package com.gamemodeai

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * ShizukuHelper — Samsung Galaxy A06
 *
 * REGRAS ESTRITAS (A06):
 * - APENAS comandos "settings put" e "cmd activity/package/power" permitidos
 * - PROIBIDO: am force-stop, am kill-all
 * - PROIBIDO: qualquer comando agressivo
 * - Anti-spam interno: 11 s entre execuções de manutenção
 */
object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    private const val REQUEST_CODE = 1001

    // Anti-spam: não executar manutenção mais de 1x a cada 11 s
    private var lastMaintenanceMs: Long = 0L
    private const val ANTI_SPAM_MS = 11_000L

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
    // MODO JOGO — Fase 1 (ao ativar)
    // APENAS configurações seguras via "settings put" — SEM am force-stop
    // ══════════════════════════════════════════════════════════════════════════
    suspend fun enableGameMode(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(

            // BLOCO 1 · SAMSUNG GOS — desativar via settings (sem force-stop)
            "settings put global game_home_enable 0",
            "settings put global game_tools_enable 0",
            "settings put global game_automatic_fps 0",

            // BLOCO 2 · ANTI-AQUECIMENTO FASE 1
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

            // BLOCO 3 · DESEMPENHO CPU (Exynos 850)
            "cmd power set-mode 2",
            "settings put global sem_enhanced_cpu_responsiveness 1",
            "settings put global adaptive_battery_management_enabled 0",
            "settings put global automatic_power_save_mode 0",
            "settings put global low_power 0",
            "settings put global extreme_power_save_mode 0",
            "settings put global app_standby_enabled 0",
            "settings put global enable_freeform_support 0",

            // BLOCO 4 · TELA — 60 Hz fixo (LCD A06)
            "settings put global window_animation_scale 0",
            "settings put global transition_animation_scale 0",
            "settings put global animator_duration_scale 0",
            "settings put system peak_refresh_rate 60",
            "settings put system min_refresh_rate 60",
            "settings put secure Vision_Booster 0",
            "settings put secure accessibility_display_daltonizer_enabled 0",
            "settings put system screen_off_timeout 1800000",

            // BLOCO 5 · GPU — Vulkan para Exynos 850
            "settings put global gpu_debug_layers_enable 0",
            "settings put global enable_gpu_debug_layers 0",
            "settings put global skia_use_vulkan_for_android 1",
            "settings put global enable_vulkan_validation_layers 0",

            // BLOCO 6 · AIM / TOUCH — precisão máxima A06
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

            // BLOCO 7 · GESTOS SAMSUNG — sem interferência durante o jogo
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

            // BLOCO 8 · SAMSUNG SECURITY ENGINE — reduz picos de CPU
            "settings put global sem_mobile_security_engine 0",
            "settings put global game_mode_intervention 0",
            "settings put secure screensaver_enabled 0",
            "settings put global network_scoring_ui_enabled 0",

            // BLOCO 9 · PROCESSO — Free Fire com máxima prioridade (seguro)
            "cmd activity set-standby-bucket com.dts.freefireth active",
            "cmd activity set-standby-bucket com.dts.freefiremaxob active",

            // BLOCO 10 · MEMÓRIA — limites suaves (SEM am kill-all)
            "settings put global always_finish_activities 0",
            "settings put global background_process_limit 2",
            "settings put global cached_apps_freezer enabled",

            // BLOCO 11 · REDE — WiFi estável sem interrupções
            "settings put global wifi_sleep_policy 2",
            "settings put global mobile_data_always_on 1",
            "settings put global captive_portal_detection_enabled 0",
            "settings put global nsd_on 0",
            "settings put global aggressive_wifi_to_mobile_handover 1",
            "settings put global wifi_connected_mac_randomization_enabled 0",
            "settings put global network_recommendations_enabled 0",
            "settings put global wifi_enhanced_auto_join 0",
            "settings put global wifi_suspend_optimizations_enabled 0",
            "settings put global vsync_for_cpu_throttle 0",

            // BLOCO 12 · LIMPEZA FINAL (settings apenas)
            "settings put global auto_time 0",
            "settings put global heads_up_notifications_enabled 0",
            "settings put system notification_bubbles 0",
            "settings put global fstrim_mandatory_interval 86400000"
        ))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MANUTENÇÃO — reconfirmar settings (SEM am force-stop, SEM am kill-all)
    // Anti-spam integrado: máximo 1x a cada 11 s
    // ══════════════════════════════════════════════════════════════════════════
    suspend fun applyMaintenanceMode(): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastMaintenanceMs < ANTI_SPAM_MS) {
            Log.d(TAG, "applyMaintenanceMode: anti-spam skip (${now - lastMaintenanceMs}ms)")
            return@withContext false
        }
        val ok = runCommands(listOf(
            // Reconfirmar GOS desativado
            "settings put global game_home_enable 0",
            "settings put global game_tools_enable 0",
            // Prioridade Free Fire
            "cmd activity set-standby-bucket com.dts.freefireth active",
            "cmd activity set-standby-bucket com.dts.freefiremaxob active",
            // Animações e rede
            "settings put global window_animation_scale 0",
            "settings put global transition_animation_scale 0",
            "settings put global animator_duration_scale 0",
            "settings put global wifi_scan_always_enabled 0",
            "settings put global sync_disabled 1",
            "settings put global nfc_on 0",
            // AIM — reconfirmar (alguns serviços Samsung revertem)
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
            // Notificações e memória
            "settings put global heads_up_notifications_enabled 0",
            "settings put global background_process_limit 2",
            "settings put global wifi_connected_mac_randomization_enabled 0",
            "settings put global network_recommendations_enabled 0"
        ))
        if (ok) lastMaintenanceMs = now
        ok
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FASE 2 — Partida longa (20 min após ativar)
    // Redução térmica adicional — SEM am force-stop, SEM am kill-all
    // ══════════════════════════════════════════════════════════════════════════
    suspend fun applyLongGameMode(): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(
            // Brilho mais baixo para reduzir calor (LCD é maior fonte de calor)
            "settings put system screen_brightness 75",
            // Confirmar configurações críticas
            "settings put global wifi_scan_always_enabled 0",
            "settings put global sync_disabled 1",
            "settings put secure location_mode 0",
            "settings put global nfc_on 0",
            "settings put global wifi_connected_mac_randomization_enabled 0",
            "settings put global network_recommendations_enabled 0",
            // Reduzir processos em background (limite seguro)
            "settings put global background_process_limit 1",
            // Prioridade máxima para Free Fire
            "cmd activity set-standby-bucket com.dts.freefireth active",
            "cmd activity set-standby-bucket com.dts.freefiremaxob active"
        ))
    }

    suspend fun applyLongGameMaintenance(): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastMaintenanceMs < ANTI_SPAM_MS) {
            Log.d(TAG, "applyLongGameMaintenance: anti-spam skip")
            return@withContext false
        }
        val ok = runCommands(listOf(
            "settings put system screen_brightness 75",
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
        if (ok) lastMaintenanceMs = now
        ok
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DESATIVAR MODO JOGO — restaurar todos os ajustes
    // ══════════════════════════════════════════════════════════════════════════
    suspend fun disableGameMode(): Boolean = withContext(Dispatchers.IO) {
        lastMaintenanceMs = 0L
        runCommands(listOf(
            // Animações
            "settings put global window_animation_scale 1",
            "settings put global transition_animation_scale 1",
            "settings put global animator_duration_scale 1",
            // CPU / bateria
            "cmd power set-mode 0",
            "settings put global sem_enhanced_cpu_responsiveness 0",
            "settings put global adaptive_battery_management_enabled 1",
            "settings put global automatic_power_save_mode 1",
            "settings put global app_standby_enabled 1",
            "settings put global enable_freeform_support 0",
            // Tela
            "settings put global nfc_on 1",
            "settings put system screen_brightness_mode 1",
            "settings put global always_on_display_enabled 1",
            "settings put system accelerometer_rotation 1",
            "settings put system screen_off_timeout 300000",
            "settings put global bluetooth_scan_mode 23",
            // GPU
            "settings put global gpu_debug_layers_enable 0",
            "settings put global skia_use_vulkan_for_android 0",
            // Touch — restaurar
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
            "settings put secure touch_exploration_enabled 0",
            "settings put secure spell_checker_enabled 1",
            "settings put system long_press_timeout 400",
            "settings put system multi_press_timeout 400",
            "settings put secure assist_gesture_enabled 1",
            "settings put secure assist_gesture_wake 1",
            "settings put secure double_tap_to_wake 1",
            // Painéis Samsung
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
            // Samsung Security Engine — restaurar
            "settings put global sem_mobile_security_engine 1",
            "settings put global game_mode_intervention 1",
            "settings put global network_scoring_ui_enabled 1",
            // Notificações
            "settings put system notification_bubbles 1",
            "settings put global heads_up_notifications_enabled 1",
            // Free Fire — prioridade normal
            "cmd activity set-standby-bucket com.dts.freefireth working_set",
            "cmd activity set-standby-bucket com.dts.freefiremaxob working_set",
            // Memória
            "settings put global always_finish_activities 0",
            "settings put global background_process_limit -1",
            "settings put global cached_apps_freezer disabled",
            // Rede — restaurar
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
            "settings put global fstrim_mandatory_interval 3600000"
        ))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AOT — Compilação Ahead-of-Time do Free Fire
    // ══════════════════════════════════════════════════════════════════════════
    suspend fun optimizeFreeFireAOT(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "AOT: iniciando compilação speed do Free Fire…")
        val ok = runCommands(listOf(
            "cmd package compile -m speed com.dts.freefireth",
            "cmd package compile -m speed com.dts.freefiremaxob"
        ))
        Log.d(TAG, if (ok) "AOT: compilação concluída" else "AOT: falhou ou Shizuku indisponível")
        ok
    }

    /**
     * Detecta se o Free Fire (normal ou MAX) está entre os processos ativos.
     */
    suspend fun isFreeFireRunning(): Boolean = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) return@withContext false
        return@withContext try {
            val p = Shizuku.newProcess(
                arrayOf("sh", "-c",
                    "dumpsys activity processes | grep -Ec 'freefireth|freefiremaxob'"),
                null, null
            )
            val output = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            p.destroy()
            (output.toIntOrNull() ?: 0) > 0
        } catch (e: Exception) {
            Log.e(TAG, "isFreeFireRunning: " + e.message)
            false
        }
    }

    /** Executa um único comando shell via Shizuku. */
    suspend fun run(cmd: String): Boolean = withContext(Dispatchers.IO) {
        runCommands(listOf(cmd))
    }

    /**
     * Executa todos os comandos em UM ÚNICO processo de shell.
     * Comandos separados por ';' — falhas individuais não param os próximos.
     */
    private fun runCommands(commands: List<String>): Boolean {
        if (!isShizukuAvailable()) { Log.w(TAG, "Shizuku not available"); return false }
        if (!hasPermission()) { requestPermission(); return false }
        if (commands.isEmpty()) return true
        val script = commands.joinToString(" ; ")
        return try {
            Log.d(TAG, "runCommands: ${commands.size} cmds em 1 processo")
            val p = Shizuku.newProcess(arrayOf("sh", "-c", script), null, null)
            val exit = p.waitFor()
            p.destroy()
            if (exit != 0) Log.w(TAG, "Script exit=$exit (${commands.size} cmds)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "runCommands exception: ${e.message}")
            false
        }
    }
}
