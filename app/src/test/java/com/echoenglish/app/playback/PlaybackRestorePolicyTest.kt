package com.echoenglish.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRestorePolicyTest {
    @Test fun liveSnapshotWinsOverSavedTrack() {
        assertEquals(
            PlaybackRestoreDecision.AttachToActive(42L),
            PlaybackRestorePolicy.decide("42", activeSegmentCount = 8, lastTrackId = 7L)
        )
    }

    @Test fun savedTrackIsUsedWhenNoLiveSnapshotExists() {
        assertEquals(
            PlaybackRestoreDecision.LoadPaused(7L),
            PlaybackRestorePolicy.decide("", activeSegmentCount = 0, lastTrackId = 7L)
        )
    }

    @Test fun invalidIdsProduceNoRestore() {
        assertEquals(
            PlaybackRestoreDecision.None,
            PlaybackRestorePolicy.decide("bad", activeSegmentCount = 4, lastTrackId = 0L)
        )
    }

    @Test fun staleSnapshotFallsBackToSavedTrack() {
        assertEquals(
            PlaybackRestoreDecision.LoadPaused(7L),
            PlaybackRestorePolicy.decide("42", activeSegmentCount = 0, lastTrackId = 7L)
        )
    }
}
