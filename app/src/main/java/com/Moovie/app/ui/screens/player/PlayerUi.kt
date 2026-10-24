package com.Moovie.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import android.annotation.SuppressLint
import android.net.Uri
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.Moovie.app.data.remote.Caption
import com.Moovie.app.data.remote.StreamQuality
import com.Moovie.app.ui.screens.detail.DownloadDialog
import com.Moovie.app.ui.screens.detail.DownloadViewModel
import com.Moovie.app.ui.screens.detail.EpisodeDownloadDialog

/** Which playback source the user has chosen. */
enum class PlaybackProvider { EMBED, OMNI_SAVE }

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
    var fullscreen by remember { mutableStateOf(false) }
    var showDownloadPicker by remember { mutableStateOf(false) }
    val downloaded by vm.downloaded.collectAsState()
    val posterPath by vm.posterPath.collectAsState()
    val year by vm.year.collectAsState()
    val directStream by vm.directStream.collectAsState()
    val qualities by vm.qualities.collectAsState()
    val captions by vm.captions.collectAsState()
    val resolvingNative by vm.resolvingNative.collectAsState()
    val selectedQualityUrl by vm.selectedQualityUrl.collectAsState()
    val selectedCaption by vm.selectedCaption.collectAsState()
    val playingLocalFile by vm.playingLocalFile.collectAsState()

    // OmniSave is now a first-class provider alongside the embed, for both
    // movies and TV. A downloaded local file always forces the native player.
    var provider by remember { mutableStateOf(PlaybackProvider.EMBED) }
    val useNative = playingLocalFile || provider == PlaybackProvider.OMNI_SAVE

    // Fullscreen only applies to the native player; switching back to the
    // embed must restore the normal layout.
    val isFullscreen = fullscreen && useNative
    BackHandler(enabled = isFullscreen) { fullscreen = false }

    val url = embedUrl(mediaType, vm.idForUi, currentSeason, currentEpisode)

    // Watch tracking: mark "Watching" on entry, "Watched" on exit, so the
    // profile stats reflect what the user actually plays.
    androidx.compose.runtime.DisposableEffect(Unit) {
        vm.startWatching()
        onDispose { vm.finishWatching() }
    }

    // Keep the screen on while the player is open (both embed and native).
    val activity = LocalContext.current.findActivity()
    androidx.compose.runtime.DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // In fullscreen the player is the only thing on screen; chrome is hidden
        // so the video can use the whole display instead of a middle strip.
        if (!isFullscreen) {
            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        titleName.ifBlank { "Now playing" },
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (mediaType == "tv") {
                        Text(
                            "S$currentSeason · E$currentEpisode",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                // Downloaded items are removed straight away; otherwise ask for
                // quality + subtitle before starting the transfer.
                IconButton(onClick = {
                    if (downloaded) vm.toggleDownload() else showDownloadPicker = true
                }) {
                    Icon(
                        if (downloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                        contentDescription = if (downloaded) "Remove download" else "Save for offline",
                        tint = if (downloaded) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
                if (mediaType == "tv") {
                    IconButton(onClick = { showEpisodes = true }) {
                        Icon(Icons.Filled.Tune, "Episodes", tint = Color.White)
                    }
                }
            }

            // Provider switch — Embed (vidstuck) vs the native OmniSave stream.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    "Source",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                )
                ProviderChip(
                    label = "Embed",
                    selected = !useNative,
                    enabled = !playingLocalFile,
                    onClick = {
                        provider = PlaybackProvider.EMBED
                        fullscreen = false
                    },
                )
                ProviderChip(
                    label = if (resolvingNative) "OmniSave…" else "OmniSave",
                    selected = useNative,
                    enabled = true,
                    onClick = { provider = PlaybackProvider.OMNI_SAVE },
                )
                if (playingLocalFile) {
                    Text(
                        "offline",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        // Player area.
        val stream = directStream
        val isLocalFile = stream != null && !stream.startsWith("http")
        if (useNative) {
            if (stream != null) {
                OmniSavePlayer(
                    url = stream,
                    qualities = qualities,
                    selectedQualityUrl = selectedQualityUrl,
                    onSelectQuality = { vm.selectQuality(it) },
                    captions = captions,
                    selectedCaption = selectedCaption,
                    onSelectCaption = { vm.selectCaption(it) },
                    isTv = mediaType == "tv",
                    hasPreviousEpisode = currentEpisode > 1,
                    onPreviousEpisode = { vm.previousEpisode() },
                    onNextEpisode = { vm.nextEpisode() },
                    onPosition = { vm.reportPosition(it) },
                    contentKey = "$mediaType-${vm.idForUi}-$currentSeason-$currentEpisode",
                    fullscreen = isFullscreen,
                    onToggleFullscreen = { fullscreen = !fullscreen },
                    modifier = if (isFullscreen) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.fillMaxWidth().weight(1f)
                    },
                )
            } else {
                MoovieWaitingBanner(
                    text = when {
                        resolvingNative -> "Preparing OmniSave stream…"
                        isLocalFile -> "Loading downloaded file…"
                        else -> "No OmniSave source found — switch to Embed."
                    },
                    modifier = if (isFullscreen) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.fillMaxWidth().weight(1f)
                    },
                )
            }
        } else {
            WebViewPlayer(
                url = url,
                reloadKey = reloadKey,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }

        if (!isFullscreen && mediaType == "tv") {
            // Season quick-switch row
            LazyRow(
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            ) {
                items((1..seasons).toList()) { s ->
                    FilterChip(
                        selected = s == currentSeason,
                        onClick = { vm.setSeason(s); reloadKey++ },
                        label = { Text("S$s", color = Color.White) },
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

    // Quality + subtitle picker for the movie/episode currently playing.
    // Previously the download icon queued the item straight away, so there was
    // no way to choose a resolution or a subtitle track from the player.
    if (showDownloadPicker) {
        val dvm: DownloadViewModel = viewModel(
            key = "dl-player-${vm.idForUi}-" +
                (if (mediaType == "tv") "s${currentSeason}e$currentEpisode" else "movie"),
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    DownloadViewModel(mediaType, vm.idForUi) as T
            },
        )
        val ep = episodes.firstOrNull { it.episodeNumber == currentEpisode }
        if (mediaType == "tv") {
            EpisodeDownloadDialog(
                showName = titleName.ifBlank { "Series" },
                season = currentSeason,
                episode = currentEpisode,
                episodeName = ep?.name,
                airYear = ep?.airDate?.take(4)?.toIntOrNull() ?: year,
                posterPath = posterPath,
                onQueueFallback = { vm.toggleDownload() },
                onDismiss = {
                    showDownloadPicker = false
                    vm.refreshDownload()
                },
                vm = dvm,
            )
        } else {
            DownloadDialog(
                titleName = titleName,
                year = year,
                posterPath = posterPath,
                onDismiss = {
                    showDownloadPicker = false
                    vm.refreshDownload()
                },
                vm = dvm,
            )
        }
    }
}

/** Small chip used for the Embed/OmniSave provider switch. */
@Composable
private fun ProviderChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = { Text(label, color = Color.White) },
    )
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

    val activity = LocalContext.current.findActivity()

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
                            return !u.host.orEmpty().contains("vidstuck.cc")
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: android.view.View, callback: android.webkit.WebChromeClient.CustomViewCallback) {
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
            onRelease = {
                it.clearCache(true)
                it.destroy()
            },
        )
    }
}

/**
 * Native OmniSave player: ExoPlayer (media3) with a custom Compose control
 * layer instead of the default PlayerView controller.
 *
 * Controls: play/pause, seek bar + time, ±10s, playback speed, quality picker,
 * subtitle picker, fullscreen (rotate), and TV episode prev/next. A branded
 * "Moovie" banner is shown while the stream is buffering/preparing.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun OmniSavePlayer(
    url: String,
    qualities: List<StreamQuality>,
    selectedQualityUrl: String?,
    onSelectQuality: (StreamQuality) -> Unit,
    captions: List<Caption>,
    selectedCaption: Caption?,
    onSelectCaption: (Caption?) -> Unit,
    isTv: Boolean,
    hasPreviousEpisode: Boolean,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onPosition: (Long) -> Unit,
    /** Identifies the movie/episode; a change means "start from the beginning". */
    contentKey: String,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val activity = context.findActivity()

    val dsFactory = remember {
        androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(
            okhttp3.OkHttpClient.Builder().build()
        ).apply {
            setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150 Mobile Safari/537.36")
            setDefaultRequestProperties(mapOf("referer" to "https://videodownloader.site/"))
        }
    }

    val player = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    androidx.media3.datasource.DefaultDataSource.Factory(context, dsFactory)
                )
            )
            .build()
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var showControls by remember { mutableStateOf(true) }
    var lastContentKey by remember { mutableStateOf<String?>(null) }

    val subtitleUri = remember(selectedCaption) {
        selectedCaption?.url?.takeIf { it.isNotBlank() }?.let { raw ->
            // Downloaded subtitles are plain file paths; Uri.parse() would treat
            // them as a relative URI and silently fail to open them.
            if (raw.startsWith("/")) Uri.fromFile(java.io.File(raw)) else Uri.parse(raw)
        }
    }

    // (Re)load whenever the stream URL or subtitle track changes. Switching a
    // subtitle must not restart the video, so keep the position unless the
    // movie/episode itself changed.
    androidx.compose.runtime.LaunchedEffect(url, subtitleUri) {
        val keepPosition = lastContentKey == contentKey
        val resumeAt = if (keepPosition) player.currentPosition.coerceAtLeast(0L) else 0L
        val subtitleMime = subtitleUri?.let { mimeForSubtitle(it.toString()) }
        val subConfigs = subtitleUri?.let { u ->
            listOf(
                androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(u)
                    .setMimeType(subtitleMime ?: mimeForSubtitle(u.toString()))
                    .setLanguage(selectedCaption?.lan)
                    .setLabel(selectedCaption?.lanName)
                    // Without DEFAULT the track is loaded but never selected,
                    // which looked exactly like "subtitles don't work".
                    .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                    .build()
            )
        } ?: emptyList()
        val item = androidx.media3.common.MediaItem.Builder()
            .setUri(if (url.startsWith("/")) Uri.fromFile(java.io.File(url)) else Uri.parse(url))
            .setSubtitleConfigurations(subConfigs)
            .build()
        android.util.Log.d(
            TAG,
            "Loading stream=${url.take(60)}… subtitle=${selectedCaption?.lan ?: "off"} mime=$subtitleMime",
        )
        // Explicitly enable/disable the text renderer; ExoPlayer otherwise keeps
        // whatever selection state the previous media item had.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, subtitleUri == null)
            .build()
        player.setMediaItem(item, resumeAt)
        player.prepare()
        player.playWhenReady = true
        lastContentKey = contentKey
    }

    androidx.compose.runtime.DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == androidx.media3.common.Player.STATE_BUFFERING ||
                    state == androidx.media3.common.Player.STATE_IDLE
                if (state == androidx.media3.common.Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Pause/release with lifecycle to avoid playing in the background.
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Rotate for fullscreen and restore on exit.
    androidx.compose.runtime.DisposableEffect(fullscreen) {
        if (fullscreen && activity != null) {
            val old = activity.requestedOrientation
            activity.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            onDispose { activity.requestedOrientation = old }
        } else {
            onDispose { }
        }
    }

    // Hide the status/navigation bars while fullscreen so the video really uses
    // the whole display; restore them when leaving.
    androidx.compose.runtime.DisposableEffect(fullscreen, view) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            if (fullscreen) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null) {
                WindowCompat.getInsetsController(window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Position/duration polling for the seek bar, and progress reporting.
    androidx.compose.runtime.LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            val d = player.duration
            if (d > 0) durationMs = d
            if (player.playWhenReady) onPosition(positionMs / 1000)
            kotlinx.coroutines.delay(500)
        }
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize().clickable { showControls = !showControls },
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    this.player = player
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            onRelease = { },
        )

        if (buffering) {
            MoovieWaitingBanner(
                text = "Buffering from OmniSave…",
                modifier = Modifier.fillMaxSize(),
                compact = true,
            )
        }

        if (showControls) {
            OmniSaveControls(
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                speed = speed,
                isTv = isTv,
                hasPreviousEpisode = hasPreviousEpisode,
                qualities = qualities,
                selectedQualityUrl = selectedQualityUrl,
                captions = captions,
                selectedCaption = selectedCaption,
                fullscreen = fullscreen,
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                onSeek = { ms -> player.seekTo(ms) },
                onSkip = { delta -> player.seekTo((player.currentPosition + delta).coerceAtLeast(0L)) },
                onSpeed = { s -> speed = s; player.setPlaybackSpeed(s) },
                onQuality = onSelectQuality,
                onCaption = onSelectCaption,
                onPreviousEpisode = onPreviousEpisode,
                onNextEpisode = onNextEpisode,
                onFullscreen = onToggleFullscreen,
            )
        }
    }
}

