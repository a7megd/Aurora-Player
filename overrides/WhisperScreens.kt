package com.aurora.player.ui.navigation

import android.content.Context
import android.net.Uri
import android.media.audiofx.Equalizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import java.util.Locale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import androidx.media3.common.Player
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
            Text("قوائم التشغيل", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { name = ""; selectedIds.clear(); showCreate = true }) { Text("إنشاء قائمة تشغيل") }
        }
        if (names.isEmpty()) Text("أنشئ أول قائمة تشغيل", modifier = Modifier.padding(top = 24.dp))
        LazyColumn {
            items(names) { playlist ->
                val ids = prefs.getString("playlist:$playlist", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
                val tracks = songs.filter { it.id in ids }
                ListItem(
                    headlineContent = { Text(playlist) },
                    supportingContent = { Text("${tracks.size} مقطعًا") },
                    trailingContent = { TextButton(onClick = { prefs.edit().remove("playlist:$playlist").apply(); refresh++ }) { Text("حذف") } },
                    modifier = Modifier.clickable { tracks.firstOrNull()?.let(onPlay) }
                )
            }
        }
    }
    if (showCreate) AlertDialog(
        onDismissRequest = { showCreate = false },
        title = { Text("إنشاء قائمة تشغيل") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم القائمة") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text("اختر المقاطع", style = MaterialTheme.typography.titleSmall)
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
                Text("تم اختيار ${selectedIds.size}", style = MaterialTheme.typography.labelSmall)
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
            }, enabled = name.isNotBlank() && selectedIds.isNotEmpty()) { Text("إنشاء") }
        },
        dismissButton = { TextButton(onClick = { showCreate = false }) { Text("إلغاء") } }
    )
}

