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

    @Test fun subtitlePaddingDefaultsAreSafeForSpeechBoundaries() {
        val settings = PlaybackSettings()
        assertEquals(300, settings.leadInMs)
        assertEquals(500, settings.leadOutMs)
    }}
