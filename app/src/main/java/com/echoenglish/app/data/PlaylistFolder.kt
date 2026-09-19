package com.echoenglish.app.data

data class PlaylistFolder(
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
)
