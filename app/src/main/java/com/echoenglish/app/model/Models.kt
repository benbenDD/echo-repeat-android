package com.echoenglish.app.model

data class SrtCue(val index: Int, val startMs: Long, val endMs: Long, val text: String)
data class Segment(val startMs: Long, val endMs: Long, val text: String = "") {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}
enum class SegmentMode { FIXED, SUBTITLE }
enum class PlaylistMode { STOP_AFTER_TRACK, SEQUENTIAL, LOOP_LIST }
data class PlaybackSettings(
    val segmentMode: SegmentMode = SegmentMode.FIXED,
    val segmentSeconds: Int = 15,
    val repeatCount: Int = 3,
    val segmentGapMs: Long = 0,
    val speed: Float = 1f,
    val leadInMs: Long = 300,
    val leadOutMs: Long = 500,
    val playlistMode: PlaylistMode = PlaylistMode.SEQUENTIAL,
    val stopAtSegmentEnd: Boolean = true
)
