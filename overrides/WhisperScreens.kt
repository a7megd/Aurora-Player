package com.aurora.player.ui.navigation

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import java.util.Locale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable
fun WhisperQueueScreen(controller: MediaController?) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(controller) { while (true) { tick++; kotlinx.coroutines.delay(500) } }
    val count = controller?.mediaItemCount ?: 0
    val current = controller?.currentMediaItemIndex ?: -1
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("قائمة التشغيل الحالية", style = MaterialTheme.typography.headlineMedium)
        Text("$count مقطعًا في قائمة الانتظار", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedButton(onClick = { controller?.clearMediaItems() }, enabled = count > 0) { Text("مسح قائمة الانتظار") }
        }
        LazyColumn {
            items(count) { index ->
                val item = controller?.getMediaItemAt(index)
                ListItem(
                    headlineContent = { Text(item?.mediaMetadata?.title?.toString().orEmpty().ifBlank { "مقطع ${index + 1}" }) },
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
fun WhisperSettingsScreen(repository: SettingsRepository, controller: MediaController?) {
    val settings by repository.settings.collectAsState(initial = com.aurora.player.domain.model.AppSettings())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val wallpaperPrefs = remember { context.getSharedPreferences("whisper_preferences", Context.MODE_PRIVATE) }
    var wallpaper by remember { mutableStateOf(wallpaperPrefs.getString("wallpaper_uri", null)) }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            wallpaper = uri.toString()
            wallpaperPrefs.edit().putString("wallpaper_uri", wallpaper).apply()
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text("تخصيص المظهر", style = MaterialTheme.typography.headlineMedium)
        }
        Text("تحكم في ألوان التطبيق وطريقة عرضه بما يناسبك.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { wallpaperPicker.launch(arrayOf("image/*")) }) { Text("اختيار صورة") }
                    if (wallpaper != null) OutlinedButton(onClick = { wallpaper = null; wallpaperPrefs.edit().remove("wallpaper_uri").apply() }) { Text("إزالة الخلفية") }
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
                    if (active) Icon(Icons.Rounded.GraphicEq, contentDescription = "الأغنية تعمل الآن", tint = MaterialTheme.colorScheme.primary)
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
                TextButton(onClick = { onRadio(); onDismiss() }) { Icon(Icons.Rounded.GraphicEq, null); Spacer(Modifier.width(8.dp)); Text("تشغيل مقاطع مشابهة للفنان") }
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
        LazyColumn {
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
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("مساء الخير", style = MaterialTheme.typography.headlineMedium)
                Text("الصوت، بطريقتك", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("تطوير: أحمد عبد الحميد", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { onNavigate(Routes.SETTINGS) }) { Icon(Icons.Rounded.Settings, contentDescription = "الإعدادات") }
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
            LazyColumn {
                items(songs.take(40)) { song ->
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
            LazyColumn {
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
