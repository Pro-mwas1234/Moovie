package com.Moovie.app.ui.screens.social

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.Moovie.app.data.model.ReviewPost
import com.Moovie.app.data.model.UserProfile
import com.Moovie.app.data.remote.TmdbClient
import com.Moovie.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SocialViewModel : ViewModel() {
    val feed = MutableStateFlow<List<ReviewPost>>(emptyList())
    val searchResults = MutableStateFlow<List<UserProfile>>(emptyList())
    val following = MutableStateFlow<Set<String>>(emptySet())
    val query = MutableStateFlow("")

    private val uid get() = ServiceLocator.auth.uid

    init {
        val u = uid
        if (u != null) {
            viewModelScope.launch {
                ServiceLocator.social.feed(u).collect { feed.value = it }
            }
            viewModelScope.launch {
                ServiceLocator.social.following(u).collect { following.value = it.toSet() }
            }
        }
    }

    fun onQuery(q: String) {
        query.value = q
        viewModelScope.launch {
            searchResults.value = if (q.length >= 2) ServiceLocator.social.searchProfiles(q) else emptyList()
        }
    }

    fun toggleFollow(target: String) {
        val u = uid ?: return
        viewModelScope.launch {
            val follow = target !in following.value
            ServiceLocator.social.setFollowing(u, target, follow)
            following.value = if (follow) following.value + target else following.value - target
        }
    }
}

@Composable
fun SocialScreen(nav: NavController, vm: SocialViewModel = viewModel()) {
    val feed by vm.feed.collectAsState()
    val results by vm.searchResults.collectAsState()
    val following by vm.following.collectAsState()
    val query by vm.query.collectAsState()

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text("Friends & Feed", style = MaterialTheme.typography.headlineSmall)
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = vm::onQuery,
                placeholder = { Text("Find friends by name…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
            )
        }
        if (results.isNotEmpty()) {
            items(results.size) { i ->
                val person = results[i]
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = null,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(person.name.ifBlank { "User ${person.uid.take(6)}" })
                        Text(
                            "@${person.handle.ifBlank { person.uid.take(8) }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = { vm.toggleFollow(person.uid) }) {
                        Text(if (person.uid in following) "Following" else "Follow")
                    }
                }
            }
        }
        item {
            Text(
                "Recent reviews",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (feed.isEmpty()) {
            item {
                Text(
                    "No activity yet. Follow friends or post a review from any movie page.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else {
            items(feed.size) { i ->
                val review = feed[i]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = TmdbClient.posterUrl(review.posterPath, "w92"),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp, 66.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(review.authorName)
                            Icon(
                                Icons.Filled.Star,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .size(12.dp),
                            )
                            Text(" ${review.rating ?: "-"}")
                        }
                        Text(
                            review.titleName,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (review.text.isNotBlank()) {
                            Text(
                                review.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                            )
                        }
                    }
                    TextButton(onClick = {
                        nav.navigate(Routes.detail(review.mediaType, review.tmdbId))
                    }) { Text("View") }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
