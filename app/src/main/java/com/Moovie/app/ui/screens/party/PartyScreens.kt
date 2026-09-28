package com.Moovie.app.ui.screens.party

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.Moovie.app.ServiceLocator
import com.Moovie.app.data.model.ChatMessage
import com.Moovie.app.data.model.WatchItem
import com.Moovie.app.data.remote.TmdbClient
import com.Moovie.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// ---------------- Intro ----------------

class PartyIntroViewModel : ViewModel() {
    val watchlist = MutableStateFlow<List<WatchItem>>(emptyList())
    val joinError = MutableStateFlow<String?>(null)
    val hostError = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            // Cloud mode needs a Firebase user for watch parties; auto guest
            // sign-in so Host/Join always work instead of silently failing.
            if (ServiceLocator.auth.uid == null) {
                ServiceLocator.auth.continueAsGuest()
            }
            ServiceLocator.auth.uid?.let { uid ->
                ServiceLocator.watchlist.watchlist(uid).collect { watchlist.value = it }
            }
        }
    }

    fun host(item: WatchItem, onCreated: (String) -> Unit) {
        hostError.value = null
        val uid = ServiceLocator.auth.uid
        if (uid == null) {
            hostError.value = "Sign in first (Guest mode works too) to host a party."
            return
        }
        viewModelScope.launch {
            busy.value = true
            ServiceLocator.watchParty.createRoom(
                hostUid = uid,
                hostName = ServiceLocator.auth.name ?: "Host",
                mediaType = item.mediaType,
                tmdbId = item.tmdbId,
                titleName = item.titleName,
                posterPath = item.posterPath,
            ).onSuccess { code -> onCreated(code) }
                .onFailure { hostError.value = it.message ?: "Couldn't create the room." }
            busy.value = false
        }
    }

    fun join(code: String, onJoined: (String) -> Unit) {
        joinError.value = null
        val uid = ServiceLocator.auth.uid
        if (uid == null) {
            joinError.value = "Sign in first (Guest mode works too) to join a party."
            return
        }
        viewModelScope.launch {
            busy.value = true
            ServiceLocator.watchParty.joinRoom(code, uid, ServiceLocator.auth.name ?: "Guest")
                .onSuccess { onJoined(it.code) }
                .onFailure { joinError.value = it.message }
            busy.value = false
        }
    }
}

