package com.echoenglish.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleSearchTest {
    @Test fun emptyQueryShowsEverySubtitle() {
        assertTrue(SubtitleSearch.matches("任意字幕", ""))
        assertTrue(SubtitleSearch.matches("任意字幕", "   "))
    }

    @Test fun searchesChineseSubtitleText() {
        assertTrue(SubtitleSearch.matches("我想起那句台词了", "那句台词"))
        assertFalse(SubtitleSearch.matches("我想起那句台词了", "另一个片段"))
    }

    @Test fun ignoresEnglishCaseAndWhitespaceDifferences() {
        assertTrue(SubtitleSearch.matches("Where  are\nyou going?", "where are you"))
    }
}
