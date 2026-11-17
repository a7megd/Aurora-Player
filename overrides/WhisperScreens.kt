package com.aurora.player.ui.navigation

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.aurora.player.data.prefs.SettingsRepository
import com.aurora.player.domain.model.Song
import com.aurora.player.domain.model.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun WhisperPlaylistsScreen(songs: List<Song>, onPlay: (Song) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("whisper_playlists", Context.MODE_PRIVATE) }
    var refresh by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<Long>() }
    val names = remember(refresh) { prefs.all.keys.filter { it.startsWith("playlist:") }.map { it.removePrefix("playlist:") }.sorted() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Playlists", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { name = ""; selectedIds.clear(); showCreate = true }) { Text("Create playlist") }
        }
        if (names.isEmpty()) Text("Create your first playlist", modifier = Modifier.padding(top = 24.dp))
        LazyColumn {
            items(names) { playlist ->
                val ids = prefs.getString("playlist:$playlist", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
                val tracks = songs.filter { it.id in ids }
                ListItem(
                    headlineContent = { Text(playlist) },
                    supportingContent = { Text("${tracks.size} songs") },
                    trailingContent = { TextButton(onClick = { prefs.edit().remove("playlist:$playlist").apply(); refresh++ }) { Text("Delete") } },
                    modifier = Modifier.clickable { tracks.firstOrNull()?.let(onPlay) }
                )
            }
        }
    }
    if (showCreate) AlertDialog(
        onDismissRequest = { showCreate = false },
        title = { Text("Create playlist") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Playlist name") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text("Choose songs", style = MaterialTheme.typography.titleSmall)
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(songs) { song ->
                        val checked = song.id in selectedIds
                        Row(Modifier.fillMaxWidth().clickable {
                            if (checked) selectedIds.remove(song.id) else selectedIds.add(song.id)
                        }.padding(vertical = 2.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = {
                                if (it) { if (song.id !in selectedIds) selectedIds.add(song.id) } else selectedIds.remove(song.id)
                            })
                            Column(Modifier.weight(1f)) {
                                Text(song.title, maxLines = 1)
                                Text(song.artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
                Text("${selectedIds.size} selected", style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clean = name.trim()
                if (clean.isNotEmpty() && selectedIds.isNotEmpty()) {
                    prefs.edit().putString("playlist:$clean", selectedIds.joinToString(",")).apply()
                    refresh++
                    name = ""
                    selectedIds.clear()
                    showCreate = false
                }
            }, enabled = name.isNotBlank() && selectedIds.isNotEmpty()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
    )
}

@Composable
fun WhisperQueueScreen(controller: MediaController?) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(controller) {
        while (true) { tick++; kotlinx.coroutines.delay(700) }
    }
    val count = controller?.mediaItemCount ?: 0
    val current = controller?.currentMediaItemIndex ?: -1
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Current playlist", style = MaterialTheme.typography.headlineMedium)
        Text("$count tracks in the queue", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn {
            items(count) { index ->
                val item = controller?.getMediaItemAt(index)
                ListItem(
                    headlineContent = { Text(item?.mediaMetadata?.title?.toString().orEmpty().ifBlank { "Track ${index + 1}" }) },
                    supportingContent = { Text(item?.mediaMetadata?.artist?.toString().orEmpty()) },
                    leadingContent = { Text(if (index == current) "▶" else "${index + 1}") },
                    modifier = Modifier.clickable { controller?.seekTo(index, 0L); controller?.play() }
                )
            }
        }
    }
}

@Composable
fun WhisperLyricsScreen(controller: MediaController?) {
    val context = LocalContext.current
    val mediaId = controller?.currentMediaItem?.mediaId.orEmpty()
    val title = controller?.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
    val prefs = remember { context.getSharedPreferences("whisper_lyrics", Context.MODE_PRIVATE) }
    var lyrics by remember(mediaId) { mutableStateOf(prefs.getString(mediaId, "").orEmpty()) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Lyrics", style = MaterialTheme.typography.headlineMedium)
        Text(title.ifBlank { "No track selected" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = lyrics,
            onValueChange = { lyrics = it; if (mediaId.isNotBlank()) prefs.edit().putString(mediaId, it).apply() },
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = { Text("Paste or type song lyrics") },
            placeholder = { Text("Lyrics for this track…") }
        )
        Text("Lyrics are saved on this device for the selected track.", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun WhisperSettingsScreen(repository: SettingsRepository) {
    val settings by repository.settings.collectAsState(initial = com.aurora.player.domain.model.AppSettings())
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Appearance", style = MaterialTheme.typography.headlineMedium)
        Text("Theme", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        listOf(ThemeMode.SYSTEM to "System", ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light").forEach { (mode, label) ->
            Row(Modifier.fillMaxWidth().clickable { scope.launch { repository.setThemeMode(mode) } }.padding(vertical = 8.dp)) {
                RadioButton(selected = settings.themeMode == mode, onClick = { scope.launch { repository.setThemeMode(mode) } })
                Text(label, modifier = Modifier.padding(start = 8.dp, top = 12.dp))
            }
        }
        Row(Modifier.fillMaxWidth().clickable { scope.launch { repository.setAmoled(!settings.amoled) } }.padding(vertical = 8.dp)) {
            Checkbox(checked = settings.amoled, onCheckedChange = { scope.launch { repository.setAmoled(it) } })
            Text("AMOLED black background", modifier = Modifier.padding(start = 8.dp, top = 12.dp))
        }
        Text("Accent color", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0xFF7C5CFF, 0xFF00C2A8, 0xFFFF5C8A, 0xFFFFB020, 0xFF3D9BFF, 0xFF8BE04E).forEach { color ->
                Surface(
                    color = androidx.compose.ui.graphics.Color(color),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.size(38.dp).clickable { scope.launch { repository.setAccent(color.toInt()) } }
                ) {}
            }
        }
    }
}

@Composable
fun WhisperHomeScreen(songs: List<Song>, onPlay: (Song) -> Unit, onNavigate: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Good evening", style = MaterialTheme.typography.headlineMedium)
                Text("Your music, your way", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { onNavigate(Routes.SETTINGS) }) { Text("Settings") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Search" to Routes.SEARCH, "Library" to Routes.SONGS, "Playlists" to Routes.PLAYLISTS).forEach { (label, route) ->
                FilledTonalButton(onClick = { onNavigate(route) }) { Text(label) }
            }
        }
        Text("Recently added", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        if (songs.isEmpty()) {
            Text("Your local music will appear here after you grant audio permission.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn {
                items(songs.take(40)) { song ->
                    ListItem(
                        headlineContent = { Text(song.title, maxLines = 1) },
                        supportingContent = { Text(song.artist, maxLines = 1) },
                        leadingContent = { Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                            Box(Modifier.size(48.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("♫", style = MaterialTheme.typography.titleLarge) }
                        } },
                        trailingContent = { TextButton(onClick = { onPlay(song) }) { Text("Play") } },
                        modifier = Modifier.clickable { onPlay(song) }
                    )
                }
            }
        }
    }
}

@Composable
fun WhisperSearchScreen(songs: List<Song>, onPlay: (Song) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(songs, query) {
        if (query.isBlank()) emptyList() else songs.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Search your music", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            label = { Text("Songs or artists") }, singleLine = true
        )
        if (query.isBlank()) {
            Text("Search songs and artists in your device library.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
        } else if (filtered.isEmpty()) {
            Text("No matches found", modifier = Modifier.padding(top = 16.dp))
        } else {
            LazyColumn {
                items(filtered) { song ->
                    ListItem(
                        headlineContent = { Text(song.title, maxLines = 1) },
                        supportingContent = { Text(song.artist, maxLines = 1) },
                        modifier = Modifier.clickable { onPlay(song) }
                    )
                }
            }
        }
    }
}