@Composable
fun PartyIntroScreen(nav: NavController, vm: PartyIntroViewModel = viewModel()) {
    val watchlist by vm.watchlist.collectAsState()
    val joinError by vm.joinError.collectAsState()
    val hostError by vm.hostError.collectAsState()
    val busy by vm.busy.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Watch Party", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick something from your watchlist, share the code, and everyone presses play together. Moovie syncs the play state and chat — you stream in your own apps.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        Button(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Groups, null)
            Text("  Host a party")
        }
        hostError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().take(6) },
            label = { Text("Join code") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        joinError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = code.length == 6 && !busy,
            onClick = { vm.join(code) { c -> nav.navigate(Routes.partyRoom(c)) } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Join") }

        if (watchlist.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Save a few titles to your watchlist first — then host a party from here.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Pick what to watch") },
            text = {
                LazyColumn(modifier = Modifier.size(width = 320.dp, height = 380.dp)) {
                    items(watchlist, key = { it.key }) { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                        ) {
                            AsyncImage(
                                model = TmdbClient.posterUrl(item.posterPath, "w92"),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(40.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(6.dp)),
                            )
                            Text(
                                item.titleName,
                                modifier = Modifier.weight(1f).padding(start = 10.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = {
                                showPicker = false
                                vm.host(item) { c -> nav.navigate(Routes.partyRoom(c)) }
                            }) { Text("Host") }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        )
    }
}

// ---------------- Room ----------------

class PartyRoomViewModel : ViewModel() {
    val state = MutableStateFlow<com.Moovie.app.data.model.PartyRoomState?>(null)
    val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val roomError = MutableStateFlow<String?>(null)
    var code: String = ""

    fun bind(roomCode: String) {
        if (code == roomCode) return
        code = roomCode
        viewModelScope.launch {
            runCatching {
                ServiceLocator.watchParty.roomState(roomCode).collect { state.value = it }
            }.onFailure { roomError.value = it.message }
        }
        viewModelScope.launch {
            runCatching {
                ServiceLocator.watchParty.chat(roomCode).collect { messages.value = it }
            }.onFailure { roomError.value = it.message }
        }
    }

    fun setPlaying(playing: Boolean, positionSeconds: Long) {
        viewModelScope.launch {
            ServiceLocator.watchParty.setPlaybackState(code, playing, positionSeconds)
        }
    }

    fun send(text: String) {
        val uid = ServiceLocator.auth.uid ?: return
        viewModelScope.launch {
            ServiceLocator.watchParty.sendChat(code, uid, ServiceLocator.auth.name ?: "Me", text)
        }
    }
}

@Composable
fun PartyRoomScreen(
    nav: NavController,
    roomCode: String,
    vm: PartyRoomViewModel = viewModel(),
) {
    // The code comes from THIS screen's back stack entry (wired in RootNav),
    // not currentBackStackEntry — the latter returns whatever screen is on top
    // (e.g. the player after "Watch now"), whose args have no code → the old
    // bind("") crashed on Firestore with an empty document path.
    if (roomCode.isNotBlank()) {
        androidx.compose.runtime.LaunchedEffect(roomCode) { vm.bind(roomCode) }
    }
    val state by vm.state.collectAsState()
    val messages by vm.messages.collectAsState()
    val roomError by vm.roomError.collectAsState()
    var chatInput by remember { mutableStateOf("") }
    // Ticks every second so the shared clock re-reads the room's timestamp.
    var tick by remember { mutableStateOf(0L) }
    val room = state?.room
    val isHost = room?.hostUid == ServiceLocator.auth.uid

    // One shared position derived from the host's last broadcast, so every guest
    // sees the same timeline instead of an independent local counter.
    val sharedPosition = remember(room?.positionSeconds, room?.stateUpdatedAt, room?.playing, tick) {
        val base = room?.positionSeconds ?: 0L
        val updatedMs = (room?.stateUpdatedAt?.seconds ?: 0L) * 1000L
        if (room?.playing == true && updatedMs > 0L) {
            base + ((System.currentTimeMillis() - updatedMs) / 1000L).coerceAtLeast(0L)
        } else {
            base
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column {
                Text(room?.titleName ?: "Watch Party", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Code: ${room?.code ?: roomCode} · ${room?.participants?.size ?: 1} here",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = TmdbClient.backdropUrl(room?.posterPath, "w780"),
                contentDescription = room?.titleName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isHost) {
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            val next = !(room?.playing ?: false)
                            vm.setPlaying(next, sharedPosition)
                        },
                    ) {
                        Icon(
                            if (room?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            "Play/Pause",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(formatTime(sharedPosition), color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Text(
                    if (room?.playing == true) "Playing — press play in your app!" else "Paused",
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Keeps the shared clock re-reading the room's timestamp every second.
        androidx.compose.runtime.LaunchedEffect(room?.playing) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                tick++
            }
        }

        // Everyone can hop into the in-app player at the room's title.
        Button(
            onClick = {
                if (room?.mediaType == "tv") {
                    nav.navigate(Routes.series(room.tmdbId))
                } else {
                    nav.navigate(Routes.player("movie", room?.tmdbId ?: 0))
                }
            },
            enabled = (room?.tmdbId ?: 0) > 0,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, null)
            Text(" Watch now")
        }

        roomError?.let {
            Text(
                "Party connection issue: $it",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Text(
            "Chat",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                Column {
                    Text(
                        msg.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = chatInput,
                onValueChange = { chatInput = it },
                placeholder = { Text("Type a message…") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            IconButton(enabled = chatInput.isNotBlank(), onClick = {
                vm.send(chatInput.trim())
                chatInput = ""
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
