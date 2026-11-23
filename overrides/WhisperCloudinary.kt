package com.aurora.player.community

import android.content.Context
import android.net.Uri
import com.aurora.player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Unsigned Cloudinary uploader using fixed-length multipart requests. */
object WhisperCloudinary {
    private const val CLOUD_NAME = BuildConfig.CLOUDINARY_CLOUD_NAME.trim()
    private const val UPLOAD_PRESET = BuildConfig.CLOUDINARY_UPLOAD_PRESET.trim()

    suspend fun uploadUri(context: Context, uri: Uri, folder: String, resourceType: String, contentType: String): String =
        withContext(Dispatchers.IO) {
            requireConfigured()
            val temp = File.createTempFile("whisper-upload-", extensionFor(contentType), context.cacheDir)
            try {
                val input = context.contentResolver.openInputStream(uri) ?: error("تعذر قراءة الملف المحدد من الهاتف.")
                input.use { source -> temp.outputStream().use { source.copyTo(it) } }
                require(temp.length() > 0L) { "الملف المحدد فارغ." }
                uploadFileInternal(temp, folder, resourceType, contentType)
            } finally { temp.delete() }
        }

    suspend fun uploadFile(file: File, folder: String, resourceType: String, contentType: String): String =
        withContext(Dispatchers.IO) {
            requireConfigured()
            require(file.exists() && file.isFile && file.length() > 0L) { "الملف الصوتي غير موجود أو فارغ." }
            uploadFileInternal(file, folder, resourceType, contentType)
        }

    private fun requireConfigured() {
        require(CLOUD_NAME.matches(Regex("[A-Za-z0-9_-]{1,64}")) &&
            UPLOAD_PRESET.isNotBlank() && UPLOAD_PRESET != "CHANGE_ME") {
            "رفع الوسائط غير مُعدّ: راجع متغيرَي CLOUDINARY_CLOUD_NAME وCLOUDINARY_UPLOAD_PRESET في إعدادات GitHub، وتأكد أن Upload Preset مضبوط على Unsigned."
        }
    }

    private fun extensionFor(mime: String): String = when (mime.lowercase()) {
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "image/gif" -> ".gif"
        "audio/mpeg" -> ".mp3"
        "audio/wav", "audio/x-wav" -> ".wav"
        "audio/ogg" -> ".ogg"
        "audio/aac" -> ".aac"
        "audio/mp4", "audio/x-m4a" -> ".m4a"
        else -> ".bin"
    }

    private fun uploadFileInternal(file: File, folder: String, resourceType: String, contentType: String): String {
        require(resourceType == "image" || resourceType == "video") {
            "نوع الملف غير مدعوم. ملفات الصوت تُرفع عبر video resource type."
        }
        require(folder.isNotBlank() && !folder.contains("..")) { "مسار التخزين غير صالح." }
        val boundary = "----WhisperUpload" + UUID.randomUUID().toString().replace("-", "")
        val publicId = "whisper_" + UUID.randomUUID().toString().replace("-", "")
        val endpoint = URL("https://api.cloudinary.com/v1_1/$CLOUD_NAME/$resourceType/upload")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; doInput = true; useCaches = false
            connectTimeout = 30000; readTimeout = 120000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }
        val crlf = "\r\n"
        fun field(name: String, value: String): ByteArray =
            ("--$boundary$crlf" + "Content-Disposition: form-data; name=\"$name\"$crlf$crlf" + value + crlf).toByteArray(Charsets.UTF_8)
        val prefix = field("upload_preset", UPLOAD_PRESET) + field("folder", folder) + field("public_id", publicId) +
            ("--$boundary$crlf" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"$publicId${extensionFor(contentType)}\"$crlf" +
                "Content-Type: $contentType$crlf$crlf").toByteArray(Charsets.UTF_8)
        val suffix = "$crlf--$boundary--$crlf".toByteArray(Charsets.UTF_8)
        val totalLength = prefix.size.toLong() + file.length() + suffix.size
        try {
            connection.setFixedLengthStreamingMode(totalLength)
            connection.outputStream.use { output ->
                output.write(prefix)
                FileInputStream(file).use { input -> input.copyTo(output) }
                output.write(suffix); output.flush()
            }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(response).optJSONObject("error")?.optString("message") }
                    .getOrNull()?.takeIf { it.isNotBlank() }
                error("Cloudinary رفض الرفع ($code): ${detail ?: response.take(400)}")
            }
            val secureUrl = JSONObject(response).optString("secure_url")
            require(secureUrl.startsWith("https://")) { "اكتمل رد Cloudinary دون رابط HTTPS صالح." }
            secureUrl
        } finally { connection.disconnect() }
    }
}
