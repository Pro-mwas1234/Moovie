package com.Moovie.app.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.model.Title
import com.Moovie.app.data.model.TmdbMultiResult
import com.Moovie.app.data.remote.TmdbApi
import com.Moovie.app.data.remote.TmdbClient
import com.Moovie.app.ui.components.PosterCard
import com.Moovie.app.ui.navigation.Routes
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class SearchUi(
    val query: String = "",
    val tab: Int = 0, // 0 All, 1 Movies, 2 TV, 3 People
    val results: List<Title> = emptyList(),
    val people: List<SearchPersonUi> = emptyList(),
    val genreId: Int? = null,
    val year: Int? = null,
    val minRating: Double? = null,
    val loading: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
)

data class SearchPersonUi(val id: Int, val name: String, val profilePath: String?, val knownFor: List<Title>)

/** Maps a TMDB "known for" entry to a navigable UI [Title] (movies and TV only). */
private fun knownForTitle(r: TmdbMultiResult): Title? {
    val type = r.mediaType
    if (type != "movie" && type != "tv") return null
    return Title(
        id = r.id,
        mediaType = type,
        name = r.displayTitle ?: "Untitled",
        overview = r.overview,
        posterPath = r.posterPath,
        backdropPath = null,
        year = r.displayDate?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull(),
        rating = r.voteAverage,
    )
}

class SearchViewModel : ViewModel() {
    val state = MutableStateFlow(SearchUi())
    val recentSearches = MutableStateFlow<List<String>>(emptyList())
    private val queryFlow = MutableStateFlow("")

    init {
        @OptIn(FlowPreview::class)
        queryFlow.debounce(300).onEach { q ->
            if (q.length >= 2) executeSearch(q) else resetResults()
        }.launchIn(viewModelScope)

        viewModelScope.launch {
            ServiceLocator.prefs.prefs.collect {
                recentSearches.value = it.recentSearches
            }
        }
    }

    fun onQueryChange(q: String) {
        state.value = state.value.copy(query = q)
        queryFlow.value = q
    }

    fun setTab(tab: Int) {
        state.value = state.value.copy(tab = tab)
        if (state.value.query.length >= 2) executeSearch(state.value.query)
    }

    fun setFilter(genreId: Int?, year: Int?, minRating: Double?) {
        state.value = state.value.copy(genreId = genreId, year = year, minRating = minRating)
        if (state.value.query.length >= 2) executeSearch(state.value.query)
    }

    private fun resetResults() {
        state.value = state.value.copy(results = emptyList(), people = emptyList(), searched = false, error = null)
    }

    private fun executeSearch(q: String) {
        viewModelScope.launch {
            state.value = state.value.copy(loading = true, error = null)
            try {
                val s = state.value
                when (s.tab) {
                    1 -> state.value = s.copy(
                        results = ServiceLocator.movies.searchMoviesOnly(q),
                        people = emptyList(), loading = false, searched = true,
                    )
                    2 -> state.value = s.copy(
                        results = ServiceLocator.movies.searchTvOnly(q),
                        people = emptyList(), loading = false, searched = true,
                    )
                    3 -> {
                        val people = ServiceLocator.movies.searchPeopleOnly(q).map { p ->
                            SearchPersonUi(
                                id = p.id,
                                name = p.name ?: "",
                                profilePath = p.profilePath,
                                knownFor = p.knownFor.mapNotNull { knownForTitle(it) },
                            )
                        }
                        state.value = s.copy(results = emptyList(), people = people, loading = false, searched = true)
                    }
                    else -> {
                        var results = ServiceLocator.movies.search(q)
                        val needsFilter = s.genreId != null || s.year != null || s.minRating != null
                        if (needsFilter && results.size < 6) {
                            results = ServiceLocator.movies.filterTitles(s.genreId, s.year, s.minRating)
                        } else if (needsFilter) {
                            results = results.filter { t ->
                                (s.genreId == null || s.genreId in t.genreIds) &&
                                    (s.year == null || t.year == s.year) &&
                                    (s.minRating == null || t.rating >= s.minRating!!)
                            }
                        }
                        state.value = s.copy(results = results, people = emptyList(), loading = false, searched = true)
                        ServiceLocator.prefs.addRecentSearch(q)
                        ServiceLocator.prefs.addTrendingSearch(q)
                    }
                }
            } catch (e: Exception) {
                state.value = state.value.copy(loading = false, error = "Search failed: ${e.message}")
            }
        }
    }
}

