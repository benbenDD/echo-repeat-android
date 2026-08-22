package com.echoenglish.app.playback

enum class SleepTimerExpiryAction { STOP_NOW, WAIT_FOR_SEGMENT_END }

object SleepTimerExpiryPolicy {
    fun action(stopAtSegmentEnd: Boolean, isInSegmentGap: Boolean): SleepTimerExpiryAction =
        if (!stopAtSegmentEnd || isInSegmentGap) {
            SleepTimerExpiryAction.STOP_NOW
        } else {
            SleepTimerExpiryAction.WAIT_FOR_SEGMENT_END
        }
}
