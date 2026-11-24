package com.whisper.player.shared

import com.whisper.player.shared.lyrics.LrcParser
import com.whisper.player.shared.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SharedCoreTest {
    private fun track(id: String, title: String, duration: Long = 1000) = Track(
        id = TrackId(id),
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = duration,
        source = MediaSource.FILES_IMPORT,
        locator = "file:///$id.mp3",
    )

    @Test
    fun sortsTracksWithoutPlatformLocaleDependencies() {
        val sorted = sortTracks(listOf(track("2", "zeta"), track("1", "Alpha")), SongSort.TITLE)
        assertEquals(listOf("Alpha", "zeta"), sorted.map { it.title })
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
    fun parsesMultipleLrcTimestampsAndMillisecondPrecision() {
        val lines = LrcParser.parse("[00:01.2][00:02.34]hello\n[01:03.456]world")
        assertEquals(listOf(1200L, 2340L, 63456L), lines.map { it.timeMs })
        assertEquals(listOf("hello", "hello", "world"), lines.map { it.text })
        assertEquals(1, LrcParser.currentIndex(lines, 1500))
        assertEquals(2, LrcParser.currentIndex(lines, 70000))
    }
}
