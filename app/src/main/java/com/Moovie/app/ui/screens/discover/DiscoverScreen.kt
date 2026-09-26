package com.Moovie.app.ui.screens.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.model.Title
import com.Moovie.app.data.repo.Moods
import com.Moovie.app.ui.components.ErrorBox
import com.Moovie.app.ui.components.LoadingBox
import com.Moovie.app.ui.components.PosterCard
import com.Moovie.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class TmdbGenreRef(val id: Int, val name: String)

class DiscoverViewModel : ViewModel() {
    val genres = MutableStateFlow<List<TmdbGenreRef>>(emptyList())
    val results = MutableStateFlow<List<Title>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val header = MutableStateFlow("🔥 Trending now")
    val selectedMood = MutableStateFlow<String?>(null)
    val selectedGenre = MutableStateFlow<Int?>(null)

    init {
        viewModelScope.launch {
            genres.value = runCatching {
                ServiceLocator.movies.genres().map { TmdbGenreRef(it.id, it.name ?: "") }
            }.getOrDefault(emptyList())
        }
        loadDefault()
    }

    /** Fills the grid with trending titles so the page is never blank. */
    fun loadDefault() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            header.value = "🔥 Trending now"
            selectedMood.value = null
            selectedGenre.value = null
            try {
                val movies = ServiceLocator.movies.trendingWeek()
                val tv = runCatching { ServiceLocator.movies.trendingTvWeek() }.getOrDefault(emptyList())
                results.value = (movies + tv).distinctBy { "${it.mediaType}-${it.id}" }
            } catch (e: Exception) {
                error.value = e.message
            }
            loading.value = false
        }
    }

    /** Tapping the active mood again clears back to the default list. */
    fun toggleMood(id: String) {
        if (selectedMood.value == id) { loadDefault(); return }
        viewModelScope.launch {
            loading.value = true
            error.value = null
            selectedMood.value = id
            selectedGenre.value = null
            header.value = Moods.ALL.firstOrNull { it.id == id }?.let { "${it.emoji} ${it.label}" } ?: "For you"
            try {
                results.value = ServiceLocator.movies.byMood(id)
            } catch (e: Exception) {
                error.value = e.message
            }
            loading.value = false
        }
    }

    fun toggleGenre(id: Int, name: String) {
        if (selectedGenre.value == id) { loadDefault(); return }
        viewModelScope.launch {
            loading.value = true
            error.value = null
            selectedGenre.value = id
            selectedMood.value = null
            header.value = name
            try {
                results.value = ServiceLocator.movies.byGenre(id)
            } catch (e: Exception) {
                error.value = e.message
            }
            loading.value = false
        }
    }

    fun surprise(nav: NavController) {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                val pick = ServiceLocator.movies.surpriseMe()
                loading.value = false
                nav.navigate(Routes.detail(pick.mediaType, pick.id))
            } catch (e: Exception) {
                error.value = e.message
                loading.value = false
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(nav: NavController, vm: DiscoverViewModel = viewModel()) {
    val genres by vm.genres.collectAsState()
    val results by vm.results.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val header by vm.header.collectAsState()
    val selectedMood by vm.selectedMood.collectAsState()
    val selectedGenre by vm.selectedGenre.collectAsState()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Discover", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    if (selectedMood != null || selectedGenre != null) {
                        TextButton(onClick = { vm.loadDefault() }) { Text("Reset") }
                    }
                }
                Spacer(Modifier.height(4.dp))

                Text("How are you feeling?", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Moods.ALL.forEach { m ->
                        FilterChip(
                            selected = selectedMood == m.id,
                            onClick = { vm.toggleMood(m.id) },
                            label = { Text("${m.emoji} ${m.label}") },
                        )
                    }
                }

                Text("Browse by Genre", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    genres.forEach { g ->
                        FilterChip(
                            selected = selectedGenre == g.id,
                            onClick = { vm.toggleGenre(g.id, g.name) },
                            label = { Text(g.name) },
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Button(onClick = { vm.surprise(nav) }, modifier = Modifier.fillMaxWidth()) {
                    Text("🎲 Surprise Me")
                }
                Spacer(Modifier.height(12.dp))
                Text(header, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                if (loading) LoadingBox()
                error?.let { ErrorBox(it) }
                if (!loading && error == null && results.isEmpty()) {
                    Text(
                        "Nothing here yet — try another mood or genre.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        items(results, key = { "${it.mediaType}-${it.id}" }) { t ->
            PosterCard(t) { nav.navigate(Routes.detail(t.mediaType, t.id)) }
        }
    }
}
