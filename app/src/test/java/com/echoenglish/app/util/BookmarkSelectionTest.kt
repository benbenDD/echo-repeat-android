package com.echoenglish.app.util

import com.echoenglish.app.model.SrtCue
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkSelectionTest {
    private val cues = listOf(
        SrtCue(10, 1_000, 2_000, "A"),
        SrtCue(20, 3_000, 4_000, "B"),
        SrtCue(30, 5_000, 6_000, "C")
    )

    @Test fun keepsOnlyCuesWhoseOriginalSubtitleIdsAreBookmarked() {
        assertEquals(
            listOf(10, 30),
            BookmarkSelection.filter(cues, setOf(30, 10)).map { it.index }
        )
    }

    @Test fun emptyBookmarksProduceNoPlayableCues() {
        assertEquals(emptyList<SrtCue>(), BookmarkSelection.filter(cues, emptySet()))
    }
}
