package com.aurora.player.community

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Cloud media uploads are intentionally disabled in the no-billing build.
 * Keep these signatures so older UI paths fail safely instead of silently
 * falling back to a third-party storage provider.
 */
object WhisperMediaRouter {
    private fun unavailable(): Nothing = error(
        "رفع الصور والصوتيات سحابيًا غير متاح في النسخة التي لا تتطلب وسيلة دفع. يمكنك استخدام الملفات محليًا."
    )

    suspend fun uploadImageUri(context: Context, uri: Uri, folder: String, contentType: String): String = unavailable()
    suspend fun uploadImageBytes(bytes: ByteArray, folder: String, contentType: String): String = unavailable()
    suspend fun uploadAudioUri(context: Context, uri: Uri, folder: String, contentType: String): String = unavailable()
    suspend fun uploadAudioFile(file: File, folder: String, contentType: String): String = unavailable()
}
