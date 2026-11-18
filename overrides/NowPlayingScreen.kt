package com.aurora.player.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.net.URLEncoder

@Composable
fun NowPlayingScreen(controller: MediaController?, onNavigate: (String) -> Unit = {}) {
    val context = LocalContext.current
    var position by remember(controller) { mutableLongStateOf(0L) }
    var duration by remember(controller) { mutableLongStateOf(0L) }
    var title by remember(controller) { mutableStateOf("") }
    var artist by remember(controller) { mutableStateOf("") }
    var album by remember(controller) { mutableStateOf("") }
    var artwork by remember(controller) { mutableStateOf<Uri?>(null) }
    var playing by remember(controller) { mutableStateOf(false) }
    var shuffle by remember(controller) { mutableStateOf(false) }
    var repeat by remember(controller) { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var speed by remember(controller) { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var timerMinutes by remember { mutableIntStateOf(30) }
    val sleepTimerPrefs = remember(context) { context.getSharedPreferences("whisper_sleep_timer", android.content.Context.MODE_PRIVATE) }
    var timerActive by remember { mutableStateOf(sleepTimerPrefs.getLong("deadline", 0L) > System.currentTimeMillis()) }
    var timerRemaining by remember { mutableIntStateOf(((sleepTimerPrefs.getLong("deadline", 0L) - System.currentTimeMillis()).coerceAtLeast(0L) / 60000L).toInt()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var playlistRefresh by remember { mutableIntStateOf(0) }
    val prefs = remember { context.getSharedPreferences("whisper_playlists", android.content.Context.MODE_PRIVATE) }
    val favoritePrefs = remember { context.getSharedPreferences("whisper_favorites", android.content.Context.MODE_PRIVATE) }
    val playlistNames = remember(playlistRefresh) { prefs.all.keys.filter { it.startsWith("playlist:") }.map { it.removePrefix("playlist:") }.sorted() }
    val currentItem = controller?.currentMediaItem
    val mediaId = currentItem?.mediaId.orEmpty()
    val sourceUri = currentItem?.localConfiguration?.uri
    val songId = sourceUri?.lastPathSegment?.toLongOrNull() ?: mediaId.toLongOrNull()

    fun searchLyrics() {
        val query = URLEncoder.encode("$title $artist كلمات الأغنية lyrics", "UTF-8")
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    LaunchedEffect(timerActive) {
        while (timerActive) {
            val deadline = sleepTimerPrefs.getLong("deadline", 0L)
            timerRemaining = ((deadline - System.currentTimeMillis()).coerceAtLeast(0L) / 60000L).toInt()
            if (deadline <= 0L || System.currentTimeMillis() >= deadline) {
                timerActive = false
                timerRemaining = 0
                break
            }
            delay(1000)
        }
    }
    LaunchedEffect(controller) {
        while (true) {
            controller?.let { player ->
                if (!dragging) position = player.currentPosition.coerceAtLeast(0L)
                duration = player.duration.takeIf { it > 0L } ?: 0L
                val metadata = player.currentMediaItem?.mediaMetadata
                title = metadata?.title?.toString().orEmpty()
                artist = metadata?.artist?.toString().orEmpty()
                album = metadata?.albumTitle?.toString().orEmpty()
                artwork = metadata?.artworkUri
                playing = player.isPlaying
                shuffle = player.shuffleModeEnabled
                repeat = player.repeatMode
                speed = player.playbackParameters.speed
            }
            delay(400)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text("تعمل الآن", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth()) {
            if (artwork != null) {
                AsyncImage(model = artwork, contentDescription = "غلاف الألبوم", modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(horizontal = 10.dp))
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(horizontal = 10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = "غلاف افتراضي", modifier = Modifier.size(112.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menuExpanded = true }, enabled = controller?.currentMediaItem != null) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "المزيد")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("إضافة إلى قائمة تشغيل") }, leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) }, onClick = { menuExpanded = false; showPlaylistPicker = true })
                    DropdownMenuItem(text = { Text("إضافة إلى المفضلة") }, leadingIcon = { Icon(Icons.Rounded.FavoriteBorder, null) }, onClick = {
                        if (songId != null) {
                            val ids = favoritePrefs.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
                            ids.add(songId.toString())
                            favoritePrefs.edit().putStringSet("ids", ids).apply()
                        }
                        menuExpanded = false
                    })
                    DropdownMenuItem(text = { Text("البحث عن كلمات الأغنية في Google") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, onClick = { menuExpanded = false; searchLyrics() })
                    DropdownMenuItem(text = { Text("مشاركة الأغنية") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = {
                        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "أستمع إلى $title - $artist عبر Tayer_player")
                        context.startActivity(Intent.createChooser(share, "مشاركة الأغنية").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        menuExpanded = false
                    })
                    DropdownMenuItem(text = { Text("تفاصيل الملف") }, leadingIcon = { Icon(Icons.Rounded.Info, null) }, onClick = { menuExpanded = false; showDetails = true })
                    DropdownMenuItem(text = { Text("فتح الكلمات داخل التطبيق") }, leadingIcon = { Icon(Icons.Rounded.MusicNote, null) }, onClick = { menuExpanded = false; onNavigate(Routes.LYRICS) })
                    DropdownMenuItem(text = { Text("قائمة التشغيل الحالية") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) }, onClick = { menuExpanded = false; onNavigate(Routes.QUEUE) })
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(title.ifBlank { "لا توجد أغنية قيد التشغيل" }, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(artist.ifBlank { "اختر أغنية من مكتبتك" }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (album.isNotBlank()) Text(album, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(22.dp))
        Slider(
            value = if (duration > 0L) position.coerceIn(0L, duration).toFloat() else 0f,
            onValueChange = { dragging = true; position = it.toLong() },
            onValueChangeFinished = { controller?.seekTo(position); dragging = false },
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            enabled = controller != null && duration > 0L
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), style = MaterialTheme.typography.labelSmall)
            Text(formatTime((duration - position).coerceAtLeast(0L)), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { controller?.shuffleModeEnabled = !shuffle }, enabled = controller != null) {
                Icon(Icons.Rounded.Shuffle, contentDescription = "تشغيل عشوائي", tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { controller?.seekToPreviousMediaItem() }, enabled = controller != null) { Icon(Icons.Rounded.SkipPrevious, "السابق", modifier = Modifier.size(34.dp)) }
            FilledIconButton(onClick = { if (playing) controller?.pause() else controller?.play() }, enabled = controller != null, modifier = Modifier.size(68.dp)) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "تشغيل أو إيقاف", modifier = Modifier.size(38.dp))
            }
            IconButton(onClick = { controller?.seekToNextMediaItem() }, enabled = controller != null) { Icon(Icons.Rounded.SkipNext, "التالي", modifier = Modifier.size(34.dp)) }
            IconButton(onClick = {
                controller?.repeatMode = when (repeat) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
            }, enabled = controller != null) {
                Icon(if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "تكرار", tint = if (repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onNavigate(Routes.QUEUE) }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "قائمة التشغيل") }
            IconButton(onClick = { onNavigate(Routes.LYRICS) }) { Icon(Icons.Rounded.MusicNote, "كلمات الأغنية") }
            IconButton(onClick = { showTimer = true }) { Icon(Icons.Rounded.Timer, "مؤقت النوم", tint = if (timerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = { onNavigate(Routes.EQUALIZER) }) { Icon(Icons.Rounded.Equalizer, "معادل الصوت") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Speed, contentDescription = "سرعة التشغيل")
            Spacer(Modifier.width(8.dp))
            listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { rate ->
                TextButton(onClick = { controller?.setPlaybackParameters(PlaybackParameters(rate)) }, enabled = controller != null) {
                    Text(if (rate == 1f) "1×" else "${rate}×", color = if (speed == rate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (timerActive) Text("المتبقي لإيقاف التشغيل: $timerRemaining دقيقة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

        Card(Modifier.fillMaxWidth().padding(top = 18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("تذكير", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(
                    "«لَيَكُونَنَّ مِنْ أُمَّتِي أَقْوَامٌ يَسْتَحِلُّونَ الْحِرَ وَالْحَرِيرَ وَالْخَمْرَ وَالْمَعَازِفَ»",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    lineHeight = MaterialTheme.typography.titleMedium.lineHeight
                )
                Spacer(Modifier.height(8.dp))
                Text("رواه البخاري في صحيحه، رقم (5590).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "يستدل جمهور العلماء بهذا الحديث على تحريم المعازف وآلات اللهو، ويحذّر الحديث من استحلال ما حرّمه الله. وتفاصيل المسألة وصورها محل بحث وخلاف فقهي؛ فارجع إلى أهل العلم في النوازل والاستثناءات.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dorar.net/h/w3FElCmU?osoul=1"))) }) {
                    Text("عرض التخريج والشرح من الموسوعة الحديثية")
                }
            }
        }
    }

    if (showPlaylistPicker) AlertDialog(
        onDismissRequest = { showPlaylistPicker = false },
        title = { Text("إضافة إلى قائمة تشغيل") },
        text = {
            Column {
                playlistNames.forEach { name ->
                    TextButton(onClick = {
                        val key = "playlist:$name"
                        val ids = prefs.getString(key, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toMutableSet()
                        songId?.let(ids::add)
                        prefs.edit().putString(key, ids.joinToString(",")).apply()
                        playlistRefresh++
                        showPlaylistPicker = false
                    }) { Text(name) }
                }
                if (playlistNames.isEmpty()) Text("لا توجد قوائم بعد. أنشئ قائمة جديدة.")
                TextButton(onClick = { showPlaylistPicker = false; showCreatePlaylist = true }) { Text("إنشاء قائمة جديدة") }
            }
        },
        confirmButton = { TextButton(onClick = { showPlaylistPicker = false }) { Text("إغلاق") } }
    )
    if (showCreatePlaylist) AlertDialog(
        onDismissRequest = { showCreatePlaylist = false },
        title = { Text("قائمة تشغيل جديدة") },
        text = { OutlinedTextField(value = newPlaylistName, onValueChange = { newPlaylistName = it }, label = { Text("اسم القائمة") }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = {
                val clean = newPlaylistName.trim()
                if (clean.isNotEmpty()) {
                    prefs.edit().putString("playlist:$clean", songId?.toString().orEmpty()).apply()
                    playlistRefresh++
                    newPlaylistName = ""
                    showCreatePlaylist = false
                }
            }, enabled = newPlaylistName.isNotBlank()) { Text("حفظ وإضافة") }
        },
        dismissButton = { TextButton(onClick = { showCreatePlaylist = false }) { Text("إلغاء") } }
    )
    if (showDetails) AlertDialog(
        onDismissRequest = { showDetails = false },
        title = { Text("تفاصيل الأغنية") },
        text = { Column { Text("العنوان: $title"); Text("الفنان: $artist"); Text("الألبوم: ${album.ifBlank { "غير معروف" }}"); Text("المعرّف: $mediaId"); Text("المصدر: ${sourceUri?.lastPathSegment ?: "غير متاح"}") } },
        confirmButton = { TextButton(onClick = { showDetails = false }) { Text("تم") } }
    )
    if (showTimer) AlertDialog(
        onDismissRequest = { showTimer = false },
        title = { Text("مؤقت إيقاف التشغيل") },
        text = {
            Column {
                Text(if (timerActive) "متبقي $timerRemaining دقيقة" else "$timerMinutes دقيقة")
                Slider(value = timerMinutes.toFloat(), onValueChange = { timerMinutes = it.toInt().coerceIn(1, 180) }, valueRange = 1f..180f, enabled = !timerActive)
            }
        },
        confirmButton = { TextButton(onClick = {
            timerRemaining = timerMinutes
            sleepTimerPrefs.edit().putLong("deadline", System.currentTimeMillis() + timerMinutes * 60_000L).apply()
            timerActive = true
            showTimer = false
        }) { Text("بدء") } },
        dismissButton = {
            Row {
                TextButton(onClick = { timerActive = false; timerRemaining = 0; sleepTimerPrefs.edit().remove("deadline").apply() }) { Text("إلغاء المؤقت") }
                TextButton(onClick = { showTimer = false }) { Text("إغلاق") }
            }
        }
    )
}

private fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000L
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
