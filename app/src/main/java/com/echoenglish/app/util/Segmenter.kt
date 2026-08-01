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

    fun fromCues(cues: List<SrtCue>, durationMs: Long, leadInMs: Long = 300, leadOutMs: Long = 500): List<Segment> =
        cues.mapNotNull { cue ->
            val start = (cue.startMs - leadInMs).coerceAtLeast(0)
            val end = (cue.endMs + leadOutMs).coerceAtMost(durationMs)
            if (end > start) Segment(start, end, cue.text) else null
        }
}
