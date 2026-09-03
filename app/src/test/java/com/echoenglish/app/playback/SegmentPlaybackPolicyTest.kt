package com.echoenglish.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentPlaybackPolicyTest {
    @Test fun previousRestartsCurrentAfterThreeSeconds() {
        assertEquals(4, SegmentPlaybackPolicy.previousTargetIndex(4, 3_001))
    }

    @Test fun previousMovesBackAtOrBeforeThreeSeconds() {
        assertEquals(3, SegmentPlaybackPolicy.previousTargetIndex(4, 3_000))
        assertEquals(3, SegmentPlaybackPolicy.previousTargetIndex(4, 0))
    }

    @Test fun previousOnFirstSegmentStaysOnFirstSegment() {
        assertEquals(0, SegmentPlaybackPolicy.previousTargetIndex(0, 0))
    }

    @Test fun finiteRepeatsUseTheSameSegmentUntilCountIsReached() {
        repeat(9) { zeroBased ->
            assertEquals(
                SegmentBoundaryAction.REPEAT_CURRENT,
                SegmentPlaybackPolicy.boundaryAction(10, zeroBased + 1, false)
            )
        }
        assertEquals(
            SegmentBoundaryAction.NEXT_SEGMENT,
            SegmentPlaybackPolicy.boundaryAction(10, 10, false)
        )
    }

    @Test fun finalSegmentCompletesAfterFinalRepeat() {
        assertEquals(
            SegmentBoundaryAction.COMPLETE,
            SegmentPlaybackPolicy.boundaryAction(3, 3, true)
        )
    }

    @Test fun infiniteRepeatNeverAdvances() {
        assertEquals(
            SegmentBoundaryAction.REPEAT_CURRENT,
            SegmentPlaybackPolicy.boundaryAction(0, 10_000, false)
        )
    }

    @Test fun onePassWithoutGapKeepsAdjacentSegmentsContinuous() {
        assertFalse(
            SegmentPlaybackPolicy.requiresExactBoundary(
                repeatCount = 1,
                segmentGapMs = 0,
                isLastSegment = false,
                pendingSleepStop = false
            )
        )
    }

    @Test fun repeatsLastSegmentAndSleepTimerRequireExactBoundary() {
        assertTrue(SegmentPlaybackPolicy.requiresExactBoundary(3, 0, false, false))
        assertFalse(SegmentPlaybackPolicy.requiresExactBoundary(1, 500, false, false))
        assertTrue(SegmentPlaybackPolicy.requiresExactBoundary(1, 0, true, false))
        assertTrue(SegmentPlaybackPolicy.requiresExactBoundary(1, 0, false, true))
        assertTrue(
            SegmentPlaybackPolicy.requiresExactBoundary(
                repeatCount = 1,
                segmentGapMs = 0,
                isLastSegment = false,
                pendingSleepStop = false,
                followAlongEnabled = true
            )
        )
    }

    @Test fun followAlongWaitMatchesActualPlaybackDurationAtNormalSpeed() {
        assertEquals(
            8_000L,
            SegmentPlaybackPolicy.boundaryPauseDurationMs(
                action = SegmentBoundaryAction.REPEAT_CURRENT,
                segmentDurationMs = 8_000L,
                playbackSpeed = 1f,
                configuredGapMs = 500L,
                followAlongEnabled = true
            )
        )
    }

    @Test fun followAlongWaitScalesWithPlaybackSpeed() {
        assertEquals(
            16_000L,
            SegmentPlaybackPolicy.boundaryPauseDurationMs(
                SegmentBoundaryAction.NEXT_SEGMENT,
                segmentDurationMs = 8_000L,
                playbackSpeed = .5f,
                configuredGapMs = 0L,
                followAlongEnabled = true
            )
        )
        assertEquals(
            4_000L,
            SegmentPlaybackPolicy.boundaryPauseDurationMs(
                SegmentBoundaryAction.COMPLETE,
                segmentDurationMs = 8_000L,
                playbackSpeed = 2f,
                configuredGapMs = 0L,
                followAlongEnabled = true
            )
        )
    }

    @Test fun configuredGapIsUsedOnlyWhenFollowAlongIsOff() {
        assertEquals(
            1_000L,
            SegmentPlaybackPolicy.boundaryPauseDurationMs(
                SegmentBoundaryAction.REPEAT_CURRENT,
                segmentDurationMs = 8_000L,
                playbackSpeed = 1f,
                configuredGapMs = 1_000L,
                followAlongEnabled = false
            )
        )
        assertEquals(
            0L,
            SegmentPlaybackPolicy.boundaryPauseDurationMs(
                SegmentBoundaryAction.NEXT_SEGMENT,
                segmentDurationMs = 8_000L,
                playbackSpeed = 1f,
                configuredGapMs = 1_000L,
                followAlongEnabled = false
            )
        )
    }

    @Test fun followAlongSubtitleReplaysTheSegmentTimelineAtNormalSpeed() {
        assertEquals(
            10_000L,
            SegmentPlaybackPolicy.followAlongSubtitlePositionMs(
                segmentStartMs = 10_000L,
                segmentEndMs = 18_000L,
                gapDurationMs = 8_000L,
                gapRemainingMs = 8_000L,
                playbackSpeed = 1f
            )
        )
        assertEquals(
            14_000L,
            SegmentPlaybackPolicy.followAlongSubtitlePositionMs(
                segmentStartMs = 10_000L,
                segmentEndMs = 18_000L,
                gapDurationMs = 8_000L,
                gapRemainingMs = 4_000L,
                playbackSpeed = 1f
            )
        )
    }

    @Test fun followAlongSubtitleTimelineUsesThePlaybackSpeedFromGapStart() {
        assertEquals(
            14_000L,
            SegmentPlaybackPolicy.followAlongSubtitlePositionMs(
                segmentStartMs = 10_000L,
                segmentEndMs = 18_000L,
                gapDurationMs = 4_000L,
                gapRemainingMs = 2_000L,
                playbackSpeed = 2f
            )
        )
        assertEquals(
            17_999L,
            SegmentPlaybackPolicy.followAlongSubtitlePositionMs(
                segmentStartMs = 10_000L,
                segmentEndMs = 18_000L,
                gapDurationMs = 4_000L,
                gapRemainingMs = 0L,
                playbackSpeed = 2f
            )
        )
    }

    @Test fun supportedGapValuesArePreserved() {
        listOf(0L, 500L, 1_000L, 2_000L, 3_000L, 5_000L).forEach {
            assertEquals(it, SegmentPlaybackPolicy.normalizedGapMs(it))
        }
    }

    @Test fun outOfRangeGapValuesAreClamped() {
        assertEquals(0L, SegmentPlaybackPolicy.normalizedGapMs(-1))
        assertEquals(5_000L, SegmentPlaybackPolicy.normalizedGapMs(8_000))
    }

    @Test fun repeatedPlaybackAlwaysUsesIdenticalBoundaries() {
        val start = 12_345L
        val end = 18_765L
        val ranges = buildList {
            repeat(10) { add(start to end) }
        }
        assertEquals(1, ranges.distinct().size)
        assertEquals(start to end, ranges.first())
    }

    @Test fun adjacentRangesDoNotOverlap() {
        val starts = longArrayOf(0, 5_000, 10_000)
        val ends = longArrayOf(5_000, 10_000, 15_000)
        assertTrue((0 until ends.lastIndex).all { ends[it] <= starts[it + 1] })
    }

    @Test fun repeatedLoadStartsAtExactSegmentBoundary() {
        assertEquals(
            1_635_000L,
            SegmentPlaybackPolicy.alignedInitialPosition(
                requestedPositionMs = 1_638_578L,
                segmentStartMs = 1_635_000L,
                repeatCount = 5
            )
        )
        assertEquals(
            1_635_000L,
            SegmentPlaybackPolicy.alignedInitialPosition(
                requestedPositionMs = 1_638_578L,
                segmentStartMs = 1_635_000L,
                repeatCount = 0
            )
        )
    }

    @Test fun singlePassMayResumeInsideSegment() {
        assertEquals(
            1_638_578L,
            SegmentPlaybackPolicy.alignedInitialPosition(
                requestedPositionMs = 1_638_578L,
                segmentStartMs = 1_635_000L,
                repeatCount = 1
            )
        )
    }

    @Test fun coldRestoreKeepsExactPositionEvenWhenSegmentRepeats() {
        assertEquals(
            12_345L,
            SegmentPlaybackPolicy.initialPosition(
                requestedPositionMs = 12_345L,
                segmentStartMs = 10_000L,
                repeatCount = 5,
                restoreExactPosition = true
            )
        )
    }

    @Test fun manualLoadStillAlignsRepeatedSegmentToItsStart() {
        assertEquals(
            10_000L,
            SegmentPlaybackPolicy.initialPosition(
                requestedPositionMs = 12_345L,
                segmentStartMs = 10_000L,
                repeatCount = 5,
                restoreExactPosition = false
            )
        )
    }

    @Test fun finiteRepeatIndexIsClampedToConfiguredCount() {
        assertEquals(1, SegmentPlaybackPolicy.normalizedRepeatIndex(5, 0))
        assertEquals(4, SegmentPlaybackPolicy.normalizedRepeatIndex(5, 4))
        assertEquals(5, SegmentPlaybackPolicy.normalizedRepeatIndex(5, 8))
    }

    @Test fun infiniteRepeatIndexKeepsAnyPositiveIteration() {
        assertEquals(1, SegmentPlaybackPolicy.normalizedRepeatIndex(0, 0))
        assertEquals(27, SegmentPlaybackPolicy.normalizedRepeatIndex(0, 27))
    }

    @Test fun configuredGapOnlyAppliesBetweenRepeatsOfSameSegment() {
        assertTrue(
            SegmentPlaybackPolicy.shouldInsertGap(
                SegmentBoundaryAction.REPEAT_CURRENT,
                1_000
            )
        )
        assertFalse(
            SegmentPlaybackPolicy.shouldInsertGap(
                SegmentBoundaryAction.NEXT_SEGMENT,
                1_000
            )
        )
        assertFalse(
            SegmentPlaybackPolicy.shouldInsertGap(
                SegmentBoundaryAction.COMPLETE,
                1_000
            )
        )
    }

    @Test fun finalRepeatCanContinueIntoAdjacentNextSegment() {
        assertTrue(
            SegmentPlaybackPolicy.canContinueIntoAdjacentNext(
                repeatCount = 5,
                repeatIndex = 5,
                hasNextSegment = true,
                isAdjacent = true
            )
        )
        assertFalse(
            SegmentPlaybackPolicy.canContinueIntoAdjacentNext(
                repeatCount = 5,
                repeatIndex = 4,
                hasNextSegment = true,
                isAdjacent = true
            )
        )
        assertFalse(
            SegmentPlaybackPolicy.canContinueIntoAdjacentNext(
                repeatCount = 5,
                repeatIndex = 5,
                hasNextSegment = true,
                isAdjacent = false
            )
        )
    }

    @Test fun infiniteRepeatNeverContinuesIntoNextSegment() {
        assertFalse(
            SegmentPlaybackPolicy.canContinueIntoAdjacentNext(
                repeatCount = 0,
                repeatIndex = 99,
                hasNextSegment = true,
                isAdjacent = true
            )
        )
    }

    @Test fun finalRepeatReusesMatchingPreparedWindow() {
        assertTrue(
            SegmentPlaybackPolicy.canReusePreparedWindow(
                hasCurrentMediaItem = true,
                currentPipelineClipped = true,
                requiredPipelineClipped = true,
                currentWindowStartMs = 15_000,
                requiredWindowStartMs = 15_000,
                currentWindowEndMs = 20_000,
                requiredWindowEndMs = 20_000
            )
        )
    }

    @Test fun finalRepeatQueueRequirementDoesNotInvalidateWindowReuse() {
        val shouldQueueNext = SegmentPlaybackPolicy.canContinueIntoAdjacentNext(
            repeatCount = 5,
            repeatIndex = 5,
            hasNextSegment = true,
            isAdjacent = true
        )
        val canReuseCurrent = SegmentPlaybackPolicy.canReusePreparedWindow(
            hasCurrentMediaItem = true,
            currentPipelineClipped = true,
            requiredPipelineClipped = true,
            currentWindowStartMs = 15_000,
            requiredWindowStartMs = 15_000,
            currentWindowEndMs = 20_000,
            requiredWindowEndMs = 20_000
        )

        assertTrue(shouldQueueNext)
        assertTrue(canReuseCurrent)
    }

    @Test fun finiteRepeatCountsQueueOnlyOnTheirFinalRepeat() {
        listOf(2, 3, 5, 10).forEach { count ->
            assertFalse(
                SegmentPlaybackPolicy.canContinueIntoAdjacentNext(
                    repeatCount = count,
                    repeatIndex = count - 1,
                    hasNextSegment = true,
                    isAdjacent = true
                )
            )
            assertTrue(
                SegmentPlaybackPolicy.canContinueIntoAdjacentNext(
                    repeatCount = count,
                    repeatIndex = count,
                    hasNextSegment = true,
                    isAdjacent = true
                )
            )
        }
    }

    @Test fun preparedWindowCannotBeReusedWhenMediaItemIsMissing() {
        assertFalse(
            SegmentPlaybackPolicy.canReusePreparedWindow(
                false, true, true, 15_000, 15_000, 20_000, 20_000
            )
        )
    }

    @Test fun preparedWindowCannotBeReusedWhenClippingModeChanges() {
        assertFalse(
            SegmentPlaybackPolicy.canReusePreparedWindow(
                true, false, true, 15_000, 15_000, 20_000, 20_000
            )
        )
    }

    @Test fun preparedWindowCannotBeReusedWhenBoundariesChange() {
        assertFalse(
            SegmentPlaybackPolicy.canReusePreparedWindow(
                true, true, true, 14_999, 15_000, 20_000, 20_000
            )
        )
        assertFalse(
            SegmentPlaybackPolicy.canReusePreparedWindow(
                true, true, true, 15_000, 15_000, 20_001, 20_000
            )
        )
    }}
