package com.ptvin.tmdbrows.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ptvin.tmdbrows.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.io.IOException

@Serializable
private data class ReleaseInfo(
    @SerialName("tag_name") val tag: String = "",
    val assets: List<ReleaseAsset> = emptyList(),
)

@Serializable
private data class ReleaseAsset(
    val name: String = "",
    @SerialName("browser_download_url") val url: String = "",
)

/** Versão publicada no GitHub e o APK para descarregar. */
data class Update(val version: String, val url: String)

/**
 * Atualizações pela API pública das releases do GitHub.
 * Verifica no máximo uma vez por dia; o utilizador pode recusar uma versão.
 */
object Updates {
    private const val API = "https://api.github.com/repos/vinnyoliveira/tmdb-rows-for-monet/releases/latest"
    private const val CHECK_EVERY_MS = 24L * 60 * 60 * 1000
    private const val APK_MIME = "application/vnd.android.package-archive"

    fun check(context: Context, current: String): Update? {
        val now = System.currentTimeMillis()
        if (now - Prefs.lastUpdateCheck(context) < CHECK_EVERY_MS) return null
        val body = runCatching {
            val request = Request.Builder()
                .url(API)
                .header("Accept", "application/vnd.github+json")
                .build()
            Tmdb.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull() ?: return null
        Prefs.setLastUpdateCheck(context, now)
        val release = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString(ReleaseInfo.serializer(), body)
        }.getOrNull() ?: return null
        val latest = release.tag.removePrefix("v").trim()
        if (!isNewer(latest, current)) return null
        if (Prefs.ignoredVersion(context) == latest) return null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return null
        return Update(latest, apk.url)
    }

    /** Descarrega o APK e entrega-o ao instalador do sistema. Chamar fora da thread principal. */
    fun downloadAndInstall(context: Context, update: Update) {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        val file = File(dir, "tmdb-rows-${update.version}.apk")
        Tmdb.client.newCall(Request.Builder().url(update.url).build()).execute().use { response ->
            val stream = if (response.isSuccessful) response.body?.byteStream() else null
            if (stream == null) throw IOException(context.getString(R.string.update_error))
            stream.use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** "1.10" é mais recente do que "1.9"; compara número a número. */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split('.').mapNotNull { it.toIntOrNull() }
        val b = current.split('.').mapNotNull { it.toIntOrNull() }
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }
}
