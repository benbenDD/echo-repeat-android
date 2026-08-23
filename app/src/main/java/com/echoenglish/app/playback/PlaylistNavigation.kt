package com.echoenglish.app.playback

import com.echoenglish.app.model.PlaylistMode

object PlaylistNavigation {
    fun restartsCurrentTrack(mode: PlaylistMode): Boolean = mode == PlaylistMode.LOOP_TRACK

    fun nextIndex(mode: PlaylistMode, currentIndex: Int, size: Int): Int? {
        if (size <= 0 || currentIndex !in 0 until size) return null
        return when (mode) {
            PlaylistMode.STOP_AFTER_TRACK -> null
            PlaylistMode.LOOP_TRACK -> currentIndex
            PlaylistMode.SEQUENTIAL -> (currentIndex + 1).takeIf { it < size }
            PlaylistMode.LOOP_LIST -> (currentIndex + 1).mod(size)
        }
    }
}
