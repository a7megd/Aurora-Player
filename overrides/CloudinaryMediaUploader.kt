package com.aurora.player.media

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.aurora.player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class CloudinaryMediaUpload(
    val secureUrl: String,
    val assetId: String,
    val publicId: String,
    val resourceType: String,
    val format: String,
    val bytes: Long,
    val mimeType: String,
    val originalName: String
)

/**
 * Unsigned, client-side Cloudinary uploader.
 *
 * The upload preset is intentionally a public unsigned preset name, not an API secret.
 * It must be configured with restrictive allowed formats, size limits and a fixed folder.
 */
object CloudinaryMediaUploader {
    private const val CLOUD_NAME = "vlmbhhfq"
    private const val IMAGE_AND_RAW_LIMIT = 10L * 1024L * 1024L
    private const val VIDEO_AND_AUDIO_LIMIT = 100L * 1024L * 1024L
    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 120_000

    suspend fun upload(
        context: Context,
        uri: Uri,
        folder: String,
        onProgress: ((sentBytes: Long, totalBytes: Long) -> Unit)? = null
    ): CloudinaryMediaUpload = withContext(Dispatchers.IO) {
        val preset = BuildConfig.CLOUDINARY_UPLOAD_PRESET.trim()
        require(preset.isNotBlank()) {
            "إعداد الرفع السحابي غير مكتمل. يلزم ضبط Unsigned Upload Preset قبل رفع الملفات."
        }
        require(BuildConfig.CLOUDINARY_CLOUD_NAME == CLOUD_NAME) {
            "إعداد Cloudinary غير متطابق."
        }
        require(folder.matches(Regex("Whisper-Player/[A-Za-z0-9_-]{1,40}"))) {
            "مسار تخزين الوسائط غير صالح."
        }

        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val displayName = queryDisplayName(context, uri)
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: displayName.substringAfterLast('.', "").takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) }
            ?: "bin"
        val maxBytes = if (mime.startsWith("video/") || mime.startsWith("audio/")) VIDEO_AND_AUDIO_LIMIT else IMAGE_AND_RAW_LIMIT
        val temp = File(context.cacheDir, "whisper-upload-${UUID.randomUUID()}.$extension")

        try {
            var total = 0L
            resolver.openInputStream(uri)?.use { input ->
                BufferedInputStream(input).use { buffered ->
                    temp.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = buffered.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= maxBytes) {
                                "حجم الملف أكبر من حد الخطة المجانية لهذا النوع ( ${maxBytes / (1024 * 1024)} MB )."
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
            } ?: error("تعذر قراءة الملف المحدد من الجهاز.")
            require(total > 0L) { "الملف فارغ أو تعذر قراءته." }

            val boundary = "----WhisperUpload${UUID.randomUUID().toString().replace("-", "")}"
            val prefix = ByteArrayOutputStream().apply {
                val out = DataOutputStream(this)
                fun field(name: String, value: String) {
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                    out.write(value.toByteArray(Charsets.UTF_8))
                    out.writeBytes("\r\n")
                }
                field("upload_preset", preset)
                field("folder", folder)
                field("public_id", "whisper_${UUID.randomUUID().toString().replace("-", "")}")
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"upload.$extension\"\r\n")
                out.writeBytes("Content-Type: $mime\r\n\r\n")
                out.flush()
            }.toByteArray()
            val suffix = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

            val endpoint = URL("https://api.cloudinary.com/v1_1/$CLOUD_NAME/auto/upload")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Accept", "application/json")
                setFixedLengthStreamingMode(prefix.size.toLong() + total + suffix.size)
            }
            try {
                connection.outputStream.use { raw ->
                    DataOutputStream(raw).use { out ->
                        out.write(prefix)
                        temp.inputStream().buffered().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var sent = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                sent += read
                                onProgress?.invoke(sent, total)
                            }
                        }
                        out.write(suffix)
                        out.flush()
                    }
                }
                val status = connection.responseCode
                val responseText = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                require(status in 200..299) {
                    val cloudError = runCatching { JSONObject(responseText).optJSONObject("error")?.optString("message") }.getOrNull()
                    cloudError?.takeIf { it.isNotBlank() } ?: "فشل الرفع إلى Cloudinary (HTTP $status)."
                }
                val json = JSONObject(responseText)
                val secureUrl = json.optString("secure_url")
                val assetId = json.optString("asset_id")
                val publicId = json.optString("public_id")
                val resourceType = json.optString("resource_type")
                val returnedBytes = json.optLong("bytes", -1L)
                require(secureUrl.startsWith("https://res.cloudinary.com/$CLOUD_NAME/")) {
                    "استجابة Cloudinary لا تحتوي رابط HTTPS موثوقًا."
                }
                require(assetId.isNotBlank() && publicId.isNotBlank() && resourceType.isNotBlank()) {
                    "استجابة الرفع ناقصة؛ لن نحفظ رابطًا غير مكتمل."
                }
                require(returnedBytes == total) {
                    "حجم الملف المستلم لا يطابق الملف الأصلي؛ أُوقف الحفظ للحماية."
                }

                // Verify that the returned delivery URL is reachable before returning it to Firestore.
                val verify = (URL(secureUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                }
                try {
                    val verifyStatus = verify.responseCode
                    require(verifyStatus in 200..299) {
                        "تم استلام الملف لكن تعذر التحقق من رابط عرضه (HTTP $verifyStatus)."
                    }
                    val deliveredLength = verify.getHeaderFieldLong("Content-Length", -1L)
                    require(deliveredLength <= 0L || deliveredLength == returnedBytes) {
                        "حجم الملف عند التحقق لا يطابق الحجم المرفوع."
                    }
                } finally {
                    verify.disconnect()
                }

                CloudinaryMediaUpload(
                    secureUrl = secureUrl,
                    assetId = assetId,
                    publicId = publicId,
                    resourceType = resourceType,
                    format = json.optString("format"),
                    bytes = returnedBytes,
                    mimeType = mime,
                    originalName = displayName
                )
            } finally {
                connection.disconnect()
            }
        } finally {
            temp.delete()
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "whisper-file"
    }
}
