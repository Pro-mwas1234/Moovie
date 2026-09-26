package com.Moovie.app.data.update

import android.content.Context
import com.Moovie.app.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Result of checking GitHub's latest release. */
data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val publishedAt: String?,
) {
    val hasUpdate: Boolean
        get() = apkUrl.isNotBlank()
}

/** Minimal view of GitHub's GET /repos/{owner}/{repo}/releases/latest response. */
private data class GhRelease(
    @SerializedName("tag_name") val tagName: String? = null,
    val name: String? = null,
    @SerializedName("body") val body: String? = null,
    @SerializedName("published_at") val publishedAt: String? = null,
    @SerializedName("html_url") val htmlUrl: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

private data class GhAsset(
    val name: String? = null,
    @SerializedName("browser_download_url") val browserDownloadUrl: String? = null,
    val size: Long = 0,
)

/**
 * Checks the GitHub Releases feed for a newer APK and downloads it to app cache.
 *
 * - Repo comes from BuildConfig.GITHUB_REPO (local.properties `github.repo=owner/repo`).
 * - Comparing by tag name (v1.2.3 style); falls back to "different tag" if parsing fails.
 * - Only download URLs are used; no auth needed for public repos.
 */
class UpdateChecker {

    private val json = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun latestUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val repo = BuildConfig.GITHUB_REPO
        if (repo.isBlank()) return@withContext null

        val req = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Moovie-App")
            .build()

        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val rel = json.fromJson(body, GhRelease::class.java)
                val apk = rel.assets?.firstOrNull { it.name?.endsWith(".apk") == true }
                    ?: return@withContext null
                val tag = rel.tagName ?: return@withContext null
                if (!isNewer(tag, currentVersion)) return@withContext null
                UpdateInfo(
                    tagName = tag,
                    versionName = tag.removePrefix("v"),
                    notes = rel.body.orEmpty().take(4000),
                    apkUrl = apk.browserDownloadUrl ?: return@withContext null,
                    apkSizeBytes = apk.size ?: 0L,
                    publishedAt = rel.publishedAt,
                )
            }
        }.getOrNull()
    }

    /** Streams the APK into cache and returns the file for the installer. */
    suspend fun downloadApk(context: Context, url: String, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Moovie-App")
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val body = resp.body ?: error("Empty body")
                    val total = body.contentLength()
                    val dest = File(context.cacheDir, "moovie-update.apk")
                dest.outputStream().use { out ->
                    val buf = ByteArray(128 * 1024)
                    var seen = 0L
                    var lastPct = -1
                    while (true) {
                        val n = body.byteStream().read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        seen += n
                        if (total > 0) {
                            val pct = ((seen * 100) / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
                dest
            }
        }.getOrNull()
    }

    companion object {

        /**
         * Compares "vMAJOR.MINOR.PATCH" tags. Falls back to plain inequality when
         * either side doesn't parse, so a mis-tagged release still triggers.
         */
        fun isNewer(tag: String, currentVersion: String): Boolean {
            fun parts(v: String): List<Int>? =
                v.removePrefix("v").split(".").map { it.toIntOrNull() ?: return null }
            val a = parts(tag)
            val b = parts(currentVersion)
            if (a == null || b == null) return tag != currentVersion
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
