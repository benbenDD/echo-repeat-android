package com.echoenglish.app.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    val subtitle: String = "",
    val nextSubtitle: String = "",
    val sleepDeadlineMs: Long = 0,
    val completed: Boolean = false
)

object PlaybackBus {
    private val mutable = MutableStateFlow(PlaybackSnapshot())
    val state = mutable.asStateFlow()
    fun update(value: PlaybackSnapshot) { mutable.value = value }
}
