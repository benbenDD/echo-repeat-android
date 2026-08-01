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
}
