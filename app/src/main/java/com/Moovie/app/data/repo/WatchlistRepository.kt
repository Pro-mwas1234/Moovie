package com.Moovie.app.data.repo

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.model.WatchItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class WatchStats(
    val watchedCount: Int = 0,
    val hoursWatched: Int = 0,
    val reviewCount: Int = 0,
    val favoriteGenre: String? = null,
)

class WatchlistRepository {

    private val local get() = ServiceLocator.local

    private fun col(uid: String) =
        FirestoreGate.db()?.collection("users/$uid/watchlist")

    fun watchlist(uid: String): Flow<List<WatchItem>> {
        val c = col(uid)
        if (c == null) return local.watchFlow(uid)
        return callbackFlow {
            val reg = c.addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val items = snap?.documents?.mapNotNull {
                    WatchItem.fromMap(it.id, it.data ?: emptyMap())
                } ?: emptyList()
                trySend(items)
            }
            awaitClose { reg.remove() }
        }
    }

    suspend fun addOrUpdate(uid: String, item: WatchItem): Result<Unit> = runCatching {
        val c = col(uid) ?: run {
            val flow = local.watchFlow(uid)
            val current = flow.value.toMutableList()
            current.removeAll { it.key == item.key || (it.tmdbId == item.tmdbId && it.mediaType == item.mediaType) }
            current.add(0, item)
            flow.value = current
            return Result.success(Unit)
        }
        val docId = item.key.ifBlank { "${item.mediaType}-${item.tmdbId}" }
        c.document(docId).set(item.toMap()).await()
    }

    suspend fun setStatus(uid: String, docId: String, status: String): Result<Unit> = runCatching {
        val c = col(uid) ?: run {
            local.watchFlow(uid).value = local.watchFlow(uid).value.map {
                if (it.key == docId) it.copy(status = status) else it
            }
            return Result.success(Unit)
        }
        c.document(docId).update("status", status).await()
    }

    suspend fun setProgress(uid: String, docId: String, progressMinutes: Int?): Result<Unit> = runCatching {
        val c = col(uid) ?: run {
            local.watchFlow(uid).value = local.watchFlow(uid).value.map {
                if (it.key == docId) it.copy(progressMinutes = progressMinutes) else it
            }
            return Result.success(Unit)
        }
        c.document(docId).update("progressMinutes", progressMinutes).await()
    }

    suspend fun remove(uid: String, docId: String): Result<Unit> = runCatching {
        val c = col(uid) ?: run {
            local.watchFlow(uid).value = local.watchFlow(uid).value.filterNot { it.key == docId }
            return Result.success(Unit)
        }
        c.document(docId).delete().await()
    }

    suspend fun watchlistOnce(uid: String): List<WatchItem> {
        val c = col(uid) ?: return local.watchFlow(uid).value
        return try {
            c.get().await().documents.mapNotNull {
                WatchItem.fromMap(it.id, it.data ?: emptyMap())
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun findItem(uid: String, key: String): WatchItem? {
        val c = col(uid) ?: return local.watchFlow(uid).value.firstOrNull { it.key == key }
        return try {
            c.document(key).get().await().data?.let { WatchItem.fromMap(key, it) }
        } catch (_: Exception) {
            null
        }
    }

    fun stats(items: List<WatchItem>, reviewCount: Int): WatchStats {
        val watched = items.filter { it.status == WatchItem.STATUS_WATCHED }
        val hours = watched.sumOf { (it.runtimeMinutes ?: 110) } / 60
        val genreTally = watched.flatMap { it.genreIds }.groupingBy { it }.eachCount()
        val topGenreId = genreTally.maxByOrNull { it.value }?.key
        return WatchStats(
            watchedCount = watched.size,
            hoursWatched = hours,
            reviewCount = reviewCount,
            favoriteGenre = topGenreId?.let { GenreNames.name(it) },
        )
    }
}
