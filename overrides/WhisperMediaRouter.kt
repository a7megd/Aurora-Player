package com.aurora.player.community

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Hybrid media policy:
 * - Small images: Firebase Storage first, Cloudinary fallback.
 * - Large images and all audio: Cloudinary.
 *
 * Firebase's free allowance is not a hard spending cap; the Firebase console
 * billing budget/alerts must still be configured by the project owner.
 */
object WhisperMediaRouter {
    private const val FIREBASE_IMAGE_LIMIT_BYTES = 2L * 1024L * 1024L

    suspend fun uploadImageUri(context: Context, uri: Uri, folder: String, contentType: String): String {
        require(contentType.startsWith("image/")) { "الملف المحدد ليس صورة." }
        val size = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        }.getOrDefault(-1L)
        if (size in 1..FIREBASE_IMAGE_LIMIT_BYTES) {
            try {
                return WhisperStorage.uploadUri(context, uri, folder, "image", contentType)
            } catch (_: Exception) {
                // Firebase unavailable (e.g. Spark plan / quota / bucket rules): use Cloudinary.
            }
        }
        return WhisperCloudinary.uploadUri(context, uri, folder, "image", contentType)
    }

    suspend fun uploadImageBytes(bytes: ByteArray, folder: String, contentType: String): String {
        require(contentType.startsWith("image/")) { "البيانات ليست صورة." }
        if (bytes.size.toLong() in 1..FIREBASE_IMAGE_LIMIT_BYTES) {
            try {
                return WhisperStorage.uploadBytes(bytes, folder, "image", contentType)
            } catch (_: Exception) {
                // Keep upload available through the secondary provider.
            }
        }
        return WhisperCloudinary.uploadBytes(bytes, folder, "image", contentType)
    }

    suspend fun uploadAudioUri(context: Context, uri: Uri, folder: String, contentType: String): String {
        require(contentType.startsWith("audio/")) { "الملف المحدد ليس ملفًا صوتيًا." }
        return WhisperCloudinary.uploadUri(context, uri, folder, "video", contentType)
    }

    suspend fun uploadAudioFile(file: File, folder: String, contentType: String): String {
        require(contentType.startsWith("audio/")) { "الملف المحدد ليس ملفًا صوتيًا." }
        return WhisperCloudinary.uploadFile(file, folder, "video", contentType)
    }
}
