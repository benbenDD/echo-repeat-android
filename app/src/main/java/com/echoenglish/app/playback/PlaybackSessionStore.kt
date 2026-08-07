package com.echoenglish.app.playback

import android.content.Context

data class PersistedPlaybackSession(
    val trackId: Long,
    val positionMs: Long,
    val segmentIndex: Int,
    val repeatIndex: Int,
    val wasPlaying: Boolean,
    val savedAtMs: Long
)

object PlaybackSessionPolicy {
    const val SAVE_INTERVAL_MS = 1_500L

    fun isSaveDue(nowMs: Long, lastSavedAtMs: Long): Boolean =
        lastSavedAtMs <= 0L || nowMs < lastSavedAtMs ||
            nowMs - lastSavedAtMs >= SAVE_INTERVAL_MS
}

class PlaybackSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(): PersistedPlaybackSession? {
        val trackId = preferences.getLong(KEY_TRACK_ID, 0L)
        if (trackId <= 0L) return null
        return PersistedPlaybackSession(
            trackId = trackId,
            positionMs = preferences.getLong(KEY_POSITION_MS, 0L).coerceAtLeast(0L),
            segmentIndex = preferences.getInt(KEY_SEGMENT_INDEX, 0).coerceAtLeast(0),
            repeatIndex = preferences.getInt(KEY_REPEAT_INDEX, 1).coerceAtLeast(1),
            wasPlaying = preferences.getBoolean(KEY_WAS_PLAYING, false),
            savedAtMs = preferences.getLong(KEY_SAVED_AT_MS, 0L).coerceAtLeast(0L)
        )
    }

    fun save(value: PersistedPlaybackSession, synchronous: Boolean = false): Boolean {
        if (value.trackId <= 0L) return false
        val editor = preferences.edit()
            .putLong(KEY_TRACK_ID, value.trackId)
            .putLong(KEY_POSITION_MS, value.positionMs.coerceAtLeast(0L))
            .putInt(KEY_SEGMENT_INDEX, value.segmentIndex.coerceAtLeast(0))
            .putInt(KEY_REPEAT_INDEX, value.repeatIndex.coerceAtLeast(1))
            .putBoolean(KEY_WAS_PLAYING, value.wasPlaying)
            .putLong(KEY_SAVED_AT_MS, value.savedAtMs.coerceAtLeast(0L))
        return if (synchronous) editor.commit() else {
            editor.apply()
            true
        }
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "playback_session"
        const val KEY_TRACK_ID = "track_id"
        const val KEY_POSITION_MS = "position_ms"
        const val KEY_SEGMENT_INDEX = "segment_index"
        const val KEY_REPEAT_INDEX = "repeat_index"
        const val KEY_WAS_PLAYING = "was_playing"
        const val KEY_SAVED_AT_MS = "saved_at_ms"
    }
}
