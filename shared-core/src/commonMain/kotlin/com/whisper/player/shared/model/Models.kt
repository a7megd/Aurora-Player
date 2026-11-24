package com.whisper.player.shared.model

/**
 * Portable domain models. Platform file IDs and Android MediaStore IDs must never leak
 * into shared state: each adapter supplies a stable, serializable locator and TrackId.
 */
@JvmInline
value class TrackId(val value: String) {
    init { require(value.isNotBlank()) { "TrackId must not be blank" } }
}

enum class RepeatMode { OFF, ALL, ONE }
enum class ThemeMode { SYSTEM, DARK, LIGHT }
enum class SongSort { TITLE, ARTIST, ALBUM, DATE_ADDED, RECENTLY_PLAYED, MOST_PLAYED, DURATION }
enum class MediaSource { DEVICE_LIBRARY, FILES_IMPORT, APP_RECORDING, REMOTE }

data class Track(
    val id: TrackId,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String? = null,
    val durationMs: Long,
    val source: MediaSource,
    /** Opaque platform URI or app-managed locator; shared code must not parse it. */
    val locator: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val dateAddedEpochSeconds: Long? = null,
    val playCount: Long = 0,
    val lastPlayedEpochMillis: Long? = null,
    val artworkLocator: String? = null,
) {
    init {
        require(durationMs >= 0) { "durationMs cannot be negative" }
        require(locator.isNotBlank()) { "Track locator must not be blank" }
        require(playCount >= 0) { "playCount cannot be negative" }
    }
}

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val songCount: Int,
    val artworkLocator: String? = null,
)

data class Artist(val name: String, val songCount: Int, val albumCount: Int)
data class Folder(val locator: String, val name: String, val songCount: Int)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val amoled: Boolean = true,
    /** ARGB color represented as an unsigned-compatible 32-bit value in a Long. */
    val accentColorArgb: Long = 0xFF7C5CFFL,
    val songSort: SongSort = SongSort.TITLE,
    val songSortAscending: Boolean = true,
)

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<TrackId>,
    val createdAtEpochMillis: Long,
) {
    init {
        require(id.isNotBlank()) { "Playlist ID must not be blank" }
        require(name.isNotBlank()) { "Playlist name must not be blank" }
        require(createdAtEpochMillis >= 0) { "Playlist creation time cannot be negative" }
    }

    fun addTrack(trackId: TrackId, atIndex: Int = trackIds.size): Playlist {
        require(atIndex in 0..trackIds.size) { "Playlist insertion index is outside the list" }
        return copy(trackIds = trackIds.toMutableList().apply { add(atIndex, trackId) })
    }

    fun removeTrackAt(index: Int): Playlist {
        require(index in trackIds.indices) { "Playlist index is outside the list" }
        return copy(trackIds = trackIds.toMutableList().apply { removeAt(index) })
    }

    fun moveTrack(fromIndex: Int, toIndex: Int): Playlist {
        require(fromIndex in trackIds.indices) { "Playlist source index is outside the list" }
        require(toIndex in trackIds.indices) { "Playlist destination index is outside the list" }
        if (fromIndex == toIndex) return this
        val updated = trackIds.toMutableList()
        val moved = updated.removeAt(fromIndex)
        updated.add(toIndex, moved)
        return copy(trackIds = updated)
    }

    fun renamed(newName: String): Playlist {
        require(newName.isNotBlank()) { "Playlist name must not be blank" }
        return copy(name = newName.trim())
    }
}

data class QueueEntry(
    /** Unique per queue occurrence, even when the same track is queued more than once. */
    val entryId: String,
    val trackId: TrackId,
)

