package com.aurora.player.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.media3.common.Player
import com.aurora.player.MusicApp
import com.aurora.player.playback.PlaybackConnection
import com.aurora.player.ui.library.LibraryScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private val allRoutes = listOf(
    Routes.HOME to "الرئيسية", Routes.SONGS to "الأغاني", Routes.ALBUMS to "الألبومات",
    Routes.ARTISTS to "الفنانون", Routes.PLAYLISTS to "قوائم التشغيل", Routes.FOLDERS to "المجلدات",
    Routes.FAVORITES to "المفضلة", Routes.RECENT to "شُغّلت مؤخرًا",
    Routes.NOW_PLAYING to "تعمل الآن", Routes.QUEUE to "قائمة التشغيل الحالية", Routes.LYRICS to "كلمات الأغاني", Routes.SEARCH to "بحث",
    Routes.EQUALIZER to "معادل الصوت", Routes.SETTINGS to "الإعدادات",
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
    var showBrandSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1400); showBrandSplash = false }
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
    val playingSongId = controller?.currentMediaItem?.localConfiguration?.uri?.lastPathSegment?.toLongOrNull()
        ?: controller?.currentMediaItem?.mediaId?.toLongOrNull()
    var wallpaperUri by remember(context) { mutableStateOf(context.getSharedPreferences("whisper_preferences", android.content.Context.MODE_PRIVATE).getString("wallpaper_uri", null)) }
    var favoriteRefresh by remember { mutableIntStateOf(0) }
    val currentIsFavorite = remember(favoriteRefresh, playingSongId) {
        playingSongId?.let { id ->
            context.getSharedPreferences("whisper_favorites", android.content.Context.MODE_PRIVATE)
                .getStringSet("ids", emptySet()).orEmpty().contains(id.toString())
        } ?: false
    }
    fun toggleCurrentFavorite() {
        val id = playingSongId ?: return
        val prefs = context.getSharedPreferences("whisper_favorites", android.content.Context.MODE_PRIVATE)
        val ids = prefs.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
        if (!ids.add(id.toString())) ids.remove(id.toString())
        prefs.edit().putStringSet("ids", ids).apply()
        favoriteRefresh++
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                if (controller?.currentMediaItem != null && route != Routes.NOW_PLAYING) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp).clickable {
                            nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true }
                        },
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                        tonalElevation = 7.dp,
                        shadowElevation = 10.dp
                    ) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.MusicNote, contentDescription = "الصوت يعمل", tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(7.dp))
                            Column(Modifier.weight(1f)) {
                                AnimatedContent(targetState = playbackTitle.ifBlank { "الصوت يعمل" }, label = "miniPlayerTrackChange") { title ->
                                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                }
                                Text("Tayer_player", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { controller?.seekToPreviousMediaItem() }, enabled = (controller?.mediaItemCount ?: 0) > 1) {
                                Icon(Icons.Rounded.SkipPrevious, contentDescription = "السابق")
                            }
                            IconButton(onClick = { if (playbackPlaying) controller?.pause() else controller?.play() }) {
                                Icon(if (playbackPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (playbackPlaying) "إيقاف مؤقت" else "تشغيل")
                            }
                            IconButton(onClick = { controller?.seekToNextMediaItem() }, enabled = (controller?.mediaItemCount ?: 0) > 1) {
                                Icon(Icons.Rounded.SkipNext, contentDescription = "التالي")
                            }
                            IconButton(onClick = { toggleCurrentFavorite() }) {
                                Icon(if (currentIsFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = if (currentIsFavorite) "إزالة من المفضلة" else "إضافة للمفضلة",
                                    tint = if (currentIsFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (bottomTabs.any { it.route == route }) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
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
        Box(Modifier.fillMaxSize()) {
        if (wallpaperUri != null) AsyncImage(model = wallpaperUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.22f)
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            allRoutes.forEach { (r, title) ->
                composable(r) {
                    when {
                        r == Routes.NOW_PLAYING -> NowPlayingScreen(controller, onNavigate = { target -> nav.navigate(target) { launchSingleTop = true } })
                        r == Routes.SONGS -> WhisperSongListScreen(
                            songs = songs,
                            onPlay = { song -> playback.play(song, songs); nav.navigate(Routes.NOW_PLAYING) },
                            onPlayNext = { song -> controller?.let { player ->
                                val mediaItem = androidx.media3.common.MediaItem.fromUri(android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id).toString())
                                val insertAt = (player.currentMediaItemIndex + 1).coerceAtLeast(0).coerceAtMost(player.mediaItemCount)
                                player.addMediaItem(insertAt, mediaItem)
                            } },
                            onRadio = { song ->
                                val related = songs.filter { it.artist.equals(song.artist, ignoreCase = true) }
                                if (related.isNotEmpty()) playback.playQueue(related, related.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
                            },
                            onFavorite = { song ->
                                val prefs = context.getSharedPreferences("whisper_favorites", android.content.Context.MODE_PRIVATE)
                                val ids = prefs.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
                                ids.add(song.id.toString())
                                prefs.edit().putStringSet("ids", ids).apply()
                            },
                            onNavigate = { target -> nav.navigate(target) { launchSingleTop = true } },
                            activeSongId = playingSongId
                        )
                        r == Routes.ALBUMS || r == Routes.ARTISTS || r == Routes.FOLDERS ->
                            LibraryScreen(
                                title = title, songs = songs, albums = albums, artists = artists, folders = folders,
                                excludedFolders = excludedFolders, hasPermission = hasPermission,
                                onRequestPermission = { permissionLauncher.launch(audioPermission()) },
                                onRefresh = { repository.refresh() },
                                onExcludeFolder = { path -> coroutineScope.launch { repository.excludeFolder(path) } },
                                onIncludeFolder = { path -> coroutineScope.launch { repository.includeFolder(path) } },
                                onSongClick = { song -> playback.play(song, songs); nav.navigate(Routes.NOW_PLAYING) },
                                onPlayAll = { playback.playQueue(songs) },
                            )
                        r == Routes.HOME -> WhisperHomeScreen(songs, onPlay = { song -> playback.play(song, songs); nav.navigate(Routes.NOW_PLAYING) }, onPlayNext = { song -> controller?.let { player ->
                            val mediaItem = androidx.media3.common.MediaItem.fromUri(android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id).toString())
                            val insertAt = (player.currentMediaItemIndex + 1).coerceAtLeast(0).coerceAtMost(player.mediaItemCount)
                            player.addMediaItem(insertAt, mediaItem)
                        } },
                        onRadio = { song ->
                            val related = songs.filter { it.artist.equals(song.artist, ignoreCase = true) }
                            if (related.isNotEmpty()) playback.playQueue(related, related.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
                        },
                        onFavorite = { song ->
                            val prefs = context.getSharedPreferences("whisper_favorites", android.content.Context.MODE_PRIVATE)
                            val ids = prefs.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
                            ids.add(song.id.toString())
                            prefs.edit().putStringSet("ids", ids).apply()
                        },
                        onNavigate = { target -> nav.navigate(target) { launchSingleTop = true } }, activeSongId = playingSongId)
                        r == Routes.SEARCH -> WhisperSearchScreen(songs, onPlay = { song -> playback.play(song, songs); nav.navigate(Routes.NOW_PLAYING) }, onPlayNext = { song -> controller?.let { player ->
                            val mediaItem = androidx.media3.common.MediaItem.fromUri(android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id).toString())
                            val insertAt = (player.currentMediaItemIndex + 1).coerceAtLeast(0).coerceAtMost(player.mediaItemCount)
                            player.addMediaItem(insertAt, mediaItem)
                        } },
                        onRadio = { song ->
                            val related = songs.filter { it.artist.equals(song.artist, ignoreCase = true) }
                            if (related.isNotEmpty()) playback.playQueue(related, related.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
                        },
                        onFavorite = { song ->
                            val prefs = context.getSharedPreferences("whisper_favorites", android.content.Context.MODE_PRIVATE)
                            val ids = prefs.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
                            ids.add(song.id.toString())
                            prefs.edit().putStringSet("ids", ids).apply()
                        },
                        onNavigate = { target -> nav.navigate(target) { launchSingleTop = true } }, activeSongId = playingSongId)
                        r == Routes.PLAYLISTS -> WhisperPlaylistsScreen(songs) { song -> playback.play(song, songs); nav.navigate(Routes.NOW_PLAYING) }
                        r == Routes.FAVORITES -> WhisperFavoritesScreen(songs) { song -> playback.play(song, songs); nav.navigate(Routes.NOW_PLAYING) }
                        r == Routes.QUEUE -> WhisperQueueScreen(controller, songs)
                        r == Routes.LYRICS -> WhisperLyricsScreen(controller)
                        r == Routes.SETTINGS -> WhisperSettingsScreen(app.container.settingsRepository, controller, onWallpaperChanged = { wallpaperUri = it })
                        r == Routes.EQUALIZER -> WhisperEqualizerScreen()
                        else -> ScaffoldScreen(title)
                    }
                }
            }
        }
        }
    }
    }
    if (showBrandSplash) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFF05060A)).clickable { showBrandSplash = false },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = com.aurora.player.R.drawable.ic_whisper_mark),
                    contentDescription = "شعار همس",
                    modifier = Modifier.size(230.dp)
                )
                Spacer(Modifier.height(18.dp))
                Text("Tayer_player", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Text("by dr Ahmed Abdelhamid", color = Color(0xFFCE1126), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 12.dp)) { listOf(Color(0xFF111111), Color.White, Color(0xFFCE1126), Color(0xFF007A3D)).forEach { stripe -> Box(Modifier.width(28.dp).height(4.dp).background(stripe, androidx.compose.foundation.shape.RoundedCornerShape(2.dp))) } }
                Text("🇵🇸  •  ◇ ╳ ◇ ╳ ◇", color = Color(0xFFECE8DE), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
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
