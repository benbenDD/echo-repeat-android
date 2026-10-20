package com.echoenglish.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerExpiryPolicyTest {
    @Test fun expiredTimerWhilePausedIsConsumedWithoutWaitingForAnotherPlayback() {
        assertEquals(
            SleepTimerExpiryAction.STOP_NOW,
            SleepTimerExpiryPolicy.action(true, false, playbackRequested = false)
        )
    }

    @Test fun manualPlaybackClearsExpiredOrPendingTimerButPreservesFutureTimer() {
        assertTrue(SleepTimerExpiryPolicy.shouldClearOnManualPlay(1_000, 2_000, false))
        assertTrue(SleepTimerExpiryPolicy.shouldClearOnManualPlay(0, 2_000, true))
        assertFalse(SleepTimerExpiryPolicy.shouldClearOnManualPlay(3_000, 2_000, false))
        assertFalse(SleepTimerExpiryPolicy.shouldClearOnManualPlay(0, 2_000, false))
    }

    @Test fun immediateModeStopsAsSoonAsDeadlineExpires() {
        assertEquals(
            SleepTimerExpiryAction.STOP_NOW,
            SleepTimerExpiryPolicy.action(stopAtSegmentEnd = false, isInSegmentGap = false)
        )
    }

    @Test fun currentSegmentModeWaitsForThePlayingSegmentBoundary() {
        assertEquals(
            SleepTimerExpiryAction.WAIT_FOR_SEGMENT_END,
            SleepTimerExpiryPolicy.action(stopAtSegmentEnd = true, isInSegmentGap = false)
        )
    }

    @Test fun currentSegmentModeStopsImmediatelyWhenAlreadyBetweenRepeats() {
        assertEquals(
            SleepTimerExpiryAction.STOP_NOW,
            SleepTimerExpiryPolicy.action(stopAtSegmentEnd = true, isInSegmentGap = true)
        )
    }
}