data class PlaybackQueue(
    val entries: List<QueueEntry> = emptyList(),
    val currentEntryId: String? = null,
) {
    fun move(fromIndex: Int, toIndex: Int): PlaybackQueue {
        require(fromIndex in entries.indices) { "fromIndex is outside the queue" }
        require(toIndex in entries.indices) { "toIndex is outside the queue" }
        if (fromIndex == toIndex) return this
        val updated = entries.toMutableList()
        val moved = updated.removeAt(fromIndex)
        updated.add(toIndex, moved)
        return copy(entries = updated)
    }


    /**
     * Selects the next queue occurrence. [shuffleOrder] is an optional ordered list
     * of entry IDs produced by the platform shuffle adapter; omitted/stale IDs are
     * ignored and entries missing from that list retain their original order.
     * Repeat-one keeps the current occurrence, repeat-all wraps, and repeat-off stops
     * at the end without silently restarting playback.
     */
    fun selectNext(
        repeatMode: RepeatMode,
        shuffleOrder: List<String>? = null,
    ): PlaybackQueue {
        if (entries.isEmpty()) return this
        if (repeatMode == RepeatMode.ONE && currentEntryId != null) return this
        val ordered = orderedEntries(shuffleOrder)
        val currentIndex = ordered.indexOfFirst { it.entryId == currentEntryId }
        val nextIndex = when {
            currentIndex < 0 -> 0
            currentIndex < ordered.lastIndex -> currentIndex + 1
            repeatMode == RepeatMode.ALL -> 0
            else -> return this
        }
        return copy(currentEntryId = ordered[nextIndex].entryId)
    }

    /** Selects the previous occurrence, wrapping only when repeat-all is enabled. */
    fun selectPrevious(
        repeatMode: RepeatMode,
        shuffleOrder: List<String>? = null,
    ): PlaybackQueue {
        if (entries.isEmpty()) return this
        if (repeatMode == RepeatMode.ONE && currentEntryId != null) return this
        val ordered = orderedEntries(shuffleOrder)
        val currentIndex = ordered.indexOfFirst { it.entryId == currentEntryId }
        val previousIndex = when {
            currentIndex < 0 -> 0
            currentIndex > 0 -> currentIndex - 1
            repeatMode == RepeatMode.ALL -> ordered.lastIndex
            else -> 0
        }
        return copy(currentEntryId = ordered[previousIndex].entryId)
    }

    private fun orderedEntries(shuffleOrder: List<String>?): List<QueueEntry> {
        if (shuffleOrder == null) return entries
        val byId = entries.associateBy { it.entryId }
        val seen = mutableSetOf<String>()
        val ordered = shuffleOrder.mapNotNull { id ->
            byId[id]?.takeIf { seen.add(id) }
        }
        return ordered + entries.filter { seen.add(it.entryId) }
    }

    fun removeAt(index: Int): PlaybackQueue {
        require(index in entries.indices) { "index is outside the queue" }
        val removed = entries[index]
        val updated = entries.toMutableList().also { it.removeAt(index) }
        val nextCurrent = if (currentEntryId == removed.entryId) {
            updated.getOrNull(index)?.entryId ?: updated.lastOrNull()?.entryId
        } else currentEntryId
        return copy(entries = updated, currentEntryId = nextCurrent)
    }
}

/** Stable shared sorting; adapters provide play-count and last-played metadata. */
fun sortTracks(tracks: List<Track>, sort: SongSort, ascending: Boolean = true): List<Track> {
    val comparator = when (sort) {
        SongSort.TITLE -> compareBy<Track> { it.title.lowercase() }
        SongSort.ARTIST -> compareBy<Track> { it.artist.lowercase() }.thenBy { it.title.lowercase() }
        SongSort.ALBUM -> compareBy<Track> { it.album.lowercase() }.thenBy { it.trackNumber ?: Int.MAX_VALUE }
        SongSort.DATE_ADDED -> compareBy<Track> { it.dateAddedEpochSeconds ?: Long.MIN_VALUE }
        SongSort.RECENTLY_PLAYED -> compareBy<Track> { it.lastPlayedEpochMillis ?: Long.MIN_VALUE }
        SongSort.MOST_PLAYED -> compareBy<Track> { it.playCount }
        SongSort.DURATION -> compareBy<Track> { it.durationMs }
    }
    return if (ascending) tracks.sortedWith(comparator) else tracks.sortedWith(comparator.reversed())
}
