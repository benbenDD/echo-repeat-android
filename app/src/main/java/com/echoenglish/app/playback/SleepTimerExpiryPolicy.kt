package com.echoenglish.app.playback

enum class SleepTimerExpiryAction { STOP_NOW, WAIT_FOR_SEGMENT_END }

object SleepTimerExpiryPolicy {
    fun action(
        stopAtSegmentEnd: Boolean,
        isInSegmentGap: Boolean,
        playbackRequested: Boolean = true
    ): SleepTimerExpiryAction =
        if (!playbackRequested || !stopAtSegmentEnd || isInSegmentGap) {
            SleepTimerExpiryAction.STOP_NOW
        } else {
            SleepTimerExpiryAction.WAIT_FOR_SEGMENT_END
        }

    fun shouldClearOnManualPlay(deadlineMs: Long, nowMs: Long, pendingStop: Boolean): Boolean =
        pendingStop || (deadlineMs > 0L && deadlineMs <= nowMs)
}
