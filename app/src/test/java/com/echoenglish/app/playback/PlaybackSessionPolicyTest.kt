package com.echoenglish.app.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionPolicyTest {
    @Test fun firstSessionSnapshotIsDueImmediately() {
        assertTrue(PlaybackSessionPolicy.isSaveDue(nowMs = 10_000L, lastSavedAtMs = 0L))
    }

    @Test fun sessionSnapshotWaitsForTheInterval() {
        assertFalse(PlaybackSessionPolicy.isSaveDue(nowMs = 10_499L, lastSavedAtMs = 10_000L))
        assertTrue(PlaybackSessionPolicy.isSaveDue(nowMs = 11_500L, lastSavedAtMs = 10_000L))
    }

    @Test fun clockRollbackMakesTheNextSnapshotDue() {
        assertTrue(PlaybackSessionPolicy.isSaveDue(nowMs = 9_000L, lastSavedAtMs = 10_000L))
    }
}
