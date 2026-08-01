package com.echoenglish.app.playback

enum class SegmentBoundaryAction { REPEAT_CURRENT, NEXT_SEGMENT, COMPLETE }

object SegmentPlaybackPolicy {
    const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L

    fun previousTargetIndex(currentIndex: Int, positionInSegmentMs: Long): Int =
        if (positionInSegmentMs > PREVIOUS_RESTART_THRESHOLD_MS) {
            currentIndex
        } else {
            (currentIndex - 1).coerceAtLeast(0)
        }

    fun boundaryAction(
        repeatCount: Int,
        repeatIndex: Int,
        isLastSegment: Boolean
    ): SegmentBoundaryAction =
        when {
            PlaybackMath.shouldRepeatSegment(repeatCount, repeatIndex) ->
                SegmentBoundaryAction.REPEAT_CURRENT
            !isLastSegment -> SegmentBoundaryAction.NEXT_SEGMENT
            else -> SegmentBoundaryAction.COMPLETE
        }

    @Suppress("UNUSED_PARAMETER")
    fun requiresExactBoundary(
        repeatCount: Int,
        segmentGapMs: Long,
        isLastSegment: Boolean,
        pendingSleepStop: Boolean,
        skipSubtitleGaps: Boolean = false
    ): Boolean =
        skipSubtitleGaps ||
            repeatCount != 1 ||
            isLastSegment ||
            pendingSleepStop

    fun shouldInsertGap(action: SegmentBoundaryAction, segmentGapMs: Long): Boolean =
        action == SegmentBoundaryAction.REPEAT_CURRENT && segmentGapMs > 0

    fun canContinueIntoAdjacentNext(
        repeatCount: Int,
        repeatIndex: Int,
        hasNextSegment: Boolean,
        isAdjacent: Boolean
    ): Boolean =
        repeatCount > 0 &&
            repeatIndex >= repeatCount &&
            hasNextSegment &&
            isAdjacent

    fun alignedInitialPosition(
        requestedPositionMs: Long,
        segmentStartMs: Long,
        repeatCount: Int
    ): Long = if (repeatCount == 1) requestedPositionMs else segmentStartMs

    fun normalizedGapMs(value: Long): Long = value.coerceIn(0L, 5_000L)
}
