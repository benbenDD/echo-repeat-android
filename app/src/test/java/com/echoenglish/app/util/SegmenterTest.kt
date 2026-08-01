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
}
