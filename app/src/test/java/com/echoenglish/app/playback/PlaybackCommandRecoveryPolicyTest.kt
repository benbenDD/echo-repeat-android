package com.echoenglish.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCommandRecoveryPolicyTest {
    @Test fun locatorKeepsPlayIntentWhileBufferingEvenIfSourceNeedsRecovery() {
        val buffering = PlaybackSnapshot(isPlaying = false, playbackRequested = true)
        val action = PlaybackCommandRecoveryPolicy.subtitleSeekAction(buffering.playbackRequested)
        assertEquals(PlaybackContract.ACTION_SEEK_ABSOLUTE_AND_PLAY, action)
        assertEquals(
            PlaybackCommandRecoveryDecision.Recover(autoPlay = true),
            PlaybackCommandRecoveryPolicy.decide(action, false, true)
        )
    }

    @Test fun locatorKeepsUserPausedGapPausedDuringRecovery() {
        val pausedGap = PlaybackSnapshot(
            isInSegmentGap = true, isSegmentGapPaused = true, playbackRequested = false
        )
        val action = PlaybackCommandRecoveryPolicy.subtitleSeekAction(pausedGap.playbackRequested)
        assertEquals(
            PlaybackCommandRecoveryDecision.Recover(autoPlay = false),
            PlaybackCommandRecoveryPolicy.decide(action, false, true)
        )
    }

    @Test fun locatorSeekRecoversMissingSourceAndStartsPlayback() {
        assertEquals(
            PlaybackCommandRecoveryDecision.Recover(autoPlay = true),
            PlaybackCommandRecoveryPolicy.decide(
                PlaybackContract.ACTION_SEEK_ABSOLUTE_AND_PLAY,
                sourceReady = false,
                hasSelectedTrack = true
            )
        )
    }

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

    @Test fun foregroundResumeRecoversPaused() {
        assertEquals(
            PlaybackCommandRecoveryDecision.Recover(autoPlay = false),
            PlaybackCommandRecoveryPolicy.decide(
                PlaybackCommandRecoveryPolicy.ACTION_FOREGROUND_RESUME,
                sourceReady = false,
                hasSelectedTrack = true
            )
        )
    }
}
