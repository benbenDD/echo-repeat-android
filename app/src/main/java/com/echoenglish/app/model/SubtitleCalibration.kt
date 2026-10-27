package com.echoenglish.app.model

import kotlin.math.roundToLong

enum class SubtitleCalibrationMode { GRADUAL, SECTION }

data class SubtitleCalibrationPoint(val cueId: Int, val subtitleTimeMs: Long, val audioTimeMs: Long)

data class SubtitleCalibration(
    val mode: SubtitleCalibrationMode = SubtitleCalibrationMode.GRADUAL,
    val points: List<SubtitleCalibrationPoint> = emptyList()
) {
    fun mapTime(sourceMs: Long): Long {
        val ordered = points.sortedBy { it.subtitleTimeMs }
        if (ordered.isEmpty()) return sourceMs
        if (mode == SubtitleCalibrationMode.SECTION) {
            val anchor = ordered.lastOrNull { it.subtitleTimeMs <= sourceMs } ?: return sourceMs
            return sourceMs + anchor.audioTimeMs - anchor.subtitleTimeMs
        }
        val left = ordered.lastOrNull { it.subtitleTimeMs <= sourceMs }
        val right = ordered.firstOrNull { it.subtitleTimeMs > sourceMs }
        if (left == null) return sourceMs + ordered.first().let { it.audioTimeMs - it.subtitleTimeMs }
        if (right == null) return sourceMs + left.audioTimeMs - left.subtitleTimeMs
        val fraction = (sourceMs - left.subtitleTimeMs).toDouble() /
            (right.subtitleTimeMs - left.subtitleTimeMs)
        return left.audioTimeMs + ((right.audioTimeMs - left.audioTimeMs) * fraction).roundToLong()
    }

    fun validationError(durationMs: Long, offsetMs: Long): String? {
        val ordered = points.sortedBy { it.subtitleTimeMs }
        if (ordered.any { it.audioTimeMs + offsetMs !in 0L..durationMs })
            return "音频时间必须在这条音频的时长范围内"
        if (ordered.zipWithNext().any { (a, b) -> a.subtitleTimeMs == b.subtitleTimeMs })
            return "这个原始时间已有校准点，请编辑原有校准点"
        if (ordered.zipWithNext().any { (a, b) -> a.audioTimeMs >= b.audioTimeMs })
            return "后面的字幕必须对应更晚的音频时间，请检查校准点"
        return null
    }
}