@Composable
fun WhisperFavoritesScreen(songs: List<Song>, onPlay: (Song) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("whisper_favorites", Context.MODE_PRIVATE) }
    var refresh by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    val ids = remember(refresh) { prefs.getStringSet("ids", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet() }
    val favorites = songs.filter { it.id in ids }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("المفضلة", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { showAdd = true }) { Text("إضافة مقاطع") }
        }
        Text("${favorites.size} مقطعًا في المفضلة", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn {
            items(favorites) { song ->
                ListItem(
                    headlineContent = { Text(song.title, maxLines = 1) },
                    supportingContent = { Text(song.artist, maxLines = 1) },
                    trailingContent = { TextButton(onClick = {
                        prefs.edit().putStringSet("ids", (ids - song.id).map { it.toString() }.toSet()).apply()
                        refresh++
                    }) { Text("إزالة") } },
                    modifier = Modifier.clickable { onPlay(song) }
                )
            }
        }
    }
    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false },
        title = { Text("إضافة إلى المفضلة") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(songs) { song ->
                    val checked = song.id in ids
                    Row(Modifier.fillMaxWidth().clickable {
                        val next = if (checked) ids - song.id else ids + song.id
                        prefs.edit().putStringSet("ids", next.map { it.toString() }.toSet()).apply()
                        refresh++
                    }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = {
                            val next = if (it) ids + song.id else ids - song.id
                            prefs.edit().putStringSet("ids", next.map { id -> id.toString() }.toSet()).apply()
                            refresh++
                        })
                        Column {
                            Text(song.title, maxLines = 1)
                            Text(song.artist, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showAdd = false }) { Text("تم") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WhisperQueueScreen(controller: MediaController?, songs: List<Song>) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var selectedToAdd by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showSave by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("قائمة الانتظار") }
    val queueListState = rememberLazyListState()
    var menuExpanded by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("whisper_playlists", Context.MODE_PRIVATE) }
    LaunchedEffect(controller) { while (true) { tick++; kotlinx.coroutines.delay(350) } }
    val count = controller?.mediaItemCount ?: 0
    val current = controller?.currentMediaItemIndex ?: -1
    val queueIds = remember(tick, count) {
        (0 until count).mapNotNull { index ->
            controller?.getMediaItemAt(index)?.mediaId?.toLongOrNull()
                ?: controller?.getMediaItemAt(index)?.localConfiguration?.uri?.lastPathSegment?.toLongOrNull()
        }.toSet()
    }
    val shuffle = controller?.shuffleModeEnabled == true
    val repeat = controller?.repeatMode ?: Player.REPEAT_MODE_OFF
    fun mediaItem(song: Song) = androidx.media3.common.MediaItem.Builder()
        .setMediaId(song.id.toString())
        .setUri(android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id))
        .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).build())
        .build()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("قائمة الانتظار", style = MaterialTheme.typography.headlineMedium)
                Text("$count مقطعًا • ${if (current >= 0) "يعمل ${current + 1} من $count" else "لا يوجد تشغيل"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { showAdd = true }) { Icon(Icons.Rounded.Add, contentDescription = "إضافة مقاطع") }
            Box {
                IconButton(onClick = { menuExpanded = true }, enabled = count > 0) { Icon(Icons.Rounded.MoreVert, contentDescription = "خيارات القائمة") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("حفظ كقائمة تشغيل") }, leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) }, onClick = { showSave = true; menuExpanded = false })
                    DropdownMenuItem(text = { Text("مسح ما بعد المقطع الحالي") }, leadingIcon = { Icon(Icons.Rounded.ClearAll, null) }, onClick = {
                        if (controller != null) for (i in count - 1 downTo (current + 1).coerceAtLeast(0)) controller.removeMediaItem(i)
                        tick++; menuExpanded = false
                    }, enabled = current >= 0 && current < count - 1)
                    DropdownMenuItem(text = { Text("مسح قائمة الانتظار بالكامل") }, leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null) }, onClick = { controller?.clearMediaItems(); tick++; menuExpanded = false })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { controller?.shuffleModeEnabled = !shuffle; tick++ }, enabled = count > 1, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(6.dp)); Text(if (shuffle) "خلط مفعّل" else "خلط")
            }
            OutlinedButton(onClick = {
                controller?.repeatMode = when (repeat) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }; tick++
            }, enabled = count > 0, modifier = Modifier.weight(1f)) {
                Icon(if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, null)
                Spacer(Modifier.width(6.dp))
                Text(when (repeat) { Player.REPEAT_MODE_ALL -> "تكرار الكل"; Player.REPEAT_MODE_ONE -> "تكرار مقطع"; else -> "بدون تكرار" })
            }
        }
        if (count == 0) {
            Card(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.QueueMusic, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("قائمة الانتظار فارغة", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    Text("أضف مقاطع من مكتبتك أو شغّل ألبومًا لتبدأ.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { showAdd = true }, modifier = Modifier.padding(top = 12.dp)) { Text("إضافة مقاطع") }
                }
            }
        } else {
            LazyColumn(state = queueListState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(count, key = { index -> controller?.getMediaItemAt(index)?.mediaId ?: "queue_$index" }) { index ->
                    val item = controller?.getMediaItemAt(index)
                    val active = index == current
                    val title = item?.mediaMetadata?.title?.toString().orEmpty().ifBlank { "مقطع ${index + 1}" }
                    val artist = item?.mediaMetadata?.artist?.toString().orEmpty()
                    var dragDistance by remember { mutableFloatStateOf(0f) }
                    var dragging by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .animateContentSize()
                            .then(if (dragging) Modifier.graphicsLayer { translationY = dragDistance; scaleX = 1.025f; scaleY = 1.025f } else Modifier)
                            .pointerInput(index, count) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragging = true; dragDistance = 0f },
                                    onDrag = { change, amount -> change.consume(); dragDistance += amount.y },
                                    onDragEnd = {
                                        val start = queueListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == (controller?.getMediaItemAt(index)?.mediaId ?: "queue_$index") }
                                        val draggedCenter = (start?.offset ?: 0) + (start?.size ?: 0) / 2 + dragDistance.toInt()
                                        val target = queueListState.layoutInfo.visibleItemsInfo
                                            .minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - draggedCenter) }
                                            ?.index
                                        if (target != null && target != index) controller?.moveMediaItem(index, target)
                                        dragging = false
                                        dragDistance = 0f
                                        tick++
                                    },
                                    onDragCancel = { dragging = false; dragDistance = 0f }
                                )
                            }
                            .clickable { controller?.seekTo(index, 0L); controller?.play() }
                            .animateItemPlacement(),
                        colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)),
                        border = if (active || dragging) androidx.compose.foundation.BorderStroke(if (dragging) 2.dp else 1.dp, MaterialTheme.colorScheme.primary) else null,
                        elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 12.dp else 1.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                if (active) Icon(Icons.Rounded.Equalizer, null, tint = MaterialTheme.colorScheme.primary)
                                else Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 7.dp)) {
                                Text(title, maxLines = 1, style = MaterialTheme.typography.titleSmall, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Text(artist.ifBlank { " " }, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (active) Text("يعمل الآن", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.DragHandle, contentDescription = "اضغط مطولًا واسحب لأعلى أو لأسفل", tint = if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp).size(28.dp))
                                Row {
                                    IconButton(onClick = { controller?.addMediaItem((current + 1).coerceAtLeast(0).coerceAtMost(controller.mediaItemCount), item!!); tick++ }, enabled = item != null && index != current) { Icon(Icons.Rounded.SkipNext, "تشغيل بعد الحالي") }
                                    IconButton(onClick = { controller?.removeMediaItem(index); tick++ }) { Icon(Icons.Rounded.Close, "إزالة من الانتظار", tint = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) {
        val available = songs.filter { it.id !in queueIds }
        AlertDialog(
            onDismissRequest = { showAdd = false; selectedToAdd = emptySet() },
            title = { Text("إضافة إلى قائمة الانتظار") },
            text = {
                Column {
                    Text("${selectedToAdd.size} مقطع محدد", color = MaterialTheme.colorScheme.primary)
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(available, key = { it.id }) { song ->
                            Row(Modifier.fillMaxWidth().clickable {
                                selectedToAdd = if (song.id in selectedToAdd) selectedToAdd - song.id else selectedToAdd + song.id
                            }.padding(vertical = 5.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Checkbox(checked = song.id in selectedToAdd, onCheckedChange = { checked ->
                                    selectedToAdd = if (checked) selectedToAdd + song.id else selectedToAdd - song.id
                                })
                                Column(Modifier.weight(1f)) {
                                    Text(song.title, maxLines = 1)
                                    Text(song.artist, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedToAdd.forEach { id -> songs.firstOrNull { it.id == id }?.let { controller?.addMediaItem(mediaItem(it)) } }
                    if (controller?.playWhenReady == false && current < 0) controller?.prepare()
                    tick++; selectedToAdd = emptySet(); showAdd = false
                }, enabled = selectedToAdd.isNotEmpty()) { Text("إضافة (${selectedToAdd.size})") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false; selectedToAdd = emptySet() }) { Text("إلغاء") } }
        )
    }
    if (showSave) AlertDialog(
        onDismissRequest = { showSave = false },
        title = { Text("حفظ قائمة الانتظار") },
        text = { OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, label = { Text("اسم القائمة") }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = {
                val ids = (0 until (controller?.mediaItemCount ?: 0)).mapNotNull { index ->
                    val media = controller?.getMediaItemAt(index)
                    media?.mediaId?.toLongOrNull() ?: media?.localConfiguration?.uri?.lastPathSegment?.toLongOrNull()
                }.distinct()
                if (playlistName.isNotBlank() && ids.isNotEmpty()) prefs.edit().putString("playlist:${playlistName.trim()}", ids.joinToString(",")).apply()
                showSave = false
            }, enabled = playlistName.isNotBlank() && count > 0) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = { showSave = false }) { Text("إلغاء") } }
    )
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
        Text("كلمات الأغنية", style = MaterialTheme.typography.headlineMedium)
        Text(title.ifBlank { "لم يتم اختيار أغنية" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedButton(onClick = { importLrc.launch("text/*") }) { Text("استيراد كلمات LRC أو TXT") }
            OutlinedButton(onClick = { lyrics = ""; if (mediaId.isNotBlank()) prefs.edit().remove(mediaId).apply() }) { Text("مسح") }
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
                label = { Text("ألصق كلمات الأغنية أو اكتبها") },
                placeholder = { Text("كلمات هذا المقطع…") }
            )
        }
        Text("تتزامن كلمات LRC مع التشغيل، ويُحفظ النص العادي على الجهاز.", style = MaterialTheme.typography.labelSmall)
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
            Text("مؤقت النوم", style = MaterialTheme.typography.titleMedium)
            Text(if (running) "متبقي $remaining دقيقة" else "$minutes دقيقة")
            Slider(value = minutes.toFloat(), onValueChange = { minutes = it.toInt().coerceIn(1, 180) }, valueRange = 1f..180f, enabled = !running)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { remaining = minutes; running = true }, enabled = !running) { Text("بدء") }
                OutlinedButton(onClick = { running = false; remaining = 0 }) { Text("إلغاء") }
            }
        }
    }
}