@Composable
private fun OmniSaveControls(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    isTv: Boolean,
    hasPreviousEpisode: Boolean,
    qualities: List<StreamQuality>,
    selectedQualityUrl: String?,
    captions: List<Caption>,
    selectedCaption: Caption?,
    fullscreen: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Long) -> Unit,
    onSpeed: (Float) -> Unit,
    onQuality: (StreamQuality) -> Unit,
    onCaption: (Caption?) -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onFullscreen: () -> Unit,
) {
    var speedMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    var subsMenu by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val selectableQualities = qualities.filter { !it.vipLocked && it.url.isNotBlank() }
    val sliderValue = if (dragging) dragValue else positionMs.toFloat()
    val rangeEnd = durationMs.coerceAtLeast(1L).toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.38f))
            .padding(12.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isTv && hasPreviousEpisode) {
                IconButton(onClick = onPreviousEpisode) {
                    Icon(Icons.Filled.SkipPrevious, "Previous episode", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            IconButton(onClick = { onSkip(-10_000L) }) {
                Icon(Icons.Filled.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }
            IconButton(onClick = { onSkip(10_000L) }) {
                Icon(Icons.Filled.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            if (isTv) {
                IconButton(onClick = onNextEpisode) {
                    Icon(Icons.Filled.SkipNext, "Next episode", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatTime(positionMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
            Slider(
                value = sliderValue.coerceIn(0f, rangeEnd),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = {
                    onSeek(dragValue.toLong())
                    dragging = false
                },
                valueRange = 0f..rangeEnd,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(formatTime(durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Playback speed
            Box {
                IconButton(onClick = { speedMenu = true }) {
                    Icon(Icons.Filled.Speed, "Playback speed", tint = Color.White)
                }
                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                        DropdownMenuItem(
                            text = { Text(if (s == 1f) "Normal" else "${s}×") },
                            onClick = { onSpeed(s); speedMenu = false },
                        )
                    }
                }
            }

            // Quality
            Box {
                IconButton(onClick = { qualityMenu = true }, enabled = selectableQualities.isNotEmpty()) {
                    Icon(
                        Icons.Filled.HighQuality,
                        "Quality",
                        tint = if (selectableQualities.isEmpty()) Color.White.copy(alpha = 0.4f) else Color.White,
                    )
                }
                DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
                    if (selectableQualities.isEmpty()) {
                        DropdownMenuItem(text = { Text("No qualities") }, onClick = { qualityMenu = false })
                    }
                    selectableQualities.forEach { q ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${q.resolution}p · ${q.format}" +
                                        if (q.url == selectedQualityUrl) "  ✓" else "",
                                )
                            },
                            onClick = { onQuality(q); qualityMenu = false },
                        )
                    }
                }
            }

            // Subtitles
            Box {
                IconButton(onClick = { subsMenu = true }) {
                    Icon(
                        Icons.Filled.Subtitles,
                        "Subtitles",
                        tint = if (selectedCaption != null) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
                DropdownMenu(expanded = subsMenu, onDismissRequest = { subsMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Off" + if (selectedCaption == null) "  ✓" else "") },
                        onClick = { onCaption(null); subsMenu = false },
                    )
                    captions.forEach { c ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    (c.lanName ?: c.lan ?: "Unknown") +
                                        if (c.url == selectedCaption?.url) "  ✓" else "",
                                )
                            },
                            onClick = { onCaption(c); subsMenu = false },
                        )
                    }
                    if (captions.isEmpty()) {
                        DropdownMenuItem(text = { Text("No subtitles") }, onClick = { subsMenu = false })
                    }
                }
            }

            // Fullscreen
            IconButton(onClick = onFullscreen) {
                Icon(
                    if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    if (fullscreen) "Exit fullscreen" else "Fullscreen",
                    tint = Color.White,
                )
            }
        }
    }
}

/** Branded loading banner shown while a source is resolving/buffering. */
@Composable
private fun MoovieWaitingBanner(text: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    Box(modifier.background(Color.Black.copy(alpha = if (compact) 0.55f else 1f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Moovie",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
            Spacer(Modifier.height(8.dp))
            Text(text, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Extracts the hosting Activity from any Context chain, or null. */
private tailrec fun android.content.Context.findActivity(): android.app.Activity? =
    when (this) {
        is android.app.Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/** mm:ss (or h:mm:ss) for the seek bar. */
private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private const val TAG = "OmniSavePlayer"

/**
 * Best-guess subtitle MIME from the URL/file extension. OmniSave serves .srt
 * (application/x-subrip); WebVTT and ASS are supported by media3 as well.
 */
private fun mimeForSubtitle(url: String): String {
    val path = runCatching { Uri.parse(url).lastPathSegment }.getOrNull()
        ?.lowercase()
        ?: url.substringBefore('?').lowercase()
    return when {
        path.endsWith(".vtt") -> "text/vtt"
        path.endsWith(".ass") || path.endsWith(".ssa") -> "text/x-ssa"
        else -> "application/x-subrip"
    }
}
