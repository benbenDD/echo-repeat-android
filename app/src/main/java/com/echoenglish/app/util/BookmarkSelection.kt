package com.echoenglish.app.util

import com.echoenglish.app.model.SrtCue

object BookmarkSelection {
    fun filter(cues: List<SrtCue>, bookmarkedCueIds: Set<Int>): List<SrtCue> =
        cues.filter { it.index in bookmarkedCueIds }
}
