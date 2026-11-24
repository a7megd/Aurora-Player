package com.aurora.player.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import com.aurora.player.domain.model.Song
import java.io.File
import java.util.Locale

object RecordingClassifier {
    fun isRecording(song: Song): Boolean =
        isRecording(song.path, song.title, song.artist, song.album, song.mimeType)

    fun isRecording(path: String, title: String, artist: String, album: String, mime: String): Boolean {
        val text = "$path $title $artist $album".lowercase(Locale.ROOT)
        val markers = listOf("record", "recording", "voice", "callrec", "call_rec", "audio_recorder", "soundrecorder", "مسجل", "تسجيل", "مكالمات", "مكالمة")
        return markers.any { it in text } || (mime.equals("audio/3gpp", true) && ("call" in text || "record" in text))
    }
}

class MediaStoreScanner(private val resolver: ContentResolver) {
    fun scan(): List<Song> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.IS_MUSIC
        )
        val result = mutableListOf<Song>()
        resolver.query(collection, projection, "${MediaStore.Audio.Media.DURATION} > 0", null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val path = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val mime = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val size = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val year = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val track = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val date = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val isMusic = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_MUSIC)
            while (cursor.moveToNext()) {
                val filePath = cursor.getString(path).orEmpty()
                val name = cursor.getString(title)?.takeIf { it.isNotBlank() } ?: File(filePath).nameWithoutExtension
                val artistName = cursor.getString(artist).orEmpty()
                val albumName = cursor.getString(album).orEmpty()
                val mimeType = cursor.getString(mime).orEmpty()
                val recording = RecordingClassifier.isRecording(filePath, name, artistName, albumName, mimeType)
                if (!recording && cursor.getInt(isMusic) == 0) continue
                val mediaId = cursor.getLong(id)
                val albumKey = cursor.getLong(albumId)
                result += Song(
                    id = mediaId, title = name,
                    artist = artistName.takeUnless { it == "<unknown>" }.orEmpty().ifBlank { "Unknown artist" },
                    album = albumName.takeUnless { it == "<unknown>" }.orEmpty().ifBlank { "Unknown album" },
                    albumId = albumKey, durationMs = cursor.getLong(duration), path = filePath,
                    folderPath = File(filePath).parent.orEmpty(), mimeType = mimeType, sizeBytes = cursor.getLong(size),
                    year = if (cursor.isNull(year)) 0 else cursor.getInt(year), trackNumber = cursor.getInt(track),
                    dateAddedSec = cursor.getLong(date),
                    contentUri = ContentUris.withAppendedId(collection, mediaId).toString(),
                    artworkUri = if (albumKey > 0) "content://media/external/audio/albumart/$albumKey" else null
                )
            }
        }
        return result
    }
}