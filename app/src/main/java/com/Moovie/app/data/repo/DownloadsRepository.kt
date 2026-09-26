package com.Moovie.app.data.repo

import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.model.DownloadItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Saved-for-offline list. Firestore-backed per user with an in-memory fallback so
 * the UI works before Firebase is configured. Nothing here rips a stream; it just
 * tracks what the user wants offline and whether they've got it yet.
 */
class DownloadsRepository {

    private val local get() = ServiceLocator.local

    private fun col(uid: String) = FirestoreGate.db()?.collection("users/$uid/downloads")

    fun downloads(uid: String): Flow<List<DownloadItem>> {
        val c = col(uid)
        if (c == null) return local.downloadFlow(uid)
        return callbackFlow {
            val reg = c.addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val items = snap?.documents?.mapNotNull {
                    DownloadItem.fromMap(it.id, it.data ?: emptyMap())
                } ?: emptyList()
                trySend(items.sortedByDescending { it.addedAt?.seconds ?: 0 })
            }
            awaitClose { reg.remove() }
        }
    }

    suspend fun addOrUpdate(uid: String, item: DownloadItem): Result<Unit> = runCatching {
        val c = col(uid) ?: run {
            val flow = local.downloadFlow(uid)
            val current = flow.value.toMutableList()
            current.removeAll { it.key == item.key }
            flow.value = listOf(item) + current
            return Result.success(Unit)
        }
        val docId = item.key.ifBlank { "${item.mediaType}-${item.tmdbId}" }
        c.document(docId).set(item.toMap()).await()
    }

    suspend fun setStatus(uid: String, docId: String, status: String): Result<Unit> = runCatching {
        val c = col(uid) ?: run {
            local.downloadFlow(uid).value = local.downloadFlow(uid).value.map {
                if (it.key == docId) it.copy(status = status) else it
            }
            return Result.success(Unit)
        }
        c.document(docId).update("status", status).await()
    }

    suspend fun setProgress(uid: String, docId: String, percent: Int): Result<Unit> = runCatching {
        val safe = percent.coerceIn(0, 100)
        val c = col(uid) ?: run {
            local.downloadFlow(uid).value = local.downloadFlow(uid).value.map {
                if (it.key == docId) it.copy(progressPercent = safe) else it
            }
            return Result.success(Unit)
        }
        c.document(docId).update("progressPercent", safe).await()
    }

    suspend fun remove(uid: String, docId: String): Result<Unit> = runCatching {
        val c = col(uid) ?: run {
            local.downloadFlow(uid).value = local.downloadFlow(uid).value.filterNot { it.key == docId }
            return Result.success(Unit)
        }
        c.document(docId).delete().await()
    }

    suspend fun findItem(uid: String, key: String): DownloadItem? {
        val c = col(uid) ?: return local.downloadFlow(uid).value.firstOrNull { it.key == key }
        return try {
            c.document(key).get().await().data?.let { DownloadItem.fromMap(key, it) }
        } catch (_: Exception) {
            null
        }
    }
}
