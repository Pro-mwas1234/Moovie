package com.Moovie.app.ui.screens.series

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.model.DownloadItem
import com.Moovie.app.data.model.TmdbEpisode
import com.Moovie.app.data.remote.TmdbClient
import com.Moovie.app.ui.screens.detail.DownloadViewModel
import com.Moovie.app.ui.screens.detail.EpisodeDownloadDialog
import com.Moovie.app.ui.screens.player.WebViewPlayer
import com.Moovie.app.ui.screens.player.embedUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SeriesViewModel(
    val tmdbId: Int,
    initialSeason: Int,
    initialEpisode: Int,
) : ViewModel() {

    val showName = MutableStateFlow("")
    val seasons = MutableStateFlow(1)
    val season = MutableStateFlow(initialSeason.coerceAtLeast(1))
    val episode = MutableStateFlow(initialEpisode.coerceAtLeast(1))
    val episodes = MutableStateFlow<List<TmdbEpisode>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val downloadedKeys = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch {
            loading.value = true
            try {
                val d = ServiceLocator.movies.tvDetail(tmdbId)
                showName.value = d.title.name
                seasons.value = (d.seasons ?: 1).coerceAtLeast(1)
                loadEpisodes(season.value)
            } catch (e: Exception) {
                error.value = e.message
            }
            loading.value = false
        }
        refreshDownloads()
    }

    fun setSeason(s: Int) {
        if (s == season.value) return
        season.value = s
        episode.value = 1
        viewModelScope.launch { loadEpisodes(s) }
    }

    /** Switches the embedded player to this episode (URL reloads via key change). */
    fun play(e: Int) {
        episode.value = e
    }

    private suspend fun loadEpisodes(s: Int) {
        loading.value = true
        error.value = null
        episodes.value = runCatching {
            ServiceLocator.tmdb.tvSeason(tmdbId, s).episodes
        }.getOrElse {
            error.value = "Couldn't load episodes: ${it.message}"
            emptyList()
        }
        loading.value = false
    }

    fun downloadKey(s: Int = season.value, e: Int = episode.value): String = "tv-$tmdbId-s${s}e$e"

    /** Adds a queue-only entry (no OmniSave source); DownloadManager retries TV-aware on the user's tap. */
    fun queueEpisode(s: Int, e: Int) {
        viewModelScope.launch {
            val uid = ServiceLocator.auth.ensureUid() ?: return@launch
            val ep = episodes.value.firstOrNull { it.episodeNumber == e }
                ?: runCatching { ServiceLocator.tmdb.tvSeason(tmdbId, s).episodes.firstOrNull { it.episodeNumber == e } }.getOrNull()
            ServiceLocator.downloads.addOrUpdate(
                uid,
                DownloadItem(
                    key = downloadKey(s, e),
                    tmdbId = tmdbId,
                    mediaType = "tv",
                    titleName = buildString {
                        append(showName.value.ifBlank { "Series" })
                        append(" · S${s}E${e}")
                        ep?.name?.takeIf { it.isNotBlank() }?.let { append(" — $it") }
                    },
                    posterPath = null,
                    year = ep?.airDate?.take(4)?.toIntOrNull(),
                    season = s,
                    episode = e,
                    status = DownloadItem.STATUS_QUEUED,
                ),
            )
            refreshOnce(uid)
        }
    }

    /** Removes this episode's downloads entry entirely (the top-bar toggle behaviour). */
    fun removeEpisode(s: Int, e: Int) {
        viewModelScope.launch {
            val uid = ServiceLocator.auth.ensureUid() ?: return@launch
            ServiceLocator.downloadManager.cancel(downloadKey(s, e))
            ServiceLocator.downloads.remove(uid, downloadKey(s, e))
            refreshOnce(uid)
        }
    }

    /** Marks this episode as "grab it in your provider app" in the Downloads list. */
    fun toggleDownload() {
        viewModelScope.launch {
            val uid = ServiceLocator.auth.ensureUid() ?: return@launch
            val repo = ServiceLocator.downloads
            val key = downloadKey()
            if (downloadedKeys.value.contains(key)) {
                repo.remove(uid, key)
            } else {
                val ep = episodes.value.firstOrNull { it.episodeNumber == episode.value }
                repo.addOrUpdate(
                    uid,
                    DownloadItem(
                        key = key,
                        tmdbId = tmdbId,
                        mediaType = "tv",
                        titleName = buildString {
                            append(showName.value.ifBlank { "Series" })
                            append(" · S${season.value}E${episode.value}")
                            ep?.name?.takeIf { it.isNotBlank() }?.let { append(" — $it") }
                        },
                        posterPath = null,
                        year = ep?.airDate?.take(4)?.toIntOrNull(),
                        season = season.value,
                        episode = episode.value,
                        status = DownloadItem.STATUS_QUEUED,
                    ),
                )
            }
            refreshOnce(uid)
        }
    }

    /** One-shot snapshot instead of a permanent collector (simpler lifecycle). */
    private fun refreshDownloads() {
        viewModelScope.launch {
            val uid = ServiceLocator.auth.ensureUid() ?: return@launch
            refreshOnce(uid)
        }
    }

    /** Re-checks which episodes have a downloads entry (call after enqueueing). */
    fun refreshDownloadedKeys() {
        viewModelScope.launch {
            val uid = ServiceLocator.auth.ensureUid() ?: return@launch
            refreshOnce(uid)
        }
    }

    private suspend fun refreshOnce(uid: String) {
        downloadedKeys.value = ServiceLocator.downloads.downloads(uid)
            .first()
            .filter { it.tmdbId == tmdbId }
            .map { it.key }
            .toSet()
    }
}

