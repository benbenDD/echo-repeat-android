package com.echoenglish.app.playback

sealed interface PlaybackCommandRecoveryDecision {
    data object Dispatch : PlaybackCommandRecoveryDecision
    data class Recover(val autoPlay: Boolean) : PlaybackCommandRecoveryDecision
    data object Ignore : PlaybackCommandRecoveryDecision
}

object PlaybackCommandRecoveryPolicy {
    const val ACTION_FOREGROUND_RESUME = "internal.FOREGROUND_RESUME"

    fun decide(
        action: String?,
        sourceReady: Boolean,
        hasSelectedTrack: Boolean
    ): PlaybackCommandRecoveryDecision {
        if (sourceReady) return PlaybackCommandRecoveryDecision.Dispatch
        if (!hasSelectedTrack) return PlaybackCommandRecoveryDecision.Ignore
        return when (action) {
            PlaybackContract.ACTION_TOGGLE ->
                PlaybackCommandRecoveryDecision.Recover(autoPlay = true)
            PlaybackContract.ACTION_SEEK,
            PlaybackContract.ACTION_SEEK_ABSOLUTE,
            PlaybackContract.ACTION_SEEK_SEGMENT,
            PlaybackContract.ACTION_NEXT,
            PlaybackContract.ACTION_PREVIOUS,
            PlaybackContract.ACTION_RESTART,
            ACTION_FOREGROUND_RESUME ->
                PlaybackCommandRecoveryDecision.Recover(autoPlay = false)
            else -> PlaybackCommandRecoveryDecision.Ignore
        }
    }
}
