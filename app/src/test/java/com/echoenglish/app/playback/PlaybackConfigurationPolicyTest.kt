package com.echoenglish.app.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackConfigurationPolicyTest {
    @Test fun loopReloadIsDiscardedAfterPlaybackScopeChanges() {
        assertTrue(PlaybackConfigurationPolicy.isCurrent(7L, 7L))
        assertFalse(PlaybackConfigurationPolicy.isCurrent(7L, 8L))
    }
}
