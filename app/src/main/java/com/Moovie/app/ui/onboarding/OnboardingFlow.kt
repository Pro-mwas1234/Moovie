package com.Moovie.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.model.TmdbGenre
import kotlinx.coroutines.launch

class OnboardingViewModel : ViewModel() {
    var genres by mutableStateOf<List<TmdbGenre>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set

    init {
        viewModelScope.launch {
            genres = runCatching { ServiceLocator.movies.genres() }.getOrDefault(emptyList())
            loading = false
        }
    }

    fun finish(selectedGenreIds: Set<Int>, onDone: () -> Unit) {
        viewModelScope.launch {
            ServiceLocator.auth.continueAsGuest()
            ServiceLocator.prefs.setOnboarded(
                genreIds = selectedGenreIds.map { it.toString() }.toSet(),
                region = java.util.Locale.getDefault().country.ifBlank { "US" },
            )
            onDone()
        }
    }
}

private data class Slide(val emoji: String, val headline: String, val body: String)

@Composable
fun OnboardingFlow(vm: OnboardingViewModel = viewModel()) {
    val slides = listOf(
        Slide("🎬", "Every movie, one place", "Trending, new releases, hidden gems — browse everything and keep your watchlist in sync."),
        Slide("🔍", "Find your vibe", "Search, filter, or just say \"something like Inception but funnier\"."),
        Slide("🍿", "Track what you watch", "Want / Watching / Watched. Rate, review, and see your year in film."),
    )
    var step by remember { mutableIntStateOf(0) } // 0..2 slides, 3 = genres, 4 = done
    var selected by remember { mutableStateOf(setOf<Int>()) }
    val scope = rememberCoroutineScope()

    AnimatedContent(targetState = step, label = "onboarding") { s ->
        when {
            s < slides.size -> {
                val slide = slides[s]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(slide.emoji, fontSize = 72.sp)
                    Spacer(Modifier.height(24.dp))
                    Text(slide.headline, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        slide.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(40.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        slides.indices.forEach { i ->
                            Box(
                                modifier = Modifier
                                    .size(if (i == s) 24.dp else 8.dp, 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == s) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline
                                    ),
                            )
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = { step++ }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (s == slides.size - 1) "Pick my genres" else "Next")
                    }
                }
            }

            s == slides.size -> {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text("What do you love?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Pick a few genres — we'll personalize your home.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    if (vm.loading) {
                        CircularProgressIndicator()
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(100.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            items(vm.genres, key = { it.id }) { g ->
                                FilterChip(
                                    selected = g.id in selected,
                                    onClick = {
                                        selected = if (g.id in selected) selected - g.id else selected + g.id
                                    },
                                    label = { Text(g.name ?: "") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { vm.finish(selected) { step = slides.size + 1 } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Get Started 🍿") }
                }
            }
        }
    }
}
