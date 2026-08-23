package com.echoenglish.app.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSettingsChangePolicyTest {
    @Test fun playlistChangesAreNeverBlockedBySubtitleBookmarkValidation() {
        val previous = PlaybackSettings(
            segmentMode = SegmentMode.SUBTITLE,
            subtitlePlaybackScope = SubtitlePlaybackScope.BOOKMARKED_CUES,
            playlistMode = PlaylistMode.STOP_AFTER_TRACK
        )
        assertFalse(
            PlaybackSettingsChangePolicy.requiresCurrentTrackValidation(
                previous,
                previous.copy(playlistMode = PlaylistMode.LOOP_TRACK)
            )
        )
    }

    @Test fun enteringBookmarkedSubtitlePlaybackRequiresCurrentTrackValidation() {
        val previous = PlaybackSettings(
            segmentMode = SegmentMode.SUBTITLE,
            subtitlePlaybackScope = SubtitlePlaybackScope.CUES_ONLY
        )
        assertTrue(
            PlaybackSettingsChangePolicy.requiresCurrentTrackValidation(
                previous,
                previous.copy(subtitlePlaybackScope = SubtitlePlaybackScope.BOOKMARKED_CUES)
            )
        )
    }
}
