package com.echoenglish.app.playback

sealed interface PlaybackRestoreDecision {
    data class AttachToActive(val trackId: Long) : PlaybackRestoreDecision
    data class LoadPaused(val trackId: Long) : PlaybackRestoreDecision
    data object None : PlaybackRestoreDecision
}

data class PlaybackRestoreLoadState(
    val positionMs: Long,
    val segmentIndex: Int,
    val repeatIndex: Int,
    val autoPlay: Boolean = false,
    val restoreExactPosition: Boolean = true
)

object PlaybackRestorePolicy {
    fun decide(
        activeMediaId: String,
        activeSegmentCount: Int,
        lastTrackId: Long,
        activeServiceAvailable: Boolean = true
    ): PlaybackRestoreDecision {
        val activeTrackId = activeMediaId.toLongOrNull()
            ?.takeIf { it > 0L && activeSegmentCount > 0 && activeServiceAvailable }
        return when {
            activeTrackId != null -> PlaybackRestoreDecision.AttachToActive(activeTrackId)
            lastTrackId > 0L -> PlaybackRestoreDecision.LoadPaused(lastTrackId)
            else -> PlaybackRestoreDecision.None
        }
    }

    fun loadState(
        trackId: Long,
        databasePositionMs: Long,
        databaseSegmentIndex: Int,
        session: PersistedPlaybackSession?
    ): PlaybackRestoreLoadState {
        val matchingSession = session?.takeIf { it.trackId == trackId }
        return PlaybackRestoreLoadState(
            positionMs = matchingSession?.positionMs ?: databasePositionMs.coerceAtLeast(0L),
            segmentIndex = matchingSession?.segmentIndex ?: databaseSegmentIndex.coerceAtLeast(0),
            repeatIndex = matchingSession?.repeatIndex ?: 1
        )
    }
}
