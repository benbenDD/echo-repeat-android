package com.echoenglish.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCommandRecoveryPolicyTest {
    @Test fun readySourceDispatchesWithoutReload() {
        assertEquals(
            PlaybackCommandRecoveryDecision.Dispatch,
            PlaybackCommandRecoveryPolicy.decide(
                PlaybackContract.ACTION_TOGGLE,
                sourceReady = true,
                hasSelectedTrack = true
            )
        )
    }

    @Test fun missingSourcePlayRecoversWithAutoplay() {
        assertEquals(
            PlaybackCommandRecoveryDecision.Recover(autoPlay = true),
            PlaybackCommandRecoveryPolicy.decide(
                PlaybackContract.ACTION_TOGGLE,
                sourceReady = false,
                hasSelectedTrack = true
            )
        )
    }

    @Test fun missingSourceNavigationRecoversPaused() {
        listOf(
            PlaybackContract.ACTION_SEEK,
            PlaybackContract.ACTION_SEEK_ABSOLUTE,
            PlaybackContract.ACTION_SEEK_SEGMENT,
            PlaybackContract.ACTION_NEXT,
            PlaybackContract.ACTION_PREVIOUS,
            PlaybackContract.ACTION_RESTART
        ).forEach { action ->
            assertEquals(
                PlaybackCommandRecoveryDecision.Recover(autoPlay = false),
                PlaybackCommandRecoveryPolicy.decide(action, false, true)
            )
        }
    }

    @Test fun missingSourceUpdateCannotCreateEmptyService() {
        listOf(
            PlaybackContract.ACTION_UPDATE_SEGMENTS,
            PlaybackContract.ACTION_UPDATE_REPEATS,
            PlaybackContract.ACTION_UPDATE_GAP,
            PlaybackContract.ACTION_UPDATE_SPEED,
            PlaybackContract.ACTION_TIMER,
            PlaybackContract.ACTION_CANCEL_TIMER
        ).forEach { action ->
            assertEquals(
                PlaybackCommandRecoveryDecision.Ignore,
                PlaybackCommandRecoveryPolicy.decide(action, false, true)
            )
        }
    }

    @Test fun commandWithoutSelectedTrackIsIgnored() {
        assertEquals(
            PlaybackCommandRecoveryDecision.Ignore,
            PlaybackCommandRecoveryPolicy.decide(
                PlaybackContract.ACTION_TOGGLE,
                sourceReady = false,
                hasSelectedTrack = false
            )
        )
    }
}
