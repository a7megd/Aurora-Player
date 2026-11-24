package com.aurora.player.ui.navigation

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.database.Cursor
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.qiujuer.lame.Lame
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

@Composable
fun WhisperMediaConverterScreen(initialUri: Uri? = null, initialText: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<Uri?>(initialUri) }
    var fileName by remember { mutableStateOf("") }
    var bitrate by remember { mutableIntStateOf(192) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var resultName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialUri) {
        if (initialUri != null) {
            selected = initialUri
            fileName = displayName(context, initialUri).substringBeforeLast('.')
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selected = uri
            fileName = displayName(context, uri).substringBeforeLast('.')
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }

    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AudioFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("تحويل الفيديو إلى MP3", style = MaterialTheme.typography.headlineSmall)
                Text("المعالجة تتم على الهاتف ولا يتم رفع الفيديو", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!initialText.isNullOrBlank()) {
            val isYouTube = isYouTubeUrl(initialText)
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (isYouTube) "رابط YouTube مستلم" else "رابط مستلم", style = MaterialTheme.typography.titleSmall)
                    Text(initialText.take(500), style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (isYouTube) "يمكن فتح الرابط في YouTube أو استيراد ملف فيديو متاح لديك. لا يمكن تحويل رابط المشاهدة نفسه إلى MP3 من داخل التطبيق."
                        else "لو الرابط يشير إلى ملف فيديو مباشر ومسموح بتنزيله، احفظه على الهاتف ثم اختره هنا.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        OutlinedButton(onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth(), enabled = !working) {
            Icon(Icons.Rounded.VideoFile, null)
            Spacer(Modifier.width(8.dp))
            Text("اختيار فيديو من الهاتف")
        }
        selected?.let { uri ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("الفيديو المحدد", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(displayName(context, uri), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it.take(90) },
                        label = { Text("اسم ملف MP3") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Text("جودة الصوت", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(128, 192, 320).forEach { rate ->
                FilterChip(selected = bitrate == rate, onClick = { bitrate = rate }, label = { Text("$rate kbps") }, enabled = !working)
            }
        }
        Button(
            onClick = {
                val input = selected ?: return@Button
                val safe = fileName.trim().ifBlank { "Whisper Audio" }
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_").take(90)
                working = true
                message = "جارٍ استخراج الصوت وتحويله..."
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { convertToMp3(context, input, safe, bitrate) }
                    }
                    working = false
                    result.onSuccess {
                        resultName = it
                        message = "اكتمل التحويل وحُفظ الملف في Music/Whisper Downloads"
                    }.onFailure {
                        message = "تعذر التحويل: " + (it.localizedMessage ?: "صيغة الفيديو غير مدعومة")
                    }
                }
            },
            enabled = selected != null && !working && fileName.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.AudioFile, null)
            Spacer(Modifier.width(8.dp))
            Text(if (working) "جارٍ التحويل..." else "تحويل وحفظ MP3")
        }
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
        resultName?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(
            "يدعم التطبيق صيغ الفيديو التي يستطيع مفكك الترميز في أندرويد قراءتها. يجب أن يكون لديك حق استخدام الملف وتحويله.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun isYouTubeUrl(value: String): Boolean {
    val host = runCatching { Uri.parse(value.trim()).host?.lowercase(Locale.ROOT).orEmpty() }.getOrDefault("")
    return host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be" || host.endsWith(".youtu.be")
}

private fun displayName(context: Context, uri: Uri): String {
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Video"
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
        }
    }
    return name
}

private fun convertToMp3(context: Context, source: Uri, baseName: String, bitrate: Int): String {
    val resolver = context.contentResolver
    val extractor = MediaExtractor()
    var decoder: MediaCodec? = null
    var lame: Lame? = null
    var outputUri: Uri? = null
    var output: OutputStream? = null
    var outputName = "$baseName.mp3"
    try {
        extractor.setDataSource(context, source, null)
        var audioTrack = -1
        var inputFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) {
                audioTrack = i
                inputFormat = format
                break
            }
        }
        val format = inputFormat ?: error("الفيديو لا يحتوي على مسار صوتي")
        extractor.selectTrack(audioTrack)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: error("ترميز صوت غير معروف")
        decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
        if (sampleRate !in 8000..48000) error("معدل العينة غير مدعوم: $sampleRate Hz")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, outputName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Whisper Downloads")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            outputUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: error("تعذر إنشاء ملف MP3")
            output = resolver.openOutputStream(outputUri!!, "w") ?: error("تعذر فتح ملف MP3")
        } else {
            val dir = java.io.File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Whisper Downloads")
            dir.mkdirs()
            val file = java.io.File(dir, outputName)
            output = file.outputStream()
            outputName = file.absolutePath
        }
        lame = Lame(sampleRate, channels, sampleRate, bitrate, Lame.LameModel.JOINT_STEREO, Lame.LameQuality.GOOD)
        val mp3Buffer = ByteArray(lame.getMp3bufferSize())
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = decoder.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex) ?: error("تعذر قراءة مدخل الصوت")
                    inputBuffer.clear()
                    val size = extractor.readSampleData(inputBuffer, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val decoded = decoder.outputFormat
                    sampleRate = decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
                    if (sampleRate !in 8000..48000) error("معدل العينة الناتج غير مدعوم")
                }
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                else -> if (outputIndex >= 0) {
                    if (info.size > 0) {
                        val buffer = decoder.getOutputBuffer(outputIndex) ?: error("تعذر قراءة الصوت المفكوك")
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        val pcm = ShortArray(bytes.size / 2)
                        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
                        val samples = if (channels == 1) pcm.size else pcm.size / channels
                        val written = if (channels == 1) lame.encode(pcm, pcm, samples, mp3Buffer)
                            else lame.encodeInterleaved(pcm, samples, mp3Buffer)
                        if (written > 0) output!!.write(mp3Buffer, 0, written)
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    decoder.releaseOutputBuffer(outputIndex, false)
                }
            }
        }
        var flushed: Int
        do {
            flushed = lame.flush(mp3Buffer)
            if (flushed > 0) output!!.write(mp3Buffer, 0, flushed)
        } while (flushed > 0)
        output!!.flush()
        output!!.close()
        output = null
        lame.close()
        lame = null
        if (Build.VERSION.SDK_INT >= 29 && outputUri != null) {
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(outputUri!!, done, null, null)
        }
        return outputName
    } catch (error: Throwable) {
        runCatching { output?.close() }
        if (outputUri != null) runCatching { resolver.delete(outputUri!!, null, null) }
        throw error
    } finally {
        runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        runCatching { extractor.release() }
        runCatching { lame?.close() }
    }
}
