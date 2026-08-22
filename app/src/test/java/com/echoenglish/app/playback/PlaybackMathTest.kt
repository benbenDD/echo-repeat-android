package com.echoenglish.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackMathTest {
    @Test fun repeatOnceNeverLoopsAtBoundary() {
        assertEquals(false, PlaybackMath.shouldRepeatSegment(1, 1))
    }

    @Test fun finiteRepeatLoopsUntilRequestedCount() {
        assertEquals(true, PlaybackMath.shouldRepeatSegment(3, 1))
        assertEquals(true, PlaybackMath.shouldRepeatSegment(3, 2))
        assertEquals(false, PlaybackMath.shouldRepeatSegment(3, 3))
    }

    @Test fun zeroRepeatCountMeansInfiniteLooping() {
        assertEquals(true, PlaybackMath.shouldRepeatSegment(0, 999))
    }
    @Test fun adjacentBoundaryBelongsToNextSegment() {
        val starts = longArrayOf(0, 10_000, 20_000)
        val ends = longArrayOf(10_000, 20_000, 30_000)
        assertEquals(1, PlaybackMath.segmentIndexAt(starts, ends, 10_000))
    }

    @Test fun absolutePositionFindsFiveSecondSegment() {
        val starts = LongArray(24) { it * 5_000L }
        val ends = LongArray(24) { (it + 1) * 5_000L }
        assertEquals(6, PlaybackMath.segmentIndexAt(starts, ends, 32_000L))
    }

    @Test fun absoluteSeekFindsRequestedSegment() {
        val starts = longArrayOf(0, 10_000, 20_000, 30_000)
        val ends = longArrayOf(10_000, 20_000, 30_000, 40_000)
        assertEquals(2, PlaybackMath.segmentIndexAt(starts, ends, 25_000))
    }

    @Test fun subtitleIndexUsesLastStartedCue() {
        assertEquals(1, PlaybackMath.subtitleIndexAt(longArrayOf(1_000, 4_000, 9_000), 7_000))
    }

    @Test fun mergedSegmentAdvancesToTheCueThatHasActuallyStarted() {
        val starts = longArrayOf(1_000, 2_500, 4_000)
        val ends = longArrayOf(3_000, 4_200, 5_000)

        assertEquals(
            1,
            PlaybackMath.subtitleIndexForPlayback(
                starts = starts,
                ends = ends,
                positionMs = 2_700,
                segmentStartMs = 700,
                segmentEndMs = 5_500,
                preferSegmentCueDuringLeadIn = true
            )
        )
    }

    @Test fun cueOnlyLeadInShowsUpcomingCueInsteadOfPreviousTimelineCue() {
        assertEquals(
            1,
            PlaybackMath.subtitleIndexForPlayback(
                starts = longArrayOf(1_000, 5_000),
                ends = longArrayOf(2_000, 6_000),
                positionMs = 4_700,
                segmentStartMs = 4_700,
                segmentEndMs = 6_500,
                preferSegmentCueDuringLeadIn = true
            )
        )
    }
}
