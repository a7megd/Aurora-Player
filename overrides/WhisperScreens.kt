package com.aurora.player.ui.navigation

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.ui.text.font.FontWeight
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
    LaunchedEffect(controller) { while (true) { tick++; kotlinx.coroutines.delay(500) } }
    val count = controller?.mediaItemCount ?: 0
    val current = controller?.currentMediaItemIndex ?: -1
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Current playlist", style = MaterialTheme.typography.headlineMedium)
        Text("$count tracks in the queue", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedButton(onClick = { controller?.clearMediaItems() }, enabled = count > 0) { Text("Clear queue") }
        }
        LazyColumn {
            items(count) { index ->
                val item = controller?.getMediaItemAt(index)
                ListItem(
                    headlineContent = { Text(item?.mediaMetadata?.title?.toString().orEmpty().ifBlank { "Track ${index + 1}" }) },
                    supportingContent = { Text(item?.mediaMetadata?.artist?.toString().orEmpty()) },
                    leadingContent = { Text(if (index == current) "▶" else "${index + 1}") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { if (index > 0) controller?.moveMediaItem(index, index - 1) }, enabled = index > 0) { Text("↑") }
                            IconButton(onClick = { if (index < count - 1) controller?.moveMediaItem(index, index + 1) }, enabled = index < count - 1) { Text("↓") }
                            IconButton(onClick = { controller?.removeMediaItem(index) }) { Text("×") }
                        }
                    },
                    modifier = Modifier.clickable { controller?.seekTo(index, 0L); controller?.play() }
                )
            }
        }
    }
}

private data class TimedLyric(val timeMs: Long, val text: String)

private fun parseLrc(raw: String): List<TimedLyric> {
    val regex = Regex("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]")
    return raw.lineSequence().flatMap { line ->
        val matches = regex.findAll(line).toList()
        val lyric = line.replace(regex, "").trim()
        matches.map { match ->
            val minutes = match.groupValues[1].toLong()
            val seconds = match.groupValues[2].toLong()
            val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            TimedLyric((minutes * 60 + seconds) * 1000 + fraction, lyric)
        }
    }.sortedBy { it.timeMs }.toList()
}

@Composable
fun WhisperLyricsScreen(controller: MediaController?) {
    val context = LocalContext.current
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(controller) { while (true) { tick = controller?.currentPosition ?: 0L; kotlinx.coroutines.delay(180) } }
    val mediaId = controller?.currentMediaItem?.mediaId.orEmpty()
    val title = controller?.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
    val prefs = remember { context.getSharedPreferences("whisper_lyrics", Context.MODE_PRIVATE) }
    var lyrics by remember(mediaId) { mutableStateOf(prefs.getString(mediaId, "").orEmpty()) }
    val timed = remember(lyrics) { parseLrc(lyrics) }
    val active = timed.indexOfLast { it.timeMs <= tick }
    val importLrc = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val imported = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            if (!imported.isNullOrBlank()) {
                lyrics = imported
                if (mediaId.isNotBlank()) prefs.edit().putString(mediaId, imported).apply()
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Lyrics", style = MaterialTheme.typography.headlineMedium)
        Text(title.ifBlank { "No track selected" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedButton(onClick = { importLrc.launch("text/*") }) { Text("Import LRC / TXT") }
            OutlinedButton(onClick = { lyrics = ""; if (mediaId.isNotBlank()) prefs.edit().remove(mediaId).apply() }) { Text("Clear") }
        }
        if (timed.isNotEmpty()) {
            LazyColumn(Modifier.weight(1f)) {
                items(timed.size) { index ->
                    Text(
                        timed[index].text,
                        modifier = Modifier.fillMaxWidth().clickable { controller?.seekTo(timed[index].timeMs) }.padding(vertical = 10.dp),
                        color = if (index == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal,
                        style = if (index == active) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = lyrics,
                onValueChange = { lyrics = it; if (mediaId.isNotBlank()) prefs.edit().putString(mediaId, it).apply() },
                modifier = Modifier.fillMaxWidth().weight(1f),
                label = { Text("Paste or type song lyrics") },
                placeholder = { Text("Lyrics for this track…") }
            )
        }
        Text("LRC timestamps synchronize with playback. Plain text is saved locally.", style = MaterialTheme.typography.labelSmall)
    }
}


@Composable
private fun SleepTimerCard(controller: MediaController?) {
    var minutes by remember { mutableIntStateOf(15) }
    var running by remember { mutableStateOf(false) }
    var remaining by remember { mutableIntStateOf(0) }
    LaunchedEffect(running) {
        while (running && remaining > 0) {
            kotlinx.coroutines.delay(60_000)
            remaining--
        }
        if (running && remaining <= 0) { controller?.pause(); running = false }
    }
    Card(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Sleep timer", style = MaterialTheme.typography.titleMedium)
            Text(if (running) "$remaining minutes remaining" else "$minutes minutes")
            Slider(value = minutes.toFloat(), onValueChange = { minutes = it.toInt().coerceIn(1, 180) }, valueRange = 1f..180f, enabled = !running)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { remaining = minutes; running = true }, enabled = !running) { Text("Start") }
                OutlinedButton(onClick = { running = false; remaining = 0 }) { Text("Cancel") }
            }
        }
    }
}

@Composable
fun WhisperSettingsScreen(repository: SettingsRepository) {
    val settings by repository.settings.collectAsState(initial = com.aurora.player.domain.model.AppSettings())
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Appearance", style = MaterialTheme.typography.headlineMedium)
        SleepTimerCard(controller = null)
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
            listOf(0xFF168BFF, 0xFF7C5CFF, 0xFF00C2A8, 0xFFFF5C8A, 0xFFFFB020, 0xFF8BE04E).forEach { color ->
                Surface(
                    color = androidx.compose.ui.graphics.Color(color),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.size(38.dp).clickable { scope.launch { repository.setAccent(color.toInt()) } }
                ) {}
            }
        }
        Spacer(Modifier.height(28.dp))
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text("Whisper Player", style = MaterialTheme.typography.titleLarge)
            Text("By Ahmed Abdelhamid", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
