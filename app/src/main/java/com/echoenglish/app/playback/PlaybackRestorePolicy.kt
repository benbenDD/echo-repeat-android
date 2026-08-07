package com.echoenglish.app.playback

sealed interface PlaybackRestoreDecision {
    data class AttachToActive(val trackId: Long) : PlaybackRestoreDecision
    data class LoadPaused(val trackId: Long) : PlaybackRestoreDecision
    data object None : PlaybackRestoreDecision
}

object PlaybackRestorePolicy {
    fun decide(
        activeMediaId: String,
        activeSegmentCount: Int,
        lastTrackId: Long
    ): PlaybackRestoreDecision {
        val activeTrackId = activeMediaId.toLongOrNull()
            ?.takeIf { it > 0L && activeSegmentCount > 0 }
        return when {
            activeTrackId != null -> PlaybackRestoreDecision.AttachToActive(activeTrackId)
            lastTrackId > 0L -> PlaybackRestoreDecision.LoadPaused(lastTrackId)
            else -> PlaybackRestoreDecision.None
        }
    }
}
