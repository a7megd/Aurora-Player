package com.whisper.player.shared

import com.whisper.player.shared.lyrics.LrcParser
import com.whisper.player.shared.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SharedCoreTest {
    private fun track(
        id: String,
        title: String,
        duration: Long = 1000,
        plays: Long = 0,
        lastPlayed: Long? = null,
    ) = Track(
        id = TrackId(id),
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = duration,
        source = MediaSource.FILES_IMPORT,
        locator = "file:///$id.mp3",
        playCount = plays,
        lastPlayedEpochMillis = lastPlayed,
    )

    @Test
    fun sortsTracksWithoutPlatformLocaleDependencies() {
        val sorted = sortTracks(listOf(track("2", "zeta"), track("1", "Alpha")), SongSort.TITLE)
        assertEquals(listOf("Alpha", "zeta"), sorted.map { it.title })
    }

    @Test
    fun sortsByMostPlayedAndRecentlyPlayed() {
        val tracks = listOf(
            track("a", "Alpha", plays = 2, lastPlayed = 100),
            track("b", "Beta", plays = 12, lastPlayed = 300),
            track("c", "Gamma", plays = 5, lastPlayed = 200),
        )
        assertEquals(listOf("b", "c", "a"), sortTracks(tracks, SongSort.MOST_PLAYED, ascending = false).map { it.id.value })
        assertEquals(listOf("b", "c", "a"), sortTracks(tracks, SongSort.RECENTLY_PLAYED, ascending = false).map { it.id.value })
    }

    @Test
    fun queueMovesDuplicateTrackOccurrencesIndependently() {
        val first = QueueEntry("entry-1", TrackId("same-track"))
        val second = QueueEntry("entry-2", TrackId("same-track"))
        val queue = PlaybackQueue(listOf(first, second), currentEntryId = "entry-2").move(1, 0)
        assertEquals(listOf("entry-2", "entry-1"), queue.entries.map { it.entryId })
        assertEquals("entry-2", queue.currentEntryId)
    }

    @Test
    fun removingCurrentQueueEntrySelectsNextEntry() {
        val queue = PlaybackQueue(
            entries = listOf(
                QueueEntry("a", TrackId("a")),
                QueueEntry("b", TrackId("b")),
                QueueEntry("c", TrackId("c")),
            ),
            currentEntryId = "b",
        ).removeAt(1)
        assertEquals("c", queue.currentEntryId)
    }

    @Test
    fun rejectsInvalidQueueMoves() {
        val queue = PlaybackQueue(listOf(QueueEntry("a", TrackId("a"))))
        assertFailsWith<IllegalArgumentException> { queue.move(0, 1) }
    }

    @Test
    fun queueNavigationHonorsRepeatAndStopsAtBoundaries() {
        val queue = PlaybackQueue(
            entries = listOf(
                QueueEntry("a", TrackId("a")),
                QueueEntry("b", TrackId("b")),
                QueueEntry("c", TrackId("c")),
            ),
            currentEntryId = "b",
        )
        assertEquals("c", queue.selectNext(RepeatMode.OFF).currentEntryId)
        assertEquals("b", queue.selectPrevious(RepeatMode.OFF).currentEntryId)
        assertEquals("a", queue.copy(currentEntryId = "c").selectNext(RepeatMode.ALL).currentEntryId)
        assertEquals("c", queue.copy(currentEntryId = "a").selectPrevious(RepeatMode.ALL).currentEntryId)
        assertEquals("b", queue.selectNext(RepeatMode.ONE).currentEntryId)
        assertEquals("b", queue.selectPrevious(RepeatMode.ONE).currentEntryId)
    }

    @Test
    fun queueNavigationUsesShuffleOrderAndRecoversMissingEntries() {
        val queue = PlaybackQueue(
            entries = listOf(
                QueueEntry("a", TrackId("a")),
                QueueEntry("b", TrackId("b")),
                QueueEntry("c", TrackId("c")),
            ),
            currentEntryId = "c",
        )
        // The stale ID is ignored; the omitted entry is appended in its original order.
        assertEquals("a", queue.selectNext(RepeatMode.OFF, listOf("c", "missing", "a")).currentEntryId)
        assertEquals("b", queue.selectNext(RepeatMode.OFF, listOf("c", "missing", "a")).selectNext(RepeatMode.OFF, listOf("c", "missing", "a")).currentEntryId)
    }

    @Test
    fun playlistCanInsertRemoveReorderAndRenameTracks() {
        val playlist = Playlist(
            id = "mix",
            name = "  Road Trip  ",
            trackIds = listOf(TrackId("a"), TrackId("c")),
            createdAtEpochMillis = 10,
        )
        val edited = playlist
            .addTrack(TrackId("b"), atIndex = 1)
            .moveTrack(2, 0)
            .removeTrackAt(1)
            .renamed("  Night Drive  ")
        assertEquals("Night Drive", edited.name)
        assertEquals(listOf("c", "b"), edited.trackIds.map { it.value })
        assertEquals(listOf("a", "c"), playlist.trackIds.map { it.value })
    }

    @Test
    fun playlistRejectsBlankNamesAndInvalidIndexes() {
        val playlist = Playlist("mix", "Favorites", emptyList(), 0)
        assertFailsWith<IllegalArgumentException> { playlist.renamed("   ") }
        assertFailsWith<IllegalArgumentException> { playlist.addTrack(TrackId("a"), 1) }
        assertFailsWith<IllegalArgumentException> { playlist.removeTrackAt(0) }
    }

    @Test
    fun parsesMultipleLrcTimestampsAndMillisecondPrecision() {
        val lines = LrcParser.parse("[00:01.2][00:02.34]hello\n[01:03.456]world")
        assertEquals(listOf(1200L, 2340L, 63456L), lines.map { it.timeMs })
        assertEquals(listOf("hello", "hello", "world"), lines.map { it.text })
        assertEquals(0, LrcParser.currentIndex(lines, 1500))
        assertEquals(2, LrcParser.currentIndex(lines, 70000))
    }
}
