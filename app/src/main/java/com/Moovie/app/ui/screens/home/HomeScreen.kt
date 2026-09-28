package com.Moovie.app.ui.screens.home

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.Moovie.app.data.model.Title
import com.Moovie.app.data.model.WatchItem
import com.Moovie.app.data.repo.GenreNames
import com.Moovie.app.data.remote.TmdbClient
import com.Moovie.app.ui.components.ContinueWatchingRow
import com.Moovie.app.ui.components.MovieRow
import com.Moovie.app.ui.navigation.Routes
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class HomeUi(
    val hero: Title? = null,
    val trending: List<Title> = emptyList(),
    val newReleases: List<Title> = emptyList(),
    val top10: List<Title> = emptyList(),
    val trendingTv: List<Title> = emptyList(),
    val popular: List<Title> = emptyList(),
    val comingSoon: List<Title> = emptyList(),
    val topTv: List<Title> = emptyList(),
    val becauseYouLiked: Pair<String, List<Title>>? = null,
    val error: String? = null,
)

class HomeViewModel : ViewModel() {
    val state = MutableStateFlow(HomeUi())
    val continueWatching = MutableStateFlow<List<WatchItem>>(emptyList())
    val watchlistAll = MutableStateFlow<List<WatchItem>>(emptyList())
    private val loadedSeeds = MutableStateFlow<Set<Int>>(emptySet())

    init {
        refresh()
        observeWatchlist()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                coroutineScope {
                    val hero = async { ServiceLocator.movies.hero() }
                    val trending = async { ServiceLocator.movies.trendingToday() }
                    val fresh = async { ServiceLocator.movies.newReleases() }
                    val top = async { ServiceLocator.movies.trendingWeek() }
                    val tv = async { runCatching { ServiceLocator.movies.trendingTvWeek() }.getOrDefault(emptyList()) }
                    val popular = async { runCatching { ServiceLocator.movies.popular() }.getOrDefault(emptyList()) }
                    val soon = async { runCatching { ServiceLocator.movies.upcoming() }.getOrDefault(emptyList()) }
                    val topTv = async { runCatching { ServiceLocator.movies.topRatedTv() }.getOrDefault(emptyList()) }
                    state.value = HomeUi(
                        hero = hero.await(),
                        trending = trending.await(),
                        newReleases = fresh.await(),
                        top10 = top.await().take(10),
                        trendingTv = tv.await().take(20),
                        popular = popular.await().take(20),
                        comingSoon = soon.await().take(20),
                        topTv = topTv.await().take(20),
                    )
                }
                loadBecauseYouLiked()
            } catch (e: Exception) {
                state.value = state.value.copy(error = "Couldn't load movies. Check the TMDB key in local.properties. (${e.message})")
            }
        }
    }

    private suspend fun loadBecauseYouLiked() {
        val uid = ServiceLocator.auth.uid ?: return
        val items = ServiceLocator.watchlist.watchlistOnce(uid)
        val seed = items.filter { it.rating >= 4.0 || it.status == WatchItem.STATUS_WATCHED }
            .maxByOrNull { it.rating }
        if (seed == null || seed.tmdbId in loadedSeeds.value) return
        loadedSeeds.value = loadedSeeds.value + seed.tmdbId
        val titles = runCatching {
            ServiceLocator.movies.becauseYouLiked(
                Title(seed.tmdbId, seed.mediaType, seed.titleName, null, seed.posterPath, seed.backdropPath, seed.year, seed.rating, seed.genreIds)
            )
        }.getOrDefault(emptyList())
        if (titles.isNotEmpty()) {
            val cur = state.value
            state.value = cur.copy(becauseYouLiked = "Because you liked ${seed.titleName}" to titles)
        }
    }

    private fun observeWatchlist() {
        val uid = ServiceLocator.auth.uid ?: return
        viewModelScope.launch {
            ServiceLocator.watchlist.watchlist(uid).collect { items ->
                watchlistAll.value = items
                continueWatching.value = items.filter {
                    // In-progress only: once a title is Watched it drops off the row.
                    it.status != WatchItem.STATUS_WATCHED &&
                        (it.status == WatchItem.STATUS_WATCHING || it.progressMinutes != null)
                }
            }
        }
    }

    fun addToWatchlist(item: WatchItem, onResult: (Boolean) -> Unit) {
        val uid = ServiceLocator.auth.uid ?: return onResult(false)
        viewModelScope.launch {
            onResult(ServiceLocator.watchlist.addOrUpdate(uid, item).isSuccess)
        }
    }
}

