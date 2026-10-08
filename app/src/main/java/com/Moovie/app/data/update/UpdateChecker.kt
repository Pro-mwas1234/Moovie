package com.Moovie.app.data.update

import android.content.Context
import com.Moovie.app.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import android.util.Log
import java.io.File
import java.io.FileOutputStream
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

/** Outcome of an APK download: exactly one of [file] / [error] is set. */
data class DownloadResult(val file: File? = null, val error: String? = null)

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
        .connectTimeout(20, TimeUnit.SECONDS)
        // Per-read timeout (not whole-body): a slow-but-alive 100MB download
        // must not be killed just because one read takes a while.
        .readTimeout(60, TimeUnit.SECONDS)
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

    /**
     * Streams the APK into cache and returns the file for the installer.
     *
     * Release APKs are big; a single dropped connection used to throw away the
     * whole download and report "check your connection". Now it:
     *  - writes to a .part file first, so the installer never sees a half APK,
     *  - resumes with an HTTP Range request after each failure (up to 5 tries),
     *  - verifies the byte count matches the server's before declaring success,
     *  - returns the real error so the UI can say something specific.
     */
    suspend fun downloadApk(context: Context, url: String, onProgress: (Int) -> Unit): DownloadResult =
        withContext(Dispatchers.IO) {
            val dest = File(context.cacheDir, "moovie-update.apk")
            val part = File(context.cacheDir, "moovie-update.apk.part")
            if (dest.exists()) dest.delete() // stale file from an old attempt; always re-verify
            var lastError: String? = null

            repeat(MAX_ATTEMPTS) { attempt ->
                try {
                    val already = if (part.exists()) part.length() else 0L
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Moovie-App")
                        .apply { if (already > 0) header("Range", "bytes=$already-") }
                        .build()
                    http.newCall(req).execute().use { resp ->
                        val resumeOk = already > 0 && resp.code == 206
                        if (resp.code != 200 && !resumeOk) error("HTTP ${resp.code}")
                        val body = resp.body ?: error("Empty response body")
                        // Total expected size: Content-Range on resume, else Content-Length.
                        val total = if (resumeOk) {
                            resp.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                                ?: (already + body.contentLength())
                        } else {
                            if (already > 0) part.delete() // server ignored Range; start fresh
                            body.contentLength()
                        }
                        FileOutputStream(part, resumeOk).use { out ->
                            val buf = ByteArray(256 * 1024)
                            var seen = if (resumeOk) already else 0L
                            var lastPct = if (total > 0) ((seen * 100) / total).toInt() else -1
                            if (lastPct in 0..100) onProgress(lastPct)
                            while (true) {
                                val n = body.byteStream().read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                seen += n
                                if (total > 0) {
                                    val pct = ((seen * 100) / total).toInt().coerceIn(0, 100)
                                    if (pct != lastPct) {
                                        lastPct = pct
                                        onProgress(pct)
                                    }
                                }
                            }
                        }
                        if (total > 0 && part.length() < total) {
                            error("Incomplete file (${part.length()}/$total bytes)")
                        }
                        if (part.length() <= 0L) error("Downloaded file is empty")
                        if (dest.exists()) dest.delete()
                        if (!part.renameTo(dest)) error("Couldn't save the downloaded file")
                        return@withContext DownloadResult(file = dest)
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: e.javaClass.simpleName
                    Log.w(TAG, "APK download attempt ${attempt + 1}/$MAX_ATTEMPTS failed: $lastError")
                }
                kotlinx.coroutines.delay(1500L * (attempt + 1)) // back off before retrying
            }
            // Clean up leftover .part so failed checks don't accumulate in cacheDir.
            part.delete()
            DownloadResult(error = lastError ?: "Unknown error")
        }

    companion object {
        private const val TAG = "UpdateChecker"
        private const val MAX_ATTEMPTS = 5

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
