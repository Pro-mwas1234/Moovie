package com.Moovie.app.ui.screens.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.model.DownloadItem
import com.Moovie.app.data.remote.TmdbClient
import com.Moovie.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class DownloadsViewModel : ViewModel() {
    val items = MutableStateFlow<List<DownloadItem>>(emptyList())

    init {
        viewModelScope.launch {
            // Self-heal the session so the list isn't stuck empty when the app
            // was opened without a (guest) sign-in.
            val uid = ServiceLocator.auth.ensureUid() ?: return@launch
            ServiceLocator.downloads.downloads(uid).collect { items.value = it }
        }
    }

    fun remove(item: DownloadItem) {
        // Also delete the video file itself (MediaStore row or private file).
        ServiceLocator.downloadFiles.delete(item.localPath)
        viewModelScope.launch {
            val uid = ServiceLocator.auth.ensureUid() ?: return@launch
            ServiceLocator.downloads.remove(uid, item.key)
        }
    }

    /** Starts (or resumes) the real file download for a queued/failed item. */
    fun download(item: DownloadItem) {
        val fresh = if (item.localPath != null && ServiceLocator.downloadFiles.exists(item.localPath)) {
            item.copy(status = DownloadItem.STATUS_READY, progressPercent = 100)
        } else item
        // enqueue self-heals auth (ensureUid) instead of silently no-oping.
        ServiceLocator.downloadManager.enqueue(fresh)
    }

    fun cancel(item: DownloadItem) {
        ServiceLocator.downloadManager.cancel(item.key)
    }

    /** Deletes the local file but keeps the entry (back to queued). */
    fun deleteLocal(item: DownloadItem) {
        viewModelScope.launch {
            val uid = ServiceLocator.auth.ensureUid() ?: return@launch
            ServiceLocator.downloadManager.deleteLocal(uid, item)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(nav: NavController, vm: DownloadsViewModel = viewModel()) {
    val items by vm.items.collectAsState()
    var tab by remember { mutableIntStateOf(0) }

    val filtered = when (tab) {
        1 -> items.filter { it.status != DownloadItem.STATUS_READY }
        2 -> items.filter { it.status == DownloadItem.STATUS_READY }
        else -> items
    }
    val readyCount = items.count { it.status == DownloadItem.STATUS_READY }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Downloads", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "${items.size} saved · $readyCount ready offline",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Tap the download arrow to fetch the video to this device. " +
                    "Long-press a row to remove it.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "All (${items.size})",
                "Queued (${items.count { it.status != DownloadItem.STATUS_READY }})",
                "Ready ($readyCount)",
            ).forEachIndexed { i, label ->
                FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(label) })
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp),
                    )
                    Text(
                        if (items.isEmpty())
                            "No downloads yet.\nTap Download on any title to save it here."
                        else "Nothing in this tab.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { nav.navigate(Routes.DISCOVER) { launchSingleTop = true } }) {
                        Text("Browse Discover")
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(filtered, key = { it.key }) { item ->
                    DownloadRow(
                        item = item,
                        onOpen = {
                            nav.navigate(
                                Routes.player(
                                    item.mediaType,
                                    item.tmdbId,
                                    item.season ?: 1,
                                    item.episode ?: 1,
                                )
                            )
                        },
                        onDownload = { vm.download(item) },
                        onCancel = { vm.cancel(item) },
                        onDeleteLocal = { vm.deleteLocal(item) },
                        onRemove = { vm.remove(item) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadRow(
    item: DownloadItem,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDeleteLocal: () -> Unit,
    onRemove: () -> Unit,
) {
    val ready = item.status == DownloadItem.STATUS_READY
    val downloading = item.status == DownloadItem.STATUS_DOWNLOADING
    val failed = item.status == DownloadItem.STATUS_FAILED
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onRemove)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(
            Modifier
                .width(52.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = TmdbClient.posterUrl(item.posterPath, "w154"),
                contentDescription = item.titleName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(item.titleName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val bits = buildList {
                if (item.mediaType == "tv" && item.season != null) {
                    add(
                        "S${item.season}" + (item.episode?.let { " · E$it" } ?: "")
                    )
                }
                when {
                    ready -> add("Ready offline")
                    downloading -> add("Downloading… ${item.progressPercent}%")
                    failed -> add("Failed — tap retry")
                    else -> add("Queued for download")
                }
            }
            Text(
                bits.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    ready -> MaterialTheme.colorScheme.primary
                    downloading -> MaterialTheme.colorScheme.tertiary
                    failed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (downloading) {
                LinearProgressIndicator(
                    progress = { item.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
        }
        when {
            downloading -> IconButton(onClick = onCancel) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Cancel download",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            ready && item.localPath != null -> IconButton(onClick = onDeleteLocal) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete downloaded file",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> IconButton(onClick = onDownload) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = if (failed) "Retry download" else "Download now",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

