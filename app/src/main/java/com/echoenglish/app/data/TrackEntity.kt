package com.echoenglish.app.data

data class TrackEntity(
    val id: Long = 0,
    val audioUri: String,
    val fileName: String,
    val title: String,
    val subtitleUri: String? = null,
    val subtitleOffsetMs: Long = 0,
    val durationMs: Long = 0,
    val currentPositionMs: Long = 0,
    val currentSegment: Int = 0,
    val segmentMode: String = "FIXED",
    val segmentSeconds: Int = 15,
    val repeatCount: Int = 3,
    val speed: Float = 1f,
    val importedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0,
    val completed: Boolean = false,
    val sortOrder: Int = 0,
    val available: Boolean = true
)
