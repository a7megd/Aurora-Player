package com.whisper.player.shared.lyrics

data class TimedLyric(
    val timeMs: Long,
    val text: String,
)

object LrcParser {
    private val timestamp = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    /** Parses standard LRC timestamps, including multiple timestamps on one lyric line. */
    fun parse(raw: String): List<TimedLyric> {
        val result = mutableListOf<TimedLyric>()
        raw.lineSequence().forEach { line ->
            val matches = timestamp.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val text = line.substring(matches.last().range.last + 1).trim()
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: return@forEach
                val seconds = match.groupValues[2].toLongOrNull() ?: return@forEach
                val fractionText = match.groupValues[3]
                val fractionMs = when (fractionText.length) {
                    0 -> 0L
                    1 -> fractionText.toLong() * 100L
                    2 -> fractionText.toLong() * 10L
                    else -> fractionText.take(3).toLong()
                }
                if (seconds < 60) {
                    result += TimedLyric(
                        timeMs = minutes * 60_000L + seconds * 1_000L + fractionMs,
                        text = text,
                    )
                }
            }
        }
        return result.sortedBy { it.timeMs }
    }

    /** Returns the last lyric line whose timestamp is not after [positionMs]. */
    fun currentIndex(lines: List<TimedLyric>, positionMs: Long): Int {
        var low = 0
        var high = lines.lastIndex
        var answer = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (lines[middle].timeMs <= positionMs) {
                answer = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return answer
    }
}
