package com.Moovie.app.ui.screens.detail

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.Moovie.app.data.model.WatchItem
import com.Moovie.app.data.remote.TmdbClient
import com.Moovie.app.ui.navigation.Routes
import com.Moovie.app.ui.components.MovieRow
import com.Moovie.app.ui.components.SectionHeader
import com.Moovie.app.ui.components.StarRating
import com.Moovie.app.ui.screens.detail.components.TrailerPlayer
import com.Moovie.app.ui.screens.detail.components.CastRow
import com.Moovie.app.ui.screens.detail.components.ReviewsList
import com.Moovie.app.ui.screens.detail.components.VideoRow

@Composable
fun DetailScreen(nav: NavController, vm: DetailViewModel) {
    val bundle by vm.bundle.collectAsState()
    val watchItem by vm.watchItem.collectAsState()
    val downloadItem by vm.downloadItem.collectAsState()
    val myRating by vm.myRating.collectAsState()
    val error by vm.error.collectAsState()
    var showRateDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    when {
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }
        bundle == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> {
            val b = bundle!!
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                HeroSection(b, onBack = { nav.popBackStack() })

                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            onClick = {
                                if (b.title.mediaType == "tv") {
                                    nav.navigate(Routes.series(b.title.id))
                                } else {
                                    nav.navigate(Routes.player("movie", b.title.id))
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Text(if (b.title.mediaType == "tv") " Play show" else " Play")
                        }
                        if (b.trailerKey != null) {
                            OutlinedButton(onClick = { TrailerPlayer.open(context, b.trailerKey) }) {
                                Icon(Icons.Filled.Star, null)
                                Text(" Trailer")
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { vm.toggleWatchlist() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Favorite, null)
                            Text(if (watchItem != null) " Saved" else " Watchlist")
                        }
                    }
                    // Opens the quality picker; the chosen file downloads to disk.
                    OutlinedButton(
                        onClick = { showDownloadDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Download, null)
                        Text(if (downloadItem?.localPath != null) " Downloaded" else " Download")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showRateDialog = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Star, null)
                            Text(if (myRating != null) " Rated $myRating" else " Rate")
                        }
                        OutlinedButton(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Watch \"${b.title.name}\" — found on Moovie!")
                                }
                                context.startActivity(Intent.createChooser(send, "Share"))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Share, null)
                            Text(" Share")
                        }
                    }

                    if (watchItem != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChipSmall("Want", watchItem!!.status == WatchItem.STATUS_WANT) { vm.setStatus(WatchItem.STATUS_WANT) }
                            FilterChipSmall("Watching", watchItem!!.status == WatchItem.STATUS_WATCHING) { vm.setStatus(WatchItem.STATUS_WATCHING) }
                            FilterChipSmall("Watched", watchItem!!.status == WatchItem.STATUS_WATCHED) { vm.markWatched() }
                        }
                    }

                    GenresLine(b)
                    OverviewSection(b)
                }

                CastRow(b.cast)
                VideoRow(b.videos)
                ReviewsList(b.reviews)

                if (b.similar.isNotEmpty()) {
                    MovieRow("More Like This", b.similar, onTitleClick = { t -> nav.navigate(Routes.detail(t.mediaType, t.id)) })
                }
                Spacer(Modifier.height(32.dp))
            }

            if (showRateDialog) {
                RateDialog(
                    titleName = b.title.name,
                    mediaType = b.title.mediaType,
                    tmdbId = b.title.id,
                    posterPath = b.title.posterPath,
                    initialRating = myRating,
                    onDismiss = { showRateDialog = false },
                )
            }

            if (showDownloadDialog && b.title.mediaType == "movie") {
                val dvm: DownloadViewModel = viewModel(
                    key = "dl-${b.title.id}",
                    factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                            DownloadViewModel("movie", b.title.id) as T
                    },
                )
                DownloadDialog(
                    titleName = b.title.name,
                    year = b.title.year,
                    posterPath = b.title.posterPath,
                    onDismiss = { showDownloadDialog = false },
                    vm = dvm,
                )
            }
        }
    }
}

@Composable
private fun FilterChipSmall(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun GenresLine(b: com.Moovie.app.data.repo.DetailBundle) {
    if (b.genres.isEmpty()) return
    Text(
        b.genres.joinToString(" · ") { it.name ?: "" },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun OverviewSection(b: com.Moovie.app.data.repo.DetailBundle) {
    Column {
        if (b.tagline?.isNotBlank() == true) {
            Text(
                "\"${b.tagline}\"",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            b.title.overview ?: "No synopsis available.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun HeroSection(b: com.Moovie.app.data.repo.DetailBundle, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        AsyncImage(
            model = TmdbClient.backdropUrl(b.title.backdropPath ?: b.title.posterPath, "w780"),
            contentDescription = b.title.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(
                Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))
            ),
        )
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 32.dp, start = 4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
        }
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            AsyncImage(
                model = TmdbClient.posterUrl(b.title.posterPath, "w185"),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(80.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(b.title.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildString {
                            val bits = mutableListOf<String>()
                            b.title.year?.let { bits.add(it.toString()) }
                            b.runtimeMinutes?.let { bits.add("${it / 60}h ${it % 60}m") }
                            b.seasons?.let { bits.add("$it seasons") }
                            b.certification?.let { bits.add(it) }
                            append(bits.joinToString(" · "))
                            append(if (bits.isEmpty()) "" else " · ")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Icon(
                        Icons.Filled.Star,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        " %.1f".format(b.title.rating),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
