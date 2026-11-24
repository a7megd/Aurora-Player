package com.aurora.player.ui.navigation

import android.content.ContentValues
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.player.data.media.RecordingClassifier
import com.aurora.player.domain.model.Song
import java.util.Locale

private data class AudioEdit(val fileName: String, val title: String, val artist: String, val album: String, val year: String)

@Composable
fun WhisperFileManagerScreen(songs: List<Song>, recordingsOnly: Boolean = false, onRefresh: () -> Unit = {}) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    var editing by remember { mutableStateOf<Song?>(null) }
    var pendingEdit by remember { mutableStateOf<Pair<Song, AudioEdit>?>(null) }
    var pendingDelete by remember { mutableStateOf<Song?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    fun applyEdit(song: Song, edit: AudioEdit) {
        val ext = song.path.substringAfterLast('.', "mp3").ifBlank { "mp3" }
        val raw = edit.fileName.trim().ifBlank { edit.title.trim() }
        val base = raw.substringBeforeLast('.', raw)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, base + "." + ext)
            put(MediaStore.Audio.Media.TITLE, edit.title.trim())
            put(MediaStore.Audio.Media.ARTIST, edit.artist.trim())
            put(MediaStore.Audio.Media.ALBUM, edit.album.trim())
            put(MediaStore.Audio.Media.YEAR, edit.year.toIntOrNull() ?: 0)
        }
        val count = resolver.update(Uri.parse(song.contentUri), values, null, null)
        notice = if (count > 0) "تم حفظ التعديلات" else "لم يتم تعديل الملف"
        onRefresh()
    }

    val editLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val item = pendingEdit
        pendingEdit = null
        if (result.resultCode == android.app.Activity.RESULT_OK && item != null) {
            runCatching { applyEdit(item.first, item.second) }.onFailure { notice = "تعذر تعديل الملف: " + it.localizedMessage }
        } else notice = "لم يتم منح إذن تعديل الملف"
    }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val song = pendingDelete
        pendingDelete = null
        if (result.resultCode == android.app.Activity.RESULT_OK && song != null) {
            notice = "تم حذف الملف"
            onRefresh()
        } else notice = "تم إلغاء الحذف"
    }

    fun editSong(song: Song, edit: AudioEdit) {
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                val request = MediaStore.createWriteRequest(resolver, listOf(Uri.parse(song.contentUri)))
                pendingEdit = song to edit
                editLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            }.onFailure { notice = "تعذر طلب إذن التعديل: " + it.localizedMessage }
        } else runCatching { applyEdit(song, edit) }
            .onFailure { notice = "تعذر تعديل الملف: " + it.localizedMessage }
    }
    fun deleteSong(song: Song) {
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                val request = MediaStore.createDeleteRequest(resolver, listOf(Uri.parse(song.contentUri)))
                pendingDelete = song
                deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            }.onFailure { notice = "تعذر طلب إذن الحذف: " + it.localizedMessage }
        } else runCatching { resolver.delete(Uri.parse(song.contentUri), null, null) }
            .onSuccess { notice = if (it > 0) "تم حذف الملف" else "لم يتم حذف الملف"; onRefresh() }
            .onFailure { notice = "تعذر حذف الملف: " + it.localizedMessage }
    }

    val visible = remember(songs, recordingsOnly) {
        if (recordingsOnly) songs.filter { RecordingClassifier.isRecording(it) } else songs
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (recordingsOnly) "المجلد المخفي" else "إدارة الملفات الصوتية", style = MaterialTheme.typography.headlineSmall)
                    Text(visible.size.toString() + " ملف صوتي", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "تحديث") }
            }
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("لا توجد ملفات في هذا القسم", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.id }) { song ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (RecordingClassifier.isRecording(song)) Icons.Rounded.Mic else Icons.Rounded.AudioFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                Text(song.artist + " • " + song.album, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(song.path, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                            IconButton(onClick = { editing = song }) { Icon(Icons.Rounded.Edit, "تعديل الملف") }
                            IconButton(onClick = { pendingDelete = song }) { Icon(Icons.Rounded.Delete, "حذف الملف", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
    editing?.let { song ->
        var fileName by remember(song.id) { mutableStateOf(song.path.substringAfterLast('/').substringBeforeLast('.')) }
        var title by remember(song.id) { mutableStateOf(song.title) }
        var artist by remember(song.id) { mutableStateOf(song.artist) }
        var album by remember(song.id) { mutableStateOf(song.album) }
        var year by remember(song.id) { mutableStateOf(song.year.takeIf { it > 0 }?.toString().orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("تعديل الملف الصوتي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(fileName, { fileName = it }, label = { Text("اسم الملف") }, singleLine = true)
                    OutlinedTextField(title, { title = it }, label = { Text("عنوان الصوت") }, singleLine = true)
                    OutlinedTextField(artist, { artist = it }, label = { Text("الفنان / صاحب التسجيل") }, singleLine = true)
                    OutlinedTextField(album, { album = it }, label = { Text("الألبوم") }, singleLine = true)
                    OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("السنة") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { editSong(song, AudioEdit(fileName, title, artist, album, year)); editing = null },
                    enabled = fileName.isNotBlank() && title.isNotBlank()) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("إلغاء") } }
        )
    }
    pendingDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف الملف نهائيًا؟") },
            text = { Text("سيتم حذف «" + song.title + "» من الهاتف. سيطلب أندرويد تأكيد الحذف.") },
            confirmButton = { TextButton(onClick = { deleteSong(song) }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") } }
        )
    }
    LaunchedEffect(notice) {
        notice?.let { snackbar.showSnackbar(it); notice = null }
    }
}

data class ConvertedAudioRecord(
    val outputName: String,
    val sourceName: String,
    val convertedAt: Long,
    val bitrate: Int
)

object ConvertedAudioHistory {
    private const val PREFS = "whisper_converted_audio"
    private const val KEY = "history"

    fun load(context: Context): List<ConvertedAudioRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = org.json.JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                ConvertedAudioRecord(
                    item.optString("outputName"), item.optString("sourceName"),
                    item.optLong("convertedAt"), item.optInt("bitrate", 192)
                ).takeIf { it.outputName.isNotBlank() }
            }.sortedByDescending { it.convertedAt }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, record: ConvertedAudioRecord) {
        val existing = load(context).filterNot { it.outputName == record.outputName }
        val array = org.json.JSONArray()
        (listOf(record) + existing).take(200).forEach { item ->
            array.put(org.json.JSONObject().apply {
                put("outputName", item.outputName)
                put("sourceName", item.sourceName)
                put("convertedAt", item.convertedAt)
                put("bitrate", item.bitrate)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}

@Composable
fun WhisperDownloadsScreen(onOpenConverter: () -> Unit = {}) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(ConvertedAudioHistory.load(context)) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AudioFile, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("تحويلاتي الصوتية", style = MaterialTheme.typography.headlineMedium)
                Text("سجل ملفات الصوت الناتجة عن تحويل الفيديو", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { history = ConvertedAudioHistory.load(context) }) {
                Icon(Icons.Rounded.Refresh, "تحديث السجل")
            }
        }
        Button(onClick = onOpenConverter, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Icon(Icons.Rounded.VideoFile, null)
            Spacer(Modifier.width(8.dp))
            Text("اختيار فيديو وتحويله إلى MP3")
        }
        Text("سجل التحويلات", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        if (history.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.AudioFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(42.dp))
                    Text("لا توجد تحويلات بعد", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("ستظهر هنا ملفات MP3 التي تنشئها من الفيديوهات", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history, key = { it.outputName + it.convertedAt }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AudioFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(item.outputName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("من: " + item.sourceName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    java.text.SimpleDateFormat("yyyy/MM/dd • hh:mm a", Locale.getDefault()).format(java.util.Date(item.convertedAt)) + " • " + item.bitrate + " kbps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text("Music/Whisper Downloads", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

