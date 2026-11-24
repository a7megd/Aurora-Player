package com.aurora.player.data.repository

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.aurora.player.data.local.AppDatabase
import com.aurora.player.data.local.ExcludedFolderEntity
import com.aurora.player.data.media.MediaStoreScanner
import com.aurora.player.data.media.RecordingClassifier
import com.aurora.player.domain.model.Album
import com.aurora.player.domain.model.Artist
import com.aurora.player.domain.model.Folder
import com.aurora.player.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryRepository(private val resolver: ContentResolver, private val database: AppDatabase) {
    private val scanner = MediaStoreScanner(resolver)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
    private val _recordings = MutableStateFlow<List<Song>>(emptyList())
    val recordings: StateFlow<List<Song>> = _recordings.asStateFlow()
    private val _excludedFolders = MutableStateFlow<List<String>>(emptyList())
    val excludedFolders: StateFlow<List<String>> = _excludedFolders.asStateFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refresh() }
    }

    init {
        scope.launch {
            database.excludedFolderDao().observePaths().collect {
                _excludedFolders.value = it
                refresh()
            }
        }
        resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
    }

    fun refresh() {
        scope.launch {
            val excluded = _excludedFolders.value
            val scanned = withContext(Dispatchers.IO) { scanner.scan() }.filterNot { song ->
                excluded.any { path -> song.folderPath == path || song.folderPath.startsWith(path.trimEnd('/') + "/") }
            }
            _songs.value = scanned.filterNot { RecordingClassifier.isRecording(it) }
            _recordings.value = scanned.filter { RecordingClassifier.isRecording(it) }
        }
    }

    suspend fun excludeFolder(path: String) {
        database.excludedFolderDao().add(ExcludedFolderEntity(path))
        refresh()
    }

    suspend fun includeFolder(path: String) {
        database.excludedFolderDao().remove(path)
        refresh()
    }

    fun albums(songs: List<Song>): List<Album> = songs.groupBy { it.albumId }
        .map { (id, tracks) -> Album(id, tracks.first().album, tracks.first().artist, tracks.size, tracks.first().artworkUri) }
        .sortedBy { it.title.lowercase() }

    fun artists(songs: List<Song>): List<Artist> = songs.groupBy { it.artist }
        .map { (name, tracks) -> Artist(name, tracks.size, tracks.map { it.albumId }.distinct().size) }
        .sortedBy { it.name.lowercase() }

    fun folders(songs: List<Song>): List<Folder> = songs.groupBy { it.folderPath }
        .map { (path, tracks) -> Folder(path, path.substringAfterLast('/').ifBlank { "Music" }, tracks.size) }
        .sortedBy { it.name.lowercase() }
}