@Composable
fun WhisperEqualizerScreen() {
    val effect = remember { runCatching { Equalizer(0, 0).apply { enabled = true } }.getOrNull() }
    var enabled by remember(effect) { mutableStateOf(effect?.enabled == true) }
    var levels by remember(effect) { mutableStateOf<List<Float>>(effect?.let { eq -> (0 until eq.numberOfBands.toInt()).map { band -> eq.getBandLevel(band.toShort()).toFloat() } } ?: emptyList()) }
    val presetNames = remember(effect) { effect?.let { eq -> (0 until eq.numberOfPresets.toInt()).map { eq.getPresetName(it.toShort()).toString() } } ?: emptyList() }
    var presetExpanded by remember { mutableStateOf(false) }
    val range = remember(effect) { effect?.bandLevelRange?.let { it[0].toFloat()..it[1].toFloat() } ?: (-1500f..1500f) }
    DisposableEffect(effect) { onDispose { runCatching { effect?.release() } } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("موازن الصوت", style = MaterialTheme.typography.headlineMedium)
        Text("اضبط ترددات الصوت أثناء التشغيل.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp, bottom = 14.dp))
        if (effect == null) {
            Text("تعذّر تشغيل موازن الصوت على هذا الجهاز. تحقق من دعم الجهاز للمؤثرات الصوتية.", color = MaterialTheme.colorScheme.error)
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("تفعيل موازن الصوت", style = MaterialTheme.typography.titleMedium)
                            Text("تأثير صوت النظام", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enabled, onCheckedChange = { enabled = it; runCatching { effect.enabled = it } })
                    }
                    Spacer(Modifier.height(10.dp))
                    Box {
                        OutlinedButton(onClick = { presetExpanded = true }, enabled = presetNames.isNotEmpty()) { Text("اختيار نمط جاهز") }
                        DropdownMenu(expanded = presetExpanded, onDismissRequest = { presetExpanded = false }) {
                            presetNames.forEachIndexed { index, name ->
                                DropdownMenuItem(text = { Text(name) }, onClick = {
                                    runCatching { effect.usePreset(index.toShort()) }
                                    levels = (0 until effect.numberOfBands.toInt()).map { effect.getBandLevel(it.toShort()).toFloat() }
                                    enabled = true
                                    runCatching { effect.enabled = true }
                                    presetExpanded = false
                                })
                            }
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("مستويات التردد", style = MaterialTheme.typography.titleMedium)
                    levels.forEachIndexed { index, level ->
                        val hz = runCatching { effect.getCenterFreq(index.toShort()).toInt() / 1000f }.getOrDefault(0f)
                        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (hz >= 1f) "${hz.toInt()} kHz" else "${(hz * 1000).toInt()} Hz", style = MaterialTheme.typography.labelMedium)
                                Text("${level.toInt()} mB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Slider(value = level, onValueChange = { value ->
                                val next = levels.toMutableList(); next[index] = value; levels = next
                                runCatching { effect.setBandLevel(index.toShort(), value.toInt().toShort()) }
                            }, valueRange = range, enabled = enabled)
                        }
                    }
                    Text("إعادة الضبط", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable {
                        levels.indices.forEach { runCatching { effect.setBandLevel(it.toShort(), 0) } }; levels = levels.map { 0f }
                    }.padding(top = 10.dp))
                }
            }
            Text("يعتمد تأثير موازن الصوت على دعم إصدار Android والجهاز لمؤثرات الصوت.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
fun WhisperSettingsScreen(repository: SettingsRepository, controller: MediaController?, onWallpaperChanged: (String?) -> Unit = {}) {
    val settings by repository.settings.collectAsState(initial = com.aurora.player.domain.model.AppSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val wallpaperPrefs = remember { context.getSharedPreferences("whisper_preferences", Context.MODE_PRIVATE) }
    var wallpaper by remember { mutableStateOf(wallpaperPrefs.getString("wallpaper_uri", null)) }
    val profilePrefs = remember { context.getSharedPreferences("tayer_profile", Context.MODE_PRIVATE) }
    var profileName by remember { mutableStateOf(profilePrefs.getString("display_name", "").orEmpty()) }
    var profilePhoto by remember { mutableStateOf(profilePrefs.getString("photo_uri", null)) }
    val profilePhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            profilePhoto = uri.toString()
            profilePrefs.edit().putString("photo_uri", profilePhoto).apply()
        }
    }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            wallpaper = uri.toString()
            wallpaperPrefs.edit().putString("wallpaper_uri", wallpaper).apply()
            onWallpaperChanged(wallpaper)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text("تخصيص المظهر", style = MaterialTheme.typography.headlineMedium)
        }
        Text("تحكم في ألوان التطبيق وطريقة عرضه بما يناسبك.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        Card(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("الملف الشخصي", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                    if (profilePhoto != null) {
                        AsyncImage(model = profilePhoto, contentDescription = "الصورة الشخصية", contentScale = ContentScale.Crop,
                            modifier = Modifier.size(68.dp).clip(RoundedCornerShape(50)))
                    } else {
                        Surface(modifier = Modifier.size(68.dp), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Box(contentAlignment = androidx.compose.ui.Alignment.Center) { Icon(Icons.Rounded.Person, null, modifier = Modifier.size(34.dp)) }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(value = profileName, onValueChange = {
                            profileName = it
                            profilePrefs.edit().putString("display_name", it).apply()
                        }, label = { Text("اسمك داخل التطبيق") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    OutlinedButton(onClick = { profilePhotoPicker.launch(arrayOf("image/*")) }) { Text("رفع صورة شخصية") }
                    if (profilePhoto != null) TextButton(onClick = {
                        profilePhoto = null
                        profilePrefs.edit().remove("photo_uri").apply()
                    }) { Text("إزالة الصورة") }
                }
                Text("الاسم والصورة اختياريان ويُحفظان على جهازك.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DarkMode, null)
                    Spacer(Modifier.width(8.dp))
                    Text("نمط العرض", style = MaterialTheme.typography.titleMedium)
                }
                listOf(ThemeMode.SYSTEM to "حسب إعداد الجهاز", ThemeMode.DARK to "داكن", ThemeMode.LIGHT to "فاتح").forEach { (mode, label) ->
                    Row(Modifier.fillMaxWidth().clickable { scope.launch { repository.setThemeMode(mode) } }.padding(vertical = 5.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = settings.themeMode == mode, onClick = { scope.launch { repository.setThemeMode(mode) } })
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth().clickable { scope.launch { repository.setAmoled(!settings.amoled) } }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Contrast, null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("الوضع الأسود العميق")
                        Text("خلفية سوداء مناسبة لشاشات AMOLED", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.amoled, onCheckedChange = { scope.launch { repository.setAmoled(it) } })
                }
            }
        }
        Card(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ColorLens, null)
                    Spacer(Modifier.width(8.dp))
                    Text("اللون الرئيسي", style = MaterialTheme.typography.titleMedium)
                }
                Text("اختر اللون الذي يظهر في الأزرار والعناصر النشطة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(0xFF168BFF, 0xFF7C5CFF, 0xFF00C2A8, 0xFFFF5C8A, 0xFFFFB020, 0xFF8BE04E).forEach { color ->
                        Surface(
                            color = androidx.compose.ui.graphics.Color(color),
                            shape = MaterialTheme.shapes.extraLarge,
                            border = if (settings.accentColor == color.toInt()) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                            modifier = Modifier.size(38.dp).clickable { scope.launch { repository.setAccent(color.toInt()) } }
                        ) {}
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Image, null)
                    Spacer(Modifier.width(8.dp))
                    Text("خلفية الواجهة", style = MaterialTheme.typography.titleMedium)
                }
                Text(if (wallpaper == null) "لا توجد خلفية مخصصة" else "تم اختيار خلفية مخصصة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (wallpaper != null) AsyncImage(model = wallpaper, contentDescription = "معاينة الخلفية", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(16.dp)))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { wallpaperPicker.launch(arrayOf("image/*")) }) { Text("اختيار صورة") }
                    if (wallpaper != null) OutlinedButton(onClick = { wallpaper = null; wallpaperPrefs.edit().remove("wallpaper_uri").apply(); onWallpaperChanged(null) }) { Text("إزالة الخلفية") }
                }
            }
        }
        Card(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tune, null)
                    Spacer(Modifier.width(8.dp))
                    Text("أدوات التشغيل", style = MaterialTheme.typography.titleMedium)
                }
                Text("يمكنك ضبط مؤقت النوم من شاشة تشغيل الأغنية.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Text("Whisper Player", style = MaterialTheme.typography.titleLarge)
                Text("تطوير: أحمد عبد الحميد", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("مشغل موسيقى محلي", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun normalizeMusicSearch(value: String): String {
    return value.lowercase(Locale.ROOT)
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .replace(Regex("[أإآٱ]"), "ا")
        .replace("ى", "ي")
        .replace("ة", "ه")
        .replace("ؤ", "و")
        .replace("ئ", "ي")
        .replace(Regex("\\s+"), " ")
        .trim()
}

@Composable
private fun SongLongPressRow(
    song: Song,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    activeSongId: Long? = null
) {
    val active = activeSongId == song.id
    val bubbleColor by animateColorAsState(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f), label = "songBubbleColor")
    val borderColor by animateColorAsState(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.88f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), label = "songBubbleBorder")
    val elevation = if (active) 7.dp else 1.dp
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).animateContentSize()
            .border(if (active) 1.5.dp else 0.7.dp, borderColor, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = bubbleColor,
        tonalElevation = elevation,
        shadowElevation = elevation
    ) {
    ListItem(
        headlineContent = { Text(song.title, maxLines = 1) },
        supportingContent = { Text(song.artist, maxLines = 1) },
        leadingContent = {
            Surface(color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), shape = RoundedCornerShape(15.dp)) {
                Box(Modifier.size(48.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    if (active) Icon(Icons.Rounded.MusicNote, contentDescription = "الأغنية تعمل الآن", tint = MaterialTheme.colorScheme.primary)
                    else Text("♫", style = MaterialTheme.typography.titleLarge)
                }
            }
        },
        trailingContent = {
            if (active) Icon(Icons.Rounded.PlayCircle, contentDescription = "الأغنية الحالية", tint = MaterialTheme.colorScheme.primary)
            else Icon(Icons.Rounded.MoreVert, contentDescription = "خيارات الأغنية", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.pointerInput(song.id) {
            detectTapGestures(onTap = { onPlay() }, onLongPress = { onLongPress() })
        }
    )
    }
}

@Composable
private fun SongContextDialog(
    song: Song,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onFavorite: () -> Unit,
    onRadio: () -> Unit,
    onLyrics: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("whisper_playlists", Context.MODE_PRIVATE) }
    var choosePlaylist by remember { mutableStateOf(false) }
    var createPlaylist by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    val names = remember(refresh) { prefs.all.keys.filter { it.startsWith("playlist:") }.map { it.removePrefix("playlist:") }.sorted() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(song.title, maxLines = 2) },
        text = {
            Column {
                Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onPlayNext(); onDismiss() }) { Icon(Icons.Rounded.SkipNext, null); Spacer(Modifier.width(8.dp)); Text("تشغيل بعد الحالي") }
                TextButton(onClick = { choosePlaylist = true }) { Icon(Icons.Rounded.PlaylistAdd, null); Spacer(Modifier.width(8.dp)); Text("إضافة إلى قائمة تشغيل") }
                TextButton(onClick = { onFavorite(); onDismiss() }) { Icon(Icons.Rounded.FavoriteBorder, null); Spacer(Modifier.width(8.dp)); Text("إضافة إلى المفضلة") }
                TextButton(onClick = { onRadio(); onDismiss() }) { Icon(Icons.Rounded.MusicNote, null); Spacer(Modifier.width(8.dp)); Text("تشغيل مقاطع مشابهة للفنان") }
                TextButton(onClick = { onLyrics(); onDismiss() }) { Icon(Icons.Rounded.MusicNote, null); Spacer(Modifier.width(8.dp)); Text("فتح كلمات الأغنية") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
    if (choosePlaylist) AlertDialog(
        onDismissRequest = { choosePlaylist = false },
        title = { Text("إضافة إلى قائمة تشغيل") },
        text = {
            Column {
                names.forEach { name ->
                    TextButton(onClick = {
                        val key = "playlist:$name"
                        val ids = prefs.getString(key, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toMutableSet()
                        ids.add(song.id)
                        prefs.edit().putString(key, ids.joinToString(",")).apply()
                        choosePlaylist = false
                        onDismiss()
                    }) { Text(name) }
                }
                if (names.isEmpty()) Text("لا توجد قوائم بعد. يمكنك إنشاء قائمة جديدة.")
                TextButton(onClick = { createPlaylist = true; choosePlaylist = false }) { Text("إنشاء قائمة جديدة") }
            }
        },
        confirmButton = { TextButton(onClick = { choosePlaylist = false }) { Text("إلغاء") } }
    )
    if (createPlaylist) AlertDialog(
        onDismissRequest = { createPlaylist = false },
        title = { Text("قائمة جديدة") },
        text = { OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, label = { Text("اسم القائمة") }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = {
                val name = playlistName.trim()
                if (name.isNotEmpty()) {
                    prefs.edit().putString("playlist:$name", song.id.toString()).apply()
                    refresh++
                    createPlaylist = false
                    onDismiss()
                }
            }, enabled = playlistName.isNotBlank()) { Text("إنشاء وإضافة") }
        },
        dismissButton = { TextButton(onClick = { createPlaylist = false }) { Text("إلغاء") } }
    )
}

@Composable
fun WhisperSongListScreen(
    songs: List<Song>,
    onPlay: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onRadio: (Song) -> Unit,
    onFavorite: (Song) -> Unit,
    onNavigate: (String) -> Unit,
    activeSongId: Long? = null
) {
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("whisper_preferences", Context.MODE_PRIVATE) }
    var sort by remember { mutableStateOf(prefs.getString("song_sort", "newest") ?: "newest") }
    val sortedSongs = remember(songs, sort) { when (sort) {
        "oldest" -> songs.sortedBy { it.id }
        "name" -> songs.sortedBy { it.title.lowercase(Locale.ROOT) }
        "artist" -> songs.sortedBy { it.artist.lowercase(Locale.ROOT) }
        "plays" -> songs.sortedByDescending { prefs.getInt("plays:" + it.id, 0) }
        else -> songs.sortedByDescending { it.id }
    } }
    val listState = rememberLazyListState()
    LaunchedEffect(activeSongId, sortedSongs) {
        val activeIndex = sortedSongs.indexOfFirst { it.id == activeSongId }
        if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("كل الأصوات", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 16.dp))
            var expanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { expanded = true }) { Icon(Icons.Rounded.Sort, contentDescription = "ترتيب") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf("newest" to "الأحدث", "oldest" to "الأقدم", "name" to "الاسم", "artist" to "الفنان", "plays" to "الأكثر تشغيلًا").forEach { (key, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { sort = key; prefs.edit().putString("song_sort", key).apply(); expanded = false })
                    }
                }
            }
        }
        LazyColumn(state = listState) {
            items(sortedSongs) { song ->
                SongLongPressRow(song, onPlay = { onPlay(song) }, onLongPress = { selectedSong = song }, activeSongId = activeSongId)
            }
        }
    }
    selectedSong?.let { song ->
        SongContextDialog(song, onDismiss = { selectedSong = null },
            onPlayNext = { onPlayNext(song) }, onFavorite = { onFavorite(song) },
            onRadio = { onRadio(song) }, onLyrics = { onNavigate(Routes.LYRICS) })
    }
}

@Composable
fun WhisperHomeScreen(
    songs: List<Song>,
    onPlay: (Song) -> Unit,
    onNavigate: (String) -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onRadio: (Song) -> Unit = {},
    onFavorite: (Song) -> Unit = {},
    activeSongId: Long? = null
) {
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    val profilePrefs = remember { LocalContext.current.getSharedPreferences("tayer_profile", Context.MODE_PRIVATE) }
    val displayName = profilePrefs.getString("display_name", "").orEmpty().trim()
    val profilePhoto = profilePrefs.getString("photo_uri", null)
    val homeSongs = remember(songs) { songs.take(40) }
    val listState = rememberLazyListState()
    LaunchedEffect(activeSongId, homeSongs) {
        val activeIndex = homeSongs.indexOfFirst { it.id == activeSongId }
        if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Surface(color = Color(0xFF17191D), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("🇵🇸", style = MaterialTheme.typography.titleMedium)
                Text("فلسطين في القلب", color = Color(0xFFF2F0E9), style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { listOf(Color(0xFF111111), Color.White, Color(0xFFCE1126), Color(0xFF007A3D)).forEach { stripe -> Box(Modifier.width(8.dp).height(18.dp).background(stripe, RoundedCornerShape(2.dp))) } }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(if (displayName.isBlank()) "مساء الخير" else "أهلاً، $displayName", style = MaterialTheme.typography.headlineMedium)
                Text("الصوت، بطريقتك", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("by dr Ahmed Abdelhamid", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (profilePhoto != null) AsyncImage(model = profilePhoto, contentDescription = "الصورة الشخصية", contentScale = ContentScale.Crop, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(50.dp)))
                IconButton(onClick = { onNavigate(Routes.SETTINGS) }) { Icon(Icons.Rounded.Settings, contentDescription = "الإعدادات") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("بحث", Routes.SEARCH, Icons.Rounded.Search),
                Triple("المكتبة", Routes.SONGS, Icons.Rounded.LibraryMusic),
                Triple("قوائم التشغيل", Routes.PLAYLISTS, Icons.Rounded.QueueMusic),
                Triple("المفضلة", Routes.FAVORITES, Icons.Rounded.FavoriteBorder)
            ).forEach { (label, route, icon) ->
                IconButton(onClick = { onNavigate(route) }) { Icon(icon, contentDescription = label) }
            }
        }
        Text("الصوت", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        if (songs.isEmpty()) {
            Text("ستظهر ملفاتك الصوتية هنا بعد السماح بالوصول إلى الموسيقى.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(state = listState) {
                items(homeSongs) { song ->
                    SongLongPressRow(song, onPlay = { onPlay(song) }, onLongPress = { selectedSong = song }, activeSongId = activeSongId)
                }
            }
        }
    }
    selectedSong?.let { song ->
        SongContextDialog(song, onDismiss = { selectedSong = null },
            onPlayNext = { onPlayNext(song) }, onFavorite = { onFavorite(song) },
            onRadio = { onRadio(song) }, onLyrics = { onNavigate(Routes.LYRICS) })
    }
}

@Composable
fun WhisperSearchScreen(
    songs: List<Song>,
    onPlay: (Song) -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onRadio: (Song) -> Unit = {},
    onFavorite: (Song) -> Unit = {},
    activeSongId: Long? = null
) {
    var query by remember { mutableStateOf("") }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    val normalizedQuery = remember(query) { normalizeMusicSearch(query) }
    val filtered = remember(songs, normalizedQuery) {
        if (normalizedQuery.isBlank()) emptyList() else {
            val terms = normalizedQuery.split(" ").filter { it.isNotBlank() }
            songs.filter { song ->
                val searchable = normalizeMusicSearch("${song.title} ${song.artist} ${song.id}")
                terms.all { term -> searchable.contains(term) }
            }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(activeSongId, filtered) {
        val activeIndex = filtered.indexOfFirst { it.id == activeSongId }
        if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("ابحث في مكتبتك", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            label = { Text("اسم الأغنية أو الفنان") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "مسح البحث") } }, singleLine = true
        )
        if (query.isBlank()) {
            Text("اكتب أي جزء من اسم الأغنية أو الفنان للبحث الفوري.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
        } else if (filtered.isEmpty()) {
            Text("لا توجد نتائج مطابقة", modifier = Modifier.padding(top = 16.dp))
        } else {
            Text("${filtered.size} نتيجة", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            LazyColumn(state = listState) {
                items(filtered) { song ->
                    SongLongPressRow(song, onPlay = { onPlay(song) }, onLongPress = { selectedSong = song })
                }
            }
        }
    }
    selectedSong?.let { song ->
        SongContextDialog(song, onDismiss = { selectedSong = null },
            onPlayNext = { onPlayNext(song) }, onFavorite = { onFavorite(song) },
            onRadio = { onRadio(song) }, onLyrics = { onNavigate(Routes.LYRICS) })
    }
}