@Composable
fun SearchScreen(nav: NavController, vm: SearchViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val recents by vm.recentSearches.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search movies, TV, people…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Filled.Close, "Clear") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )

        // Type toggle
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("All", "Movies", "TV", "People").forEachIndexed { i, label ->
                FilterChip(
                    selected = state.tab == i,
                    onClick = { vm.setTab(i) },
                    label = { Text(label) },
                )
            }
        }

        // Filters
        FilterBar(selectedGenre = state.genreId, selectedYear = state.year, selectedRating = state.minRating, onChange = vm::setFilter)

        when {
            state.loading -> {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.padding(32.dp).align(Alignment.CenterHorizontally),
                )
            }

            !state.searched -> {
                if (recents.isNotEmpty()) {
                    Text("Recent", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    recents.take(8).forEach { q ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.onQueryChange(q) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(q, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }

            state.error != null -> Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))

            state.tab == 3 -> PeopleGrid(state.people, nav)

            else -> {
                if (state.results.isEmpty()) {
                    Text(
                        "No results for \"${state.query}\"",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(110.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.results, key = { "${it.mediaType}-${it.id}" }) { t ->
                            PosterCard(t) { nav.navigate(Routes.detail(t.mediaType, t.id)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBar(
    selectedGenre: Int?,
    selectedYear: Int?,
    selectedRating: Double?,
    onChange: (Int?, Int?, Double?) -> Unit,
) {
    val years = listOf(2026, 2025, 2024, 2023, 2020, 2015, 2010, 2000, 1990)
    val ratings = listOf(9.0, 8.0, 7.0, 6.0)
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = selectedYear != null,
                onClick = {
                    val next = when (selectedYear) {
                        null -> years.first()
                        else -> years.getOrNull(years.indexOf(selectedYear) + 1)
                    }
                    onChange(selectedGenre, next, selectedRating)
                },
                label = { Text(selectedYear?.toString() ?: "Year") },
            )
            FilterChip(
                selected = selectedRating != null,
                onClick = {
                    val next = when (selectedRating) {
                        null -> ratings.first()
                        else -> ratings.getOrNull(ratings.indexOf(selectedRating) + 1)
                    }
                    onChange(selectedGenre, selectedYear, next)
                },
                label = { Text(if (selectedRating != null) "${selectedRating}+" else "Rated") },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Star,
                        null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            val hasFilter = selectedGenre != null || selectedYear != null || selectedRating != null
            if (hasFilter) {
                FilterChip(
                    selected = false,
                    onClick = { onChange(null, null, null) },
                    label = { Text("Clear") },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(28 to "Action", 35 to "Comedy", 27 to "Horror", 878 to "Sci-Fi").forEach { (id, label) ->
                FilterChip(
                    selected = selectedGenre == id,
                    onClick = { onChange(if (selectedGenre == id) null else id, selectedYear, selectedRating) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun PeopleGrid(people: List<SearchPersonUi>, nav: NavController) {
    if (people.isEmpty()) {
        Text("No people found", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        items(people, key = { it.id }) { p ->
            Column {
                AsyncImage(
                    model = TmdbClient.profileUrl(p.profilePath),
                    contentDescription = p.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(100.dp)
                        .clip(RoundedCornerShape(50)),
                )
                Text(p.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text(
                    p.knownFor.firstOrNull()?.name ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
