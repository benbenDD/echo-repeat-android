package com.echoenglish.app.util

import com.echoenglish.app.model.SrtCue

object BookmarkSelection {
    fun filter(
        cues: List<SrtCue>,
        bookmarkedCueIds: Set<Int>
    ): List<SrtCue> = cues.filter { it.index in bookmarkedCueIds }

    fun canLocate(cueId: Int?, bookmarkedCueIds: Set<Int>, bookmarksOnly: Boolean): Boolean =
        !bookmarksOnly || (cueId != null && cueId in bookmarkedCueIds)
}
