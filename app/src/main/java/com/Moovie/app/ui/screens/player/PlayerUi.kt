package com.Moovie.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import android.annotation.SuppressLint
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

@Composable
fun PlayerScreen(nav: NavController, vm: PlayerViewModel) {
    val mediaType = vm.mediaTypeForUi
    val titleName by vm.titleName.collectAsState()
    val seasons by vm.seasons.collectAsState()
    val currentSeason by vm.currentSeason.collectAsState()
    val currentEpisode by vm.currentEpisode.collectAsState()
    val episodes by vm.episodes.collectAsState()
    var showEpisodes by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    val downloaded by vm.downloaded.collectAsState()
    val directStream by vm.directStream.collectAsState()
    // Embed is the primary player; the native MP4 (OmniSave/ExoPlayer) is an
    // explicit opt-in via the bolt toggle. A downloaded local file auto-switches.
    var preferNative by remember { mutableStateOf(false) }

    val url = embedUrl(mediaType, vm.idForUi, currentSeason, currentEpisode)

    // Watch tracking: mark "Watching" on entry, "Watched" on exit, so the
    // profile stats reflect what the user actually plays.
    androidx.compose.runtime.DisposableEffect(Unit) {
        vm.startWatching()
        onDispose { vm.finishWatching() }
    }

    // Keep the screen on while the player is open (both embed and native).
    // LocalContext.current is composable-only, so read it here (not in the effect).
    val activity = androidx.compose.ui.platform.LocalContext.current.findActivity()
    androidx.compose.runtime.DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        // Top bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = androidx.compose.ui.graphics.Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    titleName.ifBlank { "Now playing" },
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (mediaType == "tv") {
                    Text(
                        "S$currentSeason · E$currentEpisode",
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            // Saves/removes the current movie or episode in the in-app Downloads list.
            IconButton(onClick = { vm.toggleDownload() }) {
                Icon(
                    if (downloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                    contentDescription = if (downloaded) "Remove download" else "Save for offline",
                    tint = if (downloaded) MaterialTheme.colorScheme.primary
                    else androidx.compose.ui.graphics.Color.White,
                )
            }
            // Toggle native MP4 (OmniSave) vs the embed.
            if (directStream != null) {
                IconButton(onClick = { preferNative = !preferNative }) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = if (preferNative) "Use embed player" else "Use native player",
                        tint = if (preferNative) MaterialTheme.colorScheme.primary
                        else androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
            if (mediaType == "tv") {
                IconButton(onClick = { showEpisodes = true }) {
                    Icon(Icons.Filled.Tune, "Episodes", tint = androidx.compose.ui.graphics.Color.White)
                }
            }
        }

        // Player: a downloaded file (content:// MediaStore URI or /file/path)
        // always wins over the online embed and plays natively; an http(s)
        // directStream is a resolved OmniSave stream, switchable via the bolt.
        val stream = directStream
        val isLocalFile = stream != null && !stream.startsWith("http")
        if (stream != null && (preferNative || isLocalFile)) {
            Column(Modifier.fillMaxWidth().weight(1f)) {
                ExoPlayerScreen(
                    url = stream,
                    modifier = Modifier.fillMaxSize(),
                    onPosition = { vm.reportPosition(it) },
                )
                if (!isLocalFile) {
                    Text(
                        "Native stream (OmniSave) — tap the bolt icon to switch back to embed",
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        } else {
            WebViewPlayer(
                url = url,
                reloadKey = reloadKey,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }

        if (mediaType == "tv") {
            // Season quick-switch row
            LazyRow(
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            ) {
                items((1..seasons).toList()) { s ->
                    FilterChip(
                        selected = s == currentSeason,
                        onClick = { vm.setSeason(s); reloadKey++ },
                        label = { Text("S$s", color = androidx.compose.ui.graphics.Color.White) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }

    if (showEpisodes) {
        AlertDialog(
            onDismissRequest = { showEpisodes = false },
            title = { Text("Episodes · Season $currentSeason") },
            text = {
                LazyColumn {
                    items(episodes, key = { it.id }) { ep ->
                        TextButton(
                            onClick = {
                                vm.setEpisode(ep.episodeNumber)
                                showEpisodes = false
                                reloadKey++
                            },
                        ) {
                            Text(
                                "${ep.episodeNumber}. ${ep.name ?: "Episode ${ep.episodeNumber}"}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (episodes.isEmpty()) {
                        item { Text("No episode data", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showEpisodes = false }) { Text("Close") } },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewPlayer(url: String, reloadKey: Int = 0, modifier: Modifier = Modifier) {
    // Fullscreen video: the site calls HTML5 requestFullscreen, WebChromeClient
    // surfaces the video view as a "custom view". The canonical WebView pattern
    // is to add it to the Activity's decor view with MATCH_PARENT — hosting it
    // inside the Compose tree keeps it trapped in the player's 16:9 box, which
    // made fullscreen look tiny/too big on some screens. We also rotate to
    // sensor landscape while it's up and restore on exit.
    var customView by remember { mutableStateOf<android.view.View?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    var decorParent by remember { mutableStateOf<android.view.ViewGroup?>(null) }

    val activity = androidx.compose.ui.platform.LocalContext.current.findActivity()

    // Attach/detach the custom view on the decor view, sized to fill the screen.
    androidx.compose.runtime.DisposableEffect(customView) {
        val parent = decorParent
        val view = customView
        if (parent != null && view != null) {
            parent.addView(
                view,
                android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        onDispose {
            (view?.parent as? android.view.ViewGroup)?.removeView(view)
        }
    }

    androidx.compose.runtime.DisposableEffect(isFullscreen) {
        if (isFullscreen && activity != null) {
            val old = activity.requestedOrientation
            activity.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            onDispose { activity.requestedOrientation = old }
        } else {
            onDispose { }
        }
    }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val u = request?.url ?: return false
                            // Keep embed traffic inside; open external links in browser
                            return !u.host.orEmpty().contains("vidstuck.xyz")
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: android.view.View, callback: android.webkit.WebChromeClient.CustomViewCallback) {
                            // Remember the decor content view so the overlay effect
                            // can attach the fullscreen view above everything.
                            decorParent = activity?.window?.decorView as? android.view.ViewGroup
                            customView = view
                            isFullscreen = true
                        }

                        override fun onHideCustomView() {
                            customView = null
                            isFullscreen = false
                        }
                    }
                }
            },
            update = { web ->
                if (web.url != url) web.loadUrl(url)
            },
            onRelease = { it.destroy() },
        )
    }
}

/** Extracts the hosting Activity from any Context chain, or null. */
private tailrec fun android.content.Context.findActivity(): android.app.Activity? =
    when (this) {
        is android.app.Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/**
 * Native player for direct MP4 streams resolved via OmniSave.
 * Uses ExoPlayer (media3) with an OkHttp datasource so the same user-agent /
 * referer headers the CDN expects are sent.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ExoPlayerScreen(url: String, modifier: Modifier = Modifier, onPosition: (Long) -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val player = remember(url) {
        val dsFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(
            okhttp3.OkHttpClient.Builder().build()
        ).apply {
            setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150 Mobile Safari/537.36")
            setDefaultRequestProperties(mapOf("referer" to "https://videodownloader.site/"))
        }
        androidx.media3.exoplayer.ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    androidx.media3.datasource.DefaultDataSource.Factory(context, dsFactory)
                )
            )
            .build()
            .apply {
                // Downloaded files arrive as content:// (MediaStore) or as a
                // bare file path; only bare paths get the file:// scheme.
                val uri = if (url.startsWith("/")) android.net.Uri.fromFile(java.io.File(url))
                    else android.net.Uri.parse(url)
                setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
            }
    }

    // Pause/release with lifecycle to avoid playing in the background.
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> player.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            player.release()
        }
    }

    // Periodically report the playback position so Continue Watching can
    // resume here and draw a progress bar on Home.
    androidx.compose.runtime.LaunchedEffect(player) {
        while (true) {
            if (player.playWhenReady && player.playbackState != androidx.media3.common.Player.STATE_ENDED) {
                onPosition(player.currentPosition / 1000)
            }
            kotlinx.coroutines.delay(5_000)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
                useController = true
                this.player = player
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        onRelease = { },
    )
}
