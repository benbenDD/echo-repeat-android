package com.echoenglish.app.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackServicePolicyTest {
    @Test fun blockedTrackLoadIsDeferredUntilForeground() {
        assertTrue(PlaybackServicePolicy.shouldDeferBlockedStart(PlaybackContract.ACTION_LOAD))
        assertFalse(PlaybackServicePolicy.shouldDeferBlockedStart(PlaybackContract.ACTION_TOGGLE))
    }
    @Test fun loadingAudioRequiresForegroundStart() {
        assertTrue(PlaybackServicePolicy.requiresForegroundStart(PlaybackContract.ACTION_LOAD))
    }

    @Test fun pausedColdRestoreDoesNotRequestForegroundStart() {
        assertFalse(
            PlaybackServicePolicy.requiresForegroundStart(
                PlaybackContract.ACTION_LOAD,
                autoPlay = false
            )
        )
    }

    @Test fun ordinaryUpdateDoesNotCreateForegroundService() {
        assertFalse(PlaybackServicePolicy.requiresForegroundStart(PlaybackContract.ACTION_UPDATE_SPEED))
    }

    @Test fun naturalCompletionMayAdvancePlaylist() {
        assertTrue(
            PlaybackServicePolicy.shouldAdvancePlaylist(
                completed = true,
                stopReason = PlaybackStopReason.TRACK_COMPLETED
            )
        )
    }

    @Test fun sleepTimerStopNeverAdvancesPlaylist() {
        assertFalse(
            PlaybackServicePolicy.shouldAdvancePlaylist(
                completed = true,
                stopReason = PlaybackStopReason.SLEEP_TIMER
            )
        )
        assertFalse(
            PlaybackServicePolicy.shouldAdvancePlaylist(
                completed = false,
                stopReason = PlaybackStopReason.SLEEP_TIMER
            )
        )
    }

    @Test fun incompletePlaybackNeverAdvancesPlaylist() {
        assertFalse(
            PlaybackServicePolicy.shouldAdvancePlaylist(
                completed = false,
                stopReason = PlaybackStopReason.TRACK_COMPLETED
            )
        )
    }

    @Test fun playingServiceSurvivesTaskRemoval() {
        assertTrue(PlaybackServicePolicy.shouldKeepOnTaskRemoved(isPlaying = true, mediaItemCount = 1))
    }

    @Test fun pausedLoadedTrackSurvivesTaskRemoval() {
        assertTrue(PlaybackServicePolicy.shouldKeepOnTaskRemoved(isPlaying = false, mediaItemCount = 1))
    }

    @Test fun emptyIdleServiceMayStopAfterTaskRemoval() {
        assertFalse(PlaybackServicePolicy.shouldKeepOnTaskRemoved(isPlaying = false, mediaItemCount = 0))
    }

    @Test fun automatedRepeatGapRetainsExistingForegroundService() {
        assertTrue(
            PlaybackServicePolicy.shouldRunInForeground(
                startInForegroundRequired = false,
                playbackTaskActive = true,
                completed = false,
                hasSource = true,
                playlistHandoffActive = false
            )
        )
    }

    @Test fun completedOrUserPausedPlaybackMayLeaveForeground() {
        assertFalse(
            PlaybackServicePolicy.shouldRunInForeground(
                startInForegroundRequired = false,
                playbackTaskActive = false,
                completed = false,
                hasSource = true,
                playlistHandoffActive = false
            )
        )
        assertFalse(
            PlaybackServicePolicy.shouldRunInForeground(
                startInForegroundRequired = false,
                playbackTaskActive = true,
                completed = true,
                hasSource = true,
                playlistHandoffActive = false
            )
        )
    }

    @Test fun media3ForegroundRequestAlwaysPromotesService() {
        assertTrue(
            PlaybackServicePolicy.shouldRunInForeground(
                startInForegroundRequired = true,
                playbackTaskActive = false,
                completed = false,
                hasSource = false,
                playlistHandoffActive = false
            )
        )
    }

    @Test fun naturalPlaylistHandoffRetainsForegroundService() {
        assertTrue(
            PlaybackServicePolicy.shouldRunInForeground(
                startInForegroundRequired = false,
                playbackTaskActive = false,
                completed = true,
                hasSource = true,
                playlistHandoffActive = true
            )
        )
    }

    @Test fun sleepTimerStopBlocksBoundaryAutomation() {
        assertFalse(
            PlaybackServicePolicy.mayAutomateBoundary(PlaybackStopReason.SLEEP_TIMER)
        )
    }

    @Test fun naturalPlaybackMayAutomateBoundary() {
        assertTrue(PlaybackServicePolicy.mayAutomateBoundary(PlaybackStopReason.NONE))
        assertTrue(
            PlaybackServicePolicy.mayAutomateBoundary(PlaybackStopReason.TRACK_COMPLETED)
        )
    }
}
