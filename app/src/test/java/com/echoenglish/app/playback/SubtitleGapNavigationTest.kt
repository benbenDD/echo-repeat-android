package com.echoenglish.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleGapNavigationTest {
    private val starts = longArrayOf(5_000, 12_000, 20_000)
    private val ends = longArrayOf(8_000, 15_000, 23_000)

    @Test fun positionBeforeFirstCueSnapsToFirstCue() {
        assertEquals(
            5_000,
            PlaybackMath.snapToPlayablePosition(starts, ends, 0, 30_000, true)
        )
    }

    @Test fun positionInsideCueIsPreserved() {
        assertEquals(
            6_500,
            PlaybackMath.snapToPlayablePosition(starts, ends, 6_500, 30_000, true)
        )
    }

    @Test fun positionBetweenCuesSnapsToNextCue() {
        assertEquals(
            12_000,
            PlaybackMath.snapToPlayablePosition(starts, ends, 10_000, 30_000, true)
        )
    }

    @Test fun positionAfterLastCueReturnsToLastCueStart() {
        assertEquals(
            20_000,
            PlaybackMath.snapToPlayablePosition(starts, ends, 29_000, 30_000, true)
        )
    }

    @Test fun fullTimelineDoesNotSnapSubtitleFreePosition() {
        assertEquals(
            10_000,
            PlaybackMath.snapToPlayablePosition(starts, ends, 10_000, 30_000, false)
        )
    }

    @Test fun subtitleOnlyForcesBoundaryEvenForOnePassWithoutGap() {
        assertEquals(
            true,
            SegmentPlaybackPolicy.requiresExactBoundary(
                repeatCount = 1,
                segmentGapMs = 0,
                isLastSegment = false,
                pendingSleepStop = false,
                skipSubtitleGaps = true
            )
        )
    }
}
