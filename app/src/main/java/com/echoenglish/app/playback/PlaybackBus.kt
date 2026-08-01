package com.echoenglish.app.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SegmentSnapshot(val startMs: Long, val endMs: Long, val text: String = "")
data class SubtitleSnapshot(val startMs: Long, val endMs: Long, val text: String)

data class PlaybackSnapshot(
    val title: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val segmentIndex: Int = 0,
    val segmentCount: Int = 0,
    val repeatIndex: Int = 1,
    val repeatCount: Int = 1,
    val segmentStartMs: Long = 0,
    val segmentEndMs: Long = 0,
    val playbackSpeed: Float = 1f,
    val subtitle: String = "",
    val nextSubtitle: String = "",
    val subtitleIndex: Int = -1,
    val segments: List<SegmentSnapshot> = emptyList(),
    val subtitles: List<SubtitleSnapshot> = emptyList(),
    val sleepDeadlineMs: Long = 0,
    val completed: Boolean = false
) {
    val segmentPositionMs: Long get() = (positionMs - segmentStartMs).coerceAtLeast(0)
    val segmentDurationMs: Long get() = (segmentEndMs - segmentStartMs).coerceAtLeast(0)
}

object PlaybackBus {
    private val mutable = MutableStateFlow(PlaybackSnapshot())
    val state = mutable.asStateFlow()
    fun update(value: PlaybackSnapshot) { mutable.value = value }
}