@Composable
fun HomeScreen(nav: NavController, vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val continueWatching by vm.continueWatching.collectAsState()
    val watchlist by vm.watchlistAll.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            HomeTopBar(
                onProfile = { nav.navigate(Routes.PROFILE) { launchSingleTop = true } },
                onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
                onSearch = { nav.navigate(Routes.SEARCH) { launchSingleTop = true } },
            )
        }

        state.error?.let { msg ->
            item {
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        state.hero?.let { hero ->
            item {
                HeroCard(
                    title = hero,
                    inWatchlist = watchlist.any { it.tmdbId == hero.id && it.mediaType == hero.mediaType },
                    onDetails = { nav.navigate(Routes.detail(hero.mediaType, hero.id)) },
                    onAdd = {
                        vm.addToWatchlist(
                            WatchItem(
                                tmdbId = hero.id,
                                mediaType = hero.mediaType,
                                titleName = hero.name,
                                posterPath = hero.posterPath,
                                backdropPath = hero.backdropPath,
                                year = hero.year,
                                rating = hero.rating,
                                genreIds = hero.genreIds,
                                status = WatchItem.STATUS_WANT,
                            )
                        ) { ok ->
                            scope.launch { snackbar.showSnackbar(if (ok) "Added to Watchlist" else "Couldn't add") }
                        }
                    },
                )
            }
        }

        if (continueWatching.isNotEmpty()) {
            item {
                ContinueWatchingRow(
                    continueWatching,
                    onPlay = { w -> nav.navigate(Routes.player(w.mediaType, w.tmdbId, w.season ?: 1, w.episode ?: 1)) },
                )
            }
        }

        item {
            FeatureChips(
                onAi = { nav.navigate(Routes.AI_RECS) },
                onTrivia = { nav.navigate(Routes.TRIVIA) },
                onParty = { nav.navigate(Routes.PARTY_INTRO) },
                onSocial = { nav.navigate(Routes.SOCIAL) },
            )
        }

        state.becauseYouLiked?.let { (label, titles) ->
            item { MovieRow(label, titles, onTitleClick = { t -> nav.navigate(Routes.detail(t.mediaType, t.id)) }) }
        }

        item { MovieRow("Trending Now", state.trending, onTitleClick = { t -> nav.navigate(Routes.detail(t.mediaType, t.id)) }) }
        item { MovieRow("New Releases", state.newReleases, onTitleClick = { t -> nav.navigate(Routes.detail(t.mediaType, t.id)) }) }
        item { MovieRow("Trending TV Shows", state.trendingTv, onTitleClick = { t -> nav.navigate(Routes.detail(t.mediaType, t.id)) }) }
        item { MovieRow("Popular Movies", state.popular, onTitleClick = { t -> nav.navigate(Routes.detail(t.mediaType, t.id)) }) }
        item { MovieRow("Coming Soon", state.comingSoon, onTitleClick = { t -> nav.navigate(Routes.detail(t.mediaType, t.id)) }) }
        item { MovieRow("Top Rated TV", state.topTv, onTitleClick = { t -> nav.navigate(Routes.detail(t.mediaType, t.id)) }) }

        // Top 10 with big rank numbers
        if (state.top10.isNotEmpty()) {
            item {
                Column {
                    Text(
                        "Top 10 This Week",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.top10, key = { "top-${it.mediaType}-${it.id}" }) { t ->
                            Top10Card(t, state.top10.indexOf(t) + 1) {
                                nav.navigate(Routes.detail(t.mediaType, t.id))
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
    SnackbarHost(snackbar)
}

@Composable
private fun HomeTopBar(onProfile: () -> Unit, onNotifications: () -> Unit, onSearch: () -> Unit) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(ServiceLocator.auth.name ?: "there", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Search") }
        IconButton(onClick = onNotifications) { Icon(Icons.Filled.Notifications, "Notifications") }
        IconButton(onClick = onProfile) { Icon(Icons.Filled.Person, "Profile") }
    }
}

@Composable
private fun HeroCard(title: Title, inWatchlist: Boolean, onDetails: () -> Unit, onAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(20.dp)),
    ) {
        AsyncImage(
            model = TmdbClient.backdropUrl(title.backdropPath ?: title.posterPath, "w780"),
            contentDescription = title.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                        startY = 180f,
                    )
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                title.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocalFireDepartment,
                    null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    " ${"%.1f".format(title.rating)}${title.year?.let { " · $it" } ?: ""}",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDetails) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Text(" Details")
                }
                OutlinedButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, null)
                    Text(if (inWatchlist) " In Watchlist" else " Watchlist")
                }
            }
        }
    }
}

@Composable
private fun FeatureChips(onAi: () -> Unit, onTrivia: () -> Unit, onParty: () -> Unit, onSocial: () -> Unit) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(listOf(
            Triple("AI Recs", Icons.Filled.Psychology, onAi),
            Triple("Trivia", Icons.Filled.Extension, onTrivia),
            Triple("Watch Party", Icons.Filled.Groups, onParty),
            Triple("Friends", Icons.AutoMirrored.Filled.Send, onSocial),
        ), key = { it.first }) { (label, icon, action) ->
            AssistChip(onClick = action, label = { Text(label) }, leadingIcon = { Icon(icon, null) })
        }
    }
}

@Composable
private fun Top10Card(title: Title, rank: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = TmdbClient.posterUrl(title.posterPath),
                    contentDescription = title.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
