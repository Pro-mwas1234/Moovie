package com.Moovie.app.data.repo

import android.content.Context
import com.Moovie.app.data.model.ChatMessage
import com.Moovie.app.data.model.DownloadItem
import com.Moovie.app.data.model.NotificationItem
import com.Moovie.app.data.model.PartyRoomState
import com.Moovie.app.data.model.ReviewPost
import com.Moovie.app.data.model.WatchItem
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory fallback so the app is fully usable before Firebase is configured.
 *
 * The **downloads** list is additionally persisted to a JSON file per user
 * under [downloadDir], so saved movies survive app restarts and updates
 * (previously everything lived only in RAM and vanished every relaunch).
 * Other collections remain session-only.
 */
class LocalDataStore {

    /** Set by [attach] before init() completes; persistence no-ops until then. */
    private var appContext: Context? = null

    /** Public accessor for export/write paths so repo classes can write exports. */
    val filesDir: File?
        get() = appContext?.let { File(it.filesDir, "exports").apply { mkdirs() } }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val persistMutex = Mutex()

    val watchItems = ConcurrentHashMap<String, MutableStateFlow<List<WatchItem>>>()
    val downloads = ConcurrentHashMap<String, MutableStateFlow<List<DownloadItem>>>()
    val reviews = ConcurrentHashMap<String, MutableStateFlow<List<ReviewPost>>>()
    val allReviews = MutableStateFlow<List<ReviewPost>>(emptyList())
    val follows = MutableStateFlow<Set<String>>(emptySet())
    val chat = ConcurrentHashMap<String, MutableStateFlow<List<ChatMessage>>>()
    val rooms = ConcurrentHashMap<String, MutableStateFlow<PartyRoomState>>()
    val notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    private val mutex = Mutex()
    var profileName: String = ""
        private set

    /** Loading uids whose disk seed is still in flight (don't re-load or persist over them). */
    private val loadingDownloads = ConcurrentHashMap.newKeySet<String>()

    /** Called from ServiceLocator.init with the application context. */
    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    private val downloadDir: File?
        get() = appContext?.let { ctx ->
            File(ctx.filesDir, "downloads").apply { mkdirs() }
        }

    fun watchFlow(uid: String): MutableStateFlow<List<WatchItem>> =
        watchItems.getOrPut(uid) { MutableStateFlow(emptyList()) }

    fun downloadFlow(uid: String): MutableStateFlow<List<DownloadItem>> {
        val flow = downloads.getOrPut(uid) { MutableStateFlow(emptyList()) }
        // Seed from disk exactly once per uid: only applies when nothing has
        // been added in this session yet, so in-flight writes aren't clobbered.
        if (loadingDownloads.add(uid)) {
            scope.launch {
                val fromDisk = runCatching { loadDownloads(uid) }.getOrDefault(emptyList())
                if (fromDisk.isNotEmpty() && flow.value.isEmpty()) flow.value = fromDisk
                loadingDownloads.remove(uid)
            }
        }
        return flow
    }

    fun reviewFlow(key: String): MutableStateFlow<List<ReviewPost>> =
        reviews.getOrPut(key) { MutableStateFlow(emptyList()) }

    fun chatFlow(code: String): MutableStateFlow<List<ChatMessage>> =
        chat.getOrPut(code) { MutableStateFlow(emptyList()) }

    fun roomFlow(code: String): MutableStateFlow<PartyRoomState> =
        rooms.getOrPut(code) { MutableStateFlow(PartyRoomState()) }

    suspend fun setProfileName(name: String) = mutex.withLock { profileName = name }

    /** Clears all locally cached collections and persisted downloads for every uid.
     * Used by the account-deletion flow and by dev reset, not by normal UI.
     */
    suspend fun clearAll() {
        mutex.withLock {
            watchItems.clear()
            downloads.clear()
            reviews.clear()
            allReviews.value = emptyList()
            follows.value = emptySet()
            chat.clear()
            rooms.clear()
            notifications.value = emptyList()
        }
        appContext?.let { ctx ->
            scope.launch { persistMutex.withLock { runCatching { File(ctx.filesDir, "downloads").deleteRecursively() } } }
        }
    }

    suspend fun addNotification(item: NotificationItem) {
        notifications.value = listOf(item) + notifications.value
    }

    /** Persists the current downloads list for [uid]; call after every mutation. */
    fun persistDownloads(uid: String, items: List<DownloadItem>) {
        val ctx = appContext ?: return
        scope.launch {
            persistMutex.withLock {
                runCatching { writeDownloads(ctx, uid, items) }
            }
        }
    }

    private suspend fun loadDownloads(uid: String): List<DownloadItem> = withContext(Dispatchers.IO) {
        val f = downloadDir ?: return@withContext emptyList()
        val file = File(f, fileNameFor(uid))
        if (!file.exists()) return@withContext emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            DownloadItem(
                key = o.optString("key"),
                tmdbId = o.optInt("tmdbId"),
                mediaType = o.optString("mediaType", "movie"),
                titleName = o.optString("titleName"),
                posterPath = o.optString("posterPath").takeIf { it.isNotEmpty() },
                year = if (o.has("year")) o.optInt("year") else null,
                rating = o.optDouble("rating", 0.0),
                season = if (o.has("season")) o.optInt("season") else null,
                episode = if (o.has("episode")) o.optInt("episode") else null,
                status = o.optString("status", DownloadItem.STATUS_QUEUED),
                progressPercent = o.optInt("progressPercent"),
                localPath = o.optString("localPath").takeIf { it.isNotEmpty() },
                addedAt = o.optLong("addedAt", -1L).takeIf { it > 0 }?.let { Timestamp(java.util.Date(it)) },
                updatedAt = o.optLong("updatedAt", -1L).takeIf { it > 0 }?.let { Timestamp(java.util.Date(it)) },
            ).takeIf { it.key.isNotBlank() }
        }
    }

    private suspend fun writeDownloads(context: Context, uid: String, items: List<DownloadItem>) =
        withContext(Dispatchers.IO) {
            val dir = downloadDir ?: return@withContext
            val file = File(dir, fileNameFor(uid))
            if (items.isEmpty()) {
                file.delete()
                return@withContext
            }
            val arr = JSONArray()
            items.forEach { d ->
                arr.put(
                    JSONObject().apply {
                        put("key", d.key)
                        put("tmdbId", d.tmdbId)
                        put("mediaType", d.mediaType)
                        put("titleName", d.titleName)
                        put("posterPath", d.posterPath.orEmpty())
                        put("year", d.year ?: -1)
                        put("rating", d.rating)
                        put("season", d.season ?: -1)
                        put("episode", d.episode ?: -1)
                        put("status", d.status)
                        put("progressPercent", d.progressPercent)
                        put("localPath", d.localPath.orEmpty())
                        put("addedAt", d.addedAt?.toDate()?.time ?: System.currentTimeMillis())
                        put("updatedAt", d.updatedAt?.toDate()?.time ?: System.currentTimeMillis())
                    },
                )
            }
            // Atomic-ish write: temp file then rename, so a crash mid-write
            // can't truncate the existing list.
            val tmp = File(dir, file.name + ".tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(arr.toString())
                tmp.delete()
            }
        }

    private fun fileNameFor(uid: String): String {
        val safe = uid.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(64).ifBlank { "user" }
        return "downloads-$safe.json"
    }
}
