package com.Moovie.app.ui.screens.trivia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.Moovie.app.trivia.Question
import com.Moovie.app.trivia.TriviaBank
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class TriviaState(
    val questions: List<Question> = emptyList(),
    val index: Int = 0,
    val score: Int = 0,
    val picked: Int? = null,
    val finished: Boolean = false,
)

class TriviaViewModel : ViewModel() {
    val state = MutableStateFlow(TriviaState())

    init {
        start()
    }

    fun start() {
        viewModelScope.launch {
            val titles = runCatching { ServiceLocator.movies.popular().take(10) }.getOrDefault(emptyList())
            state.value = TriviaState(questions = TriviaBank.draw(roundSize = 6, titles = titles))
        }
    }

    fun pick(optionIndex: Int) {
        val s = state.value
        if (s.picked != null || s.finished) return
        val correct = s.questions[s.index].correctIndex == optionIndex
        state.value = s.copy(picked = optionIndex, score = if (correct) s.score + 1 else s.score)
    }

    fun next() {
        val s = state.value
        if (s.index + 1 >= s.questions.size) {
            state.value = s.copy(finished = true)
        } else {
            state.value = s.copy(index = s.index + 1, picked = null)
        }
    }
}

@Composable
fun TriviaScreen(nav: NavController, vm: TriviaViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.finished) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.EmojiEvents,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        "  Round over!",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                Text(
                    "You scored ${state.score} / ${state.questions.size}",
                    style = MaterialTheme.typography.titleLarge,
                )
                Button(onClick = { vm.start() }) { Text("Play again") }
                OutlinedButton(onClick = { nav.popBackStack() }) { Text("Done") }
            }
        } else {
            val q = state.questions.getOrNull(state.index) ?: return@Column
            LinearProgressIndicator(
                progress = { (state.index) / state.questions.size.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Question ${state.index + 1} of ${state.questions.size} · Score ${state.score}")
            Text(q.text, style = MaterialTheme.typography.titleMedium)
            q.options.forEachIndexed { i, option ->
                val isCorrect = i == q.correctIndex
                val picked = state.picked == i
                val revealed = state.picked != null
                OutlinedButton(
                    onClick = { vm.pick(i) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !revealed,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (revealed && isCorrect) {
                            Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                        } else if (revealed && picked) {
                            Icon(Icons.Filled.Cancel, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(option)
                    }
                }
            }
            if (state.picked != null) {
                Button(onClick = { vm.next() }, modifier = Modifier.fillMaxWidth()) { Text("Next") }
            }
        }
    }
}
