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
    private const val API_URL = "https://api.github.com/repos/raikiripov21-debug/GameModeAI/releases/latest"
    private const val CONNECT_TIMEOUT = 10_000
    private const val READ_TIMEOUT    = 15_000
    private const val MAX_REDIRECTS   = 5

    suspend fun checkForUpdate(currentVersionCode: Int): Result<UpdateInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "GameModeAI-Android")
                    connectTimeout = CONNECT_TIMEOUT
                    readTimeout    = READ_TIMEOUT
                    instanceFollowRedirects = true
                }
                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    conn.disconnect()
                    throw Exception("HTTP $responseCode")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val json    = JSONObject(body)
                val tagName = json.getString("tag_name")
                val latestCode = tagName.trimStart('v').toIntOrNull() ?: 0
                val assets  = json.getJSONArray("assets")
                val dlUrl   = (0 until assets.length())
                    .mapNotNull { assets.getJSONObject(it).getString("browser_download_url") }
                    .firstOrNull { it.endsWith(".apk") } ?: ""

                UpdateInfo(
                    latestVersionCode = latestCode,
                    tagName           = tagName,
                    downloadUrl       = dlUrl,
                    isUpdateAvailable = latestCode > currentVersionCode && dlUrl.isNotEmpty()
                )
            }
        }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        versionTag: String,
        onProgress: suspend (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.cacheDir, "GameModeAI_$versionTag.apk")
            if (file.exists()) file.delete()

            var url = downloadUrl
            var conn: HttpURLConnection? = null
            // Seguir redirecciones manualmente para streams
            repeat(MAX_REDIRECTS) {
                conn?.disconnect()
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", "GameModeAI-Android")
                    connectTimeout = CONNECT_TIMEOUT
                    readTimeout    = 120_000
                    instanceFollowRedirects = false
                }
                val code = conn!!.responseCode
                if (code in 301..308) {
                    url = conn!!.getHeaderField("Location") ?: return@repeat
                    return@repeat
                }
            }

            val total = conn!!.contentLength.toLong()
            var read = 0L

            file.outputStream().use { out ->
                conn!!.inputStream.use { inp ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (inp.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress((read * 100 / total).toInt())
                    }
                }
            }
            conn!!.disconnect()
            file
        }.getOrNull()
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", apkFile)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
            )
        } catch (_: Exception) { }
    }
}
