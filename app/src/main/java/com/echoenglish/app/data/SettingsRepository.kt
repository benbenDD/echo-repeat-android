package com.echoenglish.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.echoenglish.app.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("echo_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val MODE = stringPreferencesKey("segment_mode")
        val SUBTITLE_SCOPE = stringPreferencesKey("subtitle_playback_scope")
        val SECONDS = intPreferencesKey("segment_seconds")
        val REPEATS = intPreferencesKey("repeat_count")
        val GAP_MS = longPreferencesKey("segment_gap_ms")
        val SPEED = floatPreferencesKey("speed")
        val LEAD_IN = longPreferencesKey("lead_in")
        val LEAD_OUT = longPreferencesKey("lead_out")
        val LIST_MODE = stringPreferencesKey("playlist_mode")
        val STOP_AT_END = booleanPreferencesKey("stop_at_segment_end")
        val LAST_TRACK = longPreferencesKey("last_track")
    }

    val settings: Flow<PlaybackSettings> = context.dataStore.data.map { p ->
        PlaybackSettings(
            segmentMode = runCatching { SegmentMode.valueOf(p[Keys.MODE] ?: "FIXED") }
                .getOrDefault(SegmentMode.FIXED),
            subtitlePlaybackScope = runCatching {
                SubtitlePlaybackScope.valueOf(p[Keys.SUBTITLE_SCOPE] ?: "FULL_TIMELINE")
            }.getOrDefault(SubtitlePlaybackScope.FULL_TIMELINE),
            segmentSeconds = p[Keys.SECONDS] ?: 15,
            repeatCount = p[Keys.REPEATS] ?: 3,
            segmentGapMs = (p[Keys.GAP_MS] ?: 0L).coerceIn(0L, 5_000L),
            speed = p[Keys.SPEED] ?: 1f,
            leadInMs = p[Keys.LEAD_IN] ?: 300,
            leadOutMs = p[Keys.LEAD_OUT] ?: 500,
            playlistMode = runCatching {
                PlaylistMode.valueOf(p[Keys.LIST_MODE] ?: "SEQUENTIAL")
            }.getOrDefault(PlaylistMode.SEQUENTIAL),
            stopAtSegmentEnd = p[Keys.STOP_AT_END] ?: true
        )
    }

    val lastTrackId: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_TRACK] ?: 0L }

    suspend fun save(value: PlaybackSettings) = context.dataStore.edit { p ->
        p[Keys.MODE] = value.segmentMode.name
        p[Keys.SUBTITLE_SCOPE] = value.subtitlePlaybackScope.name
        p[Keys.SECONDS] = value.segmentSeconds
        p[Keys.REPEATS] = value.repeatCount
        p[Keys.GAP_MS] = value.segmentGapMs.coerceIn(0L, 5_000L)
        p[Keys.SPEED] = value.speed
        p[Keys.LEAD_IN] = value.leadInMs
        p[Keys.LEAD_OUT] = value.leadOutMs
        p[Keys.LIST_MODE] = value.playlistMode.name
        p[Keys.STOP_AT_END] = value.stopAtSegmentEnd
    }

    suspend fun saveLastTrack(id: Long) = context.dataStore.edit { it[Keys.LAST_TRACK] = id }
}
