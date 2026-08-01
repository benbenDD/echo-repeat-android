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
    ): List<Segment> {
        if (durationMs <= 0) return emptyList()
        val mergedCues = cueOnly(cues, durationMs)
        if (mergedCues.isEmpty()) return emptyList()
        if (mergedCues.size == 1) {
            return listOf(Segment(0, durationMs, mergedCues.single().text))
        }

        val boundaries = mutableListOf(0L)
        for (index in 0 until mergedCues.lastIndex) {
            val previous = mergedCues[index]
            val next = mergedCues[index + 1]
            val preferredPreviousEnd = (previous.endMs + leadOutMs)
                .coerceIn(0L, durationMs)
            val preferredNextStart = (next.startMs - leadInMs)
                .coerceIn(0L, durationMs)
            val midpoint = preferredPreviousEnd +
                (preferredNextStart - preferredPreviousEnd) / 2
            val minimum = boundaries.last() + 1
            val maximum = durationMs - (mergedCues.lastIndex - index)
            boundaries += midpoint.coerceIn(minimum, maximum.coerceAtLeast(minimum))
        }
        boundaries += durationMs

        return mergedCues.indices.mapNotNull { index ->
            val start = boundaries[index]
            val end = boundaries[index + 1]
            if (end > start) Segment(start, end, mergedCues[index].text) else null
        }
    }

    fun cueOnly(
        cues: List<SrtCue>,
        durationMs: Long,
        leadInMs: Long = 0,
        leadOutMs: Long = 0
    ): List<Segment> {
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
        if (result.isEmpty()) return emptyList()

        val before = leadInMs.coerceIn(0L, MAX_CUE_PADDING_MS)
        val after = leadOutMs.coerceIn(0L, MAX_CUE_PADDING_MS)
        if (before == 0L && after == 0L) return result

        val starts = result.map { (it.startMs - before).coerceAtLeast(0L) }.toMutableList()
        val ends = result.map { (it.endMs + after).coerceAtMost(durationMs) }.toMutableList()

        for (index in 0 until result.lastIndex) {
            if (ends[index] > starts[index + 1]) {
                val originalGap = result[index + 1].startMs - result[index].endMs
                val sharedBoundary = result[index].endMs + originalGap / 2
                ends[index] = sharedBoundary
                starts[index + 1] = sharedBoundary
            }
        }

        return result.indices.mapNotNull { index ->
            val start = starts[index]
            val end = ends[index]
            if (end > start) result[index].copy(startMs = start, endMs = end) else null
        }
    }

    private const val MAX_CUE_PADDING_MS = 5_000L

    fun normalize(
        segments: List<Segment>,
        durationMs: Long,
        mergeOverlaps: Boolean = true
    ): List<Segment> {
        if (durationMs <= 0) return emptyList()
        val sorted = segments.mapNotNull { segment ->
            val start = segment.startMs.coerceIn(0L, durationMs)
            val end = segment.endMs.coerceIn(0L, durationMs)
            if (end > start) segment.copy(startMs = start, endMs = end) else null
        }.sortedWith(compareBy<Segment> { it.startMs }.thenBy { it.endMs })

        val result = mutableListOf<Segment>()
        sorted.forEach { next ->
            val previous = result.lastOrNull()
            when {
                previous == null || next.startMs >= previous.endMs -> result += next
                mergeOverlaps -> {
                    result[result.lastIndex] = previous.copy(
                        endMs = maxOf(previous.endMs, next.endMs),
                        text = listOf(previous.text, next.text)
                            .filter(String::isNotBlank)
                            .distinct()
                            .joinToString("\n")
                    )
                }
                next.endMs > previous.endMs -> result += next.copy(startMs = previous.endMs)
            }
        }
        return result
    }
}
