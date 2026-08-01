package com.echoenglish.app.util

import org.junit.Assert.*
import org.junit.Test

class FilenameMatcherTest {
    @Test fun matchesSeparatorsAndLanguageSuffix() {
        assertEquals("lesson-01.en.srt", FilenameMatcher.findSubtitle("Lesson_01.MP3", listOf("lesson-01.en.srt")))
    }

    @Test fun doesNotGuessWhenCandidatesConflict() {
        assertNull(FilenameMatcher.findSubtitle("lesson01.mp3", listOf("lesson01.en.srt", "lesson01.eng.srt")))
    }
}
