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

    @Test fun snapshotFromDestroyedServiceFallsBackToSavedTrack() {
        assertEquals(
            PlaybackRestoreDecision.LoadPaused(7L),
            PlaybackRestorePolicy.decide(
                activeMediaId = "42",
                activeSegmentCount = 8,
                lastTrackId = 7L,
                activeServiceAvailable = false
            )
        )
    }

    @Test fun matchingSessionOverridesDatabaseProgressForColdRestore() {
        val state = PlaybackRestorePolicy.loadState(
            trackId = 7L,
            databasePositionMs = 12_000L,
            databaseSegmentIndex = 2,
            session = PersistedPlaybackSession(
                trackId = 7L,
                positionMs = 18_450L,
                segmentIndex = 3,
                repeatIndex = 4,
                wasPlaying = true,
                savedAtMs = 99_000L
            )
        )

        assertEquals(18_450L, state.positionMs)
        assertEquals(3, state.segmentIndex)
        assertEquals(4, state.repeatIndex)
        assertEquals(false, state.autoPlay)
        assertEquals(true, state.restoreExactPosition)
    }

    @Test fun anotherTracksSessionCannotPolluteColdRestore() {
        val state = PlaybackRestorePolicy.loadState(
            trackId = 7L,
            databasePositionMs = 12_000L,
            databaseSegmentIndex = 2,
            session = PersistedPlaybackSession(
                trackId = 8L,
                positionMs = 18_450L,
                segmentIndex = 3,
                repeatIndex = 4,
                wasPlaying = true,
                savedAtMs = 99_000L
            )
        )

        assertEquals(12_000L, state.positionMs)
        assertEquals(2, state.segmentIndex)
        assertEquals(1, state.repeatIndex)
        assertEquals(false, state.autoPlay)
        assertEquals(true, state.restoreExactPosition)
    }
}
