package com.echoenglish.app.playback

import com.echoenglish.app.model.PlaylistMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistNavigationTest {
    @Test fun stopAfterTrackHasNoNextItem() {
        assertNull(PlaylistNavigation.nextIndex(PlaylistMode.STOP_AFTER_TRACK, 1, 3))
    }

    @Test fun sequentialMovesToNextAndStopsAtEnd() {
        assertEquals(2, PlaylistNavigation.nextIndex(PlaylistMode.SEQUENTIAL, 1, 3))
        assertNull(PlaylistNavigation.nextIndex(PlaylistMode.SEQUENTIAL, 2, 3))
    }

    @Test fun loopListWrapsAfterLastItem() {
        assertEquals(0, PlaylistNavigation.nextIndex(PlaylistMode.LOOP_LIST, 2, 3))
    }

    @Test fun invalidOrEmptyListHasNoNextItem() {
        assertNull(PlaylistNavigation.nextIndex(PlaylistMode.LOOP_LIST, 0, 0))
        assertNull(PlaylistNavigation.nextIndex(PlaylistMode.SEQUENTIAL, -1, 3))
    }
}
