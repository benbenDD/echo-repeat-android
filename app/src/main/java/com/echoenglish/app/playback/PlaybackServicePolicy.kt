package com.echoenglish.app.playback

object PlaybackServicePolicy {
    fun requiresForegroundStart(action: String?): Boolean = action == PlaybackContract.ACTION_LOAD

    fun shouldKeepOnTaskRemoved(isPlaying: Boolean, mediaItemCount: Int): Boolean =
        isPlaying || mediaItemCount > 0

    fun shouldRetainForegroundDuringAutomation(
        startInForegroundRequired: Boolean,
        playbackTaskActive: Boolean,
        completed: Boolean,
        hasSource: Boolean
    ): Boolean =
        !startInForegroundRequired && playbackTaskActive && !completed && hasSource
}