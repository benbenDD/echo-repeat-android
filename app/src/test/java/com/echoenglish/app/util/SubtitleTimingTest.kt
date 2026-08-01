package com.echoenglish.app.util

import com.echoenglish.app.model.SrtCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTimingTest {
    private val cue = SrtCue(7, 2_000, 4_000, "Hello")

    @Test fun zeroOffsetPreservesCueTimes() {
        assertEquals(listOf(cue), SubtitleTiming.adjustCues(listOf(cue), 0, 10_000))
    }

    @Test fun negativeOffsetMovesSubtitleEarlier() {
        val result = SubtitleTiming.adjustCues(listOf(cue), -500, 10_000).single()
        assertEquals(1_500, result.startMs)
        assertEquals(3_500, result.endMs)
    }

    @Test fun positiveOffsetMovesSubtitleLater() {
        val result = SubtitleTiming.adjustCues(listOf(cue), 500, 10_000).single()
        assertEquals(2_500, result.startMs)
        assertEquals(4_500, result.endMs)
    }

    @Test fun earlyCueIsClampedAtAudioStart() {
        val result = SubtitleTiming.adjustCues(
            listOf(SrtCue(1, 1_000, 3_000, "A")), -1_500, 10_000
        ).single()
        assertEquals(0, result.startMs)
        assertEquals(1_500, result.endMs)
    }

    @Test fun delayedCueIsClampedAtAudioEnd() {
        val result = SubtitleTiming.adjustCues(
            listOf(SrtCue(1, 7_000, 9_000, "A")), 1_500, 10_000
        ).single()
        assertEquals(8_500, result.startMs)
        assertEquals(10_000, result.endMs)
    }

    @Test fun cueMovedCompletelyBeforeAudioIsFiltered() {
        assertTrue(
            SubtitleTiming.adjustCues(
                listOf(SrtCue(1, 100, 500, "A")), -1_000, 10_000
            ).isEmpty()
        )
    }

    @Test fun cueMovedCompletelyAfterAudioIsFiltered() {
        assertTrue(
            SubtitleTiming.adjustCues(
                listOf(SrtCue(1, 9_700, 9_900, "A")), 1_000, 10_000
            ).isEmpty()
        )
    }

    @Test fun adjustedCuesAreSortedByTime() {
        val result = SubtitleTiming.adjustCues(
            listOf(
                SrtCue(2, 5_000, 6_000, "B"),
                SrtCue(1, 1_000, 2_000, "A")
            ),
            -500,
            10_000
        )
        assertEquals(listOf(1, 2), result.map { it.index })
    }

    @Test fun offsetIsLimitedToTenSeconds() {
        assertEquals(10_000, SubtitleTiming.normalizedOffsetMs(50_000))
        assertEquals(-10_000, SubtitleTiming.normalizedOffsetMs(-50_000))
    }

    @Test fun originalCueListIsNeverMutated() {
        val original = listOf(cue)
        SubtitleTiming.adjustCues(original, -500, 10_000)
        assertEquals(2_000, original.single().startMs)
        assertEquals(4_000, original.single().endMs)
    }

    @Test fun repeatedCalculationFromOriginalDoesNotDoubleApplyOffset() {
        val first = SubtitleTiming.adjustCues(listOf(cue), -500, 10_000)
        val second = SubtitleTiming.adjustCues(listOf(cue), -500, 10_000)
        assertEquals(first, second)
        assertEquals(1_500, second.single().startMs)
    }

    @Test fun blankAndInvalidCuesAreFiltered() {
        val result = SubtitleTiming.adjustCues(
            listOf(
                SrtCue(1, 1_000, 2_000, "  "),
                SrtCue(2, 3_000, 2_000, "invalid"),
                cue
            ),
            0,
            10_000
        )
        assertEquals(listOf(cue), result)
    }

    @Test fun fixedSegmentBoundariesDoNotChangeWithSubtitleOffset() {
        val before = Segmenter.fixed(31_000, 10_000)
        SubtitleTiming.adjustCues(listOf(cue), 2_000, 31_000)
        val after = Segmenter.fixed(31_000, 10_000)
        assertEquals(before, after)
    }

    @Test fun subtitleOffsetIsAppliedBeforeCuePadding() {
        val adjusted = SubtitleTiming.adjustCues(listOf(cue), -500, 10_000)
        val padded = Segmenter.cueOnly(adjusted, 10_000, 300, 500).single()
        assertEquals(1_200, padded.startMs)
        assertEquals(4_000, padded.endMs)
    }

    @Test fun offsetOverlapStillProducesNonOverlappingSegments() {
        val adjusted = SubtitleTiming.adjustCues(
            listOf(
                SrtCue(1, 1_000, 2_000, "A"),
                SrtCue(2, 2_200, 3_000, "B")
            ),
            -500,
            10_000
        )
        val segments = Segmenter.cueOnly(adjusted, 10_000, 500, 500)
        assertTrue(segments.zipWithNext().all { (previous, next) -> previous.endMs <= next.startMs })
    }
}
