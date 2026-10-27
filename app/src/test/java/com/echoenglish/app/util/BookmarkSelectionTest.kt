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

    @Test fun temporaryLocationAddsOnlyTheSelectedCueWithoutChangingBookmarks() {
        val bookmarks = setOf(10)
        assertEquals(listOf(10, 20), BookmarkSelection.filter(cues, bookmarks, 20).map { it.index })
        assertEquals(setOf(10), bookmarks)
        assertEquals(listOf(10), BookmarkSelection.filter(cues, bookmarks).map { it.index })
    }

    @Test fun replacingTemporaryLocationDoesNotKeepOldTemporaryCues() {
        assertEquals(listOf(10, 30), BookmarkSelection.filter(cues, setOf(10), 30).map { it.index })
        assertEquals(listOf(10), BookmarkSelection.filter(cues, setOf(10), 10).map { it.index })
    }
}
