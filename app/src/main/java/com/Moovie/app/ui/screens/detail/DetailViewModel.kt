package com.Moovie.app.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.model.DownloadItem
import com.Moovie.app.data.model.WatchItem
import com.Moovie.app.data.repo.DetailBundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(
    private val mediaType: String,
    private val id: Int,
) : ViewModel() {

    val bundle = MutableStateFlow<DetailBundle?>(null)
    val error = MutableStateFlow<String?>(null)
    val watchItem = MutableStateFlow<WatchItem?>(null)
    val downloadItem = MutableStateFlow<DownloadItem?>(null)
    val myRating = MutableStateFlow<Int?>(null)

    private val uid get() = ServiceLocator.auth.uid

    init {
        viewModelScope.launch {
            try {
                bundle.value = ServiceLocator.movies.detail(id, mediaType)
                val u = uid ?: return@launch
                watchItem.value = ServiceLocator.watchlist.findItem(u, "$mediaType-$id")
                downloadItem.value = ServiceLocator.downloads.findItem(u, "$mediaType-$id")
                ServiceLocator.social.myReviews(u).collect { reviews ->
                    myRating.value = reviews.firstOrNull { it.tmdbId == id && it.key == "$mediaType-$id" }?.rating
                }
            } catch (e: Exception) {
                error.value = "Couldn't load details: ${e.message}"
            }
        }
    }

    fun toggleWatchlist() {
        val u = uid ?: return
        val b = bundle.value ?: return
        viewModelScope.launch {
            val current = watchItem.value
            if (current == null) {
                val item = WatchItem(
                    key = "$mediaType-$id",
                    tmdbId = id,
                    mediaType = mediaType,
                    titleName = b.title.name,
                    posterPath = b.title.posterPath,
                    backdropPath = b.title.backdropPath,
                    year = b.title.year,
                    rating = b.title.rating,
                    genreIds = b.genres.map { it.id },
                    runtimeMinutes = b.runtimeMinutes,
                    status = WatchItem.STATUS_WANT,
                )
                ServiceLocator.watchlist.addOrUpdate(u, item)
                watchItem.value = item
            } else {
                ServiceLocator.watchlist.remove(u, current.key)
                watchItem.value = null
            }
        }
    }

    fun setStatus(status: String) {
        val u = uid ?: return
        val current = watchItem.value ?: return
        viewModelScope.launch {
            ServiceLocator.watchlist.setStatus(u, current.key, status)
            watchItem.value = current.copy(status = status)
        }
    }

    fun markWatched() = setStatus(WatchItem.STATUS_WATCHED)

    /** Saves/removes this title from the offline Downloads list. */
    fun toggleDownload() {
        val u = uid ?: return
        val b = bundle.value ?: return
        viewModelScope.launch {
            val current = downloadItem.value
            if (current == null) {
                val item = DownloadItem(
                    key = "$mediaType-$id",
                    tmdbId = id,
                    mediaType = mediaType,
                    titleName = b.title.name,
                    posterPath = b.title.posterPath,
                    year = b.title.year,
                    rating = b.title.rating,
                    status = DownloadItem.STATUS_QUEUED,
                )
                ServiceLocator.downloads.addOrUpdate(u, item)
                downloadItem.value = item
            } else {
                ServiceLocator.downloads.remove(u, current.key)
                downloadItem.value = null
            }
        }
    }
}
