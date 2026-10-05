package com.Moovie.app.ui.screens.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.model.DownloadItem
import com.Moovie.app.data.remote.OmniSaveClient
import com.Moovie.app.data.remote.Caption
import com.Moovie.app.data.remote.StreamQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Quality picker for real file downloads. Resolves the available MP4
 * resolutions via OmniSave (the engine behind node-movie/downloader.js) and
 * hands the chosen URL to DownloadManager.
 *
 * Used for movies ([load]) and for series episodes ([loadEpisode]), where the
 * season/episode numbers are passed through to OmniSave's `se`/`ep` params.
 */
class DownloadViewModel(
    private val mediaType: String,
    private val tmdbId: Int,
) : ViewModel() {

    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val qualities = MutableStateFlow<List<StreamQuality>>(emptyList())
    val subtitles = MutableStateFlow<List<Caption>>(emptyList())
    val selectedSubtitle = MutableStateFlow<Caption?>(null)
    /** Set right after DownloadManager accepts the job; the dialog closes then. */
    val started = MutableStateFlow(false)

    private var searchTitle = ""

    fun load(title: String, year: Int?) {
        if (loading.value || qualities.value.isNotEmpty()) return
        viewModelScope.launch {
            loading.value = true
            error.value = null
            // OmniSave's library matches on title text; adding the year narrows
            // false positives like remakes.
            val query = listOf(title, year?.toString().orEmpty()).joinToString(" ").trim()
            searchTitle = title
            qualities.value = OmniSaveClient.resolveQualitiesDetailed(query)
            subtitles.value = OmniSaveClient.resolveSubtitles(query)
            if (qualities.value.isEmpty()) {
                error.value = "No downloadable source found for this title."
            }
            loading.value = false
        }
    }

    /**
     * Episode variant: prefers TV matches in OmniSave's search and targets
     * exactly this season/episode via the `se`/`ep` params. Shows an error with
     * a queue fallback when the episode can't be fetched.
     */
    fun loadEpisode(showTitle: String, year: Int?, season: Int, episode: Int) {
        if (loading.value || qualities.value.isNotEmpty()) return
        viewModelScope.launch {
            loading.value = true
            error.value = null
            val query = listOf(showTitle, year?.toString().orEmpty()).joinToString(" ").trim()
            searchTitle = showTitle
            qualities.value = OmniSaveClient.resolveQualitiesDetailed(
                title = query,
                season = season,
                episode = episode,
                preferTv = true,
            )
            subtitles.value = OmniSaveClient.resolveSubtitles(
                title = query,
                season = season,
                episode = episode,
                preferTv = true,
            )
            if (qualities.value.isEmpty()) {
                error.value = "No downloadable source found for S${season}E$episode."
            }
            loading.value = false
        }
    }

    /**
     * Saves the item and immediately starts streaming it to disk.
     * Guarantees an auth session first (self-heals a missing guest sign-in) and
     * reports failure through [error] instead of dying silently — the dialog
     * stays open so the user sees what happened.
     * Pass [subtitle] to download a subtitle track alongside the video.
     */
    fun startDownload(
        item: DownloadItem,
        quality: StreamQuality,
        subtitle: Caption? = null,
        onStarted: () -> Unit,
    ) {
        if (quality.vipLocked || quality.url.isBlank()) return
        viewModelScope.launch {
            try {
                val uid = ServiceLocator.auth.ensureUid()
                if (uid == null) {
                    error.value = "Couldn't sign in (guest auth failed). Check your connection and try again."
                    return@launch
                }
                // Persist first so DownloadManager's update() finds the row.
                ServiceLocator.downloads.addOrUpdate(uid, item)
                ServiceLocator.downloadManager.enqueue(
                    item, quality.url, subtitle?.url?.takeIf { it.isNotBlank() },
                )
                started.value = true
                onStarted()
            } catch (e: Exception) {
                error.value = "Couldn't save the download: ${e.message}"
            }
        }
    }

    fun itemFor(posterPath: String?, year: Int?): DownloadItem = DownloadItem(
        key = "$mediaType-$tmdbId",
        tmdbId = tmdbId,
        mediaType = mediaType,
        titleName = searchTitle,
        posterPath = posterPath,
        year = year,
        status = DownloadItem.STATUS_QUEUED,
    )

    /** Episode DownloadItem: key `tv-<id>-s<season>e<episode>` (same key as SeriesScreen). */
    fun episodeItemFor(
        showName: String,
        season: Int,
        episode: Int,
        episodeName: String?,
        airYear: Int?,
        posterPath: String?,
    ): DownloadItem = DownloadItem(
        key = "tv-$tmdbId-s${season}e$episode",
        tmdbId = tmdbId,
        mediaType = "tv",
        titleName = buildString {
            append(showName.ifBlank { "Series" })
            append(" · S${season}E$episode")
            episodeName?.takeIf { it.isNotBlank() }?.let { append(" — $it") }
        },
        posterPath = posterPath,
        year = airYear,
        season = season,
        episode = episode,
        status = DownloadItem.STATUS_QUEUED,
    )
}

