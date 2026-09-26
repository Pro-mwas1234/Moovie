package com.Moovie.app.ui.screens.recs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.Moovie.app.ServiceLocator
import com.Moovie.app.ai.RecommendEngine
import com.Moovie.app.ui.components.PosterCard
import com.Moovie.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AiRecsViewModel : ViewModel() {
    data class Turn(
        val question: String,
        val reply: RecommendEngine.Reply? = null,
        val loading: Boolean = false,
        val error: String? = null,
    )

    val history = MutableStateFlow<List<Turn>>(emptyList())
    val input = MutableStateFlow("")

    fun ask(prompt: String) {
        if (prompt.isBlank()) return
        input.value = ""
        history.value = history.value + Turn(question = prompt, loading = true)
        viewModelScope.launch {
            try {
                val reply = ServiceLocator.recommend.recommend(prompt)
                history.value = history.value.dropLast(1) + Turn(question = prompt, reply = reply)
            } catch (e: Exception) {
                history.value = history.value.dropLast(1) + Turn(question = prompt, error = e.message)
            }
        }
    }
}

@Composable
fun AiRecsScreen(nav: NavController, vm: AiRecsViewModel = viewModel()) {
    val history by vm.history.collectAsState()
    val input by vm.input.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column {
                Text("AI Recommend", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "\"something like Inception but funnier\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            if (history.isEmpty()) {
                Text(
                    "Tell me a vibe and I'll find matches 🍿",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 24.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    listOf(
                        "Something funny and short",
                        "Sci-fi that bends my mind",
                        "A horror movie for date night",
                    ).forEach { s ->
                        Button(onClick = { vm.ask(s) }) { Text(s, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            history.forEach { turn ->
                Text(
                    "You: ${turn.question}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp),
                )
                when {
                    turn.loading -> Text("Picking titles…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    turn.error != null -> Text(turn.error!!, color = MaterialTheme.colorScheme.error)
                    turn.reply != null -> {
                        Text(turn.reply.intro, modifier = Modifier.padding(top = 4.dp))
                        LazyRow(
                            contentPadding = PaddingValues(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(turn.reply.suggestions, key = { it.rawName }) { s ->
                                val title = s.title
                                if (title != null) {
                                    PosterCard(title) {
                                        nav.navigate(Routes.detail(title.mediaType, title.id))
                                    }
                                } else {
                                    Text(s.rawName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { vm.input.value = it },
                placeholder = { Text("Describe what you're in the mood for…") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            IconButton(
                enabled = input.isNotBlank(),
                onClick = { vm.ask(input.trim()) },
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.primary) }
        }
    }
}
