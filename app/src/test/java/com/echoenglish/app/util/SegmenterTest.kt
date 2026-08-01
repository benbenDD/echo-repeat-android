package com.echoenglish.app.util

import com.echoenglish.app.model.SrtCue
import org.junit.Assert.*
import org.junit.Test

class SegmenterTest {
    @Test fun fixedKeepsRemainder() {
        val segments = Segmenter.fixed(125_000, 15_000)
        assertEquals(9, segments.size)
        assertEquals(120_000, segments.last().startMs)
        assertEquals(125_000, segments.last().endMs)
    }

    @Test fun shortAudioIsOneSegment() {
        val segments = Segmenter.fixed(4_200, 10_000)
        assertEquals(1, segments.size)
        assertEquals(4_200, segments.first().endMs)
    }

    @Test fun subtitleOffsetsAreClamped() {
        val result = Segmenter.fromCues(listOf(SrtCue(1, 100, 9_800, "Hello")), 10_000, 300, 500)
        assertEquals(0, result.first().startMs)
        assertEquals(10_000, result.first().endMs)
    }

    @Test fun fullTimelineSubtitleSegmentsPartitionEntireAudioWithoutOverlap() {
        val result = Segmenter.fromCues(
            listOf(
                SrtCue(1, 1_000, 3_000, "A"),
                SrtCue(2, 3_200, 5_000, "B"),
                SrtCue(3, 8_000, 9_000, "C")
            ),
            durationMs = 10_000,
            leadInMs = 300,
            leadOutMs = 500
        )

        assertEquals(0, result.first().startMs)
        assertEquals(10_000, result.last().endMs)
        assertTrue(result.zipWithNext().all { (previous, next) -> previous.endMs == next.startMs })
    }

    @Test fun fullTimelineMergesOriginallyOverlappingCuesBeforePartitioning() {
        val result = Segmenter.fromCues(
            listOf(
                SrtCue(1, 1_000, 4_000, "A"),
                SrtCue(2, 3_000, 5_000, "B"),
                SrtCue(3, 7_000, 8_000, "C")
            ),
            10_000
        )

        assertEquals(2, result.size)
        assertEquals("A\nB", result.first().text)
        assertEquals(result.first().endMs, result.last().startMs)
    }

    @Test fun normalizeMergesOverlappingRanges() {
        val result = Segmenter.normalize(
            listOf(
                com.echoenglish.app.model.Segment(5_000, 9_000, "A"),
                com.echoenglish.app.model.Segment(8_000, 12_000, "B")
            ),
            20_000
        )

        assertEquals(1, result.size)
        assertEquals(5_000, result.single().startMs)
        assertEquals(12_000, result.single().endMs)
    }

    @Test fun fixedFiveTenAndFifteenSecondSegmentsAreStrictlyContiguous() {
        listOf(5_000L, 10_000L, 15_000L).forEach { segmentMs ->
            val result = Segmenter.fixed(62_345, segmentMs)
            assertEquals(0, result.first().startMs)
            assertEquals(62_345, result.last().endMs)
            assertTrue(
                result.zipWithNext().all { (previous, next) ->
                    previous.endMs == next.startMs
                }
            )
        }
    }

    @Test fun normalizeClampsInvalidRangesAndPreservesTouchingRanges() {
        val result = Segmenter.normalize(
            listOf(
                com.echoenglish.app.model.Segment(-500, 2_000, "A"),
                com.echoenglish.app.model.Segment(2_000, 4_000, "B"),
                com.echoenglish.app.model.Segment(9_000, 8_000, "invalid")
            ),
            10_000
        )

        assertEquals(2, result.size)
        assertEquals(0, result.first().startMs)
        assertEquals(result.first().endMs, result.last().startMs)
    }
}
