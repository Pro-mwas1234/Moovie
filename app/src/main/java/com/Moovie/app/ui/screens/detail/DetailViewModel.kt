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

    private fun toast(msg: String) {
        android.util.Log.w("DetailViewModel", msg)
    }

    init {
        viewModelScope.launch {
            try {
                bundle.value = ServiceLocator.movies.detail(id, mediaType)
                // ensureUid mints a guest session when none exists, so the saved
                // state below is real even before the user signs in.
                val u = ServiceLocator.auth.ensureUid() ?: return@launch
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
        val b = bundle.value ?: return
        viewModelScope.launch {
            // ensureUid self-heals the missing-session case instead of the
            // button silently doing nothing.
            val u = ServiceLocator.auth.ensureUid() ?: run {
                toast("Couldn't start a session — try again.")
                return@launch
            }
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
                    .onFailure { toast("Couldn't save: ${it.message}") }
                watchItem.value = item
            } else {
                ServiceLocator.watchlist.remove(u, current.key)
                    .onFailure { toast("Couldn't remove: ${it.message}") }
                watchItem.value = null
            }
        }
    }

    fun setStatus(status: String) {
        val current = watchItem.value ?: return
        viewModelScope.launch {
            val u = ServiceLocator.auth.ensureUid() ?: return@launch
            ServiceLocator.watchlist.setStatus(u, current.key, status)
                .onFailure { toast("Couldn't update: ${it.message}") }
            watchItem.value = current.copy(status = status)
        }
    }

    fun markWatched() = setStatus(WatchItem.STATUS_WATCHED)

    /** Saves/removes this title from the offline Downloads list. */
    fun toggleDownload() {
        val b = bundle.value ?: return
        viewModelScope.launch {
            val u = ServiceLocator.auth.ensureUid() ?: return@launch
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
