package com.echoenglish.app.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerExpiryPolicyTest {
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
