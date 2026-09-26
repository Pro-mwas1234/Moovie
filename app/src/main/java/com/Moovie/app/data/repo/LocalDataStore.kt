package com.Moovie.app.data.repo

import com.Moovie.app.data.model.ChatMessage
import com.Moovie.app.data.model.DownloadItem
import com.Moovie.app.data.model.NotificationItem
import com.Moovie.app.data.model.PartyRoomState
import com.Moovie.app.data.model.ReviewPost
import com.Moovie.app.data.model.WatchItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory fallback so the app is fully usable before Firebase is configured.
 * Data lives for the app session only.
 */
class LocalDataStore {
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

    fun watchFlow(uid: String): MutableStateFlow<List<WatchItem>> =
        watchItems.getOrPut(uid) { MutableStateFlow(emptyList()) }

    fun downloadFlow(uid: String): MutableStateFlow<List<DownloadItem>> =
        downloads.getOrPut(uid) { MutableStateFlow(emptyList()) }

    fun reviewFlow(key: String): MutableStateFlow<List<ReviewPost>> =
        reviews.getOrPut(key) { MutableStateFlow(emptyList()) }

    fun chatFlow(code: String): MutableStateFlow<List<ChatMessage>> =
        chat.getOrPut(code) { MutableStateFlow(emptyList()) }

    fun roomFlow(code: String): MutableStateFlow<PartyRoomState> =
        rooms.getOrPut(code) { MutableStateFlow(PartyRoomState()) }

    suspend fun setProfileName(name: String) = mutex.withLock { profileName = name }

    suspend fun addNotification(item: NotificationItem) {
        notifications.value = listOf(item) + notifications.value
    }
}
