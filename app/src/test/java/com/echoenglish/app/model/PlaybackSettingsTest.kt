package com.echoenglish.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSettingsTest {
    @Test fun oldInstallDefaultsToFullTimeline() {
        assertEquals(
            SubtitlePlaybackScope.FULL_TIMELINE,
            PlaybackSettings().subtitlePlaybackScope
        )
    }
}
