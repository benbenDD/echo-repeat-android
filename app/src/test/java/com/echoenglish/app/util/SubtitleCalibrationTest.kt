package com.echoenglish.app.util

import com.echoenglish.app.model.SrtCue
import com.echoenglish.app.model.SubtitleCalibration
import com.echoenglish.app.model.SubtitleCalibrationMode
import com.echoenglish.app.model.SubtitleCalibrationPoint
import org.junit.Assert.*
import org.junit.Test

class SubtitleCalibrationTest {
    private fun point(source: Long, audio: Long, id: Int = source.toInt()) =
        SubtitleCalibrationPoint(id, source, audio)

    @Test fun noPointsKeepsOriginalAndOnePointActsAsOffset() {
        assertEquals(5_000L, SubtitleCalibration().mapTime(5_000))
        val calibration = SubtitleCalibration(points = listOf(point(10_000, 12_000)))
        assertEquals(7_000L, calibration.mapTime(5_000))
        assertEquals(22_000L, calibration.mapTime(20_000))
    }

    @Test fun gradualModeInterpolatesAndKeepsNearestOffsetOutsideAnchors() {
        val calibration = SubtitleCalibration(points = listOf(point(1_000, 2_000), point(11_000, 14_000)))
        assertEquals(8_000L, calibration.mapTime(6_000))
        assertEquals(1_000L, calibration.mapTime(0))
        assertEquals(24_000L, calibration.mapTime(21_000))
    }

    @Test fun multiplePointsUseTheirOwnNeighbouringSlopes() {
        val calibration = SubtitleCalibration(points = listOf(
            point(21_000, 22_000), point(1_000, 2_000), point(11_000, 14_000)
        ))
        assertEquals(8_000L, calibration.mapTime(6_000))
        assertEquals(18_000L, calibration.mapTime(16_000))
        assertEquals(14_000L, calibration.mapTime(11_000))
    }

    @Test fun sectionModeDoesNotChangeEarlierSectionsOrStretchCrossingCue() {
        val calibration = SubtitleCalibration(SubtitleCalibrationMode.SECTION,
            listOf(point(10_000, 12_000), point(20_000, 20_500)))
        assertEquals(5_000L, calibration.mapTime(5_000))
        assertEquals(17_000L, calibration.mapTime(15_000))
        assertEquals(21_500L, calibration.mapTime(21_000))
        val result = SubtitleTiming.adjustCues(listOf(
            SrtCue(1, 9_000, 11_000, "before"), SrtCue(2, 10_000, 11_000, "after")
        ), 0, 30_000, calibration)
        assertEquals(11_000L, result[0].endMs)
        assertEquals(12_000L, result[1].startMs)
        assertEquals(13_000L, result[1].endMs)
    }

    @Test fun overallOffsetAppliesOnceAndOriginalIdsRemainBookmarked() {
        val source = listOf(SrtCue(7, 1_000, 2_000, "test"))
        val calibration = SubtitleCalibration(points = listOf(point(1_000, 2_000, 7)))
        val adjusted = SubtitleTiming.adjustCues(source, 500, 10_000, calibration)
        assertEquals(2_500L, adjusted.single().startMs)
        assertEquals(3_500L, adjusted.single().endMs)
        assertEquals(1_000L, source.single().startMs)
        assertEquals(7, BookmarkSelection.filter(adjusted, setOf(7)).single().index)
        assertEquals(2_500L, Segmenter.cueOnly(adjusted, 10_000).single().startMs)
    }

    @Test fun mappedCuesAreClampedToAudioAndCollapsedOnesRemoved() {
        val calibration = SubtitleCalibration(points = listOf(point(1_000, 3_000)))
        val adjusted = SubtitleTiming.adjustCues(listOf(
            SrtCue(1, 5_000, 9_000, "end"), SrtCue(2, 8_000, 9_000, "outside")
        ), 0, 8_000, calibration)
        assertEquals(1, adjusted.size)
        assertEquals(8_000L, adjusted.single().endMs)
    }

    @Test fun duplicateAnchorsReverseAudioOrderAndOutOfRangeAreRejected() {
        assertNotNull(SubtitleCalibration(points = listOf(point(1_000, 1_000, 1), point(1_000, 2_000, 2)))
            .validationError(10_000, 0))
        assertNotNull(SubtitleCalibration(points = listOf(point(1_000, 3_000), point(2_000, 2_000)))
            .validationError(10_000, 0))
        assertNotNull(SubtitleCalibration(points = listOf(point(1_000, 11_000))).validationError(10_000, 0))
        assertNull(SubtitleCalibration(points = listOf(point(1_000, -500))).validationError(10_000, 500))
    }

    @Test fun parsesMillisecondsMinutesHoursAndDecimalSeconds() {
        assertEquals(754_500L, SubtitleTiming.parseTime("12:34.500"))
        assertEquals(3_723_250L, SubtitleTiming.parseTime("1:02:03,250"))
        assertEquals(12_500L, SubtitleTiming.parseTime("12.5"))
        assertEquals(1_000L, SubtitleTiming.parseTime("00:01"))
        listOf("-1", "1:60", "1:99:00", "hello", "1.1234", "").forEach {
            assertNull(SubtitleTiming.parseTime(it))
        }
    }
}
