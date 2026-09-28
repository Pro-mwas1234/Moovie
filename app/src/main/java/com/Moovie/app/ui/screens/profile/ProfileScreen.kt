package com.Moovie.app.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.Moovie.app.data.model.ReviewPost
import com.Moovie.app.data.model.Title
import com.Moovie.app.data.model.WatchItem
import com.Moovie.app.data.repo.WatchStats
import com.Moovie.app.data.remote.TmdbClient
import com.Moovie.app.ui.components.MovieRow
import com.Moovie.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {
    val user = ServiceLocator.auth.user
    val stats = MutableStateFlow(WatchStats())
    val myReviews = MutableStateFlow<List<ReviewPost>>(emptyList())
    val favorites = MutableStateFlow<List<WatchItem>>(emptyList())

    init {
        viewModelScope.launch {
            // ensureUid mints a guest session when none exists, so stats work
            // before sign-in too (the old plain-uid check silently showed all
            // zeroes for guests).
            val uid = ServiceLocator.auth.ensureUid() ?: return@launch
            launch {
                ServiceLocator.watchlist.watchlist(uid).collect { items ->
                    favorites.value = items.filter { it.rating >= 4.0 || it.status == WatchItem.STATUS_WATCHED }
                    stats.value = ServiceLocator.watchlist.stats(items, myReviews.value.size)
                }
            }
            launch {
                ServiceLocator.social.myReviews(uid).collect { reviews ->
                    myReviews.value = reviews
                    stats.value = stats.value.copy(reviewCount = reviews.size)
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(nav: NavController, vm: ProfileViewModel = viewModel()) {
    val accountState by vm.user.collectAsState()
    val account = accountState
    val stats by vm.stats.collectAsState()
    val reviews by vm.myReviews.collectAsState()

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Filled.Settings, "Settings")
                    }
                }
                AsyncImage(
                    model = null,
                    contentDescription = null,
                    modifier = Modifier.size(88.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    account?.name ?: ServiceLocator.auth.name ?: "You",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (account != null && !account.isAnonymous) account.email ?: "Moovie member"
                    else "Moovie member",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                if (account == null || account.isAnonymous) {
                    Button(onClick = { nav.navigate(Routes.AUTH) }) {
                        Text(if (account == null) "Sign in" else "Sign in to sync")
                    }
                } else {
                    TextButton(onClick = { nav.navigate(Routes.AUTH) }) {
                        Text("Manage account")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard("${stats.watchedCount}", "Movies", Modifier.weight(1f))
                StatCard("${stats.hoursWatched}h", "Watched", Modifier.weight(1f))
                StatCard("${stats.reviewCount}", "Reviews", Modifier.weight(1f))
            }
        }

        item {
            Text(
                stats.favoriteGenre?.let { "Favorite genre: $it" } ?: "Rate some movies to find your favorite genre",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedButton(onClick = { nav.navigate(Routes.DOWNLOADS) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Download, null)
                    Text("  Downloads")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { nav.navigate(Routes.SOCIAL) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Groups, null)
                    Text("  Friends & Feed")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { nav.navigate(Routes.AI_RECS) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Psychology, null)
                    Text("  AI Recommend")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { nav.navigate(Routes.TRIVIA) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.Send, null)
                    Text("  Trivia Night")
                }
            }
        }

        if (reviews.isNotEmpty()) {
            item {
                Text(
                    "My Ratings",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(reviews.size) { i ->
                val r = reviews[i]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = TmdbClient.posterUrl(r.posterPath, "w92"),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp, 66.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(r.titleName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            r.text.ifBlank { "Rated without review" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Star,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            " ${r.rating ?: "-"}",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
