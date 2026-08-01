package com.echoenglish.app.playback

enum class SegmentBoundaryAction { REPEAT_CURRENT, NEXT_SEGMENT, COMPLETE }

object SegmentPlaybackPolicy {
    const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L

    fun previousTargetIndex(currentIndex: Int, positionInSegmentMs: Long): Int =
        if (positionInSegmentMs > PREVIOUS_RESTART_THRESHOLD_MS) currentIndex else (currentIndex - 1).coerceAtLeast(0)

    fun boundaryAction(repeatCount: Int, repeatIndex: Int, isLastSegment: Boolean): SegmentBoundaryAction =
        when {
            PlaybackMath.shouldRepeatSegment(repeatCount, repeatIndex) -> SegmentBoundaryAction.REPEAT_CURRENT
            !isLastSegment -> SegmentBoundaryAction.NEXT_SEGMENT
            else -> SegmentBoundaryAction.COMPLETE
        }

    fun requiresExactBoundary(repeatCount: Int, segmentGapMs: Long, isLastSegment: Boolean, pendingSleepStop: Boolean): Boolean =
        repeatCount != 1 || segmentGapMs > 0 || isLastSegment || pendingSleepStop

    fun normalizedGapMs(value: Long): Long = value.coerceIn(0L, 5_000L)
}
