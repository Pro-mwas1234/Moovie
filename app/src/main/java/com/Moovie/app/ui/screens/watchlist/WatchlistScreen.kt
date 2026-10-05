package com.Moovie.app.ui.screens.watchlist

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.model.WatchItem
import com.Moovie.app.data.model.WatchItem.Companion.STATUS_WATCHED
import com.Moovie.app.data.remote.TmdbClient
import com.Moovie.app.ui.components.ContinueWatchingRow
import com.Moovie.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class WatchlistViewModel : ViewModel() {
    val items = MutableStateFlow<List<WatchItem>>(emptyList())

    init {
        // ensureUid mints a guest session when none exists so the list is real,
        // instead of showing an empty watchlist forever when uid was null.
        viewModelScope.launch {
            val uid = ServiceLocator.auth.ensureUid()
            if (uid != null) {
                ServiceLocator.watchlist.watchlist(uid).collect { items.value = it }
            }
        }
    }

    fun remove(item: WatchItem) {
        val uid = ServiceLocator.auth.uid ?: return
        viewModelScope.launch { ServiceLocator.watchlist.remove(uid, item.key) }
    }

    fun setStatus(item: WatchItem, status: String) {
        val uid = ServiceLocator.auth.uid ?: return
        viewModelScope.launch { ServiceLocator.watchlist.setStatus(uid, item.key, status) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WatchlistScreen(nav: NavController, vm: WatchlistViewModel = viewModel()) {
    val items by vm.items.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var actionItem by remember { mutableStateOf<WatchItem?>(null) }

    val filtered = when (tab) {
        0 -> items.filter { it.status == WatchItem.STATUS_WANT }
        1 -> items.filter { it.status == WatchItem.STATUS_WATCHING }
        else -> items.filter { it.status == WatchItem.STATUS_WATCHED }
    }
    val continueWatching = items.filter {
        it.status != WatchItem.STATUS_WATCHED &&
            (it.status == WatchItem.STATUS_WATCHING || it.progressMinutes != null)
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "My Watchlist",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Want to Watch", "Watching", "Watched").forEachIndexed { i, label ->
                FilterChip(
                    selected = tab == i,
                    onClick = { tab = i },
                    label = { Text("$label (${when (i) { 0 -> items.count { it.status == WatchItem.STATUS_WANT }; 1 -> items.count { it.status == WatchItem.STATUS_WATCHING }; else -> items.count { it.status == WatchItem.STATUS_WATCHED } }})") },
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.LocalMovies,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp),
                    )
                    Text(
                        if (items.isEmpty()) "Nothing saved yet.\nBrowse and tap + to save titles."
                        else "Nothing in this tab yet.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { nav.navigate(Routes.DISCOVER) { launchSingleTop = true } }) {
                        Text("Browse Discover")
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                // Watching tab leads with the progress-bar row; tap to resume.
                if (tab == 1 && continueWatching.isNotEmpty()) {
                    ContinueWatchingRow(
                        continueWatching,
                        onPlay = { w -> nav.navigate(Routes.player(w.mediaType, w.tmdbId, w.season ?: 1, w.episode ?: 1)) },
                        onRemove = { w ->
                            vm.remove(w)
                            actionItem = null
                        },
                        onMarkWatched = { w ->
                            vm.setStatus(w, WatchItem.STATUS_WATCHED)
                        },
                    )
                }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(filtered, key = { it.key }) { item ->
                    Column(
                        modifier = Modifier.combinedClickable(
                            onClick = { nav.navigate(Routes.detail(item.mediaType, item.tmdbId)) },
                            onLongClick = { actionItem = item },
                        ),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            AsyncImage(
                                model = TmdbClient.posterUrl(item.posterPath),
                                contentDescription = item.titleName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Text(
                            item.titleName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            }
        }
    }

    actionItem?.let { item ->
        AlertDialog(
            onDismissRequest = { actionItem = null },
            title = { Text(item.titleName) },
            text = { Text("What do you want to do?") },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        vm.setStatus(item, if (item.status == WatchItem.STATUS_WATCHED) WatchItem.STATUS_WATCHING else WatchItem.STATUS_WATCHED)
                        actionItem = null
                    }) { Text(if (item.status == WatchItem.STATUS_WATCHED) "Watching" else "Watched") }
                    TextButton(onClick = {
                        vm.remove(item)
                        actionItem = null
                    }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = { actionItem = null }) { Text("Cancel") } },
        )
    }
}
