package com.aurora.player.community

import android.content.Context
import android.net.Uri
import com.aurora.player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Unsigned Cloudinary uploader. The upload preset must be configured in Cloudinary
 * with strict allowed formats and size limits. No API secret is embedded in the APK.
 */
object WhisperCloudinary {
    private const val CLOUD_NAME = BuildConfig.CLOUDINARY_CLOUD_NAME
    private const val UPLOAD_PRESET = BuildConfig.CLOUDINARY_UPLOAD_PRESET

    suspend fun uploadUri(
        context: Context,
        uri: Uri,
        folder: String,
        resourceType: String,
        contentType: String
    ): String = withContext(Dispatchers.IO) {
        requireConfigured()
        val name = "whisper_${UUID.randomUUID()}"
        val input = context.contentResolver.openInputStream(uri)
            ?: error("تعذر قراءة الملف المحدد.")
        input.use { uploadStream(it, name, folder, resourceType, contentType) }
    }

    suspend fun uploadFile(
        file: File,
        folder: String,
        resourceType: String,
        contentType: String
    ): String = withContext(Dispatchers.IO) {
        requireConfigured()
        require(file.exists() && file.length() > 0L) { "الملف الصوتي غير موجود أو فارغ." }
        FileInputStream(file).use {
            uploadStream(it, "whisper_${UUID.randomUUID()}", folder, resourceType, contentType)
        }
    }

    private fun requireConfigured() {
        require(CLOUD_NAME.isNotBlank() && CLOUD_NAME != "CHANGE_ME" &&
            UPLOAD_PRESET.isNotBlank() && UPLOAD_PRESET != "CHANGE_ME") {
            "إعداد Cloudinary غير مكتمل. يلزم ضبط اسم Cloud واسم Upload Preset في إعدادات البناء."
        }
    }

    private fun uploadStream(
        input: InputStream,
        publicId: String,
        folder: String,
        resourceType: String,
        contentType: String
    ): String {
        require(resourceType == "image" || resourceType == "video") { "نوع الملف غير مدعوم." }
        val boundary = "----WhisperUpload${UUID.randomUUID().toString().replace("-", "")}"
        val endpoint = URL("https://api.cloudinary.com/v1_1/$CLOUD_NAME/$resourceType/upload")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20000
            readTimeout = 60000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
            setChunkedStreamingMode(64 * 1024)
        }
        try {
            connection.outputStream.use { output ->
                fun field(name: String, value: String) {
                    output.write("--$boundary\r\n".toByteArray())
                    output.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                    output.write(value.toByteArray(Charsets.UTF_8))
                    output.write("\r\n".toByteArray())
                }
                field("upload_preset", UPLOAD_PRESET)
                field("folder", folder)
                field("public_id", publicId)
                output.write("--$boundary\r\n".toByteArray())
                output.write("Content-Disposition: form-data; name=\"file\"; filename=\"$publicId\"\r\n".toByteArray())
                output.write("Content-Type: $contentType\r\n\r\n".toByteArray())
                input.copyTo(output)
                output.write("\r\n--$boundary--\r\n".toByteArray())
                output.flush()
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(response).optJSONObject("error")?.optString("message") }.getOrNull()
                error("فشل رفع الملف إلى Cloudinary ($code): ${detail ?: response.take(300)}")
            }
            val secureUrl = JSONObject(response).optString("secure_url")
            require(secureUrl.startsWith("https://")) { "لم يُرجع Cloudinary رابطًا آمنًا للملف." }
            return secureUrl
        } finally {
            connection.disconnect()
        }
    }
}
