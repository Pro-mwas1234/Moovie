package com.Moovie.app.data.repo

import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.model.DownloadItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Saved-for-offline list. Uses local in-memory storage only.
 * Firestore dependency removed for simplicity.
 */
class DownloadsRepository {

    private val local = ServiceLocator.local

    fun downloads(uid: String): StateFlow<List<DownloadItem>> = local.downloadFlow(uid)

    suspend fun addOrUpdate(uid: String, item: DownloadItem) {
        local.downloadFlow(uid).value = {
            val current = it.toMutableList()
            current.removeAll { it.key == item.key }
            listOf(item) + current
        }(local.downloadFlow(uid).value)
    }

    suspend fun setStatus(uid: String, docId: String, status: String) {
        local.downloadFlow(uid).value = local.downloadFlow(uid).value.map {
            if (it.key == docId) it.copy(status = status) else it
        }
    }

    suspend fun setProgress(uid: String, docId: String, percent: Int) {
        val safe = percent.coerceIn(0, 100)
        local.downloadFlow(uid).value = local.downloadFlow(uid).value.map {
            if (it.key == docId) it.copy(progressPercent = safe) else it
        }
    }

    suspend fun remove(uid: String, docId: String) {
        local.downloadFlow(uid).value = local.downloadFlow(uid).value.filterNot { it.key == docId }
    }

    suspend fun findItem(uid: String, key: String): DownloadItem? =
        local.downloadFlow(uid).value.firstOrNull { it.key == key }
}
