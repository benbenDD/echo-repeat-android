package com.echoenglish.app.util

import com.echoenglish.app.model.SrtCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleOnlySegmenterTest {
    @Test fun usesExactCueBoundariesWithoutLeadPadding() {
        val result = Segmenter.cueOnly(
            listOf(SrtCue(1, 5_000, 8_000, "Hello")),
            20_000
        )
        assertEquals(5_000, result.single().startMs)
        assertEquals(8_000, result.single().endMs)
    }

    @Test fun keepsSubtitleFreeGapsOutOfPlayableRanges() {
        val result = Segmenter.cueOnly(
            listOf(
                SrtCue(1, 5_000, 8_000, "A"),
                SrtCue(2, 12_000, 15_000, "B")
            ),
            20_000
        )
        assertEquals(listOf(5_000L, 12_000L), result.map { it.startMs })
        assertEquals(listOf(8_000L, 15_000L), result.map { it.endMs })
        assertTrue(result[0].endMs < result[1].startMs)
    }

    @Test fun sortsUnorderedCues() {
        val result = Segmenter.cueOnly(
            listOf(
                SrtCue(2, 12_000, 15_000, "B"),
                SrtCue(1, 5_000, 8_000, "A")
            ),
            20_000
        )
        assertEquals(listOf("A", "B"), result.map { it.text })
    }

    @Test fun ignoresBlankAndInvalidCues() {
        val result = Segmenter.cueOnly(
            listOf(
                SrtCue(1, 1_000, 2_000, " "),
                SrtCue(2, 4_000, 4_000, "zero"),
                SrtCue(3, 8_000, 7_000, "backwards"),
                SrtCue(4, 10_000, 12_000, "valid")
            ),
            20_000
        )
        assertEquals(1, result.size)
        assertEquals("valid", result.single().text)
    }

    @Test fun clampsCuesToAudioDuration() {
        val result = Segmenter.cueOnly(
            listOf(
                SrtCue(1, -500, 1_000, "start"),
                SrtCue(2, 9_000, 15_000, "end")
            ),
            10_000
        )
        assertEquals(0, result.first().startMs)
        assertEquals(10_000, result.last().endMs)
    }

    @Test fun mergesOverlappingCuesWithoutReplayingAudio() {
        val result = Segmenter.cueOnly(
            listOf(
                SrtCue(1, 5_000, 9_000, "first"),
                SrtCue(2, 8_000, 12_000, "second")
            ),
            20_000
        )
        assertEquals(1, result.size)
        assertEquals(5_000, result.single().startMs)
        assertEquals(12_000, result.single().endMs)
        assertEquals("first\nsecond", result.single().text)
    }

    @Test fun mergesTouchingCuesIntoOneContinuousRange() {
        val result = Segmenter.cueOnly(
            listOf(
                SrtCue(1, 1_000, 3_000, "A"),
                SrtCue(2, 3_000, 5_000, "B")
            ),
            10_000
        )
        assertEquals(1, result.size)
        assertEquals(1_000, result.single().startMs)
        assertEquals(5_000, result.single().endMs)
    }
}
