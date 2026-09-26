package com.Moovie.app.ui.screens.player

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.model.DownloadItem
import com.Moovie.app.data.model.TmdbEpisode
import com.Moovie.app.data.remote.OmniSaveClient
import com.Moovie.app.data.remote.TmdbClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private const val EMBED_BASE = "https://vidstuck.xyz/embed"

fun embedUrl(mediaType: String, tmdbId: Int, season: Int = 1, episode: Int = 1): String =
    if (mediaType == "tv") {
        "$EMBED_BASE/tv/$tmdbId/$season/$episode?branding=Moovie&loading=1&back=true"
    } else {
        "$EMBED_BASE/movie/$tmdbId?branding=Moovie&loading=1&back=true"
    }

class PlayerViewModel(
    private val mediaType: String,
    private val tmdbId: Int,
    private val initialSeason: Int,
    private val initialEpisode: Int,
) : ViewModel() {
    val mediaTypeForUi: String get() = mediaType
    val idForUi: Int get() = tmdbId


    val titleName = MutableStateFlow("")
    val posterPath = MutableStateFlow<String?>(null)
    val seasons = MutableStateFlow(1)
    val currentSeason = MutableStateFlow(initialSeason.coerceAtLeast(1))
    val currentEpisode = MutableStateFlow(initialEpisode.coerceAtLeast(1))
    val episodes = MutableStateFlow<List<TmdbEpisode>>(emptyList())
    val downloaded = MutableStateFlow(false)

    /** Direct MP4 URL resolved via OmniSave; null while resolving / on failure. */
    val directStream = MutableStateFlow<String?>(null)

    /** Key for the currently-playing movie or episode in the Downloads list. */
    private fun downloadKey(): String =
        if (mediaType == "tv") "$mediaType-$tmdbId-s${currentSeason.value}e${currentEpisode.value}"
        else "$mediaType-$tmdbId"

    init {
        viewModelScope.launch {
            try {
                if (mediaType == "tv") {
                    val detail = ServiceLocator.movies.tvDetail(tmdbId)
                    titleName.value = detail.title.name
                    posterPath.value = detail.title.posterPath
                    seasons.value = (detail.seasons ?: 1).coerceAtLeast(1)
                    loadEpisodes(currentSeason.value)
                } else {
                    val detail = ServiceLocator.movies.movieDetail(tmdbId)
                    titleName.value = detail.title.name
                    posterPath.value = detail.title.posterPath
                }
            } catch (_: Exception) {
            }
            // Prefer an already-downloaded local file; otherwise try to resolve
            // a direct MP4 for native playback. The WebView embed stays as
            // fallback when both fail.
            val local = runCatching {
                val uid = ServiceLocator.auth.ensureUid()
                if (uid != null) ServiceLocator.downloads.findItem(uid, downloadKey()) else null
            }.getOrNull()
            if (local?.localPath != null && java.io.File(local.localPath!!).exists()) {
                directStream.value = local.localPath
            } else {
                titleName.value.takeIf { it.isNotBlank() }?.let { name ->
                    directStream.value = OmniSaveClient.resolveStream(name)
                }
            }
        }
        refreshDownload()
    }

    fun setSeason(s: Int) {
        currentSeason.value = s
        currentEpisode.value = 1
        viewModelScope.launch { loadEpisodes(s) }
        refreshDownload()
    }

    fun setEpisode(e: Int) {
        currentEpisode.value = e
        refreshDownload()
    }

    private fun refreshDownload() {
        viewModelScope.launch {
            val uid = ServiceLocator.auth.ensureUid() ?: return@launch
            downloaded.value = ServiceLocator.downloads.findItem(uid, downloadKey()) != null
        }
    }

    /** Adds/removes the current movie or episode from the Downloads list. */
    fun toggleDownload() {
        viewModelScope.launch {
            val uid = ServiceLocator.auth.ensureUid() ?: return@launch
            val key = downloadKey()
            val existing = ServiceLocator.downloads.findItem(uid, key)
            if (existing == null) {
                val label = buildString {
                    append(titleName.value.ifBlank { "Untitled" })
                    if (mediaType == "tv") append(" · S${currentSeason.value}E${currentEpisode.value}")
                }
                val item = DownloadItem(
                    key = key,
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    titleName = label,
                    posterPath = posterPath.value,
                    season = if (mediaType == "tv") currentSeason.value else null,
                    episode = if (mediaType == "tv") currentEpisode.value else null,
                    status = DownloadItem.STATUS_QUEUED,
                )
                ServiceLocator.downloads.addOrUpdate(uid, item)
                downloaded.value = true
            } else {
                ServiceLocator.downloads.remove(uid, key)
                downloaded.value = false
            }
        }
    }

    private suspend fun loadEpisodes(season: Int) {
        episodes.value = runCatching {
            ServiceLocator.tmdb.tvSeason(tmdbId, season).episodes
        }.getOrDefault(emptyList())
    }
}