/** One quality row; VIP-locked tiers render disabled with a lock note. */
@Composable
private fun QualityRow(quality: StreamQuality, enabled: Boolean, onPick: () -> Unit) {
    TextButton(
        onClick = onPick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (quality.vipLocked) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "VIP only",
                    modifier = Modifier.padding(end = 6.dp).height(16.dp),
                )
            }
            Text(
                buildString {
                    append("${quality.resolution}p · ${quality.format}")
                    quality.size?.takeIf { it.isNotBlank() }?.let { append("  ·  $it") }
                    if (quality.vipLocked) append("  ·  VIP")
                },
            )
        }
    }
}

/** One subtitle row; the selected language is bolded. */
@Composable
private fun SubtitleRow(label: String, selected: Boolean, onPick: () -> Unit) {
    TextButton(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                label,
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold
                    else androidx.compose.ui.text.font.FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun DownloadDialog(
    titleName: String,
    year: Int?,
    posterPath: String?,
    onDismiss: () -> Unit,
    vm: DownloadViewModel,
) {
    androidx.compose.runtime.LaunchedEffect(titleName) { vm.load(titleName, year) }
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val qualities by vm.qualities.collectAsState()
    val subtitles by vm.subtitles.collectAsState()
    val selectedSubtitle by vm.selectedSubtitle.collectAsState()
    val started by vm.started.collectAsState()

    if (started) return

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download \"$titleName\"") },
        text = {
            Column {
                when {
                    loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("Finding available qualities…")
                    }

                    error != null -> Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                    )

                    else -> {
                        // Subtitle section
                        if (subtitles.isNotEmpty()) {
                            Text(
                                "Subtitles",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.height(4.dp))
                            SubtitleRow(
                                label = "None",
                                selected = selectedSubtitle == null,
                                onPick = { vm.selectedSubtitle.value = null },
                            )
                            subtitles.forEach { sub ->
                                SubtitleRow(
                                    label = sub.lanName ?: sub.lan ?: "Unknown",
                                    selected = selectedSubtitle?.url == sub.url,
                                    onPick = { vm.selectedSubtitle.value = sub },
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                        }

                        Text(
                            "Pick a resolution — the file is saved to this device for offline playback.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        qualities.forEach { q ->
                            QualityRow(
                                quality = q,
                                enabled = !q.vipLocked,
                                onPick = {
                                    vm.startDownload(
                                        vm.itemFor(posterPath, year),
                                        q,
                                        selectedSubtitle,
                                    ) { onDismiss() }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Quality picker for a single series episode. Resolves the show on OmniSave
 * (TV-biased) and fetches the chosen `se`/`ep`; if the episode isn't available,
 * offers to queue it so DownloadManager's TV-aware retry can pick it up later.
 */
@Composable
fun EpisodeDownloadDialog(
    showName: String,
    season: Int,
    episode: Int,
    episodeName: String?,
    airYear: Int?,
    posterPath: String?,
    onQueueFallback: () -> Unit,
    onDismiss: () -> Unit,
    vm: DownloadViewModel,
) {
    androidx.compose.runtime.LaunchedEffect(showName, season, episode) {
        vm.loadEpisode(showName, airYear, season, episode)
    }
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val qualities by vm.qualities.collectAsState()
    val subtitles by vm.subtitles.collectAsState()
    val selectedSubtitle by vm.selectedSubtitle.collectAsState()
    val started by vm.started.collectAsState()

    if (started) return

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download S${season}E$episode") },
        text = {
            Column {
                if (episodeName?.isNotBlank() == true) {
                    Text(
                        episodeName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(8.dp))
                when {
                    loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("Checking sources for S${season}E$episode…")
                    }

                    error != null -> {
                        Text(
                            error!!,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "You can still queue it — Moovie will retry the download automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = {
                                onQueueFallback()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Queue anyway")
                        }
                    }

                    else -> {
                        if (subtitles.isNotEmpty()) {
                            Text(
                                "Subtitles",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.height(4.dp))
                            SubtitleRow(
                                label = "None",
                                selected = selectedSubtitle == null,
                                onPick = { vm.selectedSubtitle.value = null },
                            )
                            subtitles.forEach { sub ->
                                SubtitleRow(
                                    label = sub.lanName ?: sub.lan ?: "Unknown",
                                    selected = selectedSubtitle?.url == sub.url,
                                    onPick = { vm.selectedSubtitle.value = sub },
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                        }

                        Text(
                            "Pick a resolution — the file is saved to this device for offline playback.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        qualities.forEach { q ->
                            QualityRow(
                                quality = q,
                                enabled = !q.vipLocked,
                                onPick = {
                                    vm.startDownload(
                                        vm.episodeItemFor(
                                            showName, season, episode, episodeName, airYear, posterPath,
                                        ),
                                        q,
                                        selectedSubtitle,
                                    ) { onDismiss() }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
