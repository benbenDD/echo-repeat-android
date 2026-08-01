package com.echoenglish.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackMathTest {
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
}
