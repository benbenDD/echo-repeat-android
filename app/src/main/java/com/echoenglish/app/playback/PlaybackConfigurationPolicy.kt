package com.echoenglish.app.playback

object PlaybackConfigurationPolicy {
    fun isCurrent(expectedGeneration: Long, currentGeneration: Long): Boolean =
        expectedGeneration == currentGeneration
}