@Composable
fun SeriesScreen(nav: NavController, vm: SeriesViewModel) {
    val showName by vm.showName.collectAsState()
    val seasons by vm.seasons.collectAsState()
    val season by vm.season.collectAsState()
    val episode by vm.episode.collectAsState()
    val episodes by vm.episodes.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val downloadedKeys by vm.downloadedKeys.collectAsState()
    var downloadEpisode by remember { mutableStateOf<TmdbEpisode?>(null) }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // ---- Top bar ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    showName.ifBlank { "Series" },
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "S${season} · E${episode}",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            val isDownloaded = downloadedKeys.contains(vm.downloadKey())
            IconButton(onClick = { vm.toggleDownload() }) {
                Icon(
                    if (isDownloaded) Icons.Filled.DownloadDone else Icons.Filled.DownloadForOffline,
                    contentDescription = if (isDownloaded) "Remove from downloads" else "Save for offline",
                    tint = if (isDownloaded) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
        }

        // ---- Player pinned at the top ----
        WebViewPlayer(
            url = embedUrl("tv", vm.tmdbId, season, episode),
            reloadKey = season * 1000 + episode,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )

        // ---- Season selector ----
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items((1..seasons).toList()) { s ->
                FilterChip(
                    selected = s == season,
                    onClick = { vm.setSeason(s) },
                    label = { Text("S$s", color = if (s == season) MaterialTheme.colorScheme.onPrimary else Color.White) },
                )
            }
        }

        // ---- Episode list ----
        Text(
            "Episodes",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.padding(24.dp).align(Alignment.CenterHorizontally),
            )
            error != null -> Text(
                error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(episodes, key = { _, ep -> ep.id }) { _, ep ->
                    EpisodeRow(
                        ep = ep,
                        selected = ep.episodeNumber == episode,
                        downloaded = downloadedKeys.contains(vm.downloadKey(season, ep.episodeNumber)),
                        onDownload = { downloadEpisode = ep },
                        onClick = { vm.play(ep.episodeNumber) },
                    )
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    // ---- Episode quality-picker dialog (real MP4 download via OmniSave) ----
    downloadEpisode?.let { ep ->
        val dvm: DownloadViewModel = viewModel(
            key = "dl-tv-${vm.tmdbId}-s${season}e${ep.episodeNumber}",
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    DownloadViewModel("tv", vm.tmdbId) as T
            },
        )
        EpisodeDownloadDialog(
            showName = showName.ifBlank { "Series" },
            season = season,
            episode = ep.episodeNumber,
            episodeName = ep.name,
            airYear = ep.airDate?.take(4)?.toIntOrNull(),
            posterPath = null,
            onQueueFallback = { vm.queueEpisode(season, ep.episodeNumber) },
            onDismiss = {
                downloadEpisode = null
                vm.refreshDownloadedKeys()
            },
            vm = dvm,
        )
    }
}

@Composable
private fun EpisodeRow(
    ep: TmdbEpisode,
    selected: Boolean,
    downloaded: Boolean,
    onDownload: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier
                .width(96.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = TmdbClient.backdropUrl(ep.stillPath, "w300"),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (selected) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                "${ep.episodeNumber}. ${ep.name ?: "Episode ${ep.episodeNumber}"}",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                ep.airDate?.let {
                    Text(
                        it,
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (downloaded) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.DownloadDone,
                        contentDescription = "Saved",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            ep.overview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onDownload) {
            Icon(
                if (downloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                contentDescription = if (downloaded) "Downloaded" else "Download this episode",
                tint = if (downloaded) MaterialTheme.colorScheme.primary else Color.White,
            )
        }
    }
}
