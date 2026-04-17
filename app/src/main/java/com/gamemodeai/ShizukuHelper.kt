package com.gamemodeai

import android.content.pm.PackageManager
import android.util.Log
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

    fun enableGameMode(): Boolean {
        return runCommands(
            listOf(
                "settings put global window_animation_scale 0",
                "settings put global transition_animation_scale 0",
                "settings put global animator_duration_scale 0"
            )
        )
    }

    fun disableGameMode(): Boolean {
        return runCommands(
            listOf(
                "settings put global window_animation_scale 1",
                "settings put global transition_animation_scale 1",
                "settings put global animator_duration_scale 1"
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
        return try {
            for (cmd in commands) {
                val process = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    Log.e(TAG, "Command failed (exit $exitCode): $cmd")
                }
                process.destroy()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error running Shizuku command: ${e.message}")
            false
        }
    }
}
