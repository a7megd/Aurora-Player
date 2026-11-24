package com.aurora.player.ui.navigation

import android.app.DownloadManager
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
import kotlinx.coroutines.delay
import java.net.URLDecoder
import java.util.Locale

private data class AudioEdit(val fileName: String, val title: String, val artist: String, val album: String, val year: String)
private data class DownloadRow(val id: Long, val title: String, val status: Int, val done: Long, val total: Long, val localUri: String?)

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

@Composable
fun WhisperDownloadsScreen(onOpenConverter: () -> Unit = {}) {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }
    val prefs = remember { context.getSharedPreferences("whisper_downloads", Context.MODE_PRIVATE) }
    var link by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var autoStart by remember { mutableStateOf(prefs.getBoolean("auto_start", false)) }
    var tab by remember { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf<List<DownloadRow>>(emptyList()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var lastAuto by remember { mutableStateOf("") }
    val youtubeUri = remember(link) {
        runCatching {
            val parsed = Uri.parse(link.trim())
            val host = parsed.host?.lowercase(Locale.ROOT).orEmpty()
            parsed.takeIf { it.scheme in listOf("http", "https") && (host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be" || host.endsWith(".youtu.be")) }
        }.getOrNull()
    }

    fun startDownload(raw: String) {
        val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull()
        val host = uri?.host?.lowercase(Locale.ROOT).orEmpty()
        if (uri == null || uri.scheme !in listOf("http", "https") || host.isBlank()) {
            notice = "الصق رابط تنزيل مباشر صالحًا يبدأ بـ https://"; return
        }
        if (host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be" || host.endsWith(".youtu.be")) {
            notice = "لا يمكن استخراج الصوت من يوتيوب هنا. استخدم تنزيل يوتيوب الرسمي أو رابط ملف تملك حق تنزيله."; return
        }
        val path = uri.lastPathSegment.orEmpty()
        val ext = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext !in setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "amr", "3gp", "wma")) {
            notice = "الرابط ليس ملفًا صوتيًا مباشرًا بصيغة مدعومة (MP3/M4A/AAC/FLAC/WAV/OGG/OPUS/AMR)."; return
        }
        val decoded = runCatching { URLDecoder.decode(path.substringBeforeLast('.'), "UTF-8") }.getOrDefault(path.substringBeforeLast('.'))
        val safeName = (name.trim().ifBlank { decoded.ifBlank { "Whisper Audio" } })
            .replace(Regex("[\\\\/:*?\"<>|]"), "_").take(90)
        runCatching {
            val request = DownloadManager.Request(uri)
                .setTitle(safeName)
                .setDescription("Whisper Player • تنزيل صوتي")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "Whisper Downloads/" + safeName + "." + ext)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            manager.enqueue(request)
            link = ""; name = ""; notice = "أُضيف الملف إلى قائمة التنزيلات"
        }.onFailure { notice = "تعذر بدء التنزيل: " + it.localizedMessage }
    }

    fun refresh(): List<DownloadRow> {
        val found = mutableListOf<DownloadRow>()
        val statuses = DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_SUCCESSFUL or DownloadManager.STATUS_FAILED
        manager.query(DownloadManager.Query().setFilterByStatus(statuses))?.use { cursor ->
            val id = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
            val title = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val status = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val done = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val total = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val local = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            while (cursor.moveToNext()) found += DownloadRow(
                cursor.getLong(id), cursor.getString(title).orEmpty(), cursor.getInt(status),
                cursor.getLong(done), cursor.getLong(total), if (cursor.isNull(local)) null else cursor.getString(local)
            )
        }
        return found.sortedByDescending { it.id }
    }

    LaunchedEffect(Unit) { while (true) { rows = refresh(); delay(900) } }
    LaunchedEffect(link, autoStart) {
        val candidate = link.trim()
        if (autoStart && youtubeUri == null && candidate != lastAuto && (candidate.startsWith("http://") || candidate.startsWith("https://")) && Uri.parse(candidate).host != null) {
            lastAuto = candidate
            delay(500)
            if (link.trim() == candidate) startDownload(candidate)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("التنزيلات", style = MaterialTheme.typography.headlineMedium)
                Text("تنزيل الملفات الصوتية المباشرة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = onOpenConverter, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Icon(Icons.Rounded.AudioFile, null)
            Spacer(Modifier.width(8.dp))
            Text("اختيار فيديو وتحويله إلى MP3")
        }
        Card(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("إضافة رابط", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = link, onValueChange = { link = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("صندوق لصق الرابط") }, placeholder = { Text("https://example.com/audio.mp3") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            link = clip.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        }) { Icon(Icons.Rounded.ContentPaste, "لصق") }
                    }
                )
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم الملف (اختياري)") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("بدء التنزيل تلقائيًا")
                        Text("ابدأ عند اكتشاف رابط صالح", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = autoStart, onCheckedChange = { autoStart = it; prefs.edit().putBoolean("auto_start", it).apply() })
                }
                Button(onClick = { lastAuto = link.trim(); startDownload(link) }, modifier = Modifier.fillMaxWidth(), enabled = link.isNotBlank()) {
                    Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(8.dp)); Text("بدء التنزيل")
                }
                if (youtubeUri != null) {
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, youtubeUri).setPackage("com.google.android.youtube"))
                        }.onFailure {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, youtubeUri)) }
                                .onFailure { notice = "تعذر فتح رابط يوتيوب." }
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("فتح الفيديو في تطبيق YouTube")
                    }
                    Text("للتنزيل دون اتصال استخدم خيار التنزيل داخل YouTube إذا كان متاحًا للفيديو وحسابك. لا يحوّل هذا الخيار الفيديو إلى ملف صوتي مستقل.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("يدعم روابط ملفات الصوت المباشرة المسموح بتنزيلها، ولا يستخرج الصوت من يوتيوب أو يتجاوز قيود المنصات.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            listOf("التنزيلات" to 0, "قائمة الانتظار" to 1, "المكتملة" to 2).forEachIndexed { i, item ->
                SegmentedButton(selected = tab == item.second, onClick = { tab = item.second }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(item.first) }
            }
        }
        val shown = when (tab) {
            1 -> rows.filter { it.status == DownloadManager.STATUS_PENDING || it.status == DownloadManager.STATUS_RUNNING || it.status == DownloadManager.STATUS_PAUSED }
            2 -> rows.filter { it.status == DownloadManager.STATUS_SUCCESSFUL }
            else -> rows
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(if (tab == 1) "قائمة الانتظار فارغة" else if (tab == 2) "لا توجد تنزيلات مكتملة بعد" else "لا توجد تنزيلات بعد", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (item.status == DownloadManager.STATUS_SUCCESSFUL) Icons.Rounded.CheckCircle else Icons.Rounded.AudioFile, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(downloadStatus(item.status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { manager.remove(item.id); rows = refresh() }) { Icon(Icons.Rounded.Delete, "إزالة") }
                        }
                        if (item.status == DownloadManager.STATUS_PENDING || item.status == DownloadManager.STATUS_RUNNING || item.status == DownloadManager.STATUS_PAUSED) {
                            val progress = if (item.total > 0) item.done.toFloat() / item.total else 0f
                            LinearProgressIndicator(progress = progress.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                            Text((progress * 100).toInt().toString() + "% • " + item.done / 1024 + " / " + item.total / 1024 + " KB", style = MaterialTheme.typography.labelSmall)
                        }
                        if (item.status == DownloadManager.STATUS_SUCCESSFUL && item.localUri != null) {
                            Text("حُفظ في Music/Whisper Downloads", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    notice?.let { text ->
        AlertDialog(onDismissRequest = { notice = null }, title = { Text("التنزيل") }, text = { Text(text) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("حسنًا") } })
    }
}

private fun downloadStatus(status: Int): String = when (status) {
    DownloadManager.STATUS_PENDING -> "في الانتظار"
    DownloadManager.STATUS_RUNNING -> "جارٍ التنزيل"
    DownloadManager.STATUS_PAUSED -> "متوقف مؤقتًا"
    DownloadManager.STATUS_SUCCESSFUL -> "اكتمل التنزيل"
    DownloadManager.STATUS_FAILED -> "فشل التنزيل"
    else -> "حالة غير معروفة"
}