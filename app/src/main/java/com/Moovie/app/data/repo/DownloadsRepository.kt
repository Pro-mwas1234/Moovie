package com.Moovie.app.data.repo

import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.model.DownloadItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Saved-for-offline list. Backed by [LocalDataStore], which keeps the list in
 * memory during the session **and** persists it to disk per user — so
 * downloads survive app restarts and updates. Every mutation re-persists.
 */
class DownloadsRepository {

    private val local get() = ServiceLocator.local

    private fun downloadFiles() = ServiceLocator.downloadFiles

    fun downloads(uid: String): StateFlow<List<DownloadItem>> = local.downloadFlow(uid)

    suspend fun addOrUpdate(uid: String, item: DownloadItem) {
        val current = local.downloadFlow(uid).value.toMutableList()
        current.removeAll { it.key == item.key }
        val next = listOf(item) + current
        local.downloadFlow(uid).value = next
        local.persistDownloads(uid, next)
    }

    suspend fun setStatus(uid: String, docId: String, status: String) {
        val next = local.downloadFlow(uid).value.map {
            if (it.key == docId) it.copy(status = status) else it
        }
        local.downloadFlow(uid).value = next
        local.persistDownloads(uid, next)
    }

    suspend fun setProgress(uid: String, docId: String, percent: Int) {
        val safe = percent.coerceIn(0, 100)
        val next = local.downloadFlow(uid).value.map {
            if (it.key == docId) it.copy(progressPercent = safe) else it
        }
        local.downloadFlow(uid).value = next
        local.persistDownloads(uid, next)
    }

    suspend fun remove(uid: String, docId: String) {
        val next = local.downloadFlow(uid).value.filterNot { it.key == docId }
        local.downloadFlow(uid).value = next
        local.persistDownloads(uid, next)
    }

    suspend fun findItem(uid: String, key: String): DownloadItem? =
        local.downloadFlow(uid).value.firstOrNull { it.key == key }
}
