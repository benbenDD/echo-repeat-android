package com.echoenglish.app.playback

object PlaybackMath {
    fun shouldRepeatSegment(repeatCount: Int, repeatIndex: Int): Boolean =
        repeatCount == 0 || repeatIndex < repeatCount

    fun segmentIndexAt(starts: LongArray, ends: LongArray, positionMs: Long): Int {
        if (starts.isEmpty()) return 0
        val containing = starts.indices.lastOrNull {
            positionMs >= starts[it] &&
                positionMs < ends.getOrElse(it) { Long.MAX_VALUE }
        }
        if (containing != null) return containing
        return (starts.indices.lastOrNull { starts[it] <= positionMs } ?: 0)
            .coerceIn(0, starts.lastIndex)
    }

    fun snapToPlayablePosition(
        starts: LongArray,
        ends: LongArray,
        positionMs: Long,
        durationMs: Long,
        skipGaps: Boolean
    ): Long {
        val clamped = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0))
        if (!skipGaps || starts.isEmpty() || starts.size != ends.size) return clamped
        val containing = starts.indices.firstOrNull {
            clamped >= starts[it] && clamped < ends[it]
        }
        if (containing != null) return clamped
        val next = starts.firstOrNull { it > clamped }
        return next ?: starts.last()
    }

    fun subtitleIndexAt(starts: LongArray, positionMs: Long): Int {
        if (starts.isEmpty()) return -1
        return starts.indices.lastOrNull { starts[it] <= positionMs } ?: -1
    }
}
