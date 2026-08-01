package com.echoenglish.app.util

import com.echoenglish.app.model.Segment
import com.echoenglish.app.model.SrtCue

object Segmenter {
    fun fixed(durationMs: Long, segmentMs: Long): List<Segment> {
        if (durationMs <= 0 || segmentMs <= 0) return emptyList()
        val result = mutableListOf<Segment>()
        var start = 0L
        while (start < durationMs) {
            val end = (start + segmentMs).coerceAtMost(durationMs)
            result += Segment(start, end)
            start = end
        }
        return result
    }

    fun fromCues(
        cues: List<SrtCue>,
        durationMs: Long,
        leadInMs: Long = 300,
        leadOutMs: Long = 500
    ): List<Segment> =
        cues.mapNotNull { cue ->
            val start = (cue.startMs - leadInMs).coerceAtLeast(0)
            val end = (cue.endMs + leadOutMs).coerceAtMost(durationMs)
            if (end > start && cue.text.isNotBlank()) Segment(start, end, cue.text.trim()) else null
        }

    fun cueOnly(cues: List<SrtCue>, durationMs: Long): List<Segment> {
        if (durationMs <= 0) return emptyList()
        val normalized = cues.mapNotNull { cue ->
            val text = cue.text.trim()
            val start = cue.startMs.coerceIn(0L, durationMs)
            val end = cue.endMs.coerceIn(0L, durationMs)
            if (text.isBlank() || end <= start) null else Segment(start, end, text)
        }.sortedWith(compareBy<Segment> { it.startMs }.thenBy { it.endMs })

        val result = mutableListOf<Segment>()
        normalized.forEach { next ->
            val previous = result.lastOrNull()
            if (previous == null || next.startMs > previous.endMs) {
                result += next
            } else {
                val mergedText = listOf(previous.text, next.text)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString("\n")
                result[result.lastIndex] = previous.copy(
                    endMs = maxOf(previous.endMs, next.endMs),
                    text = mergedText
                )
            }
        }
        return result
    }
}
