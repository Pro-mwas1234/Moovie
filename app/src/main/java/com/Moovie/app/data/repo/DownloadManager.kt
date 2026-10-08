package com.Moovie.app.data.repo

import android.content.Context
import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.model.DownloadItem
import com.Moovie.app.data.remote.OmniSaveClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Real in-app downloader. For each queued DownloadItem it:
 *  1. resolves a direct MP4 URL via OmniSaveClient (title -> signed CDN link),
 *  2. streams it to app-private storage (no storage permissions needed),
 *  3. pushes live progress into the Downloads list (DOWNLOADING -> READY).
 *
 * Only one download per item at a time; [cancel] aborts an in-flight transfer.
 */
class DownloadManager(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jobs = ConcurrentHashMap<String, Job>()
    val active = MutableStateFlow<Set<String>>(emptySet())

    fun isDownloading(key: String) = key in active.value

    /**
     * Starts a download. Pass [url] (from the quality picker) to skip automatic
     * resolution; otherwise the best available quality is resolved by title.
     * Pass [subtitleUrl] to download a subtitle track alongside the video.
     * Self-heals a missing auth session via ensureUid instead of silently no-oping.
     */
    fun enqueue(item: DownloadItem, url: String? = null, subtitleUrl: String? = null) {
        val key = item.key
        if (jobs.containsKey(key)) return
        active.value = active.value + key

        val job = ServiceLocator.appScope.launch {
            val uid = ServiceLocator.auth.ensureUid()
            if (uid == null) {
                // No session could be minted (offline / auth blocked) — nothing
                // we can persist or resolve; drop back to inactive.
                jobs.remove(key)
                active.value = active.value - key
                return@launch
            }
            try {
                update(uid, key) { it.copy(status = DownloadItem.STATUS_DOWNLOADING, progressPercent = 0) }

                // 1. Use the caller-provided URL, or resolve a fresh signed one
                //    (they expire, so never persist them). TV items target their
                //    exact se/ep and prefer TV matches in OmniSave's search.
                //    titleName carries a " · S1E2 — …" suffix that must be
                //    stripped before matching the show by keyword.
                val bareTitle = item.titleName.substringBefore(" · S")
                val resolved = url ?: if (item.mediaType == "tv" && item.season != null && item.episode != null) {
                    OmniSaveClient.resolveStream(
                        title = bareTitle,
                        season = item.season ?: 0,
                        episode = item.episode ?: 0,
                        preferTv = true,
                    )
                } else {
                    OmniSaveClient.resolveStream(item.titleName)
                }
                if (resolved == null) {
                    update(uid, key) { it.copy(status = DownloadItem.STATUS_FAILED) }
                    return@launch
                }

                // 2. Stream to disk.
                val dest = fileFor(item)
                downloadTo(resolved, dest) { pct ->
                    // Throttle Firestore writes: update local state every step,
                    // but only push to backend every ~10%.
                    if (pct % 10 == 0) update(uid, key, backend = true) { it.copy(progressPercent = pct) }
                    else update(uid, key, backend = false) { it.copy(progressPercent = pct) }
                }

                // 3. Download subtitle alongside the video (if provided).
                var subtitlePath: String? = null
                if (!subtitleUrl.isNullOrBlank()) {
                    runCatching {
                        val subDest = subtitleFileFor(item)
                        downloadTo(subtitleUrl, subDest) { } // no progress for subtitles
                        subtitlePath = subDest.absolutePath
                    }
                }

                // 4. Publish into the shared Movies/Moovie gallery (MediaStore)
                //    so the file shows up in gallery apps and survives updates,
                //    then mark ready with the playback path (content:// URI on
                //    modern Android, file path on older devices).
                val playbackPath = ServiceLocator.downloadFiles.publishToGallery(
                    dest, ServiceLocator.downloadFiles.displayNameFor(item),
                )
                update(uid, key) {
                    it.copy(
                        status = DownloadItem.STATUS_READY,
                        progressPercent = 100,
                        localPath = playbackPath,
                        subtitlePath = subtitlePath,
                        subtitleLang = subtitlePath?.let { sub ->
                            item.titleName.substringBefore(" · S").substringBefore(" — ")
                        },
                    )
                }
                if (playbackPath != dest.absolutePath) dest.delete()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    // Cancelled by the user: clean up partial file, back to queued.
                    fileFor(item).delete()
                    update(uid, key) { it.copy(status = DownloadItem.STATUS_QUEUED, progressPercent = 0) }
                } else {
                    update(uid, key) { it.copy(status = DownloadItem.STATUS_FAILED) }
                }
            } finally {
                jobs.remove(key)
                active.value = active.value - key
            }
        }
        jobs[key] = job
    }

    fun cancel(key: String) {
        jobs.remove(key)?.cancel()
    }

    /** Deletes the local file (MediaStore row or file; kept in list, back to queued). */
    fun deleteLocal(uid: String, item: DownloadItem) {
        ServiceLocator.downloadFiles.delete(item.localPath)
        ServiceLocator.downloadFiles.delete(item.subtitlePath)
        ServiceLocator.appScope.launch {
            ServiceLocator.downloads.addOrUpdate(
                uid, item.copy(
                    status = DownloadItem.STATUS_QUEUED,
                    progressPercent = 0,
                    localPath = null,
                    subtitlePath = null,
                    subtitleLang = null,
                )
            )
        }
    }

    private suspend fun update(
        uid: String,
        key: String,
        backend: Boolean = true,
        transform: (DownloadItem) -> DownloadItem,
    ) {
        val repo = ServiceLocator.downloads
        val current = repo.findItem(uid, key) ?: return
        val next = transform(current)
        // Local (fallback store) always gets the update; Firestore at throttled steps.
        repo.addOrUpdate(uid, next)
    }

    /**
     * Resolves the download base directory from the user's preference.
     * Path must not contain ".." or start with "/" (no absolute escapes).
     * Falls back to the default "movies" dir and logs a warning on failure.
     */
    internal suspend fun downloadDir(): File {
        val default = File(context.getExternalFilesDir(null) ?: context.filesDir, "movies")
        val folder = ServiceLocator.prefs.prefs.first().downloadFolder
        if (folder.isBlank()) { default.mkdirs(); return default }
        if (folder.contains("..") || folder.startsWith("/") || folder.startsWith("\\")) {
            android.util.Log.w(TAG, "Rejected unsafe download folder: $folder")
            default.mkdirs()
            return default
        }
        val resolved = File(context.getExternalFilesDir(null) ?: context.filesDir, folder)
        if ((resolved.exists() || resolved.mkdirs()) && resolved.isDirectory && resolved.canWrite()) {
            return resolved
        }
        android.util.Log.w(TAG, "Configured download folder not writable, falling back: $resolved")
        default.mkdirs()
        return default
    }

    private suspend fun fileFor(item: DownloadItem): File {
        val dir = downloadDir()
        val safe = item.titleName.replace(Regex("[<>:\"/\\\\|?*]"), "").trim().take(80).ifBlank { item.key }
        val se = item.season?.let { s -> item.episode?.let { e -> ".S$s.E$e" } } ?: ""
        return File(dir, "$safe$se.${item.key.hashCode().toUInt()}.mp4")
    }

    private suspend fun subtitleFileFor(item: DownloadItem): File {
        val dir = downloadDir()
        val safe = item.titleName.replace(Regex("[<>:\"/\\\\|?*]"), "").trim().take(80).ifBlank { item.key }
        val se = item.season?.let { s -> item.episode?.let { e -> ".S$s.E$e" } } ?: ""
        return File(dir, "$safe$se.${item.key.hashCode().toUInt()}.srt")
    }

    private suspend fun downloadTo(url: String, dest: File, onProgress: suspend (Int) -> Unit) =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(url)
                .header("referer", "https://videodownloader.site/")
                .header("user-agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/150 Mobile Safari/537.36")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body ?: error("Empty body")
                val total = body.contentLength()
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
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
            }
        }

    companion object {
        const val TAG = "DownloadManager"
    }
}
