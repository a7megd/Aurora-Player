package com.aurora.player.community

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

/** One authenticated Firebase Storage uploader for all user-generated images and audio. */
object WhisperStorage {
    private val storage by lazy { FirebaseStorage.getInstance() }

    suspend fun uploadUri(context: Context, uri: Uri, folder: String, resourceType: String, contentType: String): String =
        withContext(Dispatchers.IO) {
            validate(folder, resourceType, contentType)
            val readable = context.contentResolver.openInputStream(uri)
                ?: error("تعذر فتح الملف المحدد من الهاتف.")
            readable.use { /* Verify that Android can open the selected content URI. */ }
            val name = UUID.randomUUID().toString().replace("-", "") + extensionFor(contentType)
            upload(storage.reference.child("$folder/$name"), uri, contentType)
        }

    suspend fun uploadFile(file: File, folder: String, resourceType: String, contentType: String): String =
        withContext(Dispatchers.IO) {
            validate(folder, resourceType, contentType)
            require(file.exists() && file.isFile && file.length() > 0L) { "الملف غير موجود أو فارغ." }
            val name = UUID.randomUUID().toString().replace("-", "") + extensionFor(contentType)
            upload(storage.reference.child("$folder/$name"), Uri.fromFile(file), contentType)
        }

    private fun validate(folder: String, resourceType: String, contentType: String) {
        require(folder.startsWith("Whisper-Player/") && !folder.contains("..") && !folder.endsWith("/")) {
            "مسار التخزين غير صالح."
        }
        require(resourceType == "image" || resourceType == "video") { "نوع الوسائط غير مدعوم." }
        require((resourceType == "image" && contentType.startsWith("image/")) ||
            (resourceType == "video" && contentType.startsWith("audio/"))) {
            "نوع الملف لا يطابق نوع الوسائط المطلوب."
        }
    }

    private suspend fun upload(reference: StorageReference, uri: Uri, contentType: String): String {
        val metadata = StorageMetadata.Builder().setContentType(contentType).build()
        reference.putFile(uri, metadata).await()
        val url = reference.downloadUrl.await().toString()
        require(url.startsWith("https://")) { "لم يُرجع Firebase Storage رابط HTTPS صالحًا." }
        return url
    }

    private fun extensionFor(mime: String): String = when (mime.lowercase()) {
        "image/jpeg", "image/jpg" -> ".jpg"
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
}
