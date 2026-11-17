package com.aurora.player.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.media3.common.Player
import com.aurora.player.MusicApp
import com.aurora.player.playback.PlaybackConnection
import com.aurora.player.ui.library.LibraryScreen
import kotlinx.coroutines.launch

private val allRoutes = listOf(
    Routes.HOME to "Home", Routes.SONGS to "Songs", Routes.ALBUMS to "Albums",
    Routes.ARTISTS to "Artists", Routes.PLAYLISTS to "Playlists", Routes.FOLDERS to "Folders",
    Routes.FAVORITES to "Favorites", Routes.RECENT to "Recently Played",
    Routes.NOW_PLAYING to "Now Playing", Routes.SEARCH to "Search",
    Routes.EQUALIZER to "Equalizer", Routes.SETTINGS to "Settings",
)

private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as MusicApp
    val repository = app.container.libraryRepository
    val playback = remember { PlaybackConnection(context) }
    DisposableEffect(playback) { onDispose { playback.release() } }
    val controller by playback.controller.collectAsState()
    var playbackTitle by remember { mutableStateOf("") }
    var playbackPlaying by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playbackTitle = player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
                playbackPlaying = player.isPlaying
            }
        }
        controller?.addListener(listener)
        playbackTitle = controller?.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
        playbackPlaying = controller?.isPlaying == true
        onDispose { controller?.removeListener(listener) }
    }

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) repository.refresh()
    }
    val songs by repository.songs.collectAsState()
    val excludedFolders by repository.excludedFolders.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val albums = remember(songs) { repository.albums(songs) }
    val artists = remember(songs) { repository.artists(songs) }
    val folders = remember(songs) { repository.folders(songs) }
    LaunchedEffect(hasPermission) { if (hasPermission) repository.refresh() }

    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                if (playbackTitle.isNotBlank() && route != Routes.NOW_PLAYING) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(
                            Modifier.fillMaxWidth().clickable { nav.navigate(Routes.NOW_PLAYING) }
                                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(playbackTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (playbackPlaying) "Playing" else "Paused", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { if (controller?.isPlaying == true) controller?.pause() else controller?.play() }) {
                                Icon(if (playbackPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play or pause")
                            }
                            IconButton(onClick = { controller?.seekToNextMediaItem() }) { Text("Next") }
                        }
                    }
                }
                if (bottomTabs.any { it.route == route }) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        bottomTabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            allRoutes.forEach { (r, title) ->
                composable(r) {
                    when {
                        r == Routes.NOW_PLAYING -> NowPlayingScreen(controller)
                        r == Routes.SONGS || r == Routes.ALBUMS || r == Routes.ARTISTS || r == Routes.FOLDERS ->
                            LibraryScreen(
                                title = title, songs = songs, albums = albums, artists = artists, folders = folders,
                                excludedFolders = excludedFolders, hasPermission = hasPermission,
                                onRequestPermission = { permissionLauncher.launch(audioPermission()) },
                                onRefresh = { repository.refresh() },
                                onExcludeFolder = { path -> coroutineScope.launch { repository.excludeFolder(path) } },
                                onIncludeFolder = { path -> coroutineScope.launch { repository.includeFolder(path) } },
                                onSongClick = { song -> playback.play(song, songs) },
                                onPlayAll = { playback.playQueue(songs) },
                            )
                        else -> ScaffoldScreen(title)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScaffoldScreen(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}
