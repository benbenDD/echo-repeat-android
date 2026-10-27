package com.echoenglish.app.util

import com.echoenglish.app.model.SrtCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun unbookmarkedCueCannotBeLocatedUntilUserBookmarksIt() {
        val bookmarks = mutableSetOf(10)
        assertFalse(BookmarkSelection.canLocate(20, bookmarks, bookmarksOnly = true))
        assertEquals(listOf(10), BookmarkSelection.filter(cues, bookmarks).map { it.index })
        bookmarks.add(20)
        assertTrue(BookmarkSelection.canLocate(20, bookmarks, bookmarksOnly = true))
        assertEquals(listOf(10, 20), BookmarkSelection.filter(cues, bookmarks).map { it.index })
    }

    @Test fun bookmarkScopeAllowsExistingBookmarkButRejectsMissingCue() {
        assertTrue(BookmarkSelection.canLocate(10, setOf(10), bookmarksOnly = true))
        assertFalse(BookmarkSelection.canLocate(null, setOf(10), bookmarksOnly = true))
    }

    @Test fun otherPlaybackScopesDoNotRequireBookmarks() {
        assertTrue(BookmarkSelection.canLocate(20, emptySet(), bookmarksOnly = false))
        assertTrue(BookmarkSelection.canLocate(null, emptySet(), bookmarksOnly = false))
    }
}
