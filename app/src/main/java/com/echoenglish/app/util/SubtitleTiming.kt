package com.echoenglish.app.util

import com.echoenglish.app.model.SrtCue

object SubtitleTiming {
    const val MAX_OFFSET_MS = 10_000L

    fun normalizedOffsetMs(value: Long): Long = value.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)

    fun adjustCues(cues: List<SrtCue>, offsetMs: Long, durationMs: Long): List<SrtCue> {
        if (durationMs <= 0) return emptyList()
        val shift = normalizedOffsetMs(offsetMs)
        return cues.mapNotNull { cue ->
            val start = (cue.startMs + shift).coerceIn(0L, durationMs)
            val end = (cue.endMs + shift).coerceIn(0L, durationMs)
            val text = cue.text.trim()
            if (text.isBlank() || end <= start) null else cue.copy(startMs = start, endMs = end, text = text)
        }.sortedWith(compareBy<SrtCue> { it.startMs }.thenBy { it.endMs }.thenBy { it.index })
    }
}
