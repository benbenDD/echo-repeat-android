package com.echoenglish.app.util

import com.echoenglish.app.model.SrtCue
import com.echoenglish.app.model.SubtitleCalibration
import com.echoenglish.app.model.SubtitleCalibrationMode

object SubtitleTiming {
    const val MAX_OFFSET_MS = 10_000L

    fun normalizedOffsetMs(value: Long): Long = value.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)

    fun adjustCues(
        cues: List<SrtCue>, offsetMs: Long, durationMs: Long,
        calibration: SubtitleCalibration = SubtitleCalibration()
    ): List<SrtCue> {
        if (durationMs <= 0) return emptyList()
        val shift = normalizedOffsetMs(offsetMs)
        return cues.mapNotNull { cue ->
            val mappedStart = calibration.mapTime(cue.startMs)
            val mappedEnd = if (calibration.mode == SubtitleCalibrationMode.SECTION) {
                cue.endMs + mappedStart - cue.startMs
            } else calibration.mapTime(cue.endMs)
            val start = (mappedStart + shift).coerceIn(0L, durationMs)
            val end = (mappedEnd + shift).coerceIn(0L, durationMs)
            val text = cue.text.trim()
            if (text.isBlank() || end <= start) null else cue.copy(startMs = start, endMs = end, text = text)
        }.sortedWith(compareBy<SrtCue> { it.startMs }.thenBy { it.endMs }.thenBy { it.index })
    }

    fun parseTime(value: String): Long? {
        val parts = value.trim().replace(',', '.').split(':')
        if (parts.size !in 1..3) return null
        val seconds = parts.last().split('.')
        if (seconds.size !in 1..2 || seconds[0].isEmpty() || !seconds[0].all(Char::isDigit)) return null
        val whole = seconds[0].toLongOrNull() ?: return null
        if (parts.size > 1 && whole >= 60) return null
        val fraction = seconds.getOrNull(1).orEmpty()
        if (fraction.length > 3 || !fraction.all(Char::isDigit)) return null
        val minutes = if (parts.size > 1) parts[parts.lastIndex - 1].toLongOrNull() ?: return null else 0L
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
        if (minutes < 0 || hours < 0 || (parts.size == 3 && minutes >= 60)) return null
        if (hours > 999 || minutes > 59_999 || whole > 3_600_000) return null
        return (hours * 3600 + minutes * 60 + whole) * 1000 +
            fraction.padEnd(3, '0').toLong()
    }
}
