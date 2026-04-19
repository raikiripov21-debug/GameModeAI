package com.gamemodeai

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latestVersionCode: Int,
    val tagName: String,
    val downloadUrl: String,
    val isUpdateAvailable: Boolean
)

object UpdateChecker {
    private const val API = "https://api.github.com/repos/raikiripov21-debug/GameModeAI/releases/latest"

    suspend fun checkForUpdate(currentVersionCode: Int): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 10_000
                readTimeout    = 10_000
            }
            val code = conn.responseCode
            if (code != 200) return@withContext Result.failure(Exception("HTTP $code"))

            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            conn.disconnect()

            val tagName           = json.getString("tag_name")          // ej: "v42"
            val latestVersionCode = tagName.removePrefix("v").toIntOrNull() ?: 0
            val assets            = json.getJSONArray("assets")
            val downloadUrl       = if (assets.length() > 0)
                assets.getJSONObject(0).getString("browser_download_url")
            else ""

            Result.success(UpdateInfo(
                latestVersionCode = latestVersionCode,
                tagName           = tagName,
                downloadUrl       = downloadUrl,
                isUpdateAvailable = latestVersionCode > currentVersionCode && downloadUrl.isNotEmpty()
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Descarga el APK con progreso (0-100). Devuelve null si falla. */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        versionTag: String,
        onProgress: suspend (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "GameModeAI_$versionTag.apk")
            if (file.exists()) file.delete()

            val conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout    = 120_000
                instanceFollowRedirects = true
            }
            val total = conn.contentLength.toLong()
            var read  = 0L

            file.outputStream().use { out ->
                conn.inputStream.use { inp ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (inp.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress((read * 100 / total).toInt())
                    }
                }
            }
            conn.disconnect()
            file
        } catch (_: Exception) { null }
    }

    /** Lanza el instalador del sistema con el APK descargado. */
    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        )
    }
}
