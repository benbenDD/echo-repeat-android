package com.echoenglish.app.playback

enum class SegmentBoundaryAction { REPEAT_CURRENT, NEXT_SEGMENT, COMPLETE }

object SegmentPlaybackPolicy {
    const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L
    private const val GAP_WAKE_LOCK_FINISH_MARGIN_MS = 30_000L

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
        skipSubtitleGaps: Boolean = false,
        followAlongEnabled: Boolean = false
    ): Boolean =
        skipSubtitleGaps ||
            followAlongEnabled ||
            repeatCount != 1 ||
            isLastSegment ||
            pendingSleepStop

    fun shouldInsertGap(action: SegmentBoundaryAction, segmentGapMs: Long): Boolean =
        action == SegmentBoundaryAction.REPEAT_CURRENT && segmentGapMs > 0

    fun boundaryPauseDurationMs(
        action: SegmentBoundaryAction,
        segmentDurationMs: Long,
        playbackSpeed: Float,
        configuredGapMs: Long,
        followAlongEnabled: Boolean
    ): Long = if (followAlongEnabled) {
        (segmentDurationMs.coerceAtLeast(0) / playbackSpeed.coerceAtLeast(0.25f))
            .toLong()
    } else if (shouldInsertGap(action, configuredGapMs)) {
        normalizedGapMs(configuredGapMs)
    } else {
        0L
    }

    fun followAlongSubtitlePositionMs(
        segmentStartMs: Long,
        segmentEndMs: Long,
        gapDurationMs: Long,
        gapRemainingMs: Long,
        playbackSpeed: Float
    ): Long {
        val safeStart = segmentStartMs.coerceAtLeast(0L)
        val safeEnd = segmentEndMs.coerceAtLeast(safeStart)
        if (safeEnd == safeStart) return safeStart
        val elapsedWallMs = (gapDurationMs - gapRemainingMs)
            .coerceIn(0L, gapDurationMs.coerceAtLeast(0L))
        val audioElapsedMs = (elapsedWallMs * playbackSpeed.coerceAtLeast(0.25f)).toLong()
        return (safeStart + audioElapsedMs).coerceIn(safeStart, safeEnd - 1L)
    }

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

    fun canReusePreparedWindow(
        hasCurrentMediaItem: Boolean,
        currentPipelineClipped: Boolean,
        requiredPipelineClipped: Boolean,
        currentWindowStartMs: Long,
        requiredWindowStartMs: Long,
        currentWindowEndMs: Long,
        requiredWindowEndMs: Long
    ): Boolean =
        hasCurrentMediaItem &&
            currentPipelineClipped == requiredPipelineClipped &&
            currentWindowStartMs == requiredWindowStartMs &&
            currentWindowEndMs == requiredWindowEndMs

    fun alignedInitialPosition(
        requestedPositionMs: Long,
        segmentStartMs: Long,
        repeatCount: Int
    ): Long = if (repeatCount == 1) requestedPositionMs else segmentStartMs

    fun initialPosition(
        requestedPositionMs: Long,
        segmentStartMs: Long,
        repeatCount: Int,
        restoreExactPosition: Boolean
    ): Long = if (restoreExactPosition) {
        requestedPositionMs
    } else {
        alignedInitialPosition(requestedPositionMs, segmentStartMs, repeatCount)
    }

    fun normalizedRepeatIndex(repeatCount: Int, requestedRepeatIndex: Int): Int =
        if (repeatCount <= 0) {
            requestedRepeatIndex.coerceAtLeast(1)
        } else {
            requestedRepeatIndex.coerceIn(1, repeatCount)
        }

    fun normalizedGapMs(value: Long): Long = value.coerceIn(0L, 5_000L)

    fun gapWakeLockTimeoutMs(gapDurationMs: Long): Long =
        gapDurationMs.coerceAtLeast(0L) + GAP_WAKE_LOCK_FINISH_MARGIN_MS
}
