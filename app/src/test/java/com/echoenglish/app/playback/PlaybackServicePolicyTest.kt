package com.echoenglish.app.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackServicePolicyTest {
    @Test fun loadingAudioRequiresForegroundStart() {
        assertTrue(PlaybackServicePolicy.requiresForegroundStart(PlaybackContract.ACTION_LOAD))
    }

    @Test fun ordinaryUpdateDoesNotCreateForegroundService() {
        assertFalse(PlaybackServicePolicy.requiresForegroundStart(PlaybackContract.ACTION_UPDATE_SPEED))
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
}