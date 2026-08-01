package com.echoenglish.app.playback

object PlaybackMath {
    fun segmentIndexAt(starts: LongArray, ends: LongArray, positionMs: Long): Int {
        if (starts.isEmpty()) return 0
        val containing = starts.indices.lastOrNull { positionMs >= starts[it] && positionMs < ends.getOrElse(it) { Long.MAX_VALUE } }
        if (containing != null) return containing
        return (starts.indices.lastOrNull { starts[it] <= positionMs } ?: 0).coerceIn(0, starts.lastIndex)
    }

    fun subtitleIndexAt(starts: LongArray, positionMs: Long): Int {
        if (starts.isEmpty()) return -1
        return starts.indices.lastOrNull { starts[it] <= positionMs } ?: -1
    }
}
